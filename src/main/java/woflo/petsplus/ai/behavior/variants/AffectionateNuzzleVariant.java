package woflo.petsplus.ai.behavior.variants;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import woflo.petsplus.ai.context.PetContext;

/**
 * Affectionate nuzzling behavior - slow approach with gentle interaction.
 */
public class AffectionateNuzzleVariant implements BehaviorVariant {
    
    private PlayerEntity targetOwner;
    private boolean hasReachedOwner = false;
    
    @Override
    public void initialize(MobEntity mob, PetContext context) {
        this.targetOwner = context.owner();
        this.hasReachedOwner = false;
    }
    
    @Override
    public void tick(MobEntity mob, int ticks) {
        if (targetOwner == null) {
            return;
        }
        
        // Always look at owner's eyes
        mob.getLookControl().lookAt(
            targetOwner.getX(),
            targetOwner.getEyeY(),
            targetOwner.getZ(),
            30.0f,
            30.0f
        );
        
        double distance = mob.distanceTo(targetOwner);
        
        if (!hasReachedOwner && distance > 1.5) {
            // Slow, deliberate approach
            mob.getNavigation().startMovingTo(targetOwner, 0.8);
        } else {
            hasReachedOwner = true;
            mob.getNavigation().stop();
            
            // Stay close, subtle movements
            if (distance > 2.0) {
                hasReachedOwner = false;
            }
        }
        
        // Subtle head tilt for affection
        if (hasReachedOwner && ticks % 30 == 0) {
            float tiltAmount = 5.0f + mob.getRandom().nextFloat() * 5.0f;
            mob.setPitch(mob.getPitch() + (mob.getRandom().nextBoolean() ? tiltAmount : -tiltAmount));
        }
        
        // Gentle tail wag (slower, more controlled than playful)
        if (hasReachedOwner && ticks % 6 == 0) {
            float wagAmount = 1.5f;
            mob.bodyYaw += (ticks % 12 == 0) ? wagAmount : -wagAmount;
        }
        
        // Soft nuzzle movement when very close
        if (hasReachedOwner && distance < 1.2 && ticks % 40 == 0) {
            // Slight push forward as if nuzzling
            double dx = targetOwner.getX() - mob.getX();
            double dz = targetOwner.getZ() - mob.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.01) {
                mob.setVelocity(dx / len * 0.1, 0, dz / len * 0.1);
                mob.velocityModified = true;
            }
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
        
        // Can extend duration if still close and owner still crouching
        if (hasReachedOwner && distance < 2.0 && targetOwner.isSneaking()) {
            // Allow lingering close to owner
            return ticks < getDefaultDuration() + 40;
        }
        
        return ticks < getDefaultDuration();
    }
    
    @Override
    public void stop(MobEntity mob) {
        mob.getNavigation().stop();
        mob.setPitch(0);
    }
    
    @Override
    public int getDefaultDuration() {
        return 80; // 4 seconds
    }
}
