package woflo.petsplus.state.processing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetWorkScheduler;

/**
 * Coordinates aggregation of pet upkeep tasks into owner-scoped batches. This
 * manager keeps an owner cache that can be used by high-level systems to process
 * swarms in a single pass and to pivot toward event-driven scheduling.
 */
public final class OwnerProcessingManager {
    private static final EnumSet<OwnerEventType> ALWAYS_ACTIVE_EVENTS = EnumSet.of(OwnerEventType.INTERVAL);

    private final Map<UUID, OwnerProcessingGroup> groups = new HashMap<>();
    private final Map<PetComponent, UUID> membership = new IdentityHashMap<>();
    private final List<PetWorkScheduler.ScheduledTask> orphanTasks = new ArrayList<>();
    private final ArrayDeque<OwnerProcessingGroup> pendingQueue = new ArrayDeque<>();
    private final TreeMap<Long, List<OwnerProcessingGroup>> eventDueQueue = new TreeMap<>();
    private final EnumSet<OwnerEventType> activeEvents = EnumSet.copyOf(ALWAYS_ACTIVE_EVENTS);
    private final EnumMap<OwnerEventType, Boolean> lastPresence = new EnumMap<>(OwnerEventType.class);
    private long currentTick;

    public void trackPet(PetComponent component) {
        if (component == null) {
            return;
        }
        UUID ownerId = component.getOwnerUuid();
        UUID previous = membership.put(component, ownerId);
        if (previous != null && (ownerId == null || !previous.equals(ownerId))) {
            OwnerProcessingGroup previousGroup = groups.get(previous);
            if (previousGroup != null) {
                previousGroup.untrack(component);
                if (previousGroup.isEmpty()) {
                    groups.remove(previous);
                }
            }
        }
        if (ownerId == null) {
            return;
        }
        OwnerProcessingGroup group = groups.computeIfAbsent(ownerId, OwnerProcessingGroup::new);
        group.track(component);
    }

    public void untrackPet(PetComponent component) {
        if (component == null) {
            return;
        }
        UUID ownerId = membership.remove(component);
        if (ownerId == null) {
            return;
        }
        OwnerProcessingGroup group = groups.get(ownerId);
        if (group != null) {
            group.untrack(component);
            if (group.isEmpty()) {
                groups.remove(ownerId);
            }
        }
    }

    public void removeOwner(UUID ownerId) {
        OwnerProcessingGroup group = groups.remove(ownerId);
        if (group != null) {
            for (PetComponent component : group.currentPets()) {
                membership.remove(component);
            }
            group.clear();
        }
    }

    public void markPetChanged(PetComponent component, long currentTick) {
        if (component == null) {
            return;
        }
        UUID ownerId = component.getOwnerUuid();
        UUID previousOwner = membership.put(component, ownerId);
        if (previousOwner != null && (ownerId == null || !previousOwner.equals(ownerId))) {
            OwnerProcessingGroup previousGroup = groups.get(previousOwner);
            if (previousGroup != null) {
                previousGroup.untrack(component);
                if (previousGroup.currentPets().isEmpty()) {
                    removeGroupFromDueQueue(previousGroup);
                    groups.remove(previousOwner, previousGroup);
                    previousGroup.clear();
                }
            }
        }
        if (ownerId == null) {
            membership.remove(component);
            return;
        }
        OwnerProcessingGroup group = groups.computeIfAbsent(ownerId, OwnerProcessingGroup::new);
        group.markPetChanged(component, currentTick);
    }

    public void onTaskScheduled(PetComponent component, PetWorkScheduler.TaskType type, long tick) {
        if (component == null) {
            return;
        }
        UUID ownerId = component.getOwnerUuid();
        if (ownerId == null) {
            return;
        }
        OwnerProcessingGroup group = groups.computeIfAbsent(ownerId, OwnerProcessingGroup::new);
        membership.put(component, ownerId);
        OwnerEventType eventType = OwnerEventType.fromTaskType(type);
        boolean eventEnabled = isEventActive(eventType);
        group.onTaskScheduled(component, type, tick, eventEnabled);
        if (eventEnabled && eventType != null) {
            scheduleEventGroup(group);
        }
    }

