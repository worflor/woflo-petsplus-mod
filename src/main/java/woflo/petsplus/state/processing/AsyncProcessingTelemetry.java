package woflo.petsplus.state.processing;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import woflo.petsplus.config.DebugSettings;

/**
 * Telemetry counters and timers used by async processing components. All metrics are
 * optional and guarded by {@link DebugSettings}; when disabled the calls are cheap no-ops.
 *
 * Notes:
 * - No references to Minecraft classes.
 * - Thread-safety: AtomicLongs for counters and timer accumulators.
 * - Admin toggles and sample cadence are sourced from {@link DebugSettings}.
 */
public final class AsyncProcessingTelemetry {

    public AsyncProcessingTelemetry() {
        // Allow instantiation
    }

    // ============ COUNTERS ============

    /** Number of ingress events observed (queued for processing). */
    public static final AtomicLong INGRESS_EVENTS = new AtomicLong();

    /** Number of owner batches built or processed. */
    public static final AtomicLong OWNER_BATCHES = new AtomicLong();

    /** Number of tasks enqueued for async execution. */
    public static final AtomicLong TASKS_ENQUEUED = new AtomicLong();

    /** Number of tasks actually executed by the async processor. */
    public static final AtomicLong TASKS_EXECUTED = new AtomicLong();

    /** Number of tasks dropped due to capacity or policy. */
    public static final AtomicLong TASKS_DROPPED = new AtomicLong();

    /** Number of stimuli coalesced (merged) to reduce work. */
    public static final AtomicLong STIMULI_COALESCED = new AtomicLong();

    // ============ ENABLEMENT & SAMPLING ============

    /**
     * Returns whether telemetry is globally enabled via admin toggles.
     */
    public static boolean isEnabled() {
        return DebugSettings.isTelemetryEnabled();
    }

    /**
     * Returns the configured sampling cadence in ticks.
     */
    public static int sampleRateTicks() {
        return DebugSettings.getTelemetrySampleRateTicks();
    }

    /**
     * True when a sample should be taken at the provided tick count, honoring the admin
     * toggle and the configured sampling cadence.
     */
    public static boolean shouldSampleByTick(int tickCount) {
        final int rate = sampleRateTicks();
        return isEnabled() && (rate <= 1 || (tickCount % rate == 0));
    }

    // ============ TIMERS ============

    /**
     * Minimal timer metric with total nanoseconds and count aggregation.
     */
    public static final class TimerMetric {
        private final AtomicLong totalNanos = new AtomicLong();
        private final AtomicLong count = new AtomicLong();

        /**
         * Record a duration in nanoseconds (non-positive values are ignored).
         */
        public void record(long nanos) {
            if (nanos <= 0) return;
            totalNanos.addAndGet(nanos);
            count.incrementAndGet();
        }

        /**
         * Total accumulated nanoseconds recorded.
         */
        public long getTotalNanos() {
            return totalNanos.get();
        }

        /**
         * Number of timing samples recorded.
         */
        public long getCount() {
            return count.get();
        }
    }

    /** Time spent from ingress to initial handling for a batch/sample. */
    public static final TimerMetric INGRESS_TIME = new TimerMetric();

    /** Time spent dispatching tasks/events to workers/queues. */
    public static final TimerMetric DISPATCH_TIME = new TimerMetric();

    /** Time spent committing or finalizing results/state. */
    public static final TimerMetric COMMIT_TIME = new TimerMetric();

    /**
     * Start a timer and return the nanoTime snapshot.
     */
    public static long startTimer() {
        return System.nanoTime();
    }

    /**
     * Stop a timer and record the elapsed duration to the target metric.
     */
    public static void stopTimer(TimerMetric metric, long startNanos) {
        if (metric == null) return;
        final long now = System.nanoTime();
        final long elapsed = now - startNanos;
        if (elapsed > 0) {
            metric.record(elapsed);
        }
    }

    // ============ INSTANCE METRICS ============

    private final LongAdder captureTotalNanos = new LongAdder();
    private final LongAdder captureCount = new LongAdder();

    private final LongAdder asyncTotalNanos = new LongAdder();
    private final LongAdder asyncCount = new LongAdder();

    private final LongAdder applyTotalNanos = new LongAdder();
    private final LongAdder applyCount = new LongAdder();

