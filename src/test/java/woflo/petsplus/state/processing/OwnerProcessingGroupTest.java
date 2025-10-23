package woflo.petsplus.state.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.mob.MobEntity;

import org.junit.jupiter.api.Test;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetWorkScheduler;

class OwnerProcessingGroupTest {

    @Test
    void moodTasksDrainIncrementallyWithoutDuplication() throws Exception {
        OwnerProcessingGroup group = new OwnerProcessingGroup(UUID.randomUUID());
        int totalTasks = 200;
        for (int i = 0; i < totalTasks; i++) {
            group.enqueue(moodTask(i));
        }

        Set<Long> delivered = new LinkedHashSet<>();
        int drained = 0;
        while (drained < totalTasks) {
            OwnerTaskBatch batch = group.drain(0L, 3);
            List<PetWorkScheduler.ScheduledTask> tasks =
                batch.tasksFor(PetWorkScheduler.TaskType.MOOD_PROVIDER);
            assertTrue(!tasks.isEmpty());
            for (PetWorkScheduler.ScheduledTask task : tasks) {
                delivered.add(task.dueTick());
            }
            drained += tasks.size();
            batch.close();

            assertEquals(totalTasks - drained, remainingMoodTasks(group));
        }

        assertEquals(totalTasks, delivered.size());
        assertEquals(0, remainingMoodTasks(group));
        assertTrue(delivered.contains(0L));
        assertTrue(delivered.contains((long) (totalTasks - 1)));
    }

    private static PetWorkScheduler.ScheduledTask moodTask(long dueTick) throws Exception {
        Constructor<PetWorkScheduler.ScheduledTask> ctor =
            PetWorkScheduler.ScheduledTask.class.getDeclaredConstructor(
                MobEntity.class,
                PetComponent.class,
                PetWorkScheduler.TaskType.class,
                long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(null, null, PetWorkScheduler.TaskType.MOOD_PROVIDER, dueTick);
    }

    @SuppressWarnings("unchecked")
    private static int remainingMoodTasks(OwnerProcessingGroup group) throws Exception {
        Field pendingField = OwnerProcessingGroup.class.getDeclaredField("pendingTasks");
        pendingField.setAccessible(true);
        EnumMap<PetWorkScheduler.TaskType, List<PetWorkScheduler.ScheduledTask>> pending =
            (EnumMap<PetWorkScheduler.TaskType, List<PetWorkScheduler.ScheduledTask>>) pendingField.get(group);
        if (pending == null) {
            return 0;
        }
        List<PetWorkScheduler.ScheduledTask> tasks = pending.get(PetWorkScheduler.TaskType.MOOD_PROVIDER);
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }
        Field offsetField = OwnerProcessingGroup.class.getDeclaredField("moodTaskOffset");
        offsetField.setAccessible(true);
        int offset = offsetField.getInt(group);
        return Math.max(0, tasks.size() - offset);
    }
}
