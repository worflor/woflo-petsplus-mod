package woflo.petsplus.behavior.social;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetSwarmIndex;
import woflo.petsplus.state.gossip.RumorEntry;

/**
 * Simple data carrier passed to social behaviour routines so they can pull the
 * information they need without touching the {@link PetComponent} state map
 * directly.
 */
public class SocialContextSnapshot {

    private final MobEntity pet;
    private final PetComponent component;
    private final ServerPlayerEntity owner;
    private final ServerWorld world;
    private final long currentTick;
    private final PetSocialData petData;
    private final Map<MobEntity, PetSocialData> petDataCache;

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

    public SocialContextSnapshot(MobEntity pet, PetComponent component,
                                 ServerPlayerEntity owner, ServerWorld world,
                                 long currentTick, PetSocialData petData,
                                 Map<MobEntity, PetSocialData> petDataCache) {
        this.pet = Objects.requireNonNull(pet, "pet");
        this.component = Objects.requireNonNull(component, "component");
        this.owner = owner;
        this.world = world;
        this.currentTick = currentTick;
        this.petData = Objects.requireNonNull(petData, "petData");
        this.petDataCache = Objects.requireNonNull(petDataCache, "petDataCache");
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
