package woflo.petsplus.state;

import net.minecraft.entity.mob.MobEntity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Schedules pet-specific upkeep buckets so work only runs when the next due
 * tick arrives.
 */
public final class PetWorkScheduler {

    public enum TaskType {
        INTERVAL,
        AURA,
        SUPPORT_POTION,
        PARTICLE,
        GOSSIP_DECAY
    }

    public static final class ScheduledTask {
        private final MobEntity pet;
        private final PetComponent component;
        private final TaskType type;
        private final long dueTick;
        private boolean cancelled;

        private ScheduledTask(MobEntity pet, PetComponent component, TaskType type, long dueTick) {
            this.pet = pet;
            this.component = component;
            this.type = type;
            this.dueTick = dueTick;
        }

        public MobEntity pet() {
            return pet;
        }

        public PetComponent component() {
            return component;
        }

        public TaskType type() {
            return type;
        }

        public long dueTick() {
            return dueTick;
        }
    }

    private final NavigableMap<Long, List<ScheduledTask>> buckets = new TreeMap<>();
    private final Map<PetComponent, EnumMap<TaskType, ScheduledTask>> tasksByComponent = new IdentityHashMap<>();

    public synchronized void schedule(PetComponent component, TaskType type, long tick) {
        if (component == null) {
            return;
        }

        EnumMap<TaskType, ScheduledTask> existing = tasksByComponent.computeIfAbsent(component, c -> new EnumMap<>(TaskType.class));
        ScheduledTask current = existing.remove(type);
        if (current != null) {
            current.cancelled = true;
            component.onTaskUnschedule(type);
            if (existing.isEmpty()) {
                tasksByComponent.remove(component);
            }
        }

        if (tick == Long.MAX_VALUE) {
            return;
        }

        long sanitized = Math.max(0L, tick);
        ScheduledTask task = new ScheduledTask(component.getPetEntity(), component, type, sanitized);
        existing = tasksByComponent.computeIfAbsent(component, c -> new EnumMap<>(TaskType.class));
        existing.put(type, task);
        buckets.computeIfAbsent(sanitized, ignored -> new ArrayList<>()).add(task);
        component.onTaskScheduled(type, sanitized);
    }

    public synchronized void unscheduleAll(PetComponent component) {
        EnumMap<TaskType, ScheduledTask> tasks = tasksByComponent.remove(component);
        if (tasks == null) {
            return;
        }
        for (ScheduledTask task : tasks.values()) {
            task.cancelled = true;
            component.onTaskUnschedule(task.type);
        }
    }

    public synchronized void processDue(long currentTick, Consumer<ScheduledTask> consumer) {
        var iterator = buckets.entrySet().iterator();
        List<ScheduledTask> tasksToProcess = new ArrayList<>();

        // First pass: collect all due tasks and remove buckets
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey() > currentTick) {
                break;
            }
            iterator.remove();
            List<ScheduledTask> tasks = entry.getValue();
            for (ScheduledTask task : tasks) {
                if (!task.cancelled) {
                    tasksToProcess.add(task);
                }
            }
        }

        // Second pass: clean up task mappings and notify
        for (ScheduledTask task : tasksToProcess) {
            EnumMap<TaskType, ScheduledTask> map = tasksByComponent.get(task.component);
            if (map != null) {
                map.remove(task.type);
                if (map.isEmpty()) {
                    tasksByComponent.remove(task.component);
                }
            }
            task.component.onTaskUnschedule(task.type);
        }

        // Third pass: execute consumers outside of synchronized modification
        for (ScheduledTask task : tasksToProcess) {
            consumer.accept(task);
        }
    }
}
