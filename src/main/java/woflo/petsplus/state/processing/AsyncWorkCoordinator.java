package woflo.petsplus.state.processing;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import woflo.petsplus.Petsplus;

import woflo.petsplus.state.processing.AsyncProcessingTelemetry;

/**
 * Coordinates background work for owner-centric processing. Jobs are executed on a
 * bounded executor and their results are funneled back onto the main server thread
 * via a double-buffered queue that can be drained deterministically each tick.
 */
public final class AsyncWorkCoordinator implements AutoCloseable {
    private static final int MAX_IDLE_SECONDS = 30;
    private static final AtomicLong TASK_SEQUENCE = new AtomicLong();
    private static final Thread.UncaughtExceptionHandler ASYNC_EXCEPTION_HANDLER = (thread, throwable) ->
        Petsplus.LOGGER.error("Uncaught exception in async work thread {}", thread.getName(), throwable);

    private final DoubleSupplier loadFactorSupplier;
    private final ExecutorService executor;
    private final Executor mainThreadExecutor;
    private final AtomicInteger activeJobs = new AtomicInteger();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private final ConcurrentLinkedQueue<Runnable>[] resultBuffers;
    private final AtomicInteger[] bufferSizes;
    private final AtomicInteger writeBufferIndex = new AtomicInteger();
    private final int baseThreadCount;
    private final AsyncProcessingTelemetry telemetry = new AsyncProcessingTelemetry();
    private final AsyncWorkerBudget.Registration budgetRegistration;

    public AsyncWorkCoordinator(MinecraftServer server,
                                DoubleSupplier loadFactorSupplier) {
        this(server, loadFactorSupplier, null);
    }

    public AsyncWorkCoordinator(MinecraftServer server,
                                DoubleSupplier loadFactorSupplier,
                                @Nullable AsyncWorkerBudget.Registration budgetRegistration) {
        this(loadFactorSupplier, runnable -> Objects.requireNonNull(server, "server").submit(runnable), budgetRegistration);
    }

    @SuppressWarnings("unchecked")
    AsyncWorkCoordinator(DoubleSupplier loadFactorSupplier, Executor mainThreadExecutor) {
        this(loadFactorSupplier, mainThreadExecutor, null);
    }

    @SuppressWarnings("unchecked")
    AsyncWorkCoordinator(DoubleSupplier loadFactorSupplier,
                         Executor mainThreadExecutor,
                         @Nullable AsyncWorkerBudget.Registration budgetRegistration) {
        this.loadFactorSupplier = loadFactorSupplier != null ? loadFactorSupplier : () -> 1.0D;
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        int available = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.baseThreadCount = Math.min(4, Math.max(1, available));
        this.executor = createExecutor(this.baseThreadCount);
        this.resultBuffers = new ConcurrentLinkedQueue[] {
            new ConcurrentLinkedQueue<>(),
            new ConcurrentLinkedQueue<>()
        };
        this.bufferSizes = new AtomicInteger[] {
            new AtomicInteger(),
            new AtomicInteger()
        };
        this.budgetRegistration = budgetRegistration;
    }

