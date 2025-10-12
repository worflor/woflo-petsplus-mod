package woflo.petsplus.mixin;

import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.ai.group.GroupTuning;
import woflo.petsplus.ai.group.GroupCoordinator;

import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void petsplus$runPetScheduler(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (world == null) {
            return;
        }
        StateManager manager = StateManager.forWorld(world);
        if (manager == null) {
            return;
        }
        manager.handleWorldPerceptionTick();
        manager.processScheduledPetTasks(world.getTime());

        // Periodic cleanup of group invites; runs once per world (per tick hook) at interval
        if ((world.getTime() % GroupTuning.CLEANUP_PERIOD_TICKS) == 0L) {
            GroupCoordinator.cleanupExpiredInvites(world);
        }
    }
}
