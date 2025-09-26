package woflo.petsplus.roles.scout;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements Scout role mechanics: detection pings and loot attraction.
 * 
 * Core Features:
 * - Baseline: +Move Speed scalar, occasional Glowing ping on nearby hostiles
 * - L3 Loot Wisp: Nearby drops and XP drift toward owner after combat
 * - Detection abilities and positioning advantages
 * 
 * Design Philosophy:
 * - Information advantage and mobility archetype
 * - Reveals threats and improves resource collection
 * - Enhances exploration and situational awareness
 */
public class ScoutCore {
    
    public static void initialize() {
        // Register combat events for loot wisp and detection
        ServerLivingEntityEvents.AFTER_DEATH.register(ScoutCore::onEntityDeath);
    }
    
    /**
     * Handle entity death for Loot Wisp mechanics.
     */
    private static void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        // Only process if death was caused by a player
        if (!(damageSource.getAttacker() instanceof ServerPlayerEntity player)) {
            return;
        }
        
        // Check if player has nearby Scout pets with Loot Wisp (L3+)
        if (!hasNearbyScoutWithLootWisp(player)) {
            return;
        }
        
        // Scout behaviors handle detection and spotting
        ScoutBehaviors.checkSpotterFallback(player);
    }
    
    /**
     * World tick handler for detection pings and passive effects.
     */
    private static final Map<UUID, Long> nextDetectionTick = new ConcurrentHashMap<>();

    public static void handlePlayerTick(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        long currentTick = world.getTime();
        long nextTick = nextDetectionTick.getOrDefault(player.getUuid(), 0L);
        if (currentTick < nextTick) {
            return;
        }

        nextDetectionTick.put(player.getUuid(), currentTick + 60);
        if (hasActiveScoutDetection(player)) {
            ScoutBehaviors.checkSpotterFallback(player);
        }
    }
    
    /**
     * Check if player has a nearby Scout pet with Loot Wisp ability (L3+).
     */
    private static boolean hasNearbyScoutWithLootWisp(ServerPlayerEntity player) {
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
                       component.hasRole(PetRoleType.SCOUT) &&
                       component.getLevel() >= 3 && // L3+ for Loot Wisp
                       entity.isAlive() &&
                       component.isOwnedBy(player) &&
                       entity.squaredDistanceTo(player) <= searchRadius * searchRadius;
            }
        ).size() > 0;
    }
    
    /**
     * Check if a player has active Scout detection coverage.
     */
    public static boolean hasActiveScoutDetection(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        
        double detectionRadius = 16.0;
        return world.getEntitiesByClass(
            MobEntity.class,
            player.getBoundingBox().expand(detectionRadius),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null &&
                       component.hasRole(PetRoleType.SCOUT) &&
                       entity.isAlive() &&
                       component.isOwnedBy(player) &&
                       entity.squaredDistanceTo(player) <= detectionRadius * detectionRadius;
            }
        ).size() > 0;
    }
    
    /**
     * Get the movement speed bonus from nearby Scout pets.
     */
    public static float getScoutSpeedBonus(ServerPlayerEntity player) {
        if (!hasActiveScoutDetection(player)) {
            return 0.0f;
        }
        
        // Scout pets provide inherent speed bonus through their presence
        return 0.1f; // 10% speed bonus when Scout is nearby
    }
}