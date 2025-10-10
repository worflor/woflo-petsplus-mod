package woflo.petsplus.mixin;

import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.EmotionsEventHandler;

/**
 * Mirrors potency changes on arcane block entities into the shared ambient cache
 * so scans refresh when block entity data (e.g., beacon level) mutates without
 * altering the block state itself.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMarkDirtyMixin {

    @Inject(method = "markDirty", at = @At("TAIL"))
    private void petsplus$invalidateArcaneAmbientOnMarkDirty(CallbackInfo ci) {
        EmotionsEventHandler.invalidateArcaneAmbientForBlockEntity((BlockEntity) (Object) this);
    }
}
