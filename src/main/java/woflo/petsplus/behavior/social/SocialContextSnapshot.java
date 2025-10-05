package woflo.petsplus.behavior.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import woflo.petsplus.mood.EmotionStimulusBus;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.gossip.RumorEntry;

/**
 * Simple data carrier passed to social behaviour routines so they can pull the
 * information they need without touching the {@link PetComponent} state map
 * directly.
 */
public class SocialContextSnapshot {

    public static final double NEIGHBOR_SAMPLE_RADIUS = 12.0;
    public static final double PACK_SAMPLE_RADIUS = 8.0;
    public static final int MAX_PACK_SAMPLE = 8;

    private final MobEntity pet;
    private final PetComponent component;
    private final ServerPlayerEntity owner;
    public interface EmotionDispatcher {
        void push(PetComponent component, PetComponent.Emotion emotion, float amount);

        EmotionStimulusBus.SimpleStimulusCollector collectorFor(PetComponent component);
    }

    private final ServerWorld world;
    private final long currentTick;
    private final PetSocialData petData;
    private final Map<MobEntity, PetSocialData> petDataCache;
    private final EmotionDispatcher emotionDispatcher;

    private final NeighborSampleCache neighborSampleCache = new NeighborSampleCache();

    private int nearbyPetCount;
    private boolean hasOlderPet;
    private boolean hasYoungerPet;
    private boolean hasEldestPet;
    private boolean hasSimilarAge;
    private boolean hasNewbornPet;
    private float strongestBondResonance;
    private PetSocialData closestPetData;
    private double closestDistance = Double.MAX_VALUE;
    private boolean packContextComputed;

    private List<PetSocialData> moodNeighbors = Collections.emptyList();
    private List<NeighborSample> nearestNeighbors = Collections.emptyList();
    private List<NeighborSample> gossipNeighbors = Collections.emptyList();
    private List<RumorEntry> sharedRumors = Collections.emptyList();
    private List<List<NeighborSample>> gossipClusters = Collections.emptyList();
    private int gossipClusterCursor = 0;

    public SocialContextSnapshot(MobEntity pet, PetComponent component,
                                 ServerPlayerEntity owner, ServerWorld world,
                                 long currentTick, PetSocialData petData,
                                 Map<MobEntity, PetSocialData> petDataCache,
                                 EmotionDispatcher emotionDispatcher) {
        this.pet = Objects.requireNonNull(pet, "pet");
        this.component = Objects.requireNonNull(component, "component");
        this.owner = owner;
        this.world = world;
        this.currentTick = currentTick;
        this.petData = Objects.requireNonNull(petData, "petData");
        this.petDataCache = Objects.requireNonNull(petDataCache, "petDataCache");
        this.emotionDispatcher = emotionDispatcher;
    }

    public MobEntity pet() {
        return pet;
    }

    public PetComponent component() {
        return component;
    }

    public ServerPlayerEntity owner() {
        return owner;
    }

    public ServerWorld world() {
        return world;
    }

    public long currentTick() {
        return currentTick;
    }

    public PetSocialData petData() {
        return petData;
    }

    public PetSocialData getOrCreateNeighborData(PetSwarmIndex.SwarmEntry entry, long tick) {
        return petDataCache.computeIfAbsent(entry.pet(), key -> new PetSocialData(entry, tick));
    }

    public PetSocialData getOrCreateNeighborData(MobEntity neighbor, PetComponent neighborComponent, long tick) {
        return petDataCache.computeIfAbsent(neighbor, key -> new PetSocialData(neighbor, neighborComponent, tick));
    }

    public NeighborSummary ensureNeighborSample(PetSwarmIndex swarm) {
        return ensureNeighborSample(swarm, NEIGHBOR_SAMPLE_RADIUS, MAX_PACK_SAMPLE, PACK_SAMPLE_RADIUS);
    }

    public NeighborSummary ensureNeighborSample(PetSwarmIndex swarm, double sampleRadius,
                                                int maxSamples, double packRadius) {
        return neighborSampleCache.ensure(this, swarm, sampleRadius, maxSamples, packRadius);
    }

    public EmotionStimulusBus.SimpleStimulusCollector collectorFor(PetComponent target) {
        if (emotionDispatcher == null || target == null) {
            return (emotion, amount) -> {
                if (target != null) {
                    target.pushEmotion(emotion, amount);
                }
            };
        }
        return emotionDispatcher.collectorFor(target);
    }

    public void pushEmotion(PetComponent.Emotion emotion, float amount) {
        if (emotionDispatcher != null) {
            emotionDispatcher.push(component, emotion, amount);
        } else {
            component.pushEmotion(emotion, amount);
        }
    }

