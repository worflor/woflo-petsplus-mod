package woflo.petsplus.state.processing;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import woflo.petsplus.state.coordination.PetWorkScheduler;
import woflo.petsplus.state.processing.GossipPropagationPlanner.GossipPropagationPlan;

/**
 * Utility that prepares owner-scoped planning results on a background thread.
 */
public final class OwnerBatchPlanner {
    private OwnerBatchPlanner() {
    }

    public static OwnerBatchPlan plan(OwnerBatchSnapshot snapshot) {
        if (snapshot == null) {
            return OwnerBatchPlan.empty();
        }

        OwnerBatchPlan.Builder builder = OwnerBatchPlan.builder();
        EnumSet<OwnerEventType> spatialConsumers = collectSpatialConsumers(snapshot);
        if (!spatialConsumers.isEmpty()) {
            OwnerSpatialResult spatialResult = OwnerSpatialResult.analyze(snapshot);
            if (spatialResult != null && !spatialResult.isEmpty()) {
                builder.withSharedPayload(spatialConsumers, spatialResult);
            }
        }

        EnumMap<OwnerEventType, Long> windowPredictions = OwnerEventWindowAnalysis.analyze(snapshot);
        if (!windowPredictions.isEmpty()) {
            builder.withEventWindows(windowPredictions);
        }

        GossipPropagationPlan gossipPlan = GossipPropagationPlanner.plan(snapshot);
        if (!gossipPlan.isEmpty()) {
            builder.withPayload(OwnerEventType.GOSSIP, gossipPlan);
        }

        AbilityCooldownPlan cooldownPlan = AbilityCooldownPlanner.plan(snapshot);
        if (!cooldownPlan.isEmpty()) {
            builder.withAbilityCooldownPlan(cooldownPlan);
        }

        if (AsyncProcessingSettings.asyncPredictiveSchedulingEnabled()) {
            OwnerSchedulingPrediction prediction = OwnerSchedulingPrediction.predict(snapshot);
            builder.withSchedulingPrediction(prediction);
        }

        return builder.build();
    }

    private static EnumSet<OwnerEventType> collectSpatialConsumers(OwnerBatchSnapshot snapshot) {
        EnumSet<OwnerEventType> consumers = EnumSet.noneOf(OwnerEventType.class);
        Set<OwnerEventType> dueEvents = snapshot.dueEvents();
        for (OwnerEventType event : dueEvents) {
            if (event != null && event.requiresSwarmSnapshot()) {
                consumers.add(event);
            }
        }
        Map<PetWorkScheduler.TaskType, java.util.List<OwnerBatchSnapshot.TaskSnapshot>> buckets = snapshot.taskBuckets();
        for (Map.Entry<PetWorkScheduler.TaskType, java.util.List<OwnerBatchSnapshot.TaskSnapshot>> entry : buckets.entrySet()) {
            PetWorkScheduler.TaskType type = entry.getKey();
            if (type == null) {
                continue;
            }
            OwnerEventType eventType = OwnerEventType.fromTaskType(type);
            if (eventType != null && eventType.requiresSwarmSnapshot()) {
                consumers.add(eventType);
            }
        }
        return consumers;
    }
}
