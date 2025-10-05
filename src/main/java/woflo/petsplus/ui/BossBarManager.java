package woflo.petsplus.ui;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import woflo.petsplus.state.tracking.PlayerTickDispatcher;
import woflo.petsplus.state.tracking.PlayerTickListener;

/**
 * Manages boss bar displays for ability feedback and cooldowns.
 */
public final class BossBarManager implements PlayerTickListener {
    private static final BossBarManager INSTANCE = new BossBarManager();

    private BossBarManager() {}

    public static BossBarManager getInstance() {
        return INSTANCE;
    }
    // Thread-safe map for concurrent access from multiple threads
    private static final Map<UUID, BossBarInfo> activeBossBars = new ConcurrentHashMap<>();

    private static void scheduleBossBar(ServerPlayerEntity player, BossBarInfo info, long tick, boolean immediate) {
        if (player == null || info == null) {
            return;
        }

        info.scheduleAt(tick);
        if (immediate) {
            PlayerTickDispatcher.requestImmediateRun(player, INSTANCE);
        }
    }

    private static long resolveServerTick(ServerPlayerEntity player) {
        if (player == null) {
            return 0L;
        }

        MinecraftServer server = player.getServer();
        if (server != null) {
            return server.getTicks();
        }

        return getCurrentTick(player);
    }
    
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
        scheduleBossBar(player, info, resolveServerTick(player), false);

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
        scheduleBossBar(player, info, resolveServerTick(player), false);
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

