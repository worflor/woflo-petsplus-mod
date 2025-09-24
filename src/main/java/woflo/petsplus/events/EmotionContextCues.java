package woflo.petsplus.events;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lightweight manager for delivering action-bar context cues tied to emotion events.
 * Ensures cues are throttled per-player/per-event to avoid overwhelming spam while
 * still giving players insight into why their pets' emotions are shifting.
 */
public final class EmotionContextCues {

    private static final Map<ServerPlayerEntity, Map<String, Long>> LAST_CUES = new WeakHashMap<>();

    private EmotionContextCues() {}

    /**
     * Send a contextual cue to the player's action bar with a configurable cooldown.
     *
     * @param player        player to receive the cue
     * @param cueId         stable identifier for the cue (per-player cooldown bucket)
     * @param text          translated text to display
     * @param cooldownTicks minimum ticks between repeats of the same cueId
     */
    public static void sendCue(ServerPlayerEntity player, String cueId, Text text, long cooldownTicks) {
        if (player == null || cueId == null || cueId.isEmpty() || text == null) {
            return;
        }

        Map<String, Long> playerCues = LAST_CUES.computeIfAbsent(player, p -> new HashMap<>());
        long now = player.getWorld().getTime();
        Long last = playerCues.get(cueId);
        if (last != null) {
            long delta = now - last;
            if (delta >= 0 && delta < cooldownTicks) {
                return;
            }
        }

        playerCues.put(cueId, now);
        player.sendMessage(text, true);
    }

    /**
     * Convenience overload with a default 5 second cooldown.
     */
    public static void sendCue(ServerPlayerEntity player, String cueId, Text text) {
        sendCue(player, cueId, text, 100);
    }

    /**
     * Clear all cooldowns for a given player (useful on respawn/dimension change).
     */
    public static void clear(ServerPlayerEntity player) {
        if (player != null) {
            LAST_CUES.remove(player);
        }
    }
}
