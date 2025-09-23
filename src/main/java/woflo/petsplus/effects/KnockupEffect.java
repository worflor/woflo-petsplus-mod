package woflo.petsplus.effects;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.TriggerContext;

/**
 * Effect that applies a knockup/knockback to a target entity.
 */
public class KnockupEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "knockup");
    
    private final double strength;
    private final String targetType;
    
    public KnockupEffect(double strength, String targetType) {
        this.strength = strength;
        this.targetType = targetType;
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        Entity target = getTargetEntity(context);
        
        if (target instanceof LivingEntity living) {
            // Apply knockup by adding upward velocity
            Vec3d velocity = living.getVelocity();
            living.setVelocity(velocity.x, Math.max(velocity.y, strength), velocity.z);
            living.velocityModified = true;
            
            return true;
        }
        
        return false;
    }
    
    @Override
    public int getDurationTicks() {
        return 0; // Instant effect
    }
    
    private Entity getTargetEntity(EffectContext context) {
        TriggerContext triggerContext = context.getTriggerContext();

        switch (targetType.toLowerCase()) {
            case "victim":
                Entity storedVictim = getStoredTarget(context);
                if (storedVictim != null) {
                    return storedVictim;
                }

                return triggerContext != null ? triggerContext.getVictim() : null;
            case "owner":
                return context.getOwner();
            case "pet":
                return context.getPet();
            default:
                Entity target = getStoredTarget(context);
                if (target != null) {
                    return target;
                }

                if (triggerContext != null) {
                    Entity triggerTarget = triggerContext.getData("target", Entity.class);
                    if (triggerTarget != null) {
                        return triggerTarget;
                    }

                    return triggerContext.getVictim();
                }

                return null;
        }
    }

    private Entity getStoredTarget(EffectContext context) {
        Entity target = context.getTarget();
        if (target != null) {
            return target;
        }

        target = context.getData("target", Entity.class);
        if (target != null) {
            return target;
        }

        return context.getData("victim", Entity.class);
    }
}
