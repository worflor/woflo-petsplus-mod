package woflo.petsplus.roles.cursedone;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.state.PetComponent;

/**
 * Cursed One mount-specific behaviors.
 */
public class CursedOneMountBehaviors {
    
    /**
     * Apply Resistance buff to mount when owner auto-resurrects.
     */
    public static void applyMountResistanceOnResurrect(PlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        
        // Check if owner is mounted
        if (!(owner.getVehicle() instanceof net.minecraft.entity.LivingEntity mount)) {
            return;
        }
        
        // Check if we have Cursed One pets
        boolean hasCursedOnePet = !serverWorld.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(16),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.getRole().equals(PetRole.CURSED_ONE) &&
                       component.isOwnedBy(owner) &&
                       entity.isAlive();
            }
        ).isEmpty();
        
        if (hasCursedOnePet) {
            // Apply Resistance I for 60 ticks (3 seconds)
            mount.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0));
        }
    }
    
    /**
     * Check if auto-resurrect should apply mount resistance.
     */
    public static boolean shouldApplyMountResistance(PlayerEntity owner) {
        return owner.getVehicle() instanceof net.minecraft.entity.LivingEntity;
    }
}