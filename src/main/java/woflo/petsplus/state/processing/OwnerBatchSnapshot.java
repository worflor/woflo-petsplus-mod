package woflo.petsplus.state.processing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import org.jetbrains.annotations.Nullable;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetWorkScheduler;

/**
 * Immutable description of an owner task batch that strips out references to
 * live world state so it can safely be handed to background workers.
 */
public final class OwnerBatchSnapshot {
    private final UUID ownerId;
    private final long snapshotTick;
    private final UUID lastKnownOwnerId;
    private final Set<OwnerEventType> dueEvents;
    private final Map<PetWorkScheduler.TaskType, List<TaskSnapshot>> taskBuckets;
    private final List<PetSummary> pets;

    private OwnerBatchSnapshot(UUID ownerId,
                               long snapshotTick,
                               @Nullable UUID lastKnownOwnerId,
                               Set<OwnerEventType> dueEvents,
                               Map<PetWorkScheduler.TaskType, List<TaskSnapshot>> taskBuckets,
                               List<PetSummary> pets) {
        this.ownerId = ownerId;
        this.snapshotTick = snapshotTick;
        this.lastKnownOwnerId = lastKnownOwnerId;
        this.dueEvents = dueEvents;
        this.taskBuckets = taskBuckets;
        this.pets = pets;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public long snapshotTick() {
        return snapshotTick;
    }

    @Nullable
    public UUID lastKnownOwnerId() {
        return lastKnownOwnerId;
    }

    public Set<OwnerEventType> dueEvents() {
        return dueEvents;
    }

    public Map<PetWorkScheduler.TaskType, List<TaskSnapshot>> taskBuckets() {
        return taskBuckets;
    }

    public List<PetSummary> pets() {
        return pets;
    }

    /**
     * Create an immutable snapshot of the supplied batch for background execution.
     */
    public static OwnerBatchSnapshot capture(OwnerTaskBatch batch) {
        Objects.requireNonNull(batch, "batch");
        UUID ownerId = batch.ownerId();
        long snapshotTick = batch.snapshotTick();
        UUID lastKnownOwnerId = null;
        ServerPlayerEntity lastKnownOwner = batch.lastKnownOwner();
        if (lastKnownOwner != null) {
            lastKnownOwnerId = lastKnownOwner.getUuid();
        }

        Set<OwnerEventType> dueEvents;
        Set<OwnerEventType> dueView = batch.dueEventsView();
        if (dueView.isEmpty()) {
            dueEvents = Collections.emptySet();
        } else {
            dueEvents = Collections.unmodifiableSet(EnumSet.copyOf(dueView));
        }

        Map<PetWorkScheduler.TaskType, List<TaskSnapshot>> taskBuckets = new EnumMap<>(PetWorkScheduler.TaskType.class);
        batch.forEachBucket((type, tasks) -> {
            if (tasks == null || tasks.isEmpty()) {
                return;
            }
            List<TaskSnapshot> sanitized = new ArrayList<>(tasks.size());
            for (PetWorkScheduler.ScheduledTask task : tasks) {
                PetComponent component = task.component();
                Identifier roleId = component != null ? component.getRoleId() : null;
                UUID petId = null;
                if (component != null) {
                    MobEntity entity = component.getPetEntity();
                    petId = entity != null ? entity.getUuid() : null;
                } else if (task.pet() != null) {
                    petId = task.pet().getUuid();
                }
                sanitized.add(new TaskSnapshot(type, petId, roleId, task.dueTick()));
            }
            taskBuckets.put(type, List.copyOf(sanitized));
        });

        List<PetSummary> pets;
        List<PetComponent> batchPets = batch.pets();
        if (batchPets.isEmpty()) {
            pets = List.of();
        } else {
            List<PetSummary> copies = new ArrayList<>(batchPets.size());
            for (PetComponent component : batchPets) {
                if (component == null) {
                    continue;
                }
                MobEntity entity = component.getPetEntity();
                UUID petId = entity != null ? entity.getUuid() : null;
                Identifier roleId = component.getRoleId();
                int level = component.getLevel();
                long lastAttackTick = component.getLastAttackTick();
                boolean perched = component.isPerched();
                Map<String, Long> cooldowns = component.copyCooldownSnapshot();
                Map<String, Long> sanitizedCooldowns = cooldowns.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(cooldowns);
                double x = Double.NaN;
                double y = Double.NaN;
                double z = Double.NaN;
                if (entity != null) {
                    x = entity.getX();
                    y = entity.getY();
                    z = entity.getZ();
                }
                copies.add(new PetSummary(petId, roleId, level, lastAttackTick, perched, x, y, z, sanitizedCooldowns));
            }
            pets = List.copyOf(copies);
        }

        return new OwnerBatchSnapshot(
            ownerId,
            snapshotTick,
            lastKnownOwnerId,
            dueEvents,
            Collections.unmodifiableMap(taskBuckets),
            pets
        );
    }

    public record TaskSnapshot(PetWorkScheduler.TaskType type,
                               @Nullable UUID petUuid,
                               @Nullable Identifier roleId,
                               long dueTick) {
    }

    public record PetSummary(UUID petUuid,
                              @Nullable Identifier roleId,
                              int level,
                              long lastAttackTick,
                              boolean perched,
                              double x,
                              double y,
                              double z,
                              Map<String, Long> cooldowns) {
    }
}
