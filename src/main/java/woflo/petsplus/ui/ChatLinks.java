package woflo.petsplus.ui;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Helper for sending clickable chat components using tellraw with suggest_command/hover text.
 */
public class ChatLinks {

    /**
     * Simple container for a clickable suggestion.
     */
    public record Suggest(String label, String command, String hover, String color, boolean bold) {}

    /**
     * Send a single clickable suggestion component line to a player.
     */
    public static void sendSuggest(ServerPlayerEntity player, Suggest suggest) {
        if (player == null || player.getServer() == null || suggest == null) return;
        String json = componentJson(suggest);
        String full = wrapExtras(json);
        executeTellraw(player, full);
    }

    /**
     * Send a row of clickable suggestions (spaced) to a player.
     * perLine controls wrapping; if <= 0 no wrapping occurs.
     */
    public static void sendSuggestRow(ServerPlayerEntity player, Suggest[] suggests, int perLine) {
        if (player == null || player.getServer() == null || suggests == null || suggests.length == 0) return;
        StringBuilder extra = new StringBuilder();
        int count = 0;
        for (Suggest s : suggests) {
            if (s == null) continue;
            if (count > 0) {
                extra.append(',').append("{\"text\":\"  \",\"color\":\"dark_gray\"}");
            }
            extra.append(',').append(componentJson(s));
            count++;
            if (perLine > 0 && count == perLine) {
                String json = wrapExtras(extra.substring(1));
                executeTellraw(player, json);
                extra.setLength(0);
                count = 0;
            }
        }
        if (extra.length() > 0) {
            String json = wrapExtras(extra.substring(1));
            executeTellraw(player, json);
        }
    }

    private static String componentJson(Suggest s) {
        String label = escape(s.label());
        String command = escape(s.command());
        String hover = escape(s.hover() == null ? "" : s.hover());
        String color = s.color() == null ? "aqua" : s.color();
        String bold = s.bold() ? ",\"bold\":true" : "";
        return "{\"text\":\"" + label + "\",\"color\":\"" + color + "\"" + bold
                + ",\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"" + command + "\"}"
                + ",\"hoverEvent\":{\"action\":\"show_text\",\"contents\":{\"text\":\"" + hover + "\",\"color\":\"gray\",\"italic\":true}}}";
    }

    private static String wrapExtras(String extras) {
        return "{\"text\":\"\",\"extra\":[" + extras + "]}";
    }

    private static void executeTellraw(ServerPlayerEntity player, String json) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerCommandSource source = server.getCommandSource();
        String target = player.getName().getString();
        String cmd = "tellraw " + target + " " + json;
        server.getCommandManager().executeWithPrefix(source, cmd);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
