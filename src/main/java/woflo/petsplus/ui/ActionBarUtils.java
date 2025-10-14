package woflo.petsplus.ui;

import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Utility helpers for sending action bar overlays that behave consistently across versions.
 */
public final class ActionBarUtils {

    private ActionBarUtils() {}

    public static void sendActionBar(ServerPlayerEntity player, Text message) {
        if (player == null || message == null) {
            return;
        }

        try {
            player.networkHandler.sendPacket(new OverlayMessageS2CPacket(message));
            return;
        } catch (Exception overlayFailure) {
            // Try vanilla action bar delivery before falling back to chat
        }

        try {
            player.sendMessage(message, true);
            return;
        } catch (Exception vanillaFailure) {
            // Continue to chat fallback
        }

        try {
            player.sendMessage(message, false); // Fallback to chat if overlay delivery fails
        } catch (Exception ignored) {
            // Final fallback is to drop the message to avoid cascading failures
        }
    }
}
