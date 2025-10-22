package woflo.petsplus.mood;

import woflo.petsplus.config.DebugSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.PetMoodEngine;
import woflo.petsplus.state.processing.AsyncProcessingTelemetry;
import woflo.petsplus.state.processing.AsyncWorkCoordinator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;

/**
 * Central dispatcher that batches emotion stimuli per pet and commits them on the
 * server thread with minimal allocation overhead.
 */
public final class EmotionStimulusBus {

    private static final PetComponent.Emotion[] EMOTIONS = PetComponent.Emotion.values();

    private final MoodService moodService;
    private final Object lock = new Object();
    private final Map<MobEntity, StimulusWork> pendingStimuli = new IdentityHashMap<>();
    private final ArrayDeque<StimulusWork> workPool = new ArrayDeque<>();
    private final Map<MobEntity, IdleDrainHandle> idleTasks = new IdentityHashMap<>();
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

    // Coalescing policy configuration

    /** Coalescing window policy (in ticks). */
    private static final int COALESCE_WINDOW_TICKS = 8;

    /** Returns the configured coalescing window length. */
    public static int coalesceWindowTicks() {
        return COALESCE_WINDOW_TICKS;
    }

    /** Placeholder hook for coalescing policies. Currently never coalesces. */
    private static final Object COALESCE_LOCK = new Object();
    private static final Map<Long, Map<Integer, Integer>> COALESCE_WINDOWS = new HashMap<>();

    public static boolean shouldCoalesce(long petId, int stimulusKey, int nowTick) {
        if (COALESCE_WINDOW_TICKS <= 0 || stimulusKey == 0) {
            return false;
        }
        synchronized (COALESCE_LOCK) {
            Map<Integer, Integer> window = COALESCE_WINDOWS.computeIfAbsent(petId, ignored -> new HashMap<>());
            Integer previousTick = window.get(stimulusKey);
            if (previousTick != null && nowTick - previousTick <= COALESCE_WINDOW_TICKS) {
                AsyncProcessingTelemetry.STIMULI_COALESCED.incrementAndGet();
                recordCoalesce(petId, stimulusKey, previousTick, nowTick, 0f);
                return true;
            }
            window.put(stimulusKey, nowTick);
            int expiryThreshold = COALESCE_WINDOW_TICKS * 2;
            window.entrySet().removeIf(entry -> nowTick - entry.getValue() > expiryThreshold);
            if (window.isEmpty()) {
                COALESCE_WINDOWS.remove(petId);
            }
            return false;
        }
    }

    private static int keyHash(@Nullable Identifier key) {
        return key != null ? key.hashCode() : 0;
    }

    // Optional tracing of the last coalesce decision

    private static volatile boolean COALESCE_TRACE_ENABLED = false;

    public static boolean isCoalesceTraceEnabled() {
        return COALESCE_TRACE_ENABLED;
    }

    public static void enableCoalesceTrace() {
        COALESCE_TRACE_ENABLED = true;
    }

    public static void disableCoalesceTrace() {
        COALESCE_TRACE_ENABLED = false;
        lastCoalesceWallNanos = 0L;
    }

    // Trace fields (primitive-only; volatile; updated only if enabled AND debug)
    private static volatile long lastCoalescePetId = 0L;
    private static volatile int lastCoalesceStimulusKey = 0;
    private static volatile int lastCoalesceStartTick = 0;
    private static volatile int lastCoalesceEndTick = 0;
    private static volatile float lastCoalesceMagnitude = 0f;
    private static volatile long lastCoalesceWallNanos = 0L;

    /**
     * Records a snapshot of the most recent coalesce decision for inspector use.
     * Inert unless both {@link DebugSettings#isDebugEnabled()} and {@link #isCoalesceTraceEnabled()} are true.
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

    /** Returns a compact summary of the last recorded coalesce snapshot. */
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

    // Dispatch listener API
    public interface DispatchListener {
        void onDispatch(net.minecraft.entity.mob.MobEntity pet,
                        woflo.petsplus.state.PetComponent component,
                        long time);
    }

    public void addDispatchListener(DispatchListener listener) {
        addDispatchListenerInternal(listener);
    }

    public void removeDispatchListener(DispatchListener listener) {
        removeDispatchListenerInternal(listener);
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
        queueSimpleStimulus(pet, null, collectorConsumer);
    }

    public void queueSimpleStimulus(net.minecraft.entity.mob.MobEntity pet,
                                    @Nullable Identifier coalesceKey,
                                    java.util.function.Consumer<woflo.petsplus.mood.EmotionStimulusBus.SimpleStimulusCollector> collectorConsumer) {
        if (pet == null || collectorConsumer == null) {
            return;
        }
        ServerWorld world = pet.getEntityWorld() instanceof ServerWorld sw ? sw : null;
        int tick = world != null ? (int) world.getTime() : 0;
        long petId = pet.getUuid().getLeastSignificantBits();
        int stimulusKey = keyHash(coalesceKey);
        boolean coalesced = shouldCoalesce(petId, stimulusKey, tick);
        StimulusWork work = getOrCreateWork(pet);
        PetComponent component = work.ensureComponent(pet);
        collectorConsumer.accept(work);
        work.markQueuedTick(tick, coalesced);
        notifyQueued(world, pet, work, tick);
        scheduleIdle(world, pet, work, tick + 1L);
    }

