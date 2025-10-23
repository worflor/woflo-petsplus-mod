package woflo.petsplus.ai.suggester;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.context.perception.ContextSlice;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignalRegistry;
import woflo.petsplus.ai.suggester.signal.FeasibilitySignal;
import woflo.petsplus.ai.suggester.signal.FeasibilitySignalRegistry;
import woflo.petsplus.ai.suggester.signal.SignalBootstrap;
import woflo.petsplus.ai.suggester.signal.SignalResult;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Evaluates pet state and suggests behavioural goals with confidence weights.
 *
 * <p>The suggester keeps per-pet evaluation caches so the hot path only recomputes
 * goals when relevant context slices change or cadence windows expire. This keeps
 * immersive behaviour while dramatically reducing per-tick allocation churn.</p>
 */
public class GoalSuggester {

    public record Suggestion(
        GoalDefinition definition,
        float desirability,
        float feasibility,
        String reason
    ) {
        public float score() {
            return desirability * feasibility;
        }
    }

    public static final float MAX_DESIRABILITY = 12.0f;
    public static final float MAX_FEASIBILITY = 1.0f;
    private static final float MIN_MULTIPLIER = 0.0f;

    private final CapabilityAnalyzer capabilityAnalyzer;
    private final Map<MobEntity, EvaluationState> states = new WeakHashMap<>();
    private final Map<Identifier, EnumSet<ContextSlice>> dependencyCache = new HashMap<>();

    public GoalSuggester() {
        this(mob -> mob != null ? MobCapabilities.analyze(mob) : defaultCapabilities());
    }

    public GoalSuggester(CapabilityAnalyzer capabilityAnalyzer) {
        this.capabilityAnalyzer = Objects.requireNonNull(capabilityAnalyzer);
    }

    private static MobCapabilities.CapabilityProfile defaultCapabilities() {
        return new MobCapabilities.CapabilityProfile(
            true, true, true, true,
            true, true, true, true,
            true, true, true, true,
            true
        );
    }

    @FunctionalInterface
    public interface CapabilityAnalyzer {
        MobCapabilities.CapabilityProfile analyze(MobEntity mob);
    }

    public List<Suggestion> suggest(PetContext ctx) {
        if (ctx == null || ctx.mob() == null) {
            return List.of();
        }

        SignalBootstrap.ensureInitialized();

        EvaluationState state = stateFor(ctx.mob());
        MobCapabilities.CapabilityProfile capabilities = capabilityAnalyzer.analyze(ctx.mob());
        ensureFreshEvaluations(state, ctx, capabilities, ctx.worldTime(), true);

        List<GoalEvaluationCache.Entry> entries = state.cache.validEntriesSorted();
        List<Suggestion> suggestions = new ArrayList<>(entries.size());
        for (GoalEvaluationCache.Entry entry : entries) {
            suggestions.add(entry.suggestion());
        }
        return suggestions;
    }

    public Optional<Suggestion> suggestBest(PetContext ctx) {
        if (ctx == null || ctx.mob() == null) {
            return Optional.empty();
        }

        SignalBootstrap.ensureInitialized();

        EvaluationState state = stateFor(ctx.mob());
        MobCapabilities.CapabilityProfile capabilities = capabilityAnalyzer.analyze(ctx.mob());
        ensureFreshEvaluations(state, ctx, capabilities, ctx.worldTime(), false);

        GoalEvaluationCache.Entry best = state.cache.bestEntry();
        return best != null ? Optional.of(best.suggestion()) : Optional.empty();
    }

    private void ensureFreshEvaluations(
        EvaluationState state,
        PetContext ctx,
        MobCapabilities.CapabilityProfile capabilities,
        long tick,
        boolean forceImmediate
    ) {
        ContextSignature signature = ContextSignature.capture(ctx);
        EnumSet<ContextSlice> dirtySlices = state.updateSignature(signature);
        if (!dirtySlices.isEmpty()) {
            state.cache.markDirty(dirtySlices);
        }

        for (GoalDefinition definition : GoalRegistry.all()) {
            if (!definition.isCompatible(capabilities)) {
                state.cache.remove(definition.id());
                continue;
            }

            EvaluationCadence cadence = cadenceFor(definition);
            EnumSet<ContextSlice> dependencies = dependenciesFor(definition);
            GoalEvaluationCache.Entry entry = state.cache.entryFor(
                definition,
                dependencies,
                cadence,
                state.jitterSeed()
            );

            boolean active = isActiveGoal(ctx, definition);
            boolean forced = forceImmediate && cadence.isRealTime();

            if (entry.shouldEvaluate(tick, active, forced)) {
                evaluateGoal(state, ctx, definition, tick, entry);
            }
        }
    }

