package woflo.petsplus.ai.behavior.variants;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;

/**
 * Excited circling behavior - runs in circles around owner with high energy.
 */
public class ExcitedCircleVariant implements BehaviorVariant {
    
    private PlayerEntity targetOwner;
    private float circleAngle = 0.0f;
    private static final double CIRCLE_RADIUS = 2.5;
    private static final float ANGLE_INCREMENT = 12.0f; // degrees per tick
    
    @Override
    public void initialize(MobEntity mob, PetContext context) {
        this.targetOwner = context.owner();
        
        // Start at current angle relative to owner
        if (targetOwner != null) {
            Vec3d toMob = mob.getPos().subtract(targetOwner.getPos());
            this.circleAngle = (float) Math.toDegrees(Math.atan2(toMob.z, toMob.x));
        }
    }
    
    @Override
    public void tick(MobEntity mob, int ticks) {
        if (targetOwner == null) {
            return;
        }
        
        // Look at owner while circling
        mob.getLookControl().lookAt(targetOwner, 30.0f, 30.0f);
        
        // Calculate circle position with slight variation
        circleAngle += ANGLE_INCREMENT;
        if (circleAngle >= 360.0f) {
            circleAngle -= 360.0f;
        }
        
        // Add slight radius variation for natural movement
        double currentRadius = CIRCLE_RADIUS + Math.sin(ticks * 0.2) * 0.3;
        
        double radians = Math.toRadians(circleAngle);
        double targetX = targetOwner.getX() + Math.cos(radians) * currentRadius;
        double targetZ = targetOwner.getZ() + Math.sin(radians) * currentRadius;
        
        // Navigate to circle position with high energy
        mob.getNavigation().startMovingTo(targetX, targetOwner.getY(), targetZ, 1.5);
        
        // Enthusiastic tail wag (fast)
        if (ticks % 3 == 0) {
            float wagAmount = 4.0f;
            mob.bodyYaw += (ticks % 6 == 0) ? wagAmount : -wagAmount;
        }
        
        // Occasional excited jump
        if (ticks % 25 == 0 && mob.isOnGround() && mob.getRandom().nextFloat() < 0.5f) {
            mob.setVelocity(mob.getVelocity().x, 0.3, mob.getVelocity().z);
            mob.velocityModified = true;
        }
    }
    
    @Override
    public boolean shouldContinue(MobEntity mob, int ticks) {
        if (targetOwner == null || !targetOwner.isAlive()) {
            return false;
        }
        
        double distance = mob.distanceTo(targetOwner);
        
        // Stop if owner moves too far away
        if (distance > 12.0) {
            return false;
        }
        
        // Stop if owner stands up (excitement fades)
        if (!targetOwner.isSneaking() && ticks > 30) {
            return false;
        }
        
        return ticks < getDefaultDuration();
    }
    
    @Override
    public void stop(MobEntity mob) {
        mob.getNavigation().stop();
    }
    
    @Override
    public int getDefaultDuration() {
        return 100; // 5 seconds
    }
}
