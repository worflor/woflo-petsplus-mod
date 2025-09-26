package woflo.petsplus.roles.skyrider;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements Skyrider role mechanics: fall safety and wind-based abilities.
 *
 * Core Features:
 * - Baseline: Reduced fall damage for pet, gust dodge on hit
 * - L7 Windlash Rider: Jump boost on owner fall, knockup attacks
 * - Air control and vertical mobility enhancement
 *
 * Design Philosophy:
 * - Air control and fall mastery archetype
 * - Enhances vertical mobility and aerial combat
 * - Provides fall safety and positioning advantages
 */
public class SkyriderCore {

    private static final double NEARBY_RADIUS = 16.0;
    private static final long WIND_INTERVAL_TICKS = 5L;
    private static final Map<UUID, Long> NEXT_WIND_TICK = new ConcurrentHashMap<>();
    
    public static void initialize() {
        // Register damage events for fall damage reduction
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(SkyriderCore::onEntityDamage);
        
    }

    public static void handlePlayerTick(ServerPlayerEntity player) {
        if (player.isRemoved() || player.isSpectator()) {
            return;
        }

        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        List<MobEntity> skyriderPets = getNearbySkyriderPets(player, NEARBY_RADIUS);
        if (skyriderPets.isEmpty()) {
            NEXT_WIND_TICK.remove(player.getUuid());
            return;
        }

        long now = world.getTime();
        long nextTick = NEXT_WIND_TICK.getOrDefault(player.getUuid(), 0L);
        if (now < nextTick) {
            return;
        }

        NEXT_WIND_TICK.put(player.getUuid(), now + WIND_INTERVAL_TICKS);
        processSkyriderWindEffects(player, skyriderPets);
    }

    public static void handlePlayerDisconnect(ServerPlayerEntity player) {
        NEXT_WIND_TICK.remove(player.getUuid());
    }
    
    /**
     * Handle damage events for fall damage reduction and gust dodge.
     */
    private static boolean onEntityDamage(LivingEntity entity, DamageSource damageSource, float damageAmount) {
        // Handle Skyrider pet fall damage reduction
        if (entity instanceof MobEntity mobEntity) {
            PetComponent petComp = PetComponent.get(mobEntity);
            if (petComp != null && petComp.hasRole(PetRoleType.SKYRIDER)) {
                // Reduce fall damage for Skyrider pets
                if (damageSource.isOf(DamageTypes.FALL)) {
                    return damageAmount <= 2.0f; // Absorb small fall damage completely
                }
                
                // Check for wind abilities  
                if (petComp.getOwner() instanceof ServerPlayerEntity owner &&
                    mobEntity instanceof PetsplusTameable tameable) {
                    // Use wind abilities for fall reduction checks
                    boolean shouldApplyFallReduction = SkyriderWinds.shouldApplyFallReductionToMount(mobEntity, owner);
                    if (shouldApplyFallReduction && SkyriderWinds.isOwnerFallingMinDistance(owner, 3.0)) {
                        // Apply levitation effect if configured
                        if (SkyriderWinds.shouldTriggerProjLevitation(mobEntity, owner)) {
                            // Wind effects triggered
                        }
                    }
                }
            }
        }
        
        // Handle owner fall damage reduction from nearby Skyrider
        if (entity instanceof ServerPlayerEntity player && damageSource.isOf(DamageTypes.FALL)) {
            if (hasNearbySkyrider(player)) {
                // Skyrider provides fall damage reduction to owner
                return damageAmount <= 4.0f; // Absorb moderate fall damage
            }
        }
        
        return true; // Allow damage
    }
    
    private static void processSkyriderWindEffects(ServerPlayerEntity player, List<MobEntity> skyriderPets) {
        for (MobEntity skyriderPet : skyriderPets) {
            if (skyriderPet instanceof PetsplusTameable) {
                SkyriderWinds.onServerTick(skyriderPet, player);
            }
        }
    }
    
    /**
     * Check if player has a nearby Skyrider pet.
     */
    private static boolean hasNearbySkyrider(ServerPlayerEntity player) {
        return !getNearbySkyriderPets(player, NEARBY_RADIUS).isEmpty();
    }

    private static List<MobEntity> getNearbySkyriderPets(ServerPlayerEntity player, double radius) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return java.util.Collections.emptyList();
        }

        return world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(radius),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null &&
                       component.hasRole(PetRoleType.SKYRIDER) &&
                       entity.isAlive() &&
                       component.isOwnedBy(player) &&
                       entity.squaredDistanceTo(player) <= radius * radius &&
                       entity instanceof PetsplusTameable;
            }
        );
    }
    
    /**
     * Check if a player has active Skyrider wind protection.
     */
    public static boolean hasActiveSkyriderProtection(ServerPlayerEntity player) {
        return hasNearbySkyrider(player);
    }
    
    /**
     * Get the fall damage reduction from nearby Skyrider pets.
     */
    public static float getSkyriderFallReduction(ServerPlayerEntity player) {
        if (!hasNearbySkyrider(player)) {
            return 0.0f;
        }
        
        // Skyrider provides significant fall damage reduction
        return 0.5f; // 50% fall damage reduction
    }
    
    /**
     * Trigger Windlash Rider effects when owner begins falling.
     */
    public static void onOwnerStartFalling(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        
        // Find nearby Skyrider pets with Windlash Rider (L7+)
        double minFallBlocks = SkyriderWinds.getWindlashMinFallBlocks();
        if (!SkyriderWinds.isOwnerFallingMinDistance(player, minFallBlocks)) {
            return;
        }

        if (!SkyriderWinds.isWindlashOffCooldown(player)) {
            long remaining = SkyriderWinds.getWindlashCooldownRemaining(player);
            Petsplus.LOGGER.debug(
                "Windlash Rider on cooldown for {} ({} ticks remaining)",
                player.getName().getString(),
                remaining
            );
            return;
        }

        var skyriderPets = world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(16.0),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null &&
                       component.hasRole(PetRoleType.SKYRIDER) &&
                       component.getLevel() >= 7 &&
                       entity.isAlive() &&
                       component.isOwnedBy(player) &&
                       entity instanceof PetsplusTameable;
            }
        );

        boolean triggered = false;

        for (MobEntity skyriderPet : skyriderPets) {
            if (!(skyriderPet instanceof PetsplusTameable tameable)) {
                continue;
            }

            if (!SkyriderWinds.isOwnerFallingMinDistance(player, minFallBlocks)) {
                continue;
            }

            boolean applyToMount = SkyriderWinds.shouldApplyFallReductionToMount(skyriderPet, player);
            SkyriderWinds.onServerTick(skyriderPet, player);
            triggered = true;

            Petsplus.LOGGER.debug(
                "Skyrider Windlash primed by pet {} for {} ({})",
                skyriderPet.getDisplayName().getString(),
                player.getName().getString(),
                applyToMount ? "mounted" : "on foot"
            );
        }

        if (triggered) {
            SkyriderWinds.markWindlashTriggered(player);
        }
    }
}