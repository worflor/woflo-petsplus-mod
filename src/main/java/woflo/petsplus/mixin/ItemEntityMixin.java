package woflo.petsplus.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.roles.support.SupportPotionVacuumManager;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void petsplus$trackSupportPotions(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        World world = self.getWorld();
        if (world.isClient()) {
            return;
        }
        SupportPotionVacuumManager.getInstance().trackOrUpdate(self);
    }

    @Inject(method = "onPlayerCollision", at = @At("TAIL"))
    private void petsplus$onPotionPicked(PlayerEntity player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        World world = self.getWorld();
        if (world.isClient()) {
            return;
        }
        SupportPotionVacuumManager.getInstance().remove(self);
    }

}
