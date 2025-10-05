package woflo.petsplus.mixin;

import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.SleepEventHandler;
import woflo.petsplus.state.tracking.PlayerTickDispatcher;

/**
 * Handles disconnect cleanup for systems that previously relied on world tick sweeps.
 */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin {

    @Inject(method = "disconnect(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void petsplus$onDisconnect(Text reason, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayNetworkHandler handler) {
            ServerPlayerEntity player = handler.player;
            SleepEventHandler.onPlayerDisconnect(player);
            PlayerTickDispatcher.clearPlayer(player);
        }
    }
}
