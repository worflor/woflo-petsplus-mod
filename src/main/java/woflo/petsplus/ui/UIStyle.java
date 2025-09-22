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
        return Text.literal(s).setStyle(Text.literal("").getStyle().withColor(PRIMARY_TEXT));
    }

    public static MutableText secondary(String s) {
        return Text.literal(s).setStyle(Text.literal("").getStyle().withColor(SECONDARY_TEXT));
    }

    public static MutableText value(String s, Formatting color) {
        return Text.literal(s).formatted(color);
    }
    
    public static MutableText valueHex(String s, TextColor color) {
        return Text.literal(s).setStyle(Text.literal("").getStyle().withColor(color));
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
        return dynamicPetName(name, healthPercent, null);
    }
    
    /**
     * Pet name with custom base color override
     */
    public static MutableText dynamicPetName(String name, float healthPercent, TextColor customBaseColor) {
        TextColor color;
        boolean bold = false;
        
        if (healthPercent <= 0.15f) {
            // Critical health - bright red with bold
            color = HEALTH_CRITICAL;
            bold = true;
        } else if (healthPercent <= 0.35f) {
            // Low health - orange
            color = HEALTH_LOW;
        } else if (healthPercent <= 0.60f) {
            // Medium health - yellow
            color = HEALTH_MEDIUM; 
        } else if (healthPercent <= 0.85f) {
            // Good health - light green
            color = HEALTH_GOOD;
        } else if (healthPercent >= 0.95f) {
            // Perfect health - bright aqua with bold
            color = HEALTH_PERFECT;
            bold = true;
        } else {
            // High health - use custom color or default
            color = customBaseColor != null ? customBaseColor : DEFAULT_PET_NAME_COLOR;
        }
        
        MutableText text = valueHex(name, color);
        return bold ? text.formatted(Formatting.BOLD) : text;
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
     * Enhanced level display that integrates XP gain flashing with true color gradients
     */
    public static MutableText levelWithXpFlash(int level, float xpProgress, boolean canLevelUp, boolean recentXpGain, long currentTick) {
        // Create true gradient progress bar
        MutableText progressBar = createGradientProgressBar(xpProgress, 10);
        
        if (canLevelUp) {
            // Gentle pulsing effect at tribute threshold
            boolean pulse = (currentTick / 30) % 2 == 0;
            TextColor levelColor = pulse ? LEVEL_TRIBUTE_READY : LEVEL_NEAR_TRIBUTE;
            
            return secondary("Lv.").append(valueHex(String.valueOf(level), levelColor))
                .append(secondary(" ")).append(progressBar)
                .append(secondary(" ")).append(pulsingTextHex("◆", LEVEL_TRIBUTE_READY, LEVEL_NEAR_TRIBUTE, currentTick, 15));
        } else if (recentXpGain) {
            // Gentle green flash for recent XP gain (first 30 ticks = 1.5 seconds)
            long flashDuration = 30;
            long ticksSinceStart = currentTick % 100; // Cycle with detection system
            
            if (ticksSinceStart < flashDuration) {
                // Very gentle green pulse during XP gain
                boolean softFlash = (currentTick / 8) % 2 == 0; // Gentle 8-tick cycle
                TextColor flashColor = softFlash ? LEVEL_NORMAL : interpolateColor(LEVEL_NORMAL, SECONDARY_TEXT, 0.3f);
                
                return secondary("Lv.").append(valueHex(String.valueOf(level), LEVEL_NORMAL))
                    .append(secondary(" ")).append(createFlashingGradientBar(xpProgress, 10, flashColor, currentTick));
            } else {
                // Fade back to normal
                return secondary("Lv.").append(valueHex(String.valueOf(level), LEVEL_NORMAL))
                    .append(secondary(" ")).append(progressBar);
            }
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
     * Creates a gradient progress bar with actual hex color transitions
     */
    public static MutableText createGradientProgressBar(float percent, int length) {
        int filled = Math.round(percent * length);
        MutableText result = Text.literal("");
        
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                // Calculate gradient position (0.0 to 1.0)
                float gradientPos = (float)i / (length - 1);
                
                // Get color from gradient array
                int colorIndex = Math.min((int)(gradientPos * (PROGRESS_GRADIENT.length - 1)), PROGRESS_GRADIENT.length - 1);
                TextColor blockColor = PROGRESS_GRADIENT[colorIndex];
                
                // Use solid block character with gradient color
                result.append(valueHex("█", blockColor));
            } else {
                // Empty block with very dark color
                result.append(valueHex("░", PROGRESS_GRADIENT[0]));
            }
        }
        
        return result;
    }
    
    /**
     * Creates a gradient progress bar that flashes during XP gain
     */
    public static MutableText createFlashingGradientBar(float progress, int length, TextColor flashColor, long currentTick) {
        if (progress <= 0) return Text.literal("░".repeat(length)).styled(style -> style.withColor(PROGRESS_GRADIENT[0]));
        if (progress >= 1) {
            // Flash the entire bar on level up
            boolean flash = (currentTick / 5) % 2 == 0;
            TextColor fullColor = flash ? flashColor : PROGRESS_GRADIENT[PROGRESS_GRADIENT.length - 1];
            return Text.literal("█".repeat(length)).styled(style -> style.withColor(fullColor));
        }
        
        int filledCount = Math.round(progress * length);
        MutableText bar = Text.literal("");
        
        for (int i = 0; i < length; i++) {
            if (i < filledCount) {
                // Create gradient with subtle flash influence
                float gradientPos = (float)i / (length - 1);
                int colorIndex = Math.min((int)(gradientPos * (PROGRESS_GRADIENT.length - 1)), PROGRESS_GRADIENT.length - 1);
                TextColor baseColor = PROGRESS_GRADIENT[colorIndex];
                
                // Subtle flash influence - very gentle
                boolean flash = (currentTick / 8) % 2 == 0;
                TextColor finalColor = flash ? interpolateColor(baseColor, flashColor, 0.15f) : baseColor;
                
                bar.append(Text.literal("█").styled(style -> style.withColor(finalColor)));
            } else {
                bar.append(Text.literal("░").styled(style -> style.withColor(PROGRESS_GRADIENT[0])));
            }
        }
        
        return bar;
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
        return value("💎 ", Formatting.GOLD).append(secondary("Needs "))
            .append(value(itemName, Formatting.GOLD));
    }
}
