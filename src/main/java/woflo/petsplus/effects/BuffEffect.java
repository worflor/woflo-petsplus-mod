package woflo.petsplus.effects;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.util.PetPerchUtil;

/**
 * Effect that applies a status effect buff to a target entity.
 */
public class BuffEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "buff");
    
    public enum Target {
        OWNER, PET, VICTIM, MOUNT
    }
    
    private final Target target;
    private final StatusEffectInstance statusEffect;
    private final boolean onlyIfMounted;
    private final boolean onlyIfPerched;
    
    public BuffEffect(Target target, StatusEffectInstance statusEffect, boolean onlyIfMounted, boolean onlyIfPerched) {
        this.target = target;
        this.statusEffect = statusEffect;
        this.onlyIfMounted = onlyIfMounted;
        this.onlyIfPerched = onlyIfPerched;
    }
    
    public BuffEffect(Target target, StatusEffectInstance statusEffect) {
        this(target, statusEffect, false, false);
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        // Check guards
        if (onlyIfMounted) {
            LivingEntity owner = context.getOwner();
            if (owner == null || owner.getVehicle() == null) {
                return false;
            }
        }
        
        if (onlyIfPerched) {
            PetComponent petComponent = PetComponent.get(context.getPet());
            if (!PetPerchUtil.isPetPerched(petComponent)) {
                return false;
            }
        }
        
        LivingEntity targetEntity = getTargetEntity(context);
        if (targetEntity == null) return false;
        
        targetEntity.addStatusEffect(new StatusEffectInstance(statusEffect));
        return true;
    }
    
    @Override
    public int getDurationTicks() {
        return statusEffect.getDuration();
    }
    
    private LivingEntity getTargetEntity(EffectContext context) {
        switch (target) {
            case OWNER:
                return context.getOwner();
            case PET:
                return context.getPet();
            case VICTIM:
                Entity victim = context.getTriggerContext().getVictim();
                return victim instanceof LivingEntity ? (LivingEntity) victim : null;
            case MOUNT:
                Entity mount = context.getMount();
                return mount instanceof LivingEntity ? (LivingEntity) mount : null;
            default:
                return null;
        }
    }
}