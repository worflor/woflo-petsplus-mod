package woflo.petsplus.ai.goals.follow;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.movement.PositionalOffsetModifier;
import woflo.petsplus.ai.movement.TargetDistanceModifier;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.behavior.social.PetSocialData;
import woflo.petsplus.behavior.social.SocialContextSnapshot;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Influencer goal that keeps a comfortable spacing within owner pet packs.
 */
public class MaintainPackSpacingGoal extends AdaptiveGoal {
    static final double SPACING_SAMPLE_RADIUS = 4.5;
    static final double SPACING_PACK_RADIUS = 4.0;
    static final int SPACING_MAX_SAMPLES = 3;
    private static final long FOLLOW_SAMPLE_INTERVAL = 5L;
    private static final long SWARM_CHECK_INTERVAL = 20L;
    private static final double MAX_FORWARD_EXTENSION = 1.6d;
    private static final double MAX_LATERAL_OFFSET = 1.8d;
    private static final float MAX_PADDING = 1.2f;
    private static final double OWNER_MOVEMENT_DAMPING_THRESHOLD = 0.03d;
    private static final double OWNER_MOVING_EXTENSION_SCALE = 0.65d;
    private static final float OWNER_MOVING_PADDING_SCALE = 0.45f;
    private static final double NEIGHBOR_INFLUENCE_MAX_DISTANCE = 6.0d;
    private static final double NEIGHBOR_INFLUENCE_MAX_DISTANCE_SQ = NEIGHBOR_INFLUENCE_MAX_DISTANCE * NEIGHBOR_INFLUENCE_MAX_DISTANCE;
    private static final int MAX_CACHE_SIZE = 128; // Limit cache to prevent unbounded growth
    private static final Map<UUID, CachedSpacing> OWNER_SPACING_CACHE = new HashMap<>();

    private long lastSwarmCheckTick = Long.MIN_VALUE;
    private int lastObservedSwarmSize = 1;
    private final Map<MobEntity, PetSocialData> socialScratch = new HashMap<>();
    private long lastSpacingUpdateTick = Long.MIN_VALUE;