    public void onTaskExecuted(PetComponent component, PetWorkScheduler.TaskType type, long tick) {
        if (component == null) {
            return;
        }
        UUID ownerId = membership.get(component);
        if (ownerId == null) {
            return;
        }
        OwnerProcessingGroup group = groups.get(ownerId);
        if (group != null) {
            OwnerEventType eventType = OwnerEventType.fromTaskType(type);
            boolean eventEnabled = isEventActive(eventType);
            group.onTaskExecuted(component, type, tick, eventEnabled);
            if (eventEnabled && eventType != null) {
                scheduleEventGroup(group);
            }
        }
    }

    public void onTasksCleared(PetComponent component) {
        if (component == null) {
            return;
        }
        UUID ownerId = membership.get(component);
        if (ownerId == null) {
            return;
        }
        OwnerProcessingGroup group = groups.get(ownerId);
        if (group != null) {
            group.onTasksCleared(component);
            scheduleEventGroup(group);
        }
    }

    public void signalMovement(PetComponent component, long currentTick) {
        signalEvent(component, OwnerEventType.AURA, currentTick);
        signalEvent(component, OwnerEventType.SUPPORT, currentTick);
        signalEvent(component, OwnerEventType.MOVEMENT, currentTick);
    }

    public void signalEvent(PetComponent component, OwnerEventType type, long tick) {
        if (component == null || type == null) {
            return;
        }
        UUID ownerId = component.getOwnerUuid();
        if (ownerId == null) {
            return;
        }
        membership.put(component, ownerId);
        OwnerProcessingGroup group = groups.computeIfAbsent(ownerId, OwnerProcessingGroup::new);
        group.track(component);
        boolean eventEnabled = isEventActive(type);
        group.signalEvent(type, tick, eventEnabled);
        if (eventEnabled) {
            scheduleEventGroup(group);
        }
    }

    public void signalEvent(UUID ownerId, OwnerEventType type, long tick) {
        if (ownerId == null || type == null) {
            return;
        }
        OwnerProcessingGroup group = groups.computeIfAbsent(ownerId, OwnerProcessingGroup::new);
        boolean eventEnabled = isEventActive(type);
        group.signalEvent(type, tick, eventEnabled);
        if (eventEnabled) {
            scheduleEventGroup(group);
        }
    }

    public OwnerTaskBatch createAdHocBatch(UUID ownerId, EnumSet<OwnerEventType> dueEvents, long currentTick) {
        if (ownerId == null || dueEvents == null || dueEvents.isEmpty()) {
            return null;
        }
        OwnerProcessingGroup group = groups.get(ownerId);
        if (group == null) {
            return null;
        }
        group.beginTick(currentTick);
        return group.snapshot(dueEvents, currentTick);
    }

    public void prepareForTick(long currentTick) {
        this.currentTick = currentTick;
        for (OwnerProcessingGroup group : groups.values()) {
            group.beginTick(currentTick);
        }
    }

    public int preparePendingGroups(long currentTick) {
        this.currentTick = currentTick;
        promoteDueEvents();
        return pendingQueue.size();
    }

    public void processOwnerTick(ServerPlayerEntity owner, long currentTick) {
        OwnerProcessingGroup group = groups.get(owner.getUuid());
        if (group != null) {
            group.onOwnerTick(owner, currentTick);
        }
    }

    @Nullable
    public OwnerTaskBatch snapshotOwnerTick(ServerPlayerEntity owner, long currentTick) {
        if (owner == null) {
            return null;
        }
        OwnerProcessingGroup group = groups.get(owner.getUuid());
        if (group == null) {
            return null;
        }
        group.onOwnerTick(owner, currentTick);
        return group.snapshotForOwnerTick(currentTick);
    }