    public void queueStimulus(net.minecraft.entity.mob.MobEntity pet, java.util.function.Consumer<woflo.petsplus.state.PetComponent> componentConsumer) {
        queueStimulus(pet, null, componentConsumer);
    }

    public void queueStimulus(net.minecraft.entity.mob.MobEntity pet,
                              @Nullable Identifier coalesceKey,
                              java.util.function.Consumer<woflo.petsplus.state.PetComponent> componentConsumer) {
        if (pet == null || componentConsumer == null) {
            return;
        }
        ServerWorld world = pet.getEntityWorld() instanceof ServerWorld sw ? sw : null;
        int tick = world != null ? (int) world.getTime() : 0;
        long petId = pet.getUuid().getLeastSignificantBits();
        int stimulusKey = keyHash(coalesceKey);
        boolean coalesced = shouldCoalesce(petId, stimulusKey, tick);
        StimulusWork work = getOrCreateWork(pet);
        work.addComponentConsumer(componentConsumer);
        work.ensureComponent(pet);
        work.markQueuedTick(tick, coalesced);
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
        List<IdleDrainHandle> handles;
        synchronized (lock) {
            if (idleTasks.isEmpty()) {
                return;
            }
            handles = new ArrayList<>(idleTasks.values());
            idleTasks.clear();
        }
        for (IdleDrainHandle handle : handles) {
            if (handle != null) {
                handle.cancel();
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
            try {
                executor.awaitTermination(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
        if (work == null || work.isIdleScheduled()) {
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
            registerIdleTask(pet, tick, future);
        }
        work.markIdleScheduled();
    }

    private ScheduledExecutorService ensureIdleExecutor() {
        synchronized (lock) {
            if (idleExecutor instanceof ScheduledThreadPoolExecutor executor && !executor.isShutdown()) {
                return executor;
            }
            ThreadFactory factory = Thread.ofVirtual().name("PetsPlus-MoodIdle-", 1).factory();
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, factory);
            executor.setRemoveOnCancelPolicy(true);
            executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            idleExecutor = executor;
            return executor;
        }
    }

    private void registerIdleTask(MobEntity pet, long tick, ScheduledFuture<?> future) {
        synchronized (lock) {
            IdleDrainHandle existing = idleTasks.get(pet);
            if (existing != null && existing.scheduledTick() <= tick && !existing.future().isDone()) {
                future.cancel(false);
                return;
            }
            IdleDrainHandle prior = idleTasks.put(pet, new IdleDrainHandle(tick, future));
            if (prior != null) {
                prior.cancel();
            }
        }
    }

    private void clearIdleTask(MobEntity pet) {
        synchronized (lock) {
            IdleDrainHandle prior = idleTasks.remove(pet);
            if (prior != null) {
                prior.cancel();
            }
        }
    }

    private void drainStimuli(MobEntity pet) {
        if (pet == null) {
            return;
        }
        long coalesceKey = pet.getUuid().getLeastSignificantBits();
        StimulusWork work;
        synchronized (lock) {
            work = pendingStimuli.get(pet);
            if (work == null) {
                clearIdleTask(pet);
                clearCoalesceWindow(coalesceKey);
                return;
            }
            work.clearIdleScheduledFlag();
        }

        PetComponent component = work.ensureComponent(pet);
        if (component == null) {
            synchronized (lock) {
                StimulusWork removed = pendingStimuli.remove(pet);
                if (removed != null && removed != work) {
                    removed.reset();
                    recycleWork(removed);
                }
            }
            clearIdleTask(pet);
            clearCoalesceWindow(coalesceKey);
            work.reset();
            recycleWork(work);
            return;
        }

        ServerWorld world = component.getPet() != null && component.getPet().getEntityWorld() instanceof ServerWorld sw
            ? sw
            : pet.getEntityWorld() instanceof ServerWorld swPet ? swPet : null;
        long time = world != null ? world.getTime() : 0L;

        if (shouldDeferDrain(component, work, time)) {
            long rescheduleTick = time >= Long.MAX_VALUE - 1L ? Long.MAX_VALUE : time + 1L;
            scheduleIdle(world, pet, work, rescheduleTick);
            return;
        }

        synchronized (lock) {
            StimulusWork removed = pendingStimuli.remove(pet);
            if (removed != null && removed != work) {
                work.mergeFrom(removed);
                removed.reset();
                recycleWork(removed);
            }
        }

        try {
            clearIdleTask(pet);
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
            clearCoalesceWindow(coalesceKey);
            work.reset();
            recycleWork(work);
        }
    }

    private boolean shouldDeferDrain(PetComponent component, StimulusWork work, long time) {
        int currentTick;
        if (time <= 0L) {
            currentTick = 0;
        } else if (time >= Integer.MAX_VALUE) {
            currentTick = Integer.MAX_VALUE;
        } else {
            currentTick = (int) time;
        }
        if (work.shouldDefer(currentTick)) {
            return true;
        }
        PetMoodEngine engine = component.getMoodEngine();
        if (engine != null && engine.shouldDeferStimulusDrain()) {
            long nextTickLong = (long) currentTick + 1L;
            int minTick = nextTickLong >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) nextTickLong;
            work.extendDeferWindow(minTick);
            return true;
        }
        return false;
    }

    private static void clearCoalesceWindow(long petId) {
        synchronized (COALESCE_LOCK) {
            COALESCE_WINDOWS.remove(petId);
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

    private static final class IdleDrainHandle {
        private final long scheduledTick;
        private final ScheduledFuture<?> future;

        private IdleDrainHandle(long scheduledTick, ScheduledFuture<?> future) {
            this.scheduledTick = scheduledTick;
            this.future = Objects.requireNonNull(future, "future");
        }

        private long scheduledTick() {
            return scheduledTick;
        }

        private ScheduledFuture<?> future() {
            return future;
        }

        private void cancel() {
            if (!future.isDone()) {
                future.cancel(false);
            }
        }
    }

    private static final class StimulusWork implements SimpleStimulusCollector {
        private final float[] emotionDeltas = new float[EMOTIONS.length];
        private final ArrayDeque<Consumer<PetComponent>> componentConsumers = new ArrayDeque<>();
        private PetComponent component;
        private boolean collectorUsed;
        private boolean queueNotified;
        private boolean idleScheduled;
        private int lastQueuedTick = Integer.MIN_VALUE;
        private int deferUntilTick = Integer.MIN_VALUE;
        private int firstCoalesceTick = Integer.MIN_VALUE;

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
            lastQueuedTick = Integer.MIN_VALUE;
            deferUntilTick = Integer.MIN_VALUE;
            firstCoalesceTick = Integer.MIN_VALUE;
            if (!componentConsumers.isEmpty()) {
                componentConsumers.clear();
            }
            for (int i = 0; i < EMOTIONS.length; i++) {
                emotionDeltas[i] = 0.0f;
            }
        }

        void markQueuedTick(int tick, boolean coalesced) {
            lastQueuedTick = tick;
            if (coalesced) {
                if (firstCoalesceTick == Integer.MIN_VALUE) {
                    firstCoalesceTick = tick;
                }
                int window = Math.max(1, COALESCE_WINDOW_TICKS);
                long flushAt = (long) firstCoalesceTick + window;
                int windowEnd = flushAt >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) flushAt;
                if (windowEnd > deferUntilTick) {
                    deferUntilTick = windowEnd;
                }
            } else {
                firstCoalesceTick = Integer.MIN_VALUE;
            }
        }

        boolean shouldDefer(int currentTick) {
            if (firstCoalesceTick != Integer.MIN_VALUE) {
                int window = Math.max(1, COALESCE_WINDOW_TICKS);
                long earliestFlush = (long) firstCoalesceTick + window;
                int minimumTick = earliestFlush >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) earliestFlush;
                if (currentTick < minimumTick) {
                    return true;
                }
            }
            return currentTick < deferUntilTick;
        }

        void extendDeferWindow(int minTick) {
            if (minTick < 0) {
                deferUntilTick = Integer.MAX_VALUE;
            } else {
                deferUntilTick = Math.max(deferUntilTick, minTick);
            }
        }

        void clearIdleScheduledFlag() {
            idleScheduled = false;
        }

        boolean isIdleScheduled() {
            return idleScheduled;
        }

        void markIdleScheduled() {
            idleScheduled = true;
        }

        void mergeFrom(StimulusWork other) {
            if (other == null || other == this) {
                return;
            }
            for (int i = 0; i < emotionDeltas.length && i < other.emotionDeltas.length; i++) {
                emotionDeltas[i] += other.emotionDeltas[i];
                other.emotionDeltas[i] = 0.0f;
            }
            while (!other.componentConsumers.isEmpty()) {
                Consumer<PetComponent> consumer = other.componentConsumers.pollFirst();
                if (consumer != null) {
                    componentConsumers.addLast(consumer);
                }
            }
            collectorUsed |= other.collectorUsed;
            queueNotified |= other.queueNotified;
            if (other.component != null) {
                component = other.component;
            }
            if (other.deferUntilTick > deferUntilTick) {
                deferUntilTick = other.deferUntilTick;
            }
            if (other.firstCoalesceTick != Integer.MIN_VALUE) {
                if (firstCoalesceTick == Integer.MIN_VALUE || other.firstCoalesceTick < firstCoalesceTick) {
                    firstCoalesceTick = other.firstCoalesceTick;
                }
            }
        }
    }
}
