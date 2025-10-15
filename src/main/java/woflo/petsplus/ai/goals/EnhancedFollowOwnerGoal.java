package woflo.petsplus.ai.goals;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.goals.OwnerAssistAttackGoal;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.behavior.social.PetSocialData;
import woflo.petsplus.behavior.social.SocialContextSnapshot;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;
import woflo.petsplus.state.emotions.PetMoodEngine;

/**
 * Enhanced follow owner goal with better pathfinding and role-specific behavior.
 */
public class EnhancedFollowOwnerGoal extends Goal {
    private static final String LAST_WET_TICK_KEY = "last_wet_tick";
    private static final float HESITATION_LOOK_YAW = 20.0f;
    private static final double HESITATION_SPEED_BOOST = 0.2d;
    private static final float HESITATION_DISTANCE_FACTOR = 0.6f;
    private static final float HESITATION_CLEAR_DISTANCE = 2.5f;
    private static final double SPACING_SAMPLE_RADIUS = 4.5;
    private static final double SPACING_PACK_RADIUS = 4.0;
    private static final int SPACING_MAX_SAMPLES = 3;
    private static final long FOLLOW_SAMPLE_INTERVAL = 5L;
    private static final double LATERAL_PUSH_SCALE = 1.1;
    private static final double MOVE_TARGET_REFRESH_EPSILON = 0.0625 * 0.0625;
    private static final int PATH_FAILURE_TIMEOUT = 80;
    private static final int PATH_BLOCK_COOLDOWN_TICKS = 100;
    private static final double MIN_DYNAMIC_SPEED = 0.55d;
    private static final double MAX_DYNAMIC_SPEED = 1.9d;
    private static final String FOLLOW_PATH_COOLDOWN_KEY = "follow_path_blocked";
    private static final Set<Identifier> FOLLOW_SUPPRESSION_GOALS = Set.of(
        GoalIds.PARALLEL_PLAY,
        GoalIds.TOY_POUNCE,
        GoalIds.HIDE_AND_SEEK,
        GoalIds.SUNBEAM_SPRAWL,
        GoalIds.SHOW_AND_DROP
    );

    private final MobEntity mob;
    private final PetsplusTameable tameable;
    private final PetComponent petComponent;
    private final double speed;
    private final float baseFollowDistance;
    private final float teleportDistance;
    private float activeFollowDistance;
    private boolean scoutMode = false;
    private int stuckCounter = 0;
    private BlockPos lastOwnerPos = BlockPos.ORIGIN;
    private MobEntity spacingFocus;
    private long spacingFocusExpiryTick;
    private Vec3d lastMoveTarget;
    private long lastMomentumSampleTick = Long.MIN_VALUE;
    private int failedPathTicks;
    private long pathBlockedUntilTick = Long.MIN_VALUE;

    public EnhancedFollowOwnerGoal(MobEntity mob, PetsplusTameable tameable, PetComponent petComponent, double speed, float followDistance, float teleportDistance) {
        this.mob = mob;
        this.tameable = tameable;
        this.petComponent = petComponent;
        this.speed = speed;
        this.baseFollowDistance = followDistance;
        this.teleportDistance = teleportDistance;
        this.activeFollowDistance = followDistance;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        this.failedPathTicks = 0;
    }

    public void setScoutMode(boolean scoutMode) {
        this.scoutMode = scoutMode;
    }

