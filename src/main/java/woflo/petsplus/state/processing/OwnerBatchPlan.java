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
    private static final OwnerBatchPlan EMPTY = new OwnerBatchPlan(Map.of(), Map.of(), null, AbilityCooldownPlan.empty());

    private final Map<OwnerEventType, Object> eventPayloads;
    private final Set<OwnerEventType> payloadEventTypes;
    private final Map<OwnerEventType, Long> eventWindows;
    private final OwnerSchedulingPrediction schedulingPrediction;
    private final AbilityCooldownPlan abilityCooldownPlan;

    OwnerBatchPlan(Map<OwnerEventType, Object> eventPayloads,
                   Map<OwnerEventType, Long> eventWindows,
                   OwnerSchedulingPrediction schedulingPrediction,
                   AbilityCooldownPlan abilityCooldownPlan) {
        if (eventPayloads == null || eventPayloads.isEmpty()) {
            this.eventPayloads = Map.of();
            this.payloadEventTypes = Set.of();
        } else {
            EnumMap<OwnerEventType, Object> copy;
            if (eventPayloads instanceof EnumMap<?, ?> enumMap) {
                @SuppressWarnings("unchecked")
                EnumMap<OwnerEventType, Object> typed = (EnumMap<OwnerEventType, Object>) enumMap;
                copy = new EnumMap<>(typed);
            } else {
                copy = new EnumMap<>(eventPayloads);
            }
            this.eventPayloads = Collections.unmodifiableMap(copy);
            this.payloadEventTypes = Collections.unmodifiableSet(copy.keySet());
        }

        if (eventWindows == null || eventWindows.isEmpty()) {
            this.eventWindows = Map.of();
        } else {
            EnumMap<OwnerEventType, Long> copy;
            if (eventWindows instanceof EnumMap<?, ?> enumMap) {
                @SuppressWarnings("unchecked")
                EnumMap<OwnerEventType, Long> typed = (EnumMap<OwnerEventType, Long>) enumMap;
                copy = new EnumMap<>(typed);
            } else {
                copy = new EnumMap<>(eventWindows);
            }
            this.eventWindows = Collections.unmodifiableMap(copy);
        }

        this.schedulingPrediction = schedulingPrediction;
        this.abilityCooldownPlan = abilityCooldownPlan == null ? AbilityCooldownPlan.empty() : abilityCooldownPlan;
    }

    /**
     * @return immutable view of the payloads keyed by owner event type.
     */
    public Map<OwnerEventType, Object> eventPayloads() {
        return eventPayloads;
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
        return eventWindows;
    }

    public boolean hasEventWindowPrediction(OwnerEventType type) {
        return type != null && eventWindows.containsKey(type);
    }

    public boolean hasPayloadFor(OwnerEventType type) {
        return type != null && eventPayloads.containsKey(type);
    }

    public Set<OwnerEventType> payloadEventTypes() {
        return payloadEventTypes;
    }

    public Object payload(OwnerEventType type) {
        return type == null ? null : eventPayloads.get(type);
    }

    public static OwnerBatchPlan empty() {
        return EMPTY;
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