    private final LongAdder drainOperations = new LongAdder();
    private final LongAdder drainedTasks = new LongAdder();
    private final AtomicInteger maxDrainTasks = new AtomicInteger();

    private final LongAdder throttledSubmissions = new LongAdder();
    private final LongAdder rejectedSubmissions = new LongAdder();

    private final LongAdder queueDepthTotal = new LongAdder();
    private final LongAdder queueDepthSamples = new LongAdder();
    private final AtomicInteger maxQueueDepth = new AtomicInteger();

    private final LongAdder activeJobTotal = new LongAdder();
    private final LongAdder activeJobSamples = new LongAdder();
    private final AtomicInteger maxActiveJobs = new AtomicInteger();

    public static final class TelemetrySnapshot {
        private final long captureCount;
        private final long captureNanos;
        private final long asyncCount;
        private final long asyncNanos;
        private final long applyCount;
        private final long applyNanos;
        private final long drainOperations;
        private final long drainedTasks;
        private final int maxDrainTasks;
        private final long throttledSubmissions;
        private final long rejectedSubmissions;
        private final long queueDepthTotal;
        private final long queueDepthSamples;
        private final int maxQueueDepth;
        private final long activeJobTotal;
        private final long activeJobSamples;
        private final int maxActiveJobs;

        private TelemetrySnapshot(long captureCount,
                                  long captureNanos,
                                  long asyncCount,
                                  long asyncNanos,
                                  long applyCount,
                                  long applyNanos,
                                  long drainOperations,
                                  long drainedTasks,
                                  int maxDrainTasks,
                                  long throttledSubmissions,
                                  long rejectedSubmissions,
                                  long queueDepthTotal,
                                  long queueDepthSamples,
                                  int maxQueueDepth,
                                  long activeJobTotal,
                                  long activeJobSamples,
                                  int maxActiveJobs) {
            this.captureCount = captureCount;
            this.captureNanos = captureNanos;
            this.asyncCount = asyncCount;
            this.asyncNanos = asyncNanos;
            this.applyCount = applyCount;
            this.applyNanos = applyNanos;
            this.drainOperations = drainOperations;
            this.drainedTasks = drainedTasks;
            this.maxDrainTasks = maxDrainTasks;
            this.throttledSubmissions = throttledSubmissions;
            this.rejectedSubmissions = rejectedSubmissions;
            this.queueDepthTotal = queueDepthTotal;
            this.queueDepthSamples = queueDepthSamples;
            this.maxQueueDepth = maxQueueDepth;
            this.activeJobTotal = activeJobTotal;
            this.activeJobSamples = activeJobSamples;
            this.maxActiveJobs = maxActiveJobs;
        }

        public boolean hasSamples() {
            return captureCount > 0
                || asyncCount > 0
                || applyCount > 0
                || drainOperations > 0
                || throttledSubmissions > 0
                || rejectedSubmissions > 0
                || maxQueueDepth > 0
                || maxActiveJobs > 0;
        }

        public String toSummaryString() {
            StringBuilder sb = new StringBuilder(192);
            sb.append("capture=").append(captureCount)
              .append(" avg=").append(formatDuration(captureNanos, captureCount));

            sb.append(" | async=").append(asyncCount)
              .append(" avg=").append(formatDuration(asyncNanos, asyncCount));

            sb.append(" | apply=").append(applyCount)
              .append(" avg=").append(formatDuration(applyNanos, applyCount));

            sb.append(" | drains=").append(drainOperations);
            if (drainOperations > 0) {
                sb.append(" avgTasks=").append(formatDecimal(drainedTasks, drainOperations));
            }
            sb.append(" max=").append(maxDrainTasks);

            sb.append(" | queueMax=").append(maxQueueDepth);
            if (queueDepthSamples > 0) {
                sb.append(" avg=").append(formatDecimal(queueDepthTotal, queueDepthSamples));
            }

            sb.append(" | activeMax=").append(maxActiveJobs);
            if (activeJobSamples > 0) {
                sb.append(" avg=").append(formatDecimal(activeJobTotal, activeJobSamples));
            }

            if (throttledSubmissions > 0 || rejectedSubmissions > 0) {
                sb.append(" | throttled=").append(throttledSubmissions)
                  .append(" rejected=").append(rejectedSubmissions);
            }

            return sb.toString();
        }

