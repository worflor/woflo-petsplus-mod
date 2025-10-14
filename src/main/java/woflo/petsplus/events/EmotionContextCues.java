package woflo.petsplus.events;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Deque;
import java.util.ArrayDeque;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import woflo.petsplus.Petsplus;

import woflo.petsplus.ui.UIFeedbackManager;

/**
 * Manages emotion context cues for players.
 * This is a utility class to prevent spamming players with too many emotion cues.
 */
public class EmotionContextCues implements PlayerTickListener {
    private static final Map<UUID, Map<String, Long>> PLAYER_CUE_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, AtomicLong> CUE_SEQUENCE_NUMBERS = new ConcurrentHashMap<>();
    
    // Add diagnostic logging
    private static final boolean DIAGNOSTIC_LOGGING = Boolean.getBoolean("petsplus.debug.cue_logging");
    
    // Singleton instance and accessors
    private static final EmotionContextCues INSTANCE = new EmotionContextCues();
    public static EmotionContextCues getInstance() { return INSTANCE; }
    public static void dumpJournal(ServerPlayerEntity player) {}
    public static Deque<Text> getJournalForPlayer(UUID playerId) { return new ArrayDeque<>(); }
    
    /**
     * Sends a cue to a player if they are not on cooldown for that cue.
     * @param player The player to send the cue to
     * @param cueId The unique identifier for the cue
     * @param message The message to send
     * @param cooldownTicks The cooldown in ticks before this cue can be sent again
     */
    public static void sendCue(ServerPlayerEntity player, String cueId, Text message, long cooldownTicks) {
        sendCue(player, cueId, null, message, cooldownTicks);
    }

    public static void sendCue(ServerPlayerEntity player, String cueId, MobEntity pet, Text message, long cooldownTicks) {
        if (player == null || cueId == null || message == null) {
            return;
        }

        UUID playerId = player.getUuid();
        Map<String, Long> playerCues = PLAYER_CUE_COOLDOWNS.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());

        long currentTime = player.getEntityWorld().getTime();
        if (!shouldSendCue(player, cueId, cooldownTicks, playerCues, currentTime)) {
            return;
        }

        dispatchToUi(player, pet, message);

