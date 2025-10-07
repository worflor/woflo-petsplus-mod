package woflo.petsplus.ai.behavior.variants;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.state.PetComponent;

/**
 * Playful bouncing behavior - jumps excitedly toward owner.
 */
public class PlayfulBounceVariant implements BehaviorVariant {
    
    private PlayerEntity targetOwner;
    private int jumpCooldown = 0;
    
    @Override
    public void initialize(MobEntity mob, PetContext context) {
        this.targetOwner = context.owner();
    }
    
    @Override
    public void tick(MobEntity mob, int ticks) {
        if (targetOwner == null) {
            return;
        }
        
        // Look at owner with excitement
        mob.getLookControl().lookAt(targetOwner, 30.0f, 30.0f);
        
        // Navigate toward owner with playful energy
        double distance = mob.distanceTo(targetOwner);
        if (distance > 2.0) {
            // Variable speed - faster when further away
            double speed = distance > 5.0 ? 1.4 : 1.2;
            mob.getNavigation().startMovingTo(targetOwner, speed);
        } else {
            mob.getNavigation().stop();
            // Bounce in place when close
            if (ticks % 15 == 0 && mob.isOnGround()) {
                mob.setVelocity(mob.getVelocity().x * 0.5, 0.25, mob.getVelocity().z * 0.5);
                mob.velocityModified = true;
            }
        }
        
        // Playful bounces while approaching
        jumpCooldown--;
        if (jumpCooldown <= 0 && mob.isOnGround() && distance > 1.5 && distance < 8.0) {
            // Vary jump height based on excitement
            float jumpHeight = 0.3f + mob.getRandom().nextFloat() * 0.15f;
            mob.setVelocity(mob.getVelocity().x, jumpHeight, mob.getVelocity().z);
            mob.velocityModified = true;
            jumpCooldown = 12 + mob.getRandom().nextInt(15);
        }
        
        // Enthusiastic tail wag (fast and energetic)
        if (ticks % 4 == 0) {
            float wagAmount = 3.0f + mob.getRandom().nextFloat() * 2.0f;
            mob.bodyYaw += (ticks % 8 == 0) ? wagAmount : -wagAmount;
        }
        
        // Occasional happy wiggle (full body)
        if (ticks % 20 == 0 && distance < 3.0) {
            mob.bodyYaw += mob.getRandom().nextFloat() * 20.0f - 10.0f;
        }
    }
    
    @Override
    public boolean shouldContinue(MobEntity mob, int ticks) {
        if (targetOwner == null || !targetOwner.isAlive()) {
            return false;
        }
        
        double distance = mob.distanceTo(targetOwner);
        
        // Stop if owner moves too far away
        if (distance > 16.0) {
            return false;
        }
        
        // Stop if owner stands up (no longer crouching)
        if (!targetOwner.isSneaking() && ticks > 20) {
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
        return 60; // 3 seconds
    }
}
