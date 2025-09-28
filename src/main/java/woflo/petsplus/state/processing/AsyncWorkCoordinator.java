package woflo.petsplus.state.processing;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
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
    private static final AtomicLong THREAD_COUNTER = new AtomicLong();

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

    public AsyncWorkCoordinator(MinecraftServer server,
                                DoubleSupplier loadFactorSupplier) {
        this(loadFactorSupplier, runnable -> Objects.requireNonNull(server, "server").submit(runnable));
    }

    @SuppressWarnings("unchecked")
    AsyncWorkCoordinator(DoubleSupplier loadFactorSupplier, Executor mainThreadExecutor) {
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
    }

    private ExecutorService createExecutor(int threads) {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("PetsPlus-Async-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            threads,
            threads,
            MAX_IDLE_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            factory
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
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(job, "job");

        int allowedThreads = computeAllowedThreads();
        if (allowedThreads <= 0) {
            return rejectThrottled("Server TPS is too low for async work");
        }

        int maxJobs = computeMaxJobs(allowedThreads);
        if (!tryAcquireSlot(maxJobs)) {
            return rejectThrottled("Async job queue is at capacity");
        }

        telemetry.recordActiveJobs(activeJobs.get());
        CompletableFuture<T> completion = new CompletableFuture<>();
        try {
            executor.execute(() -> runJob(snapshot, job, applier, completion));
        } catch (RejectedExecutionException ex) {
            activeJobs.decrementAndGet();
            telemetry.recordActiveJobs(activeJobs.get());
            telemetry.recordRejectedSubmission();
            completion.completeExceptionally(ex);
            return completion;
        }
        return completion;
    }

    /**
     * Submit a non-owner-scoped task to the background executor while still
     * respecting the coordinator's load-based throttling and main-thread
     * application semantics.
     */
    public <T> CompletableFuture<T> submitStandalone(String descriptor,
                                                     Callable<T> job,
                                                     @Nullable Consumer<T> applier) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(job, "job");

        int allowedThreads = computeAllowedThreads();
        if (allowedThreads <= 0) {
            return rejectThrottled("Standalone async task '" + descriptor + "' rejected: Server TPS is too low for async work");
        }

        int maxJobs = computeMaxJobs(allowedThreads);
        if (!tryAcquireSlot(maxJobs)) {
            return rejectThrottled("Standalone async task '" + descriptor + "' rejected: async job queue is at capacity");
        }

        telemetry.recordActiveJobs(activeJobs.get());
        CompletableFuture<T> completion = new CompletableFuture<>();
        try {
            executor.execute(() -> runStandalone(descriptor, job, applier, completion));
        } catch (RejectedExecutionException ex) {
            activeJobs.decrementAndGet();
            telemetry.recordActiveJobs(activeJobs.get());
            telemetry.recordRejectedSubmission();
            completion.completeExceptionally(ex);
            return completion;
        }
        return completion;
    }

    private <T> void runJob(OwnerBatchSnapshot snapshot,
                            OwnerBatchJob<T> job,
                            @Nullable Consumer<T> applier,
                            CompletableFuture<T> completion) {
        long start = System.nanoTime();
        T result = null;
        Throwable failure = null;
        try {
            result = job.run(snapshot);
        } catch (Throwable throwable) {
            failure = throwable;
        } finally {
            telemetry.recordAsyncDuration(System.nanoTime() - start);
        }

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

        activeJobs.decrementAndGet();
        telemetry.recordActiveJobs(activeJobs.get());
        scheduleDrain();
    }

    private <T> void runStandalone(String descriptor,
                                   Callable<T> job,
                                   @Nullable Consumer<T> applier,
                                   CompletableFuture<T> completion) {
        long start = System.nanoTime();
        T result = null;
        Throwable failure = null;
        try {
            result = job.call();
        } catch (Throwable throwable) {
            failure = throwable;
        } finally {
            telemetry.recordAsyncDuration(System.nanoTime() - start);
        }

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

        activeJobs.decrementAndGet();
        telemetry.recordActiveJobs(activeJobs.get());
        scheduleDrain();
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
        while ((task = queue.poll()) != null) {
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
            return Math.max(1, baseThreadCount / 2);
        }
        return baseThreadCount;
    }

    private int computeMaxJobs(int allowedThreads) {
        if (allowedThreads <= 0) {
            return 0;
        }
        return allowedThreads + Math.max(1, allowedThreads * 2);
    }

    private boolean tryAcquireSlot(int maxJobs) {
        if (maxJobs <= 0) {
            return false;
        }
        while (true) {
            int current = activeJobs.get();
            if (current >= maxJobs) {
                return false;
            }
            if (activeJobs.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private <T> CompletableFuture<T> rejectThrottled(String message) {
        telemetry.recordThrottledSubmission();
        return rejectedFuture(message);
    }

    private static <T> CompletableFuture<T> rejectedFuture(String message) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new RejectedExecutionException(message));
        return future;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    public AsyncProcessingTelemetry telemetry() {
        return telemetry;
    }

    @FunctionalInterface
    public interface OwnerBatchJob<T> {
        T run(OwnerBatchSnapshot snapshot) throws Exception;
    }
}