    public void pushEmotion(PetComponent target, PetComponent.Emotion emotion, float amount) {
        if (target == null) {
            return;
        }
        if (emotionDispatcher != null) {
            emotionDispatcher.push(target, emotion, amount);
        } else {
            target.pushEmotion(emotion, amount);
        }
    }

    public boolean isNeighborLookingAtSelf(PetSocialData neighbor, double toleranceDegrees) {
        return neighbor != null && neighbor.isFacingToward(petData, toleranceDegrees);
    }

    public boolean isSelfLookingAt(PetSocialData neighbor, double toleranceDegrees) {
        return neighbor != null && petData.isFacingToward(neighbor, toleranceDegrees);
    }

    public boolean areMutuallyFacing(PetSocialData neighbor, double toleranceDegrees) {
        return neighbor != null && petData.isFacingToward(neighbor, toleranceDegrees)
            && neighbor.isFacingToward(petData, toleranceDegrees);
    }

    public double relativeSpeedTo(PetSocialData neighbor) {
        return neighbor == null ? 0.0 : petData.relativeSpeedTo(neighbor);
    }

    public double headingAlignmentWith(PetSocialData neighbor) {
        return neighbor == null ? 0.0 : petData.headingAlignmentWith(neighbor);
    }

    public boolean isNeighborCalm(PetSocialData neighbor, double speedThreshold) {
        return neighbor != null && neighbor.speed() <= speedThreshold;
    }

    public boolean shareRecentCuddle(PetSocialData neighbor, long window) {
        if (neighbor == null) {
            return false;
        }
        long selfLast = petData.lastCrouchCuddleTick();
        long otherLast = neighbor.lastCrouchCuddleTick();
        if (selfLast <= 0 || otherLast <= 0) {
            return false;
        }
        long current = currentTick;
        return (current - selfLast) < window && (current - otherLast) < window;
    }

    public void setPackObservations(int nearbyPetCount, boolean hasOlderPet, boolean hasYoungerPet,
                                    boolean hasEldestPet, boolean hasSimilarAge, boolean hasNewbornPet,
                                    float strongestBondResonance, PetSocialData closestPetData,
                                    double closestDistance) {
        this.nearbyPetCount = nearbyPetCount;
        this.hasOlderPet = hasOlderPet;
        this.hasYoungerPet = hasYoungerPet;
        this.hasEldestPet = hasEldestPet;
        this.hasSimilarAge = hasSimilarAge;
        this.hasNewbornPet = hasNewbornPet;
        this.strongestBondResonance = strongestBondResonance;
        this.closestPetData = closestPetData;
        this.closestDistance = closestDistance;
        this.packContextComputed = true;
    }

    public boolean hasPackContext() {
        return packContextComputed;
    }

    public int nearbyPetCount() {
        return nearbyPetCount;
    }

    public boolean hasOlderPet() {
        return hasOlderPet;
    }

    public boolean hasYoungerPet() {
        return hasYoungerPet;
    }

    public boolean hasEldestPet() {
        return hasEldestPet;
    }

    public boolean hasSimilarAge() {
        return hasSimilarAge;
    }

    public boolean hasNewbornPet() {
        return hasNewbornPet;
    }

    public float strongestBondResonance() {
        return strongestBondResonance;
    }

    public PetSocialData closestPetData() {
        return closestPetData;
    }

    public double closestDistance() {
        return closestDistance;
    }

    public void setMoodNeighbors(List<PetSocialData> neighbors) {
        this.moodNeighbors = List.copyOf(neighbors);
    }

    public List<PetSocialData> moodNeighbors() {
        return moodNeighbors;
    }

    public void setNearestNeighbors(List<NeighborSample> nearestNeighbors) {
        this.nearestNeighbors = List.copyOf(nearestNeighbors);
    }

    public List<NeighborSample> nearestNeighbors() {
        return nearestNeighbors;
    }

    public void setGossipNeighbors(List<NeighborSample> neighbors) {
        this.gossipNeighbors = List.copyOf(neighbors);
    }

    public List<NeighborSample> gossipNeighbors() {
        return gossipNeighbors;
    }

    public void setGossipClusters(List<List<NeighborSample>> clusters) {
        if (clusters.isEmpty()) {
            this.gossipClusters = Collections.emptyList();
            return;
        }
        List<List<NeighborSample>> frozen = new java.util.ArrayList<>(clusters.size());
        for (List<NeighborSample> cluster : clusters) {
            frozen.add(List.copyOf(cluster));
        }
        this.gossipClusters = List.copyOf(frozen);
    }

    public List<List<NeighborSample>> gossipClusters() {
        return gossipClusters;
    }

    public void setGossipClusterCursor(int cursor) {
        this.gossipClusterCursor = Math.max(0, cursor);
    }

