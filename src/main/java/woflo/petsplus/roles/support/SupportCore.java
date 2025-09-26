package woflo.petsplus.roles.support;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

import java.util.List;

/**
 * Implements Support role mechanics: potion aura system and life support abilities.
 * 
 * Core Features:
 * - Baseline: Potion Carrier - load potions, emit aura pulses at reduced potency
 * - L5 Perch Potion Efficiency: Reduced consumption while perched
 * - L15 Mounted Cone Aura: Projects cone aura from mount
 * 
 * Design Philosophy:
 * - Support archetype focused on sustaining owner and allies
 * - Hands-free, persistent buffs with minimal micromanagement
 * - Sacrifices individual potion strength for longer, shared coverage
 */
public class SupportCore {
    
    public static void initialize() {
        // Support mechanics are driven by event hooks and per-pet upkeep.
    }
    
    /**
     * Check if a player has active Support aura coverage.
     */
    public static boolean hasActiveSupportAura(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        
        double auraRadius = 16.0; // Support aura range
        List<MobEntity> supportPets = world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(auraRadius),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null &&
                       component.hasRole(PetRoleType.SUPPORT) &&
                       entity.isAlive() &&
                       component.isOwnedBy(player) &&
                       entity.squaredDistanceTo(player) <= auraRadius * auraRadius;
            }
        );
        
        return !supportPets.isEmpty();
    }
    
    /**
     * Get the potion sip discount from nearby Support pets.
     */
    public static double getSupportPotionDiscount(ServerPlayerEntity player) {
        return SupportBehaviors.getPotionSipDiscount(player);
    }
    
    /**
     * Get the mounted aura radius bonus from Support pets.
     */
    public static double getSupportMountedAuraBonus(ServerPlayerEntity player) {
        return SupportBehaviors.getMountedAuraExtraRadius(player);
    }
}