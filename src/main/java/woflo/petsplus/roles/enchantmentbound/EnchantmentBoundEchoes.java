package woflo.petsplus.roles.enchantmentbound;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.util.PetPerchUtil;

/**
 * Enchantment-bound owner-centric echo behaviors.
 */
public class EnchantmentBoundEchoes {
    
    /**
     * Add bonus ticks to Haste pulse duration if pet is perched.
     */
    public static int getPerchedHasteBonusTicks(PlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return 0;
        }
        
        // Find perched Enchantment-bound pets
        boolean hasPerchedEnchantmentBound = !serverWorld.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(16),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null &&
                       component.hasRole(PetRoleType.ENCHANTMENT_BOUND) &&
                       component.isOwnedBy(owner) &&
                       PetPerchUtil.isPetPerched(component) &&
                       entity.isAlive();
            }
        ).isEmpty();

        return hasPerchedEnchantmentBound ?
            PetsPlusConfig.getInstance().getRoleInt(PetRoleType.ENCHANTMENT_BOUND.id(), "perchedHasteBonusTicks", 10) : 0;
    }
    
    /**
     * Apply enhanced Haste effect with perch bonus.
     */
    public static void applyEnhancedHaste(PlayerEntity owner, int baseDuration) {
        int bonusTicks = getPerchedHasteBonusTicks(owner);
        int totalDuration = baseDuration + bonusTicks;
        
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, totalDuration, 0));
    }
    
    /**
     * Check if mounted extra rolls should be enabled.
     */
    public static boolean shouldEnableMountedExtraRolls(PlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        
        if (owner.getVehicle() == null) {
            return false;
        }
        
        // Check config setting
        if (!PetsPlusConfig.getInstance().getRoleBoolean(PetRoleType.ENCHANTMENT_BOUND.id(), "mountedExtraRollsEnabled", true)) {
            return false;
        }
        
        // Check if we have Enchantment-bound pets
        return !serverWorld.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(16),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null &&
                       component.hasRole(PetRoleType.ENCHANTMENT_BOUND) &&
                       component.isOwnedBy(owner) &&
                       entity.isAlive();
            }
        ).isEmpty();
    }
    
    /**
     * Handle extra rolls for lassoed mobs killed by owner.
     */
    public static void handleLassoodMobKill(PlayerEntity owner, net.minecraft.entity.LivingEntity killedMob) {
        if (shouldEnableMountedExtraRolls(owner)) {
            // This would integrate with the loot generation system
            // For now, just mark that extra rolls should be applied
            // Implementation would depend on how loot tables are processed
        }
    }
}