    @Override
    public boolean canStart() {
        if (!this.tameable.petsplus$isTamed()) {
            return false;
        }

        if (this.mob.isSleeping()) {
            return false;
        }

        if (this.tameable.petsplus$isSitting()) {
            return false;
        }

        long currentTick = this.mob.getEntityWorld().getTime();
        if (pathBlockedUntilTick != Long.MIN_VALUE && currentTick < pathBlockedUntilTick) {
            return false;
        }

        if (petComponent.isOnCooldown(FOLLOW_PATH_COOLDOWN_KEY)) {
            return false;
        }

        if (petComponent.getAIState().isPanicking()) {
            return false;
        }

        Identifier activeGoal = petComponent.getActiveAdaptiveGoalId();
        if (activeGoal != null && FOLLOW_SUPPRESSION_GOALS.contains(activeGoal)) {
            return false;
        }

        if (petComponent.hasMoodAbove(PetComponent.Mood.ANGRY, 0.55f)) {
            return false;
        }

        // Rain-shelter hold: if it's raining, the pet is sheltered and was recently wet,
        // avoid starting follow unless the owner is already very close (won't force leaving cover).
        if (isShelterHoldActive()) {
            LivingEntity owner = this.tameable.petsplus$getOwner();
            double distanceSq = (owner == null) ? Double.POSITIVE_INFINITY : this.mob.squaredDistanceTo(owner);
            if (!Double.isNaN(distanceSq) && distanceSq > (3.0 * 3.0)) {
                return false;
            }
        }

        LivingEntity livingOwner = this.tameable.petsplus$getOwner();
        if (!(livingOwner instanceof PlayerEntity owner)) {
            return false;
        }

        if (owner.isSpectator()) {
            if (this.scoutMode) {
                this.lastOwnerPos = owner.getBlockPos();
            }
            return false;
        }

        long now = owner.getEntityWorld().getTime();
        boolean hesitating = OwnerAssistAttackGoal.isPetHesitating(petComponent, now);
        boolean moodHoldActive = petComponent.isMoodFollowHoldActive(now);
        float moodDistanceBonus = moodHoldActive ? petComponent.getMoodFollowDistanceBonus(now) : 0.0f;
        float effectiveFollowDistance = baseFollowDistance + moodDistanceBonus;
        double effectiveDistanceSq = effectiveFollowDistance * effectiveFollowDistance;

        if (scoutMode) {
            BlockPos currentOwnerPos = owner.getBlockPos();
            boolean ownerMoved = !currentOwnerPos.equals(lastOwnerPos);
            lastOwnerPos = currentOwnerPos;

            double distance = this.mob.squaredDistanceTo(owner);
            if (hesitating) {
                return true;
            }
            if (moodHoldActive && !ownerMoved && distance <= effectiveDistanceSq) {
                return false;
            }
            return ownerMoved || distance > effectiveDistanceSq;
        }

        if (hesitating) {
            return true;
        }

        double distance = this.mob.squaredDistanceTo(owner);
        if (moodHoldActive && distance <= effectiveDistanceSq) {
            return false;
        }
        return distance > effectiveDistanceSq;
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity livingOwner = this.tameable.petsplus$getOwner();
        if (!(livingOwner instanceof PlayerEntity owner)) {
            return false;
        }

        if (owner.isSpectator()) {
            return false;
        }

        if (this.mob.isSleeping() || this.tameable.petsplus$isSitting()) {
            return false;
        }

        if (petComponent.isOnCooldown(FOLLOW_PATH_COOLDOWN_KEY)) {
            return false;
        }

        long now = owner.getEntityWorld().getTime();
        if (pathBlockedUntilTick != Long.MIN_VALUE && now < pathBlockedUntilTick) {
            return false;
        }

        if (petComponent.getAIState().isPanicking()) {
            return false;
        }

        // Keep holding position under shelter if recently wet during rain.
        if (isShelterHoldActive()) {
            double distance = this.mob.squaredDistanceTo(owner);
            if (!Double.isNaN(distance) && distance > (3.0 * 3.0)) {
                return false;
            }
        }

        double distance = this.mob.squaredDistanceTo(owner);
        if (distance > (this.teleportDistance * this.teleportDistance)) {
            return true;
        }

        if (OwnerAssistAttackGoal.isPetHesitating(petComponent, now)) {
            return true;
        }

        return distance > (this.activeFollowDistance * this.activeFollowDistance);
    }

    @Override
    public void start() {
        this.stuckCounter = 0;
        this.lastMoveTarget = null;
        this.failedPathTicks = 0;
        this.pathBlockedUntilTick = Long.MIN_VALUE;
        long now = this.mob.getEntityWorld().getTime();
        float moodDistanceBonus = petComponent.getMoodFollowDistanceBonus(now);
        this.activeFollowDistance = baseFollowDistance + moodDistanceBonus + petComponent.getFollowSpacingPadding();

        if (petComponent.getMoodEngine() != null) {
            // Kick off a small burst so momentum reacts quickly to new movement.
            petComponent.getMoodEngine().recordBehavioralActivity(0.4f, 6L, PetMoodEngine.ActivityType.PHYSICAL);
        }
        lastMomentumSampleTick = now;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
        this.lastMoveTarget = null;
        this.failedPathTicks = 0;
        lastMomentumSampleTick = Long.MIN_VALUE;
    }

