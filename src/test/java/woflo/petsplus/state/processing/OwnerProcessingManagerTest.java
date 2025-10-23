package woflo.petsplus.state.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerProcessingManagerTest {

    @Test
    void reschedulingKeepsDueQueueIndexedWithoutDuplicates() throws Exception {
        OwnerProcessingManager manager = new OwnerProcessingManager();
        int groupCount = 512;
        UUID[] owners = new UUID[groupCount];

        for (int i = 0; i < groupCount; i++) {
            UUID ownerId = new UUID(0L, i + 1L);
            owners[i] = ownerId;
            manager.signalEvent(ownerId, OwnerEventType.INTERVAL, 1_000L + i);
        }

        Map<Long, LinkedHashSet<OwnerProcessingGroup>> initialQueue = eventDueQueue(manager);
        Map<OwnerProcessingGroup, Long> initialIndex = eventDueIndex(manager);

        assertEquals(groupCount, totalGroups(initialQueue));
        assertEquals(groupCount, initialIndex.size());

        for (int i = 0; i < groupCount; i++) {
            manager.signalEvent(owners[i], OwnerEventType.INTERVAL, 2_000L + i);
        }

        Map<Long, LinkedHashSet<OwnerProcessingGroup>> rescheduledQueue = eventDueQueue(manager);
        Map<OwnerProcessingGroup, Long> rescheduledIndex = eventDueIndex(manager);

        assertEquals(groupCount, totalGroups(rescheduledQueue));
        assertEquals(groupCount, rescheduledIndex.size());
        for (Map.Entry<OwnerProcessingGroup, Long> entry : rescheduledIndex.entrySet()) {
            LinkedHashSet<OwnerProcessingGroup> bucket = rescheduledQueue.get(entry.getValue());
            assertTrue(bucket != null && bucket.contains(entry.getKey()));
        }

        manager.prepareForTick(3_000L);
        manager.preparePendingGroups(3_000L);

        assertTrue(eventDueQueue(manager).isEmpty());
        assertEquals(0, eventDueIndex(manager).size());
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, LinkedHashSet<OwnerProcessingGroup>> eventDueQueue(OwnerProcessingManager manager)
        throws Exception {
        Field field = OwnerProcessingManager.class.getDeclaredField("eventDueQueue");
        field.setAccessible(true);
        return (Map<Long, LinkedHashSet<OwnerProcessingGroup>>) field.get(manager);
    }

    @SuppressWarnings("unchecked")
    private static Map<OwnerProcessingGroup, Long> eventDueIndex(OwnerProcessingManager manager)
        throws Exception {
        Field field = OwnerProcessingManager.class.getDeclaredField("eventDueIndex");
        field.setAccessible(true);
        return (Map<OwnerProcessingGroup, Long>) field.get(manager);
    }

    private static int totalGroups(Map<Long, ? extends Set<OwnerProcessingGroup>> queue) {
        return queue.values().stream().mapToInt(Set::size).sum();
    }
}
