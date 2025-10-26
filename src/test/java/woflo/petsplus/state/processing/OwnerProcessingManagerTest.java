package woflo.petsplus.state.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Map;
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

        Map<OwnerProcessingGroup, Object> initialIndex = eventDueIndex(manager);
        assertEquals(groupCount, initialIndex.size());

        for (int i = 0; i < groupCount; i++) {
            manager.signalEvent(owners[i], OwnerEventType.INTERVAL, 2_000L + i);
        }

        Map<OwnerProcessingGroup, Object> rescheduledIndex = eventDueIndex(manager);
        assertEquals(groupCount, rescheduledIndex.size());
        for (Map.Entry<OwnerProcessingGroup, Object> entry : rescheduledIndex.entrySet()) {
            assertEquals(false, ticketCancelled(entry.getValue()));
        }

        manager.prepareForTick(3_000L);
        manager.preparePendingGroups(3_000L);

        assertEquals(0, eventDueIndex(manager).size());
    }

    @SuppressWarnings("unchecked")
    private static Map<OwnerProcessingGroup, Object> eventDueIndex(OwnerProcessingManager manager)
        throws Exception {
        Field field = OwnerProcessingManager.class.getDeclaredField("eventDueIndex");
        field.setAccessible(true);
        return (Map<OwnerProcessingGroup, Object>) field.get(manager);
    }

    private static long ticketTick(Object ticket) throws Exception {
        Field tickField = ticket.getClass().getDeclaredField("tick");
        tickField.setAccessible(true);
        return tickField.getLong(ticket);
    }

    private static boolean ticketCancelled(Object ticket) throws Exception {
        Field cancelledField = ticket.getClass().getDeclaredField("cancelled");
        cancelledField.setAccessible(true);
        return cancelledField.getBoolean(ticket);
    }
}
