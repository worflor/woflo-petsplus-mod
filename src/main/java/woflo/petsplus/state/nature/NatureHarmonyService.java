package woflo.petsplus.state.nature;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.stats.nature.harmony.NatureHarmonyRegistry;
import woflo.petsplus.stats.nature.harmony.NatureHarmonySet;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves active harmony/disharmony sets for pets using the owner-scoped swarm snapshots.
 */
public final class NatureHarmonyService implements PetSwarmIndex.SwarmListener {

    private static final float ACTIVATION_ALPHA = 0.42f;
    private static final float DECAY_ALPHA = 0.18f;
    private static final float STATE_ALPHA = 0.35f;
    private static final float LINGER_FALLOFF = 0.62f;

    private final PetSwarmIndex swarmIndex;
    private final Map<MobEntity, Participant> participants = new IdentityHashMap<>();
    private final Map<UUID, Set<Participant>> ownerParticipants = new HashMap<>();

    public NatureHarmonyService(PetSwarmIndex swarmIndex) {
        this.swarmIndex = Objects.requireNonNull(swarmIndex, "swarmIndex");
    }

    public void track(MobEntity pet, PetComponent component) {
        if (pet == null || component == null) {
            return;
        }
        Participant participant = participants.computeIfAbsent(pet, key -> new Participant(this, pet, component));
        participant.updateComponent(component);
    }

    public void untrack(MobEntity pet) {
        if (pet == null) {
            return;
        }
        Participant participant = participants.remove(pet);
        if (participant != null) {
            participant.forceClear();
        }
    }

    public void clear() {
        for (Participant participant : participants.values()) {
            participant.forceClear();
        }
        participants.clear();
        ownerParticipants.clear();
    }

    @Override
    public void onSwarmUpdated(@Nullable UUID ownerId, List<PetSwarmIndex.SwarmEntry> entries) {
        if (entries == null) {
            return;
        }
        if (entries.isEmpty()) {
            if (ownerId == null) {
                return;
            }
            List<Participant> owned = snapshotParticipants(ownerId);
            if (owned.isEmpty()) {
                return;
            }
            long tick = resolveParticipantTick(owned);
            for (Participant participant : owned) {
                participant.reconcile(List.of(), tick);
            }
            return;
        }

        long tick = resolveTick(entries);
        Map<Identifier, List<OwnerSnapshot>> byNature = new HashMap<>();
        List<OwnerSnapshot> snapshots = new ArrayList<>(entries.size());

        for (PetSwarmIndex.SwarmEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            PetComponent component = entry.component();
            MobEntity pet = entry.pet();
            if (component == null || pet == null) {
                continue;
            }
            Participant participant = participants.computeIfAbsent(pet, key -> new Participant(this, pet, component));
            participant.updateComponent(component);
            Identifier natureId = component.getNatureId();
            if (natureId == null) {
                participant.reconcile(List.of(), tick);
                continue;
            }
            Identifier signId = component.getAstrologySignId();
            OwnerSnapshot snapshot = new OwnerSnapshot(pet, component, natureId, signId, entry.x(), entry.y(), entry.z());
            snapshots.add(snapshot);
            byNature.computeIfAbsent(natureId, key -> new ArrayList<>()).add(snapshot);
        }

        for (OwnerSnapshot snapshot : snapshots) {
            Participant participant = participants.get(snapshot.pet());
            if (participant == null) {
                continue;
            }
            List<ActiveSet> matches = resolveMatches(snapshot, byNature);
            participant.reconcile(matches, tick);
        }
    }

    private static long resolveTick(List<PetSwarmIndex.SwarmEntry> entries) {
        for (PetSwarmIndex.SwarmEntry entry : entries) {
            if (entry == null) continue;
            MobEntity pet = entry.pet();
            if (pet != null && pet.getEntityWorld() instanceof ServerWorld world) {
                return world.getTime();
            }
        }
        return 0L;
    }

