package woflo.petsplus.roles.eepyeeper;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.util.PetPerchUtil;

/**
 * Eepy Eeper cozy behaviors.
 */
public class EepyEeperNapBehaviors {
    
    /**
     * Get extra radius for regen when pet is sitting or perched.
     */
    public static double getNapExtraRadius(PlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return 0.0;
        }
        
        // Find sitting or perched Eepy Eeper pets
        boolean hasNappingEepyEeper = !serverWorld.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(16),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                if (component == null ||
                    !component.hasRole(PetRoleType.EEPY_EEPER) ||
                    !component.isOwnedBy(owner) ||
                    !entity.isAlive()) {
                    return false;
                }
                
                // Check if pet is sitting or perched
                boolean isSitting = false;
                if (entity instanceof PetsplusTameable tameable) {
                    isSitting = tameable.petsplus$isSitting();
                }
                
                return isSitting || PetPerchUtil.isPetPerched(component);
            }
        ).isEmpty();
        
        return hasNappingEepyEeper ?
            PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.EEPY_EEPER.id(), "perchNapExtraRadius", 1.0) : 0.0;
    }
    
    /**
     * Calculate enhanced regen radius for Nap Time ability.
     */
    public static double getEnhancedRegenRadius(PlayerEntity owner, double baseRadius) {
        double extraRadius = getNapExtraRadius(owner);
        return baseRadius + extraRadius;
    }
    
    /**
     * Check if Nap Time regen enhancement should be active.
     */
    public static boolean isNapTimeActive(PlayerEntity owner) {
        return getNapExtraRadius(owner) > 0;
    }
}