        long scheduleTick = resolveServerTick(player);
        BossBarInfo info = activeBossBars.get(playerId);
        if (info == null) {
            // Create new boss bar info
            Text safeMessage;
            try {
                safeMessage = message.copy().formatted(Formatting.GRAY);
            } catch (Exception e) {
                // Fallback to plain text if formatting fails
                if (woflo.petsplus.Petsplus.DEBUG_MODE) {
                    woflo.petsplus.Petsplus.LOGGER.warn("Boss bar message formatting failed, using fallback: {}", e.getMessage());
                }
                safeMessage = Text.literal(message.getString()).formatted(Formatting.GRAY);
            }

            info = new BossBarInfo(safeMessage, durationTicks, color);
            info.fixedPercent = true;
            info.percent = clamp01(percent);
            info.lastUpdateTick = getCurrentTick(player);
            activeBossBars.put(playerId, info);
            scheduleBossBar(player, info, scheduleTick, false);

            try {
                // Use PROGRESS style for health bars (smooth gradient), not NOTCHED_20
                info.bossBar = new ServerBossBar(info.message, color, BossBar.Style.PROGRESS);
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

            // Adaptive rate limiting: Skip updates if called too frequently (unless forced)
            long adaptiveInterval = getAdaptiveUpdateInterval(info, currentTick);
            if (!forceUpdate && info.lastUpdateTick > 0 && (currentTick - info.lastUpdateTick) < adaptiveInterval) {
                // Update duration but skip visual update to prevent spam
                info.remainingTicks = Math.max(info.remainingTicks, durationTicks);
                info.totalTicks = Math.max(info.totalTicks, durationTicks);
                scheduleBossBar(player, info, scheduleTick, false);
                return;
            }

            Text newMsg;
            try {
                newMsg = message.copy().formatted(Formatting.GRAY);
            } catch (Exception e) {
                if (woflo.petsplus.Petsplus.DEBUG_MODE) {
                    woflo.petsplus.Petsplus.LOGGER.warn("Boss bar message update formatting failed, using fallback: {}", e.getMessage());
                }
                newMsg = Text.literal(message.getString()).formatted(Formatting.GRAY);
            }

            float newPct = clamp01(percent);
            info.fixedPercent = true;
            info.lastUpdateTick = currentTick;

            boolean changed = forceUpdate; // Always consider changed if forceUpdate is true
            if (!newMsg.getString().equals(info.message.getString())) {
                info.message = newMsg;
                changed = true;
            }
            if (Math.abs(newPct - info.percent) > EPSILON) {
                info.percent = newPct;
                changed = true;
            }
            if (info.color != color) {
                // Start color transition instead of instant change
                if (info.pendingColor != color) {
                    info.pendingColor = color;
                    info.colorTransitionTicks = BossBarInfo.COLOR_TRANSITION_DURATION;
                }
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
                    // Keep PROGRESS style for smooth health display
                    info.bossBar.setStyle(BossBar.Style.PROGRESS);
                } catch (Exception e) {
                    // If boss bar update fails, try to recover or fall back
                    handleBossBarFailure(player, info, e);
                }
            }

            scheduleBossBar(player, info, scheduleTick, false);
        }
    }

    /**
     * Show a countdown/information bar (e.g., CD/Aura) using progress style.
     */
    public static void showOrUpdateInfoBar(ServerPlayerEntity player, Text message, BossBar.Color color, int durationTicks) {
        UUID playerId = player.getUuid();
        long scheduleTick = resolveServerTick(player);
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
        if (info != null) {
            scheduleBossBar(player, info, scheduleTick, false);
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
            info.clearSchedule();
            detachBossBar(info);
        }
    }

    /**
     * Clear boss bars for dimension changes to avoid cross-dimensional display issues.
     */
    public static void clearForDimensionChange(ServerPlayerEntity player) {
        removeBossBar(player);
    }
    
    @Override
    public long nextRunTick(ServerPlayerEntity player) {
        if (player == null) {
            return Long.MAX_VALUE;
        }

        BossBarInfo info = activeBossBars.get(player.getUuid());
        if (info == null) {
            return Long.MAX_VALUE;
        }

        return info.nextRunTick();
    }

    @Override
    public void run(ServerPlayerEntity player, long currentTick) {
        if (player == null) {
            return;
        }

        BossBarInfo info = activeBossBars.get(player.getUuid());
        if (info == null) {
            return;
        }

        info.clearSchedule();

        if (info.shouldFallbackToActionBar()) {
            if (activeBossBars.remove(player.getUuid(), info)) {
                info.clearSchedule();
                detachBossBar(info);
            }
            return;
        }

        info.remainingTicks--;
        if (info.remainingTicks <= 0) {
            if (activeBossBars.remove(player.getUuid(), info)) {
                info.clearSchedule();
                detachBossBar(info);
            }
            return;
        }

        if (info.bossBar == null) {
            if (activeBossBars.remove(player.getUuid(), info)) {
                info.clearSchedule();
            }
            return;
        }

        try {
            // Update color transition if active
            if (info.colorTransitionTicks > 0) {
                info.colorTransitionTicks--;
                if (info.colorTransitionTicks == 0 && info.pendingColor != null) {
                    // Transition complete, apply new color
                    info.color = info.pendingColor;
                    info.pendingColor = null;
                    if (info.bossBar != null) {
                        info.bossBar.setColor(info.color);
                    }
                }
            }

            if (info.fixedPercent) {
                info.bossBar.setPercent(clamp01(info.percent));
            } else {
                float progress = info.totalTicks > 0 ? (float) info.remainingTicks / info.totalTicks : 1.0f;
                info.bossBar.setPercent(clamp01(progress));
            }

            if (info.failureCount > 0) {
                info.resetFailures();
            }
        } catch (Exception e) {
            info.incrementFailure();
            if (info.shouldFallbackToActionBar()) {
                if (activeBossBars.remove(player.getUuid(), info)) {
                    info.clearSchedule();
                    detachBossBar(info);
                }
                return;
            }
        }

        info.scheduleAt(currentTick + 1);
    }

    @Override
    public void onPlayerRemoved(ServerPlayerEntity player) {
        onPlayerDisconnect(player);
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        BossBarInfo info = activeBossBars.remove(player.getUuid());
        if (info != null) {
            info.clearSchedule();
            detachBossBar(info);
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
            scheduleBossBar(player, info, resolveServerTick(player), false);
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
        long lastUpdateTick = 0;
        int failureCount = 0;
        boolean isFallbackMode = false;
        private long nextRunTick = Long.MAX_VALUE;
        BossBar.Color pendingColor = null;
        int colorTransitionTicks = 0;
        private static final int COLOR_TRANSITION_DURATION = 2; // 2 ticks (100ms) smooth transition

        long nextRunTick() {
            return nextRunTick;
        }

        void scheduleAt(long tick) {
            if (tick < 0) {
                tick = 0;
            }
            if (tick < nextRunTick) {
                nextRunTick = tick;
            }
        }

        void clearSchedule() {
            nextRunTick = Long.MAX_VALUE;
        }

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

    private static final float EPSILON = 0.001f; // Epsilon for floating point comparisons

    private static float clamp01(float v) {
        // Clamp with epsilon tolerance to avoid micro-updates from floating point errors
        if (v < EPSILON) return 0;
        if (v > 1.0f - EPSILON) return 1.0f;
        return v;
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
     * Adaptive: increases if updates are too frequent, decreases during calm periods.
     */
    private static long getMinUpdateInterval() {
        return 4; // Minimum 4 ticks (200ms) for smoother updates and better performance
    }

    /**
     * Get adaptive update interval based on update frequency
     */
    private static long getAdaptiveUpdateInterval(BossBarInfo info, long currentTick) {
        if (info.lastUpdateTick <= 0) {
            return getMinUpdateInterval();
        }

        long timeSinceLastUpdate = currentTick - info.lastUpdateTick;

        // If updates are very frequent (< 4 ticks), increase throttle
        if (timeSinceLastUpdate < 4) {
            return 6; // Throttle to 300ms during spam
        }

        // If updates are moderate (4-10 ticks), use standard interval
        if (timeSinceLastUpdate < 10) {
            return 4; // Standard 200ms
        }

        // If updates are infrequent (> 10 ticks), allow faster response
        return 2; // Faster 100ms response for low-frequency updates
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
                info.bossBar = new ServerBossBar(info.message, info.color, BossBar.Style.PROGRESS);
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

    private static void detachBossBar(BossBarInfo info) {
        if (info == null || info.bossBar == null) {
            return;
        }

        try {
            // Get player list snapshot to avoid concurrent modification
            List<ServerPlayerEntity> playerSnapshot;
            try {
                playerSnapshot = List.copyOf(info.bossBar.getPlayers());
            } catch (Exception e) {
                // Boss bar already disposed or in invalid state
                info.bossBar = null;
                return;
            }

            // Remove each player safely
            for (ServerPlayerEntity player : playerSnapshot) {
                if (player == null) {
                    continue;
                }
                try {
                    info.bossBar.removePlayer(player);
                } catch (Exception e) {
                    // Ignore individual player removal failures - player may have disconnected
                }
            }
        } catch (Exception e) {
            // Final safety net for any unexpected failures
        } finally {
            // Always null out the boss bar reference to prevent memory leaks
            info.bossBar = null;
        }
    }

    /**
     * Clean up all boss bars during server shutdown.
     */
    public static void shutdown() {
        for (Map.Entry<UUID, BossBarInfo> entry : activeBossBars.entrySet()) {
            try {
                entry.getValue().clearSchedule();
                detachBossBar(entry.getValue());
            } catch (Exception e) {
                // Ignore cleanup failures during shutdown
            }
        }
        activeBossBars.clear();
    }
}
