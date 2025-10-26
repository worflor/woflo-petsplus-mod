package woflo.petsplus.ui;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent.CopyToClipboard;
import net.minecraft.text.HoverEvent.ShowText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

/**
 * Helper for sending formatted chat components with basic styling.
 */
public class ChatLinks {

    /**
     * Simple container for a formatted text suggestion.
     */
    public record Suggest(String label, String command, String hover, String color, boolean bold) {}

    /**
     * Container for a formatted text command display.
     */
    public record RunCommand(String label, String command, String hover, String color, boolean bold) {}

    /**
     * Container for a formatted text component that copies a payload to the clipboard when clicked.
     */
    public record Copy(String label, String payload, String hover, String color, boolean bold) {}

    /**
     * Send a single formatted text component line to a player.
     */
    public static void sendSuggest(ServerPlayerEntity player, Suggest suggest) {
        if (player == null || suggest == null) return;
        // Note: Click/hover interactivity can be added once API usage is finalized for this MC version
        MutableText text = createFormattedText(suggest.label(), suggest.color(), suggest.bold());
        player.sendMessage(text, false);
    }

    /**
     * Send a single formatted text command display to a player.
     */
    public static void sendRunCommand(ServerPlayerEntity player, RunCommand command) {
        if (player == null || command == null) return;
        MutableText text = createFormattedText(command.label(), command.color(), command.bold());
        player.sendMessage(text, false);
    }

    /**
     * Send a formatted component that copies the supplied payload when clicked.
     */
    public static void sendCopy(ServerPlayerEntity player, Copy copy) {
        if (player == null || copy == null) return;

        MutableText text = createFormattedText(copy.label(), copy.color(), copy.bold());
        Style style = text.getStyle();

        if (copy.payload() != null && !copy.payload().isEmpty()) {
            style = style.withClickEvent(new CopyToClipboard(copy.payload()));
        }

        if (copy.hover() != null && !copy.hover().isEmpty()) {
            style = style.withHoverEvent(new ShowText(Text.literal(copy.hover())));
        }

        text.setStyle(style);
        player.sendMessage(text, false);
    }

    /**
     * Send a row of formatted suggestions (spaced) to a player.
     * perLine controls wrapping; if <= 0 no wrapping occurs.
     */
    public static void sendSuggestRow(ServerPlayerEntity player, Suggest[] suggests, int perLine) {
        if (player == null || suggests == null || suggests.length == 0) return;
        
        MutableText currentLine = Text.empty();
        int count = 0;
        
        for (Suggest s : suggests) {
            if (s == null) continue;
            
            if (count > 0) {
                currentLine.append(Text.literal("  ").formatted(Formatting.DARK_GRAY));
            }
            currentLine.append(createFormattedText(s.label(), s.color(), s.bold()));
            count++;
            
            if (perLine > 0 && count == perLine) {
                player.sendMessage(currentLine, false);
                currentLine = Text.empty();
                count = 0;
            }
        }
        
        if (count > 0) {
            player.sendMessage(currentLine, false);
        }
    }

    /**
     * Send a row of formatted commands (spaced) to a player.
     * perLine controls wrapping; if <= 0 no wrapping occurs.
     */
    public static void sendRunCommandRow(ServerPlayerEntity player, RunCommand[] commands, int perLine) {
        if (player == null || commands == null || commands.length == 0) return;
        
        MutableText currentLine = Text.empty();
        int count = 0;
        
        for (RunCommand c : commands) {
            if (c == null) continue;
            
            if (count > 0) {
                currentLine.append(Text.literal("  ").formatted(Formatting.DARK_GRAY));
            }
            currentLine.append(createFormattedText(c.label(), c.color(), c.bold()));
            count++;
            
            if (perLine > 0 && count == perLine) {
                player.sendMessage(currentLine, false);
                currentLine = Text.empty();
                count = 0;
            }
        }
        
        if (count > 0) {
            player.sendMessage(currentLine, false);
        }
    }

    /**
     * Create a formatted text component with color and styling.
     */
    private static MutableText createFormattedText(String label, String color, boolean bold) {
        if (label == null) return Text.empty();
        
        // Create the base text with color and formatting
        MutableText text = Text.literal(label);

    // Build style starting from current
    Style style = text.getStyle();

        // Apply color (supports named Formatting or hex like #RRGGBB)
        boolean appliedColor = false;
        if (color != null) {
            Integer hex = parseHexColor(color);
            if (hex != null) {
                style = style.withColor(TextColor.fromRgb(hex));
                appliedColor = true;
            } else {
                Formatting colorFormat = parseColor(color);
                if (colorFormat != null && colorFormat.isColor() && colorFormat.getColorValue() != null) {
                    style = style.withColor(TextColor.fromRgb(colorFormat.getColorValue()));
                    appliedColor = true;
                }
            }
        }
        if (!appliedColor) {
            // Fallback color
            style = style.withColor(TextColor.fromRgb(Formatting.AQUA.getColorValue()));
        }

        // Apply bold if requested
        if (bold) {
            style = style.withBold(true);
        }

        text.setStyle(style);
        return text;
    }
    
    /**
     * Parse color string to Formatting enum.
     */
    private static Formatting parseColor(String color) {
        if (color == null) return null;
        // Leverage Formatting.byName which sanitizes names (ignores spaces/underscores/case)
        return Formatting.byName(color);
    }

    /**
     * Parse hex color strings like "#RRGGBB" or "0xRRGGBB" to an integer RGB value.
     */
    private static Integer parseHexColor(String color) {
        String c = color.trim().toLowerCase();
        try {
            if (c.startsWith("#") && c.length() == 7) {
                return Integer.parseInt(c.substring(1), 16);
            }
            if (c.startsWith("0x") && c.length() == 8) {
                return Integer.parseInt(c.substring(2), 16);
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