    private void evaluateGoal(
        EvaluationState state,
        PetContext ctx,
        GoalDefinition definition,
        long tick,
        GoalEvaluationCache.Entry entry
    ) {
        AggregatedSignalResult desirability = aggregateDesirability(ctx, definition);
        if (desirability.appliedValue() <= 0.0f) {
            entry.invalidate(tick);
            state.cache.onEntryInvalidated(entry);
            return;
        }

        AggregatedSignalResult feasibility = aggregateFeasibility(ctx, definition);
        if (feasibility.appliedValue() <= 0.0f) {
            entry.invalidate(tick);
            state.cache.onEntryInvalidated(entry);
            return;
        }

        String reason = explainSuggestion(
            definition,
            ctx,
            desirability.appliedValue(),
            feasibility.appliedValue()
        );
        Suggestion suggestion = new Suggestion(
            definition,
            desirability.appliedValue(),
            feasibility.appliedValue(),
            reason
        );
        entry.update(suggestion, tick);
        state.cache.onEntryUpdated(entry);
    }

    private AggregatedSignalResult aggregateDesirability(PetContext ctx, GoalDefinition definition) {
        float rawValue = 1.0f;
        float appliedValue = 1.0f;
        for (DesirabilitySignal signal : DesirabilitySignalRegistry.all()) {
            SignalResult result = signal.evaluate(definition, ctx);
            rawValue *= result.rawValue();
            appliedValue *= result.appliedValue();
            if (appliedValue <= 0.0f) {
                break;
            }
        }
        float clamped = MathHelper.clamp(appliedValue, MIN_MULTIPLIER, MAX_DESIRABILITY);
        return new AggregatedSignalResult(rawValue, clamped);
    }

    private AggregatedSignalResult aggregateFeasibility(PetContext ctx, GoalDefinition definition) {
        float rawValue = 1.0f;
        float appliedValue = 1.0f;
        for (FeasibilitySignal signal : FeasibilitySignalRegistry.all()) {
            SignalResult result = signal.evaluate(definition, ctx);
            rawValue *= result.rawValue();
            appliedValue *= result.appliedValue();
            if (appliedValue <= 0.0f) {
                break;
            }
        }
        float clamped = MathHelper.clamp(appliedValue, MIN_MULTIPLIER, MAX_FEASIBILITY);
        return new AggregatedSignalResult(rawValue, clamped);
    }

    private EvaluationState stateFor(MobEntity mob) {
        synchronized (states) {
            return states.computeIfAbsent(mob, key -> new EvaluationState(key));
        }
    }

    private EnumSet<ContextSlice> dependenciesFor(GoalDefinition definition) {
        return dependencyCache.computeIfAbsent(definition.id(), id -> computeDependencies(definition));
    }

    private EnumSet<ContextSlice> computeDependencies(GoalDefinition definition) {
        EnumSet<ContextSlice> slices = EnumSet.noneOf(ContextSlice.class);
        for (DesirabilitySignal signal : DesirabilitySignalRegistry.all()) {
            slices.addAll(signal.observedSlices(definition));
            if (slices.contains(ContextSlice.ALL)) {
                return EnumSet.of(ContextSlice.ALL);
            }
        }
        for (FeasibilitySignal signal : FeasibilitySignalRegistry.all()) {
            slices.addAll(signal.observedSlices(definition));
            if (slices.contains(ContextSlice.ALL)) {
                return EnumSet.of(ContextSlice.ALL);
            }
        }
        if (slices.isEmpty()) {
            slices.add(ContextSlice.ALL);
        }
        return EnumSet.copyOf(slices);
    }

    private static EvaluationCadence cadenceFor(GoalDefinition definition) {
        if (definition.marksMajorActivity()) {
            return EvaluationCadence.REAL_TIME;
        }
        return switch (definition.category()) {
            case SPECIAL -> EvaluationCadence.REAL_TIME;
            case SOCIAL, PLAY, WANDER -> EvaluationCadence.FREQUENT;
            case IDLE_QUIRK -> EvaluationCadence.AMBIENT;
        };
    }

