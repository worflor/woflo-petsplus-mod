package woflo.petsplus.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.processing.AsyncWorkCoordinator;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Central queue for emotion stimuli. Stimuli are enqueued per pet and flushed on
 * demand on the main server thread, allowing event-driven emotion updates
 * without relying on the world tick loop.
 */
public final class EmotionStimulusBus {

    @FunctionalInterface
    public interface StimulusAction extends Consumer<PetComponent> {
    }

    @FunctionalInterface
    public interface SimpleStimulusAction {
        void contribute(SimpleStimulusCollector collector);
    }

    public interface SimpleStimulusCollector {
        void pushEmotion(PetComponent.Emotion emotion, float amount);
    }

    @FunctionalInterface
    public interface DispatchListener {
        void onDispatch(MobEntity pet, PetComponent component, long time);
    }

    @FunctionalInterface
    public interface QueueListener {
        void onStimulusQueued(ServerWorld world, MobEntity pet, long time);
    }

    @FunctionalInterface
    public interface IdleListener {
        /**
         * Invoked when an idle emotion refresh is scheduled. Implementations may return {@code true}
         * to indicate they took ownership of the scheduling work, preventing the default executor
         * from running.
         */
        boolean onIdleStimulusScheduled(ServerWorld world, MobEntity pet, long scheduledTick);
    }