    public int gossipClusterCursor() {
        return gossipClusterCursor;
    }

    public void setSharedRumors(List<RumorEntry> rumors) {
        this.sharedRumors = List.copyOf(rumors);
    }

    public List<RumorEntry> sharedRumors() {
        return sharedRumors;
    }

    public boolean tryMarkBeat(String key, long interval) {
        String stateKey = "species_" + key;
        Long last = component.getStateData(stateKey, Long.class);
        if (last != null && currentTick - last < interval) {
            return false;
        }
        component.setStateData(stateKey, currentTick);
        return true;
    }

    public void resetTransientState() {
        this.nearbyPetCount = 0;
        this.hasOlderPet = false;
        this.hasYoungerPet = false;
        this.hasEldestPet = false;
        this.hasSimilarAge = false;
        this.hasNewbornPet = false;
        this.strongestBondResonance = 0f;
        this.closestPetData = null;
        this.closestDistance = Double.MAX_VALUE;
        this.packContextComputed = false;
        this.moodNeighbors = Collections.emptyList();
        this.nearestNeighbors = Collections.emptyList();
        this.gossipNeighbors = Collections.emptyList();
        this.sharedRumors = Collections.emptyList();
        this.gossipClusters = Collections.emptyList();
        this.gossipClusterCursor = 0;
    }

    private static final class NeighborSampleCache {
        private long cachedTick = Long.MIN_VALUE;
        private double cachedSampleRadius = -1.0;
        private int cachedMaxSamples = -1;
        private double cachedPackRadius = -1.0;
        private NeighborSummary summary = NeighborSummary.empty();

        NeighborSummary ensure(SocialContextSnapshot context, PetSwarmIndex swarm,
                                double sampleRadius, int maxSamples, double packRadius) {
            if (swarm == null) {
                summary = NeighborSummary.empty();
                cachedTick = context.currentTick;
                cachedSampleRadius = sampleRadius;
                cachedMaxSamples = maxSamples;
                cachedPackRadius = packRadius;
                return summary;
            }

            if (summary == null
                || cachedTick != context.currentTick
                || Double.compare(cachedSampleRadius, sampleRadius) != 0
                || cachedMaxSamples != maxSamples
                || Double.compare(cachedPackRadius, packRadius) != 0) {
                summary = NeighborSummary.collect(swarm, context.pet, context.component,
                    context.petData, context.petDataCache, context.currentTick,
                    sampleRadius, maxSamples, packRadius);
                cachedTick = context.currentTick;
                cachedSampleRadius = sampleRadius;
                cachedMaxSamples = maxSamples;
                cachedPackRadius = packRadius;
            }
            return summary;
        }
    }

    public static final class NeighborSummary {
        private static final NeighborSummary EMPTY = new NeighborSummary(List.of(), List.of(),
            List.of(), 0f, null, Double.MAX_VALUE);

        private final List<NeighborSample> samples;
        private final List<PetSocialData> packNeighbors;
        private final List<PetSocialData> packMoodNeighbors;
        private final float packStrongestBondResonance;
        private final PetSocialData closestPackNeighbor;
        private final double closestPackDistanceSq;

        private NeighborSummary(List<NeighborSample> samples, List<PetSocialData> packNeighbors,
                                List<PetSocialData> packMoodNeighbors,
                                float packStrongestBondResonance,
                                PetSocialData closestPackNeighbor,
                                double closestPackDistanceSq) {
            this.samples = samples;
            this.packNeighbors = packNeighbors;
            this.packMoodNeighbors = packMoodNeighbors;
            this.packStrongestBondResonance = packStrongestBondResonance;
            this.closestPackNeighbor = closestPackNeighbor;
            this.closestPackDistanceSq = closestPackDistanceSq;
        }

        public static NeighborSummary empty() {
            return EMPTY;
        }

