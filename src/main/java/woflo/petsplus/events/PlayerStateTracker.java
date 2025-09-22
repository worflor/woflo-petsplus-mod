package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.Petsplus;
import woflo.petsplus.roles.skyrider.SkyriderCore;
import woflo.petsplus.roles.skyrider.SkyriderWinds;
import woflo.petsplus.state.OwnerCombatState;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Handles player movement and state tracking events.
 */
public class PlayerStateTracker {
    
    public static void register() {
        // Register for player tick events to track sprint changes
        ServerPlayerEvents.AFTER_RESPAWN.register(PlayerStateTracker::onPlayerRespawn);
        ServerTickEvents.END_WORLD_TICK.register(world ->
            world.getPlayers().forEach(PlayerStateTracker::onPlayerTick)
        );

        Petsplus.LOGGER.info("Player state tracker registered");
    }

    private static final Map<ServerPlayerEntity, Double> LAST_FALL_DISTANCE = new WeakHashMap<>();
    private static final Set<ServerPlayerEntity> FALL_TRIGGERED = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Monitor players each tick to detect when they cross fall thresholds.
     */
    private static void onPlayerTick(ServerPlayerEntity player) {
        if (player == null || player.isRemoved()) {
            return;
        }

        double currentFallDistance = player.fallDistance;
        boolean isFalling = !player.isOnGround() && player.getVelocity().y < 0 && currentFallDistance > 0.0;

        if (isFalling) {
            double previousFallDistance = LAST_FALL_DISTANCE.getOrDefault(player, 0.0);
            double threshold = SkyriderWinds.getWindlashMinFallBlocks();

            if (!FALL_TRIGGERED.contains(player) && currentFallDistance >= threshold && previousFallDistance < threshold) {
                FALL_TRIGGERED.add(player);
                trackFallStart(player, currentFallDistance);
            }

            LAST_FALL_DISTANCE.put(player, currentFallDistance);
        } else {
            LAST_FALL_DISTANCE.remove(player);
            FALL_TRIGGERED.remove(player);
        }
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
        double threshold = SkyriderWinds.getWindlashMinFallBlocks();
        if (fallDistance >= threshold) {
            java.util.Map<String,Object> data = new java.util.HashMap<>();
            data.put("fall_distance", fallDistance);
            CombatEventHandler.triggerAbilitiesForOwner(player, "owner_begin_fall", data);

            if (player instanceof ServerPlayerEntity serverPlayer) {
                boolean mounted = serverPlayer.getVehicle() != null;
                Petsplus.LOGGER.debug(
                    "Owner {} began falling {} blocks ({})",
                    serverPlayer.getName().getString(),
                    String.format("%.2f", fallDistance),
                    mounted ? "mounted" : "on foot"
                );
                SkyriderCore.onOwnerStartFalling(serverPlayer);
            }
        }
    }
}