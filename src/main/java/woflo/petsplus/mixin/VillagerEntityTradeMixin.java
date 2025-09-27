package woflo.petsplus.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.EmotionsEventHandler;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityTradeMixin {
    @Inject(method = "afterUsing(Lnet/minecraft/village/TradeOffer;)V", at = @At("TAIL"))
    private void petsplus$shareCompletedTrade(TradeOffer offer, CallbackInfo ci) {
        VillagerEntity villager = (VillagerEntity) (Object) this;
        if (!(villager.getCustomer() instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        EmotionsEventHandler.onVillagerTradeCompleted(serverPlayer, villager, offer);
    }
}
