package woflo.petsplus.state.processing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetWorkScheduler;

/**
 * Represents the aggregation of all pet work associated with a single owner.
 * The group caches pet lookups, batches scheduled tasks, and keeps track of
 * owner-scoped events so that the dispatcher can process them in large chunks
 * rather than per-pet polling.
 */
final class OwnerProcessingGroup {
    private final UUID ownerId;
    private final Map<MobEntity, PetComponent> petLookup = new IdentityHashMap<>();
    private final EnumMap<PetWorkScheduler.TaskType, List<PetWorkScheduler.ScheduledTask>> pendingTasks =
        new EnumMap<>(PetWorkScheduler.TaskType.class);
    private final EnumMap<OwnerEventType, OwnerEventWindow> eventWindows =
        new EnumMap<>(OwnerEventType.class);
    private final EnumMap<OwnerEventType, Long> deferredEvents =
        new EnumMap<>(OwnerEventType.class);

    private final List<PetComponent> cachedPets = new ArrayList<>();
    private List<PetComponent> cachedPetsView = List.of();
    private boolean petCacheDirty = true;
    private long lastPetRefreshTick = Long.MIN_VALUE;
    private @Nullable ServerPlayerEntity lastKnownOwner;
    private long nextEventTick = Long.MAX_VALUE;
    private boolean pending;

    OwnerProcessingGroup(UUID ownerId) {
        this.ownerId = ownerId;
    }

    UUID ownerId() {
        return ownerId;
    }

    void track(PetComponent component) {
        MobEntity pet = component.getPetEntity();
        if (pet == null) {
            return;
        }
        petLookup.put(pet, component);
        petCacheDirty = true;
    }

    void untrack(PetComponent component) {
        MobEntity pet = component.getPetEntity();
        if (pet == null) {
            return;
        }
        if (petLookup.remove(pet) != null) {
            petCacheDirty = true;
        }
    }

    boolean isEmpty() {
        return petLookup.isEmpty() && pendingTasks.isEmpty() && !hasScheduledEvents();
    }

    Collection<PetComponent> currentPets() {
        return petLookup.values();
    }

    void clear() {
        petLookup.clear();
        cachedPets.clear();
        cachedPetsView = List.of();
        for (List<PetWorkScheduler.ScheduledTask> list : pendingTasks.values()) {
            PooledTaskLists.recycle(list);
        }
        pendingTasks.clear();
        eventWindows.clear();
        deferredEvents.clear();
        petCacheDirty = true;
        lastKnownOwner = null;
        lastPetRefreshTick = Long.MIN_VALUE;
        nextEventTick = Long.MAX_VALUE;
        pending = false;
    }

    void beginTick(long currentTick) {
        if (currentTick - lastPetRefreshTick > 40L) {
            petCacheDirty = true;
        }
    }

    void markPetChanged(PetComponent component, long currentTick) {
        track(component);
        lastPetRefreshTick = currentTick;
    }

    void onOwnerTick(ServerPlayerEntity owner, long currentTick) {
        this.lastKnownOwner = owner;
        beginTick(currentTick);
    }

    void onTaskScheduled(PetComponent component,
                         PetWorkScheduler.TaskType type,
                         long tick,
                         boolean eventEnabled) {
        OwnerEventType eventType = OwnerEventType.fromTaskType(type);
        if (eventType != null) {
            if (eventEnabled) {
                eventWindows.computeIfAbsent(eventType, ignored -> new OwnerEventWindow()).schedule(tick);
                deferredEvents.remove(eventType);
                recomputeNextEventTick();
            } else {
                deferEvent(eventType, tick);
            }
        }
        markPetChanged(component, tick);
    }

    void onTaskExecuted(PetComponent component,
                        PetWorkScheduler.TaskType type,
                        long tick,
                        boolean eventEnabled) {
        OwnerEventType eventType = OwnerEventType.fromTaskType(type);
        if (eventType != null) {
            if (eventEnabled) {
                OwnerEventWindow window = eventWindows.get(eventType);
                if (window != null) {
                    window.markRan(tick);
                }
                deferredEvents.remove(eventType);
            } else {
                deferEvent(eventType, tick);
            }
            recomputeNextEventTick();
        }
        markPetChanged(component, tick);
    }

    void onTasksCleared(PetComponent component) {
        for (OwnerEventWindow window : eventWindows.values()) {
            window.clear();
        }
        deferredEvents.clear();
        markPetChanged(component, lastPetRefreshTick);
        recomputeNextEventTick();
    }

    void enqueue(PetWorkScheduler.ScheduledTask task) {
        List<PetWorkScheduler.ScheduledTask> list = pendingTasks.get(task.type());
        if (list == null) {
            list = PooledTaskLists.borrow();
            pendingTasks.put(task.type(), list);
        }
        list.add(task);
    }

