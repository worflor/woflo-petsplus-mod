package woflo.petsplus.ui;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages boss bar displays for ability feedback and cooldowns.
 */
public class BossBarManager {
    // Thread-safe map for concurrent access from multiple threads
    private static final Map<UUID, BossBarInfo> activeBossBars = new ConcurrentHashMap<>();
    
    /**
     * Show a temporary boss bar for ability feedback.
     */
    public static void showTemporaryBossBar(ServerPlayerEntity player, String messageKey, Object[] args, 
                                          int durationTicks, BossBar.Color color) {
        UUID playerId = player.getUuid();
        
        // Remove existing boss bar if any
        removeBossBar(player);
        
    Text message = Text.translatable(messageKey, args).formatted(Formatting.GRAY).formatted(Formatting.BOLD);
        BossBarInfo info = new BossBarInfo(message, durationTicks, color);
        activeBossBars.put(playerId, info);
        
        // Create and show boss bar
        info.bossBar = new ServerBossBar(message, color, BossBar.Style.PROGRESS);
        try {
            info.bossBar.addPlayer(player);
        } catch (Exception e) {
            // Fallback: send action bar message instead
            player.sendMessage(message, true);
        }
    }

    /**
     * Show a temporary boss bar for an explicit message.
     */
    public static void showTemporaryBossBar(ServerPlayerEntity player, Text message, int durationTicks) {
        UUID playerId = player.getUuid();
        removeBossBar(player);
        Text styled = message.copy().formatted(Formatting.GRAY);
        BossBarInfo info = new BossBarInfo(styled, durationTicks, BossBar.Color.PURPLE);
        activeBossBars.put(playerId, info);
        info.bossBar = new ServerBossBar(styled, BossBar.Color.PURPLE, BossBar.Style.PROGRESS);
        try {
            info.bossBar.addPlayer(player);
        } catch (Exception e) {
            // Fallback: send action bar message instead
            player.sendMessage(styled, true);
        }
    }

    /**
     * Show or update a boss bar with a fixed percent (e.g., XP/HP). Auto-refreshes duration and keeps existing bar if present.
     */
    public static void showOrUpdateBossBar(ServerPlayerEntity player, Text message, float percent, BossBar.Color color, int durationTicks) {
        showOrUpdateBossBar(player, message, percent, color, durationTicks, false);
    }

