package woflo.petsplus.mood;

import woflo.petsplus.config.DebugSettings;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.processing.AsyncWorkCoordinator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Phase A scaffold for stimulus coalescing decisions and trace hooks.
 * <p>
 * No coalescing logic or state tables yet; designed for zero per-tick allocations on hot paths.
 * All methods are static and fields are primitive/volatile to avoid allocations and synchronization.
 * Non-goals: no gameplay wiring, no UI, no internal caches in this phase.
 */
public final class EmotionStimulusBus {

    private EmotionStimulusBus() {}

    // Required constructor used by MoodService
    public EmotionStimulusBus(woflo.petsplus.mood.MoodService service) {}

    // ---------------------------------------------------------------------
    // Coalescing policy and API (Phase A scaffold)
    // ---------------------------------------------------------------------

    /**
     * Documented coalescing window policy (in ticks).
     * Phase A scaffold; tweakable in later phases via policy.
     */
    private static final int COALESCE_WINDOW_TICKS = 4;

    /**
     * Phase A scaffold; no coalescing logic or state tables yet; designed for zero per-tick allocations.
     * Returns the documented coalescing window in ticks.
     *
     * @return number of ticks in the current coalescing window
     */
    public static int coalesceWindowTicks() {
        return COALESCE_WINDOW_TICKS;
    }

    /**
     * Phase A scaffold; no coalescing logic or state tables yet; designed for zero per-tick allocations.
     * Placeholder decision API for stimulus coalescing. Always returns false in Phase A.
     *
     * @param petId       stable pet identifier (primitive; no allocations)
     * @param stimulusKey stable stimulus key (primitive; no allocations)
     * @param nowTick     current tick (primitive; no allocations)
     * @return false in Phase A (no coalescing yet)
     */
    public static boolean shouldCoalesce(long petId, int stimulusKey, int nowTick) {
        // Phase A: behavior intentionally inert; replace with real decision logic in later phases.
        return false;
    }

    // ---------------------------------------------------------------------
    // Guardable “last coalesce” trace (inert unless enabled)
    // ---------------------------------------------------------------------

    /**
     * Phase A scaffold; no coalescing logic or state tables yet; designed for zero per-tick allocations.
     * Global toggle for coalesce trace. Guarded alongside DebugSettings.isDebugEnabled().
     */
    private static volatile boolean COALESCE_TRACE_ENABLED = false;

    /**
     * Phase A scaffold; no coalescing logic or state tables yet; designed for zero per-tick allocations.
     * Indicates whether coalesce trace is enabled. Allocation-free.
     *
     * @return true if coalesce trace is enabled and may be recorded
     */
    public static boolean isCoalesceTraceEnabled() {
        return COALESCE_TRACE_ENABLED;
    }

    /**
     * Phase A scaffold; no coalescing logic or state tables yet; designed for zero per-tick allocations.
     * Enables coalesce trace. Allocation-free; guarded in recordCoalesce().
     */
    public static void enableCoalesceTrace() {
        COALESCE_TRACE_ENABLED = true;
    }

    /**
     * Phase A scaffold; no coalescing logic or state tables yet; designed for zero per-tick allocations.
     * Disables coalesce trace. Allocation-free.
     */
    public static void disableCoalesceTrace() {
        COALESCE_TRACE_ENABLED = false;
    }

    // Trace fields (primitive-only; volatile; updated only if enabled AND debug)
    private static volatile long lastCoalescePetId = 0L;
    private static volatile int lastCoalesceStimulusKey = 0;
    private static volatile int lastCoalesceStartTick = 0;
    private static volatile int lastCoalesceEndTick = 0;
    private static volatile float lastCoalesceMagnitude = 0f;
    private static volatile long lastCoalesceWallNanos = 0L;

    /**
     * Phase A scaffold; no coalescing logic or state tables yet; designed for zero per-tick allocations.
     * Records a snapshot of the most recent coalesce decision for inspector use.
     * Inert unless both DebugSettings.isDebugEnabled() and COALESCE_TRACE_ENABLED are true.
     * Uses only primitive volatile fields to avoid allocations on hot paths.
     *
     * @param petId       stable pet identifier
     * @param stimulusKey stable stimulus key
     * @param startTick   start of the coalescing window (inclusive)
     * @param endTick     end of the coalescing window (exclusive or inclusive per future policy)
     * @param magnitude   resulting magnitude after coalescing
     */
    public static void recordCoalesce(long petId, int stimulusKey, int startTick, int endTick, float magnitude) {
        if (!(DebugSettings.isDebugEnabled() && COALESCE_TRACE_ENABLED)) {
            return;
        }
        lastCoalescePetId = petId;
        lastCoalesceStimulusKey = stimulusKey;
        lastCoalesceStartTick = startTick;
        lastCoalesceEndTick = endTick;
        lastCoalesceMagnitude = magnitude;
        lastCoalesceWallNanos = System.nanoTime();
    }

