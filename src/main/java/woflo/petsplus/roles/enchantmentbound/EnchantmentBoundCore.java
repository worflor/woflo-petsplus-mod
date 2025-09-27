package woflo.petsplus.roles.enchantmentbound;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PlayerTickListener;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
public class EnchantmentBoundCore implements PlayerTickListener {

    private static final double NEARBY_RADIUS = 16.0;
    private static final long ENCHANTMENT_INTERVAL_TICKS = 10L;
    private static final long IDLE_RECHECK_TICKS = 40L;
    private static final Map<UUID, Long> NEXT_ENCHANTMENT_TICK = new ConcurrentHashMap<>();

    private static final EnchantmentBoundCore INSTANCE = new EnchantmentBoundCore();

    private EnchantmentBoundCore() {}

    public static EnchantmentBoundCore getInstance() {
        return INSTANCE;
    }
    
    public static void initialize() {
        // Register damage events for magical damage handling
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EnchantmentBoundCore::onEntityDamage);

        // Initialize the existing enchantment-bound handler
        EnchantmentBoundHandler.initialize();
    }

    @Override
    public long nextRunTick(ServerPlayerEntity player) {
        if (player == null) {
            return Long.MAX_VALUE;
        }
        return NEXT_ENCHANTMENT_TICK.getOrDefault(player.getUuid(), 0L);
    }

    @Override
    public void run(ServerPlayerEntity player, long currentTick) {
        if (player == null || player.isRemoved() || player.isSpectator()) {
            if (player != null) {
                NEXT_ENCHANTMENT_TICK.remove(player.getUuid());
            }
            return;
        }

        UUID playerId = player.getUuid();
        NEXT_ENCHANTMENT_TICK.put(playerId, currentTick + IDLE_RECHECK_TICKS);

        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        List<MobEntity> enchantmentPets = getNearbyEnchantmentBoundPets(player, NEARBY_RADIUS);
        if (enchantmentPets.isEmpty()) {
            return;
        }

        NEXT_ENCHANTMENT_TICK.put(playerId, currentTick + ENCHANTMENT_INTERVAL_TICKS);
        processEnchantmentEffects(player, enchantmentPets);
    }

    @Override
    public void onPlayerRemoved(ServerPlayerEntity player) {
        if (player != null) {
            NEXT_ENCHANTMENT_TICK.remove(player.getUuid());
        }
    }
    
    /**
     * Handle damage events for magical damage bonuses.
     */
    private static boolean onEntityDamage(LivingEntity entity, net.minecraft.entity.damage.DamageSource damageSource, float damageAmount) {
        // Handle Enchantment-Bound pet magical resistances
        if (entity instanceof MobEntity mobEntity) {
            PetComponent petComp = PetComponent.get(mobEntity);
            if (petComp != null && petComp.hasRole(PetRoleType.ENCHANTMENT_BOUND)) {
                // Apply magical damage resistance
                if (isMagicalDamage(damageSource)) {
                    return damageAmount <= 1.0f; // High magical resistance
                }
            }
        }
        
        return true; // Allow damage
    }
    
    private static void processEnchantmentEffects(ServerPlayerEntity player, List<MobEntity> enchantmentPets) {
        for (MobEntity enchantedPet : enchantmentPets) {
            PetComponent petComp = PetComponent.get(enchantedPet);
            if (petComp != null && petComp.getLevel() >= 7) {
                EnchantmentBoundEchoes.applyEnhancedHaste(player, EnchantmentBoundEchoes.getPerchedHasteBonusTicks(player));
            }
        }
    }
    
    /**
     * Check if player has a nearby Enchantment-Bound pet.
     */
    private static boolean hasNearbyEnchantmentBound(ServerPlayerEntity player) {
        return !getNearbyEnchantmentBoundPets(player, NEARBY_RADIUS).isEmpty();
    }

    private static List<MobEntity> getNearbyEnchantmentBoundPets(ServerPlayerEntity player, double radius) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
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
        
        if (!(player.getWorld() instanceof ServerWorld)) {
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
        if (!(player.getWorld() instanceof ServerWorld)) {
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
        
        if (!(player.getWorld() instanceof ServerWorld world)) {
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