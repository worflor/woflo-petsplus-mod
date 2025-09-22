package woflo.petsplus.roles.eepyeeper;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;

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
                    !component.getRole().equals(PetRole.EEPY_EEPER) ||
                    !component.isOwnedBy(owner) ||
                    !entity.isAlive()) {
                    return false;
                }
                
                // Check if pet is sitting or perched
                boolean isSitting = false;
                if (entity instanceof net.minecraft.entity.passive.TameableEntity tameable) {
                    isSitting = tameable.isSitting();
                }
                
                return isSitting || component.isPerched();
            }
        ).isEmpty();
        
        return hasNappingEepyEeper ? 
            PetsPlusConfig.getInstance().getDouble("eepy_eeper", "perchNapExtraRadius", 1.0) : 0.0;
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