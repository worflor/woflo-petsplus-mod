package woflo.petsplus.ai.goals.follow;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.OwnerAssistAttackGoal;
import woflo.petsplus.ai.movement.MovementCommand;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;
import woflo.petsplus.state.emotions.PetMoodEngine;

import java.util.EnumSet;

/**
 * Drives concrete navigation toward the pet's owner after blending movement modifiers.
 */
public class FollowOwnerAdaptiveGoal extends AdaptiveGoal {
    private static final String FOLLOW_PATH_COOLDOWN_KEY = "follow_path_blocked";
    private static final double MOVE_TARGET_REFRESH_EPSILON = 0.0625 * 0.0625;
    private static final int PATH_FAILURE_TIMEOUT = 80;
    private static final int PATH_BLOCK_COOLDOWN_TICKS = 100;
    private static final double MIN_DYNAMIC_SPEED = 0.55d;
    private static final double MAX_DYNAMIC_SPEED = 1.9d;
    private static final double HESITATION_CLEAR_DISTANCE = 2.5d;

    private final PetsplusTameable tameable;
    private final PetComponent petComponent;
    private final PetComponent.MovementDirector movementDirector;
    private final double baseSpeed;

    private float baseFollowDistance;
    private float teleportDistance;
    private float activeFollowDistance;
    private Vec3d lastMoveTarget;
    private int stuckCounter;
    private int failedPathTicks;
    private long pathBlockedUntilTick = Long.MIN_VALUE;
    private long lastMomentumSampleTick = Long.MIN_VALUE;

    public FollowOwnerAdaptiveGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.FOLLOW_OWNER), EnumSet.of(Control.MOVE, Control.LOOK));
        this.tameable = mob instanceof PetsplusTameable petsplus ? petsplus : null;
        this.petComponent = PetComponent.get(mob);
        this.movementDirector = petComponent != null ? petComponent.getMovementDirector() : null;
        this.baseSpeed = 1.0d;
        if (petComponent != null) {
            this.baseFollowDistance = FollowTuning.resolveFollowDistance(petComponent);
            this.teleportDistance = FollowTuning.resolveTeleportDistance(petComponent);
        } else {
            this.baseFollowDistance = 6.0f;
            this.teleportDistance = 12.0f;
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
        long now = mob.getEntityWorld().getTime();
        if (pathBlockedUntilTick != Long.MIN_VALUE && now < pathBlockedUntilTick) {
            return false;
        }
        if (petComponent.isOnCooldown(FOLLOW_PATH_COOLDOWN_KEY)) {
            return false;
        }
        if (petComponent.getAIState().isPanicking()) {
            return false;
        }
        if (isMajorActivityActive()) {
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
        if (distanceSq > teleportDistance * teleportDistance) {
            return true;
        }

        boolean hesitating = OwnerAssistAttackGoal.isPetHesitating(petComponent, now);
        boolean moodHoldActive = petComponent.isMoodFollowHoldActive(now);
        float moodDistanceBonus = moodHoldActive ? petComponent.getMoodFollowDistanceBonus(now) : 0.0f;
        double baseDistance = baseFollowDistance + moodDistanceBonus;
        MovementCommand command = movementDirector.resolveMovement(owner.getEntityPos(), baseDistance, baseSpeed);
        double thresholdSq = command.desiredDistance() * command.desiredDistance();

        if (hesitating) {
            return true;
        }
        if (moodHoldActive && distanceSq <= thresholdSq) {
            return false;
        }
        return distanceSq > thresholdSq;
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
        if (petComponent.isOnCooldown(FOLLOW_PATH_COOLDOWN_KEY)) {
            return false;
        }
        long now = mob.getEntityWorld().getTime();
        if (pathBlockedUntilTick != Long.MIN_VALUE && now < pathBlockedUntilTick) {
            return false;
        }
        if (petComponent.getAIState().isPanicking()) {
            return false;
        }
        if (isMajorActivityActive()) {
            return false;
        }
        double distanceSq = mob.squaredDistanceTo(owner);
        if (Double.isNaN(distanceSq)) {
            return false;
        }
        if (distanceSq > teleportDistance * teleportDistance) {
            return true;
        }
        boolean hesitating = OwnerAssistAttackGoal.isPetHesitating(petComponent, now);
        if (hesitating) {
            return true;
        }
        boolean moodHoldActive = petComponent.isMoodFollowHoldActive(now);
        float moodDistanceBonus = moodHoldActive ? petComponent.getMoodFollowDistanceBonus(now) : 0.0f;
        double baseDistance = baseFollowDistance + moodDistanceBonus;
        MovementCommand command = movementDirector.resolveMovement(owner.getEntityPos(), baseDistance, baseSpeed);
        double thresholdSq = command.desiredDistance() * command.desiredDistance();
        if (moodHoldActive && distanceSq <= thresholdSq) {
            return false;
        }
        return distanceSq > thresholdSq;
    }

    @Override
    protected void onStartGoal() {
        stuckCounter = 0;
        failedPathTicks = 0;
        pathBlockedUntilTick = Long.MIN_VALUE;
        lastMoveTarget = null;
        long now = mob.getEntityWorld().getTime();
        lastMomentumSampleTick = now;
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
        lastMomentumSampleTick = Long.MIN_VALUE;
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
        if (petComponent.isOnCooldown(FOLLOW_PATH_COOLDOWN_KEY)
            || (pathBlockedUntilTick != Long.MIN_VALUE && now < pathBlockedUntilTick)) {
            mob.getNavigation().stop();
            return;
        }

        this.baseFollowDistance = FollowTuning.resolveFollowDistance(petComponent);
        this.teleportDistance = FollowTuning.resolveTeleportDistance(petComponent);

        boolean hesitating = OwnerAssistAttackGoal.isPetHesitating(petComponent, now);
        boolean moodHoldActive = petComponent.isMoodFollowHoldActive(now);
        float moodDistanceBonus = moodHoldActive ? petComponent.getMoodFollowDistanceBonus(now) : 0.0f;
        double baseDistance = baseFollowDistance + moodDistanceBonus;

        double distanceToOwnerSq = mob.squaredDistanceTo(owner);
        double baseSpeed = resolveDynamicSpeed(distanceToOwnerSq, baseDistance);
        MovementCommand command = movementDirector.resolveMovement(owner.getEntityPos(), baseDistance, baseSpeed);
        this.activeFollowDistance = (float) command.desiredDistance();
        Vec3d moveTarget = command.targetPosition();
        double adjustedSpeed = command.speed();

        mob.getLookControl().lookAt(owner, hesitating ? 20.0f : 10.0f, mob.getMaxLookPitchChange());

        boolean worldsMatch = owner.getEntityWorld().getRegistryKey().equals(mob.getEntityWorld().getRegistryKey());
        if (!worldsMatch || Double.isInfinite(distanceToOwnerSq)) {
            mob.getNavigation().stop();
            lastMoveTarget = null;
            return;
        }

        if (moodHoldActive && !hesitating && distanceToOwnerSq <= (baseDistance * baseDistance)) {
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

        if (navigationIdle || targetChanged) {
            mob.getNavigation().startMovingTo(moveTarget.x, moveTarget.y, moveTarget.z, adjustedSpeed);
        } else {
            mob.getNavigation().setSpeed(adjustedSpeed);
        }
        lastMoveTarget = moveTarget;

        boolean followingPath = mob.getNavigation().isFollowingPath();
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

        double distanceToOwnerSq = mob.squaredDistanceTo(owner);
        if (Double.isInfinite(distanceToOwnerSq)) {
            return false;
        }

        for (int i = 0; i < 10; i++) {
            int x = ownerPos.getX() + mob.getRandom().nextInt(7) - 3;
            int z = ownerPos.getZ() + mob.getRandom().nextInt(7) - 3;
            int y = ownerPos.getY();

            BlockPos teleportPos = new BlockPos(x, y, z);
            if (isSafeTeleportLocation(world, teleportPos)) {
                mob.teleport(x + 0.5, y, z + 0.5, false);
                mob.getNavigation().stop();
                return true;
            }
        }

        return false;
    }

    private boolean isSafeTeleportLocation(WorldView world, BlockPos pos) {
        return world.getBlockState(pos.down()).isSolidBlock(world, pos.down())
            && world.getBlockState(pos).isAir()
            && world.getBlockState(pos.up()).isAir()
            && world.getFluidState(pos).isEmpty();
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
}
