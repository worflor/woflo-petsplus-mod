package woflo.petsplus.state.processing;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import woflo.petsplus.state.PetWorkScheduler;

/**
 * Computes upcoming owner event window predictions using immutable owner batch
 * snapshots so expensive window reconciliation can be staged on background
 * threads.
 */
final class OwnerEventWindowAnalysis {
    private OwnerEventWindowAnalysis() {
    }

    static EnumMap<OwnerEventType, Long> analyze(OwnerBatchSnapshot snapshot) {
        EnumMap<OwnerEventType, Long> predictions = new EnumMap<>(OwnerEventType.class);
        if (snapshot == null) {
            return predictions;
        }

        long snapshotTick = Math.max(0L, snapshot.snapshotTick());

        Set<OwnerEventType> dueEvents = snapshot.dueEvents();
        if (dueEvents != null && !dueEvents.isEmpty()) {
            for (OwnerEventType eventType : dueEvents) {
                if (eventType != null) {
                    predictions.put(eventType, snapshotTick);
                }
            }
        }

        Map<PetWorkScheduler.TaskType, List<OwnerBatchSnapshot.TaskSnapshot>> buckets = snapshot.taskBuckets();
        if (buckets != null && !buckets.isEmpty()) {
            for (Map.Entry<PetWorkScheduler.TaskType, List<OwnerBatchSnapshot.TaskSnapshot>> entry : buckets.entrySet()) {
                PetWorkScheduler.TaskType taskType = entry.getKey();
                OwnerEventType eventType = OwnerEventType.fromTaskType(taskType);
                if (eventType == null) {
                    continue;
                }
                List<OwnerBatchSnapshot.TaskSnapshot> tasks = entry.getValue();
                if (tasks == null || tasks.isEmpty()) {
                    continue;
                }
                long earliest = predictions.getOrDefault(eventType, Long.MAX_VALUE);
                for (OwnerBatchSnapshot.TaskSnapshot task : tasks) {
                    if (task == null) {
                        continue;
                    }
                    long dueTick = Math.max(0L, task.dueTick());
                    if (dueTick < earliest) {
                        earliest = dueTick;
                    }
                }
                if (earliest != Long.MAX_VALUE) {
                    predictions.put(eventType, Math.max(snapshotTick, earliest));
                }
            }
        }

        long abilityTriggerTick = Long.MAX_VALUE;
        List<OwnerBatchSnapshot.PetSummary> pets = snapshot.pets();
        if (pets != null && !pets.isEmpty()) {
            for (OwnerBatchSnapshot.PetSummary pet : pets) {
                if (pet == null) {
                    continue;
                }
                Map<String, Long> cooldowns = pet.cooldowns();
                if (cooldowns == null || cooldowns.isEmpty()) {
                    continue;
                }
                for (Long expiry : cooldowns.values()) {
                    if (expiry == null) {
                        continue;
                    }
                    long sanitized = Math.max(snapshotTick, Math.max(0L, expiry));
                    if (sanitized < abilityTriggerTick) {
                        abilityTriggerTick = sanitized;
                    }
                }
            }
        }
        if (abilityTriggerTick != Long.MAX_VALUE) {
            predictions.put(OwnerEventType.ABILITY_TRIGGER, abilityTriggerTick);
        }

        return predictions;
    }
}
