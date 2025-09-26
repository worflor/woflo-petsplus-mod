package woflo.petsplus.mixin;

import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.events.PlayerStateTracker;
import woflo.petsplus.interaction.OwnerAbilitySignalTracker;
import woflo.petsplus.events.SleepEventHandler;
import woflo.petsplus.mechanics.StargazeMechanic;
import woflo.petsplus.ui.ActionBarCueManager;
import woflo.petsplus.ui.BossBarManager;
import woflo.petsplus.ui.PetInspectionManager;
import woflo.petsplus.effects.MagnetizeDropsAndXpEffect;
import woflo.petsplus.roles.eclipsed.EclipsedCore;
import woflo.petsplus.roles.skyrider.SkyriderCore;
import woflo.petsplus.roles.enchantmentbound.EnchantmentBoundCore;
import woflo.petsplus.roles.enchantmentbound.EnchantmentBoundHandler;

/**
 * Handles disconnect cleanup for systems that previously relied on world tick sweeps.
 */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin {

    @Inject(method = "disconnect(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void petsplus$onDisconnect(Text reason, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayNetworkHandler handler) {
            ServerPlayerEntity player = handler.player;
            EmotionContextCues.onPlayerDisconnect(player);
            PlayerStateTracker.handlePlayerDisconnect(player);
            OwnerAbilitySignalTracker.handlePlayerDisconnect(player);
            SleepEventHandler.onPlayerDisconnect(player);
            StargazeMechanic.handlePlayerDisconnect(player);
            ActionBarCueManager.onPlayerDisconnect(player);
            PetInspectionManager.onPlayerDisconnect(player);
            BossBarManager.onPlayerDisconnect(player);
            MagnetizeDropsAndXpEffect.onPlayerDisconnect(player);
            EclipsedCore.handlePlayerDisconnect(player);
            SkyriderCore.handlePlayerDisconnect(player);
            EnchantmentBoundCore.handlePlayerDisconnect(player);
            EnchantmentBoundHandler.handlePlayerDisconnect(player);
        }
    }
}