    private static boolean isActiveGoal(PetContext ctx, GoalDefinition definition) {
        Identifier active = ctx.activeAdaptiveGoalId();
        return active != null && active.equals(definition.id());
    }

    private String explainSuggestion(GoalDefinition goalType, PetContext ctx, float desirability, float feasibility) {
        StringBuilder reason = new StringBuilder();
        reason.append(goalType.id()).append(" (");

        if (ctx.hasPetsPlusComponent() && ctx.currentMood() != null) {
            reason.append("Mood: ").append(ctx.currentMood().name()).append(", ");
        }

        reason.append("Age: ").append(ctx.getAgeCategory()).append(", ");
        reason.append("Desire: ").append(String.format("%.2f", desirability)).append(", ");
        reason.append("Feasible: ").append(String.format("%.2f", feasibility));
        reason.append(")");

        return reason.toString();
    }

    private static int quantize(float value) {
        if (Float.isInfinite(value) || Float.isNaN(value)) {
            return 0;
        }
        return Math.round(value * 100.0f);
    }

    private static int quantize(double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            return 0;
        }
        return (int) Math.round(value * 100.0d);
    }

    private static final class EvaluationState {
        private final GoalEvaluationCache cache = new GoalEvaluationCache();
        private final long jitterSeed;
        private ContextSignature lastSignature;

        private EvaluationState(MobEntity mob) {
            this.jitterSeed = computeJitterSeed(mob);
        }

        long jitterSeed() {
            return jitterSeed;
        }

        EnumSet<ContextSlice> updateSignature(ContextSignature signature) {
            if (signature == null) {
                return EnumSet.noneOf(ContextSlice.class);
            }
            if (lastSignature == null) {
                lastSignature = signature;
                return EnumSet.of(ContextSlice.ALL);
            }
            EnumSet<ContextSlice> diff = signature.diff(lastSignature);
            lastSignature = signature;
            return diff;
        }
        private static long computeJitterSeed(MobEntity mob) {
            if (mob == null) {
                return 0L;
            }
            try {
                return mob.getUuid().getLeastSignificantBits() ^ mob.getUuid().getMostSignificantBits();
            } catch (Exception ignored) {
                return System.identityHashCode(mob);
            }
        }
    }

    private static final class GoalEvaluationCache {
        private final Map<Identifier, Entry> entries = new HashMap<>();
        private Entry bestEntry;

        Entry entryFor(
            GoalDefinition definition,
            EnumSet<ContextSlice> dependencies,
            EvaluationCadence cadence,
            long jitterSeed
        ) {
            Entry entry = entries.get(definition.id());
            if (entry == null) {
                entry = new Entry(definition, dependencies, cadence, jitterSeed);
                entries.put(definition.id(), entry);
            } else {
                entry.setDependencies(dependencies);
                entry.setCadence(cadence);
            }
            return entry;
        }

        void remove(Identifier id) {
            Entry removed = entries.remove(id);
            if (removed != null && removed == bestEntry) {
                bestEntry = null;
            }
        }

        void markDirty(EnumSet<ContextSlice> slices) {
            if (entries.isEmpty()) {
                return;
            }
            if (slices.contains(ContextSlice.ALL)) {
                for (Entry entry : entries.values()) {
                    entry.markDirty();
                }
                bestEntry = null;
                return;
            }
            for (Entry entry : entries.values()) {
                if (entry.dependsOnAny(slices)) {
                    entry.markDirty();
                }
            }
            if (bestEntry != null && bestEntry.isDirty()) {
                bestEntry = null;
            }
        }

        void onEntryUpdated(Entry entry) {
            if (!entry.isValid()) {
                onEntryInvalidated(entry);
                return;
            }
            if (entry == bestEntry) {
                if (entry.score() < entry.previousScore()) {
                    bestEntry = null;
                    bestEntry();
                }
                return;
            }
            if (bestEntry == null || entry.score() >= bestEntry.score()) {
                bestEntry = entry;
            }
        }

        void onEntryInvalidated(Entry entry) {
            if (bestEntry == entry) {
                bestEntry = null;
            }
        }

        Entry bestEntry() {
            if (bestEntry != null && bestEntry.isValid()) {
                return bestEntry;
            }
            bestEntry = null;
            for (Entry entry : entries.values()) {
                if (!entry.isValid()) {
                    continue;
                }
                if (bestEntry == null || entry.score() > bestEntry.score()) {
                    bestEntry = entry;
                }
            }
            return bestEntry;
        }

        List<Entry> validEntriesSorted() {
            if (entries.isEmpty()) {
                return List.of();
            }
            List<Entry> result = new ArrayList<>();
            for (Entry entry : entries.values()) {
                if (entry.isValid()) {
                    result.add(entry);
                }
            }
            result.sort((a, b) -> Float.compare(b.score(), a.score()));
            return result;
        }

        private static final class Entry {
            private final GoalDefinition definition;
            private final long entrySeed;
            private EnumSet<ContextSlice> dependencies;
            private EvaluationCadence cadence;
            private int cadenceJitter;
            private Suggestion suggestion;
            private float score;
            private float previousScore;
            private long expiresAtTick;
            private long nextEvaluationTick;
            private long lastEvaluatedTick;
            private boolean valid;
            private boolean dirty;

            Entry(
                GoalDefinition definition,
                EnumSet<ContextSlice> dependencies,
                EvaluationCadence cadence,
                long jitterSeed
            ) {
                this.definition = definition;
                this.entrySeed = computeEntrySeed(definition, jitterSeed);
                this.dependencies = copyDependencies(dependencies);
                this.cadence = cadence;
                this.cadenceJitter = cadence.jitterOffset(entrySeed);
                this.suggestion = null;
                this.score = 0.0f;
                this.previousScore = 0.0f;
                this.expiresAtTick = Long.MIN_VALUE;
                this.nextEvaluationTick = Long.MIN_VALUE;
                this.lastEvaluatedTick = Long.MIN_VALUE;
                this.valid = false;
                this.dirty = true;
            }

            void setDependencies(EnumSet<ContextSlice> dependencies) {
                EnumSet<ContextSlice> copy = copyDependencies(dependencies);
                if (!this.dependencies.equals(copy)) {
                    this.dependencies = copy;
                    this.dirty = true;
                }
            }

            void setCadence(EvaluationCadence cadence) {
                int newJitter = cadence.jitterOffset(entrySeed);
                if (this.cadence != cadence || this.cadenceJitter != newJitter) {
                    this.cadence = cadence;
                    this.cadenceJitter = newJitter;
                    this.dirty = true;
                }
            }

            boolean shouldEvaluate(long tick, boolean active, boolean forceImmediate) {
                if (forceImmediate && cadence.isRealTime() && tick > lastEvaluatedTick) {
                    return true;
                }
                if (dirty) {
                    return true;
                }
                if (!valid) {
                    if (active && tick > lastEvaluatedTick) {
                        return true;
                    }
                    return tick >= nextEvaluationTick;
                }
                if (active && tick > lastEvaluatedTick) {
                    return true;
                }
                if (tick >= expiresAtTick) {
                    return true;
                }
                return tick >= nextEvaluationTick;
            }

            void update(Suggestion suggestion, long tick) {
                this.suggestion = suggestion;
                this.previousScore = this.score;
                this.score = suggestion.score();
                this.lastEvaluatedTick = tick;
                this.expiresAtTick = cadence.expiryTick(tick);
                this.nextEvaluationTick = cadence.nextTick(tick, cadenceJitter);
                this.valid = true;
                this.dirty = false;
            }

            void invalidate(long tick) {
                this.suggestion = null;
                this.previousScore = this.score;
                this.score = 0.0f;
                this.lastEvaluatedTick = tick;
                this.expiresAtTick = cadence.expiryTick(tick);
                this.nextEvaluationTick = cadence.guardTick(tick);
                this.valid = false;
                this.dirty = false;
            }

            void markDirty() {
                this.dirty = true;
            }

            boolean dependsOnAny(EnumSet<ContextSlice> slices) {
                if (dependencies.contains(ContextSlice.ALL)) {
                    return true;
                }
                for (ContextSlice slice : slices) {
                    if (dependencies.contains(slice)) {
                        return true;
                    }
                }
                return false;
            }

            boolean isValid() {
                return valid;
            }

            boolean isDirty() {
                return dirty;
            }

            Suggestion suggestion() {
                return suggestion;
            }

            float score() {
                return score;
            }

            float previousScore() {
                return previousScore;
            }

            private static long computeEntrySeed(GoalDefinition definition, long jitterSeed) {
                long base = definition.id().hashCode();
                return base ^ jitterSeed;
            }

            private static EnumSet<ContextSlice> copyDependencies(EnumSet<ContextSlice> dependencies) {
                if (dependencies == null || dependencies.isEmpty()) {
                    return EnumSet.noneOf(ContextSlice.class);
                }
                return EnumSet.copyOf(dependencies);
            }
        }
    }

    private record AggregatedSignalResult(float rawValue, float appliedValue) { }

    private enum EvaluationCadence {
        REAL_TIME(1, 3, 1, 0),
        FREQUENT(5, 8, 3, 2),
        AMBIENT(20, 26, 7, 8);

        private final int intervalTicks;
        private final int ttlTicks;
        private final int guardTicks;
        private final int jitterRange;

        EvaluationCadence(int intervalTicks, int ttlTicks, int guardTicks, int jitterRange) {
            this.intervalTicks = intervalTicks;
            this.ttlTicks = ttlTicks;
            this.guardTicks = guardTicks;
            this.jitterRange = Math.max(0, jitterRange);
        }

        long nextTick(long tick, int jitterOffset) {
            int interval = Math.max(1, intervalTicks);
            int offset = Math.max(0, jitterOffset);
            return tick + interval + offset;
        }

        long expiryTick(long tick) {
            return tick + Math.max(1, ttlTicks);
        }

        long guardTick(long tick) {
            return tick + Math.max(1, guardTicks);
        }

        boolean isRealTime() {
            return this == REAL_TIME;
        }

        int jitterOffset(long seed) {
            if (jitterRange == 0) {
                return 0;
            }
            long scrambled = seed * 1103515245L + 12345L;
            int mod = jitterRange + 1;
            return (int) Math.floorMod(scrambled, mod);
        }
    }

    private record ContextSignature(
        int ownerHash,
        int crowdHash,
        int aggregateHash,
        int environmentHash,
        int socialHash,
        int moodHash,
        int emotionsHash,
        int energyHash,
        int historyHash,
        int stimuliHash,
        int stateHash,
        int worldHash,
        int lodHash
    ) {
        static ContextSignature capture(PetContext ctx) {
            return new ContextSignature(
                ownerHash(ctx),
                crowdHash(ctx),
                aggregateHash(ctx),
                environmentHash(ctx),
                socialHash(ctx),
                moodHash(ctx),
                emotionsHash(ctx),
                energyHash(ctx),
                historyHash(ctx),
                stimuliHash(ctx),
                stateHash(ctx),
                worldHash(ctx),
                lodHash(ctx)
            );
        }

        EnumSet<ContextSlice> diff(ContextSignature previous) {
            EnumSet<ContextSlice> slices = EnumSet.noneOf(ContextSlice.class);
            if (previous == null) {
                slices.add(ContextSlice.ALL);
                return slices;
            }
            if (ownerHash != previous.ownerHash) {
                slices.add(ContextSlice.OWNER);
            }
            if (crowdHash != previous.crowdHash) {
                slices.add(ContextSlice.CROWD);
            }
            if (aggregateHash != previous.aggregateHash) {
                slices.add(ContextSlice.AGGREGATES);
            }
            if (environmentHash != previous.environmentHash) {
                slices.add(ContextSlice.ENVIRONMENT);
            }
            if (socialHash != previous.socialHash) {
                slices.add(ContextSlice.SOCIAL);
            }
            if (moodHash != previous.moodHash) {
                slices.add(ContextSlice.MOOD);
            }
            if (emotionsHash != previous.emotionsHash) {
                slices.add(ContextSlice.EMOTIONS);
            }
            if (energyHash != previous.energyHash) {
                slices.add(ContextSlice.ENERGY);
            }
            if (historyHash != previous.historyHash) {
                slices.add(ContextSlice.HISTORY);
            }
            if (stimuliHash != previous.stimuliHash) {
                slices.add(ContextSlice.STIMULI);
            }
            if (stateHash != previous.stateHash) {
                slices.add(ContextSlice.STATE_DATA);
            }
            if (worldHash != previous.worldHash) {
                slices.add(ContextSlice.WORLD);
            }
            if (lodHash != previous.lodHash) {
                slices.add(ContextSlice.LOD);
            }
            return slices;
        }

        private static int ownerHash(PetContext ctx) {
            int hash = ctx.ownerNearby() ? 1 : 0;
            hash = 31 * hash + quantize(ctx.distanceToOwner());
            if (ctx.getOwner() != null && ctx.getOwner().getUuid() != null) {
                hash = 31 * hash + ctx.getOwner().getUuid().hashCode();
            }
            return hash;
        }

        private static int crowdHash(PetContext ctx) {
            PetContextCrowdSummary summary = ctx.crowdSummary();
            if (summary == null) {
                return 0;
            }
            int hash = summary.friendlyCount();
            hash = 31 * hash + summary.hostileCount();
            hash = 31 * hash + summary.neutralCount();
            hash = 31 * hash + quantize(summary.nearestFriendlyDistance());
            hash = 31 * hash + quantize(summary.nearestHostileDistance());
            return hash;
        }

        private static int aggregateHash(PetContext ctx) {
            PetContextCrowdSummary summary = ctx.crowdSummary();
            int hash = summary != null ? summary.hashCode() : 0;
            hash = 31 * hash + (ctx.mobInteractionProfile() != null ? ctx.mobInteractionProfile().hashCode() : 0);
            return hash;
        }

        private static int environmentHash(PetContext ctx) {
            int hash = ctx.isDaytime() ? 1 : 0;
            BlockPos pos = ctx.currentPos();
            if (pos != null) {
                hash = 31 * hash + Long.hashCode(pos.asLong());
            }
            hash = 31 * hash + (ctx.behaviouralEnergyProfile() != null ? quantize(ctx.behaviouralEnergyProfile().momentum()) : 0);
            return hash;
        }

        private static int socialHash(PetContext ctx) {
            return ctx.socialSnapshot() != null ? ctx.socialSnapshot().edges().hashCode() : 0;
        }

        private static int moodHash(PetContext ctx) {
            int hash = ctx.currentMood() != null ? ctx.currentMood().ordinal() : -1;
            hash = 31 * hash + ctx.moodLevel();
            if (ctx.moodBlend() != null) {
                hash = 31 * hash + ctx.moodBlend().hashCode();
            }
            return hash;
        }

        private static int emotionsHash(PetContext ctx) {
            return ctx.activeEmotions() != null ? ctx.activeEmotions().hashCode() : 0;
        }

        private static int energyHash(PetContext ctx) {
            BehaviouralEnergyProfile profile = ctx.behaviouralEnergyProfile();
            if (profile == null) {
                return 0;
            }
            int hash = quantize(profile.momentum());
            hash = 31 * hash + quantize(profile.physicalStamina());
            hash = 31 * hash + quantize(profile.socialCharge());
            hash = 31 * hash + quantize(profile.mentalFocus());
            return hash;
        }

        private static int historyHash(PetContext ctx) {
            int hash = ctx.recentGoals().hashCode();
            hash = 31 * hash + ctx.lastExecuted().hashCode();
            hash = 31 * hash + ctx.quirkCounters().hashCode();
            return hash;
        }

        private static int stimuliHash(PetContext ctx) {
            StimulusSnapshot snapshot = ctx.stimuli();
            if (snapshot == null || snapshot.isEmpty()) {
                return 0;
            }
            int hash = snapshot.events().size();
            for (StimulusSnapshot.Event event : snapshot.events()) {
                hash = 31 * hash + event.type().hashCode();
                hash = 31 * hash + (int) (event.tick() & 0xFFFF);
                hash = 31 * hash + event.slices().hashCode();
            }
            return hash;
        }

        private static int stateHash(PetContext ctx) {
            int hash = ctx.role() != null ? ctx.role().hashCode() : 0;
            hash = 31 * hash + (ctx.natureId() != null ? ctx.natureId().hashCode() : 0);
            hash = 31 * hash + ctx.level();
            hash = 31 * hash + (int) (ctx.ticksAlive() & 0x7FFF);
            return hash;
        }

        private static int worldHash(PetContext ctx) {
            long time = ctx.worldTime();
            long bucket = time >> 2; // group ticks into 4-tick windows to avoid per-tick invalidation
            int hash = (int) (bucket & 0xFFFF);
            hash = 31 * hash + (ctx.isDaytime() ? 1 : 0);
            return hash;
        }

        private static int lodHash(PetContext ctx) {
            return ctx.dormant() ? 1 : 0;
        }
    }
}
