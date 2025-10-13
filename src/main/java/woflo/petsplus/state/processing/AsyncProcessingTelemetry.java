package woflo.petsplus.state.processing;

import java.util.concurrent.atomic.AtomicLong;
import woflo.petsplus.config.DebugSettings;

/**
 * Phase A - Chunk 4: Telemetry scaffold for async processing.
 * 
 * Provides compile-safe counters and timers with zero external dependencies.
 * This class is cheap when disabled (no call sites in this phase). Future chunks
 * will add minimal, guarded instrumentation using the APIs exposed here.
 *
 * Notes:
 * - No references to Minecraft classes.
 * - Thread-safety: AtomicLongs for counters and timer accumulators.
 * - Admin toggles and sample cadence are sourced from DebugSettings.
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
    
    // ============ Minimal telemetry snapshot and record APIs (no-op stubs) ============
    
    public static final class TelemetrySnapshot {
        public boolean hasSamples() { return false; }
        public String toSummaryString() { return ""; }
    }
    
    public void recordCaptureDuration(long nanos) {}
    
    public TelemetrySnapshot snapshotAndReset() { return new TelemetrySnapshot(); }
    
    public void recordActiveJobs(int count) {}
    
    public void recordRejectedSubmission() {}
    
    public void recordDrainBatchSize(int size) {}
    
    public void recordThrottledSubmission() {}
    
    public void recordAsyncDuration(long nanos) {}
    
    public void recordApplyDuration(long nanos) {}
    
    public void recordResultQueueDepth(int depth) {}
}
