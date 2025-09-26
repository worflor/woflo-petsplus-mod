package woflo.petsplus.mixin;

import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.EmotionsEventHandler;
import woflo.petsplus.mixin.support.HungerManagerAccess;

/**
 * Emits hunger change notifications whenever the food level mutates.
 */
@Mixin(HungerManager.class)
public abstract class HungerManagerMixin implements HungerManagerAccess {

    @Shadow
    private int foodLevel;

    @Unique
    private PlayerEntity petsplus$owner;

    @Unique
    private int petsplus$previousFoodLevel;

    @Override
    public void petsplus$setOwner(PlayerEntity player) {
        this.petsplus$owner = player;
    }

    @Override
    public PlayerEntity petsplus$getOwner() {
        return this.petsplus$owner;
    }

    @Inject(method = "add", at = @At("HEAD"))
    private void petsplus$captureAdd(int food, float saturationModifier, CallbackInfo ci) {
        this.petsplus$previousFoodLevel = this.foodLevel;
    }

    @Inject(method = "add", at = @At("TAIL"))
    private void petsplus$onAdd(int food, float saturationModifier, CallbackInfo ci) {
        this.petsplus$fireHungerChange();
    }

    @Inject(method = "eat", at = @At("HEAD"))
    private void petsplus$captureEat(net.minecraft.item.Item item, net.minecraft.item.ItemStack stack, CallbackInfo ci) {
        this.petsplus$previousFoodLevel = this.foodLevel;
    }

    @Inject(method = "eat", at = @At("TAIL"))
    private void petsplus$onEat(net.minecraft.item.Item item, net.minecraft.item.ItemStack stack, CallbackInfo ci) {
        this.petsplus$fireHungerChange();
    }

    @Inject(method = "setFoodLevel", at = @At("HEAD"))
    private void petsplus$captureSetFoodLevel(int foodLevel, CallbackInfo ci) {
        this.petsplus$previousFoodLevel = this.foodLevel;
    }

    @Inject(method = "setFoodLevel", at = @At("TAIL"))
    private void petsplus$onSetFoodLevel(int foodLevel, CallbackInfo ci) {
        this.petsplus$fireHungerChange();
    }

    private void petsplus$fireHungerChange() {
        if (!(this.petsplus$owner instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (this.foodLevel != this.petsplus$previousFoodLevel) {
            EmotionsEventHandler.handleHungerLevelChanged(serverPlayer, this.petsplus$previousFoodLevel, this.foodLevel);
        }
    }
}