    public void enqueueTask(PetWorkScheduler.ScheduledTask task) {
        PetComponent component = task.component();
        if (component == null) {
            orphanTasks.add(task);
            return;
        }
        UUID ownerId = membership.get(component);
        if (ownerId == null) {
            ownerId = component.getOwnerUuid();
            if (ownerId != null) {
                membership.put(component, ownerId);
            }
        }
        if (ownerId == null) {
            orphanTasks.add(task);
            return;
        }
        OwnerProcessingGroup group = groups.computeIfAbsent(ownerId, OwnerProcessingGroup::new);
        group.track(component);
        group.enqueue(task);
        queueGroup(group);
    }

    public void flushBatches(BiConsumer<UUID, OwnerTaskBatch> consumer, long currentTick, int budget) {
        this.currentTick = currentTick;
        if (budget <= 0) {
            processOrphanTasks(consumer, currentTick);
            return;
        }

        int processed = 0;
        while (processed < budget && !pendingQueue.isEmpty()) {
            OwnerProcessingGroup group = pendingQueue.pollFirst();
            if (group == null) {
                continue;
            }
            if (groups.get(group.ownerId()) != group) {
                continue;
            }

            group.clearPendingFlag();

            boolean hasTasks = group.hasPendingTasks();
            boolean hasDueEvents = group.hasDueEvents(currentTick);
            if (!hasTasks && !hasDueEvents) {
                if (group.isEmpty()) {
                    groups.remove(group.ownerId(), group);
                } else {
                    scheduleEventGroup(group);
                }
                continue;
            }

            OwnerTaskBatch batch = group.drain(currentTick);
            if (batch == null) {
                continue;
            }
            consumer.accept(group.ownerId(), batch);
            processed++;

            if (group.isEmpty()) {
                groups.remove(group.ownerId(), group);
            } else {
                scheduleEventGroup(group);
            }
        }

        processOrphanTasks(consumer, currentTick);
    }

    public void markOwnerEventExecuted(@Nullable UUID ownerId, OwnerEventType type, long tick) {
        if (ownerId == null || type == null) {
            return;
        }
        OwnerProcessingGroup group = groups.get(ownerId);
        if (group != null) {
            boolean eventEnabled = isEventActive(type);
            group.markEventExecuted(type, tick, eventEnabled);
            if (eventEnabled) {
                scheduleEventGroup(group);
            }
        }
    }

    public Collection<OwnerProcessingGroup> groups() {
        return groups.values();
    }

