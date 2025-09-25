package woflo.petsplus.ui;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

/**
 * Centralizes UI styling rules: primary/secondary text, accents, and lightweight emphasis.
 * Enhanced with dynamic contextual styling based on pet state and custom color support.
 */
public final class UIStyle {
    private UIStyle() {}

    // === COLOR CONFIGURATION ===
    
    // Health gradient colors (hex codes for smooth transitions)
    public static final TextColor HEALTH_CRITICAL = TextColor.fromRgb(0xFF4444);      // Bright red
    public static final TextColor HEALTH_LOW = TextColor.fromRgb(0xFF8844);           // Orange
    public static final TextColor HEALTH_MEDIUM = TextColor.fromRgb(0xFFDD44);        // Yellow  
    public static final TextColor HEALTH_GOOD = TextColor.fromRgb(0x88FF44);          // Light green
    public static final TextColor HEALTH_HIGH = TextColor.fromRgb(0xDDDDDD);          // Light gray (configurable base)
    public static final TextColor HEALTH_PERFECT = TextColor.fromRgb(0x44FFFF);       // Bright aqua
    
    // Default base colors (configurable)
    public static TextColor DEFAULT_PET_NAME_COLOR = HEALTH_HIGH; // Can be overridden per pet
    public static final TextColor PRIMARY_TEXT = TextColor.fromRgb(0xAAAAAA);         // Gray
    public static final TextColor SECONDARY_TEXT = TextColor.fromRgb(0x555555);       // Dark gray
    
    // Level progression colors
    public static final TextColor LEVEL_NORMAL = TextColor.fromRgb(0x55FF55);         // Green
    public static final TextColor LEVEL_NEAR_TRIBUTE = TextColor.fromRgb(0xFFDD44);   // Yellow
    public static final TextColor LEVEL_TRIBUTE_READY = TextColor.fromRgb(0xFFAA00);  // Gold
    
    // Progress bar gradient colors (black to white with smooth steps)
    public static final TextColor[] PROGRESS_GRADIENT = {
        TextColor.fromRgb(0x111111),  // Very dark
        TextColor.fromRgb(0x333333),  // Dark
        TextColor.fromRgb(0x555555),  // Medium dark
        TextColor.fromRgb(0x777777),  // Medium
        TextColor.fromRgb(0x999999),  // Medium light
        TextColor.fromRgb(0xBBBBBB),  // Light
        TextColor.fromRgb(0xDDDDDD),  // Very light
        TextColor.fromRgb(0xFFFFFF)   // White
    };

    public static MutableText primary(String s) {
        return Text.literal(s).styled(style -> style.withColor(PRIMARY_TEXT));
    }

    public static MutableText secondary(String s) {
        return Text.literal(s).styled(style -> style.withColor(SECONDARY_TEXT));
    }

    public static MutableText value(String s, Formatting color) {
        return Text.literal(s).formatted(color);
    }
    
    public static MutableText valueHex(String s, TextColor color) {
        return Text.literal(s).styled(style -> style.withColor(color));
    }

    public static MutableText bold(Text base) {
        return base.copy().formatted(Formatting.BOLD);
    }

    public static MutableText italic(Text base) {
        return base.copy().formatted(Formatting.ITALIC);
    }

    public static MutableText sepDot() {
        return secondary(" • ");
    }

    public static MutableText spacer() {
        return secondary("  ");
    }

    // === DYNAMIC CONTEXTUAL STYLING ===
    
    /**
     * Pet name with smooth health-based color gradient and custom color support
     */
    public static MutableText dynamicPetName(String name, float healthPercent) {
        return dynamicPetName(name, healthPercent, null, 0);
    }

    /**
     * Pet name with custom base color override
     */
    public static MutableText dynamicPetName(String name, float healthPercent, TextColor customBaseColor) {
        return dynamicPetName(name, healthPercent, customBaseColor, 0);
    }

    /**
     * Pet name with health-based blinking effect and combat state priority
     * Gray by default, shows health color during combat/damage, blinks health color periodically
     */
    public static MutableText dynamicPetName(String name, float healthPercent, TextColor customBaseColor, long currentTick) {
        return dynamicPetName(name, healthPercent, customBaseColor, currentTick, false);
    }

