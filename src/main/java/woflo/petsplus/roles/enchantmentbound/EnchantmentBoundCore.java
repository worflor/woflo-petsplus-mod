package woflo.petsplus.roles.enchantmentbound;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.state.PetComponent;

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
public class EnchantmentBoundCore {
    
    public static void initialize() {
        // Register damage events for magical damage handling
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EnchantmentBoundCore::onEntityDamage);
        
        // Register world tick for enchantment effect processing
        ServerTickEvents.END_WORLD_TICK.register(EnchantmentBoundCore::onWorldTick);
        
        // Initialize the existing enchantment-bound handler
        EnchantmentBoundHandler.initialize();
    }
    
    /**
     * Handle damage events for magical damage bonuses.
     */
    private static boolean onEntityDamage(LivingEntity entity, net.minecraft.entity.damage.DamageSource damageSource, float damageAmount) {
        // Handle Enchantment-Bound pet magical resistances
        if (entity instanceof MobEntity mobEntity) {
            PetComponent petComp = PetComponent.get(mobEntity);
            if (petComp != null && petComp.getRole() == PetRole.ENCHANTMENT_BOUND) {
                // Apply magical damage resistance
                if (isMagicalDamage(damageSource)) {
                    return damageAmount <= 1.0f; // High magical resistance
                }
            }
        }
        
        return true; // Allow damage
    }
    
    /**
     * World tick handler for enchantment effects and mystic bond.
     */
    private static void onWorldTick(ServerWorld world) {
        // Process enchantment effects for all Enchantment-Bound pets
        processEnchantmentEffects(world);
    }
    
    /**
     * Process enchantment effects for Enchantment-Bound pets.
     */
    private static void processEnchantmentEffects(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (hasNearbyEnchantmentBound(player)) {
                // Apply mystic bond effects for high-level pets
                world.getEntitiesByClass(
                    MobEntity.class,
                    player.getBoundingBox().expand(16.0),
                    entity -> {
                        PetComponent component = PetComponent.get(entity);
                        return component != null && 
                               component.getRole() == PetRole.ENCHANTMENT_BOUND &&
                               entity.isAlive() &&
                               component.isOwnedBy(player);
                    }
                ).forEach(enchantedPet -> {
                    PetComponent petComp = PetComponent.get(enchantedPet);
                    if (petComp != null && petComp.getLevel() >= 7) {
                        // Apply mystic bond effects for high-level pets
                        EnchantmentBoundEchoes.applyEnhancedHaste(player, EnchantmentBoundEchoes.getPerchedHasteBonusTicks(player));
                    }
                });
            }
        }
    }
    
    /**
     * Check if player has a nearby Enchantment-Bound pet.
     */
    private static boolean hasNearbyEnchantmentBound(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        
        double searchRadius = 16.0;
        return world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(searchRadius),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.getRole() == PetRole.ENCHANTMENT_BOUND &&
                       entity.isAlive() &&
                       component.isOwnedBy(player) &&
                       entity.squaredDistanceTo(player) <= searchRadius * searchRadius;
            }
        ).size() > 0;
    }
    
    /**
     * Check if damage source is considered magical.
     */
    private static boolean isMagicalDamage(net.minecraft.entity.damage.DamageSource damageSource) {
        // Check for magical damage types
        return damageSource.isOf(net.minecraft.entity.damage.DamageTypes.MAGIC) ||
               damageSource.isOf(net.minecraft.entity.damage.DamageTypes.INDIRECT_MAGIC) ||
               damageSource.isOf(net.minecraft.entity.damage.DamageTypes.WITHER) ||
               damageSource.isOf(net.minecraft.entity.damage.DamageTypes.DRAGON_BREATH);
    }
    
    /**
     * Get enchantment damage bonus for nearby Enchantment-Bound pets.
     */
    public static float getEnchantmentDamageBonus(ServerPlayerEntity player) {
        if (!hasNearbyEnchantmentBound(player)) {
            return 0.0f;
        }
        
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return 0.0f;
        }
        
        // Calculate bonus based on highest level Enchantment-Bound pet
        int maxLevel = world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(16.0),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.getRole() == PetRole.ENCHANTMENT_BOUND &&
                       entity.isAlive() &&
                       component.isOwnedBy(player);
            }
        ).stream()
        .mapToInt(entity -> {
            PetComponent component = PetComponent.get(entity);
            return component != null ? component.getLevel() : 0;
        })
        .max()
        .orElse(0);
        
        // Base enchantment bonus scaling with level
        return Math.min(maxLevel * 0.5f, 5.0f); // Max +5 damage from enchantment resonance
    }
    
    /**
     * Check if player has active Mystic Bond (L7+ Enchantment-Bound).
     */
    public static boolean hasActiveMysticBond(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        
        return world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(16.0),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.getRole() == PetRole.ENCHANTMENT_BOUND &&
                       component.getLevel() >= 7 && // L7+ for Mystic Bond
                       entity.isAlive() &&
                       component.isOwnedBy(player);
            }
        ).size() > 0;
    }
    
    /**
     * Apply enchantment resonance effects when owner uses enchanted items.
     */
    public static void onEnchantedItemUse(ServerPlayerEntity player, net.minecraft.item.ItemStack item) {
        if (!hasNearbyEnchantmentBound(player) || !item.hasEnchantments()) {
            return;
        }
        
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        
        // Find nearby Enchantment-Bound pets and apply resonance
        world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(16.0),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null && 
                       component.getRole() == PetRole.ENCHANTMENT_BOUND &&
                       entity.isAlive() &&
                       component.isOwnedBy(player);
            }
        ).forEach(enchantedPet -> {
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