    private long resolveParticipantTick(List<Participant> participants) {
        for (Participant participant : participants) {
            if (participant == null) {
                continue;
            }
            PetComponent component = participant.component;
            long tick = resolveTick(component);
            if (tick != 0L) {
                return tick;
            }
        }
        return 0L;
    }

    private static long resolveTick(PetComponent component) {
        if (component == null) {
            return 0L;
        }
        if (component.getPetEntity().getEntityWorld() instanceof ServerWorld world) {
            return world.getTime();
        }
        return 0L;
    }

    private List<Participant> snapshotParticipants(UUID ownerId) {
        Set<Participant> set = ownerParticipants.get(ownerId);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        return List.copyOf(set);
    }

    private void updateOwnerIndex(Participant participant, @Nullable UUID ownerId) {
        UUID previous = participant.ownerId;
        if (Objects.equals(previous, ownerId)) {
            return;
        }
        if (previous != null) {
            Set<Participant> existing = ownerParticipants.get(previous);
            if (existing != null) {
                existing.remove(participant);
                if (existing.isEmpty()) {
                    ownerParticipants.remove(previous);
                }
            }
        }
        participant.ownerId = ownerId;
        if (ownerId != null) {
            ownerParticipants.computeIfAbsent(ownerId, key -> new LinkedHashSet<>()).add(participant);
        }
    }

    private List<ActiveSet> resolveMatches(OwnerSnapshot snapshot, Map<Identifier, List<OwnerSnapshot>> byNature) {
        List<NatureHarmonySet> candidates = NatureHarmonyRegistry.getSetsForNature(snapshot.nature());
        if (candidates.isEmpty()) {
            return List.of();
        }

        double maxRadius = 0.0d;
        for (NatureHarmonySet set : candidates) {
            maxRadius = Math.max(maxRadius, set.radius());
        }

        Map<Identifier, List<OwnerSnapshot>> crossOwner = maxRadius > 0.0d
            ? collectCrossOwnerSnapshots(snapshot, maxRadius)
            : Map.of();

        List<ActiveSet> matches = new ArrayList<>();
        for (NatureHarmonySet set : candidates) {
            ActiveSet active = matchSet(snapshot, set, byNature, crossOwner);
            if (active != null) {
                matches.add(active);
            }
        }
        return matches.isEmpty() ? List.of() : List.copyOf(matches);
    }

    @Nullable
    private static ActiveSet matchSet(OwnerSnapshot self, NatureHarmonySet set,
                                      Map<Identifier, List<OwnerSnapshot>> byNature,
                                      Map<Identifier, List<OwnerSnapshot>> crossOwner) {
        double radiusSq = set.radius() * set.radius();
        List<OwnerSnapshot> used = new ArrayList<>(set.members().size());
        used.add(self);
        double totalDistance = 0.0d;
        int contributing = 0;

        for (NatureHarmonySet.Member member : set.members()) {
            OwnerSnapshot chosen = selectCandidate(self, member,
                byNature.get(member.natureId()),
                crossOwner.get(member.natureId()),
                used, radiusSq);
            if (chosen == null) {
                return null;
            }
            if (!containsPet(used, chosen.pet())) {
                double distSq = distanceSquared(self, chosen);
                totalDistance += Math.sqrt(Math.max(0.0d, distSq));
                contributing++;
                used.add(chosen);
            }
        }

        if (contributing == 0) {
            return new ActiveSet(set, 1.0f);
        }
        double average = totalDistance / Math.max(1, contributing);
        double normalized = 1.0d - (average / set.radius());
        float intensity = MathHelper.clamp((float) normalized, 0.0f, 1.0f);
        return new ActiveSet(set, intensity);
    }