    public MaintainPackSpacingGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.MAINTAIN_PACK_SPACING), EnumSet.noneOf(Control.class));
    }

    @Override
    protected boolean canStartGoal() {
        if (petComponent == null || movementDirector == null) {
            return false;
        }
        MobEntity pet = petComponent.getPetEntity();
        if (pet == null || pet.isSleeping()) {
            return false;
        }
        if (pet instanceof PetsplusTameable tameable && tameable.petsplus$isSitting()) {
            return false;
        }
        PlayerEntity owner = petComponent.getOwner() instanceof PlayerEntity player ? player : null;
        if (owner == null) {
            return false;
        }
        long now = owner.getEntityWorld().getTime();
        refreshSwarmSize(owner, now);
        return lastObservedSwarmSize > 1;
    }

    @Override
    protected boolean shouldContinueGoal() {
        return canStartGoal();
    }

    @Override
    protected void onStartGoal() {
        updateSpacing();
    }

    @Override
    protected void onStopGoal() {
        if (movementDirector != null) {
            movementDirector.clearSource(goalId);
        }
        petComponent.setFollowSpacingFocus(null, Long.MIN_VALUE);
        lastSwarmCheckTick = Long.MIN_VALUE;
        lastObservedSwarmSize = 1;
        // Clean up cache entry when owner is no longer tracked
        PlayerEntity owner = petComponent.getOwner() instanceof PlayerEntity player ? player : null;
        if (owner != null) {
            evictCacheIfNeeded(owner.getUuid());
        }
    }

    @Override
    protected void onTickGoal() {
        updateSpacing();
    }

    @Override
    protected float calculateEngagement() {
        return 0.3f;
    }

    private void updateSpacing() {
        if (movementDirector == null) {
            return;
        }

        PlayerEntity owner = petComponent.getOwner() instanceof PlayerEntity player ? player : null;
        if (owner == null) {
            movementDirector.clearSource(goalId);
            petComponent.setFollowSpacingFocus(null, Long.MIN_VALUE);
            return;
        }
        long now = owner.getEntityWorld().getTime();
        refreshSwarmSize(owner, now);
        // Throttle compute frequency based on pack size
        int interval = lastObservedSwarmSize >= 10 ? 5 : 2; // ticks
        if (lastSpacingUpdateTick != Long.MIN_VALUE && now - lastSpacingUpdateTick < interval) {
            return;
        }
        lastSpacingUpdateTick = now;
        if (lastObservedSwarmSize <= 1) {
            movementDirector.clearSource(goalId);
            petComponent.setFollowSpacingFocus(null, Long.MIN_VALUE);
            OWNER_SPACING_CACHE.remove(owner.getUuid());
            return;
        }

        long lastSample = petComponent.getFollowSpacingSampleTick();
        if (lastSample != Long.MIN_VALUE && now - lastSample < FOLLOW_SAMPLE_INTERVAL) {
            return;
        }

        UUID ownerId = owner.getUuid();
        FollowSpacingResult result = null;
        boolean cacheHit = false;
        long computeDuration = 0L;

        CachedSpacing cached = OWNER_SPACING_CACHE.get(ownerId);
        // Dynamic recalc interval based on pack size and owner motion
        boolean ownerIsMoving = owner.getVelocity().horizontalLengthSquared() > OWNER_MOVEMENT_DAMPING_THRESHOLD;
        long recalcInterval = computeSpacingRecalcInterval(lastObservedSwarmSize, ownerIsMoving);
        if (cached != null && (now - cached.tick) < recalcInterval) {
            result = cached.result();
            cacheHit = true;
        }

        if (result == null) {
            FollowSpacingResult computed = FollowSpacingResult.empty();
            StateManager manager = petComponent.getStateManager();
            if (manager == null && owner.getEntityWorld() instanceof ServerWorld serverWorld) {
                manager = StateManager.forWorld(serverWorld);
            }
            if (manager != null) {
                PetSwarmIndex swarmIndex = manager.getSwarmIndex();
                if (swarmIndex != null) {
                    long computeStart = isDebugLoggingEnabled() ? System.nanoTime() : 0L;
                    computed = computeSpacing(now, owner, swarmIndex);
                    if (isDebugLoggingEnabled()) {
                        computeDuration = System.nanoTime() - computeStart;
                    }
                }
            }
            result = computed;
            evictCacheIfNeeded(ownerId);
            OWNER_SPACING_CACHE.put(ownerId, new CachedSpacing(now, result));
        }

        if (isDebugLoggingEnabled()) {
            recordSpacingDebug(ownerId, now, cacheHit, result, computeDuration);
        }

        double weight = Math.max(0.0, getMovementConfig().influencerWeight());
        if (weight <= 0.0) {
            movementDirector.clearSource(goalId);
            petComponent.setFollowSpacingFocus(null, Long.MIN_VALUE);
            return;
        }

        movementDirector.setPositionalOffset(goalId,
            new PositionalOffsetModifier(result.offsetX(), 0.0, result.offsetZ(), weight));
        movementDirector.setTargetDistance(goalId,
            new TargetDistanceModifier(0.0, result.padding(), weight));
        petComponent.setFollowSpacingSample(result.offsetX(), result.offsetZ(), result.padding(), now);
        if (result.focus() != null) {
            petComponent.setFollowSpacingFocus(result.focus(), now + FOLLOW_SAMPLE_INTERVAL);
        } else {
            petComponent.setFollowSpacingFocus(null, Long.MIN_VALUE);
        }
    }

    private void refreshSwarmSize(PlayerEntity owner, long now) {
        if (lastSwarmCheckTick != Long.MIN_VALUE && now - lastSwarmCheckTick < SWARM_CHECK_INTERVAL) {
            return;
        }
        lastSwarmCheckTick = now;
        lastObservedSwarmSize = Math.max(1, resolveSwarmSize(owner));
    }

    private int resolveSwarmSize(PlayerEntity owner) {
        StateManager manager = petComponent.getStateManager();
        if (manager == null && owner.getEntityWorld() instanceof ServerWorld serverWorld) {
            manager = StateManager.forWorld(serverWorld);
        }
        if (manager == null) {
            return 1;
        }
        PetSwarmIndex swarmIndex = manager.getSwarmIndex();
        if (swarmIndex == null) {
            return 1;
        }
        int size = swarmIndex.ownerSize(owner.getUuid());
        if (size <= 0) {
            return 1;
        }
        return size;
    }

    private FollowSpacingResult computeSpacing(long now, PlayerEntity owner, PetSwarmIndex swarmIndex) {
        Map<MobEntity, PetSocialData> cache = socialScratch;
        cache.clear();
        try {
            PetSocialData selfData = new PetSocialData(mob, petComponent, now);
            cache.put(mob, selfData);

            SocialContextSnapshot.NeighborSummary summary = SocialContextSnapshot.NeighborSummary.collect(
                swarmIndex, mob, petComponent, selfData, cache, now,
                SPACING_SAMPLE_RADIUS, SPACING_MAX_SAMPLES, SPACING_PACK_RADIUS);

            List<SocialContextSnapshot.NeighborSample> neighbors = summary.nearestWithin(
                SPACING_PACK_RADIUS * SPACING_PACK_RADIUS, SPACING_MAX_SAMPLES);
            if (neighbors.isEmpty()) {
                return FollowSpacingResult.empty();
            }

            double ownerPosX = owner.getX();
            double ownerPosZ = owner.getZ();
            double petPosX = mob.getX();
            double petPosZ = mob.getZ();
            double petDeltaX = petPosX - ownerPosX;
            double petDeltaZ = petPosZ - ownerPosZ;
            double ownerDistanceSq = petDeltaX * petDeltaX + petDeltaZ * petDeltaZ;
            double forwardX;
            double forwardZ;
            if (ownerDistanceSq < 1.0e-6d) {
                forwardX = 1.0d;
                forwardZ = 0.0d;
            } else {
                double inv = MathHelper.fastInverseSqrt(ownerDistanceSq);
                forwardX = petDeltaX * inv;
                forwardZ = petDeltaZ * inv;
            }
            double lateralX = -forwardZ;
            double lateralZ = forwardX;
            double ownerSpeedSq = owner.getVelocity().horizontalLengthSquared();
            boolean ownerIsMoving = ownerSpeedSq > OWNER_MOVEMENT_DAMPING_THRESHOLD;

            double extension = 0.0;
            double lateralOffset = 0.0;
            double padding = 0.0;
            double weight = 0.0;
            MobEntity focus = null;
            double bestAffinity = Double.NEGATIVE_INFINITY;

            for (SocialContextSnapshot.NeighborSample sample : neighbors) {
                MobEntity neighbor = sample.pet();
                double toNeighborX = neighbor.getX() - ownerPosX;
                double toNeighborZ = neighbor.getZ() - ownerPosZ;
                double forwardComponent = (toNeighborX * forwardX) + (toNeighborZ * forwardZ);
                double lateralComponent = (toNeighborX * lateralX) + (toNeighborZ * lateralZ);
                double affinity = computeAffinityWeight(selfData, sample.data(), now);
                double distanceSq = sample.squaredDistance();
                double distanceFactor = 0.0d;
                if (distanceSq < NEIGHBOR_INFLUENCE_MAX_DISTANCE_SQ) {
                    double distance = 0.0d;
                    if (distanceSq > 1.0e-8d) {
                        double inv = MathHelper.fastInverseSqrt(distanceSq);
                        distance = distanceSq * inv;
                    }
                    distanceFactor = 1.0d - (distance / NEIGHBOR_INFLUENCE_MAX_DISTANCE);
                }
                double weightContribution = Math.max(0.1, distanceFactor) * affinity;
                extension += forwardComponent * weightContribution;
                lateralOffset += lateralComponent * weightContribution;
                padding += Math.max(0.0, MAX_PADDING - Math.abs(lateralComponent)) * weightContribution;
                weight += weightContribution;
                if (weightContribution > 0.0 && affinity > bestAffinity) {
                    focus = sample.pet();
                    bestAffinity = affinity;
                }
            }

            if (weight > 0.0) {
                extension /= weight;
                lateralOffset /= weight;
                if (ownerIsMoving) {
                    extension *= OWNER_MOVING_EXTENSION_SCALE;
                    lateralOffset *= OWNER_MOVING_EXTENSION_SCALE;
                }
                float normalizedPadding = MathHelper.clamp((float) (padding / weight), 0.0f, MAX_PADDING);
                float motionScale = ownerIsMoving ? OWNER_MOVING_PADDING_SCALE : 1.0f;

                extension = MathHelper.clamp(extension, -MAX_FORWARD_EXTENSION, MAX_FORWARD_EXTENSION);
                lateralOffset = MathHelper.clamp(lateralOffset, -MAX_LATERAL_OFFSET, MAX_LATERAL_OFFSET);
                padding = MathHelper.clamp(normalizedPadding * motionScale, 0.0f, MAX_PADDING);
            } else {
                extension = 0.0d;
                lateralOffset = 0.0d;
                padding = 0.0f;
            }

            if (petComponent != null) {
                PetComponent.OwnerCourtesyState courtesyState = petComponent.getOwnerCourtesyState(now);
                if (courtesyState.isActive(now)) {
                    double courtesyBonus = MathHelper.clamp(courtesyState.distanceBonus(), 0.0d, 4.0d);
                    double shift = MathHelper.clamp(courtesyBonus * 0.2d, 0.0d, 0.6d);
                    extension = MathHelper.clamp(extension + shift, -MAX_FORWARD_EXTENSION, MAX_FORWARD_EXTENSION);
                    double lateralScale = Math.max(1.0d, courtesyState.lateralInflation());
                    double paddingScale = Math.max(1.0d, courtesyState.paddingInflation());
                    lateralOffset = MathHelper.clamp(lateralOffset * lateralScale, -MAX_LATERAL_OFFSET, MAX_LATERAL_OFFSET);
                    padding = MathHelper.clamp((float) (padding * paddingScale), 0.0f, MAX_PADDING);
                }
            }

            double offsetX = (forwardX * extension) + (lateralX * lateralOffset);
            double offsetZ = (forwardZ * extension) + (lateralZ * lateralOffset);
            return new FollowSpacingResult(offsetX, offsetZ, (float) padding, focus, neighbors.size());
        } finally {
            cache.clear();
        }
    }

    static double computeAffinityWeight(PetSocialData selfData, PetSocialData neighborData, long now) {
        if (neighborData == null) {
            return 1.0d;
        }
        double bond = MathHelper.clamp(neighborData.bondStrength(), 0.0f, 1.0f);
        double alignment = 0.0d;
        if (selfData != null) {
            alignment = MathHelper.clamp(selfData.headingAlignmentWith(neighborData), 0.0d, 1.0d);
        }
        long affectionTick = Math.max(neighborData.lastCrouchCuddleTick(), neighborData.lastPetTick());
        long threatTick = neighborData.lastThreatRecoveryTick();
        return computeAffinityWeight((float) bond, alignment, affectionTick, threatTick, now);
    }

    static double computeAffinityWeight(float bondStrength,
                                        double alignment,
                                        long affectionTick,
                                        long threatTick,
                                        long now) {
        double bond = MathHelper.clamp(bondStrength, 0.0f, 1.0f);
        double clampedAlignment = MathHelper.clamp(alignment, 0.0d, 1.0d);
        double affection = 0.0d;
        if (affectionTick > 0L && now > affectionTick) {
            long age = now - affectionTick;
            if (age < 200L) {
                affection = (200L - age) / 200.0d;
            }
        }
        double tensionPenalty = 0.0d;
        if (threatTick > 0L && now > threatTick) {
            long age = now - threatTick;
            if (age < 160L) {
                tensionPenalty = (160L - age) / 160.0d;
            }
        }
        double affinity = 1.0d
            + (bond * 0.65d)
            + (clampedAlignment * 0.2d)
            + (affection * 0.25d)
            - (tensionPenalty * 0.35d);
        return MathHelper.clamp(affinity, 0.4d, 2.2d);
    }


    private void recordSpacingDebug(UUID ownerId, long tick, boolean cacheHit, FollowSpacingResult result, long computeNanos) {
        if (!isDebugLoggingEnabled()) {
            return;
        }
        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        woflo.petsplus.debug.DebugSnapshotAggregator.recordSpacing(
            serverWorld.getServer(), ownerId, tick, cacheHit, result.neighborCount(), cacheHit ? 0L : computeNanos);
    }

    private static long computeSpacingRecalcInterval(int swarmSize, boolean ownerMoving) {
        int base = ownerMoving ? 5 : 8;
        int extra = Math.min(20, Math.max(0, swarmSize - 2) / (ownerMoving ? 6 : 3));
        return Math.max(3, base + extra);
    }

    private static boolean isDebugLoggingEnabled() {
        return woflo.petsplus.config.DebugSettings.isDebugEnabled();
    }

    private record CachedSpacing(long tick, FollowSpacingResult result) {}

    private record FollowSpacingResult(double offsetX, double offsetZ, float padding, MobEntity focus, int neighborCount) {
        static FollowSpacingResult empty() {
            return new FollowSpacingResult(0.0, 0.0, 0.0f, null, 0);
        }
    }

    /**
     * Evict cache entries if the map exceeds MAX_CACHE_SIZE.
     * Uses simple FIFO eviction - removes oldest entry when limit is reached.
     */
    private static void evictCacheIfNeeded(UUID currentOwnerId) {
        if (OWNER_SPACING_CACHE.size() >= MAX_CACHE_SIZE) {
            // Remove first entry found (simple FIFO) - avoid stream overhead
            var iterator = OWNER_SPACING_CACHE.keySet().iterator();
            if (iterator.hasNext()) {
                OWNER_SPACING_CACHE.remove(iterator.next());
            }
        }
    }
}
