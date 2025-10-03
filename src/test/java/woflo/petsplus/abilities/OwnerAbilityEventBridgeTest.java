package woflo.petsplus.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;

import net.minecraft.server.network.ServerPlayerEntity;

import org.junit.jupiter.api.Test;

import sun.misc.Unsafe;

import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.processing.AsyncWorkCoordinator;
import woflo.petsplus.state.processing.OwnerBatchSnapshot;
import woflo.petsplus.state.processing.OwnerEventFrame;
import woflo.petsplus.state.processing.OwnerEventType;
import woflo.petsplus.state.processing.OwnerTaskBatch;

class OwnerAbilityEventBridgeTest {

    @Test
    void asyncRejectionFallsBackToSynchronousDispatch() throws Exception {
        AsyncWorkCoordinator coordinator = throttledCoordinator();
        StateManager manager = allocate(StateManager.class);

        setStateManagerField(manager, "asyncWorkCoordinator", coordinator);

        UUID ownerId = UUID.randomUUID();
        PetComponent pet = allocate(PetComponent.class);
        List<PetComponent> pets = List.of(pet);

        OwnerTaskBatch batch = OwnerTaskBatch.adHoc(ownerId, pets, EnumSet.of(OwnerEventType.ABILITY_TRIGGER), 42L, null);
        OwnerBatchSnapshot snapshot = emptySnapshot(ownerId);
        AbilityTriggerPayload payload = AbilityTriggerPayload.of("test_trigger", Map.of("value", 1));

        OwnerEventFrame frame = frameWithPets(pets, ownerId, payload);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<PetComponent>> dispatchedPets = new AtomicReference<>();
        AtomicReference<String> dispatchedTrigger = new AtomicReference<>();
        AtomicReference<Map<String, Object>> dispatchedData = new AtomicReference<>();

        OwnerAbilityEventBridge.setAbilityExecutorForTesting((eventWorld, owner, eventPets, trigger, data) -> {
            dispatchedPets.set(eventPets);
            dispatchedTrigger.set(trigger);
            dispatchedData.set(data);
            latch.countDown();
            return AbilityTriggerResult.empty();
        });

        try {
            Method method = OwnerAbilityEventBridge.class.getDeclaredMethod(
                "dispatchAsync",
                StateManager.class,
                OwnerEventFrame.class,
                OwnerBatchSnapshot.class,
                AbilityTriggerPayload.class,
                String.class
            );
            method.setAccessible(true);

            method.invoke(OwnerAbilityEventBridge.INSTANCE, manager, frame, snapshot, payload, "test_trigger");

            assertTrue(latch.await(1, TimeUnit.SECONDS), "Fallback dispatcher should run synchronously");

            assertEquals("test_trigger", dispatchedTrigger.get(), "Fallback should reuse trigger id");
            assertEquals(pets, dispatchedPets.get(), "Fallback should retain pet list snapshot");
            assertEquals(payload.eventData(), dispatchedData.get(), "Fallback should reuse payload data");
        } finally {
            OwnerAbilityEventBridge.setAbilityExecutorForTesting(null);
            batch.close();
            coordinator.close();
        }
    }

    private static OwnerBatchSnapshot emptySnapshot(UUID ownerId) throws Exception {
        Constructor<OwnerBatchSnapshot> constructor = OwnerBatchSnapshot.class.getDeclaredConstructor(
            UUID.class,
            long.class,
            UUID.class,
            java.util.Set.class,
            Map.class,
            List.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
            ownerId,
            21L,
            null,
            java.util.Set.of(OwnerEventType.ABILITY_TRIGGER),
            Map.of(),
            List.of()
        );
    }

    private static AsyncWorkCoordinator throttledCoordinator() throws Exception {
        Constructor<AsyncWorkCoordinator> constructor = AsyncWorkCoordinator.class.getDeclaredConstructor(
            DoubleSupplier.class,
            Executor.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance((DoubleSupplier) () -> 2.0D, (Executor) Runnable::run);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe().allocateInstance(type));
    }

    private static void setStateManagerField(Object target, String name, Object value) throws Exception {
        Unsafe unsafe = unsafe();
        Field field = StateManager.class.getDeclaredField(name);
        field.setAccessible(true);
        unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
    }

    private static OwnerEventFrame frameWithPets(List<PetComponent> pets,
                                                 UUID ownerId,
                                                 AbilityTriggerPayload payload) throws Exception {
        Constructor<OwnerEventFrame> constructor = OwnerEventFrame.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        OwnerEventFrame frame = constructor.newInstance();

        setFrameField(frame, "eventType", OwnerEventType.ABILITY_TRIGGER);
        setFrameField(frame, "owner", (ServerPlayerEntity) null);
        setFrameField(frame, "ownerId", ownerId);
        setFrameField(frame, "pets", pets);
        setFrameField(frame, "payload", payload);

        return frame;
    }

    private static void setFrameField(OwnerEventFrame frame, String name, Object value) throws Exception {
        Field field = OwnerEventFrame.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(frame, value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
