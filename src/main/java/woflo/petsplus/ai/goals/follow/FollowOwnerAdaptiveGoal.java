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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.world.WorldView;
import net.minecraft.util.shape.VoxelShape;
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
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.processing.OwnerFocusSnapshot;

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
    private static final double[] TELEPORT_RADII = {1.75d, 2.5d, 3.5d};
    private static final double[][] TELEPORT_PATTERN = {
        {-1.0d, 0.0d},
        {-0.85d, 0.55d},
        {-0.85d, -0.55d},
        {-0.45d, 0.9d},
        {-0.45d, -0.9d},
        {-0.1d, 0.0d},
        {0.35d, 0.75d},
        {0.35d, -0.75d},
        {0.65d, 0.4d},
        {0.65d, -0.4d}
    };
    private static final int[] TELEPORT_PATTERN_RIGHT_ORDER = {0, 1, 3, 6, 8, 5, 2, 4, 7, 9};
    private static final int[] TELEPORT_PATTERN_LEFT_ORDER = {0, 2, 4, 7, 9, 5, 1, 3, 6, 8};
    private static final double TELEPORT_LATERAL_BIAS_EPSILON = 0.15d;
    private static final int[] TELEPORT_VERTICAL_OFFSETS = {0, -1, 1};
    private static final long OWNER_BUSY_COURTESY_WINDOW = 40L;
    private static final double OWNER_BUSY_BASE_DISTANCE_BONUS = 2.25d;
    private static final double COURTESY_MAX_DISTANCE_BONUS = 3.75d;

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
        CourtesyProfile courtesy = sampleCourtesyProfile(now);
        double courtesyDistance = baseFollowDistance + moodDistanceBonus + courtesy.distanceBonus();
        movementDirector.previewMovement(owner.getEntityPos(), courtesyDistance, baseSpeed);
        double startThresholdSq = computeStartThresholdSqFromBaseline(courtesyDistance,
            courtesy.courtesyActive() ? false : ownerIsMoving);
        if (isDebugLoggingEnabled()) {
            // Track for summaries; avoid per-tick chat spam
            double startDistance = Math.sqrt(startThresholdSq);
            debugLastStartDistance = startDistance;
        }

        if (hesitating) {
            return true;
        }
        if (courtesy.courtesyActive() && !requiresImmediateReturn
            && distanceSq <= (courtesyDistance * courtesyDistance)) {
            return false;
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
        CourtesyProfile courtesy = sampleCourtesyProfile(now);
        double courtesyDistance = baseFollowDistance + moodDistanceBonus + courtesy.distanceBonus();
        double baselineStartDistance = computeStartThresholdDistance(courtesyDistance,
            courtesy.courtesyActive() ? false : ownerIsMoving);
        movementDirector.previewMovement(owner.getEntityPos(), courtesyDistance, baseSpeed);
        double continueThresholdSq = computeStopThresholdSqFromBaseline(courtesyDistance,
            courtesy.courtesyActive() ? false : ownerIsMoving);
        // Threshold changes are tracked via aggregated snapshots; suppress direct chat spam.
        if (isDebugLoggingEnabled()) {
            debugLastStartDistance = baselineStartDistance;
            debugLastStopDistance = Math.sqrt(continueThresholdSq);
        }
        if (courtesy.courtesyActive() && distanceSq <= (courtesyDistance * courtesyDistance)) {
            return false;
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
        if (petComponent != null) {
            petComponent.updateOwnerCourtesyState(PetComponent.OwnerCourtesyState.inactive());
        }
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
            CourtesyProfile courtesy = sampleCourtesyProfile(now);
            double courtesyDistance = baseFollowDistance + moodDistanceBonus + courtesy.distanceBonus();
            double baseDistance = courtesyDistance;
            double baselineStartDistance = computeStartThresholdDistance(baseDistance,
                courtesy.courtesyActive() ? false : ownerIsMoving);

            distanceToOwnerSq = mob.squaredDistanceTo(owner);
            if (courtesy.courtesyActive() && !teleportOverride
                && distanceToOwnerSq <= (courtesyDistance * courtesyDistance)) {
                if (!mob.getNavigation().isIdle()) {
                    mob.getNavigation().stop();
                }
                lastMoveTarget = null;
                return;
            }

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

            double baselineStopDistance = computeStopThresholdDistance(baseDistance,
                courtesy.courtesyActive() ? false : ownerIsMoving);
        if (isDebugLoggingEnabled()) {
            debugLastStopDistance = baselineStopDistance;
        }

            if ((courtesy.courtesyActive() && distanceToOwnerSq <= (courtesyDistance * courtesyDistance))
                || (moodHoldActive && !hesitating && distanceToOwnerSq <= (baselineStopDistance * baselineStopDistance))) {
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

            if (budgetDenied) { // Pathfinding budget was denied
                consecutiveBudgetDenials++;
                if (consecutiveBudgetDenials >= 5) { // After 5 consecutive failures...
                    // Go on cooldown. The duration increases with more failures (exponential backoff).
                    petComponent.setCooldown(FOLLOW_PATH_COOLDOWN_KEY, Math.min(100, 10 * consecutiveBudgetDenials)); // Cooldown up to 5 seconds
                    requestStop(); // Immediately stop the goal. It won't run again until the cooldown expires.
                }
            } else if (attemptedPathStart || !navigationIdle) { // Reset if we successfully started a path or are already moving.
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

    private CourtesyProfile sampleCourtesyProfile(long now) {
        if (petComponent == null) {
            return CourtesyProfile.inactive();
        }
        boolean courtesyActive = petComponent.isOwnerBusy(now, OWNER_BUSY_COURTESY_WINDOW);
        if (!courtesyActive) {
            petComponent.updateOwnerCourtesyState(PetComponent.OwnerCourtesyState.inactive());
            return CourtesyProfile.inactive();
        }
        OwnerFocusSnapshot focus = petComponent.getCourtesyFocusSnapshot(now, OWNER_BUSY_COURTESY_WINDOW);
        if (focus == null || !focus.isBusy()) {
            focus = petComponent.getLastBusyOwnerFocusSnapshot();
        }
        if (focus == null || !focus.isBusy()) {
            petComponent.updateOwnerCourtesyState(PetComponent.OwnerCourtesyState.inactive());
            return CourtesyProfile.inactive();
        }
        long lastBusyTick = petComponent.getLastOwnerActivityTick();
        long ticksSinceBusy = lastBusyTick == Long.MIN_VALUE ? Long.MAX_VALUE : Math.max(0L, now - lastBusyTick);
        double severity = computeCourtesySeverity(focus);
        double recencyScale = computeCourtesyRecencyScale(ticksSinceBusy, OWNER_BUSY_COURTESY_WINDOW);
        double distanceBonus = OWNER_BUSY_BASE_DISTANCE_BONUS * severity * recencyScale;
        if (distanceBonus <= 0.05d) {
            petComponent.updateOwnerCourtesyState(PetComponent.OwnerCourtesyState.inactive());
            return CourtesyProfile.inactive();
        }
        distanceBonus = MathHelper.clamp(distanceBonus, 0.35d, COURTESY_MAX_DISTANCE_BONUS);
        float lateralInflation = (float) MathHelper.clamp(1.0d + distanceBonus / 4.5d, 1.0d, 1.8d);
        float paddingInflation = (float) MathHelper.clamp(1.0d + distanceBonus / 3.25d, 1.0d, 1.85d);
        long expiryTick = lastBusyTick == Long.MIN_VALUE
            ? now + OWNER_BUSY_COURTESY_WINDOW
            : lastBusyTick + OWNER_BUSY_COURTESY_WINDOW;
        PetComponent.OwnerCourtesyState courtesyState = new PetComponent.OwnerCourtesyState(true, distanceBonus,
            lateralInflation, paddingInflation, focus, expiryTick);
        petComponent.updateOwnerCourtesyState(courtesyState);
        return new CourtesyProfile(true, distanceBonus, focus);
    }

    static double computeCourtesySeverity(OwnerFocusSnapshot focus) {
        if (focus == null || !focus.isBusy()) {
            return 0.0d;
        }
        if (focus.isSleeping()) {
            return 1.7d;
        }
        if (focus.screenOpen()) {
            return focus.usingItem() ? 1.45d : 1.25d;
        }
        if (focus.usingItem()) {
            return focus.crouching() ? 1.1d : 1.0d;
        }
        if (focus.handsBusy()) {
            return 0.85d;
        }
        if (focus.crouching()) {
            return 0.7d;
        }
        return 0.5d;
    }

    static double computeCourtesyRecencyScale(long ticksSinceBusy, long courtesyWindow) {
        if (ticksSinceBusy == Long.MAX_VALUE) {
            return 0.0d;
        }
        if (courtesyWindow <= 0L) {
            return 1.0d;
        }
        if (ticksSinceBusy >= courtesyWindow) {
            return 0.0d;
        }
        double normalized = 1.0d - (double) ticksSinceBusy / (double) courtesyWindow;
        return MathHelper.clamp(normalized, 0.0d, 1.0d);
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
        // Prevent debug logging during server shutdown to avoid client-side rendering issues.
        // Assuming StateManager has a static method `isServerStopping()` to check this state.
        if (StateManager.isServerStopping()) {
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

        float yawDegrees = owner.getYaw(1.0f);
        float yawRadians = yawDegrees * ((float) Math.PI / 180.0f);
        double forwardX = -MathHelper.sin(yawRadians);
        double forwardZ = MathHelper.cos(yawRadians);
        double rightX = forwardZ;
        double rightZ = -forwardX;

        double baseX = ownerPos.getX() + 0.5d;
        double baseZ = ownerPos.getZ() + 0.5d;
        double mobDeltaX = mob.getX() - baseX;
        double mobDeltaZ = mob.getZ() - baseZ;
        double lateralOffset = mobDeltaX * rightX + mobDeltaZ * rightZ;
        int[] patternOrder;
        if (lateralOffset > TELEPORT_LATERAL_BIAS_EPSILON) {
            patternOrder = TELEPORT_PATTERN_RIGHT_ORDER;
        } else if (lateralOffset < -TELEPORT_LATERAL_BIAS_EPSILON) {
            patternOrder = TELEPORT_PATTERN_LEFT_ORDER;
        } else if (((mob.age + mob.getId()) & 1) != 0) {
            patternOrder = TELEPORT_PATTERN_LEFT_ORDER;
        } else {
            patternOrder = TELEPORT_PATTERN_RIGHT_ORDER;
        }

        double ownerDistance = Math.sqrt(mob.squaredDistanceTo(owner));
        double closeness = MathHelper.clamp(ownerDistance / Math.max(1.0d, (double) teleportDistance), 0.0d, 1.0d);
        double radiusScale = 0.85d + (0.35d * closeness);

        int[] verticalOffsets = orderVerticalOffsets(ownerPos.getY(), mob.getBlockY());
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (double radius : TELEPORT_RADII) {
            double scaledRadius = radius * radiusScale;
            for (int orderIndex = 0; orderIndex < patternOrder.length; orderIndex++) {
                int idx = patternOrder[orderIndex];
                double forwardFactor = TELEPORT_PATTERN[idx][0];
                double strafeFactor = TELEPORT_PATTERN[idx][1];
                double offsetX = (forwardFactor * scaledRadius * forwardX) + (strafeFactor * scaledRadius * rightX);
                double offsetZ = (forwardFactor * scaledRadius * forwardZ) + (strafeFactor * scaledRadius * rightZ);

                int x = MathHelper.floor(baseX + offsetX);
                int z = MathHelper.floor(baseZ + offsetZ);
                int baseY = ownerPos.getY();

                for (int dy : verticalOffsets) {
                    mutable.set(x, baseY + dy, z);
                    if (isSafeTeleportLocation(world, mutable, canFly, isAquatic)) {
                        mob.teleport(x + 0.5d, baseY + dy, z + 0.5d, false);
                        mob.getNavigation().stop();
                        return true;
                    }
                }
            }
        }

        // Fall back to a simple local sweep near the owner to handle cramped interiors.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int x = ownerPos.getX() + dx;
                int z = ownerPos.getZ() + dz;
                int baseY = ownerPos.getY();
                for (int dy : verticalOffsets) {
                    mutable.set(x, baseY + dy, z);
                    if (isSafeTeleportLocation(world, mutable, canFly, isAquatic)) {
                        mob.teleport(x + 0.5d, baseY + dy, z + 0.5d, false);
                        mob.getNavigation().stop();
                        return true;
                    }
                }
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
        FluidState supportFluid = world.getFluidState(belowPos);

        if (isAquatic) {
            boolean waterAtBody = bodyFluid.isIn(FluidTags.WATER);
            boolean headClear = headState.isAir() || headFluid.isIn(FluidTags.WATER);
            if (waterAtBody && headClear) {
                double aquaticSupport = computeSupportScore(world, belowPos, belowState, supportFluid, canFly, true);
                if (aquaticSupport >= 0.5d) {
                    return true;
                }
            }
        }

        if (!hasAirClearance(bodyState, headState, bodyFluid, headFluid)) {
            return false;
        }

        double supportScore = computeSupportScore(world, belowPos, belowState, supportFluid, canFly, isAquatic);
        double requiredScore = canFly ? 0.25d : 0.6d;
        return supportScore >= requiredScore;
    }

    private boolean hasAirClearance(BlockState bodyState, BlockState headState, FluidState bodyFluid, FluidState headFluid) {
        return bodyState.isAir() && headState.isAir() && bodyFluid.isEmpty() && headFluid.isEmpty();
    }

    private double computeSupportScore(WorldView world, BlockPos pos, BlockState state, FluidState fluid, boolean canFly, boolean isAquatic) {
        double support = 0.0d;

        if (!state.isAir()) {
            if (state.isSolidBlock(world, pos)) {
                support = 1.0d;
            } else if (state.isSideSolidFullSquare(world, pos, Direction.UP)) {
                support = 0.9d;
            } else {
                VoxelShape shape = state.getCollisionShape(world, pos);
                if (!shape.isEmpty()) {
                    double height = MathHelper.clamp(shape.getMax(Direction.Axis.Y), 0.0d, 1.0d);
                    double easedHeight = MathHelper.square(height);
                    support = Math.max(support, 0.5d + 0.5d * height);
                    support = Math.max(support, easedHeight);
                }
            }
        }

        if (!fluid.isEmpty()) {
            if (fluid.isIn(FluidTags.WATER)) {
                double waterScore = isAquatic ? 1.0d : 0.2d;
                support = Math.max(support, waterScore);
            } else {
                support = Math.min(support, 0.05d);
            }
        }

        if (canFly && support < 0.5d) {
            double aerialTolerance = fluid.isIn(FluidTags.WATER) ? 0.6d : (state.isAir() ? 0.5d : 0.35d);
            support = Math.max(support, aerialTolerance);
        }

        return MathHelper.clamp(support, 0.0d, 1.0d);
    }

    private static int[] orderVerticalOffsets(int ownerY, int mobY) {
        int delta = MathHelper.clamp(mobY - ownerY, -1, 1);
        if (delta == 0) {
            return TELEPORT_VERTICAL_OFFSETS;
        }
        int[] ordered = new int[TELEPORT_VERTICAL_OFFSETS.length];
        ordered[0] = delta;
        int index = 1;
        for (int offset : TELEPORT_VERTICAL_OFFSETS) {
            if (offset != delta) {
                ordered[index++] = offset;
            }
        }
        return ordered;
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

    private record CourtesyProfile(boolean courtesyActive, double distanceBonus, OwnerFocusSnapshot focusSnapshot) {
        private static final CourtesyProfile INACTIVE = new CourtesyProfile(false, 0.0d, OwnerFocusSnapshot.idle());

        static CourtesyProfile inactive() {
            return INACTIVE;
        }
    }

    private static boolean isDebugLoggingEnabled() {
        return DebugSettings.isDebugEnabled();
    }
}