    /**
     * Show or update a boss bar with a fixed percent and optional forced updates for animated content.
     */
    public static void showOrUpdateBossBar(ServerPlayerEntity player, Text message, float percent, BossBar.Color color, int durationTicks, boolean forceUpdate) {
        // Input validation
        if (player == null || message == null || color == null) {
            return; // Silently ignore invalid inputs to prevent crashes
        }
        if (durationTicks <= 0) {
            durationTicks = 20; // Minimum 1 second display
        }
        if (durationTicks > 72000) {
            durationTicks = 72000; // Maximum 1 hour display to prevent memory leaks
        }

        UUID playerId = player.getUuid();
        if (playerId == null) {
            return; // Invalid player state
        }

        BossBarInfo info = activeBossBars.get(playerId);
        if (info == null) {
            // Create new boss bar info
            Text safeMessage;
            try {
                safeMessage = message.copy().formatted(Formatting.GRAY);
            } catch (Exception e) {
                // Fallback to plain text if formatting fails
                safeMessage = Text.literal(message.getString()).formatted(Formatting.GRAY);
            }

            info = new BossBarInfo(safeMessage, durationTicks, color);
            info.fixedPercent = true;
            info.percent = clamp01(percent);
            info.forceUpdates = forceUpdate;
            info.lastUpdateTick = getCurrentTick(player);
            activeBossBars.put(playerId, info);

            try {
                info.bossBar = new ServerBossBar(info.message, color, BossBar.Style.NOTCHED_20);
                info.bossBar.addPlayer(player);
                info.bossBar.setPercent(info.percent);
            } catch (Exception e) {
                // Clean up on failure and fallback to action bar
                activeBossBars.remove(playerId);
                try {
                    player.sendMessage(info.message, true);
                } catch (Exception fallbackError) {
                    // Ultimate fallback - do nothing if even action bar fails
                }
            }
        } else {
            long currentTick = getCurrentTick(player);

            // Rate limiting: Skip updates if called too frequently (unless forced)
            if (!forceUpdate && info.lastUpdateTick > 0 && (currentTick - info.lastUpdateTick) < getMinUpdateInterval()) {
                // Update duration but skip visual update to prevent spam
                info.remainingTicks = Math.max(info.remainingTicks, durationTicks);
                info.totalTicks = Math.max(info.totalTicks, durationTicks);
                return;
            }

            Text newMsg;
            try {
                newMsg = message.copy().formatted(Formatting.GRAY);
            } catch (Exception e) {
                newMsg = Text.literal(message.getString()).formatted(Formatting.GRAY);
            }

            float newPct = clamp01(percent);
            info.fixedPercent = true;
            info.forceUpdates = forceUpdate;
            info.lastUpdateTick = currentTick;

            boolean changed = forceUpdate; // Always consider changed if forceUpdate is true
            if (!newMsg.getString().equals(info.message.getString())) {
                info.message = newMsg;
                changed = true;
            }
            if (Math.abs(newPct - info.percent) > 0.001f) {
                info.percent = newPct;
                changed = true;
            }
            if (info.color != color) {
                info.color = color;
                changed = true;
            }
            info.remainingTicks = Math.max(info.remainingTicks, durationTicks);
            info.totalTicks = Math.max(info.totalTicks, durationTicks);

            // Always update the message for animated content, even if string content is the same
            if (forceUpdate) {
                info.message = newMsg;
            }

            if (info.bossBar != null && changed) {
                try {
                    info.bossBar.setName(info.message);
                    info.bossBar.setColor(info.color);
                    info.bossBar.setPercent(info.percent);
                    info.bossBar.setStyle(BossBar.Style.NOTCHED_20);
                } catch (Exception e) {
                    // If boss bar update fails, try to recover or fall back
                    handleBossBarFailure(player, info, e);
                }
            }
        }
    }

    /**
     * Show a countdown/information bar (e.g., CD/Aura) using progress style.
     */
    public static void showOrUpdateInfoBar(ServerPlayerEntity player, Text message, BossBar.Color color, int durationTicks) {
        UUID playerId = player.getUuid();
        BossBarInfo info = activeBossBars.get(playerId);
        if (info == null) {
            info = new BossBarInfo(message.copy().formatted(Formatting.GRAY), durationTicks, color);
            info.fixedPercent = false;
            activeBossBars.put(playerId, info);
            info.bossBar = new ServerBossBar(info.message, color, BossBar.Style.PROGRESS);
            try {
                info.bossBar.addPlayer(player);
            } catch (Exception e) {
                // Fallback: send action bar message instead
                player.sendMessage(info.message, true);
            }
        } else {
            Text newMsg = message.copy().formatted(Formatting.GRAY);
            boolean changed = false;
            if (!newMsg.getString().equals(info.message.getString())) {
                info.message = newMsg;
                changed = true;
            }
            if (info.color != color) {
                info.color = color;
                changed = true;
            }
            info.fixedPercent = false;
            info.remainingTicks = durationTicks;
            info.totalTicks = durationTicks;
            if (info.bossBar != null && changed) {
                try {
                    info.bossBar.setName(info.message);
                    info.bossBar.setColor(info.color);
                    info.bossBar.setStyle(BossBar.Style.PROGRESS);
                } catch (Exception e) {
                    // If boss bar update fails, remove and fall back to action bar
                    detachBossBar(info);
                    player.sendMessage(info.message, true);
                }
            }
        }
    }
    
