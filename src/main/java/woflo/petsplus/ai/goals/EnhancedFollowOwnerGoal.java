package woflo.petsplus.ai.goals;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import java.util.EnumSet;

/**
 * Enhanced follow owner goal with better pathfinding and role-specific behavior.
 */
public class EnhancedFollowOwnerGoal extends Goal {
    private final TameableEntity tameable;
    private final double speed;
    private final float followDistance;
    private final float teleportDistance;
    private boolean scoutMode = false;
    private int stuckCounter = 0;
    private BlockPos lastOwnerPos = BlockPos.ORIGIN;
    
    public EnhancedFollowOwnerGoal(TameableEntity tameable, double speed, float followDistance, float teleportDistance, boolean leavesAllowed) {
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
        if (!this.tameable.isTamed()) return false;
        
        LivingEntity livingOwner = this.tameable.getOwner();
        if (!(livingOwner instanceof PlayerEntity owner)) return false;
        
        // Scout mode: only follow if owner is moving or far away
        if (scoutMode) {
            BlockPos currentOwnerPos = owner.getBlockPos();
            boolean ownerMoved = !currentOwnerPos.equals(lastOwnerPos);
            lastOwnerPos = currentOwnerPos;
            
            double distance = this.tameable.squaredDistanceTo(owner);
            return ownerMoved || distance > (followDistance * followDistance);
        }
        
        double distance = this.tameable.squaredDistanceTo(owner);
        return distance > (followDistance * followDistance);
    }
    
    @Override
    public void tick() {
        LivingEntity livingOwner = this.tameable.getOwner();
        if (!(livingOwner instanceof PlayerEntity owner)) return;
        
        this.tameable.getLookControl().lookAt(owner, 10.0f, this.tameable.getMaxLookPitchChange());
        
        if (this.tameable.squaredDistanceTo(owner) > (teleportDistance * teleportDistance)) {
            // Try to teleport if too far
            this.tameable.teleport(owner.getX(), owner.getY(), owner.getZ(), false);
            return;
        }
        
        if (this.tameable.getNavigation().isIdle()) {
            this.tameable.getNavigation().startMovingTo(owner, this.speed);
        }
        
        // Check if pet is stuck
        if (!this.tameable.getNavigation().isFollowingPath()) {
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
        float oldWaterPenalty = this.tameable.getPathfindingPenalty(PathNodeType.WATER);
        float oldFencePenalty = this.tameable.getPathfindingPenalty(PathNodeType.FENCE);
        
        this.tameable.setPathfindingPenalty(PathNodeType.WATER, -1.0f);
        this.tameable.setPathfindingPenalty(PathNodeType.FENCE, -2.0f);
        this.tameable.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, -1.0f);
        
        // Try pathfinding with reduced penalties
        boolean success = this.tameable.getNavigation().startMovingTo(owner, this.speed);
        
        // Restore original penalties after a short delay
        this.tameable.getWorld().getServer().execute(() -> {
            this.tameable.setPathfindingPenalty(PathNodeType.WATER, oldWaterPenalty);
            this.tameable.setPathfindingPenalty(PathNodeType.FENCE, oldFencePenalty);
            this.tameable.setPathfindingPenalty(PathNodeType.DOOR_WOOD_CLOSED, 0.0f);
        });
        
        // If still can't path, consider teleporting if distance is reasonable
        if (!success) {
            double distance = this.tameable.squaredDistanceTo(owner);
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
        WorldView world = this.tameable.getWorld();
        BlockPos ownerPos = owner.getBlockPos();
        
        // Try positions around the owner
        for (int i = 0; i < 10; i++) {
            int x = ownerPos.getX() + this.tameable.getRandom().nextInt(7) - 3;
            int z = ownerPos.getZ() + this.tameable.getRandom().nextInt(7) - 3;
            int y = ownerPos.getY();
            
            BlockPos teleportPos = new BlockPos(x, y, z);
            
            // Check if the position is safe for teleportation
            if (isSafeTeleportLocation(world, teleportPos)) {
                this.tameable.teleport(x + 0.5, y, z + 0.5, false);
                this.tameable.getNavigation().stop();
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
               !world.getFluidState(pos).isEmpty();
    }
}