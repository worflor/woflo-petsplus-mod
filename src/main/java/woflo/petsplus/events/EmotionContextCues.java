package woflo.petsplus.events;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Deque;
import java.util.ArrayDeque;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.tracking.PlayerTickListener;

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
        if (player == null || cueId == null || message == null) {
            return;
        }
        
        UUID playerId = player.getUuid();
        Map<String, Long> playerCues = PLAYER_CUE_COOLDOWNS.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        
        long currentTime = player.getEntityWorld().getTime();
        Long lastSent = playerCues.get(cueId);
        
        // Add diagnostic logging
        if (DIAGNOSTIC_LOGGING) {
            String threadName = Thread.currentThread().getName();
            long sequenceNumber = CUE_SEQUENCE_NUMBERS.computeIfAbsent(playerId, id -> new AtomicLong(0)).incrementAndGet();
            Petsplus.LOGGER.info("[CUE-DEBUG] Thread: {} Player: {} Cue: {} Sequence: {} Time: {} LastSent: {} Cooldown: {}", 
                threadName, player.getName().getString(), cueId, sequenceNumber, currentTime, lastSent, cooldownTicks);
        }
        
        if (lastSent == null || currentTime - lastSent >= cooldownTicks) {
            // Use atomic operation to avoid race conditions
            Long previousTime = playerCues.putIfAbsent(cueId, currentTime);
            if (previousTime != null && currentTime - previousTime < cooldownTicks) {
                // Another thread already set the cooldown within the window
                if (DIAGNOSTIC_LOGGING) {
                    Petsplus.LOGGER.info("[CUE-DEBUG] Race condition detected for cue {} on player {}", cueId, player.getName().getString());
                }
                return;
            }
            
            player.sendMessage(message.copy().formatted(Formatting.GRAY));
            
            if (DIAGNOSTIC_LOGGING) {
                Petsplus.LOGGER.info("[CUE-DEBUG] Sent cue {} to player {}", cueId, player.getName().getString());
            }
        } else {
            if (DIAGNOSTIC_LOGGING) {
                Petsplus.LOGGER.info("[CUE-DEBUG] Cooldown active for cue {} on player {} (remaining: {} ticks)", 
                    cueId, player.getName().getString(), cooldownTicks - (currentTime - lastSent));
            }
        }
    }
    
    /**
     * Sends a cue to a player without a cooldown.
     * @param player The player to send the cue to
     * @param cueId The unique identifier for the cue
     * @param message The message to send
     */
    public static void sendCue(ServerPlayerEntity player, String cueId, Text message) {
        sendCue(player, cueId, message, 0);
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