    /**
     * Pet name with combat state override for immediate health color display
     */
    public static MutableText dynamicPetName(String name, float healthPercent, TextColor customBaseColor, long currentTick, boolean inCombat) {
        // Default color is gray
        TextColor baseColor = customBaseColor != null ? customBaseColor : PRIMARY_TEXT;

        // Determine health color
        TextColor healthColor;
        if (healthPercent <= 0.25f) {
            healthColor = HEALTH_CRITICAL;    // Red for critical
        } else if (healthPercent <= 0.35f) {
            healthColor = HEALTH_LOW;         // Orange for low
        } else if (healthPercent <= 0.60f) {
            healthColor = HEALTH_MEDIUM;      // Yellow for medium
        } else {
            healthColor = HEALTH_GOOD;        // Green for good
        }

        // Priority 1: Combat state (immediate health color)
        if (inCombat) {
            return valueHex(name, healthColor);
        }

        // Priority 2: Periodic blinking based on health percentage removed (damage taken)
        float healthMissing = 1.0f - healthPercent; // 0.0 = full health, 1.0 = no health
        int blinkInterval;
        boolean doubleBlink = false;
        
        // Blinking frequency increases with damage taken
        if (healthMissing >= 0.75f) {
            // Very injured (25% health or less) - rapid triple blink every 2s
            blinkInterval = 40; // 2 seconds
            doubleBlink = true; // Will be enhanced to triple blink below
        } else if (healthMissing >= 0.40f) {
            // Moderately injured (60% health or less) - double blink every 3s
            blinkInterval = 60; // 3 seconds  
            doubleBlink = true;
        } else if (healthMissing >= 0.05f) {
            // Lightly injured (95% health or less) - single blink every 5s
            blinkInterval = 100; // 5 seconds
        } else {
            // Full health - no blinking
            return valueHex(name, baseColor);
        }

        // Calculate blinking pattern
        long cyclePosition = currentTick % blinkInterval;
        boolean shouldBlink = false;

        if (healthMissing >= 0.75f) {
            // Triple blink pattern for critical health: blink at 0-3, 6-9, and 12-15 ticks
            shouldBlink = (cyclePosition >= 0 && cyclePosition <= 3) ||
                         (cyclePosition >= 6 && cyclePosition <= 9) ||
                         (cyclePosition >= 12 && cyclePosition <= 15);
        } else if (doubleBlink) {
            // Double blink pattern: blink at 0-3 ticks and 8-11 ticks
            shouldBlink = (cyclePosition >= 0 && cyclePosition <= 3) ||
                         (cyclePosition >= 8 && cyclePosition <= 11);
        } else {
            // Single blink pattern: blink at 0-5 ticks
            shouldBlink = cyclePosition >= 0 && cyclePosition <= 5;
        }

        TextColor displayColor = shouldBlink ? healthColor : baseColor;
        return valueHex(name, displayColor);
    }
    
    /**
     * Get a smooth gradient color between two colors based on percentage
     */
    public static TextColor interpolateColor(TextColor from, TextColor to, float percent) {
        percent = Math.max(0f, Math.min(1f, percent)); // Clamp to 0-1
        
        int fromRgb = from.getRgb();
        int toRgb = to.getRgb();
        
        int fromR = (fromRgb >> 16) & 0xFF;
        int fromG = (fromRgb >> 8) & 0xFF;
        int fromB = fromRgb & 0xFF;
        
        int toR = (toRgb >> 16) & 0xFF;
        int toG = (toRgb >> 8) & 0xFF;
        int toB = toRgb & 0xFF;
        
        int r = Math.round(fromR + (toR - fromR) * percent);
        int g = Math.round(fromG + (toG - fromG) * percent);
        int b = Math.round(fromB + (toB - fromB) * percent);
        
        return TextColor.fromRgb((r << 16) | (g << 8) | b);
    }

    /**
     * Level display with visual progress bar that shows XP progression using true color gradients
     */
    public static MutableText dynamicLevel(int level, float xpProgress, boolean canLevelUp, long currentTick) {
        // Create true gradient progress bar
        MutableText progressBar = createGradientProgressBar(xpProgress, 10);
        
        if (canLevelUp) {
            // Gentle pulsing effect at tribute threshold
            boolean pulse = (currentTick / 30) % 2 == 0;
            TextColor levelColor = pulse ? LEVEL_TRIBUTE_READY : LEVEL_NEAR_TRIBUTE;
            
            return secondary("Lv.").append(valueHex(String.valueOf(level), levelColor))
                .append(secondary(" ")).append(progressBar)
                .append(secondary(" ")).append(pulsingTextHex("◆", LEVEL_TRIBUTE_READY, LEVEL_NEAR_TRIBUTE, currentTick, 15));
        } else if (xpProgress > 0.85f) {
            // Close to leveling - subtle anticipation glow
            return secondary("Lv.").append(valueHex(String.valueOf(level), LEVEL_NEAR_TRIBUTE))
                .append(secondary(" ")).append(progressBar);
        } else {
            // Normal level display with gradient bar
            return secondary("Lv.").append(valueHex(String.valueOf(level), LEVEL_NORMAL))
                .append(secondary(" ")).append(progressBar);
        }
    }
    
