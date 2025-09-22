package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.effects.PetsplusEffectManager;
import woflo.petsplus.state.PetComponent;

import java.util.WeakHashMap;
import java.util.Map;

/**
 * Event handler for the enhanced status effects system.
 * Manages periodic aura applications and effect cleanup.
 */
public class PetsplusEffectEventHandler {
    
    private static final Map<ServerWorld, Long> lastTickTime = new WeakHashMap<>();
    private static final int EFFECT_TICK_INTERVAL = 20; // Every second
    
    /**
     * Initialize the effect event handler.
     */
    public static void initialize() {
        // Register world tick event for periodic effect application
        ServerTickEvents.END_WORLD_TICK.register(PetsplusEffectEventHandler::onWorldTick);
    }
    
    /**
     * Handle world tick events for effect management.
     */
    private static void onWorldTick(ServerWorld world) {
        long currentTime = world.getTime();
        Long lastTick = lastTickTime.get(world);
        
        // Only process effects every second to avoid performance issues
        if (lastTick == null || currentTime - lastTick >= EFFECT_TICK_INTERVAL) {
            lastTickTime.put(world, currentTime);
            
            // Process all pets in the world for aura effects
            for (var entity : world.iterateEntities()) {
                if (!(entity instanceof MobEntity mob)) continue;
                
                PetComponent petComp = PetComponent.get(mob);
                if (petComp == null) continue;
                
                PlayerEntity owner = petComp.getOwner();
                if (owner == null || !owner.isAlive()) continue;
                
                // Apply role-based aura effects
                try {
                    PetsplusEffectManager.applyRoleAuraEffects(world, mob, petComp, owner);
                } catch (Exception e) {
                    // Log error but don't crash the server
                    System.err.println("Error applying pet aura effects: " + e.getMessage());
                }
            }
            
            // Clean up old tracking data every 5 minutes
            if (currentTime % 6000 == 0) { // 6000 ticks = 5 minutes
                PetsplusEffectManager.cleanup();
            }
        }
    }
}