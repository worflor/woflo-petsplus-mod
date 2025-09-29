package woflo.petsplus.state.processing;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import woflo.petsplus.state.PetWorkScheduler;

/**
 * Immutable container describing predicted next-run ticks for owner-scoped
 * events. Predictions are derived from {@link OwnerBatchSnapshot} data so they
 * can be computed safely on a background worker.
 */
public final class OwnerSchedulingPrediction {
    private static final OwnerSchedulingPrediction EMPTY = new OwnerSchedulingPrediction(new EnumMap<>(OwnerEventType.class));

    private final EnumMap<OwnerEventType, Long> nextEventTicks;

    private OwnerSchedulingPrediction(EnumMap<OwnerEventType, Long> nextEventTicks) {
        this.nextEventTicks = nextEventTicks;
    }

    public boolean isEmpty() {
        return nextEventTicks.isEmpty();
    }

    public Map<OwnerEventType, Long> nextEventTicks() {
        return nextEventTicks;
    }

    public static OwnerSchedulingPrediction empty() {
        return EMPTY;
    }

    /**
     * Builds a scheduling prediction using immutable batch data captured on the
     * main thread. Predictions favour the earliest task due dates for each owner
     * event and fall back to a conservative interval when no explicit schedule
     * is available.
     */
    public static OwnerSchedulingPrediction predict(OwnerBatchSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        EnumMap<OwnerEventType, Long> ticks = new EnumMap<>(OwnerEventType.class);
        long snapshotTick = snapshot.snapshotTick();

        Map<PetWorkScheduler.TaskType, List<OwnerBatchSnapshot.TaskSnapshot>> buckets = snapshot.taskBuckets();
        for (Map.Entry<PetWorkScheduler.TaskType, List<OwnerBatchSnapshot.TaskSnapshot>> entry : buckets.entrySet()) {
            PetWorkScheduler.TaskType taskType = entry.getKey();
            if (taskType == null) {
                continue;
            }
            OwnerEventType eventType = OwnerEventType.fromTaskType(taskType);
            if (eventType == null) {
                continue;
            }
            List<OwnerBatchSnapshot.TaskSnapshot> tasks = entry.getValue();
            if (tasks == null || tasks.isEmpty()) {
                continue;
            }
            long bestTick = ticks.getOrDefault(eventType, Long.MAX_VALUE);
            for (OwnerBatchSnapshot.TaskSnapshot task : tasks) {
                if (task == null) {
                    continue;
                }
                long dueTick = clampFutureTick(task.dueTick(), snapshotTick);
                if (dueTick < bestTick) {
                    bestTick = dueTick;
                }
            }
            if (bestTick != Long.MAX_VALUE) {
                ticks.put(eventType, bestTick);
            }
        }

        Set<OwnerEventType> dueEvents = snapshot.dueEvents();
        if (dueEvents != null && !dueEvents.isEmpty()) {
            for (OwnerEventType eventType : dueEvents) {
                if (eventType == null) {
                    continue;
                }
                long existing = ticks.getOrDefault(eventType, Long.MAX_VALUE);
                long fallback = clampFutureTick(snapshotTick + estimateFallbackInterval(eventType), snapshotTick);
                long chosen = existing == Long.MAX_VALUE ? fallback : Math.min(existing, fallback);
                ticks.put(eventType, chosen);
            }
        }

        if (ticks.isEmpty()) {
            return empty();
        }

        return new OwnerSchedulingPrediction(new EnumMap<>(ticks));
    }

    private static long clampFutureTick(long candidate, long baseline) {
        if (candidate <= baseline) {
            candidate = baseline + 1L;
        }
        return Math.max(0L, candidate);
    }

    private static long estimateFallbackInterval(OwnerEventType type) {
        return switch (type) {
            case INTERVAL -> 20L;
            case AURA -> 20L;
            case SUPPORT -> 40L;
            case PARTICLE -> 40L;
            case GOSSIP -> 200L;
            case MOVEMENT -> 5L;
            case XP_GAIN -> 1L;
            case EMOTION -> 60L;
            case ABILITY_TRIGGER -> 5L;
        };
    }
}
