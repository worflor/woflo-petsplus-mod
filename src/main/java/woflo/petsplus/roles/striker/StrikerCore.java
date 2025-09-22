package woflo.petsplus.roles.striker;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.state.PetComponent;

/**
 * Implements Striker role mechanics: execution bonuses and finisher marks.
 * 
 * Core Features:
 * - Baseline: +Attack Damage/Speed scalars (handled in PetAttributeManager)
 * - Execution: Damage bonus vs low-health targets that have been recently damaged
 * - L7 Finisher Mark: Enhanced execution with slow effect and mount benefits
 * 
 * Design Philosophy:
 * - Burst/Execution archetype focused on finishing off wounded enemies
 * - Rewards tactical engagement and target prioritization
 * - Amplifies owner damage rather than direct pet attacks
 */
public class StrikerCore {
    
    public static void initialize() {
        // Register damage event handler for execution bonuses
        ServerLivingEntityEvents.AFTER_DAMAGE.register(StrikerCore::onEntityDamaged);
        
        // Register world tick for cleanup and processing
        ServerTickEvents.END_WORLD_TICK.register(StrikerCore::onWorldTick);
    }
    
    /**
     * Handle damage events for Striker execution mechanics.
     */
    private static void onEntityDamaged(LivingEntity entity, DamageSource damageSource, float baseDamageAmount, float damageTaken, boolean blocked) {
        // Only process damage dealt by players
        if (!(damageSource.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return;
        }
        
        // Check if attacker has nearby Striker pets
        if (!hasNearbyStrikerPet(attacker)) {
            return;
        }
        
        // Note the damage for execution tracking
        StrikerExecution.onOwnerDealDamage(attacker, entity, damageTaken);
        
        // Handle Finisher Mark attacks if applicable
        if (StrikerExecution.hasFinisherMark(entity)) {
            StrikerExecution.onAttackFinisherMark(attacker, entity, damageTaken);
        }
    }
    
    /**
     * Check if player has a nearby Striker pet.
     */
    private static boolean hasNearbyStrikerPet(ServerPlayerEntity player) {
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
                       component.getRole() == PetRole.STRIKER &&
                       entity.isAlive() &&
                       component.isOwnedBy(player) &&
                       entity.squaredDistanceTo(player) <= searchRadius * searchRadius;
            }
        ).size() > 0;
    }
    
    /**
     * World tick handler for cleanup and passive effects.
     */
    private static void onWorldTick(ServerWorld world) {
        // Striker execution system handles its own cleanup internally
        // No explicit cleanup needed here
    }
    
    /**
     * Get the execution damage bonus for a player.
     */
    public static float getExecutionBonus(ServerPlayerEntity player, LivingEntity target) {
        if (!hasNearbyStrikerPet(player)) {
            return 0.0f;
        }
        
        return StrikerExecution.evaluateExecution(player, target, 1.0f, false).bonusDamage();
    }
}