    @Override
    public void tick() {
        LivingEntity livingOwner = this.tameable.petsplus$getOwner();
        if (!(livingOwner instanceof PlayerEntity owner)) {
            return;
        }

        if (owner.isSpectator()) {
            this.mob.getNavigation().stop();
            this.lastMoveTarget = null;
            return;
        }

        if (this.mob.isSleeping() || this.tameable.petsplus$isSitting()) {
            this.mob.getNavigation().stop();
            this.lastMoveTarget = null;
            return;
        }

        long now = owner.getEntityWorld().getTime();
        if (petComponent.isOnCooldown(FOLLOW_PATH_COOLDOWN_KEY)
            || (pathBlockedUntilTick != Long.MIN_VALUE && now < pathBlockedUntilTick)) {
            this.mob.getNavigation().stop();
            this.lastMoveTarget = null;
            return;
        }

        // Respect shelter hold during rain: don't leave cover to follow while drying off.
        if (isShelterHoldActive()) {
            this.mob.getNavigation().stop();
            this.lastMoveTarget = null;
            return;
        }

        Vec3d ownerPos = owner.getEntityPos();
        boolean hesitating = OwnerAssistAttackGoal.isPetHesitating(petComponent, now);
        boolean moodHoldActive = petComponent.isMoodFollowHoldActive(now);
        float moodDistanceBonus = moodHoldActive ? petComponent.getMoodFollowDistanceBonus(now) : 0.0f;
        double offsetX = petComponent.getFollowSpacingOffsetX();
        double offsetZ = petComponent.getFollowSpacingOffsetZ();
        float spacingPadding = petComponent.getFollowSpacingPadding();

        PetSwarmIndex swarmIndex = null;
        if (owner.getEntityWorld() instanceof ServerWorld serverWorld) {
            swarmIndex = StateManager.forWorld(serverWorld).getSwarmIndex();
        }
        Vec3d followAnchor = ownerPos;
        if (swarmIndex != null) {
            Vec3d swarmCenter = computeSwarmCenter(swarmIndex);
            if (swarmCenter != null) {
                followAnchor = ownerPos.lerp(swarmCenter, 0.25);
            }
        }
        if (swarmIndex != null && !moodHoldActive) {
            long lastSampleTick = petComponent.getFollowSpacingSampleTick();
            if (lastSampleTick == Long.MIN_VALUE || now - lastSampleTick >= FOLLOW_SAMPLE_INTERVAL) {
                FollowSpacingResult spacing = computeSpacing(now, owner, swarmIndex);
                offsetX = spacing.offsetX;
                offsetZ = spacing.offsetZ;
                spacingPadding = spacing.padding;
                petComponent.setFollowSpacingSample(offsetX, offsetZ, spacingPadding, now);
                this.spacingFocus = spacing.focus;
                this.spacingFocusExpiryTick = now + FOLLOW_SAMPLE_INTERVAL;
            } else if (this.spacingFocus != null && (this.spacingFocus.isRemoved() || now > this.spacingFocusExpiryTick)) {
                this.spacingFocus = null;
            }
        } else if (swarmIndex == null) {
            this.spacingFocus = null;
        } else if (moodHoldActive && this.spacingFocus != null && (this.spacingFocus.isRemoved() || now > this.spacingFocusExpiryTick)) {
            this.spacingFocus = null;
        }

        float baseDistance = hesitating
            ? Math.max(HESITATION_CLEAR_DISTANCE, baseFollowDistance * HESITATION_DISTANCE_FACTOR)
            : baseFollowDistance;
        baseDistance += moodDistanceBonus;
        this.activeFollowDistance = baseDistance + spacingPadding;

        float lookYaw = hesitating ? HESITATION_LOOK_YAW : 10.0f;
        if (this.spacingFocus != null && this.spacingFocus.isRemoved()) {
            this.spacingFocus = null;
        }
        Entity lookTarget = (!hesitating && this.spacingFocus != null) ? this.spacingFocus : owner;
        this.mob.getLookControl().lookAt(lookTarget, lookYaw, this.mob.getMaxLookPitchChange());

        Vec3d moveTarget = followAnchor.add(offsetX, 0.0, offsetZ);
        double distanceToOwnerSq = this.mob.squaredDistanceTo(owner);
        boolean worldsMatch = owner.getEntityWorld().getRegistryKey().equals(this.mob.getEntityWorld().getRegistryKey());
        if (!worldsMatch || Double.isInfinite(distanceToOwnerSq)) {
            this.mob.getNavigation().stop();
            this.lastMoveTarget = null;
            return;
        }

        if (moodHoldActive && !hesitating && distanceToOwnerSq <= (baseDistance * baseDistance)) {
            if (!this.mob.getNavigation().isIdle()) {
                this.mob.getNavigation().stop();
            }
            this.lastMoveTarget = null;
            return;
        }

        if (distanceToOwnerSq > (teleportDistance * teleportDistance)) {
            if (this.tryEmergencyTeleport(owner)) {
                this.lastMoveTarget = null;
                return;
            }
        }

        if (hesitating && distanceToOwnerSq <= (HESITATION_CLEAR_DISTANCE * HESITATION_CLEAR_DISTANCE)) {
            OwnerAssistAttackGoal.clearAssistHesitation(petComponent);
        }

        double adjustedSpeed = resolveDynamicSpeed(distanceToOwnerSq, hesitating);
        boolean navigationIdle = this.mob.getNavigation().isIdle();
        boolean targetChanged = this.lastMoveTarget == null
            || this.lastMoveTarget.squaredDistanceTo(moveTarget) > MOVE_TARGET_REFRESH_EPSILON;

        if (navigationIdle || targetChanged) {
            this.mob.getNavigation().startMovingTo(moveTarget.x, moveTarget.y, moveTarget.z, adjustedSpeed);
        } else {
            this.mob.getNavigation().setSpeed(adjustedSpeed);
        }
        this.lastMoveTarget = moveTarget;

        boolean followingPath = this.mob.getNavigation().isFollowingPath();
        if (!followingPath) {
            stuckCounter++;
            if (distanceToOwnerSq > (this.activeFollowDistance * this.activeFollowDistance)) {
                failedPathTicks = Math.min(PATH_FAILURE_TIMEOUT + 20, failedPathTicks + 1);
            }
            if (stuckCounter > 60) {
                tryAlternativePathfinding(owner, moveTarget);
                stuckCounter = 0;
            }
        } else {
            stuckCounter = 0;
            failedPathTicks = 0;
        }

        if (failedPathTicks > PATH_FAILURE_TIMEOUT) {
            enterPathBlockedState(now);
            return;
        }

        sampleMomentum(now, hesitating, distanceToOwnerSq);
    }