    /**
     * Simplified level display for non-visual contexts
     */
    public static MutableText dynamicLevel(int level, float xpProgress, boolean canLevelUp) {
        return dynamicLevel(level, xpProgress, canLevelUp, 0);
    }

    /**
     * Smart health display that adapts based on context
     */
    public static MutableText smartHealth(float current, float max, boolean inCombat) {
        float percent = max > 0 ? current / max : 0;
        
        Formatting color;
        String indicator = "";
        
        if (percent <= 0.15f) {
            color = Formatting.RED;
            indicator = inCombat ? " ⚠" : " ✗"; // Warning in combat, X when safe
        } else if (percent <= 0.35f) {
            color = Formatting.YELLOW;
            indicator = inCombat ? " !" : "";
        } else if (percent >= 0.95f) {
            color = Formatting.GREEN;
            indicator = " ✓";
        } else {
            color = Formatting.WHITE;
        }
        
        return value(String.format("%.1f", current), color)
            .append(secondary("/"))
            .append(secondary(String.format("%.1f", max)))
            .append(value(indicator, color));
    }

    /**
     * Clean level display with XP-based color progression and XP gain flashing
     * Level color goes from black (0% XP) to white (100% XP), fades to/from green on XP gain
     */
    public static MutableText levelWithXpFlash(int level, float xpProgress, boolean canLevelUp, boolean recentXpGain, long currentTick) {
        TextColor levelColor;

        if (canLevelUp) {
            // Ready to level up - bright gold with gentle pulse
            boolean pulse = (currentTick / 30) % 2 == 0;
            levelColor = pulse ? LEVEL_TRIBUTE_READY : LEVEL_NEAR_TRIBUTE;
        } else if (recentXpGain) {
            // Recent XP gain - smooth fade to/from green over 40 ticks (2 seconds)
            levelColor = getXpFlashColor(xpProgress, currentTick);
        } else {
            // Normal: XP-based color progression from black to white
            levelColor = getXpProgressColor(xpProgress);
        }

        return secondary("Lv.").append(valueHex(String.valueOf(level), levelColor));
    }

    /**
     * Get color based on XP progress: black (0%) to white (100%)
     */
    private static TextColor getXpProgressColor(float xpProgress) {
        xpProgress = Math.max(0f, Math.min(1f, xpProgress)); // Clamp 0-1

        // Smooth progression from dark gray to white
        int intensity = Math.round(80 + (175 * xpProgress)); // 80-255 range for good visibility
        int rgb = (intensity << 16) | (intensity << 8) | intensity; // Grayscale

        return TextColor.fromRgb(rgb);
    }

    /**
     * Get smooth fading XP flash color: base → green → base over 2 seconds (40 ticks)
     */
    private static TextColor getXpFlashColor(float xpProgress, long currentTick) {
        // Calculate flash progress over 40 ticks (2 seconds)
        long flashCycle = currentTick % 100; // Within the 5-second recentXpGain window
        
        if (flashCycle >= 40) {
            // After flash period, return to normal color
            return getXpProgressColor(xpProgress);
        }
        
        // Create smooth fade: 0→1→0 over 40 ticks
        float flashPhase;
        if (flashCycle <= 20) {
            // Fade to green (0-20 ticks)
            flashPhase = flashCycle / 20.0f;
        } else {
            // Fade from green (20-40 ticks)
            flashPhase = (40 - flashCycle) / 20.0f;
        }
        
        // Interpolate between base XP color and bright green
        TextColor baseColor = getXpProgressColor(xpProgress);
        TextColor flashColor = TextColor.fromRgb(0x55FF55); // Bright green
        
        return interpolateColor(baseColor, flashColor, flashPhase);
    }

