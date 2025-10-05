package woflo.petsplus.state.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import woflo.petsplus.state.coordination.PetWorkScheduler;
import woflo.petsplus.state.processing.AsyncProcessingTelemetry;

class AsyncWorkCoordinatorTest {

    private final AsyncWorkCoordinator coordinator = new AsyncWorkCoordinator(() -> 1.0D, Runnable::run);

    @AfterEach
    void shutdown() {
        coordinator.close();
    }

    @Test
    void applierRunsEvenWhenJobReturnsNull() throws Exception {
        OwnerBatchSnapshot snapshot = emptySnapshot();
        AtomicBoolean applied = new AtomicBoolean(false);

        CompletableFuture<?> future = coordinator.submitOwnerBatch(
            snapshot,
            ignored -> null,
            ignored -> applied.set(true)
        );

        try {
            future.get();
        } catch (ExecutionException ex) {
            throw new AssertionError("Unexpected async failure", ex.getCause());
        }

        assertTrue(applied.get(), "Applier should run even when async job returns null");
    }

    @Test
    void rejectedExecutionCompletesFutureExceptionally() throws Exception {
        AsyncWorkCoordinator shuttingDown = new AsyncWorkCoordinator(() -> 1.0D, Runnable::run);
        shuttingDown.close();

        CompletableFuture<?> future = shuttingDown.submitOwnerBatch(
            emptySnapshot(),
            ignored -> null,
            ignored -> {}
        );

        ExecutionException failure = assertThrows(ExecutionException.class, future::get);
        assertTrue(failure.getCause() instanceof RejectedExecutionException,
            "Expected RejectedExecutionException when executor is shut down");

        AsyncProcessingTelemetry.TelemetrySnapshot snapshot = shuttingDown.telemetry().snapshotAndReset();
        assertEquals(0L, snapshot.throttledSubmissions(), "No throttles expected when executor rejects directly");
        assertEquals(1L, snapshot.rejectedSubmissions(), "Executor rejection should be recorded");
    }

    @Test
    void throttledSubmissionRecordedWhenLoadFactorTooHigh() throws Exception {
        AsyncWorkCoordinator throttled = new AsyncWorkCoordinator(() -> 2.0D, Runnable::run);

        CompletableFuture<?> future = throttled.submitOwnerBatch(
            emptySnapshot(),
            ignored -> null,
            ignored -> {}
        );

        ExecutionException failure = assertThrows(ExecutionException.class, future::get);
        assertTrue(failure.getCause() instanceof RejectedExecutionException,
            "Expected rejection when load factor disallows async work");

        AsyncProcessingTelemetry.TelemetrySnapshot snapshot = throttled.telemetry().snapshotAndReset();
        assertEquals(1L, snapshot.throttledSubmissions(), "Throttle should be recorded");
        assertEquals(0L, snapshot.rejectedSubmissions(), "No executor rejection expected");

        throttled.close();
    }

    @Test
    void standaloneThrottleIncludesDescriptorInFailureMessage() throws Exception {
        AsyncWorkCoordinator throttled = new AsyncWorkCoordinator(() -> 2.0D, Runnable::run);

        CompletableFuture<?> future = throttled.submitStandalone(
            "mood-score",
            () -> null,
            ignored -> {}
        );

        ExecutionException failure = assertThrows(ExecutionException.class, future::get);
        assertTrue(failure.getCause() instanceof RejectedExecutionException,
            "Expected rejection when load factor disallows async work");
        assertTrue(failure.getCause().getMessage().contains("mood-score"),
            "Descriptor should be present in rejection message");

        AsyncProcessingTelemetry.TelemetrySnapshot snapshot = throttled.telemetry().snapshotAndReset();
        assertEquals(1L, snapshot.throttledSubmissions(), "Throttle should be recorded");
        assertEquals(0L, snapshot.rejectedSubmissions(), "Executor rejection expected to be zero");

        throttled.close();
    }

    private static OwnerBatchSnapshot emptySnapshot() throws Exception {
        Constructor<OwnerBatchSnapshot> constructor = OwnerBatchSnapshot.class.getDeclaredConstructor(
            UUID.class,
            long.class,
            UUID.class,
            Set.class,
            java.util.Map.class,
            List.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
            UUID.randomUUID(),
            0L,
            null,
            Collections.emptySet(),
            new EnumMap<>(PetWorkScheduler.TaskType.class),
            List.of()
        );
    }
}