    /**
     * Phase A scaffold; no coalescing logic or state tables yet; designed for zero per-tick allocations.
     * Returns a compact summary of the last recorded coalesce snapshot.
     * Allocation occurs only upon caller request of this summary (not on per-tick hot paths).
     *
     * @return "trace=disabled" if tracing or debug is off, "trace=n/a" if no snapshot yet,
     *         otherwise compact summary string.
     */
    public static String getLastCoalesceSummary() {
        if (!(DebugSettings.isDebugEnabled() && COALESCE_TRACE_ENABLED)) {
            return "trace=disabled";
        }
        if (lastCoalesceWallNanos == 0L) {
            return "trace=n/a";
        }
        int window = lastCoalesceEndTick - lastCoalesceStartTick;
        return "petId=" + lastCoalescePetId
                + ", key=" + lastCoalesceStimulusKey
                + ", window=" + window + "t"
                + ", mag=" + lastCoalesceMagnitude;
    }

    // ---------------------------------------------------------------------
    // Phase A scaffold: Dispatch listener API (compile-only; no-ops)
    // ---------------------------------------------------------------------

    /**
     * Phase A scaffold; accepting dispatch callbacks without using them.
     * No behavior change; not referenced by existing logic yet.
     * This interface is allocation-free in hot paths; listeners are not stored in Phase A.
     */
    public interface DispatchListener {
        void onDispatch(net.minecraft.entity.mob.MobEntity pet,
                        woflo.petsplus.state.PetComponent component,
                        long time);
    }

    /**
     * Phase A scaffold; compile-only no-op. Accepts a listener but does not store it.
     * No behavior change at runtime.
     */
    public static void addDispatchListener(DispatchListener listener) {
        // no-op (Phase A scaffold)
    }

    /**
     * Phase A scaffold; compile-only no-op. Accepts a listener but does not remove anything.
     * No behavior change at runtime.
     */
    public static void removeDispatchListener(DispatchListener listener) {
        // no-op (Phase A scaffold)
    }
    
    // Nested listener invoked when a stimulus is queued for a pet.
    public interface QueueListener {
        void onStimulusQueued(ServerWorld world, MobEntity pet, long queuedTick);
    }
    
    // Nested listener invoked when an idle stimulus dispatch is scheduled for a pet.
    // Returns true if the scheduling was accepted.
    public interface IdleListener {
        boolean onStimulusIdleScheduled(ServerWorld world, MobEntity pet, long scheduledTick);
    }
    
    // Minimal collector for pushing emotion deltas during event handling.
    public interface SimpleStimulusCollector {
        void pushEmotion(PetComponent.Emotion emotion, float amount);
    }
    
    // ---------------------------------------------------------------------
    // Added Phase A compile-pass instance/static API stubs (no behavior)
    // ---------------------------------------------------------------------
    
    public void addQueueListener(EmotionStimulusBus.QueueListener listener) {}
    
    public void removeQueueListener(EmotionStimulusBus.QueueListener listener) {}
    
    public void addIdleListener(EmotionStimulusBus.IdleListener listener) {}
    
    public void removeIdleListener(EmotionStimulusBus.IdleListener listener) {}
    
    public void queueSimpleStimulus(net.minecraft.entity.mob.MobEntity pet, java.util.function.Consumer<woflo.petsplus.mood.EmotionStimulusBus.SimpleStimulusCollector> collectorConsumer) {}
    
    public void queueStimulus(net.minecraft.entity.mob.MobEntity pet, java.util.function.Consumer<woflo.petsplus.state.PetComponent> componentConsumer) {}
    
    public void dispatchStimuli(MobEntity pet) {}
    
    public void dispatchStimuli(MobEntity pet, AsyncWorkCoordinator coordinator) {}
    
    public CompletableFuture<Void> dispatchStimuliAsync(MobEntity pet, AsyncWorkCoordinator coordinator) { return CompletableFuture.completedFuture(null); }
    
    public void cancelPendingIdleTasks() {}
    
    public static void shutdownIdleExecutor() {}
}