    /**
     * Get boss bar color based on health percentage
     */
    public static net.minecraft.entity.boss.BossBar.Color getHealthBasedBossBarColor(float healthPercent) {
        if (healthPercent <= 0.25f) {
            return net.minecraft.entity.boss.BossBar.Color.RED;    // Critical health
        } else if (healthPercent <= 0.60f) {
            return net.minecraft.entity.boss.BossBar.Color.YELLOW; // Low health
        } else {
            return net.minecraft.entity.boss.BossBar.Color.GREEN;  // Good health
        }
    }

    /**
     * Create clean pet display: "Name • Lv.X" where level color shows XP progress
     * Name blinks health color every 5s (3s with double blink for low health)
     */
    public static MutableText cleanPetDisplay(String name, float healthPercent, int level, float xpProgress, boolean canLevelUp, boolean recentXpGain, long currentTick) {
        return cleanPetDisplay(name, healthPercent, level, xpProgress, canLevelUp, recentXpGain, currentTick, false);
    }

    /**
     * Create clean pet display with combat state for immediate health color feedback
     */
    public static MutableText cleanPetDisplay(String name, float healthPercent, int level, float xpProgress, boolean canLevelUp, boolean recentXpGain, long currentTick, boolean inCombat) {
        return dynamicPetName(name, healthPercent, null, currentTick, inCombat)
            .append(sepDot())
            .append(levelWithXpFlash(level, xpProgress, canLevelUp, recentXpGain, currentTick));
    }
    
    /**
     * Simple XP display (kept for backward compatibility)
     */
    public static MutableText dynamicXP(float percent, boolean recentGain) {
        String progressBar = createProgressBar(percent, 8);
        Formatting color = recentGain ? Formatting.YELLOW : Formatting.GREEN;
        String indicator = recentGain ? " ↗" : "";
        
        return secondary("XP ").append(value(progressBar, color))
            .append(secondary(" ")).append(value(String.format("%.0f%%", percent * 100), color))
            .append(value(indicator, Formatting.YELLOW));
    }

    /**
     * Cooldown display with urgency indication and pulse effects
     */
    public static MutableText smartCooldown(String name, long ticks, long currentTick) {
        long sec = Math.max(0, ticks) / 20;
        long m = sec / 60;
        long s = sec % 60;
        String timeStr = m > 0 ? m + "m" + s + "s" : s + "s";
        
        Formatting color;
        String prefix;
        
        if (sec <= 3) {
            // Almost ready - pulsing green
            color = Formatting.GREEN;
            prefix = "⚡ ";
            return pulsingText(prefix, Formatting.GREEN, Formatting.DARK_GREEN, currentTick, 8)
                .append(primary(name))
                .append(secondary(" ")).append(pulsingText(timeStr, Formatting.GREEN, Formatting.DARK_GREEN, currentTick, 8));
        } else if (sec <= 10) {
            color = Formatting.YELLOW;
            prefix = "⏳ ";
        } else {
            color = Formatting.RED;
            prefix = "⏸ ";
        }
        
        return value(prefix, color).append(primary(name))
            .append(secondary(" ")).append(value(timeStr, color));
    }
    
    /**
     * Backward compatibility cooldown display
     */
    public static MutableText smartCooldown(String name, long ticks) {
        return smartCooldown(name, ticks, 0);
    }

    /**
     * Aura display with enhanced pulsing and activity indication
     */
    public static MutableText dynamicAura(String effects, boolean isPulsing, long currentTick) {
        if (isPulsing) {
            // Active pulsing aura with sparkle effect
            String sparkle = pulsingText("✨", Formatting.YELLOW, Formatting.GOLD, currentTick, 6).getString();
            return value(sparkle + " ", Formatting.YELLOW).append(secondary("Aura "))
                .append(pulsingText(effects, Formatting.LIGHT_PURPLE, Formatting.AQUA, currentTick, 12));
        } else {
            // Static aura
            return value("🌟 ", Formatting.LIGHT_PURPLE).append(secondary("Aura "))
                .append(value(effects, Formatting.LIGHT_PURPLE));
        }
    }
    
    /**
     * Backward compatibility aura display
     */
    public static MutableText dynamicAura(String effects, boolean isPulsing) {
        return dynamicAura(effects, isPulsing, 0);
    }