        private static String formatDuration(long totalNanos, long samples) {
            if (samples <= 0 || totalNanos <= 0) {
                return "0ms";
            }
            double avgNanos = (double) totalNanos / (double) samples;
            if (avgNanos >= 1_000_000.0) {
                return String.format(Locale.ROOT, "%.2fms", avgNanos / 1_000_000.0);
            }
            if (avgNanos >= 1_000.0) {
                return String.format(Locale.ROOT, "%.2fµs", avgNanos / 1_000.0);
            }
            return String.format(Locale.ROOT, "%.0Fns", avgNanos);
        }

        private static String formatDecimal(long total, long samples) {
            if (samples <= 0) {
                return "0.00";
            }
            double value = (double) total / (double) samples;
            return String.format(Locale.ROOT, "%.2f", value);
        }
    }

    public void recordCaptureDuration(long nanos) {
        if (!isEnabled() || nanos <= 0) {
            return;
        }
        captureTotalNanos.add(nanos);
        captureCount.increment();
    }

    public void recordActiveJobs(int count) {
        if (!isEnabled() || count < 0) {
            return;
        }
        activeJobTotal.add(count);
        activeJobSamples.increment();
        updateMax(maxActiveJobs, count);
    }

    public void recordRejectedSubmission() {
        if (!isEnabled()) {
            return;
        }
        rejectedSubmissions.increment();
    }

    public void recordDrainBatchSize(int size) {
        if (!isEnabled() || size <= 0) {
            return;
        }
        drainOperations.increment();
        drainedTasks.add(size);
        updateMax(maxDrainTasks, size);
    }

    public void recordThrottledSubmission() {
        if (!isEnabled()) {
            return;
        }
        throttledSubmissions.increment();
    }

    public void recordAsyncDuration(long nanos) {
        if (!isEnabled() || nanos <= 0) {
            return;
        }
        asyncTotalNanos.add(nanos);
        asyncCount.increment();
        DISPATCH_TIME.record(nanos);
    }

    public void recordApplyDuration(long nanos) {
        if (!isEnabled() || nanos <= 0) {
            return;
        }
        applyTotalNanos.add(nanos);
        applyCount.increment();
        COMMIT_TIME.record(nanos);
    }

    public void recordResultQueueDepth(int depth) {
        if (!isEnabled() || depth < 0) {
            return;
        }
        queueDepthTotal.add(depth);
        queueDepthSamples.increment();
        updateMax(maxQueueDepth, depth);
    }

    public TelemetrySnapshot snapshotAndReset() {
        long captureCount = this.captureCount.sumThenReset();
        long captureNanos = this.captureTotalNanos.sumThenReset();
        long asyncCount = this.asyncCount.sumThenReset();
        long asyncNanos = this.asyncTotalNanos.sumThenReset();
        long applyCount = this.applyCount.sumThenReset();
        long applyNanos = this.applyTotalNanos.sumThenReset();
        long drains = this.drainOperations.sumThenReset();
        long drained = this.drainedTasks.sumThenReset();
        int maxDrain = this.maxDrainTasks.getAndSet(0);
        long throttled = this.throttledSubmissions.sumThenReset();
        long rejected = this.rejectedSubmissions.sumThenReset();
        long queueTotal = this.queueDepthTotal.sumThenReset();
        long queueSamples = this.queueDepthSamples.sumThenReset();
        int maxQueue = this.maxQueueDepth.getAndSet(0);
        long activeTotal = this.activeJobTotal.sumThenReset();
        long activeSamples = this.activeJobSamples.sumThenReset();
        int maxActive = this.maxActiveJobs.getAndSet(0);

        return new TelemetrySnapshot(
            captureCount,
            captureNanos,
            asyncCount,
            asyncNanos,
            applyCount,
            applyNanos,
            drains,
            drained,
            maxDrain,
            throttled,
            rejected,
            queueTotal,
            queueSamples,
            maxQueue,
            activeTotal,
            activeSamples,
            maxActive
        );
    }

    private static void updateMax(AtomicInteger current, int candidate) {
        int prev;
        do {
            prev = current.get();
            if (candidate <= prev) {
                return;
            }
        } while (!current.compareAndSet(prev, candidate));
    }
}
