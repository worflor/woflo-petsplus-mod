package woflo.petsplus.mixin;

import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.ai.group.GroupTuning;
import woflo.petsplus.ai.group.GroupCoordinator;
import woflo.petsplus.state.processing.AsyncProcessingTelemetry;

import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldTickMixin {

    // Phase A (Chunk 5): Minimal ingress timing sample state (no per-tick allocations)
    private static int ppTelemetryTick = 0;
    private static boolean ppTelemetrySampled = false;
    private static long ppIngressStartNanos = 0L;

    @Inject(method = "tick", at = @At("HEAD"))
    private void petsplus$telemetryIngressHead(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        if (!AsyncProcessingTelemetry.isEnabled()) { ppTelemetrySampled = false; return; }
        int tick = ++ppTelemetryTick;
        boolean sampled = AsyncProcessingTelemetry.shouldSampleByTick(tick);
        if (!sampled) { ppTelemetrySampled = false; return; }
        ppTelemetrySampled = true;
        ppIngressStartNanos = AsyncProcessingTelemetry.startTimer();
    }
    
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

        // Phase A (Chunk 5): Minimal ingress timing sample - TAIL
        if (ppTelemetrySampled) {
            AsyncProcessingTelemetry.stopTimer(AsyncProcessingTelemetry.INGRESS_TIME, ppIngressStartNanos);
            AsyncProcessingTelemetry.INGRESS_EVENTS.incrementAndGet();
            ppTelemetrySampled = false;
            ppIngressStartNanos = 0L;
        }
    }
}