    /**
     * Role indicator that shows activity state
     */
    public static MutableText roleStatus(String roleName, boolean isActive, boolean onCooldown) {
        String icon;
        Formatting color;
        
        if (onCooldown) {
            icon = "⏸";
            color = Formatting.DARK_GRAY;
        } else if (isActive) {
            icon = "⚡";
            color = Formatting.YELLOW;
        } else {
            icon = "●";
            color = Formatting.GREEN;
        }
        
        return value(icon + " ", color).append(primary(roleName));
    }

    /**
     * Creates a visual progress bar using Unicode characters
     */
    private static String createProgressBar(float percent, int length) {
        int filled = Math.round(percent * length);
        StringBuilder bar = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        
        return bar.toString();
    }
    
    /**
     * Creates a simple progress bar optimized for boss bar display
     */
    public static MutableText createGradientProgressBar(float percent, int length) {
        // Use simpler characters for better boss bar compatibility
        return createSimpleProgressBar(percent, length);
    }

    /**
     * Creates a simple progress bar using standard formatting instead of complex gradients
     */
    public static MutableText createSimpleProgressBar(float percent, int length) {
        int filled = Math.round(percent * length);
        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < length; i++) {
            if (i < filled) {
                bar.append("▰"); // Simpler filled character
            } else {
                bar.append("▱"); // Simpler empty character
            }
        }

        // Use standard formatting instead of complex hex colors
        Formatting color = percent >= 0.85f ? Formatting.YELLOW : Formatting.GREEN;
        return value(bar.toString(), color);
    }
    
    /**
     * Creates a simple flashing progress bar for XP gain
     */
    public static MutableText createFlashingGradientBar(float progress, int length, TextColor flashColor, long currentTick) {
        int filled = Math.round(progress * length);
        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < length; i++) {
            if (i < filled) {
                bar.append("▰");
            } else {
                bar.append("▱");
            }
        }

        // Simple flashing effect using standard formatting
        boolean flash = (currentTick / 8) % 2 == 0;
        Formatting color = flash ? Formatting.YELLOW : Formatting.GREEN;

        return value(bar.toString(), color);
    }
    
    /**
     * Create a flashing effect text with fade timing
     */
    public static MutableText flashingText(String text, Formatting color, long currentTick, int flashRate) {
        boolean visible = (currentTick / flashRate) % 2 == 0;
        return visible ? value(text, color) : secondary("");
    }
    
    /**
     * Create a pulsing effect with alternating colors
     */
    public static MutableText pulsingText(String text, Formatting primary, Formatting secondary, long currentTick, int pulseRate) {
        boolean usePrimary = (currentTick / pulseRate) % 2 == 0;
        return value(text, usePrimary ? primary : secondary);
    }
    
    /**
     * Context bar display for cooldowns/auras with light gray base and colored accents
     */
    public static MutableText contextBar(String context, String details, Formatting accentColor, boolean hasActivity, long currentTick) {
        MutableText base = secondary("■").append(primary(" " + context + " "));
        
        if (hasActivity) {
            // Brief green flash for activity
            boolean flash = (currentTick % 40) < 5; // Flash for 5 ticks every 40 ticks (2 seconds)
            if (flash) {
                base = base.append(value("●", Formatting.GREEN));
            } else {
                base = base.append(value(details, accentColor));
            }
        } else {
            base = base.append(value(details, accentColor));
        }
        
        return base;
    }

    /**
     * Status indicator for different pet states
     */
    public static MutableText statusIndicator(String status) {
        return switch (status.toLowerCase()) {
            case "combat" -> value("⚔ ", Formatting.RED);
            case "following" -> value("👁 ", Formatting.GREEN);
            case "sitting" -> value("💤 ", Formatting.GRAY);
            case "mounted" -> value("🏇 ", Formatting.AQUA);
            case "injured" -> value("💔 ", Formatting.RED);
            case "hungry" -> value("🍖 ", Formatting.YELLOW);
            case "happy" -> value("😊 ", Formatting.GREEN);
            default -> value("● ", Formatting.WHITE);
        };
    }

    /**
     * Create a pulsing effect text with hex colors
     */
    public static MutableText pulsingTextHex(String text, TextColor color1, TextColor color2, long currentTick, int pulseRate) {
        boolean pulse = (currentTick / pulseRate) % 2 == 0;
        TextColor currentColor = pulse ? color1 : color2;
        return valueHex(text, currentColor);
    }

    /**
     * Tribute indicator for milestone pets
     */
    public static MutableText tributeNeeded(String itemName) {
        return Text.translatable("petsplus.tribute.needs", itemName).formatted(Formatting.GRAY);
    }
}
