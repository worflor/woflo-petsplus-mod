package woflo.petsplus.ai.goals.follow;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.world.WorldView;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.OwnerAssistAttackGoal;
import woflo.petsplus.ai.movement.MovementCommand;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.config.DebugSettings;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;
import woflo.petsplus.state.emotions.PetMoodEngine;

import java.util.EnumSet;
import java.util.Locale;
/**
 * Drives concrete navigation toward the pet's owner after blending movement modifiers.
 */
public class FollowOwnerAdaptiveGoal extends AdaptiveGoal {
    private static final String FOLLOW_PATH_COOLDOWN_KEY = "follow_path_blocked";
    // Loosen refresh epsilon to reduce target churn under micro-movements
    private static final double MOVE_TARGET_REFRESH_EPSILON = 0.125 * 0.125;
    private static final int PATH_FAILURE_TIMEOUT = 80;
    private static final int PATH_BLOCK_COOLDOWN_TICKS = 100;
    private static final double MIN_DYNAMIC_SPEED = 0.6d;
    private static final double MAX_DYNAMIC_SPEED = 1.95d;
    private static final double HESITATION_CLEAR_DISTANCE = 2.5d;
    private static final double OWNER_SPEED_DAMPING_THRESHOLD = 0.035d;
    private static final double THRESHOLD_SMOOTHING_ALPHA = 0.3d;
    private static final long PACK_SIZE_SAMPLE_INTERVAL = 20L;

    private final PetsplusTameable tameable;
    private final double baseSpeed;

    private float baseFollowDistance;
    private float teleportDistance;
    private float activeFollowDistance;
    private Vec3d lastMoveTarget;
    private int stuckCounter;
    private int failedPathTicks;
    private long pathBlockedUntilTick = Long.MIN_VALUE;
    private long lastMomentumSampleTick = Long.MIN_VALUE;
    private double smoothedStartDistance = Double.NaN;
    private double smoothedStopDistance = Double.NaN;
    private long debugLastReportTick = Long.MIN_VALUE;
    private long debugAccumulatedNanos = 0L;
    private int debugSampleTicks = 0;
    private int debugStartPathCount = 0;
    private int debugReusePathCount = 0;
    private double debugLastStartDistance = Double.NaN;
    private double debugLastStopDistance = Double.NaN;
    private long lastPathStartTick = Long.MIN_VALUE;
    private int consecutiveBudgetDenials = 0;
    private int cachedPackSize = 1;
    private long lastPackSizeSampleTick = Long.MIN_VALUE;

    public FollowOwnerAdaptiveGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.FOLLOW_OWNER), EnumSet.of(Control.MOVE, Control.LOOK));
        this.tameable = mob instanceof PetsplusTameable petsplus ? petsplus : null;
        this.baseSpeed = 1.15d;
        if (this.petComponent != null) {
            this.baseFollowDistance = FollowTuning.resolveFollowDistance(this.petComponent);
            this.teleportDistance = FollowTuning.resolveTeleportDistance(this.petComponent);
        } else {
            this.baseFollowDistance = FollowTuning.DEFAULT_BASELINE_DISTANCE;
            this.teleportDistance = FollowTuning.DEFAULT_TELEPORT_DISTANCE;
        }
    }

    @Override
    protected boolean canStartGoal() {
        if (tameable == null || petComponent == null || movementDirector == null) {
            return false;
        }
        if (!tameable.petsplus$isTamed() || tameable.petsplus$isSitting()) {
            return false;
        }
        if (mob.isSleeping()) {
            return false;
        }
        LivingEntity livingOwner = tameable.petsplus$getOwner();
        if (!(livingOwner instanceof PlayerEntity owner) || owner.isSpectator()) {
            return false;
        }

        double distanceSq = mob.squaredDistanceTo(owner);
        if (Double.isNaN(distanceSq)) {
            return false;
        }

        long now = mob.getEntityWorld().getTime();
        boolean requiresImmediateReturn = distanceSq > teleportDistance * teleportDistance;

        if (!requiresImmediateReturn) {
            if (pathBlockedUntilTick != Long.MIN_VALUE && now < pathBlockedUntilTick) {
                return false;
            }
            if (petComponent.isOnCooldown(FOLLOW_PATH_COOLDOWN_KEY)) {
                return false;
            }
        }

        if (petComponent.getAIState().isPanicking()) {
            return false;
        }
        if (isMajorActivityActive()) {
            return false;
        }

        if (requiresImmediateReturn) {
            return true;
        }

        Vec3d ownerVelocity = owner.getVelocity();
        boolean ownerIsMoving = ownerVelocity.horizontalLengthSquared() > OWNER_SPEED_DAMPING_THRESHOLD;
        boolean hesitating = OwnerAssistAttackGoal.isPetHesitating(petComponent, now);
        boolean moodHoldActive = petComponent.isMoodFollowHoldActive(now);
        float moodDistanceBonus = moodHoldActive ? petComponent.getMoodFollowDistanceBonus(now) : 0.0f;
        double baseDistance = baseFollowDistance + moodDistanceBonus;
        movementDirector.previewMovement(owner.getEntityPos(), baseDistance, baseSpeed);
        double startThresholdSq = computeStartThresholdSqFromBaseline(baseDistance, ownerIsMoving);
        if (isDebugLoggingEnabled()) {
            // Track for summaries; avoid per-tick chat spam
            double startDistance = Math.sqrt(startThresholdSq);
            debugLastStartDistance = startDistance;
        }

        if (hesitating) {
            return true;
        }
        if (moodHoldActive && distanceSq <= startThresholdSq) {
            return false;
        }
        return distanceSq > startThresholdSq;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (tameable == null || petComponent == null || movementDirector == null) {
            return false;
        }
        LivingEntity livingOwner = tameable.petsplus$getOwner();
        if (!(livingOwner instanceof PlayerEntity owner) || owner.isSpectator()) {
            return false;
        }
        if (mob.isSleeping() || tameable.petsplus$isSitting()) {
            return false;
        }
        long now = mob.getEntityWorld().getTime();
        double distanceSq = mob.squaredDistanceTo(owner);
        if (Double.isNaN(distanceSq)) {
            return false;
        }
        boolean requiresImmediateReturn = distanceSq > teleportDistance * teleportDistance;

        if (!requiresImmediateReturn) {
            if (petComponent.isOnCooldown(FOLLOW_PATH_COOLDOWN_KEY)) {
                return false;
            }
            if (pathBlockedUntilTick != Long.MIN_VALUE && now < pathBlockedUntilTick) {
                return false;
            }
        }

        if (petComponent.getAIState().isPanicking()) {
            return false;
        }
        if (isMajorActivityActive()) {
            return false;
        }

        if (requiresImmediateReturn) {
            return true;
        }
        boolean ownerIsMoving = owner.getVelocity().horizontalLengthSquared() > OWNER_SPEED_DAMPING_THRESHOLD;
        boolean hesitating = OwnerAssistAttackGoal.isPetHesitating(petComponent, now);
        if (hesitating) {
            return true;
        }
        boolean moodHoldActive = petComponent.isMoodFollowHoldActive(now);
        float moodDistanceBonus = moodHoldActive ? petComponent.getMoodFollowDistanceBonus(now) : 0.0f;
        double baseDistance = baseFollowDistance + moodDistanceBonus;
        double baselineStartDistance = computeStartThresholdDistance(baseDistance, ownerIsMoving);
        movementDirector.previewMovement(owner.getEntityPos(), baseDistance, baseSpeed);
        double continueThresholdSq = computeStopThresholdSqFromBaseline(baseDistance, ownerIsMoving);
        // Threshold changes are tracked via aggregated snapshots; suppress direct chat spam.
        if (isDebugLoggingEnabled()) {
            debugLastStartDistance = baselineStartDistance;
            debugLastStopDistance = Math.sqrt(continueThresholdSq);
        }
        if (moodHoldActive && distanceSq <= continueThresholdSq) {
            return false;
        }
        return distanceSq > continueThresholdSq;
    }

    @Override
    protected void onStartGoal() {
        stuckCounter = 0;
        failedPathTicks = 0;
        consecutiveBudgetDenials = 0;
        pathBlockedUntilTick = Long.MIN_VALUE;
        lastMoveTarget = null;
        long now = mob.getEntityWorld().getTime();
        lastMomentumSampleTick = now;
        smoothedStartDistance = Double.NaN;
        smoothedStopDistance = Double.NaN;
        if (petComponent != null) {
            this.baseFollowDistance = FollowTuning.resolveFollowDistance(petComponent);
            this.teleportDistance = FollowTuning.resolveTeleportDistance(petComponent);
            if (petComponent.getMoodEngine() != null) {
                petComponent.getMoodEngine().recordBehavioralActivity(0.4f, 6L, PetMoodEngine.ActivityType.PHYSICAL);
            }
        }
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        lastMoveTarget = null;
        failedPathTicks = 0;
        consecutiveBudgetDenials = 0;
        lastMomentumSampleTick = Long.MIN_VALUE;
        smoothedStartDistance = Double.NaN;
        smoothedStopDistance = Double.NaN;
        cachedPackSize = 1;
        lastPackSizeSampleTick = Long.MIN_VALUE;
    }

    @Override
    protected void onTickGoal() {
        LivingEntity livingOwner = tameable != null ? tameable.petsplus$getOwner() : null;
        if (!(livingOwner instanceof PlayerEntity owner)) {
            requestStop();
            return;
        }
        if (owner.isSpectator()) {
            mob.getNavigation().stop();
            requestStop();
            return;
        }
        if (mob.isSleeping() || tameable.petsplus$isSitting()) {
            mob.getNavigation().stop();
            requestStop();
            return;
        }
        long now = owner.getEntityWorld().getTime();

        long debugStartNano = 0L;
        boolean debugStartedPath = false;
        boolean debugReusedPath = false;
        if (isDebugLoggingEnabled()) {
            debugStartNano = System.nanoTime();
        }

        try {
            this.baseFollowDistance = FollowTuning.resolveFollowDistance(petComponent);
            this.teleportDistance = FollowTuning.resolveTeleportDistance(petComponent);

            double distanceToOwnerSq = mob.squaredDistanceTo(owner);
            if (Double.isNaN(distanceToOwnerSq)) {
                mob.getNavigation().stop();
                lastMoveTarget = null;
                return;
            }

            boolean teleportOverride = distanceToOwnerSq > (teleportDistance * teleportDistance);
            if (!teleportOverride
                && (petComponent.isOnCooldown(FOLLOW_PATH_COOLDOWN_KEY)
                    || (pathBlockedUntilTick != Long.MIN_VALUE && now < pathBlockedUntilTick))) {
                mob.getNavigation().stop();
                return;
            }

            Vec3d ownerVelocity = owner.getVelocity();
            boolean ownerIsMoving = ownerVelocity.horizontalLengthSquared() > OWNER_SPEED_DAMPING_THRESHOLD;
            boolean hesitating = OwnerAssistAttackGoal.isPetHesitating(petComponent, now);
            boolean moodHoldActive = petComponent.isMoodFollowHoldActive(now);
            float moodDistanceBonus = moodHoldActive ? petComponent.getMoodFollowDistanceBonus(now) : 0.0f;
            double baseDistance = baseFollowDistance + moodDistanceBonus;
            double baselineStartDistance = computeStartThresholdDistance(baseDistance, ownerIsMoving);

            distanceToOwnerSq = mob.squaredDistanceTo(owner);
            double baseSpeed = resolveDynamicSpeed(distanceToOwnerSq, baseDistance);
            MovementCommand command = movementDirector.resolveMovement(goalId, owner.getEntityPos(), baseDistance, baseSpeed);
            this.activeFollowDistance = (float) command.desiredDistance();
            Vec3d moveTarget = command.targetPosition();
            double adjustedSpeed = command.speed();

        if (isDebugLoggingEnabled()) {
            debugLastStartDistance = baselineStartDistance;
        }

            mob.getLookControl().lookAt(owner, hesitating ? 20.0f : 10.0f, mob.getMaxLookPitchChange());

            boolean worldsMatch = owner.getEntityWorld().getRegistryKey().equals(mob.getEntityWorld().getRegistryKey());
            if (!worldsMatch || Double.isInfinite(distanceToOwnerSq)) {
                mob.getNavigation().stop();
                lastMoveTarget = null;
                return;
            }

            double baselineStopDistance = computeStopThresholdDistance(baseDistance, ownerIsMoving);
        if (isDebugLoggingEnabled()) {
            debugLastStopDistance = baselineStopDistance;
        }

            if (moodHoldActive && !hesitating && distanceToOwnerSq <= (baselineStopDistance * baselineStopDistance)) {
                if (!mob.getNavigation().isIdle()) {
                    mob.getNavigation().stop();
                }
                lastMoveTarget = null;
                return;
            }

            if (distanceToOwnerSq > (teleportDistance * teleportDistance)) {
                if (tryEmergencyTeleport(owner)) {
                    lastMoveTarget = null;
                    return;
                }
            }

            if (hesitating && distanceToOwnerSq <= (HESITATION_CLEAR_DISTANCE * HESITATION_CLEAR_DISTANCE)) {
                OwnerAssistAttackGoal.clearAssistHesitation(petComponent);
            }

            boolean navigationIdle = mob.getNavigation().isIdle();
            boolean targetChanged = lastMoveTarget == null
                || lastMoveTarget.squaredDistanceTo(moveTarget) > MOVE_TARGET_REFRESH_EPSILON;
            boolean budgetDenied = false;
            boolean attemptedPathStart = false;

            if (navigationIdle || targetChanged) {
                boolean started = false;
                // Restart cooldown to prevent thrash
                if (now == Long.MIN_VALUE || lastPathStartTick == Long.MIN_VALUE || (now - lastPathStartTick) >= 8) {
                    if (mob.getEntityWorld() instanceof ServerWorld sw) {
                        java.util.UUID ownerId = owner.getUuid();
                        int packSize = getPackSize(owner, now);
                        int perTickLimit = woflo.petsplus.policy.AIBudgetPolicy.pathStartsPerOwnerPerTick(ownerIsMoving, packSize);
                        boolean allowed = woflo.petsplus.state.coordination.PathBudgetManager.get(sw)
                            .tryConsume(ownerId, now, perTickLimit);
                        if (allowed) {
                            attemptedPathStart = true;
                            started = mob.getNavigation().startMovingTo(moveTarget.x, moveTarget.y, moveTarget.z, adjustedSpeed);
                            if (started) {
                                lastPathStartTick = now;
                            }
                        } else {
                            budgetDenied = true;
                        }
                    } else {
                        attemptedPathStart = true;
                        started = mob.getNavigation().startMovingTo(moveTarget.x, moveTarget.y, moveTarget.z, adjustedSpeed);
                        if (started) {
                            lastPathStartTick = now;
                        }
                    }
                }
                if (isDebugLoggingEnabled() && started) {
                    debugStartedPath = true;
                }
            } else {
                mob.getNavigation().setSpeed(adjustedSpeed);
                if (isDebugLoggingEnabled()) {
                    debugReusedPath = true;
                }
            }
            lastMoveTarget = moveTarget;

            if (budgetDenied) {
                consecutiveBudgetDenials = Math.min(consecutiveBudgetDenials + 1, PATH_FAILURE_TIMEOUT);
            } else if (!navigationIdle || attemptedPathStart) {
                consecutiveBudgetDenials = 0;
            }

            if (budgetDenied && consecutiveBudgetDenials >= 5) {
                double teleportGuard = teleportDistance * 0.9d;
                double teleportGuardSq = teleportGuard * teleportGuard;
                if (distanceToOwnerSq >= teleportGuardSq) {
                    if (tryEmergencyTeleport(owner)) {
                        lastMoveTarget = null;
                        consecutiveBudgetDenials = 0;
                        return;
                    }
                }
            }

            boolean followingPath = mob.getNavigation().isFollowingPath();
            if (!followingPath) {
                stuckCounter++;
                if (budgetDenied) {
                    failedPathTicks = Math.max(0, failedPathTicks - 1);
                } else if (distanceToOwnerSq > (this.activeFollowDistance * this.activeFollowDistance)) {
                    failedPathTicks = Math.min(PATH_FAILURE_TIMEOUT + 20, failedPathTicks + 1);
                }
                if (stuckCounter > 60) {
                    tryAlternativePathfinding(owner, moveTarget);
                    stuckCounter = 0;
                }
            } else {
                stuckCounter = 0;
                failedPathTicks = 0;
                consecutiveBudgetDenials = 0;
            }

            if (failedPathTicks > PATH_FAILURE_TIMEOUT) {
                enterPathBlockedState(now);
                return;
            }

            sampleMomentum(now, hesitating, distanceToOwnerSq);
        } finally {
            if (isDebugLoggingEnabled()) {
                recordFollowDebug(owner.getUuid(), now, System.nanoTime() - debugStartNano, debugStartedPath, debugReusedPath);
            }
        }
    }

    @Override
    protected float calculateEngagement() {
        return 0.65f;
    }

    private boolean isMajorActivityActive() {
        Identifier activeId = petComponent.getAIState().getActiveMajorGoal();
        if (activeId == null) {
            return false;
        }
        GoalDefinition activeDefinition = GoalRegistry.get(activeId).orElse(null);
        return activeDefinition != null && activeDefinition.marksMajorActivity();
    }

    private double resolveDynamicSpeed(double distanceToOwnerSq, double desiredDistance) {
        double base = this.baseSpeed;
        double distance = Math.sqrt(Math.max(distanceToOwnerSq, 0.0));
        double gap = Math.max(0.0, distance - desiredDistance);
        double distanceFactor = MathHelper.clamp(gap / Math.max(1.0f, desiredDistance), 0.0, 2.5);
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
        return MathHelper.clamp(speed, MIN_DYNAMIC_SPEED, MAX_DYNAMIC_SPEED);
    }

    private double computeStartThresholdDistance(double baselineDistance, boolean ownerIsMoving) {
        double threshold = FollowDistanceHeuristics.computeStartThreshold(baselineDistance, ownerIsMoving);
        return updateSmoothedStartDistance(threshold);
    }

    private double computeStopThresholdDistance(double baselineDistance, boolean ownerIsMoving) {
        double threshold = FollowDistanceHeuristics.computeStopThreshold(baselineDistance, ownerIsMoving);
        return updateSmoothedStopDistance(threshold);
    }

    private double updateSmoothedStartDistance(double target) {
        if (Double.isNaN(smoothedStartDistance)) {
            smoothedStartDistance = target;
        } else {
            smoothedStartDistance += (target - smoothedStartDistance) * THRESHOLD_SMOOTHING_ALPHA;
        }
        return smoothedStartDistance;
    }

    private double updateSmoothedStopDistance(double target) {
        if (Double.isNaN(smoothedStopDistance)) {
            smoothedStopDistance = target;
        } else {
            smoothedStopDistance += (target - smoothedStopDistance) * THRESHOLD_SMOOTHING_ALPHA;
        }
        return smoothedStopDistance;
    }

    private double computeStartThresholdSqFromBaseline(double baselineDistance, boolean ownerIsMoving) {
        double distance = computeStartThresholdDistance(baselineDistance, ownerIsMoving);
        return distance * distance;
    }

    private double computeStopThresholdSqFromBaseline(double baselineDistance, boolean ownerIsMoving) {
        double distance = computeStopThresholdDistance(baselineDistance, ownerIsMoving);
        return distance * distance;
    }

    private void recordFollowDebug(java.util.UUID ownerId, long tick, long durationNanos, boolean startedPath, boolean reusedPath) {
        if (!isDebugLoggingEnabled()) {
            return;
        }
        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        // Delegate to centralized aggregator for periodic owner-level summaries.
        woflo.petsplus.debug.DebugSnapshotAggregator.recordFollow(
            serverWorld.getServer(),
            ownerId,
            mob.getDisplayName().getString(),
            tick,
            durationNanos,
            startedPath,
            reusedPath,
            smoothedStartDistance,
            smoothedStopDistance);
    }

    private void tryAlternativePathfinding(PlayerEntity owner, Vec3d moveTarget) {
        float oldWaterPenalty = mob.getPathfindingPenalty(PathNodeType.WATER);
        float oldFencePenalty = mob.getPathfindingPenalty(PathNodeType.FENCE);
        float oldDoorPenalty = mob.getPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED);

        mob.setPathfindingPenalty(PathNodeType.WATER, -1.0f);
        mob.setPathfindingPenalty(PathNodeType.FENCE, -2.0f);
        mob.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, -1.0f);

        boolean success = mob.getNavigation().startMovingTo(moveTarget.x, moveTarget.y, moveTarget.z, baseSpeed);

        mob.setPathfindingPenalty(PathNodeType.WATER, oldWaterPenalty);
        mob.setPathfindingPenalty(PathNodeType.FENCE, oldFencePenalty);
        mob.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, oldDoorPenalty);

        if (!success) {
            double distance = mob.squaredDistanceTo(moveTarget.x, moveTarget.y, moveTarget.z);
            if (distance > (activeFollowDistance * activeFollowDistance)
                && distance < (teleportDistance * teleportDistance)) {
                if (tryEmergencyTeleport(owner)) {
                    lastMoveTarget = null;
                }
            }
        }
    }

    private boolean tryEmergencyTeleport(PlayerEntity owner) {
        if (!owner.getEntityWorld().getRegistryKey().equals(mob.getEntityWorld().getRegistryKey())) {
            return false;
        }

        WorldView world = mob.getEntityWorld();
        BlockPos ownerPos = owner.getBlockPos();

        boolean canFly = MobCapabilities.canFly(mob);
        boolean isAquatic = MobCapabilities.prefersWater(mob);

        double distanceToOwnerSq = mob.squaredDistanceTo(owner);
        if (Double.isInfinite(distanceToOwnerSq)) {
            return false;
        }

        for (int i = 0; i < 10; i++) {
            int x = ownerPos.getX() + mob.getRandom().nextInt(7) - 3;
            int z = ownerPos.getZ() + mob.getRandom().nextInt(7) - 3;
            int y = ownerPos.getY();

            BlockPos teleportPos = new BlockPos(x, y, z);
            if (isSafeTeleportLocation(world, teleportPos, canFly, isAquatic)) {
                mob.teleport(x + 0.5, y, z + 0.5, false);
                mob.getNavigation().stop();
                return true;
            }
        }

        return false;
    }

    private boolean isSafeTeleportLocation(WorldView world, BlockPos pos, boolean canFly, boolean isAquatic) {
        BlockPos belowPos = pos.down();
        BlockState belowState = world.getBlockState(belowPos);
        BlockState bodyState = world.getBlockState(pos);
        BlockState headState = world.getBlockState(pos.up());
        FluidState bodyFluid = world.getFluidState(pos);
        FluidState headFluid = world.getFluidState(pos.up());

        if (isAquatic) {
            boolean waterAtBody = bodyFluid.isIn(FluidTags.WATER);
            boolean headClear = headState.isAir() || headFluid.isIn(FluidTags.WATER);
            if (waterAtBody && headClear) {
                boolean support = belowState.isSolidBlock(world, belowPos)
                    || world.getFluidState(belowPos).isIn(FluidTags.WATER);
                if (support) {
                    return true;
                }
            }
        }

        if (!bodyState.isAir() || !headState.isAir()) {
            return false;
        }
        if (!bodyFluid.isEmpty() || !headFluid.isEmpty()) {
            return false;
        }

        if (!canFly) {
            return belowState.isSolidBlock(world, belowPos);
        }

        return belowState.isSolidBlock(world, belowPos);
    }

    private void sampleMomentum(long worldTime, boolean hesitating, double distanceToOwnerSq) {
        PetMoodEngine moodEngine = petComponent.getMoodEngine();
        if (moodEngine == null) {
            return;
        }

        boolean activelyMoving = mob.getNavigation().isFollowingPath()
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

    private int getPackSize(PlayerEntity owner, long now) {
        if (lastPackSizeSampleTick == Long.MIN_VALUE || (now - lastPackSizeSampleTick) >= PACK_SIZE_SAMPLE_INTERVAL) {
            cachedPackSize = samplePackSize(owner);
            lastPackSizeSampleTick = now;
        }
        return cachedPackSize;
    }

    private int samplePackSize(PlayerEntity owner) {
        if (petComponent == null) {
            return 1;
        }
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
        return Math.max(1, size);
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
