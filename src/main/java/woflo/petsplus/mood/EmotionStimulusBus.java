package woflo.petsplus.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
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
    public interface DispatchListener {
        void onDispatch(MobEntity pet, PetComponent component, long time);
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

    private final MoodService service;
    private final Map<MobEntity, List<StimulusAction>> pending = new WeakHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> idleTasks = new ConcurrentHashMap<>();
    private final List<DispatchListener> dispatchListeners = new CopyOnWriteArrayList<>();

    public EmotionStimulusBus(MoodService service) {
        this.service = service;
    }

    public void queueStimulus(MobEntity pet, StimulusAction action) {
        Objects.requireNonNull(pet, "pet");
        if (!(pet.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        synchronized (pending) {
            pending.computeIfAbsent(pet, ignored -> new ArrayList<>()).add(action);
        }
        MinecraftServer server = serverWorld.getServer();
        CompletableFuture.runAsync(() -> dispatchStimuli(pet), server);
    }

    public void dispatchStimuli(MobEntity pet) {
        if (!(pet.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        List<StimulusAction> actions;
        synchronized (pending) {
            actions = pending.remove(pet);
        }
        if (actions == null || actions.isEmpty()) {
            return;
        }

        PetComponent component = PetComponent.getOrCreate(pet);
        long time = serverWorld.getTime();
        service.beginStimulusDispatch();
        try {
            for (StimulusAction action : actions) {
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
        scheduleIdleDrain(pet, component, serverWorld, time);
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

    private void scheduleIdleDrain(MobEntity pet, PetComponent component, ServerWorld world, long now) {
        UUID id = pet.getUuid();
        ScheduledFuture<?> existing = idleTasks.remove(id);
        if (existing != null) {
            existing.cancel(false);
        }
        long delayTicks = MathHelper.clamp(component.estimateNextEmotionUpdate(now), 20L, 400L);
        long delayMillis = delayTicks * 50L;
        ScheduledFuture<?> future = IDLE_EXECUTOR.schedule(() -> {
            MinecraftServer server = world.getServer();
            server.submit(() -> service.ensureFresh(pet, component, world.getTime()));
        }, delayMillis, TimeUnit.MILLISECONDS);
        idleTasks.put(id, future);
    }
}
