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
        } catch (Exception overlayFailure) {
            try {
                player.sendMessage(message, false); // Fallback to chat if overlay delivery fails
            } catch (Exception ignored) {
                // Final fallback is to drop the message to avoid cascading failures
            }
        }
    }
}
