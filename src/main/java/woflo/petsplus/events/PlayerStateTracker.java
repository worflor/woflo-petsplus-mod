package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.OwnerCombatState;

/**
 * Handles player movement and state tracking events.
 */
public class PlayerStateTracker {
    
    public static void register() {
        // Register for player tick events to track sprint changes
        ServerPlayerEvents.AFTER_RESPAWN.register(PlayerStateTracker::onPlayerRespawn);
        
        Petsplus.LOGGER.info("Player state tracker registered");
    }
    
    /**
     * Called when a player respawns.
     */
    private static void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        // Reset combat state on respawn
        OwnerCombatState.remove(oldPlayer);
        
        // Trigger any resurrection abilities if it was a death respawn
        if (!alive) {
            // Check for Cursed One mount resistance
            woflo.petsplus.roles.cursedone.CursedOneMountBehaviors.applyMountResistanceOnResurrect(newPlayer);
        }
    }
    
    /**
     * Track sprint state changes for owners.
     * This should be called from a movement tracking system.
     */
    public static void trackSprintChange(PlayerEntity player, boolean isNowSprinting, boolean wasSprinting) {
        if (isNowSprinting && !wasSprinting) {
            // Sprint started
            CombatEventHandler.triggerAbilitiesForOwner(player, "after_owner_sprint_start");
        }
    }
    
    /**
     * Track fall damage for Edge Step and other fall-related abilities.
     */
    public static float modifyFallDamage(PlayerEntity player, float fallDamage, double fallDistance) {
        float modifiedDamage = fallDamage;
        
        // Apply Eclipsed Edge Step reduction
        if (woflo.petsplus.roles.eclipsed.EclipsedAdvancedAbilities.shouldTriggerEdgeStep(player, fallDistance)) {
            modifiedDamage = woflo.petsplus.roles.eclipsed.EclipsedAdvancedAbilities.applyEdgeStepFallReduction(player, modifiedDamage);
        }
        
        // Apply Skyrider mount fall reduction
        modifiedDamage = woflo.petsplus.roles.skyrider.SkyriderMountBehaviors.applyMountFallReduction(player, modifiedDamage);
        
        return modifiedDamage;
    }
    
    /**
     * Track when players start falling for fall-related triggers.
     */
    public static void trackFallStart(PlayerEntity player, double fallDistance) {
        if (fallDistance > 3.0) {
            java.util.Map<String,Object> data = new java.util.HashMap<>();
            data.put("fall_distance", fallDistance);
            CombatEventHandler.triggerAbilitiesForOwner(player, "owner_begin_fall", data);
        }
    }
}