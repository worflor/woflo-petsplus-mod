package woflo.petsplus.state.morality;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.modules.RelationshipModule;
import woflo.petsplus.state.relationships.RelationshipProfile;
import woflo.petsplus.state.relationships.RelationshipType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Per-pet morality tracker that accumulates owner "dark deed" telemetry and raises
 * the malevolence emotion when the configured rule thresholds are met.
 */
public final class MalevolenceLedger {
    private final PetComponent parent;

    private float score;
    private int spreeCount;
    private long spreeAnchorTick = Long.MIN_VALUE;
    private long lastDeedTick = Long.MIN_VALUE;
    private long lastTriggerTick = Long.MIN_VALUE;
    private float disharmonyStrength;
    private boolean active;
    private long lastContextTick = Long.MIN_VALUE;
    private int lastContextHash;

    public MalevolenceLedger(PetComponent parent) {
        this.parent = parent;
        this.score = parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_SCORE, Float.class, 0f);
        this.spreeCount = parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_SPREE_COUNT, Integer.class, 0);
        this.spreeAnchorTick = parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_SPREE_ANCHOR_TICK, Long.class, Long.MIN_VALUE);
        this.lastDeedTick = parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_LAST_DEED_TICK, Long.class, Long.MIN_VALUE);
        this.lastTriggerTick = parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_LAST_TRIGGER_TICK, Long.class, Long.MIN_VALUE);
        this.disharmonyStrength = parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_DISHARMONY, Float.class, 0f);
        this.active = parent.getStateData(PetComponent.StateKeys.MALEVOLENCE_ACTIVE, Boolean.class, Boolean.FALSE);
    }

    public synchronized TriggerOutcome recordDarkDeed(DarkDeedContext context, long now) {
        UUID ownerUuid = parent.getOwnerUuid();
        if (ownerUuid == null) {
            return TriggerOutcome.none();
        }

        MalevolenceRules rules = MalevolenceRulesRegistry.get();
        if (rules == null || rules == MalevolenceRules.EMPTY) {
            return TriggerOutcome.none();
        }

        int contextHash = hashContext(context);
        if (now == lastContextTick && contextHash == lastContextHash) {
            return TriggerOutcome.none();
        }

        decayScore(now, rules.thresholds());
        updateSpree(now, rules.spreeSettings());

        Set<String> resolvedTags = rules.resolveTags(context.victimType(), context.registryTags(), context.dynamicTags());
        float baseWeight = rules.baseWeight(context.victimType(), resolvedTags);
        if (baseWeight <= 0f) {
            lastContextTick = now;
            lastContextHash = contextHash;
            maybeClear(ownerUuid, rules, now);
            persistState();
            return TriggerOutcome.none();
        }

        RelationshipModule relationshipModule = parent.getRelationshipModule();
        RelationshipProfile relationshipProfile = null;
        if (relationshipModule != null && context.victimUuid() != null) {
            relationshipProfile = relationshipModule.getRelationship(context.victimUuid());
        }
        RelationshipType relationshipType = relationshipProfile != null
            ? relationshipProfile.getType()
            : context.relationshipHint();

        float relationshipMultiplier = rules.relationshipMultiplier(relationshipType, resolvedTags);
        MalevolenceRules.InteractionVector interactionVector = MalevolenceRules.InteractionVector.of(context.interactionVector());
        float telemetryMultiplier = rules.telemetryMultiplier(relationshipProfile, interactionVector,
            context.friendlyFireSeverity(), resolvedTags);
        float spreeMultiplier = rules.spreeMultiplier(spreeCount, context.lowHealthFinish(), resolvedTags);

        float addedScore = baseWeight * relationshipMultiplier * telemetryMultiplier * spreeMultiplier;
        if (addedScore <= 0f || Float.isNaN(addedScore) || Float.isInfinite(addedScore)) {
            lastContextTick = now;
            lastContextHash = contextHash;
            maybeClear(ownerUuid, rules, now);
            persistState();
            return TriggerOutcome.none();
        }

        score += addedScore;
        score = Math.max(0f, score);
        lastDeedTick = now;
        lastContextTick = now;
        lastContextHash = contextHash;

        ThresholdEvaluation evaluation = evaluateThreshold(rules.thresholds(), now);
        DisharmonyEvaluation disharmonyEvaluation = updateDisharmony(ownerUuid, rules, evaluation, now);
        persistState();
        return TriggerOutcome.of(evaluation, disharmonyEvaluation);
    }

    private void decayScore(long now, MalevolenceRules.Thresholds thresholds) {
        if (score <= 0f || lastDeedTick == Long.MIN_VALUE) {
            return;
        }
        float halfLife = Math.max(1f, thresholds.decayHalfLifeTicks());
        long elapsed = Math.max(0L, now - lastDeedTick);
        if (elapsed == 0L) {
            return;
        }
        double decayFactor = Math.pow(0.5d, elapsed / halfLife);
        score *= (float) decayFactor;
        if (score < 0.05f) {
            score = 0f;
        }
    }

    private void updateSpree(long now, MalevolenceRules.SpreeSettings spreeSettings) {
        if (spreeAnchorTick == Long.MIN_VALUE || now - spreeAnchorTick > spreeSettings.windowTicks()) {
            spreeCount = 0;
            spreeAnchorTick = now;
        }
        spreeCount = Math.min(Integer.MAX_VALUE - 1, spreeCount + 1);
    }

    private ThresholdEvaluation evaluateThreshold(MalevolenceRules.Thresholds thresholds, long now) {
        boolean cooledDown = lastTriggerTick == Long.MIN_VALUE || now - lastTriggerTick >= thresholds.cooldownTicks();
        if (score >= thresholds.triggerScore() && cooledDown) {
            float intensity = thresholds.intensityForScore(score);
            lastTriggerTick = now;
            active = true;
            return ThresholdEvaluation.triggeredResult(intensity);
        }
        boolean clearable = active && score <= thresholds.remissionScore();
        if (clearable) {
            active = false;
            score = Math.max(0f, score);
            return ThresholdEvaluation.clearedResult();
        }
        return ThresholdEvaluation.none();
    }

    private DisharmonyEvaluation updateDisharmony(UUID ownerUuid, MalevolenceRules rules,
                                                  ThresholdEvaluation evaluation, long now) {
        MalevolenceRules.DisharmonySettings settings = rules.disharmonySettings();
        float previous = disharmonyStrength;
        if (evaluation.triggered()) {
            float candidate = settings.resolveStrength(evaluation.intensity());
            disharmonyStrength = Math.max(disharmonyStrength, candidate);
        } else if (evaluation.cleared()) {
            disharmonyStrength = 0f;
        }
        if (!evaluation.triggered() && !evaluation.cleared() && !active) {
            if (disharmonyStrength > 0f && disharmonyStrength <= settings.remissionFloor()) {
                disharmonyStrength = 0f;
            }
        }
        applyHarmony(ownerUuid, disharmonyStrength, now, rules);
        return new DisharmonyEvaluation(previous, disharmonyStrength, evaluation.cleared());
    }

    private void maybeClear(UUID ownerUuid, MalevolenceRules rules, long now) {
        if (!active) {
            return;
        }
        MalevolenceRules.Thresholds thresholds = rules.thresholds();
        decayScore(now, thresholds);
        if (score <= thresholds.remissionScore()) {
            active = false;
            disharmonyStrength = 0f;
            applyHarmony(ownerUuid, 0f, now, rules);
        }
    }

    private void applyHarmony(UUID ownerUuid, float strength, long tick, MalevolenceRules rules) {
        PetComponent.HarmonyState current = parent.getHarmonyState();
        PetComponent.HarmonyState overlay = overlayHarmonyState(current, tick);
        if (overlay != current) {
            parent.applyHarmonyState(overlay);
        }
    }

    public synchronized PetComponent.HarmonyState overlayHarmonyState(PetComponent.HarmonyState incoming, long now) {
        if (incoming == null) {
            return PetComponent.HarmonyState.empty();
        }
        UUID ownerUuid = parent.getOwnerUuid();
        if (ownerUuid == null) {
            return incoming;
        }
        MalevolenceRules rules = MalevolenceRulesRegistry.get();
        if (rules == null || rules == MalevolenceRules.EMPTY) {
            return incoming;
        }
        Map<UUID, PetComponent.HarmonyCompatibility> compat = incoming.compatibilities();
        PetComponent.HarmonyCompatibility existing = compat.get(ownerUuid);
        float clampedStrength = MathHelper.clamp(disharmonyStrength, 0f, rules.disharmonySettings().maxStrength());
        if (clampedStrength <= 0f) {
            if (existing == null || existing.disharmonyStrength() <= 0f) {
                return incoming;
            }
            Map<UUID, PetComponent.HarmonyCompatibility> copy = new LinkedHashMap<>(compat);
            copy.remove(ownerUuid);
            float disharmonyTotal = 0f;
            for (PetComponent.HarmonyCompatibility value : copy.values()) {
                if (value != null) {
                    disharmonyTotal = Math.max(disharmonyTotal, value.disharmonyStrength());
                }
            }
            List<Identifier> disharmonySets = new ArrayList<>(incoming.disharmonySetIds());
            Identifier marker = rules.disharmonySetId();
            List<Identifier> sanitizedDisharmony;
            if (disharmonySets.remove(marker)) {
                sanitizedDisharmony = disharmonySets.isEmpty() ? List.of() : List.copyOf(disharmonySets);
            } else {
                sanitizedDisharmony = incoming.disharmonySetIds();
            }
            return new PetComponent.HarmonyState(
                incoming.harmonySetIds(),
                sanitizedDisharmony,
                incoming.moodMultiplier(),
                incoming.contagionMultiplier(),
                incoming.volatilityMultiplier(),
                incoming.resilienceMultiplier(),
                incoming.guardMultiplier(),
                incoming.harmonyStrength(),
                disharmonyTotal,
                incoming.emotionBiases(),
                copy,
                Math.max(incoming.lastUpdatedTick(), now)
            );
        }
        float maxStrength = rules.disharmonySettings().maxStrength();
        float resolvedStrength = MathHelper.clamp(existing != null
            ? Math.max(existing.disharmonyStrength(), clampedStrength)
            : clampedStrength, 0f, maxStrength);
        Identifier marker = rules.disharmonySetId();
        boolean compatSatisfied = existing != null
            && existing.disharmonyStrength() >= resolvedStrength - 0.0001f
            && existing.disharmonySetIds().contains(marker);
        boolean globalSatisfied = incoming.disharmonyStrength() >= resolvedStrength - 0.0001f
            && incoming.disharmonySetIds().contains(marker);
        if (compatSatisfied && globalSatisfied) {
            return incoming;
        }
        Map<UUID, PetComponent.HarmonyCompatibility> copy = new LinkedHashMap<>(compat);
        List<Identifier> harmonySets = existing != null ? existing.harmonySetIds() : List.of();
        List<Identifier> disharmonySets = new ArrayList<>(existing != null ? existing.disharmonySetIds() : List.of());
        if (!disharmonySets.contains(marker)) {
            disharmonySets.add(marker);
        }
        PetComponent.HarmonyCompatibility updatedCompat = new PetComponent.HarmonyCompatibility(
            ownerUuid,
            harmonySets,
            disharmonySets,
            existing != null ? existing.harmonyStrength() : 0f,
            resolvedStrength,
            Math.max(incoming.lastUpdatedTick(), now)
        );
        copy.put(ownerUuid, updatedCompat);
        List<Identifier> globalSets = new ArrayList<>(incoming.disharmonySetIds());
        if (!globalSets.contains(marker)) {
            globalSets.add(marker);
        }
        float disharmonyTotal = resolvedStrength;
        for (PetComponent.HarmonyCompatibility value : copy.values()) {
            if (value != null) {
                disharmonyTotal = Math.max(disharmonyTotal, value.disharmonyStrength());
            }
        }
        return new PetComponent.HarmonyState(
            incoming.harmonySetIds(),
            globalSets,
            incoming.moodMultiplier(),
            incoming.contagionMultiplier(),
            incoming.volatilityMultiplier(),
            incoming.resilienceMultiplier(),
            incoming.guardMultiplier(),
            incoming.harmonyStrength(),
            disharmonyTotal,
            incoming.emotionBiases(),
            copy,
            Math.max(incoming.lastUpdatedTick(), now)
        );
    }

    private static int hashContext(DarkDeedContext context) {
        if (context == null) {
            return 0;
        }
        int severityKey = MathHelper.floor(context.friendlyFireSeverity() * 100f);
        return Objects.hash(context.victimUuid(), context.registryTags(), context.dynamicTags(), severityKey,
            context.lowHealthFinish(), context.relationshipHint());
    }

    private void persistState() {
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_SCORE, score);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_LAST_DEED_TICK, lastDeedTick);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_LAST_TRIGGER_TICK, lastTriggerTick);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_SPREE_COUNT, spreeCount);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_SPREE_ANCHOR_TICK, spreeAnchorTick);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_DISHARMONY, disharmonyStrength);
        parent.setStateData(PetComponent.StateKeys.MALEVOLENCE_ACTIVE, active);
    }

    public record TriggerOutcome(boolean triggered, float intensity, boolean cleared, float disharmonyStrength) {
        private static final TriggerOutcome NONE = new TriggerOutcome(false, 0f, false, 0f);

        public static TriggerOutcome none() {
            return NONE;
        }

        public static TriggerOutcome triggeredOutcome(float intensity, float disharmonyStrength) {
            return new TriggerOutcome(true, intensity, false, disharmonyStrength);
        }

        public static TriggerOutcome clearedOutcome() {
            return new TriggerOutcome(false, 0f, true, 0f);
        }

        private static TriggerOutcome of(ThresholdEvaluation evaluation, DisharmonyEvaluation disharmony) {
            if (evaluation.triggered()) {
                return triggeredOutcome(evaluation.intensity(), disharmony.current());
            }
            if (evaluation.cleared()) {
                return clearedOutcome();
            }
            return none();
        }
    }

    private record ThresholdEvaluation(boolean triggered, float intensity, boolean cleared) {
        private static final ThresholdEvaluation NONE = new ThresholdEvaluation(false, 0f, false);

        static ThresholdEvaluation triggeredResult(float intensity) {
            return new ThresholdEvaluation(true, intensity, false);
        }

        static ThresholdEvaluation clearedResult() {
            return new ThresholdEvaluation(false, 0f, true);
        }

        static ThresholdEvaluation none() {
            return NONE;
        }
    }

    private record DisharmonyEvaluation(float previous, float current, boolean cleared) {}

    public record DarkDeedContext(
        @Nullable net.minecraft.entity.EntityType<?> victimType,
        @Nullable UUID victimUuid,
        Set<Identifier> registryTags,
        Set<String> dynamicTags,
        @Nullable woflo.petsplus.state.relationships.InteractionType.DimensionalResult interactionVector,
        float friendlyFireSeverity,
        boolean lowHealthFinish,
        RelationshipType relationshipHint
    ) {
        public DarkDeedContext {
            registryTags = registryTags == null ? Set.of() : Set.copyOf(registryTags);
            dynamicTags = dynamicTags == null ? Set.of() : Set.copyOf(dynamicTags);
            friendlyFireSeverity = MathHelper.clamp(friendlyFireSeverity, 0f, 4f);
            relationshipHint = relationshipHint == null ? RelationshipType.NEUTRAL : relationshipHint;
        }
    }
}