    private Map<Identifier, List<OwnerSnapshot>> collectCrossOwnerSnapshots(OwnerSnapshot self, double radius) {
        if (radius <= 0.0d) {
            return Map.of();
        }

        Vec3d center = new Vec3d(self.x(), self.y(), self.z());
        Map<Identifier, List<OwnerSnapshot>> byNature = new HashMap<>();
        swarmIndex.forEachPetInRange(center, radius, entry -> {
            if (entry == null) {
                return;
            }
            MobEntity pet = entry.pet();
            if (pet == null || pet == self.pet()) {
                return;
            }
            PetComponent component = entry.component();
            if (component == null) {
                return;
            }
            UUID ownerId = component.getOwnerUuid();
            if (ownerId != null && ownerId.equals(self.component().getOwnerUuid())) {
                return;
            }
            Identifier natureId = component.getNatureId();
            if (natureId == null) {
                return;
            }
            Identifier signId = component.getAstrologySignId();
            Participant participant = participants.computeIfAbsent(pet, key -> new Participant(this, pet, component));
            participant.updateComponent(component);
            OwnerSnapshot snapshot = new OwnerSnapshot(pet, component, natureId, signId, entry.x(), entry.y(), entry.z());
            byNature.computeIfAbsent(natureId, key -> new ArrayList<>()).add(snapshot);
        });
        if (byNature.isEmpty()) {
            return Map.of();
        }
        Map<Identifier, List<OwnerSnapshot>> immutable = new HashMap<>();
        for (Map.Entry<Identifier, List<OwnerSnapshot>> entry : byNature.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }

    @Nullable
    private static OwnerSnapshot selectCandidate(OwnerSnapshot self, NatureHarmonySet.Member requirement,
                                                 @Nullable List<OwnerSnapshot> ownerPool,
                                                 @Nullable List<OwnerSnapshot> crossOwnerPool,
                                                 List<OwnerSnapshot> used, double radiusSq) {
        if (ownerPool != null) {
            for (OwnerSnapshot candidate : ownerPool) {
                if (candidate.pet() == self.pet() && requirement.matches(candidate.nature(), candidate.sign())) {
                    return candidate;
                }
            }
        }
        if (crossOwnerPool != null) {
            for (OwnerSnapshot candidate : crossOwnerPool) {
                if (candidate.pet() == self.pet() && requirement.matches(candidate.nature(), candidate.sign())) {
                    return candidate;
                }
            }
        }

        OwnerSnapshot best = null;
        double bestDistSq = Double.MAX_VALUE;

        if (ownerPool != null) {
            for (OwnerSnapshot candidate : ownerPool) {
                if (!requirement.matches(candidate.nature(), candidate.sign())) {
                    continue;
                }
                if (containsPet(used, candidate.pet())) {
                    continue;
                }
                double distSq = distanceSquared(self, candidate);
                if (distSq <= radiusSq && distSq < bestDistSq) {
                    best = candidate;
                    bestDistSq = distSq;
                }
            }
        }

        if (crossOwnerPool != null) {
            for (OwnerSnapshot candidate : crossOwnerPool) {
                if (!requirement.matches(candidate.nature(), candidate.sign())) {
                    continue;
                }
                if (containsPet(used, candidate.pet())) {
                    continue;
                }
                double distSq = distanceSquared(self, candidate);
                if (distSq <= radiusSq && distSq < bestDistSq) {
                    best = candidate;
                    bestDistSq = distSq;
                }
            }
        }

        return best;
    }

    private static boolean containsPet(List<OwnerSnapshot> used, MobEntity pet) {
        for (OwnerSnapshot snapshot : used) {
            if (snapshot.pet() == pet) {
                return true;
            }
        }
        return false;
    }

    private static double distanceSquared(OwnerSnapshot a, OwnerSnapshot b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    private static final class Participant {
        private final NatureHarmonyService service;
        private final MobEntity pet;
        private PetComponent component;
        private final Map<Identifier, TimedSet> activeSets = new LinkedHashMap<>();
        private PetComponent.HarmonyState state = PetComponent.HarmonyState.empty();
        private UUID ownerId;

        private Participant(NatureHarmonyService service, MobEntity pet, PetComponent component) {
            this.service = service;
            this.pet = pet;
            this.component = null;
            updateComponent(component);
        }

        void updateComponent(PetComponent component) {
            this.component = component;
            UUID owner = component != null ? component.getOwnerUuid() : null;
            service.updateOwnerIndex(this, owner);
        }

        void reconcile(List<ActiveSet> matches, long tick) {
            Map<Identifier, ActiveSet> matched = new HashMap<>();
            for (ActiveSet set : matches) {
                matched.put(set.set().id(), set);
                TimedSet timed = activeSets.computeIfAbsent(set.set().id(), key -> new TimedSet(set.set()));
                timed.set = set.set();
                timed.expiryTick = tick + set.set().lingerTicks();
                timed.step(set.intensity(), true);
            }

            Iterator<Map.Entry<Identifier, TimedSet>> iterator = activeSets.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Identifier, TimedSet> entry = iterator.next();
                TimedSet timed = entry.getValue();
                if (matched.containsKey(entry.getKey())) {
                    continue;
                }
                float target = tick <= timed.expiryTick ? timed.intensity * LINGER_FALLOFF : 0.0f;
                timed.step(target, false);
                if (timed.intensity <= 0.01f && tick > timed.expiryTick) {
                    iterator.remove();
                }
            }

            applyAggregatedState(tick);
        }

        void forceClear() {
            service.updateOwnerIndex(this, null);
            activeSets.clear();
            state = PetComponent.HarmonyState.empty();
            pushState(state);
            component = null;
        }

        private void applyAggregatedState(long tick) {
            float mood = 1.0f;
            float contagion = 1.0f;
            float volatility = 1.0f;
            float resilience = 1.0f;
            float guard = 1.0f;
            float harmonyStrength = 0.0f;
            float disharmonyStrength = 0.0f;
            List<Identifier> harmonyIds = new ArrayList<>();
            List<Identifier> disharmonyIds = new ArrayList<>();
            EnumMap<PetComponent.Emotion, Float> emotionBiases = new EnumMap<>(PetComponent.Emotion.class);

            for (TimedSet timed : activeSets.values()) {
                if (timed.intensity <= 0.01f) {
                    continue;
                }
                NatureHarmonySet set = timed.set;
                float intensity = MathHelper.clamp(timed.intensity, 0.0f, 1.0f);
                mood *= MathHelper.lerp(intensity, 1.0f, set.moodScalar());
                contagion *= MathHelper.lerp(intensity, 1.0f, set.contagionScalar());
                volatility *= MathHelper.lerp(intensity, 1.0f, set.volatilityScalar());
                resilience *= MathHelper.lerp(intensity, 1.0f, set.resilienceScalar());
                guard *= MathHelper.lerp(intensity, 1.0f, set.guardScalar());
                HarmonyTagEffects.accumulate(set.tags(), set.type(), intensity, emotionBiases);
                if (set.type() == NatureHarmonySet.Type.HARMONY) {
                    harmonyStrength += intensity;
                    harmonyIds.add(set.id());
                } else {
                    disharmonyStrength += intensity;
                    disharmonyIds.add(set.id());
                }
            }

            Map<PetComponent.Emotion, Float> biasSnapshot = snapshotBiases(emotionBiases);

            PetComponent.HarmonyState target = new PetComponent.HarmonyState(
                List.copyOf(harmonyIds),
                List.copyOf(disharmonyIds),
                clampMultiplier(mood),
                clampMultiplier(contagion),
                clampMultiplier(volatility),
                clampMultiplier(resilience),
                clampMultiplier(guard),
                MathHelper.clamp(harmonyStrength, 0.0f, 3.0f),
                MathHelper.clamp(disharmonyStrength, 0.0f, 3.0f),
                biasSnapshot,
                tick
            );

            state = smooth(state, target);
            pushState(state);
        }

        private static float clampMultiplier(float value) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                return 1.0f;
            }
            return MathHelper.clamp(value, 0.2f, 2.5f);
        }

