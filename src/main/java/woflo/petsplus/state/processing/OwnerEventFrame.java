package woflo.petsplus.state.processing;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.jetbrains.annotations.Nullable;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;

/**
 * Snapshot describing an owner-scoped event dispatch. Provides batched context so
 * listeners can respond without recalculating pet or spatial lookups. Instances are
 * pooled; listeners must not retain a reference outside the dispatch callback.
 */
public final class OwnerEventFrame implements AutoCloseable {
    private static final int MAX_POOL_SIZE = 256;

    private static final java.util.ArrayDeque<OwnerEventFrame> POOL = new java.util.ArrayDeque<>();

    private OwnerEventType eventType;
    private ServerWorld world;
    private @Nullable ServerPlayerEntity owner;
    private @Nullable UUID ownerId;
    private List<PetComponent> pets = List.of();
    private List<PetSwarmIndex.SwarmEntry> swarmSnapshot = List.of();
    private final EnumSet<OwnerEventType> dueEvents = EnumSet.noneOf(OwnerEventType.class);
    private final Set<OwnerEventType> dueEventsView = Collections.unmodifiableSet(dueEvents);
    private long currentTick;
    private OwnerTaskBatch batch;
    private OwnerBatchSnapshot snapshot;
    private @Nullable Object payload;

    private OwnerEventFrame() {
    }

    public static OwnerEventFrame obtain(OwnerEventType eventType,
                                         ServerWorld world,
                                         @Nullable ServerPlayerEntity owner,
                                         @Nullable UUID ownerId,
                                         List<PetComponent> pets,
                                         List<PetSwarmIndex.SwarmEntry> swarmSnapshot,
                                         EnumSet<OwnerEventType> dueEvents,
                                         long currentTick,
                                         OwnerTaskBatch batch,
                                         OwnerBatchSnapshot snapshot,
                                         @Nullable Object payload) {
        OwnerEventFrame frame;
        synchronized (POOL) {
            frame = POOL.pollFirst();
        }
        if (frame == null) {
            frame = new OwnerEventFrame();
        }
        frame.prepare(eventType, world, owner, ownerId, pets, swarmSnapshot, dueEvents, currentTick, batch, snapshot, payload);
        return frame;
    }

    private void prepare(OwnerEventType eventType,
                         ServerWorld world,
                         @Nullable ServerPlayerEntity owner,
                         @Nullable UUID ownerId,
                         List<PetComponent> pets,
                         List<PetSwarmIndex.SwarmEntry> swarmSnapshot,
                         EnumSet<OwnerEventType> dueEvents,
                         long currentTick,
                         OwnerTaskBatch batch,
                         OwnerBatchSnapshot snapshot,
                         @Nullable Object payload) {
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.world = Objects.requireNonNull(world, "world");
        this.owner = owner;
        this.ownerId = ownerId;
        this.pets = pets == null || pets.isEmpty() ? List.of() : pets;
        this.swarmSnapshot = swarmSnapshot == null || swarmSnapshot.isEmpty() ? List.of() : swarmSnapshot;
        this.dueEvents.clear();
        if (dueEvents != null && !dueEvents.isEmpty()) {
            this.dueEvents.addAll(dueEvents);
        }
        this.currentTick = currentTick;
        this.batch = Objects.requireNonNull(batch, "batch");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.payload = payload;
    }

    private void reset() {
        eventType = null;
        world = null;
        owner = null;
        ownerId = null;
        pets = List.of();
        swarmSnapshot = List.of();
        dueEvents.clear();
        currentTick = 0L;
        batch = null;
        snapshot = null;
        payload = null;
    }

    @Override
    public void close() {
        release();
    }

    public void release() {
        reset();
        synchronized (POOL) {
            if (POOL.size() < MAX_POOL_SIZE) {
                POOL.addFirst(this);
            }
        }
    }

    public OwnerEventType eventType() {
        return eventType;
    }

    public ServerWorld world() {
        return world;
    }

    @Nullable
    public ServerPlayerEntity owner() {
        return owner;
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    public List<PetComponent> pets() {
        return pets;
    }

    public List<PetSwarmIndex.SwarmEntry> swarmSnapshot() {
        return swarmSnapshot;
    }

    public Set<OwnerEventType> dueEvents() {
        if (dueEvents.isEmpty()) {
            return Set.of();
        }
        return dueEventsView;
    }

    public long currentTick() {
        return currentTick;
    }

    public OwnerTaskBatch batch() {
        return batch;
    }

    public OwnerBatchSnapshot snapshot() {
        return snapshot;
    }

    @Nullable
    public Object payload() {
        return payload;
    }

    @Nullable
    public <T> T payload(Class<T> type) {
        if (type == null || payload == null) {
            return null;
        }
        return type.isInstance(payload) ? type.cast(payload) : null;
    }

    public boolean hasOwner() {
        return owner != null && !owner.isRemoved();
    }

    public boolean includesEvent(OwnerEventType type) {
        return dueEvents.contains(type);
    }
}
