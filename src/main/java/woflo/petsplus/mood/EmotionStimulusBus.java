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
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

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

    // Coalescing policy configuration

    /** Coalescing window policy (in ticks). */
    private static final int COALESCE_WINDOW_TICKS = 4;

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
                recordCoalesce(petId, stimulusKey, previousTick, nowTick, 0f);
                return true;
            }
            window.put(stimulusKey, nowTick);
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
        if (shouldCoalesce(petId, stimulusKey, tick)) {
            return;
        }
        StimulusWork work = getOrCreateWork(pet);
        PetComponent component = work.ensureComponent(pet);
        collectorConsumer.accept(work);
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
        if (shouldCoalesce(petId, stimulusKey, tick)) {
            return;
        }
        StimulusWork work = getOrCreateWork(pet);
        work.addComponentConsumer(componentConsumer);
        work.ensureComponent(pet);
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