    private static final ScheduledExecutorService IDLE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        private int idx;

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "petsplus-emotion-idle-" + idx++);
            thread.setDaemon(true);
            return thread;
        }
    });

    private static final int[] IDLE_JITTER_CHOICES = {-10, -9, -8, 8, 9, 10};
    private static final long IDLE_JITTER_SALT = 0x2D63A86C0E1F4AC3L;

    private final MoodService service;
    private final Map<MobEntity, PendingStimuli> pending = new WeakHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> idleTasks = new ConcurrentHashMap<>();
    private final List<DispatchListener> dispatchListeners = new CopyOnWriteArrayList<>();
    private final List<QueueListener> queueListeners = new CopyOnWriteArrayList<>();
    private final List<IdleListener> idleListeners = new CopyOnWriteArrayList<>();

    public EmotionStimulusBus(MoodService service) {
        this.service = service;
    }

    public void queueStimulus(MobEntity pet, StimulusAction action) {
        Objects.requireNonNull(pet, "pet");
        if (!(pet.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        synchronized (pending) {
            pending.computeIfAbsent(pet, ignored -> new PendingStimuli()).synchronous.add(action);
        }
        MinecraftServer server = serverWorld.getServer();
        long worldTime = serverWorld.getTime();
        server.submit(() -> notifyQueued(serverWorld, pet, worldTime));
    }

    public void queueSimpleStimulus(MobEntity pet, SimpleStimulusAction action) {
        Objects.requireNonNull(pet, "pet");
        if (!(pet.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        synchronized (pending) {
            pending.computeIfAbsent(pet, ignored -> new PendingStimuli()).simple.add(action);
        }
        MinecraftServer server = serverWorld.getServer();
        long worldTime = serverWorld.getTime();
        server.submit(() -> notifyQueued(serverWorld, pet, worldTime));
    }

    public void dispatchStimuli(MobEntity pet) {
        dispatchStimuliAsync(pet, null);
    }

    public void dispatchStimuli(MobEntity pet, @Nullable AsyncWorkCoordinator coordinator) {
        dispatchStimuliAsync(pet, coordinator);
    }

    public CompletableFuture<Void> dispatchStimuliAsync(MobEntity pet) {
        return dispatchStimuliAsync(pet, null);
    }

    public CompletableFuture<Void> dispatchStimuliAsync(MobEntity pet,
                                                        @Nullable AsyncWorkCoordinator coordinator) {
        if (!(pet.getWorld() instanceof ServerWorld serverWorld)) {
            return CompletableFuture.completedFuture(null);
        }
        PendingStimuli stimuli;
        synchronized (pending) {
            stimuli = pending.remove(pet);
        }
        if (stimuli == null || stimuli.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        PetComponent component = PetComponent.getOrCreate(pet);
        long time = serverWorld.getTime();
        if (coordinator != null
            && stimuli.synchronous.isEmpty()
            && !stimuli.simple.isEmpty()) {
            return processSimpleStimuliAsync(pet, serverWorld, stimuli.simple, coordinator);
        }

        processStimuliSynchronously(pet, component, serverWorld, time, stimuli.simple, stimuli.synchronous);
        return CompletableFuture.completedFuture(null);
    }

    public void addDispatchListener(DispatchListener listener) {
        if (listener != null) {
            dispatchListeners.add(listener);
        }
    }

    public void removeDispatchListener(DispatchListener listener) {
        if (listener != null) {
            dispatchListeners.remove(listener);
        }
    }

    public void addQueueListener(QueueListener listener) {
        if (listener != null) {
            queueListeners.add(listener);
        }
    }

    public void removeQueueListener(QueueListener listener) {
        if (listener != null) {
            queueListeners.remove(listener);
        }
    }

    public void addIdleListener(IdleListener listener) {
        if (listener != null) {
            idleListeners.add(listener);
        }
    }

    public void removeIdleListener(IdleListener listener) {
        if (listener != null) {
            idleListeners.remove(listener);
        }
    }

    private void notifyQueued(ServerWorld world, MobEntity pet, long time) {
        if (queueListeners.isEmpty()) {
            dispatchStimuli(pet, null);
            return;
        }
        for (QueueListener listener : queueListeners) {
            try {
                listener.onStimulusQueued(world, pet, time);
            } catch (Exception ex) {
                Petsplus.LOGGER.error("Queued emotion stimulus listener failed for pet {}", pet.getUuid(), ex);
            }
        }
    }

    private void scheduleIdleDrain(MobEntity pet, PetComponent component, ServerWorld world, long now) {
        UUID id = pet.getUuid();
        ScheduledFuture<?> existing = idleTasks.remove(id);
        if (existing != null) {
            existing.cancel(false);
        }
        long delayTicks = MathHelper.clamp(component.estimateNextEmotionUpdate(now), 20L, 400L);
        // Deterministically select a small ±8–10 tick offset so each pet drifts on its own cadence.
        int jitterIndex = component.pickStableIndex(IDLE_JITTER_SALT, IDLE_JITTER_CHOICES.length);
        long jitter = IDLE_JITTER_CHOICES[jitterIndex];
        long adjustedTicks = MathHelper.clamp(delayTicks + jitter, 20L, 400L);
        long scheduledTick = now + adjustedTicks;
        if (notifyIdleScheduled(world, pet, scheduledTick)) {
            return;
        }
        long delayMillis = adjustedTicks * 50L;
        ScheduledFuture<?> future = IDLE_EXECUTOR.schedule(() -> {
            MinecraftServer server = world.getServer();
            server.submit(() -> service.ensureFresh(pet, component, world.getTime()));
        }, delayMillis, TimeUnit.MILLISECONDS);
        idleTasks.put(id, future);
    }

    private boolean notifyIdleScheduled(ServerWorld world, MobEntity pet, long scheduledTick) {
        if (idleListeners.isEmpty()) {
            return false;
        }
        boolean handled = false;
        for (IdleListener listener : idleListeners) {
            try {
                handled |= listener.onIdleStimulusScheduled(world, pet, scheduledTick);
            } catch (Exception ex) {
                Petsplus.LOGGER.error("Idle emotion listener failed for pet {}", pet.getUuid(), ex);
            }
        }
        return handled;
    }

    private void processStimuliSynchronously(MobEntity pet,
                                             PetComponent component,
                                             ServerWorld world,
                                             long time,
                                             List<SimpleStimulusAction> simpleActions,
                                             List<StimulusAction> synchronousActions) {
        service.beginStimulusDispatch();
        try {
            if (!simpleActions.isEmpty()) {
                SimpleStimulusCollectorImpl collector = new SimpleStimulusCollectorImpl();
                for (SimpleStimulusAction action : simpleActions) {
                    try {
                        action.contribute(collector);
                    } catch (Exception ex) {
                        Petsplus.LOGGER.error("Failed to collect simple emotion stimulus for pet {}", pet.getUuid(), ex);
                    }
                }
                for (Map.Entry<PetComponent.Emotion, Float> entry : collector.results().entrySet()) {
                    component.pushEmotion(entry.getKey(), entry.getValue());
                }
            }

            for (StimulusAction action : synchronousActions) {
                try {
                    action.accept(component);
                } catch (Exception ex) {
                    Petsplus.LOGGER.error("Failed to apply emotion stimulus for pet {}", pet.getUuid(), ex);
                }
            }

            for (DispatchListener listener : dispatchListeners) {
                try {
                    listener.onDispatch(pet, component, time);
                } catch (Exception ex) {
                    Petsplus.LOGGER.error("Reactive emotion provider failed for pet {}", pet.getUuid(), ex);
                }
            }
        } finally {
            service.endStimulusDispatch();
        }

        service.commitStimuli(pet, component, time);
        scheduleIdleDrain(pet, component, world, time);
    }

    private CompletableFuture<Void> processSimpleStimuliAsync(MobEntity pet,
                                                              ServerWorld world,
                                                              List<SimpleStimulusAction> actions,
                                                              AsyncWorkCoordinator coordinator) {
        if (actions.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        UUID petId = pet.getUuid();
        CompletableFuture<SimpleStimulusResult> future = coordinator.submitStandalone(
            "mood-stimulus-" + petId,
            () -> computeSimpleStimulusResult(actions),
            result -> applySimpleStimulusResult(pet, world, result)
        );
        return future.handle((result, throwable) -> {
            if (throwable != null) {
                Throwable cause = unwrap(throwable);
                if (cause instanceof RejectedExecutionException) {
                    Petsplus.LOGGER.debug("Async mood stimulus rejected for pet {}, running synchronously", petId);
                } else {
                    Petsplus.LOGGER.error("Async mood stimulus failed for pet {}", petId, cause);
                }
                PetComponent component = PetComponent.getOrCreate(pet);
                long time = world.getTime();
                processStimuliSynchronously(pet, component, world, time, actions, List.of());
            }
            return null;
        });
    }

    private SimpleStimulusResult computeSimpleStimulusResult(List<SimpleStimulusAction> actions) throws Exception {
        SimpleStimulusCollectorImpl collector = new SimpleStimulusCollectorImpl();
        for (SimpleStimulusAction action : actions) {
            action.contribute(collector);
        }
        return new SimpleStimulusResult(new EnumMap<>(collector.results()));
    }

    private void applySimpleStimulusResult(MobEntity pet,
                                           ServerWorld expectedWorld,
                                           SimpleStimulusResult result) {
        if (result == null || result.isEmpty()) {
            return;
        }
        if (!(pet.getWorld() instanceof ServerWorld world) || world != expectedWorld || pet.isRemoved()) {
            return;
        }

        PetComponent component = PetComponent.getOrCreate(pet);
        long time = world.getTime();
        service.beginStimulusDispatch();
        try {
            for (Map.Entry<PetComponent.Emotion, Float> entry : result.emotionDeltas().entrySet()) {
                component.pushEmotion(entry.getKey(), entry.getValue());
            }

            for (DispatchListener listener : dispatchListeners) {
                try {
                    listener.onDispatch(pet, component, time);
                } catch (Exception ex) {
                    Petsplus.LOGGER.error("Reactive emotion provider failed for pet {}", pet.getUuid(), ex);
                }
            }
        } finally {
            service.endStimulusDispatch();
        }

        service.commitStimuli(pet, component, time);
        scheduleIdleDrain(pet, component, world, time);
    }

    private static final class PendingStimuli {
        final List<StimulusAction> synchronous = new ArrayList<>();
        final List<SimpleStimulusAction> simple = new ArrayList<>();

        boolean isEmpty() {
            return synchronous.isEmpty() && simple.isEmpty();
        }
    }

    private static final class SimpleStimulusCollectorImpl implements SimpleStimulusCollector {
        private final EnumMap<PetComponent.Emotion, Float> totals = new EnumMap<>(PetComponent.Emotion.class);

        @Override
        public void pushEmotion(PetComponent.Emotion emotion, float amount) {
            if (emotion == null) {
                return;
            }
            if (Math.abs(amount) <= 0.0001f) {
                return;
            }
            totals.merge(emotion, amount, Float::sum);
        }

        EnumMap<PetComponent.Emotion, Float> results() {
            return totals;
        }
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException completion && completion.getCause() != null) {
            return completion.getCause();
        }
        return error;
    }

    private record SimpleStimulusResult(EnumMap<PetComponent.Emotion, Float> emotionDeltas) {
        boolean isEmpty() {
            return emotionDeltas == null || emotionDeltas.isEmpty();
        }
    }

}
