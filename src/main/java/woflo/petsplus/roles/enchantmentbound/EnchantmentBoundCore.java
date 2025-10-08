package woflo.petsplus.roles.enchantmentbound;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

import java.util.List;
import java.util.Objects;

/**
 * Implements Enchantment-Bound role mechanics: magical enhancement and enchantment synergy.
 * 
 * Core Features:
 * - Baseline: Enchantment resonance, magical damage bonus
 * - L7 Mystic Bond: Enhanced enchantment effects, spell echo
 * - Enchantment amplification and magical enhancement
 * 
 * Design Philosophy:
 * - Magical synergy archetype
 * - Enhances enchanted items and magical effects
 * - Provides unique magical bonuses and interactions
 */
public final class EnchantmentBoundCore {

    private static final double NEARBY_RADIUS = 16.0;

    public static void initialize() {
        EnchantmentBoundAbilityHooks.initialize();
    }
    
    /**
     * Check if player has a nearby Enchantment-Bound pet.
     */
    private static boolean hasNearbyEnchantmentBound(ServerPlayerEntity player) {
        return !getNearbyEnchantmentBoundPets(player, NEARBY_RADIUS).isEmpty();
    }

    private static List<MobEntity> getNearbyEnchantmentBoundPets(ServerPlayerEntity player, double radius) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            return java.util.Collections.emptyList();
        }

        return world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(radius),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null &&
                       component.hasRole(PetRoleType.ENCHANTMENT_BOUND) &&
                       entity.isAlive() &&
                       component.isOwnedBy(player) &&
                       entity.squaredDistanceTo(player) <= radius * radius;
            }
        );
    }
    
    /**
     * Get enchantment damage bonus for nearby Enchantment-Bound pets.
     */
    public static float getEnchantmentDamageBonus(ServerPlayerEntity player) {
        if (!hasNearbyEnchantmentBound(player)) {
            return 0.0f;
        }
        
        if (!(player.getEntityWorld() instanceof ServerWorld)) {
            return 0.0f;
        }

        int maxLevel = getNearbyEnchantmentBoundPets(player, NEARBY_RADIUS).stream()
            .map(PetComponent::get)
            .filter(Objects::nonNull)
            .mapToInt(PetComponent::getLevel)
            .max()
            .orElse(0);
        
        // Base enchantment bonus scaling with level
        return Math.min(maxLevel * 0.5f, 5.0f); // Max +5 damage from enchantment resonance
    }
    
    /**
     * Check if player has active Mystic Bond (L7+ Enchantment-Bound).
     */
    public static boolean hasActiveMysticBond(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld)) {
            return false;
        }

        return getNearbyEnchantmentBoundPets(player, NEARBY_RADIUS).stream()
            .map(PetComponent::get)
            .filter(Objects::nonNull)
            .anyMatch(component -> component.getLevel() >= 7);
    }
    
    /**
     * Apply enchantment resonance effects when owner uses enchanted items.
     */
    public static void onEnchantedItemUse(ServerPlayerEntity player, net.minecraft.item.ItemStack item) {
        if (!hasNearbyEnchantmentBound(player) || !item.hasEnchantments()) {
            return;
        }
        
        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }

        // Find nearby Enchantment-Bound pets and apply resonance
        getNearbyEnchantmentBoundPets(player, NEARBY_RADIUS).forEach(enchantedPet -> {
            PetComponent petComp = PetComponent.get(enchantedPet);
            if (petComp != null && item.hasEnchantments()) {
                // Apply enchantment resonance effects using existing mechanics
                if (petComp.getLevel() >= 7) {
                    // High-level pets provide enhanced effects
                    EnchantmentBoundEchoes.applyEnhancedHaste(player, 60); // 3-second haste boost
                }
                // Check for extra drops or effects
                if (EnchantmentBoundEchoes.shouldEnableMountedExtraRolls(player)) {
                    // Enhanced enchantment effects active
                }
            }
        });
    }
}
