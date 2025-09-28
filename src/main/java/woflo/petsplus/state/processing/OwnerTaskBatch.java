package woflo.petsplus.state.processing;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.server.network.ServerPlayerEntity;

import org.jetbrains.annotations.Nullable;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetWorkScheduler;

/**
 * Pooled snapshot describing the owner-scoped work that should execute for a
 * particular tick. The batch owns the task lists until {@link #close()} is
 * invoked, at which point they are recycled back into shared pools.
 */
public final class OwnerTaskBatch implements AutoCloseable {
    private static final int MAX_POOL_SIZE = 256;
    private static final ArrayDeque<OwnerTaskBatch> POOL = new ArrayDeque<>();

    private UUID ownerId;
    private final EnumMap<PetWorkScheduler.TaskType, List<PetWorkScheduler.ScheduledTask>> bucketViews =
        new EnumMap<>(PetWorkScheduler.TaskType.class);
    private final EnumMap<PetWorkScheduler.TaskType, List<PetWorkScheduler.ScheduledTask>> bucketBacking =
        new EnumMap<>(PetWorkScheduler.TaskType.class);
    private List<PetComponent> pets = List.of();
    private final EnumSet<OwnerEventType> dueEvents = EnumSet.noneOf(OwnerEventType.class);
    private final Set<OwnerEventType> dueEventsView = Collections.unmodifiableSet(dueEvents);
    private long snapshotTick;
    private @Nullable ServerPlayerEntity lastKnownOwner;
    private boolean released;

    private OwnerTaskBatch() {
    }

    public static OwnerTaskBatch obtain(@Nullable UUID ownerId,
                                        long snapshotTick,
                                        @Nullable ServerPlayerEntity lastKnownOwner) {
        OwnerTaskBatch batch;
        synchronized (POOL) {
            batch = POOL.pollFirst();
        }
        if (batch == null) {
            batch = new OwnerTaskBatch();
        }
        batch.ownerId = ownerId;
        batch.snapshotTick = snapshotTick;
        batch.lastKnownOwner = lastKnownOwner;
        batch.released = false;
        batch.pets = List.of();
        batch.dueEvents.clear();
        batch.bucketViews.clear();
        batch.bucketBacking.clear();
        return batch;
    }

    public static OwnerTaskBatch single(PetWorkScheduler.ScheduledTask task, long tick) {
        OwnerTaskBatch batch = obtain(null, tick, null);
        List<PetWorkScheduler.ScheduledTask> list = PooledTaskLists.borrow();
        list.add(task);
        batch.addBucket(task.type(), list);
        PetComponent component = task.component();
        batch.pets = component == null ? List.of() : List.of(component);
        return batch;
    }

    public static OwnerTaskBatch adHoc(@Nullable UUID ownerId,
                                       List<PetComponent> pets,
                                       EnumSet<OwnerEventType> dueEvents,
                                       long tick,
                                       @Nullable ServerPlayerEntity lastKnownOwner) {
        OwnerTaskBatch batch = obtain(ownerId, tick, lastKnownOwner);
        batch.attachPets(pets == null || pets.isEmpty() ? List.of() : List.copyOf(pets));
        if (dueEvents != null && !dueEvents.isEmpty()) {
            batch.attachDueEvents(EnumSet.copyOf(dueEvents));
        }
        return batch;
    }

    public void addBucket(PetWorkScheduler.TaskType type, List<PetWorkScheduler.ScheduledTask> tasks) {
        if (type == null || tasks == null || tasks.isEmpty()) {
            return;
        }
        bucketBacking.put(type, tasks);
        bucketViews.put(type, Collections.unmodifiableList(tasks));
    }

    public void attachPets(List<PetComponent> pets) {
        this.pets = pets == null ? List.of() : pets;
    }

    public void attachDueEvents(EnumSet<OwnerEventType> events) {
        dueEvents.clear();
        if (events != null && !events.isEmpty()) {
            dueEvents.addAll(events);
        }
    }

    public UUID ownerId() {
        return ownerId;
    }

    public List<PetComponent> pets() {
        return pets;
    }

    public EnumSet<OwnerEventType> dueEvents() {
        return dueEvents.isEmpty() ? EnumSet.noneOf(OwnerEventType.class) : EnumSet.copyOf(dueEvents);
    }

    public Set<OwnerEventType> dueEventsView() {
        return dueEvents.isEmpty() ? Set.of() : dueEventsView;
    }

    public long snapshotTick() {
        return snapshotTick;
    }

    @Nullable
    public ServerPlayerEntity lastKnownOwner() {
        return lastKnownOwner;
    }

    public boolean isEmpty() {
        if (bucketBacking.isEmpty()) {
            return true;
        }
        for (Map.Entry<PetWorkScheduler.TaskType, List<PetWorkScheduler.ScheduledTask>> entry : bucketBacking.entrySet()) {
            List<PetWorkScheduler.ScheduledTask> tasks = entry.getValue();
            if (tasks != null && !tasks.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void forEachTask(Consumer<PetWorkScheduler.ScheduledTask> consumer) {
        bucketViews.forEach((type, tasks) -> {
            if (tasks == null) {
                return;
            }
            for (PetWorkScheduler.ScheduledTask task : tasks) {
                consumer.accept(task);
            }
        });
    }

    public void forEachBucket(BiConsumer<PetWorkScheduler.TaskType, List<PetWorkScheduler.ScheduledTask>> consumer) {
        bucketViews.forEach((type, tasks) -> {
            if (tasks == null || tasks.isEmpty()) {
                return;
            }
            consumer.accept(type, tasks);
        });
    }

    public List<PetWorkScheduler.ScheduledTask> tasksFor(PetWorkScheduler.TaskType type) {
        if (type == null) {
            return List.of();
        }
        List<PetWorkScheduler.ScheduledTask> tasks = bucketViews.get(type);
        return tasks == null || tasks.isEmpty() ? List.of() : tasks;
    }

    @Override
    public void close() {
        if (released) {
            return;
        }
        released = true;
        for (List<PetWorkScheduler.ScheduledTask> tasks : bucketBacking.values()) {
            PooledTaskLists.recycle(tasks);
        }
        bucketBacking.clear();
        bucketViews.clear();
        pets = List.of();
        dueEvents.clear();
        ownerId = null;
        snapshotTick = 0L;
        lastKnownOwner = null;
        synchronized (POOL) {
            if (POOL.size() < MAX_POOL_SIZE) {
                POOL.addFirst(this);
            }
        }
    }
}
