package woflo.petsplus.state;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import woflo.petsplus.state.processing.AsyncMigrationProgressTracker;
import woflo.petsplus.state.processing.AsyncWorkCoordinator;
import woflo.petsplus.state.processing.OwnerEventType;
import woflo.petsplus.state.processing.OwnerProcessingManager;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class StateManagerMigrationProgressTest {

    private MinecraftServer server;
    private ServerWorld world;

    @BeforeEach
    void setup() {
        AsyncMigrationProgressTracker.reset();
        StateManager.unloadAll();
        server = mock(MinecraftServer.class);
        when(server.submit(org.mockito.ArgumentMatchers.any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            if (runnable != null) {
                runnable.run();
            }
            return CompletableFuture.completedFuture(null);
        });
        when(server.submit(org.mockito.ArgumentMatchers.any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            Object result = supplier != null ? supplier.get() : null;
            return CompletableFuture.completedFuture(result);
        });

        world = mock(ServerWorld.class);
        when(world.getServer()).thenReturn(server);
        when(world.getTime()).thenReturn(200L);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
    }

    @AfterEach
    void cleanup() {
        StateManager.unloadAll();
        AsyncMigrationProgressTracker.reset();
    }

    @Test
    void requestEmotionEventMarksPhaseOne() {
        StateManager manager = StateManager.forWorld(world);
        ServerPlayerEntity owner = newOwner();

        try (MockedStatic<AsyncMigrationProgressTracker> tracker =
                 mockStatic(AsyncMigrationProgressTracker.class, CALLS_REAL_METHODS)) {
            tracker.when(() -> AsyncMigrationProgressTracker.markComplete(any())).thenCallRealMethod();

            manager.requestEmotionEvent(owner, null);

            tracker.verify(() -> AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.CORE_EMOTION));
            assertTrue(AsyncMigrationProgressTracker.isComplete(AsyncMigrationProgressTracker.Phase.CORE_EMOTION));
        }
    }

    @Test
    void handlePetTickMarksPhaseTwo() {
        StateManager manager = StateManager.forWorld(world);
        ServerPlayerEntity owner = newOwner();
        MobEntity pet = newPet();

        AsyncWorkCoordinator asyncCoordinator = mock(AsyncWorkCoordinator.class);
        when(asyncCoordinator.submitStandalone(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        setField(manager, "asyncWorkCoordinator", asyncCoordinator);

        OwnerProcessingManager ownerProcessingManager = mock(OwnerProcessingManager.class);
        doNothing().when(ownerProcessingManager).markPetChanged(any(PetComponent.class), anyLong());
        doNothing().when(ownerProcessingManager).signalEvent(any(PetComponent.class), any(OwnerEventType.class), anyLong());
        setField(manager, "ownerProcessingManager", ownerProcessingManager);

        PetComponent component = mock(PetComponent.class);
        doNothing().when(component).attachStateManager(manager);
        doNothing().when(component).ensureSchedulingInitialized(anyLong());
        when(component.snapshotSwarmState()).thenReturn(new PetComponent.SwarmStateSnapshot(false, Long.MIN_VALUE, 0.0, 0.0, 0.0));
        when(component.getOwner()).thenReturn(owner);
        when(component.getOwnerUuid()).thenReturn(owner.getUuid());
        doNothing().when(component).applySwarmUpdate(any(), anyLong(), anyDouble(), anyDouble(), anyDouble());

        insertPetComponent(manager, pet, component);

        try (MockedStatic<AsyncMigrationProgressTracker> tracker =
                 mockStatic(AsyncMigrationProgressTracker.class, CALLS_REAL_METHODS)) {
            tracker.when(() -> AsyncMigrationProgressTracker.markComplete(any())).thenCallRealMethod();

            manager.handlePetTick(pet);

            tracker.verify(() -> AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.PET_STATE));
            assertTrue(AsyncMigrationProgressTracker.isComplete(AsyncMigrationProgressTracker.Phase.PET_STATE));
        }
    }

    @Test
    void ownerTickAndScheduledTasksMarkRemainingPhases() {
        StateManager manager = StateManager.forWorld(world);
        ServerPlayerEntity owner = newOwner();

        AsyncWorkCoordinator asyncCoordinator = mock(AsyncWorkCoordinator.class);
        when(asyncCoordinator.drainMainThreadTasks()).thenReturn(0);
        setField(manager, "asyncWorkCoordinator", asyncCoordinator);

        OwnerProcessingManager ownerProcessingManager = mock(OwnerProcessingManager.class);
        when(ownerProcessingManager.preparePendingGroups(anyLong())).thenReturn(0);
        when(ownerProcessingManager.snapshotOwnerTick(any(), anyLong())).thenReturn(null);
        doNothing().when(ownerProcessingManager).prepareForTick(anyLong());
        doNothing().when(ownerProcessingManager).flushBatches(any(), anyLong(), anyInt());
        setField(manager, "ownerProcessingManager", ownerProcessingManager);

        try (MockedStatic<AsyncMigrationProgressTracker> tracker =
                 mockStatic(AsyncMigrationProgressTracker.class, CALLS_REAL_METHODS)) {
            tracker.when(() -> AsyncMigrationProgressTracker.markComplete(any())).thenCallRealMethod();

            manager.handleOwnerTick(owner);
            manager.processScheduledPetTasks(world.getTime());

            tracker.verify(() -> AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.OWNER_PROCESSING));
            tracker.verify(() -> AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.ADVANCED_SYSTEMS));
            assertTrue(AsyncMigrationProgressTracker.isComplete(AsyncMigrationProgressTracker.Phase.OWNER_PROCESSING));
            assertTrue(AsyncMigrationProgressTracker.isComplete(AsyncMigrationProgressTracker.Phase.ADVANCED_SYSTEMS));
        }
    }

    @Test
    void runningCoreSystemsCompletesAllPhases() {
        StateManager manager = StateManager.forWorld(world);
        ServerPlayerEntity owner = newOwner();
        MobEntity pet = newPet();

        AsyncWorkCoordinator asyncCoordinator = mock(AsyncWorkCoordinator.class);
        when(asyncCoordinator.submitStandalone(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(asyncCoordinator.drainMainThreadTasks()).thenReturn(0);
        setField(manager, "asyncWorkCoordinator", asyncCoordinator);

        OwnerProcessingManager ownerProcessingManager = mock(OwnerProcessingManager.class);
        when(ownerProcessingManager.snapshotOwnerTick(any(), anyLong())).thenReturn(null);
        doNothing().when(ownerProcessingManager).prepareForTick(anyLong());
        when(ownerProcessingManager.preparePendingGroups(anyLong())).thenReturn(0);
        doNothing().when(ownerProcessingManager).flushBatches(any(), anyLong(), anyInt());
        doNothing().when(ownerProcessingManager).markPetChanged(any(PetComponent.class), anyLong());
        doNothing().when(ownerProcessingManager).signalEvent(any(PetComponent.class), any(OwnerEventType.class), anyLong());
        when(ownerProcessingManager.createAdHocBatch(any(), any(), anyLong())).thenReturn(null);
        setField(manager, "ownerProcessingManager", ownerProcessingManager);

        setField(manager, "auraTargetResolver", mock(woflo.petsplus.effects.AuraTargetResolver.class));

        PetComponent component = mock(PetComponent.class);
        doNothing().when(component).attachStateManager(manager);
        doNothing().when(component).ensureSchedulingInitialized(anyLong());
        when(component.snapshotSwarmState()).thenReturn(new PetComponent.SwarmStateSnapshot(false, Long.MIN_VALUE, 0.0, 0.0, 0.0));
        when(component.getOwner()).thenReturn(owner);
        when(component.getOwnerUuid()).thenReturn(owner.getUuid());
        doNothing().when(component).applySwarmUpdate(any(), anyLong(), anyDouble(), anyDouble(), anyDouble());

        insertPetComponent(manager, pet, component);

        try (MockedStatic<AsyncMigrationProgressTracker> tracker =
                 mockStatic(AsyncMigrationProgressTracker.class, CALLS_REAL_METHODS)) {
            tracker.when(() -> AsyncMigrationProgressTracker.markComplete(any())).thenCallRealMethod();

            manager.requestEmotionEvent(owner, null);
            manager.handlePetTick(pet);
            manager.handleOwnerTick(owner);
            manager.processScheduledPetTasks(world.getTime());

            assertTrue(AsyncMigrationProgressTracker.allComplete());
        }
    }

    private ServerPlayerEntity newOwner() {
        ServerPlayerEntity owner = mock(ServerPlayerEntity.class);
        UUID ownerId = UUID.randomUUID();
        when(owner.isRemoved()).thenReturn(false);
        when(owner.isAlive()).thenReturn(true);
        when(owner.getEntityWorld()).thenReturn(world);
        when(owner.getUuid()).thenReturn(ownerId);
        when(owner.getUuidAsString()).thenReturn(ownerId.toString());
        return owner;
    }

    private MobEntity newPet() {
        MobEntity pet = mock(MobEntity.class);
        UUID petId = UUID.randomUUID();
        when(pet.getEntityWorld()).thenReturn(world);
        when(pet.isRemoved()).thenReturn(false);
        when(pet.getUuid()).thenReturn(petId);
        when(pet.getUuidAsString()).thenReturn(petId.toString());
        return pet;
    }

    @SuppressWarnings("unchecked")
    private static void insertPetComponent(StateManager manager, MobEntity pet, PetComponent component) {
        try {
            Field field = StateManager.class.getDeclaredField("petComponents");
            field.setAccessible(true);
            Map<MobEntity, PetComponent> map = (Map<MobEntity, PetComponent>) field.get(manager);
            map.put(pet, component);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void setField(StateManager manager, String name, Object value) {
        try {
            Field field = StateManager.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(manager, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
