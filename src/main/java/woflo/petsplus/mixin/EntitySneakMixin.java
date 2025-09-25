package woflo.petsplus.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.interaction.OwnerAbilitySignalTracker;

/**
 * Hooks into sneaking state changes to detect manual trigger inputs without per-tick polling.
 */
@Mixin(Entity.class)
public abstract class EntitySneakMixin {
    @Inject(method = "setSneaking", at = @At("TAIL"))
    private void petsplus$handleSneakToggle(boolean sneaking, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ServerPlayerEntity serverPlayer) {
            OwnerAbilitySignalTracker.handleSneakToggle(serverPlayer, sneaking);
        }
    }
}
