package woflo.petsplus.state.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.entity.mob.MobEntity;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetWorkScheduler;
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

    @Test
    @SuppressWarnings("unchecked")
    void markPetChangedReassignsOwnershipAndCleansUpOldGroup() throws Exception {
        OwnerProcessingManager manager = new OwnerProcessingManager();
        UUID originalOwner = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();

        PetComponent component = mock(PetComponent.class);
        MobEntity petEntity = mock(MobEntity.class);
        when(component.getPetEntity()).thenReturn(petEntity);
        when(component.getOwnerUuid()).thenReturn(originalOwner);

        manager.trackPet(component);
        manager.onTaskScheduled(component, PetWorkScheduler.TaskType.INTERVAL, 5L);

        Map<UUID, OwnerProcessingGroup> groups = (Map<UUID, OwnerProcessingGroup>) extractGroups(manager);
        OwnerProcessingGroup originalGroup = groups.get(originalOwner);
        assertNotNull(originalGroup, "Original owner group should be created");
        assertTrue(originalGroup.currentPets().contains(component), "Original group should track the pet");
        assertTrue(originalGroup.hasDueEvents(5L), "Original group should have a due event after scheduling");

        Map<PetWorkScheduler.TaskType, List<PetWorkScheduler.ScheduledTask>> pendingTasks =
            extractPendingTasks(originalGroup);
        PetWorkScheduler.ScheduledTask scheduledTask = mock(PetWorkScheduler.ScheduledTask.class);
        when(scheduledTask.component()).thenReturn(component);
        when(scheduledTask.type()).thenReturn(PetWorkScheduler.TaskType.INTERVAL);
        List<PetWorkScheduler.ScheduledTask> taskBucket = new ArrayList<>();
        taskBucket.add(scheduledTask);
        pendingTasks.put(PetWorkScheduler.TaskType.INTERVAL, taskBucket);
        assertTrue(originalGroup.hasPendingTasks(), "Original group should report pending tasks before transfer");

        when(component.getOwnerUuid()).thenReturn(newOwner);
        manager.markPetChanged(component, 10L);

        assertFalse(originalGroup.currentPets().contains(component), "Pet should be removed from the original group");
        assertFalse(originalGroup.hasDueEvents(10L), "Original group should not retain due events after transfer");
        assertFalse(originalGroup.hasPendingTasks(), "Original group should drop pending tasks after transfer");
        groups = (Map<UUID, OwnerProcessingGroup>) extractGroups(manager);
        assertFalse(groups.containsKey(originalOwner), "Original owner group should be recycled after transfer");

        OwnerProcessingGroup reassignedGroup = groups.get(newOwner);
        assertNotNull(reassignedGroup, "New owner group should be created on transfer");
        assertTrue(reassignedGroup.currentPets().contains(component), "Pet should now be tracked by the new owner");

        Map<PetComponent, UUID> membership = extractMembership(manager);
        assertEquals(newOwner, membership.get(component), "Membership should point to the new owner");

        when(component.getOwnerUuid()).thenReturn(null);
        manager.markPetChanged(component, 15L);

        assertFalse(reassignedGroup.currentPets().contains(component), "Pet should be removed from new owner when unassigned");
        assertFalse(reassignedGroup.hasDueEvents(15L), "New owner group should clear due events when emptied");
        groups = (Map<UUID, OwnerProcessingGroup>) extractGroups(manager);
        assertFalse(groups.containsKey(newOwner), "New owner group should be recycled when empty");

        membership = extractMembership(manager);
        assertFalse(membership.containsKey(component), "Membership should not retain pets without owners");
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

    @SuppressWarnings("unchecked")
    private static Map<PetWorkScheduler.TaskType, List<PetWorkScheduler.ScheduledTask>> extractPendingTasks(
        OwnerProcessingGroup group
    ) throws Exception {
        Field field = OwnerProcessingGroup.class.getDeclaredField("pendingTasks");
        field.setAccessible(true);
        return (Map<PetWorkScheduler.TaskType, List<PetWorkScheduler.ScheduledTask>>) field.get(group);
    }
}
