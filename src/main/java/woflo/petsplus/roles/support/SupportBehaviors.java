package woflo.petsplus.roles.support;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;

/**
 * Support role behaviors for pet-agnostic utility enhancement.
 */
public class SupportBehaviors {
    
    /**
     * Check if the owner should get perch sip discount.
     * Called during potion consumption to reduce sip cost.
     */
    public static double getPotionSipDiscount(PlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return 0.0;
        }
        
        // Find nearby Support pets that are perched
        return serverWorld.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(16),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.getRole().equals(PetRole.SUPPORT) &&
                       component.isOwnedBy(owner) &&
                       component.isPerched() &&
                       entity.isAlive();
            }
        ).stream()
        .findFirst()
        .map(pet -> PetsPlusConfig.getInstance().getDouble("support", "perchSipDiscount", 0.20))
        .orElse(0.0);
    }
    
    /**
     * Get extra radius for aura pulses when owner is mounted.
     * Called by aura effects to determine radius bonus.
     */
    public static double getMountedAuraExtraRadius(PlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return 0.0;
        }
        
        // Check if owner is mounted
        if (owner.getVehicle() == null) {
            return 0.0;
        }
        
        // Find nearby Support pets
        boolean hasNearbySupport = !serverWorld.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(16),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.getRole().equals(PetRole.SUPPORT) &&
                       component.isOwnedBy(owner) &&
                       entity.isAlive();
            }
        ).isEmpty();
        
        return hasNearbySupport ? 
            PetsPlusConfig.getInstance().getDouble("support", "mountedConeExtraRadius", 2.0) : 0.0;
    }
    
    /**
     * Check if aura should use forward cone bias when owner is mounted.
     */
    public static boolean shouldUseForwardConeBias(PlayerEntity owner) {
        return owner.getVehicle() != null && getMountedAuraExtraRadius(owner) > 0;
    }
}