    public void applySchedulingPrediction(UUID ownerId,
                                          OwnerSchedulingPrediction prediction,
                                          long currentTick) {
        if (ownerId == null || prediction == null || prediction.isEmpty()) {
            return;
        }
        OwnerProcessingGroup group = groups.get(ownerId);
        if (group == null) {
            return;
        }

        boolean changed = false;
        for (Map.Entry<OwnerEventType, Long> entry : prediction.nextEventTicks().entrySet()) {
            OwnerEventType type = entry.getKey();
            if (!isEventActive(type)) {
                continue;
            }
            long predictedTick = Math.max(currentTick, Math.max(0L, entry.getValue()));
            if (group.applyPrediction(type, predictedTick)) {
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        removeGroupFromDueQueue(group);
        long nextTick = group.nextEventTick();
        if (nextTick <= currentTick) {
            queueGroup(group);
        } else {
            scheduleEventGroup(group);
        }
    }

    public void applyEventWindowPredictions(UUID ownerId,
                                            Map<OwnerEventType, Long> predictions,
                                            long currentTick) {
        if (ownerId == null || predictions == null || predictions.isEmpty()) {
            return;
        }
        OwnerProcessingGroup group = groups.get(ownerId);
        if (group == null) {
            return;
        }

        boolean changed = false;
        for (Map.Entry<OwnerEventType, Long> entry : predictions.entrySet()) {
            OwnerEventType type = entry.getKey();
            if (!isEventActive(type)) {
                continue;
            }
            long tick = Math.max(0L, entry.getValue() == null ? 0L : entry.getValue());
            if (group.applyPrediction(type, tick)) {
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        removeGroupFromDueQueue(group);
        long nextTick = group.nextEventTick();
        if (nextTick <= currentTick) {
            queueGroup(group);
        } else {
            scheduleEventGroup(group);
        }
    }

    private void queueGroup(@Nullable OwnerProcessingGroup group) {
        if (group == null) {
            return;
        }
        if (groups.get(group.ownerId()) != group) {
            return;
        }
        if (group.markPending()) {
            pendingQueue.addLast(group);
        }
    }

    private void scheduleEventGroup(@Nullable OwnerProcessingGroup group) {
        if (group == null) {
            return;
        }
        long nextTick = group.nextEventTick();
        if (nextTick == Long.MAX_VALUE) {
            return;
        }
        eventDueQueue.computeIfAbsent(nextTick, ignored -> new ArrayList<>()).add(group);
    }

    private void promoteDueEvents() {
        while (!eventDueQueue.isEmpty()) {
            Map.Entry<Long, List<OwnerProcessingGroup>> entry = eventDueQueue.firstEntry();
            if (entry == null || entry.getKey() > currentTick) {
                break;
            }
            eventDueQueue.pollFirstEntry();
            List<OwnerProcessingGroup> dueGroups = entry.getValue();
            if (dueGroups == null) {
                continue;
            }
            for (OwnerProcessingGroup group : dueGroups) {
                if (group == null) {
                    continue;
                }
                if (groups.get(group.ownerId()) != group) {
                    continue;
                }
                long nextTick = group.nextEventTick();
                if (nextTick > currentTick && nextTick != Long.MAX_VALUE) {
                    scheduleEventGroup(group);
                    continue;
                }
                queueGroup(group);
            }
        }
    }

    private void processOrphanTasks(BiConsumer<UUID, OwnerTaskBatch> consumer, long currentTick) {
        if (orphanTasks.isEmpty()) {
            return;
        }
        for (PetWorkScheduler.ScheduledTask task : orphanTasks) {
            try (OwnerTaskBatch batch = OwnerTaskBatch.single(task, currentTick)) {
                consumer.accept(null, batch);
            }
        }
        orphanTasks.clear();
    }

    public void onListenerPresenceChanged(OwnerEventType type, boolean present) {
        if (type == null) {
            return;
        }
        Boolean previous = lastPresence.put(type, present);
        if (previous != null && previous == present) {
            return;
        }
        if (present) {
            if (activeEvents.add(type)) {
                for (OwnerProcessingGroup group : groups.values()) {
                    if (group.activateDeferredEvent(type, currentTick)) {
                        scheduleEventGroup(group);
                    }
                }
            }
        } else if (!ALWAYS_ACTIVE_EVENTS.contains(type)) {
            if (activeEvents.remove(type)) {
                for (OwnerProcessingGroup group : groups.values()) {
                    if (group.deactivateEvent(type)) {
                        removeGroupFromDueQueue(group);
                    }
                }
            }
        }
    }

    private boolean isEventActive(@Nullable OwnerEventType type) {
        if (type == null) {
            return true;
        }
        return ALWAYS_ACTIVE_EVENTS.contains(type) || activeEvents.contains(type);
    }

    private void removeGroupFromDueQueue(OwnerProcessingGroup target) {
        if (target == null || eventDueQueue.isEmpty()) {
            return;
        }
        var iterator = eventDueQueue.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, List<OwnerProcessingGroup>> entry = iterator.next();
            List<OwnerProcessingGroup> list = entry.getValue();
            if (list == null) {
                iterator.remove();
                continue;
            }
            list.removeIf(group -> group == target);
            if (list.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public void shutdown() {
        for (OwnerProcessingGroup group : groups.values()) {
            group.clear();
        }
        groups.clear();
        membership.clear();
        orphanTasks.clear();
        pendingQueue.clear();
        eventDueQueue.clear();
        activeEvents.clear();
        activeEvents.addAll(ALWAYS_ACTIVE_EVENTS);
        lastPresence.clear();
        currentTick = 0L;
    }
}
