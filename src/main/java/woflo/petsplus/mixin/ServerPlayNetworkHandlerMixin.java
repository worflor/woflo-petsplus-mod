package woflo.petsplus.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.EmotionsEventHandler;
import woflo.petsplus.mechanics.StargazeMechanic;

/**
 * Captures movement packets so the emotion system can track idle time without
 * scanning every player each tick.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At("TAIL"))
    private void petsplus$onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        EmotionsEventHandler.handlePlayerMovement(this.player);
        StargazeMechanic.handlePlayerMove(this.player);
    }
}