    boolean hasPendingTasks() {
        for (List<PetWorkScheduler.ScheduledTask> list : pendingTasks.values()) {
            if (list != null && !list.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    boolean hasDueEvents(long currentTick) {
        for (OwnerEventWindow window : eventWindows.values()) {
            if (window != null && window.isDue(currentTick)) {
                return true;
            }
        }
        return false;
    }

    OwnerTaskBatch drain(long currentTick) {
        OwnerTaskBatch batch = OwnerTaskBatch.obtain(ownerId, currentTick, lastKnownOwner);
        var iterator = pendingTasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PetWorkScheduler.TaskType, List<PetWorkScheduler.ScheduledTask>> entry = iterator.next();
            List<PetWorkScheduler.ScheduledTask> tasks = entry.getValue();
            if (tasks == null || tasks.isEmpty()) {
                if (tasks != null) {
                    PooledTaskLists.recycle(tasks);
                }
                iterator.remove();
                continue;
            }
            batch.addBucket(entry.getKey(), tasks);
            iterator.remove();
        }

        EnumSet<OwnerEventType> dueEvents = EnumSet.noneOf(OwnerEventType.class);
        for (Map.Entry<OwnerEventType, OwnerEventWindow> entry : eventWindows.entrySet()) {
            OwnerEventWindow window = entry.getValue();
            if (window != null && window.isDue(currentTick)) {
                dueEvents.add(entry.getKey());
            }
        }

        batch.attachDueEvents(dueEvents);
        batch.attachPets(snapshotPets());
        return batch;
    }

    private List<PetComponent> snapshotPets() {
        if (petCacheDirty) {
            cachedPets.clear();
            cachedPets.addAll(petLookup.values());
            cachedPetsView = cachedPets.isEmpty() ? List.of() : List.copyOf(cachedPets);
            petCacheDirty = false;
        }
        return cachedPetsView;
    }

    @Nullable
    ServerPlayerEntity lastKnownOwner() {
        return lastKnownOwner;
    }

    void signalEvent(OwnerEventType type, long tick, boolean eventEnabled) {
        if (type == null) {
            return;
        }
        if (eventEnabled) {
            OwnerEventWindow window = eventWindows.computeIfAbsent(type, ignored -> new OwnerEventWindow());
            window.schedule(tick);
            deferredEvents.remove(type);
            recomputeNextEventTick();
        } else {
            deferEvent(type, tick);
        }
    }

    void markEventExecuted(OwnerEventType type, long tick, boolean eventEnabled) {
        if (type == null) {
            return;
        }
        if (eventEnabled) {
            OwnerEventWindow window = eventWindows.get(type);
            if (window != null) {
                window.markRan(tick);
                if (!window.hasSchedule()) {
                    eventWindows.remove(type);
                }
            }
            deferredEvents.remove(type);
        } else {
            deferredEvents.remove(type);
        }
        recomputeNextEventTick();
    }

    OwnerTaskBatch snapshot(EnumSet<OwnerEventType> dueEvents, long currentTick) {
        OwnerTaskBatch batch = OwnerTaskBatch.obtain(ownerId, currentTick, lastKnownOwner);
        if (dueEvents != null && !dueEvents.isEmpty()) {
            batch.attachDueEvents(EnumSet.copyOf(dueEvents));
        }
        batch.attachPets(snapshotPets());
        return batch;
    }

    boolean applyPrediction(OwnerEventType type, long predictedTick) {
        if (type == null) {
            return false;
        }
        OwnerEventWindow window = eventWindows.computeIfAbsent(type, ignored -> new OwnerEventWindow());
        boolean changed = window.applyPrediction(predictedTick);
        if (changed) {
            deferredEvents.remove(type);
            recomputeNextEventTick();
        }
        return changed;
    }

    long nextEventTick() {
        return nextEventTick;
    }

    boolean markPending() {
        if (pending) {
            return false;
        }
        pending = true;
        return true;
    }

    void clearPendingFlag() {
        pending = false;
    }

    boolean isPending() {
        return pending;
    }

    private void recomputeNextEventTick() {
        long candidate = Long.MAX_VALUE;
        for (OwnerEventWindow window : eventWindows.values()) {
            if (window == null) {
                continue;
            }
            long next = window.nextTick();
            if (next < candidate) {
                candidate = next;
            }
        }
        nextEventTick = candidate;
    }

    private boolean hasScheduledEvents() {
        for (OwnerEventWindow window : eventWindows.values()) {
            if (window != null && window.hasSchedule()) {
                return true;
            }
        }
        return false;
    }

    void deferEvent(OwnerEventType type, long tick) {
        if (type == null) {
            return;
        }
        long clamped = Math.max(0L, tick);
        Long existing = deferredEvents.get(type);
        if (existing == null || clamped < existing) {
            deferredEvents.put(type, clamped);
        }
    }

    boolean activateDeferredEvent(OwnerEventType type, long currentTick) {
        if (type == null) {
            return false;
        }
        Long deferred = deferredEvents.remove(type);
        if (deferred == null) {
            return false;
        }
        OwnerEventWindow window = eventWindows.computeIfAbsent(type, ignored -> new OwnerEventWindow());
        long scheduledTick = Math.max(currentTick, deferred);
        window.schedule(scheduledTick);
        recomputeNextEventTick();
        return true;
    }

    boolean deactivateEvent(OwnerEventType type) {
        if (type == null) {
            return false;
        }
        OwnerEventWindow window = eventWindows.remove(type);
        if (window == null) {
            return false;
        }
        if (window.hasSchedule()) {
            deferEvent(type, window.nextTick());
        }
        recomputeNextEventTick();
        return true;
    }
}
