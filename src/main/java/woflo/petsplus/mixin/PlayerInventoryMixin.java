package woflo.petsplus.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import woflo.petsplus.events.EmotionsEventHandler;

/**
 * Emits inventory change notifications whenever the player's inventory is
 * mutated, allowing the emotion system to maintain a cached signature without
 * polling.
 */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {

    @Shadow
    @Final
    public PlayerEntity player;

    @Inject(method = "markDirty", at = @At("TAIL"))
    private void petsplus$onMarkDirty(CallbackInfo ci) {
        this.petsplus$notifyInventoryChange();
    }

    @Inject(method = "setStack", at = @At("TAIL"))
    private void petsplus$onSetStack(int slot, ItemStack stack, CallbackInfo ci) {
        this.petsplus$notifyInventoryChange();
    }

    @Inject(method = "removeStack(II)Lnet/minecraft/item/ItemStack;", at = @At("TAIL"))
    private void petsplus$onRemoveStack(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        this.petsplus$notifyInventoryChange();
    }

    private void petsplus$notifyInventoryChange() {
        if (this.player instanceof ServerPlayerEntity serverPlayer) {
            EmotionsEventHandler.handleInventoryMutated(serverPlayer);
        }
    }
}
