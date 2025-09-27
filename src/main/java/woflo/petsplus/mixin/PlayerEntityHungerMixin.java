package woflo.petsplus.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.api.HungerManagerAccess;

/**
 * Associates each player's {@link HungerManager} with its owning entity so that
 * hunger change callbacks can run without polling.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityHungerMixin {

    @Shadow
    public abstract HungerManager getHungerManager();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void petsplus$linkHungerManager(World world, GameProfile profile, CallbackInfo ci) {
        HungerManager hungerManager = this.getHungerManager();
        if (hungerManager instanceof HungerManagerAccess access) {
            access.petsplus$setOwner((PlayerEntity) (Object) this);
        }
    }
}