        public static NeighborSummary collect(PetSwarmIndex swarm, MobEntity pet,
                                              PetComponent component, PetSocialData selfData,
                                              Map<MobEntity, PetSocialData> cache,
                                              long currentTick, double sampleRadius,
                                              int maxSamples, double packRadius) {
            if (swarm == null || pet == null || component == null || selfData == null || cache == null) {
                return EMPTY;
            }

            List<NeighborSample> allSamples = new ArrayList<>();
            List<NeighborSample> packSamples = new ArrayList<>();
            double packRadiusSq = packRadius > 0 ? packRadius * packRadius : 0;
            int packCap = maxSamples > 0 ? Math.min(maxSamples, MAX_PACK_SAMPLE) : MAX_PACK_SAMPLE;

            swarm.forEachNeighbor(pet, component, sampleRadius, 0, (entry, distSq) -> {
                PetSocialData neighbor = cache.computeIfAbsent(entry.pet(),
                    key -> new PetSocialData(entry, currentTick));
                NeighborSample sample = new NeighborSample(entry.pet(), neighbor, distSq);
                insertSortedWithLimit(allSamples, sample, maxSamples);
                if (packRadiusSq <= 0 || distSq > packRadiusSq) {
                    return;
                }
                insertSortedWithLimit(packSamples, sample, packCap);
            });

            if (allSamples.isEmpty()) {
                return EMPTY;
            }

            List<PetSocialData> packNeighbors = new ArrayList<>(packSamples.size());
            List<PetSocialData> packMoodNeighbors = new ArrayList<>(packSamples.size());
            PetSocialData closestPack = null;
            double closestPackDistanceSq = Double.MAX_VALUE;
            float strongestBond = 0f;

            for (NeighborSample sample : packSamples) {
                PetSocialData data = sample.data();
                packNeighbors.add(data);
                if (data.currentMood() != null) {
                    packMoodNeighbors.add(data);
                }
                if (sample.squaredDistance() < closestPackDistanceSq) {
                    closestPackDistanceSq = sample.squaredDistance();
                    closestPack = data;
                }
                float bondDiff = Math.abs(selfData.bondStrength() - data.bondStrength());
                if (bondDiff < 0.2f) {
                    strongestBond = Math.max(strongestBond,
                        Math.min(selfData.bondStrength(), data.bondStrength()));
                }
            }

            return new NeighborSummary(List.copyOf(allSamples), List.copyOf(packNeighbors),
                List.copyOf(packMoodNeighbors), strongestBond, closestPack, closestPackDistanceSq);
        }

        private static void insertSortedWithLimit(List<NeighborSample> samples, NeighborSample sample, int limit) {
            int capacity = limit > 0 ? limit : Integer.MAX_VALUE;
            if (!samples.isEmpty() && samples.size() >= capacity
                && sample.squaredDistance() >= samples.get(samples.size() - 1).squaredDistance()) {
                return;
            }

            int index = Collections.binarySearch(samples, sample,
                Comparator.comparingDouble(NeighborSample::squaredDistance));
            if (index < 0) {
                index = -index - 1;
            }
            samples.add(index, sample);
            if (samples.size() > capacity) {
                samples.remove(samples.size() - 1);
            }
        }

        public List<NeighborSample> samples() {
            return samples;
        }

        public List<PetSocialData> packNeighbors() {
            return packNeighbors;
        }

        public List<PetSocialData> packMoodNeighbors() {
            return packMoodNeighbors;
        }

        public int packNeighborCount() {
            return packNeighbors.size();
        }

        public float packStrongestBondResonance() {
            return packStrongestBondResonance;
        }

        public PetSocialData closestPackNeighbor() {
            return closestPackNeighbor;
        }

        public double closestPackDistance() {
            if (closestPackDistanceSq == Double.MAX_VALUE) {
                return Double.MAX_VALUE;
            }
            if (closestPackDistanceSq <= 0.0) {
                return 0.0;
            }
            return Math.sqrt(closestPackDistanceSq);
        }

        public List<NeighborSample> nearestWithin(double maxDistanceSq, int limit) {
            if (samples.isEmpty() || limit <= 0) {
                return List.of();
            }
            List<NeighborSample> nearest = new ArrayList<>(Math.min(limit, samples.size()));
            for (NeighborSample sample : samples) {
                if (maxDistanceSq > 0 && sample.squaredDistance() > maxDistanceSq) {
                    continue;
                }
                nearest.add(sample);
                if (nearest.size() >= limit) {
                    break;
                }
            }
            return nearest.isEmpty() ? List.of() : List.copyOf(nearest);
        }

        public List<NeighborSample> samplesWithin(double maxDistanceSq) {
            if (samples.isEmpty()) {
                return List.of();
            }
            if (maxDistanceSq <= 0) {
                return samples;
            }
            List<NeighborSample> filtered = new ArrayList<>();
            for (NeighborSample sample : samples) {
                if (sample.squaredDistance() <= maxDistanceSq) {
                    filtered.add(sample);
                }
            }
            return filtered.isEmpty() ? List.of() : List.copyOf(filtered);
        }
    }

    public static final class NeighborSample {
        private final MobEntity pet;
        private final PetSocialData data;
        private final double squaredDistance;

        public NeighborSample(MobEntity pet, PetSocialData data, double squaredDistance) {
            this.pet = pet;
            this.data = data;
            this.squaredDistance = squaredDistance;
        }

        public MobEntity pet() {
            return pet;
        }

        public PetSocialData data() {
            return data;
        }

        public double squaredDistance() {
            return squaredDistance;
        }
    }
}