    /**
     * Try alternative pathfinding when pet gets stuck.
     */
    private FollowSpacingResult computeSpacing(long now, PlayerEntity owner, PetSwarmIndex swarmIndex) {
        Map<MobEntity, PetSocialData> cache = new HashMap<>();
        PetSocialData selfData = new PetSocialData(this.mob, this.petComponent, now);
        cache.put(this.mob, selfData);

        SocialContextSnapshot.NeighborSummary summary = SocialContextSnapshot.NeighborSummary.collect(
            swarmIndex, this.mob, this.petComponent, selfData, cache, now,
            SPACING_SAMPLE_RADIUS, SPACING_MAX_SAMPLES, SPACING_PACK_RADIUS);

        List<SocialContextSnapshot.NeighborSample> neighbors = summary.nearestWithin(
            SPACING_PACK_RADIUS * SPACING_PACK_RADIUS, SPACING_MAX_SAMPLES);
        if (neighbors.isEmpty()) {
            return FollowSpacingResult.empty();
        }

        Vec3d ownerPos = owner.getEntityPos();
        Vec3d petPos = this.mob.getEntityPos();
        Vec3d ownerToPet = petPos.subtract(ownerPos);
        double ownerDistanceSq = ownerToPet.lengthSquared();
        Vec3d forward;
        if (ownerDistanceSq < 1.0E-4) {
            forward = Vec3d.fromPolar(0.0f, this.mob.getYaw());
            if (forward.lengthSquared() < 1.0E-4) {
                forward = new Vec3d(0.0, 0.0, 1.0);
            } else {
                forward = forward.normalize();
            }
        } else {
            forward = ownerToPet.normalize();
        }
        Vec3d lateral = new Vec3d(-forward.z, 0.0, forward.x);

        double extension = 0.0;
        double lateralBias = 0.0;
        MobEntity focus = null;
        float bestBond = -1.0f;
        boolean selfCuddle = petComponent.isCrouchCuddleActive(now);

        for (SocialContextSnapshot.NeighborSample sample : neighbors) {
            PetSocialData neighborData = sample.data();
            if (neighborData.speed() > 0.3) {
                continue;
            }
            Vec3d neighborPos = new Vec3d(neighborData.x(), neighborData.y(), neighborData.z());
            Vec3d ownerToNeighbor = neighborPos.subtract(ownerPos);
            double neighborDistanceSq = ownerToNeighbor.lengthSquared();
            if (neighborDistanceSq < 1.0E-6) {
                continue;
            }
            Vec3d neighborDir = ownerToNeighbor.normalize();
            double closeness = Math.max(0.0, 1.0 - Math.sqrt(sample.squaredDistance()) / SPACING_PACK_RADIUS);

            if (ownerDistanceSq > 1.0E-4 && neighborDistanceSq < ownerDistanceSq && forward.dotProduct(neighborDir) > 0.75) {
                extension = Math.max(extension, closeness * 0.8);
            }

            lateralBias += (forward.x * neighborDir.z - forward.z * neighborDir.x) * closeness;

            PetComponent neighborComponent = neighborData.component();
            if (neighborComponent != null && selfCuddle && neighborComponent.isCrouchCuddleActive(now)) {
                extension = Math.min(extension, 0.25);
            }

            if (neighborData.bondStrength() > bestBond) {
                bestBond = neighborData.bondStrength();
                focus = sample.pet();
            }
        }

        float padding = (float) MathHelper.clamp(extension, 0.0, 1.5);
        double lateralOffset = MathHelper.clamp(-lateralBias, -1.0, 1.0) * LATERAL_PUSH_SCALE;
        Vec3d offset = forward.multiply(padding).add(lateral.multiply(lateralOffset));
        FollowSpacingResult result = new FollowSpacingResult(offset.x, offset.z, padding, focus);

        if (Petsplus.DEBUG_MODE) {
            debugSpacingSample(owner, neighbors, result, Math.sqrt(ownerDistanceSq), extension, lateralOffset);
        }

        return result;
    }