    /**
     * Show ability activation boss bar.
     */
    public static void showAbilityActivation(ServerPlayerEntity player, String abilityName) {
        showTemporaryBossBar(player, "petsplus.ability.activated", 
            new Object[]{abilityName}, 60, BossBar.Color.YELLOW);
    }
    
    /**
     * Show cooldown boss bar.
     */
    public static void showCooldownBar(ServerPlayerEntity player, String abilityName, int cooldownTicks) {
        showTemporaryBossBar(player, "petsplus.ability.cooldown", 
            new Object[]{abilityName}, cooldownTicks, BossBar.Color.RED);
    }
    
    /**
     * Show ready boss bar pulse.
     */
    public static void showReadyPulse(ServerPlayerEntity player, String abilityName) {
        showTemporaryBossBar(player, "petsplus.ability.ready", 
            new Object[]{abilityName}, 40, BossBar.Color.GREEN);
    }
    
    /**
     * Remove boss bar for player.
     */
    public static void removeBossBar(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUuid();
        if (playerId == null) {
            return;
        }

        BossBarInfo info = activeBossBars.remove(playerId);
        if (info != null) {
            detachBossBar(info);
        }
    }

    /**
     * Clear boss bars for dimension changes to avoid cross-dimensional display issues.
     */
    public static void clearForDimensionChange(ServerPlayerEntity player) {
        removeBossBar(player);
    }
    
