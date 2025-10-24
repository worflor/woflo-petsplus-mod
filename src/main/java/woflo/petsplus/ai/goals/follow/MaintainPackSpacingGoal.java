package woflo.petsplus.ai.goals.follow;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
import woflo.petsplus.config.DebugSettings;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private static final Map<UUID, CachedSpacing> OWNER_SPACING_CACHE = new HashMap<>();

    private long lastSwarmCheckTick = Long.MIN_VALUE;
    private int lastObservedSwarmSize = 1;
    private final Map<MobEntity, PetSocialData> socialScratch = new HashMap<>();

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
        long lastSpacingUpdateTick = petComponent.getStateData("last_spacing_update_tick", Long.class, Long.MIN_VALUE);
        long now = owner.getEntityWorld().getTime();
        refreshSwarmSize(owner, now);
        // Throttle compute frequency based on pack size
        int interval = lastObservedSwarmSize >= 10 ? 5 : 2; // ticks
        if (lastSpacingUpdateTick != Long.MIN_VALUE && now - lastSpacingUpdateTick < interval) {
            return;
        }
        petComponent.setStateData("last_spacing_update_tick", now);
        lastSpacingUpdateTick = now;
        if (lastObservedSwarmSize <= 1) {
            petComponent.clearStateData("last_spacing_update_tick");
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
        long debugComputeDuration = 0L;

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
                    debugComputeDuration = isDebugLoggingEnabled() ? System.nanoTime() - computeStart : 0L;
                }
            }
            result = computed;
            OWNER_SPACING_CACHE.put(ownerId, new CachedSpacing(now, result));
        }
        if (isDebugLoggingEnabled()) {
            recordSpacingDebug(ownerId, now, cacheHit, result, debugComputeDuration);
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
        PetSocialData selfData = new PetSocialData(mob, petComponent, now);
        cache.put(mob, selfData);

        SocialContextSnapshot.NeighborSummary summary = SocialContextSnapshot.NeighborSummary.collect(
            swarmIndex, mob, petComponent, selfData, cache, now,
            SPACING_SAMPLE_RADIUS, SPACING_MAX_SAMPLES, SPACING_PACK_RADIUS);

        List<SocialContextSnapshot.NeighborSample> neighbors = summary.nearestWithin(
            SPACING_PACK_RADIUS * SPACING_PACK_RADIUS, SPACING_MAX_SAMPLES);
        if (neighbors.isEmpty()) {
            cache.clear();
            return FollowSpacingResult.empty();
        }

        Vec3d ownerPos = owner.getEntityPos();
        Vec3d petPos = mob.getEntityPos();
        Vec3d ownerToPet = petPos.subtract(ownerPos);
        double ownerDistanceSq = ownerToPet.lengthSquared();
        Vec3d forward;
        if (ownerDistanceSq < 0.0001) {
            forward = new Vec3d(1.0, 0.0, 0.0);
        } else {
            double invDistance = 1.0 / Math.sqrt(ownerDistanceSq);
            forward = ownerToPet.multiply(invDistance);
        }
        Vec3d lateral = new Vec3d(-forward.z, 0.0, forward.x);
        double ownerSpeedSq = owner.getVelocity().horizontalLengthSquared();
        boolean ownerIsMoving = ownerSpeedSq > OWNER_MOVEMENT_DAMPING_THRESHOLD;

        double extension = 0.0;
        double lateralOffset = 0.0;
        double padding = 0.0;
        double weight = 0.0;
        MobEntity focus = null;

        for (SocialContextSnapshot.NeighborSample sample : neighbors) {
            Vec3d toNeighbor = new Vec3d(sample.pet().getX() - ownerPos.x, 0.0, sample.pet().getZ() - ownerPos.z);
            double forwardComponent = toNeighbor.dotProduct(forward);
            double lateralComponent = toNeighbor.dotProduct(lateral);
            double weightContribution = Math.max(0.1, 1.0 - Math.min(1.0, Math.sqrt(sample.squaredDistance()) / 6.0));
            extension += forwardComponent * weightContribution;
            lateralOffset += lateralComponent * weightContribution;
            padding += Math.max(0.0, MAX_PADDING - Math.abs(lateralComponent)) * weightContribution;
            weight += weightContribution;
            if (focus == null) {
                focus = sample.pet();
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

        Vec3d offset = forward.multiply(extension).add(lateral.multiply(lateralOffset));
        cache.clear();
        return new FollowSpacingResult(offset.x, offset.z, (float) padding, focus, neighbors.size());
    }


    private void recordSpacingDebug(UUID ownerId, long tick, boolean cacheHit, FollowSpacingResult result, long computeNanos) {
        if (!isDebugLoggingEnabled()) {
            return;
        }
        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        // Delegate aggregation and periodic snapshotting to the centralized aggregator.
        woflo.petsplus.debug.DebugSnapshotAggregator.recordSpacing(
            serverWorld.getServer(), ownerId, tick, cacheHit, result.neighborCount(), cacheHit ? 0L : computeNanos);
    }

    private static long computeSpacingRecalcInterval(int swarmSize, boolean ownerMoving) {
        int base = ownerMoving ? 5 : 8;
        int extra = Math.min(20, Math.max(0, swarmSize - 2) / (ownerMoving ? 6 : 3));
        return Math.max(3, base + extra);
    }

    private record CachedSpacing(long tick, FollowSpacingResult result) {}

    private record FollowSpacingResult(double offsetX, double offsetZ, float padding, MobEntity focus, int neighborCount) {
        static FollowSpacingResult empty() {
            return new FollowSpacingResult(0.0, 0.0, 0.0f, null, 0);
        }
    }

    private void sendDebugLine(String message) {
        if (!isDebugLoggingEnabled()) {
            return;
        }
        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        MinecraftServer server = serverWorld.getServer();
        if (server == null) {
            return;
        }
        DebugSettings.broadcastDebug(server, Text.literal(message));
    }

    private static boolean isDebugLoggingEnabled() {
        return DebugSettings.isDebugEnabled();
    }
}
