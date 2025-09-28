package woflo.petsplus.state.processing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetWorkScheduler;
import woflo.petsplus.state.processing.OwnerTaskBatch;

class OwnerProcessingManagerTest {

    @Test
    void untrackPetRemovesEmptyGroupAfterDrain() throws Exception {
        OwnerProcessingManager manager = new OwnerProcessingManager();
        UUID ownerId = UUID.randomUUID();

        PetComponent component = mock(PetComponent.class);
        when(component.getOwnerUuid()).thenReturn(ownerId);
        when(component.getPetEntity()).thenReturn(null);

        OwnerProcessingGroup group = new OwnerProcessingGroup(ownerId);
        injectGroup(manager, group);
        injectMembership(manager, component, ownerId);

        PetWorkScheduler.ScheduledTask task = mock(PetWorkScheduler.ScheduledTask.class);
        when(task.component()).thenReturn(component);
        when(task.type()).thenReturn(PetWorkScheduler.TaskType.INTERVAL);

        group.enqueue(task);
        try (OwnerTaskBatch batch = group.drain(0L)) {
            // drain to hand the list to the batch; closing recycles it
        }

        manager.untrackPet(component);

        Map<UUID, ?> groups = extractGroups(manager);
        assertFalse(groups.containsKey(ownerId), "Owner group should be removed after last pet is untracked");
    }

    @Test
    void shutdownClearsManagerState() throws Exception {
        OwnerProcessingManager manager = new OwnerProcessingManager();
        UUID ownerId = UUID.randomUUID();

        PetComponent component = mock(PetComponent.class);
        OwnerProcessingGroup group = new OwnerProcessingGroup(ownerId);
        injectGroup(manager, group);
        injectMembership(manager, component, ownerId);

        assertFalse(extractGroups(manager).isEmpty(), "Precondition: groups should contain owner before shutdown");
        assertFalse(extractMembership(manager).isEmpty(), "Precondition: membership should contain entries before shutdown");

        manager.shutdown();

        assertTrue(extractGroups(manager).isEmpty(), "Groups should be cleared during shutdown");
        assertTrue(extractMembership(manager).isEmpty(), "Membership should be cleared during shutdown");
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ?> extractGroups(OwnerProcessingManager manager) throws Exception {
        Field field = OwnerProcessingManager.class.getDeclaredField("groups");
        field.setAccessible(true);
        return (Map<UUID, ?>) field.get(manager);
    }

    private static void injectGroup(OwnerProcessingManager manager, OwnerProcessingGroup group) throws Exception {
        Map<UUID, OwnerProcessingGroup> groups = (Map<UUID, OwnerProcessingGroup>) extractGroups(manager);
        groups.put(group.ownerId(), group);
    }

    @SuppressWarnings("unchecked")
    private static void injectMembership(OwnerProcessingManager manager, PetComponent component, UUID ownerId) throws Exception {
        Field field = OwnerProcessingManager.class.getDeclaredField("membership");
        field.setAccessible(true);
        Map<PetComponent, UUID> membership = (Map<PetComponent, UUID>) field.get(manager);
        membership.put(component, ownerId);
    }

    @SuppressWarnings("unchecked")
    private static Map<PetComponent, UUID> extractMembership(OwnerProcessingManager manager) throws Exception {
        Field field = OwnerProcessingManager.class.getDeclaredField("membership");
        field.setAccessible(true);
        return (Map<PetComponent, UUID>) field.get(manager);
    }
}
