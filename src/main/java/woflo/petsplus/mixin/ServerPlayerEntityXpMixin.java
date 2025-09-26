package woflo.petsplus.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.XpEventHandler;

/**
 * Captures XP gains directly from the player entity so we can notify the XP
 * handler without performing world-wide polling.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityXpMixin {

    @Unique
    private int petsplus$xpBeforeChange;

    @Inject(method = "addExperience", at = @At("HEAD"))
    private void petsplus$captureBeforeAdd(int experience, CallbackInfo ci) {
        petsplus$xpBeforeChange = ((ServerPlayerEntity) (Object) this).totalExperience;
    }

    @Inject(method = "addExperience", at = @At("TAIL"))
    private void petsplus$afterAdd(int experience, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        int gained = player.totalExperience - petsplus$xpBeforeChange;
        if (gained > 0) {
            XpEventHandler.onExperienceGained(player, gained);
        }
    }

    @Inject(method = "addExperienceLevels", at = @At("HEAD"))
    private void petsplus$captureBeforeLevel(int levels, CallbackInfo ci) {
        petsplus$xpBeforeChange = ((ServerPlayerEntity) (Object) this).totalExperience;
    }

    @Inject(method = "addExperienceLevels", at = @At("TAIL"))
    private void petsplus$afterLevel(int levels, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        int gained = player.totalExperience - petsplus$xpBeforeChange;
        if (gained > 0) {
            XpEventHandler.onExperienceGained(player, gained);
        }
    }
}
