package woflo.petsplus.mood;

import woflo.petsplus.config.DebugSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.processing.AsyncWorkCoordinator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Phase A scaffold for stimulus coalescing decisions and trace hooks.
 * <p>
 * No coalescing logic or state tables yet; designed for zero per-tick allocations on hot paths.
 * All methods are static and fields are primitive/volatile to avoid allocations and synchronization.
 * Non-goals: no gameplay wiring, no UI, no internal caches in this phase.
 */
public final class EmotionStimulusBus {

    private static final PetComponent.Emotion[] EMOTIONS = PetComponent.Emotion.values();

    private final MoodService moodService;
    private final Object lock = new Object();
    private final Map<MobEntity, StimulusWork> pendingStimuli = new IdentityHashMap<>();
    private final ArrayDeque<StimulusWork> workPool = new ArrayDeque<>();
    private final Map<MobEntity, ScheduledFuture<?>> idleTasks = new IdentityHashMap<>();
    private final List<DispatchListener> dispatchListeners = new CopyOnWriteArrayList<>();
    private final List<QueueListener> queueListeners = new CopyOnWriteArrayList<>();
    private final List<IdleListener> idleListeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService idleExecutor;

    private EmotionStimulusBus() {
        this.moodService = MoodService.getInstance();
    }

