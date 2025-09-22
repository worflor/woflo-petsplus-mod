package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.roles.eepyeeper.EepyEeperCore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles sleep events for Eepy Eeper mechanics.
 * Tracks when players sleep and triggers appropriate pet behaviors.
 */
public class SleepEventHandler {

    private static final Map<UUID, Boolean> playerSleepStatus = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> sleepStartTime = new ConcurrentHashMap<>();

    public static void initialize() {
        ServerTickEvents.END_WORLD_TICK.register(SleepEventHandler::onWorldTick);
        ServerPlayerEvents.AFTER_RESPAWN.register(SleepEventHandler::onPlayerRespawn);
    }

    /**
     * Monitor player sleep status every tick
     */
    private static void onWorldTick(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            checkPlayerSleepStatus(player);
        }
    }

    /**
     * Check if player sleep status has changed
     */
    private static void checkPlayerSleepStatus(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        boolean currentlySleeping = player.isSleeping();
        boolean wasSleeping = playerSleepStatus.getOrDefault(playerId, false);

        if (currentlySleeping && !wasSleeping) {
            // Player just started sleeping
            onSleepStart(player);
        } else if (!currentlySleeping && wasSleeping) {
            // Player just woke up
            onSleepEnd(player);
        }

        playerSleepStatus.put(playerId, currentlySleeping);
    }

    /**
     * Handle player starting to sleep
     */
    private static void onSleepStart(ServerPlayerEntity player) {
        sleepStartTime.put(player.getUuid(), player.getWorld().getTime());
    }

    /**
     * Handle player finishing sleep (successful sleep)
     */
    private static void onSleepEnd(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        Long startTime = sleepStartTime.get(playerId);

        if (startTime != null) {
            long sleepDuration = player.getWorld().getTime() - startTime;

            // Check if this was a successful sleep (not interrupted)
            // A full sleep cycle is typically 100 ticks (5 seconds) minimum
            if (sleepDuration >= 100) {
                onSuccessfulSleep(player);
            }

            sleepStartTime.remove(playerId);
        }
    }

    /**
     * Handle successful sleep completion
     */
    private static void onSuccessfulSleep(ServerPlayerEntity player) {
        // Trigger Eepy Eeper sleep mechanics
        EepyEeperCore.triggerSleepEvent(player);
    }

    /**
     * Handle respawn anchor usage (should also trigger sleep benefits)
     */
    private static void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (alive) {
            // Check if respawn was from a respawn anchor in the Nether
            if (newPlayer.getWorld().getRegistryKey().getValue().getPath().equals("the_nether")) {
                // Trigger sleep-like benefits for respawn anchor usage
                EepyEeperCore.triggerSleepEvent(newPlayer);
            }
        }
    }

    /**
     * Manual trigger for sleep events (for testing or special cases)
     */
    public static void triggerSleepEvent(ServerPlayerEntity player) {
        onSuccessfulSleep(player);
    }

    /**
     * Check if a player is currently sleeping
     */
    public static boolean isPlayerSleeping(PlayerEntity player) {
        return playerSleepStatus.getOrDefault(player.getUuid(), false);
    }

    /**
     * Get how long a player has been sleeping (in ticks)
     */
    public static long getSleepDuration(PlayerEntity player) {
        Long startTime = sleepStartTime.get(player.getUuid());
        if (startTime == null || !isPlayerSleeping(player)) {
            return 0;
        }
        return player.getWorld().getTime() - startTime;
    }

    /**
     * Force trigger sleep completion for a player (admin/debug use)
     */
    public static void forceSleepCompletion(ServerPlayerEntity player) {
        onSuccessfulSleep(player);
    }
}