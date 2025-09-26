package woflo.petsplus.ai.goals;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

import java.util.EnumSet;

import woflo.petsplus.ai.goals.OwnerAssistAttackGoal;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.state.PetComponent;

/**
 * Enhanced follow owner goal with better pathfinding and role-specific behavior.
 */
public class EnhancedFollowOwnerGoal extends Goal {
    private static final float HESITATION_LOOK_YAW = 20.0f;
    private static final double HESITATION_SPEED_BOOST = 0.2d;
    private static final float HESITATION_DISTANCE_FACTOR = 0.6f;
    private static final float HESITATION_CLEAR_DISTANCE = 2.5f;

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

    public EnhancedFollowOwnerGoal(MobEntity mob, PetsplusTameable tameable, PetComponent petComponent, double speed, float followDistance, float teleportDistance) {
        this.mob = mob;
        this.tameable = tameable;
        this.petComponent = petComponent;
        this.speed = speed;
        this.baseFollowDistance = followDistance;
        this.teleportDistance = teleportDistance;
        this.activeFollowDistance = followDistance;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    public void setScoutMode(boolean scoutMode) {
        this.scoutMode = scoutMode;
    }

    @Override
    public boolean canStart() {
        if (!this.tameable.petsplus$isTamed()) {
            return false;
        }

        LivingEntity livingOwner = this.tameable.petsplus$getOwner();
        if (!(livingOwner instanceof PlayerEntity owner)) {
            return false;
        }

        long now = owner.getWorld().getTime();
        boolean hesitating = OwnerAssistAttackGoal.isPetHesitating(petComponent, now);

        if (scoutMode) {
            BlockPos currentOwnerPos = owner.getBlockPos();
            boolean ownerMoved = !currentOwnerPos.equals(lastOwnerPos);
            lastOwnerPos = currentOwnerPos;

            double distance = this.mob.squaredDistanceTo(owner);
            if (hesitating) {
                return true;
            }
            return ownerMoved || distance > (baseFollowDistance * baseFollowDistance);
        }

        if (hesitating) {
            return true;
        }

        double distance = this.mob.squaredDistanceTo(owner);
        return distance > (baseFollowDistance * baseFollowDistance);
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity livingOwner = this.tameable.petsplus$getOwner();
        if (!(livingOwner instanceof PlayerEntity owner)) {
            return false;
        }

        double distance = this.mob.squaredDistanceTo(owner);
        if (distance > (this.teleportDistance * this.teleportDistance)) {
            return true;
        }

        long now = owner.getWorld().getTime();
        if (OwnerAssistAttackGoal.isPetHesitating(petComponent, now)) {
            return true;
        }

        return distance > (this.activeFollowDistance * this.activeFollowDistance);
    }

    @Override
    public void start() {
        this.stuckCounter = 0;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity livingOwner = this.tameable.petsplus$getOwner();
        if (!(livingOwner instanceof PlayerEntity owner)) {
            return;
        }

        long now = owner.getWorld().getTime();
        boolean hesitating = OwnerAssistAttackGoal.isPetHesitating(petComponent, now);
        this.activeFollowDistance = hesitating
            ? Math.max(HESITATION_CLEAR_DISTANCE, baseFollowDistance * HESITATION_DISTANCE_FACTOR)
            : baseFollowDistance;

        float lookYaw = hesitating ? HESITATION_LOOK_YAW : 10.0f;
        this.mob.getLookControl().lookAt(owner, lookYaw, this.mob.getMaxLookPitchChange());

        if (this.mob.squaredDistanceTo(owner) > (teleportDistance * teleportDistance)) {
            this.mob.teleport(owner.getX(), owner.getY(), owner.getZ(), false);
            return;
        }

        if (hesitating && this.mob.squaredDistanceTo(owner) <= (HESITATION_CLEAR_DISTANCE * HESITATION_CLEAR_DISTANCE)) {
            OwnerAssistAttackGoal.clearAssistHesitation(petComponent);
        }

        if (this.mob.getNavigation().isIdle()) {
            double adjustedSpeed = hesitating ? this.speed + HESITATION_SPEED_BOOST : this.speed;
            this.mob.getNavigation().startMovingTo(owner, adjustedSpeed);
        }

        if (!this.mob.getNavigation().isFollowingPath()) {
            stuckCounter++;
            if (stuckCounter > 60) {
                tryAlternativePathfinding(owner);
                stuckCounter = 0;
            }
        } else {
            stuckCounter = 0;
        }
    }

    /**
     * Try alternative pathfinding when pet gets stuck.
     */
    private void tryAlternativePathfinding(PlayerEntity owner) {
        float oldWaterPenalty = this.mob.getPathfindingPenalty(PathNodeType.WATER);
        float oldFencePenalty = this.mob.getPathfindingPenalty(PathNodeType.FENCE);

        this.mob.setPathfindingPenalty(PathNodeType.WATER, -1.0f);
        this.mob.setPathfindingPenalty(PathNodeType.FENCE, -2.0f);
        this.mob.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, -1.0f);

        boolean success = this.mob.getNavigation().startMovingTo(owner, this.speed);

        if (this.mob.getWorld().getServer() != null) {
            this.mob.getWorld().getServer().execute(() -> {
                this.mob.setPathfindingPenalty(PathNodeType.WATER, oldWaterPenalty);
                this.mob.setPathfindingPenalty(PathNodeType.FENCE, oldFencePenalty);
                this.mob.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, 0.0f);
            });
        }

        if (!success) {
            double distance = this.mob.squaredDistanceTo(owner);
            if (distance > (activeFollowDistance * activeFollowDistance) && distance < (teleportDistance * teleportDistance)) {
                tryEmergencyTeleport(owner);
            }
        }
    }

    /**
     * Emergency teleport when normal pathfinding fails.
     */
    private void tryEmergencyTeleport(PlayerEntity owner) {
        WorldView world = this.mob.getWorld();
        BlockPos ownerPos = owner.getBlockPos();

        for (int i = 0; i < 10; i++) {
            int x = ownerPos.getX() + this.mob.getRandom().nextInt(7) - 3;
            int z = ownerPos.getZ() + this.mob.getRandom().nextInt(7) - 3;
            int y = ownerPos.getY();

            BlockPos teleportPos = new BlockPos(x, y, z);
            if (isSafeTeleportLocation(world, teleportPos)) {
                this.mob.teleport(x + 0.5, y, z + 0.5, false);
                this.mob.getNavigation().stop();
                break;
            }
        }
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
}
