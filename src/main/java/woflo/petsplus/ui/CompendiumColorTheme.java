package woflo.petsplus.ui;

import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Nature-based color theming for the Pet Compendium.
 * Uses light gray/dark gray as base with nature-specific accent colors.
 * Minecraft 1.21+ supports hex colors via TextColor.fromRgb().
 */
public class CompendiumColorTheme {
    
    // Base colors (used for all natures)
    public static final String LIGHT_GRAY = "§7";      // Standard text
    public static final String DARK_GRAY = "§8";       // Punctuation, secondary
    public static final String WHITE = "§f";           // Emphasis, values
    
    // Formatting codes
    public static final String BOLD = "§l";
    public static final String UNDERLINE = "§n";
    public static final String RESET = "§r";
    
    /**
     * Gets the accent color for a specific nature.
     * Returns TextColor for use with .styled() methods.
     */
    @Nullable
    public static TextColor getNatureAccent(@Nullable Identifier natureId) {
        if (natureId == null) {
            return TextColor.fromRgb(0x888888); // Neutral gray
        }
        
        String path = natureId.getPath();
        
        // Wild Natures
        if (path.equals("frisky")) {
            return TextColor.fromRgb(0x7DD3FC); // Sky blue
        }
        if (path.equals("feral")) {
            return TextColor.fromRgb(0x86EFAC); // Soft green
        }
        if (path.equals("fierce")) {
            return TextColor.fromRgb(0xF87171); // Warm red
        }
        
        // Born Natures - Celestial
        if (path.equals("radiant")) {
            return TextColor.fromRgb(0xFDE68A); // Golden yellow
        }
        if (path.equals("nocturne")) {
            return TextColor.fromRgb(0xA78BFA); // Deep purple
        }
        if (path.equals("tempest")) {
            return TextColor.fromRgb(0x60A5FA); // Storm blue
        }
        
        // Born Natures - Environment
        if (path.equals("hearth")) {
            return TextColor.fromRgb(0xFBBF24); // Warm amber
        }
        if (path.equals("solace")) {
            return TextColor.fromRgb(0xA3A3A3); // Quiet silver
        }
        if (path.equals("festival")) {
            return TextColor.fromRgb(0xF472B6); // Vibrant pink
        }
        
        // Born Natures - Dimensional
        if (path.equals("infernal")) {
            return TextColor.fromRgb(0xDC2626); // Nether red
        }
        if (path.equals("otherworldly")) {
            return TextColor.fromRgb(0x8B5CF6); // End purple
        }
        if (path.equals("echoed")) {
            return TextColor.fromRgb(0x06B6D4); // Deep cyan
        }
        
        // Born Natures - Biome
        if (path.equals("mycelial")) {
            return TextColor.fromRgb(0xC084FC); // Mushroom purple
        }
        if (path.equals("verdant")) {
            return TextColor.fromRgb(0x22C55E); // Lush green
        }
        if (path.equals("frosty")) {
            return TextColor.fromRgb(0x93C5FD); // Ice blue
        }
        if (path.equals("tidal")) {
            return TextColor.fromRgb(0x0EA5E9); // Ocean blue
        }
        if (path.equals("molten")) {
            return TextColor.fromRgb(0xF59E0B); // Lava orange
        }
        if (path.equals("mire")) {
            return TextColor.fromRgb(0x84CC16); // Swamp green
        }
        if (path.equals("summit")) {
            return TextColor.fromRgb(0xE0F2FE); // Mountain white
        }
        
        // Born Natures - Special
        if (path.equals("gilded")) {
            return TextColor.fromRgb(0xFCD34D); // Gold
        }
        if (path.equals("gloom")) {
            return TextColor.fromRgb(0x52525B); // Dark gray
        }
        if (path.equals("relic")) {
            return TextColor.fromRgb(0x94A3B8); // Ancient stone
        }
        if (path.equals("unnatural")) {
            return TextColor.fromRgb(0xE879F9); // Arcane magenta
        }
        
        // Fallback
        return TextColor.fromRgb(0x888888);
    }
    
    /**
     * Gets the formatting code for a nature's accent (for legacy formatting).
     * Use this when you can't use TextColor.
     */
    public static String getNatureAccentCode(@Nullable Identifier natureId) {
        if (natureId == null) {
            return LIGHT_GRAY;
        }
        
        String path = natureId.getPath();
        
        // Wild Natures
        if (path.equals("frisky")) return "§b";    // Aqua
        if (path.equals("feral")) return "§a";     // Green
        if (path.equals("fierce")) return "§c";    // Red
        
        // Born - Celestial
        if (path.equals("radiant")) return "§e";   // Yellow
        if (path.equals("nocturne")) return "§5";  // Dark purple
        if (path.equals("tempest")) return "§9";   // Blue
        
        // Born - Environment
        if (path.equals("hearth")) return "§6";    // Gold
        if (path.equals("solace")) return "§7";    // Gray
        if (path.equals("festival")) return "§d";  // Light purple
        
        // Born - Dimensional
        if (path.equals("infernal")) return "§4";  // Dark red
        if (path.equals("otherworldly")) return "§5"; // Purple
        if (path.equals("echoed")) return "§3";    // Dark cyan
        
        // Born - Biome
        if (path.equals("mycelial")) return "§d";  // Light purple
        if (path.equals("verdant")) return "§2";   // Dark green
        if (path.equals("frosty")) return "§b";    // Aqua
        if (path.equals("tidal")) return "§3";     // Dark cyan
        if (path.equals("molten")) return "§6";    // Gold
        if (path.equals("mire")) return "§a";      // Green
        if (path.equals("summit")) return "§f";    // White
        
        // Born - Special
        if (path.equals("gilded")) return "§e";    // Yellow
        if (path.equals("gloom")) return "§8";     // Dark gray
        if (path.equals("relic")) return "§7";     // Gray
        if (path.equals("unnatural")) return "§d"; // Light purple
        
        return LIGHT_GRAY;
    }
    
    /**
     * Format a label with nature theming: "§8[§b⬥§8] §7Label"
     */
    public static String formatSectionHeader(String label, @Nullable Identifier natureId) {
        String accent = getNatureAccentCode(natureId);
        return DARK_GRAY + "[" + accent + "⬥" + DARK_GRAY + "] " + LIGHT_GRAY + label;
    }
    
    /**
     * Format a value with nature accent: "§7Health§8: §fvalue"
     */
    public static String formatLabelValue(String label, String value, @Nullable Identifier natureId) {
        return LIGHT_GRAY + label + DARK_GRAY + ": " + WHITE + value;
    }
    
    /**
     * Format a clickable link with nature theming.
     */
    public static String formatClickableLink(String label, @Nullable Identifier natureId) {
        String accent = getNatureAccentCode(natureId);
        return DARK_GRAY + "▸ " + accent + label + RESET;
    }
}