        if (DIAGNOSTIC_LOGGING) {
            String channel = pet != null ? "action bar (pet)" : "action bar";
            Petsplus.LOGGER.info("[CUE-DEBUG] Sent cue {} to player {} via {}", cueId, player.getName().getString(), channel);
        }
    }

    /**
     * Sends a cue to a player without a cooldown.
     * @param player The player to send the cue to
     * @param cueId The unique identifier for the cue
     * @param message The message to send
     */
    public static void sendCue(ServerPlayerEntity player, String cueId, Text message) {
        sendCue(player, cueId, null, message, 0);
    }

    public static void sendCue(ServerPlayerEntity player, String cueId, MobEntity pet, Text message) {
        sendCue(player, cueId, pet, message, 0);
    }

    public static void sendPetCue(ServerPlayerEntity player, String cueId, MobEntity pet, String messageKey,
                                  long cooldownTicks, Object... args) {
        if (player == null || cueId == null || messageKey == null) {
            return;
        }

        if (pet == null) {
            sendCue(player, cueId, Text.translatable(messageKey, args), cooldownTicks);
            return;
        }

        UUID playerId = player.getUuid();
        Map<String, Long> playerCues = PLAYER_CUE_COOLDOWNS.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        long currentTime = player.getEntityWorld().getTime();

        if (!shouldSendCue(player, cueId, cooldownTicks, playerCues, currentTime)) {
            return;
        }

        UIFeedbackManager.sendActionBarMessage(player, pet, messageKey, args);

        if (DIAGNOSTIC_LOGGING) {
            Petsplus.LOGGER.info("[CUE-DEBUG] Sent cue {} to player {} via action bar", cueId, player.getName().getString());
        }
    }

    private static void dispatchToUi(ServerPlayerEntity player, MobEntity pet, Text message) {
        if (message.getContent() instanceof TranslatableTextContent translatable) {
            Object[] args = translatable.getArgs();
            if (pet != null) {
                // Tie cue to the specific pet as source
                UIFeedbackManager.sendActionBarMessage(player, pet, translatable.getKey(), args);
            } else {
                // Require focus when no explicit pet source is provided; drop otherwise
                boolean queued = woflo.petsplus.ui.ActionBarCueManager.queueCueRequireFocus(
                    player,
                    woflo.petsplus.ui.ActionBarCueManager.ActionBarCue.of(translatable.getKey(), args)
                );
                if (!queued) {
                    return; // no focused pet -> no message
                }
            }
            return;
        }

        // Non-translatable text: require focus and send directly, never chat
        boolean queued = woflo.petsplus.ui.ActionBarCueManager.queueTextCueRequireFocus(player, message.copy());
        if (!queued) {
            // nothing to do if not focused
        }
    }

    private static boolean shouldSendCue(ServerPlayerEntity player, String cueId, long cooldownTicks,
                                         Map<String, Long> playerCues, long currentTime) {
        Long lastSent = playerCues.get(cueId);

        if (DIAGNOSTIC_LOGGING) {
            String threadName = Thread.currentThread().getName();
            long sequenceNumber = CUE_SEQUENCE_NUMBERS.computeIfAbsent(player.getUuid(),
                id -> new AtomicLong(0)).incrementAndGet();
            Petsplus.LOGGER.info("[CUE-DEBUG] Thread: {} Player: {} Cue: {} Sequence: {} Time: {} LastSent: {} Cooldown: {}",
                threadName, player.getName().getString(), cueId, sequenceNumber, currentTime, lastSent, cooldownTicks);
        }

        if (lastSent != null && currentTime - lastSent < cooldownTicks) {
            if (DIAGNOSTIC_LOGGING) {
                Petsplus.LOGGER.info("[CUE-DEBUG] Cooldown active for cue {} on player {} (remaining: {} ticks)",
                    cueId, player.getName().getString(), cooldownTicks - (currentTime - lastSent));
            }
            return false;
        }

        Long previousTime = playerCues.putIfAbsent(cueId, currentTime);
        if (previousTime != null) {
            if (currentTime - previousTime < cooldownTicks) {
                if (DIAGNOSTIC_LOGGING) {
                    Petsplus.LOGGER.info("[CUE-DEBUG] Race condition detected for cue {} on player {}", cueId,
                        player.getName().getString());
                }
                return false;
            }
            playerCues.put(cueId, currentTime);
        }

        return true;
    }
    
    /**
     * Clears all cooldowns for a player.
     * @param player The player to clear cooldowns for
     */
    public static void clear(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        
        UUID playerId = player.getUuid();
        PLAYER_CUE_COOLDOWNS.remove(playerId);
        CUE_SEQUENCE_NUMBERS.remove(playerId);
        
        if (DIAGNOSTIC_LOGGING) {
            Petsplus.LOGGER.info("[CUE-DEBUG] Cleared all cooldowns for player {}", player.getName().getString());
        }
    }
    
    /**
     * Clears a specific cue cooldown for a player.
     * @param player The player to clear the cooldown for
     * @param cueId The cue ID to clear
     */
    public static void clearCue(ServerPlayerEntity player, String cueId) {
        if (player == null || cueId == null) {
            return;
        }
        
        UUID playerId = player.getUuid();
        Map<String, Long> playerCues = PLAYER_CUE_COOLDOWNS.get(playerId);
        if (playerCues != null) {
            playerCues.remove(cueId);
            
            if (DIAGNOSTIC_LOGGING) {
                Petsplus.LOGGER.info("[CUE-DEBUG] Cleared cooldown for cue {} on player {}", cueId, player.getName().getString());
            }
        }
    }
    
    /**
     * Clears all cooldowns for a player when they change dimensions.
     * @param player The player who changed dimensions
     */
    public static void clearForDimensionChange(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        
        clear(player);
        
        if (DIAGNOSTIC_LOGGING) {
            Petsplus.LOGGER.info("[CUE-DEBUG] Cleared cooldowns for dimension change for player {}", player.getName().getString());
        }
    }
    
    /**
     * Records a stimulus summary for diagnostic purposes.
     * @param player The player who received the stimulus
     * @param summary The stimulus summary
     */
    public static void recordStimulus(ServerPlayerEntity player, StimulusSummary summary) {
        if (DIAGNOSTIC_LOGGING && summary != null) {
            Petsplus.LOGGER.info("[CUE-DEBUG] Recorded stimulus for player {} with {} samples", 
                player.getName().getString(), summary.petCount());
        }
    }
    
    // PlayerTickListener stubs
    @Override
    public long nextRunTick(ServerPlayerEntity player) { return Long.MAX_VALUE; }
    
    @Override
    public void run(ServerPlayerEntity player, long currentTick) {}
}
