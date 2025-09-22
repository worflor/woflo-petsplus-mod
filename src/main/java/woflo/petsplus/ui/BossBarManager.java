package woflo.petsplus.ui;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages boss bar displays for ability feedback and cooldowns.
 */
public class BossBarManager {
    private static final Map<UUID, BossBarInfo> activeBossBars = new HashMap<>();
    
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
        info.bossBar.addPlayer(player);
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
        info.bossBar.addPlayer(player);
    }

    /**
     * Show or update a boss bar with a fixed percent (e.g., XP/HP). Auto-refreshes duration and keeps existing bar if present.
     */
    public static void showOrUpdateBossBar(ServerPlayerEntity player, Text message, float percent, BossBar.Color color, int durationTicks) {
        UUID playerId = player.getUuid();
        BossBarInfo info = activeBossBars.get(playerId);
        if (info == null) {
            info = new BossBarInfo(message.copy().formatted(Formatting.GRAY), durationTicks, color);
            info.fixedPercent = true;
            info.percent = clamp01(percent);
            activeBossBars.put(playerId, info);
            info.bossBar = new ServerBossBar(info.message, color, BossBar.Style.NOTCHED_20);
            info.bossBar.addPlayer(player);
            info.bossBar.setPercent(info.percent);
        } else {
            Text newMsg = message.copy().formatted(Formatting.GRAY);
            float newPct = clamp01(percent);
            info.fixedPercent = true;
            boolean changed = false;
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
            info.remainingTicks = durationTicks;
            info.totalTicks = durationTicks;
            if (info.bossBar != null && changed) {
                info.bossBar.setName(info.message);
                info.bossBar.setColor(info.color);
                info.bossBar.setPercent(info.percent);
                // Keep style for XP/HP as NOTCHED_20
                info.bossBar.setStyle(BossBar.Style.NOTCHED_20);
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
            info.bossBar.addPlayer(player);
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
                info.bossBar.setName(info.message);
                info.bossBar.setColor(info.color);
                info.bossBar.setStyle(BossBar.Style.PROGRESS);
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
        UUID playerId = player.getUuid();
        BossBarInfo info = activeBossBars.remove(playerId);
        if (info != null && info.bossBar != null) {
            info.bossBar.removePlayer(player);
        }
    }
    
    /**
     * Tick all active boss bars.
     * Call this from the main server tick handler.
     */
    public static void tickBossBars() {
        activeBossBars.entrySet().removeIf(entry -> {
            BossBarInfo info = entry.getValue();
            info.remainingTicks--;
            
            if (info.remainingTicks <= 0) {
                // Boss bar expired, remove it
                if (info.bossBar != null) {
                    // Note: In a full implementation, you'd need to get the player and remove the boss bar
                    // This is simplified for the current implementation
                }
                return true;
            } else {
                // Update progress: fixed percent mode keeps set percent; otherwise, show time-based countdown fill
                if (info.bossBar != null) {
                    if (info.fixedPercent) {
                        info.bossBar.setPercent(clamp01(info.percent));
                    } else {
                        float progress = info.totalTicks > 0 ? (float) info.remainingTicks / info.totalTicks : 1.0f;
                        info.bossBar.setPercent(progress);
                    }
                }
                return false;
            }
        });
    }

    /**
     * Extend the current boss bar duration for a player (used for linger behavior).
     */
    public static void extendDuration(ServerPlayerEntity player, int extraTicks) {
        BossBarInfo info = activeBossBars.get(player.getUuid());
        if (info != null) {
            info.remainingTicks = Math.max(info.remainingTicks, extraTicks);
            info.totalTicks = Math.max(info.totalTicks, extraTicks);
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
        
        BossBarInfo(Text message, int ticks, BossBar.Color color) {
            this.totalTicks = ticks;
            this.remainingTicks = ticks;
            this.message = message;
            this.color = color;
        }
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}