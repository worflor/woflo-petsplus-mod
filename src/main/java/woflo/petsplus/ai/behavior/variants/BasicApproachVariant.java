package woflo.petsplus.ai.behavior.variants;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import woflo.petsplus.ai.context.PetContext;

/**
 * Basic approach behavior - simple walk toward owner with tail wag.
 */
public class BasicApproachVariant implements BehaviorVariant {
    
    private PlayerEntity targetOwner;
    
    @Override
    public void initialize(MobEntity mob, PetContext context) {
        this.targetOwner = context.owner();
    }
    
    @Override
    public void tick(MobEntity mob, int ticks) {
        if (targetOwner == null) {
            return;
        }
        
        // Look at owner
        mob.getLookControl().lookAt(targetOwner, 30.0f, 30.0f);
        
        // Walk toward owner, stop at comfortable distance
        double distance = mob.distanceTo(targetOwner);
        if (distance > 2.0) {
            mob.getNavigation().startMovingTo(targetOwner, 1.0);
        } else {
            mob.getNavigation().stop();
        }
        
        // Simple tail wag
        if (ticks % 5 == 0) {
            float wagAmount = 2.0f;
            mob.bodyYaw += (ticks % 10 == 0) ? wagAmount : -wagAmount;
        }
        
        // Slight pause when reaching owner (natural hesitation)
        if (distance <= 2.5 && distance > 2.0 && ticks % 20 == 0) {
            mob.getNavigation().stop();
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
        
        // Natural completion when close
        if (distance <= 2.0 && ticks > 30) {
            return false; // Mission accomplished
        }
        
        return ticks < getDefaultDuration();
    }
    
    @Override
    public void stop(MobEntity mob) {
        mob.getNavigation().stop();
    }
    
    @Override
    public int getDefaultDuration() {
        return 50; // 2.5 seconds
    }
}
