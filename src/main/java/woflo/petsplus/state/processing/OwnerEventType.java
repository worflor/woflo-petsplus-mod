package woflo.petsplus.state.processing;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import woflo.petsplus.state.coordination.PetWorkScheduler;

/**
 * High level buckets that aggregate pet-centric upkeep work into owner-scoped
 * events. These events are used by the {@link OwnerProcessingManager} to batch
 * related work and drive event-triggered execution rather than polling.
 */
public enum OwnerEventType {
    INTERVAL(PetWorkScheduler.TaskType.INTERVAL, false, true),
    AURA(PetWorkScheduler.TaskType.AURA, true, false),
    SUPPORT(PetWorkScheduler.TaskType.SUPPORT_POTION, true, false),
    PARTICLE(PetWorkScheduler.TaskType.PARTICLE, false, false),
    GOSSIP(PetWorkScheduler.TaskType.GOSSIP_DECAY, true, false),
    MOVEMENT(null, true, false),
    XP_GAIN(null, true, false),
    EMOTION(null, true, false),
    ABILITY_TRIGGER(null, false, true);

    private static final Map<PetWorkScheduler.TaskType, OwnerEventType> LOOKUP =
        buildLookup();

    private final PetWorkScheduler.TaskType schedulerType;
    private final boolean requiresSwarmSnapshot;
    private final boolean primesScheduling;

    OwnerEventType(PetWorkScheduler.TaskType schedulerType,
                   boolean requiresSwarmSnapshot,
                   boolean primesScheduling) {
        this.schedulerType = schedulerType;
        this.requiresSwarmSnapshot = requiresSwarmSnapshot;
        this.primesScheduling = primesScheduling;
    }

    public PetWorkScheduler.TaskType schedulerType() {
        return schedulerType;
    }

    public boolean requiresSwarmSnapshot() {
        return requiresSwarmSnapshot;
    }

    public boolean primesScheduling() {
        return primesScheduling;
    }

    public static OwnerEventType fromTaskType(PetWorkScheduler.TaskType type) {
        if (type == null) {
            return null;
        }
        return LOOKUP.get(type);
    }

    private static Map<PetWorkScheduler.TaskType, OwnerEventType> buildLookup() {
        EnumMap<PetWorkScheduler.TaskType, OwnerEventType> map =
            new EnumMap<>(PetWorkScheduler.TaskType.class);
        for (OwnerEventType eventType : values()) {
            PetWorkScheduler.TaskType schedulerType = eventType.schedulerType;
            if (schedulerType != null) {
                map.put(Objects.requireNonNull(schedulerType), eventType);
            }
        }
        return map;
    }

    public static EnumSet<OwnerEventType> noneOf() {
        return EnumSet.noneOf(OwnerEventType.class);
    }
}
