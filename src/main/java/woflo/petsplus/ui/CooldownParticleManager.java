package woflo.petsplus.ui;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages subtle particle effects for cooldown refreshes on pets
 */
public class CooldownParticleManager {
    
    // Track last cooldown refresh time per pet to avoid spam
    private static final Map<UUID, Long> lastRefreshTime = new HashMap<>();
    private static final long MINIMUM_REFRESH_INTERVAL = 5; // 0.25 seconds minimum between effects
    private static long nextCleanupTick;
    
    /**
     * Trigger a subtle cooldown refresh particle effect around a pet
     */
    public static void triggerCooldownRefresh(ServerWorld world, MobEntity pet) {
        if (pet == null || world == null || world.isClient()) return;
        
        UUID petId = pet.getUuid();
        long currentTime = world.getTime();
        
        // Prevent particle spam - only trigger if enough time has passed
        if (lastRefreshTime.containsKey(petId)) {
            long timeSinceLastEffect = currentTime - lastRefreshTime.get(petId);
            if (timeSinceLastEffect < MINIMUM_REFRESH_INTERVAL) {
                return;
            }
        }
        
        lastRefreshTime.put(petId, currentTime);
        
        // Create the effect via centralized feedback routing
        woflo.petsplus.ui.FeedbackManager.emitFeedback("cooldown_refresh", pet, world);
    }
    
    /**
     * Creates a unique, subtle particle pattern for cooldown refresh
     */
    private static void spawnCooldownParticles(ServerWorld world, MobEntity pet) { /* centralized via FeedbackManager */ }
    
    /**
     * Cleanup old entries to prevent memory leaks
     */
    public static void cleanup(long currentTime) {
        long cutoff = currentTime - 6000L;
        lastRefreshTime.entrySet().removeIf(entry ->
            entry == null || entry.getValue() == null || entry.getValue() < 0L || entry.getValue() < cutoff);
    }

    public static void maybeCleanup(long currentTime) {
        if (currentTime < nextCleanupTick) {
            return;
        }
        cleanup(currentTime);
        long next = currentTime + 6000L;
        nextCleanupTick = next < currentTime ? currentTime + 6000L : next;
    }

    /**
     * Clean up all entries during server shutdown.
     */
    public static void shutdown() {
        lastRefreshTime.clear();
    }
}