    private void debugSpacingSample(PlayerEntity owner,
                                    List<SocialContextSnapshot.NeighborSample> neighbors,
                                    FollowSpacingResult result,
                                    double ownerDistance,
                                    double extension,
                                    double lateralOffset) {
        if (neighbors.isEmpty()) {
            Petsplus.LOGGER.debug("[FollowSpacing] {} has no neighbours influencing spacing around {}",
                mob.getName().getString(), owner.getName().getString());
            return;
        }

        String focusName = result.focus != null ? result.focus.getName().getString() : "none";
        StringBuilder builder = new StringBuilder();
        for (SocialContextSnapshot.NeighborSample sample : neighbors) {
            PetSocialData data = sample.data();
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append("[")
                .append(sample.pet().getName().getString())
                .append(", d=")
                .append(formatDouble(Math.sqrt(sample.squaredDistance())))
                .append(", bond=")
                .append(formatDouble(data.bondStrength()))
                .append(", speed=")
                .append(formatDouble(data.speed()))
                .append("]");
        }

        String ownerDistanceStr = formatDouble(ownerDistance);
        String offsetXStr = formatDouble(result.offsetX);
        String offsetZStr = formatDouble(result.offsetZ);
        String paddingStr = formatDouble(result.padding);
        String extensionStr = formatDouble(extension);
        String lateralStr = formatDouble(lateralOffset);

        Petsplus.LOGGER.debug(
            "[FollowSpacing] {} -> owner {} | dist={} offset=({}, {}) padding={} focus={} ext={} lat={} peers={}",
            mob.getName().getString(), owner.getName().getString(), ownerDistanceStr,
            offsetXStr, offsetZStr, paddingStr, focusName, extensionStr, lateralStr,
            builder.toString()
        );
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void tryAlternativePathfinding(PlayerEntity owner, Vec3d moveTarget) {
        float oldWaterPenalty = this.mob.getPathfindingPenalty(PathNodeType.WATER);
        float oldFencePenalty = this.mob.getPathfindingPenalty(PathNodeType.FENCE);
        float oldDoorPenalty = this.mob.getPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED);

        this.mob.setPathfindingPenalty(PathNodeType.WATER, -1.0f);
        this.mob.setPathfindingPenalty(PathNodeType.FENCE, -2.0f);
        this.mob.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, -1.0f);

