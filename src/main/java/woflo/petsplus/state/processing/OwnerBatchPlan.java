package woflo.petsplus.state.processing;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Describes the precomputed owner-scoped data that should be applied on the
 * main thread after an asynchronous planning pass completes.
 */
public final class OwnerBatchPlan {
    private final EnumMap<OwnerEventType, Object> eventPayloads;
    private final EnumMap<OwnerEventType, Long> eventWindows;
    private final OwnerSchedulingPrediction schedulingPrediction;
    private final AbilityCooldownPlan abilityCooldownPlan;

    OwnerBatchPlan(EnumMap<OwnerEventType, Object> eventPayloads,
                   EnumMap<OwnerEventType, Long> eventWindows,
                   OwnerSchedulingPrediction schedulingPrediction,
                   AbilityCooldownPlan abilityCooldownPlan) {
        this.eventPayloads = eventPayloads == null || eventPayloads.isEmpty()
            ? new EnumMap<>(OwnerEventType.class)
            : new EnumMap<>(eventPayloads);
        this.eventWindows = eventWindows == null || eventWindows.isEmpty()
            ? new EnumMap<>(OwnerEventType.class)
            : new EnumMap<>(eventWindows);
        this.schedulingPrediction = schedulingPrediction;
        this.abilityCooldownPlan = abilityCooldownPlan == null ? AbilityCooldownPlan.empty() : abilityCooldownPlan;
    }

    /**
     * @return immutable view of the payloads keyed by owner event type.
     */
    public Map<OwnerEventType, Object> eventPayloads() {
        if (eventPayloads.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(eventPayloads);
    }

    /**
     * @return optional scheduling prediction prepared off-thread, or {@code null}
     *         when no prediction was generated.
     */
    public OwnerSchedulingPrediction schedulingPrediction() {
        return schedulingPrediction;
    }

    public AbilityCooldownPlan abilityCooldownPlan() {
        return abilityCooldownPlan;
    }

    public Map<OwnerEventType, Long> eventWindowPredictions() {
        if (eventWindows.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(eventWindows);
    }

    public boolean hasEventWindowPrediction(OwnerEventType type) {
        return type != null && eventWindows.containsKey(type);
    }

    public boolean hasPayloadFor(OwnerEventType type) {
        return type != null && eventPayloads.containsKey(type);
    }

    public Set<OwnerEventType> payloadEventTypes() {
        if (eventPayloads.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(eventPayloads.keySet());
    }

    public Object payload(OwnerEventType type) {
        return type == null ? null : eventPayloads.get(type);
    }

    public static OwnerBatchPlan empty() {
        return new OwnerBatchPlan(new EnumMap<>(OwnerEventType.class), new EnumMap<>(OwnerEventType.class), null,
            AbilityCooldownPlan.empty());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EnumMap<OwnerEventType, Object> payloads = new EnumMap<>(OwnerEventType.class);
        private final EnumMap<OwnerEventType, Long> eventWindows = new EnumMap<>(OwnerEventType.class);
        private OwnerSchedulingPrediction schedulingPrediction;
        private AbilityCooldownPlan abilityCooldownPlan;

        public Builder withPayload(OwnerEventType type, Object payload) {
            if (type != null && payload != null) {
                payloads.put(type, payload);
            }
            return this;
        }

        public Builder withEventWindow(OwnerEventType type, long tick) {
            if (type != null && tick >= 0L) {
                eventWindows.put(type, tick);
            }
            return this;
        }

        public Builder withEventWindows(Map<OwnerEventType, Long> windows) {
            if (windows == null || windows.isEmpty()) {
                return this;
            }
            for (Map.Entry<OwnerEventType, Long> entry : windows.entrySet()) {
                OwnerEventType type = entry.getKey();
                Long tick = entry.getValue();
                if (type != null && tick != null) {
                    eventWindows.put(type, Math.max(0L, tick));
                }
            }
            return this;
        }

        public Builder withSharedPayload(Set<OwnerEventType> types, Object payload) {
            if (payload == null || types == null || types.isEmpty()) {
                return this;
            }
            for (OwnerEventType type : types) {
                if (type != null) {
                    payloads.put(type, payload);
                }
            }
            return this;
        }

        public Builder withSchedulingPrediction(OwnerSchedulingPrediction prediction) {
            if (prediction != null && !prediction.isEmpty()) {
                this.schedulingPrediction = prediction;
            }
            return this;
        }

        public Builder withAbilityCooldownPlan(AbilityCooldownPlan plan) {
            if (plan != null && !plan.isEmpty()) {
                this.abilityCooldownPlan = plan;
            }
            return this;
        }

        public OwnerBatchPlan build() {
            return new OwnerBatchPlan(payloads, eventWindows, schedulingPrediction,
                abilityCooldownPlan == null ? AbilityCooldownPlan.empty() : abilityCooldownPlan);
        }
    }
}
