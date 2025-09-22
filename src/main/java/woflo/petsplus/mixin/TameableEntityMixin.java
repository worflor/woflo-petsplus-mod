package woflo.petsplus.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.PetDetectionHandler;

/**
 * Mixin to detect when entities are tamed and trigger pet registration.
 */
@Mixin(TameableEntity.class)
public class TameableEntityMixin {
    
    @Inject(method = "setOwner", at = @At("TAIL"))
    private void onSetOwner(LivingEntity owner, CallbackInfo ci) {
        TameableEntity tameable = (TameableEntity) (Object) this;
        
        // Only trigger if the entity is now tamed and has a player owner
        if (tameable.isTamed() && owner instanceof PlayerEntity player) {
            PetDetectionHandler.onEntityTamed(tameable, player);
        }
    }
}