        boolean success = this.mob.getNavigation().startMovingTo(moveTarget.x, moveTarget.y, moveTarget.z, this.speed);

        this.mob.setPathfindingPenalty(PathNodeType.WATER, oldWaterPenalty);
        this.mob.setPathfindingPenalty(PathNodeType.FENCE, oldFencePenalty);
        this.mob.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, oldDoorPenalty);

        if (!success) {
            double distance = this.mob.squaredDistanceTo(moveTarget.x, moveTarget.y, moveTarget.z);
            if (distance > (activeFollowDistance * activeFollowDistance) && distance < (teleportDistance * teleportDistance)) {
                if (tryEmergencyTeleport(owner)) {
                    this.lastMoveTarget = null;
                }
            }
        }
    }

    private static final class FollowSpacingResult {
        static FollowSpacingResult empty() {
            return new FollowSpacingResult(0.0, 0.0, 0.0f, null);
        }

        final double offsetX;
        final double offsetZ;
        final float padding;
        final MobEntity focus;

        FollowSpacingResult(double offsetX, double offsetZ, float padding, MobEntity focus) {
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
            this.padding = padding;
            this.focus = focus;
        }
    }

    /**
     * Emergency teleport when normal pathfinding fails.
     */
    private boolean tryEmergencyTeleport(PlayerEntity owner) {
        if (!owner.getEntityWorld().getRegistryKey().equals(this.mob.getEntityWorld().getRegistryKey())) {
            return false;
        }

        WorldView world = this.mob.getEntityWorld();
        BlockPos ownerPos = owner.getBlockPos();

        double distanceToOwnerSq = this.mob.squaredDistanceTo(owner);
        if (Double.isInfinite(distanceToOwnerSq)) {
            return false;
        }

        for (int i = 0; i < 10; i++) {
            int x = ownerPos.getX() + this.mob.getRandom().nextInt(7) - 3;
            int z = ownerPos.getZ() + this.mob.getRandom().nextInt(7) - 3;
            int y = ownerPos.getY();

            BlockPos teleportPos = new BlockPos(x, y, z);
            if (isSafeTeleportLocation(world, teleportPos)) {
                this.mob.teleport(x + 0.5, y, z + 0.5, false);
                this.mob.getNavigation().stop();
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a position is safe for pet teleportation.
     */
    private boolean isSafeTeleportLocation(WorldView world, BlockPos pos) {
        return world.getBlockState(pos.down()).isSolidBlock(world, pos.down()) &&
               world.getBlockState(pos).isAir() &&
               world.getBlockState(pos.up()).isAir() &&
               world.getFluidState(pos).isEmpty();
    }

    private void sampleMomentum(long worldTime, boolean hesitating, double distanceToOwnerSq) {
        PetMoodEngine moodEngine = petComponent.getMoodEngine();
        if (moodEngine == null) {
            return;
        }

        boolean activelyMoving = this.mob.getNavigation().isFollowingPath()
            || distanceToOwnerSq > (this.activeFollowDistance * this.activeFollowDistance);

        if (!activelyMoving) {
            lastMomentumSampleTick = Long.MIN_VALUE;
            return;
        }

        if (lastMomentumSampleTick == Long.MIN_VALUE) {
            lastMomentumSampleTick = worldTime;
            return;
        }

        long delta = Math.max(1L, worldTime - lastMomentumSampleTick);
        lastMomentumSampleTick = worldTime;

        float intensity = hesitating ? 0.35f : 0.55f;
        moodEngine.recordBehavioralActivity(intensity, delta, PetMoodEngine.ActivityType.PHYSICAL);
    }

    private boolean isShelterHoldActive() {
        // Active if
        // - raining or thundering
        // - pet is not exposed to sky at its current position (sheltered)
        // - pet was recently wet (tracked by EnvironmentDesirabilitySignal / ShakeDryGoal)
        var world = this.mob.getEntityWorld();
        if (world == null || (!world.isRaining() && !world.isThundering())) {
            return false;
        }
        if (world.isSkyVisible(this.mob.getBlockPos())) {
            return false; // not sheltered
        }
        PetComponent pc = this.petComponent;
        if (pc == null) {
            return false;
        }
        Long lastWetTick = pc.getStateData(LAST_WET_TICK_KEY, Long.class);
        if (lastWetTick == null) {
            return false;
        }
        long now = world.getTime();
        // Consider "recently wet" within 120 seconds (2400 ticks), aligning with signals
        return now - lastWetTick <= 2400L;
    }

    private double resolveDynamicSpeed(double distanceToOwnerSq, boolean hesitating) {
        double base = this.speed;
        double distance = Math.sqrt(Math.max(distanceToOwnerSq, 0.0));
        double gap = Math.max(0.0, distance - this.activeFollowDistance);
        double distanceFactor = MathHelper.clamp(gap / Math.max(1.0f, this.activeFollowDistance), 0.0, 2.5);
        double momentumBoost = 1.0d;
        PetMoodEngine moodEngine = petComponent.getMoodEngine();
        if (moodEngine != null) {
            float momentum = MathHelper.clamp(moodEngine.getBehavioralMomentum(), 0.0f, 1.0f);
            BehaviouralEnergyProfile energy = moodEngine.getBehaviouralEnergyProfile();
            float stamina = energy != null ? MathHelper.clamp(energy.physicalStamina(), 0.0f, 1.0f) : 0.6f;
            double momentumScale = MathHelper.lerp(momentum, 0.85d, 1.15d);
            double staminaScale = MathHelper.lerp(stamina, 0.88d, 1.12d);
            momentumBoost = momentumScale * staminaScale;
        }
        double speed = base * momentumBoost + distanceFactor * 0.35d;
        if (hesitating) {
            speed += HESITATION_SPEED_BOOST;
        }
        return MathHelper.clamp(speed, MIN_DYNAMIC_SPEED, MAX_DYNAMIC_SPEED);
    }

    private void enterPathBlockedState(long now) {
        this.pathBlockedUntilTick = now + PATH_BLOCK_COOLDOWN_TICKS;
        this.failedPathTicks = 0;
        this.mob.getNavigation().stop();
        this.lastMoveTarget = null;
        if (!petComponent.isOnCooldown(FOLLOW_PATH_COOLDOWN_KEY)) {
            petComponent.setCooldown(FOLLOW_PATH_COOLDOWN_KEY, PATH_BLOCK_COOLDOWN_TICKS);
        }
        petComponent.getAIState().incrementQuirkCounter("follow_path_blocked");
    }

    private Vec3d computeSwarmCenter(PetSwarmIndex swarmIndex) {
        UUID ownerId = petComponent.getOwnerUuid();
        if (ownerId == null) {
            return null;
        }
        List<PetSwarmIndex.SwarmEntry> entries = swarmIndex.snapshotOwner(ownerId);
        if (entries.isEmpty()) {
            return null;
        }
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        int count = 0;
        for (PetSwarmIndex.SwarmEntry entry : entries) {
            sumX += entry.x();
            sumY += entry.y();
            sumZ += entry.z();
            count++;
        }
        if (count == 0) {
            return null;
        }
        return new Vec3d(sumX / count, sumY / count, sumZ / count);
    }
}


