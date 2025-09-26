package woflo.petsplus.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import woflo.petsplus.events.SleepEventHandler;

/**
 * Routes bed sleep/wake transitions into {@link SleepEventHandler} so the
 * handler no longer needs to poll player state each tick.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntitySleepMixin {

    @Inject(method = "sleep", at = @At("TAIL"))
    private void petsplus$onSleep(BlockPos pos, CallbackInfoReturnable<Either<PlayerEntity.SleepFailureReason, Unit>> cir) {
        SleepEventHandler.onSleepStart((ServerPlayerEntity) (Object) this);
    }

    @Inject(method = "wakeUp", at = @At("TAIL"), require = 0)
    private void petsplus$onWake(CallbackInfo ci) {
        SleepEventHandler.onSleepEnd((ServerPlayerEntity) (Object) this);
    }
}
