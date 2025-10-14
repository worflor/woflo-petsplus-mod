package woflo.petsplus.state.processing;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import woflo.petsplus.state.coordination.PetWorkScheduler;

/**
 * Batch planner shell for owner-centric processing.
 *
 * Design intent:
 * - Two-phase pipeline:
 *   1) Sense (async-eligible) aggregation/precomputation
 *   2) Act (main-thread) application with bounded work per tick
 * - No direct references to MC types to avoid compile coupling
 */
public final class OwnerBatchPlanner {

    private OwnerBatchPlanner() {
        // Prevent instantiation
    }

    /**
     * Plans and executes batches across owners according to documented policy.
     * Reserved for future expansion.
     */
    public static void planAndExecuteBatches() {
        // Intentionally left empty; batch planning is invoked per-owner via {@link #plan(OwnerBatchSnapshot)}
    }

    /**
     * Produce a deterministic asynchronous plan for the supplied snapshot.
     * Captures cooldown expiry data, scheduling hints, and optional spatial payloads.
     */
    public static OwnerBatchPlan plan(OwnerBatchSnapshot snapshot) {
        if (snapshot == null) {
            return OwnerBatchPlan.empty();
        }

        OwnerBatchPlan.Builder builder = OwnerBatchPlan.builder();
        boolean hasContent = false;

        AbilityCooldownPlan cooldownPlan = AbilityCooldownPlanner.plan(snapshot);
        if (!cooldownPlan.isEmpty()) {
            builder.withAbilityCooldownPlan(cooldownPlan);
            hasContent = true;
        }

        OwnerSchedulingPrediction prediction = OwnerSchedulingPrediction.predict(snapshot);
        if (prediction != null && !prediction.isEmpty()) {
            builder.withSchedulingPrediction(prediction);
            builder.withEventWindows(new EnumMap<>(prediction.nextEventTicks()));
            hasContent = true;
        }

        EnumSet<OwnerEventType> swarmTargets = determineSwarmTargets(snapshot);
        if (!swarmTargets.isEmpty()) {
            OwnerSpatialResult spatialResult = OwnerSpatialResult.analyze(snapshot);
            if (!spatialResult.isEmpty()) {
                for (OwnerEventType type : swarmTargets) {
                    builder.withPayload(type, spatialResult);
                }
                hasContent = true;
            }
        }

        if (!hasContent) {
            return OwnerBatchPlan.empty();
        }
        return builder.build();
    }

    private static EnumSet<OwnerEventType> determineSwarmTargets(OwnerBatchSnapshot snapshot) {
        EnumSet<OwnerEventType> targets = EnumSet.noneOf(OwnerEventType.class);
        Set<OwnerEventType> dueEvents = snapshot.dueEvents();
        Map<PetWorkScheduler.TaskType, List<OwnerBatchSnapshot.TaskSnapshot>> buckets = snapshot.taskBuckets();

        for (OwnerEventType type : OwnerEventType.values()) {
            if (type == null || !type.requiresSwarmSnapshot()) {
                continue;
            }

            boolean required = dueEvents.contains(type);
            if (!required) {
                PetWorkScheduler.TaskType schedulerType = type.schedulerType();
                if (schedulerType != null) {
                    List<OwnerBatchSnapshot.TaskSnapshot> tasks = buckets.get(schedulerType);
                    required = tasks != null && !tasks.isEmpty();
                }
            }

            if (required) {
                targets.add(type);
            }
        }
        return targets;
    }
}