    private ExecutorService createExecutor(int threads) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            threads,
            threads,
            MAX_IDLE_SECONDS,
            TimeUnit.SECONDS,
            new PriorityBlockingQueue<>(),
            Thread.ofPlatform()
                .daemon(true)
                .name("PetsPlus-Async-", 1)
                .uncaughtExceptionHandler(ASYNC_EXCEPTION_HANDLER)
                .factory()
        );
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    /**
     * Submit an owner batch job for background execution.
     *
     * @param snapshot immutable owner batch description
     * @param job asynchronous job to run
     * @param applier synchronous consumer for the job result (executed on the main thread)
     * @return future that completes once the synchronous applier finishes
     */
    public <T> CompletableFuture<T> submitOwnerBatch(OwnerBatchSnapshot snapshot,
                                                     OwnerBatchJob<T> job,
                                                     @Nullable Consumer<T> applier) {
        return submitOwnerBatch(snapshot, job, applier, AsyncJobPriority.CRITICAL);
    }

    public <T> CompletableFuture<T> submitOwnerBatch(OwnerBatchSnapshot snapshot,
                                                     OwnerBatchJob<T> job,
                                                     @Nullable Consumer<T> applier,
                                                     AsyncJobPriority priority) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(job, "job");
        AsyncJobPriority effectivePriority = priority == null ? AsyncJobPriority.CRITICAL : priority;

        int allowedThreads = computeAllowedThreads();
        if (allowedThreads <= 0) {
            return rejectThrottled("Server TPS is too low for async work");
        }

        int maxJobs = computeMaxJobs(allowedThreads);
        SlotReservation reservation = tryAcquireSlot(maxJobs);
        if (reservation == null) {
            return rejectThrottled("Async job queue is at capacity");
        }

        telemetry.recordActiveJobs(activeJobs.get());
        CompletableFuture<T> completion = new CompletableFuture<>();
        TrackedTask task = wrap(
            effectivePriority,
            () -> runJob(snapshot, job, applier, completion, reservation),
            reservation,
            completion
        );
        try {
            executor.execute(task);
            AsyncProcessingTelemetry.TASKS_ENQUEUED.incrementAndGet();
        } catch (RejectedExecutionException ex) {
            task.cancel(ex);
            return completion;
        }
        return completion;
    }

    private TrackedTask wrap(AsyncJobPriority priority,
                             Runnable delegate,
                             SlotReservation reservation,
                             CompletableFuture<?> completion) {
        return new TrackedTask(this,
            priority == null ? AsyncJobPriority.NORMAL : priority,
            TASK_SEQUENCE.incrementAndGet(), delegate, reservation, completion);
    }

    /**
     * Submit a non-owner-scoped task to the background executor while still
     * respecting the coordinator's load-based throttling and main-thread
     * application semantics.
     */
    public <T> CompletableFuture<T> submitStandalone(String descriptor,
                                                     Callable<T> job,
                                                     @Nullable Consumer<T> applier) {
        return submitStandalone(descriptor, job, applier, AsyncJobPriority.NORMAL);
    }

    public <T> CompletableFuture<T> submitStandalone(String descriptor,
                                                     Callable<T> job,
                                                     @Nullable Consumer<T> applier,
                                                     AsyncJobPriority priority) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(job, "job");
        AsyncJobPriority effectivePriority = priority == null ? AsyncJobPriority.NORMAL : priority;

        int allowedThreads = computeAllowedThreads();
        if (allowedThreads <= 0) {
            return rejectThrottled("Standalone async task '" + descriptor + "' rejected: Server TPS is too low for async work");
        }

        int maxJobs = computeMaxJobs(allowedThreads);
        SlotReservation reservation = tryAcquireSlot(maxJobs);
        if (reservation == null) {
            return rejectThrottled("Standalone async task '" + descriptor + "' rejected: async job queue is at capacity");
        }

        telemetry.recordActiveJobs(activeJobs.get());
        CompletableFuture<T> completion = new CompletableFuture<>();
        TrackedTask task = wrap(
            effectivePriority,
            () -> runStandalone(descriptor, job, applier, completion, reservation),
            reservation,
            completion
        );
        try {
            executor.execute(task);
            AsyncProcessingTelemetry.TASKS_ENQUEUED.incrementAndGet();
        } catch (RejectedExecutionException ex) {
            task.cancel(ex);
            return completion;
        }
        return completion;
    }

    private <T> void runJob(OwnerBatchSnapshot snapshot,
                            OwnerBatchJob<T> job,
                            @Nullable Consumer<T> applier,
                            CompletableFuture<T> completion,
                            SlotReservation reservation) {
        long start = System.nanoTime();
        T result = null;
        Throwable failure = null;
        try {
            result = job.run(snapshot);
        } catch (Throwable throwable) {
            failure = throwable;
        }
        long duration = System.nanoTime() - start;
        telemetry.recordAsyncDuration(duration);
        AsyncProcessingTelemetry.TASKS_EXECUTED.incrementAndGet();

        try {
            if (failure != null) {
                Throwable finalFailure = failure;
                enqueueResult(() -> {
                    long applyStart = System.nanoTime();
                    try {
                        completion.completeExceptionally(finalFailure);
                    } finally {
                        telemetry.recordApplyDuration(System.nanoTime() - applyStart);
                    }
                });
            } else {
                T finalResult = result;
                enqueueResult(() -> {
                    long applyStart = System.nanoTime();
                    try {
                        if (applier != null) {
                            applier.accept(finalResult);
                        }
                        completion.complete(finalResult);
                    } catch (Throwable applyError) {
                        completion.completeExceptionally(applyError);
                        throw applyError;
                    } finally {
                        telemetry.recordApplyDuration(System.nanoTime() - applyStart);
                    }
                });
            }
        } finally {
            reservation.close();
            activeJobs.decrementAndGet();
            telemetry.recordActiveJobs(activeJobs.get());
            scheduleDrain();
        }
    }

    private <T> void runStandalone(String descriptor,
                                   Callable<T> job,
                                   @Nullable Consumer<T> applier,
                                   CompletableFuture<T> completion,
                                   SlotReservation reservation) {
        long start = System.nanoTime();
        T result = null;
        Throwable failure = null;
        try {
            result = job.call();
        } catch (Throwable throwable) {
            failure = throwable;
        }
        long duration = System.nanoTime() - start;
        telemetry.recordAsyncDuration(duration);
        AsyncProcessingTelemetry.TASKS_EXECUTED.incrementAndGet();

        try {
            if (failure != null) {
                Throwable finalFailure = failure;
                enqueueResult(() -> {
                    long applyStart = System.nanoTime();
                    try {
                        completion.completeExceptionally(finalFailure);
                    } finally {
                        telemetry.recordApplyDuration(System.nanoTime() - applyStart);
                    }
                });
            } else {
                T finalResult = result;
                enqueueResult(() -> {
                    long applyStart = System.nanoTime();
                    try {
                        if (applier != null) {
                            applier.accept(finalResult);
                        }
                        completion.complete(finalResult);
                    } catch (Throwable applyError) {
                        completion.completeExceptionally(applyError);
                        throw applyError;
                    } finally {
                        telemetry.recordApplyDuration(System.nanoTime() - applyStart);
                    }
                });
            }
        } finally {
            reservation.close();
            activeJobs.decrementAndGet();
            telemetry.recordActiveJobs(activeJobs.get());
            scheduleDrain();
        }
    }

    private void enqueueResult(Runnable runnable) {
        int bufferIndex = writeBufferIndex.get();
        ConcurrentLinkedQueue<Runnable> queue = resultBuffers[bufferIndex];
        queue.add(runnable);
        int depth = bufferSizes[bufferIndex].incrementAndGet();
        telemetry.recordResultQueueDepth(depth);
    }

    private void scheduleDrain() {
        if (drainScheduled.compareAndSet(false, true)) {
            try {
                mainThreadExecutor.execute(() -> {
                    try {
                        drainMainThreadTasks();
                    } finally {
                        drainScheduled.set(false);
                    }
                });
            } catch (RejectedExecutionException ex) {
                drainScheduled.set(false);
                telemetry.recordRejectedSubmission();
                Petsplus.LOGGER.warn("Failed to schedule async drain on main thread", ex);
            }
        }
    }

    /**
     * Drain any queued main-thread callbacks. Should be invoked at the start of the
     * server tick to preserve deterministic ordering with synchronous work.
     *
     * @return number of callbacks executed
     */
    public int drainMainThreadTasks() {
        int bufferToDrain = writeBufferIndex.getAndUpdate(idx -> 1 - idx);
        ConcurrentLinkedQueue<Runnable> queue = resultBuffers[bufferToDrain];
        AtomicInteger bufferSize = bufferSizes[bufferToDrain];
        int drained = 0;
        Runnable task;

        // Time- and count-bounded draining to avoid long stalls on the main thread
        double load = loadFactorSupplier != null ? loadFactorSupplier.getAsDouble() : 1.0D;
        if (load <= 0.0D) load = 1.0D;
        int maxTasks = Math.max(8, (int) Math.round(64 / Math.min(2.0D, load))); // 64 @ normal, 32 @ 2x load
        long budgetNanos = (long) Math.max(500_000L, Math.round(2_000_000L / Math.min(2.0D, load))); // ~2ms budget
        long start = System.nanoTime();

        while (drained < maxTasks && (System.nanoTime() - start) < budgetNanos && (task = queue.poll()) != null) {
            try {
                task.run();
            } catch (Throwable throwable) {
                Petsplus.LOGGER.error("Async result application failed", throwable);
            }
            drained++;
        }
        if (drained > 0) {
            int remaining = bufferSize.addAndGet(-drained);
            if (remaining < 0) {
                bufferSize.set(0);
            }
        }
        telemetry.recordDrainBatchSize(drained);
        return drained;
    }

    private int computeAllowedThreads() {
        double factor = loadFactorSupplier.getAsDouble();
        if (factor >= 1.75D) {
            return 0;
        }
        if (factor >= 1.35D) {
            int throttled = Math.max(1, baseThreadCount / 2);
            return clampToBudget(throttled);
        }
        return clampToBudget(baseThreadCount);
    }

    private int computeMaxJobs(int allowedThreads) {
        if (allowedThreads <= 0) {
            return 0;
        }
        return allowedThreads + Math.max(1, allowedThreads * 2);
    }

    @Nullable
    private SlotReservation tryAcquireSlot(int maxJobs) {
        if (maxJobs <= 0) {
            return null;
        }
        int budgetLimit = Integer.MAX_VALUE;
        if (budgetRegistration != null) {
            budgetLimit = budgetRegistration.permittedSlots();
            if (budgetLimit <= 0) {
                return null;
            }
        }
        int queueLimit = Math.min(maxJobs, budgetLimit);
        if (queueLimit <= 0) {
            return null;
        }
        while (true) {
            int current = activeJobs.get();
            if (current >= queueLimit) {
                return null;
            }
            if (activeJobs.compareAndSet(current, current + 1)) {
                if (budgetRegistration != null && !budgetRegistration.tryClaimSlot()) {
                    activeJobs.decrementAndGet();
                    return null;
                }
                return new SlotReservation(budgetRegistration);
            }
        }
    }

    private <T> CompletableFuture<T> rejectThrottled(String message) {
        AsyncProcessingTelemetry.TASKS_DROPPED.incrementAndGet();
        telemetry.recordThrottledSubmission();
        return rejectedFuture(message);
    }

    private static <T> CompletableFuture<T> rejectedFuture(String message) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new RejectedExecutionException(message));
        return future;
    }

    private int clampToBudget(int desired) {
        if (budgetRegistration == null) {
            return desired;
        }
        return Math.min(desired, budgetRegistration.permittedSlots());
    }

    @Override
    public void close() {
        // Initiate graceful shutdown
        executor.shutdown();
        try {
            // Wait for tasks to complete with shorter timeout (most tasks are fast)
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                // Force shutdown if tasks don't complete in time
                List<Runnable> aborted = executor.shutdownNow();
                if (!aborted.isEmpty()) {
                    RejectedExecutionException cause = new RejectedExecutionException("Async coordinator closed before task start");
                    for (Runnable runnable : aborted) {
                        if (runnable instanceof TrackedTask task) {
                            task.cancel(cause);
                        }
                    }
                    Petsplus.LOGGER.debug("Async coordinator aborted {} pending tasks during shutdown", aborted.size());
                }
                // Wait a bit more for forceful shutdown
                if (!executor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    Petsplus.LOGGER.warn("Async coordinator executor did not terminate gracefully after 1.5s");
                }
            }
        } catch (InterruptedException e) {
            // Restore interrupt status and force shutdown
            Thread.currentThread().interrupt();
            List<Runnable> aborted = executor.shutdownNow();
            if (!aborted.isEmpty()) {
                RejectedExecutionException cause = new RejectedExecutionException("Async coordinator interrupted during shutdown");
                for (Runnable runnable : aborted) {
                    if (runnable instanceof TrackedTask task) {
                        task.cancel(cause);
                    }
                }
            }
        }
        if (budgetRegistration != null) {
            budgetRegistration.close();
        }
    }

    public AsyncProcessingTelemetry telemetry() {
        return telemetry;
    }

    private static final class SlotReservation implements AutoCloseable {
        private final @Nullable AsyncWorkerBudget.Registration registration;
        private final AtomicBoolean released = new AtomicBoolean();

        private SlotReservation(@Nullable AsyncWorkerBudget.Registration registration) {
            this.registration = registration;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true) && registration != null) {
                registration.releaseSlot();
            }
        }
    }

    @FunctionalInterface
    public interface OwnerBatchJob<T> {
        T run(OwnerBatchSnapshot snapshot) throws Exception;
    }

    private static final class TrackedTask implements Runnable, Comparable<TrackedTask> {
        private final AsyncWorkCoordinator owner;
        private final AsyncJobPriority priority;
        private final long sequence;
        private final Runnable delegate;
        private final SlotReservation reservation;
        private final CompletableFuture<?> completion;
        private final AtomicBoolean cancelled;

        private TrackedTask(AsyncWorkCoordinator owner,
                            @Nullable AsyncJobPriority priority,
                            long sequence,
                            Runnable delegate,
                            SlotReservation reservation,
                            CompletableFuture<?> completion) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.priority = priority == null ? AsyncJobPriority.NORMAL : priority;
            this.sequence = sequence;
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.reservation = Objects.requireNonNull(reservation, "reservation");
            this.completion = Objects.requireNonNull(completion, "completion");
            this.cancelled = new AtomicBoolean();
        }

        @Override
        public void run() {
            delegate.run();
        }

        @Override
        public int compareTo(TrackedTask other) {
            if (other == this) {
                return 0;
            }
            int priorityCompare = Integer.compare(other.priority.weight(), this.priority.weight());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Long.compare(this.sequence, other.sequence);
        }

        private void cancel(@Nullable Throwable cause) {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            try {
                reservation.close();
            } finally {
                owner.activeJobs.decrementAndGet();
                owner.telemetry.recordActiveJobs(owner.activeJobs.get());
                owner.telemetry.recordRejectedSubmission();
                AsyncProcessingTelemetry.TASKS_DROPPED.incrementAndGet();
                if (cause == null) {
                    cause = new RejectedExecutionException("Async task cancelled");
                }
                completion.completeExceptionally(cause);
            }
        }
    }
}
