package woflo.petsplus.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import woflo.petsplus.api.registry.PetRoleType;
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
        ServerPlayerEvents.AFTER_RESPAWN.register(SleepEventHandler::onPlayerRespawn);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            if (player != null) {
                onPlayerDisconnect(player);
            }
        });
    }

    /**
     * Handle player starting to sleep
     */
    public static void onSleepStart(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUuid();
        if (Boolean.TRUE.equals(playerSleepStatus.put(playerId, true))) {
            return;
        }

        sleepStartTime.put(playerId, player.getWorld().getTime());
    }

    /**
     * Handle player finishing sleep (successful sleep)
     */
    public static void onSleepEnd(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUuid();
        playerSleepStatus.put(playerId, false);

        Long startTime = sleepStartTime.remove(playerId);
        if (startTime == null) {
            return;
        }

        long sleepDuration = player.getWorld().getTime() - startTime;

        // A full sleep cycle is typically 100 ticks (5 seconds) minimum
        if (sleepDuration >= 100) {
            onSuccessfulSleep(player);
        }
    }

    /**
     * Handle successful sleep completion
     */
    private static void onSuccessfulSleep(ServerPlayerEntity player) {
        // Trigger Eepy Eeper sleep mechanics
        EepyEeperCore.triggerSleepEvent(player);

        // Push calming emotions to nearby owned pets
        player.getWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class,
            player.getBoundingBox().expand(32),
            mob -> {
                woflo.petsplus.state.PetComponent pc = woflo.petsplus.state.PetComponent.get(mob);
                return pc != null && pc.isOwnedBy(player);
            }
        ).forEach(pet -> {
            woflo.petsplus.state.PetComponent pc = woflo.petsplus.state.PetComponent.get(pet);
            if (pc != null) {
                pc.pushEmotion(woflo.petsplus.state.PetComponent.Emotion.BLISSFUL, 0.25f);
                pc.pushEmotion(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.20f);
                pc.pushEmotion(woflo.petsplus.state.PetComponent.Emotion.RELIEF, 0.20f);
                pc.updateMood();
                if (pc.hasRole(PetRoleType.EEPY_EEPER)) {
                    EmotionContextCues.sendCue(player,
                        "role.eepy.dream." + pet.getUuidAsString(),
                        Text.translatable("petsplus.emotion_cue.role.eepy_dream", pet.getDisplayName()),
                        2400);
                }
            }
        });

        EmotionContextCues.sendCue(player,
            "sleep.rested",
            Text.translatable("petsplus.emotion_cue.sleep.rested"),
            2400);
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
                EmotionContextCues.sendCue(newPlayer,
                    "sleep.anchor",
                    Text.translatable("petsplus.emotion_cue.sleep.anchor"),
                    2400);
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
     * Remove all cached state for a disconnecting player.
     */
    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUuid();
        playerSleepStatus.remove(playerId);
        sleepStartTime.remove(playerId);
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