    /**
     * Tick all active boss bars.
     * Call this from the main server tick handler.
     */
    public static void tickBossBars() {
        if (activeBossBars.isEmpty()) {
            return; // Early exit for performance
        }

        // Use iterator for safe removal during iteration
        activeBossBars.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            BossBarInfo info = entry.getValue();

            if (info == null) {
                return true; // Remove null entries
            }

            info.remainingTicks--;

            if (info.remainingTicks <= 0) {
                // Boss bar expired - clean up
                try {
                    detachBossBar(info);
                } catch (Exception e) {
                    // Silent cleanup failure - still remove from map
                }
                return true;
            }

            // Update progress for active boss bars
            if (info.shouldFallbackToActionBar()) {
                // In fallback mode - no boss bar updates needed
                return false;
            }

            if (info.bossBar != null) {
                try {
                    if (info.fixedPercent) {
                        info.bossBar.setPercent(clamp01(info.percent));
                    } else {
                        float progress = info.totalTicks > 0 ? (float) info.remainingTicks / info.totalTicks : 1.0f;
                        info.bossBar.setPercent(clamp01(progress));
                    }

                    // Reset failure count on successful update
                    if (info.failureCount > 0) {
                        info.resetFailures();
                    }
                } catch (Exception e) {
                    // Handle boss bar update failure
                    info.incrementFailure();

                    if (info.shouldFallbackToActionBar()) {
                        // Switch to fallback mode but keep the entry for duration tracking
                        detachBossBar(info);
                    }
                    // Continue to next tick for recovery attempt
                }
            }
            return false;
        });

        // Memory management: if we have too many boss bars, clean up old ones
        if (activeBossBars.size() > 100) {
            cleanupStaleBossBars();
        }
    }

    /**
     * Extend the current boss bar duration for a player (used for linger behavior).
     */
    public static void extendDuration(ServerPlayerEntity player, int extraTicks) {
        if (player == null || extraTicks <= 0) {
            return;
        }
        UUID playerId = player.getUuid();
        if (playerId == null) {
            return;
        }

        BossBarInfo info = activeBossBars.get(playerId);
        if (info != null) {
            synchronized (info) {
                info.remainingTicks = Math.max(info.remainingTicks, extraTicks);
                info.totalTicks = Math.max(info.totalTicks, extraTicks);
            }
        }
    }
    
    private static class BossBarInfo {
        int totalTicks;
        int remainingTicks;
        ServerBossBar bossBar;
        Text message;
        boolean fixedPercent = false;
        float percent = 1.0f;
        BossBar.Color color = BossBar.Color.PURPLE;
        boolean forceUpdates = false;
        long lastUpdateTick = 0;
        int failureCount = 0;
        boolean isFallbackMode = false;

        BossBarInfo(Text message, int ticks, BossBar.Color color) {
            this.totalTicks = Math.max(1, Math.min(72000, ticks)); // Clamp between 1 tick and 1 hour
            this.remainingTicks = this.totalTicks;
            this.message = message != null ? message : Text.literal("");
            this.color = color != null ? color : BossBar.Color.PURPLE;
        }

        boolean shouldFallbackToActionBar() {
            return isFallbackMode || failureCount >= 3;
        }

        void incrementFailure() {
            failureCount++;
            if (failureCount >= 3) {
                isFallbackMode = true;
            }
        }

        void resetFailures() {
            failureCount = 0;
            isFallbackMode = false;
        }
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /**
     * Get current world tick for rate limiting.
     */
    private static long getCurrentTick(ServerPlayerEntity player) {
        try {
            return player.getWorld().getTime();
        } catch (Exception e) {
            return System.currentTimeMillis() / 50; // Fallback to approximate tick count
        }
    }

    /**
     * Get minimum interval between updates (in ticks) to prevent spam.
     */
    private static long getMinUpdateInterval() {
        return 2; // Minimum 2 ticks (100ms) between non-forced updates
    }

    /**
     * Handle boss bar failure with recovery attempts and fallback.
     */
    private static void handleBossBarFailure(ServerPlayerEntity player, BossBarInfo info, Exception originalError) {
        info.incrementFailure();

        if (!info.shouldFallbackToActionBar()) {
            // Try to recover by recreating the boss bar
            try {
                detachBossBar(info);
                info.bossBar = new ServerBossBar(info.message, info.color, BossBar.Style.NOTCHED_20);
                info.bossBar.addPlayer(player);
                info.bossBar.setPercent(info.percent);
                info.resetFailures(); // Recovery successful
                return;
            } catch (Exception recoveryError) {
                // Recovery failed, will fall through to action bar
            }
        }

        // Fallback to action bar
        detachBossBar(info);
        try {
            player.sendMessage(info.message, true);
        } catch (Exception fallbackError) {
            // Even action bar failed - remove the boss bar entirely
            activeBossBars.remove(player.getUuid());
        }
    }

    /**
     * Clean up stale boss bars to prevent memory leaks.
     */
    private static void cleanupStaleBossBars() {
        final int maxBossBars = 50; // Reasonable limit
        if (activeBossBars.size() <= maxBossBars) {
            return;
        }

        // Find boss bars with the least remaining time and remove excess
        activeBossBars.entrySet()
            .stream()
            .sorted((a, b) -> Integer.compare(a.getValue().remainingTicks, b.getValue().remainingTicks))
            .limit(activeBossBars.size() - maxBossBars)
            .forEach(entry -> {
                try {
                    detachBossBar(entry.getValue());
                    activeBossBars.remove(entry.getKey());
                } catch (Exception e) {
                    // Silent cleanup failure
                }
            });
    }

    private static void detachBossBar(BossBarInfo info) {
        if (info == null || info.bossBar == null) {
            return;
        }

        try {
            for (ServerPlayerEntity player : List.copyOf(info.bossBar.getPlayers())) {
                try {
                    info.bossBar.removePlayer(player);
                } catch (Exception e) {
                    // Ignore individual player removal failures
                }
            }
        } catch (Exception e) {
            // Ignore failures to get player list
        }
        info.bossBar = null;
    }

    /**
     * Clean up all boss bars during server shutdown.
     */
    public static void shutdown() {
        for (Map.Entry<UUID, BossBarInfo> entry : activeBossBars.entrySet()) {
            try {
                detachBossBar(entry.getValue());
            } catch (Exception e) {
                // Ignore cleanup failures during shutdown
            }
        }
        activeBossBars.clear();
    }
}
