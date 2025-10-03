package woflo.petsplus.effects;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.TriggerContext;
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
    @Nullable
    private final String requiredDataFlag;
    private final boolean requiredDataValue;
    private final boolean requireFlag;

    public BuffEffect(Target target, StatusEffectInstance statusEffect, boolean onlyIfMounted, boolean onlyIfPerched) {
        this(target, statusEffect, onlyIfMounted, onlyIfPerched, null, true);
    }

    public BuffEffect(Target target,
                      StatusEffectInstance statusEffect,
                      boolean onlyIfMounted,
                      boolean onlyIfPerched,
                      @Nullable String requiredDataFlag,
                      boolean requiredDataValue) {
        this.target = target;
        this.statusEffect = statusEffect;
        this.onlyIfMounted = onlyIfMounted;
        this.onlyIfPerched = onlyIfPerched;
        this.requiredDataFlag = requiredDataFlag == null || requiredDataFlag.isEmpty() ? null : requiredDataFlag;
        this.requiredDataValue = requiredDataValue;
        this.requireFlag = this.requiredDataFlag != null;
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
        if (requireFlag) {
            Boolean flagValue = context.getData(requiredDataFlag, Boolean.class);
            boolean matches = flagValue != null && flagValue;
            if (matches != requiredDataValue) {
                return false;
            }
        }

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
                LivingEntity storedTarget = getStoredLivingTarget(context);
                if (storedTarget != null) {
                    return storedTarget;
                }

                TriggerContext triggerContext = context.getTriggerContext();
                if (triggerContext == null) {
                    return null;
                }

                Entity victim = triggerContext.getVictim();
                return victim instanceof LivingEntity ? (LivingEntity) victim : null;
            case MOUNT:
                Entity mount = context.getMount();
                return mount instanceof LivingEntity ? (LivingEntity) mount : null;
            default:
                return null;
        }
    }

    private LivingEntity getStoredLivingTarget(EffectContext context) {
        LivingEntity fromTarget = asLivingEntity(context.getTarget());
        if (fromTarget != null) {
            return fromTarget;
        }

        LivingEntity fromTargetData = asLivingEntity(context.getData("target", Entity.class));
        if (fromTargetData != null) {
            return fromTargetData;
        }

        return asLivingEntity(context.getData("victim", Entity.class));
    }

    private LivingEntity asLivingEntity(Entity entity) {
        return entity instanceof LivingEntity living ? living : null;
    }
}