        private static PetComponent.HarmonyState smooth(PetComponent.HarmonyState previous, PetComponent.HarmonyState target) {
            if (previous == null || previous.isEmpty()) {
                return target;
            }
            float mood = lerp(previous.moodMultiplier(), target.moodMultiplier());
            float contagion = lerp(previous.contagionMultiplier(), target.contagionMultiplier());
            float volatility = lerp(previous.volatilityMultiplier(), target.volatilityMultiplier());
            float resilience = lerp(previous.resilienceMultiplier(), target.resilienceMultiplier());
            float guard = lerp(previous.guardMultiplier(), target.guardMultiplier());
            float harmony = lerp(previous.harmonyStrength(), target.harmonyStrength());
            float disharmony = lerp(previous.disharmonyStrength(), target.disharmonyStrength());
            Map<PetComponent.Emotion, Float> biases = blendEmotionBiases(previous.emotionBiases(), target.emotionBiases());
            return new PetComponent.HarmonyState(
                target.harmonySetIds(),
                target.disharmonySetIds(),
                mood,
                contagion,
                volatility,
                resilience,
                guard,
                harmony,
                disharmony,
                biases,
                target.lastUpdatedTick()
            );
        }

        private static float lerp(float previous, float target) {
            return MathHelper.lerp(STATE_ALPHA, previous, target);
        }

