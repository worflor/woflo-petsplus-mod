package woflo.petsplus.ai.goals;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import java.util.EnumSet;
import woflo.petsplus.api.entity.PetsplusTameable;

/**
 * Enhanced follow owner goal with better pathfinding and role-specific behavior.
 */
public class EnhancedFollowOwnerGoal extends Goal {
    private final MobEntity mob;
    private final PetsplusTameable tameable;
    private final double speed;
    private final float followDistance;
    private final float teleportDistance;
    private boolean scoutMode = false;
    private int stuckCounter = 0;
    private BlockPos lastOwnerPos = BlockPos.ORIGIN;
    
    public EnhancedFollowOwnerGoal(MobEntity mob, PetsplusTameable tameable, double speed, float followDistance, float teleportDistance, boolean leavesAllowed) {
        this.mob = mob;
        this.tameable = tameable;
        this.speed = speed;
        this.followDistance = followDistance;
        this.teleportDistance = teleportDistance;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    public void setScoutMode(boolean scoutMode) {
        this.scoutMode = scoutMode;
    }
    
    @Override
    public boolean canStart() {
        if (!this.tameable.petsplus$isTamed()) return false;

        LivingEntity livingOwner = this.tameable.petsplus$getOwner();
        if (!(livingOwner instanceof PlayerEntity owner)) return false;

        // Scout mode: only follow if owner is moving or far away
        if (scoutMode) {
            BlockPos currentOwnerPos = owner.getBlockPos();
            boolean ownerMoved = !currentOwnerPos.equals(lastOwnerPos);
            lastOwnerPos = currentOwnerPos;

            double distance = this.mob.squaredDistanceTo(owner);
            return ownerMoved || distance > (followDistance * followDistance);
        }

        double distance = this.mob.squaredDistanceTo(owner);
        return distance > (followDistance * followDistance);
    }

    @Override
    public void tick() {
        LivingEntity livingOwner = this.tameable.petsplus$getOwner();
        if (!(livingOwner instanceof PlayerEntity owner)) return;

        this.mob.getLookControl().lookAt(owner, 10.0f, this.mob.getMaxLookPitchChange());

        if (this.mob.squaredDistanceTo(owner) > (teleportDistance * teleportDistance)) {
            // Try to teleport if too far
            this.mob.teleport(owner.getX(), owner.getY(), owner.getZ(), false);
            return;
        }

        if (this.mob.getNavigation().isIdle()) {
            this.mob.getNavigation().startMovingTo(owner, this.speed);
        }

        // Check if pet is stuck
        if (!this.mob.getNavigation().isFollowingPath()) {
            stuckCounter++;

            // If stuck for too long, try alternative pathfinding
            if (stuckCounter > 60) {  // 3 seconds
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
        // Temporarily reduce pathfinding penalties for emergency navigation
        float oldWaterPenalty = this.mob.getPathfindingPenalty(PathNodeType.WATER);
        float oldFencePenalty = this.mob.getPathfindingPenalty(PathNodeType.FENCE);

        this.mob.setPathfindingPenalty(PathNodeType.WATER, -1.0f);
        this.mob.setPathfindingPenalty(PathNodeType.FENCE, -2.0f);
        this.mob.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, -1.0f);

        // Try pathfinding with reduced penalties
        boolean success = this.mob.getNavigation().startMovingTo(owner, this.speed);

        // Restore original penalties after a short delay
        this.mob.getWorld().getServer().execute(() -> {
            this.mob.setPathfindingPenalty(PathNodeType.WATER, oldWaterPenalty);
            this.mob.setPathfindingPenalty(PathNodeType.FENCE, oldFencePenalty);
            this.mob.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, 0.0f);
        });

        // If still can't path, consider teleporting if distance is reasonable
        if (!success) {
            double distance = this.mob.squaredDistanceTo(owner);
            if (distance > (followDistance * followDistance) && distance < (teleportDistance * teleportDistance)) {
                // Try to find a safe teleport location
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

        // Try positions around the owner
        for (int i = 0; i < 10; i++) {
            int x = ownerPos.getX() + this.mob.getRandom().nextInt(7) - 3;
            int z = ownerPos.getZ() + this.mob.getRandom().nextInt(7) - 3;
            int y = ownerPos.getY();

            BlockPos teleportPos = new BlockPos(x, y, z);

            // Check if the position is safe for teleportation
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
        // Check if there's solid ground and space for the pet
        return world.getBlockState(pos.down()).isSolidBlock(world, pos.down()) &&
               world.getBlockState(pos).isAir() &&
               world.getBlockState(pos.up()).isAir() &&
               world.getFluidState(pos).isEmpty();
    }
}
