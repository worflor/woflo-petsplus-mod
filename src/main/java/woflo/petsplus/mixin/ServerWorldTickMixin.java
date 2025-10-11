package woflo.petsplus.mixin;

import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.state.StateManager;

import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void petsplus$runPetScheduler(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        StateManager manager = StateManager.forWorld(world);
        manager.handleWorldPerceptionTick();
        manager.processScheduledPetTasks(world.getTime());
    }
}