        private static Map<PetComponent.Emotion, Float> snapshotBiases(EnumMap<PetComponent.Emotion, Float> raw) {
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            EnumMap<PetComponent.Emotion, Float> copy = new EnumMap<>(PetComponent.Emotion.class);
            for (Map.Entry<PetComponent.Emotion, Float> entry : raw.entrySet()) {
                float value = entry.getValue();
                if (value <= 0f) {
                    continue;
                }
                copy.put(entry.getKey(), MathHelper.clamp(value, 0f, 0.85f));
            }
            if (copy.isEmpty()) {
                return Map.of();
            }
            return Map.copyOf(copy);
        }

        private static Map<PetComponent.Emotion, Float> blendEmotionBiases(Map<PetComponent.Emotion, Float> previous,
                                                                           Map<PetComponent.Emotion, Float> target) {
            if ((previous == null || previous.isEmpty()) && (target == null || target.isEmpty())) {
                return Map.of();
            }
            EnumMap<PetComponent.Emotion, Float> blended = new EnumMap<>(PetComponent.Emotion.class);
            if (previous != null) {
                for (Map.Entry<PetComponent.Emotion, Float> entry : previous.entrySet()) {
                    blended.put(entry.getKey(), MathHelper.clamp(entry.getValue(), 0f, 0.85f));
                }
            }
            if (target != null) {
                for (Map.Entry<PetComponent.Emotion, Float> entry : target.entrySet()) {
                    float prev = blended.getOrDefault(entry.getKey(), 0f);
                    float next = MathHelper.clamp(entry.getValue(), 0f, 0.85f);
                    blended.put(entry.getKey(), MathHelper.lerp(STATE_ALPHA, prev, next));
                }
            }
            blended.entrySet().removeIf(e -> e.getValue() <= 0.001f);
            return blended.isEmpty() ? Map.of() : Map.copyOf(blended);
        }

        private void pushState(PetComponent.HarmonyState state) {
            if (component == null) {
                return;
            }
            component.applyHarmonyState(state);
        }
    }

    private static final class TimedSet {
        private NatureHarmonySet set;
        private float intensity;
        private long expiryTick;

        private TimedSet(NatureHarmonySet set) {
            this.set = set;
            this.intensity = 0.0f;
            this.expiryTick = 0L;
        }

        private void step(float target, boolean activeNow) {
            float alpha = activeNow ? ACTIVATION_ALPHA : DECAY_ALPHA;
            float clamped = MathHelper.clamp(target, 0.0f, 1.0f);
            intensity = MathHelper.clamp(MathHelper.lerp(alpha, intensity, clamped), 0.0f, 1.0f);
        }
    }

    private record OwnerSnapshot(MobEntity pet, PetComponent component, Identifier nature, Identifier sign,
                                 double x, double y, double z) {
    }

    private record ActiveSet(NatureHarmonySet set, float intensity) {
    }
}