    // Required constructor used by MoodService
    public EmotionStimulusBus(woflo.petsplus.mood.MoodService service) {
        this.moodService = service != null ? service : MoodService.getInstance();
    }

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
        MoodService.getInstance().getStimulusBus().addDispatchListenerInternal(listener);
    }

    /**
     * Phase A scaffold; compile-only no-op. Accepts a listener but does not remove anything.
     * No behavior change at runtime.
     */
    public static void removeDispatchListener(DispatchListener listener) {
        MoodService.getInstance().getStimulusBus().removeDispatchListenerInternal(listener);
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
    
    public void addQueueListener(EmotionStimulusBus.QueueListener listener) {
        if (listener == null) {
            return;
        }
        queueListeners.add(listener);
    }

    public void removeQueueListener(EmotionStimulusBus.QueueListener listener) {
        if (listener == null) {
            return;
        }
        queueListeners.remove(listener);
    }

    public void addIdleListener(EmotionStimulusBus.IdleListener listener) {
        if (listener == null) {
            return;
        }
        idleListeners.add(listener);
    }

    public void removeIdleListener(EmotionStimulusBus.IdleListener listener) {
        if (listener == null) {
            return;
        }
        idleListeners.remove(listener);
    }

    public void queueSimpleStimulus(net.minecraft.entity.mob.MobEntity pet, java.util.function.Consumer<woflo.petsplus.mood.EmotionStimulusBus.SimpleStimulusCollector> collectorConsumer) {
        if (pet == null || collectorConsumer == null) {
            return;
        }
        StimulusWork work = getOrCreateWork(pet);
        PetComponent component = work.ensureComponent(pet);
        collectorConsumer.accept(work);
        ServerWorld world = pet.getEntityWorld() instanceof ServerWorld sw ? sw : null;
        long tick = world != null ? world.getTime() : 0L;
        notifyQueued(world, pet, work, tick);
        scheduleIdle(world, pet, work, tick + 1L);
    }

    public void queueStimulus(net.minecraft.entity.mob.MobEntity pet, java.util.function.Consumer<woflo.petsplus.state.PetComponent> componentConsumer) {
        if (pet == null || componentConsumer == null) {
            return;
        }
        StimulusWork work = getOrCreateWork(pet);
        work.addComponentConsumer(componentConsumer);
        work.ensureComponent(pet);
        ServerWorld world = pet.getEntityWorld() instanceof ServerWorld sw ? sw : null;
        long tick = world != null ? world.getTime() : 0L;
        notifyQueued(world, pet, work, tick);
        scheduleIdle(world, pet, work, tick + 1L);
    }

    public void dispatchStimuli(MobEntity pet) {
        dispatchStimuli(pet, null);
    }

    public void dispatchStimuli(MobEntity pet, AsyncWorkCoordinator coordinator) {
        if (pet == null) {
            return;
        }
        drainStimuli(pet);
    }

    public CompletableFuture<Void> dispatchStimuliAsync(MobEntity pet, AsyncWorkCoordinator coordinator) {
        if (pet == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            drainStimuli(pet);
            completion.complete(null);
        } catch (Throwable throwable) {
            completion.completeExceptionally(throwable);
        }
        return completion;
    }

    public void cancelPendingIdleTasks() {
        List<ScheduledFuture<?>> futures;
        synchronized (lock) {
            if (idleTasks.isEmpty()) {
                return;
            }
            futures = new ArrayList<>(idleTasks.values());
            idleTasks.clear();
        }
        for (ScheduledFuture<?> future : futures) {
            if (future != null) {
                future.cancel(false);
            }
        }
    }

    public static void shutdownIdleExecutor() {
        EmotionStimulusBus bus = MoodService.getInstance().getStimulusBus();
        bus.shutdownIdleExecutorInternal();
    }

    private void shutdownIdleExecutorInternal() {
        cancelPendingIdleTasks();
        ScheduledExecutorService executor;
        synchronized (lock) {
            executor = this.idleExecutor;
            this.idleExecutor = null;
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void addDispatchListenerInternal(DispatchListener listener) {
        if (listener == null) {
            return;
        }
        dispatchListeners.add(listener);
    }

    private void removeDispatchListenerInternal(DispatchListener listener) {
        if (listener == null) {
            return;
        }
        dispatchListeners.remove(listener);
    }

    private StimulusWork getOrCreateWork(MobEntity pet) {
        synchronized (lock) {
            StimulusWork work = pendingStimuli.get(pet);
            if (work != null) {
                return work;
            }
            work = workPool.pollFirst();
            if (work == null) {
                work = new StimulusWork();
            }
            pendingStimuli.put(pet, work);
            return work;
        }
    }

    private void notifyQueued(ServerWorld world, MobEntity pet, StimulusWork work, long tick) {
        if (work == null || work.queueNotified) {
            return;
        }
        work.queueNotified = true;
        if (queueListeners.isEmpty()) {
            return;
        }
        for (QueueListener listener : queueListeners) {
            try {
                listener.onStimulusQueued(world, pet, tick);
            } catch (Throwable ignored) {
            }
        }
    }

    private void scheduleIdle(ServerWorld world, MobEntity pet, StimulusWork work, long tick) {
        if (work == null || work.idleScheduled) {
            return;
        }
        boolean accepted = false;
        if (!idleListeners.isEmpty()) {
            for (IdleListener listener : idleListeners) {
                try {
                    if (listener.onStimulusIdleScheduled(world, pet, tick)) {
                        accepted = true;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (!accepted) {
            ScheduledExecutorService executor = ensureIdleExecutor();
            ScheduledFuture<?> future = executor.schedule(() -> {
                MinecraftServer server = world != null ? world.getServer()
                    : pet.getEntityWorld() instanceof ServerWorld petWorld ? petWorld.getServer() : null;
                if (server != null) {
                    server.submit(() -> drainStimuli(pet));
                } else {
                    drainStimuli(pet);
                }
                clearIdleTask(pet);
            }, 50L, TimeUnit.MILLISECONDS);
            registerIdleTask(pet, future);
        }
        work.idleScheduled = true;
    }

    private ScheduledExecutorService ensureIdleExecutor() {
        synchronized (lock) {
            if (idleExecutor != null && !idleExecutor.isShutdown()) {
                return idleExecutor;
            }
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, "PetsPlus-MoodIdle");
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            };
            idleExecutor = Executors.newSingleThreadScheduledExecutor(factory);
            return idleExecutor;
        }
    }

    private void registerIdleTask(MobEntity pet, ScheduledFuture<?> future) {
        synchronized (lock) {
            ScheduledFuture<?> prior = idleTasks.put(pet, future);
            if (prior != null) {
                prior.cancel(false);
            }
        }
    }

    private void clearIdleTask(MobEntity pet) {
        synchronized (lock) {
            ScheduledFuture<?> prior = idleTasks.remove(pet);
            if (prior != null) {
                prior.cancel(false);
            }
        }
    }

    private void drainStimuli(MobEntity pet) {
        StimulusWork work;
        synchronized (lock) {
            work = pendingStimuli.remove(pet);
            if (work == null) {
                clearIdleTask(pet);
                return;
            }
        }

        try {
            clearIdleTask(pet);
            PetComponent component = work.ensureComponent(pet);
            if (component == null) {
                work.reset();
                recycleWork(work);
                return;
            }
            ServerWorld world = component.getPet() != null && component.getPet().getEntityWorld() instanceof ServerWorld sw
                ? sw
                : pet.getEntityWorld() instanceof ServerWorld swPet ? swPet : null;
            long time = world != null ? world.getTime() : 0L;

            moodService.beginStimulusDispatch();
            try {
                work.runComponentConsumers(component);
                work.flushCollector(component);
                if (!dispatchListeners.isEmpty()) {
                    for (DispatchListener listener : dispatchListeners) {
                        try {
                            listener.onDispatch(pet, component, time);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } finally {
                moodService.endStimulusDispatch();
            }

            moodService.commitStimuli(pet, component, time);
        } finally {
            work.reset();
            recycleWork(work);
        }
    }

    private void recycleWork(StimulusWork work) {
        if (work == null) {
            return;
        }
        synchronized (lock) {
            workPool.addLast(work);
        }
    }

    private static final class StimulusWork implements SimpleStimulusCollector {
        private final float[] emotionDeltas = new float[EMOTIONS.length];
        private final ArrayDeque<Consumer<PetComponent>> componentConsumers = new ArrayDeque<>();
        private PetComponent component;
        private boolean collectorUsed;
        private boolean queueNotified;
        private boolean idleScheduled;

        @Override
        public void pushEmotion(PetComponent.Emotion emotion, float amount) {
            if (emotion == null || amount == 0.0f) {
                return;
            }
            emotionDeltas[emotion.ordinal()] += amount;
            collectorUsed = true;
        }

        PetComponent ensureComponent(MobEntity pet) {
            if (component != null) {
                return component;
            }
            if (pet == null) {
                return null;
            }
            component = PetComponent.getOrCreate(pet);
            return component;
        }

        void addComponentConsumer(Consumer<PetComponent> consumer) {
            if (consumer == null) {
                return;
            }
            componentConsumers.addLast(consumer);
        }

        void runComponentConsumers(PetComponent component) {
            while (!componentConsumers.isEmpty()) {
                Consumer<PetComponent> consumer = componentConsumers.pollFirst();
                if (consumer == null) {
                    continue;
                }
                try {
                    consumer.accept(component);
                } catch (Throwable ignored) {
                }
            }
        }

        void flushCollector(PetComponent component) {
            if (!collectorUsed || component == null) {
                return;
            }
            for (int i = 0; i < EMOTIONS.length; i++) {
                float amount = emotionDeltas[i];
                if (amount == 0.0f) {
                    continue;
                }
                component.pushEmotion(EMOTIONS[i], amount);
                emotionDeltas[i] = 0.0f;
            }
            collectorUsed = false;
        }

        void reset() {
            component = null;
            collectorUsed = false;
            queueNotified = false;
            idleScheduled = false;
            if (!componentConsumers.isEmpty()) {
                componentConsumers.clear();
            }
            for (int i = 0; i < EMOTIONS.length; i++) {
                emotionDeltas[i] = 0.0f;
            }
        }
    }
}
