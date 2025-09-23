package woflo.petsplus.roles.skyrider;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

/**
 * Skyrider mount-specific behaviors.
 */
public class SkyriderMountBehaviors {
    
    /**
     * Apply fall reduction to mount when owner is mounted and has Skybond aura.
     */
    public static float applyMountFallReduction(PlayerEntity owner, float fallDamage) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return fallDamage;
        }
        
        // Check if owner is mounted
        Entity mount = owner.getVehicle();
        if (mount == null) {
            return fallDamage;
        }
        
        // Check if we have Skyrider pets providing Skybond aura
        boolean hasSkybondAura = !serverWorld.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(16),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null &&
                       component.hasRole(PetRoleType.SKYRIDER) &&
                       component.isOwnedBy(owner) &&
                       entity.isAlive();
            }
        ).isEmpty();
        
        if (hasSkybondAura) {
            // Apply fall reduction (this would need to be integrated with fall damage calculation)
            return fallDamage * 0.75f; // 25% reduction as example
        }
        
        return fallDamage;
    }
    
    /**
     * Check if mount should receive fall damage reduction from Skybond aura.
     */
    public static boolean shouldApplyMountFallReduction(PlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        
        return !serverWorld.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(16),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null &&
                       component.hasRole(PetRoleType.SKYRIDER) &&
                       component.isOwnedBy(owner) &&
                       entity.isAlive();
            }
        ).isEmpty();
    }
}