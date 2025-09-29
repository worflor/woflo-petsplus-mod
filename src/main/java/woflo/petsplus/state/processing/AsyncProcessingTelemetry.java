package woflo.petsplus.state.processing;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Simple metrics collector for the asynchronous owner-processing pipeline. The
 * coordinator records capture, async execution, and main-thread application
 * timings so operators can inspect the effectiveness of the decoupled stages.
 */
public final class AsyncProcessingTelemetry {
    private final LongAdder captureNanos = new LongAdder();
    private final LongAdder captureCount = new LongAdder();

    private final LongAdder asyncNanos = new LongAdder();
    private final LongAdder asyncCount = new LongAdder();

    private final LongAdder applyNanos = new LongAdder();
    private final LongAdder applyCount = new LongAdder();

    private final AtomicInteger peakActiveJobs = new AtomicInteger();
    private final AtomicInteger peakResultQueue = new AtomicInteger();
    private final AtomicInteger peakDrainBatch = new AtomicInteger();
    private final LongAdder throttledSubmissions = new LongAdder();
    private final LongAdder rejectedSubmissions = new LongAdder();

    private final AtomicLong lastSnapshotNanos = new AtomicLong(System.nanoTime());

    public void recordCaptureDuration(long nanos) {
        if (nanos <= 0L) {
            return;
        }
        captureCount.increment();
        captureNanos.add(nanos);
    }

    public void recordAsyncDuration(long nanos) {
        if (nanos <= 0L) {
            return;
        }
        asyncCount.increment();
        asyncNanos.add(nanos);
    }

    public void recordApplyDuration(long nanos) {
        if (nanos <= 0L) {
            return;
        }
        applyCount.increment();
        applyNanos.add(nanos);
    }

    public void recordActiveJobs(int activeJobs) {
        updatePeak(peakActiveJobs, activeJobs);
    }

    public void recordResultQueueDepth(int queueDepth) {
        updatePeak(peakResultQueue, queueDepth);
    }

    public void recordDrainBatchSize(int drained) {
        updatePeak(peakDrainBatch, drained);
    }

    public void recordThrottledSubmission() {
        throttledSubmissions.increment();
    }

    public void recordRejectedSubmission() {
        rejectedSubmissions.increment();
    }

    public TelemetrySnapshot snapshotAndReset() {
        long captureSamples = captureCount.sumThenReset();
        long captureTotal = captureNanos.sumThenReset();
        long asyncSamples = asyncCount.sumThenReset();
        long asyncTotal = asyncNanos.sumThenReset();
        long applySamples = applyCount.sumThenReset();
        long applyTotal = applyNanos.sumThenReset();
        long throttled = throttledSubmissions.sumThenReset();
        long rejected = rejectedSubmissions.sumThenReset();

        int peakJobs = peakActiveJobs.getAndSet(0);
        int peakQueue = peakResultQueue.getAndSet(0);
        int peakDrain = peakDrainBatch.getAndSet(0);

        long now = System.nanoTime();
        long interval = now - lastSnapshotNanos.getAndSet(now);

        return new TelemetrySnapshot(
            captureSamples,
            captureTotal,
            asyncSamples,
            asyncTotal,
            applySamples,
            applyTotal,
            peakJobs,
            peakQueue,
            peakDrain,
            interval,
            throttled,
            rejected
        );
    }

    private static void updatePeak(AtomicInteger target, int candidate) {
        if (candidate <= 0) {
            return;
        }
        int current;
        do {
            current = target.get();
            if (candidate <= current) {
                return;
            }
        } while (!target.compareAndSet(current, candidate));
    }

    /**
     * Immutable view of the collected metrics during a sampling window.
     */
    public static final class TelemetrySnapshot {
        private final long captureCount;
        private final long captureNanos;
        private final long asyncCount;
        private final long asyncNanos;
        private final long applyCount;
        private final long applyNanos;
        private final int peakActiveJobs;
        private final int peakResultQueue;
        private final int peakDrainBatch;
        private final long windowNanos;
        private final long throttledSubmissions;
        private final long rejectedSubmissions;

        private TelemetrySnapshot(long captureCount,
                                  long captureNanos,
                                  long asyncCount,
                                  long asyncNanos,
                                  long applyCount,
                                  long applyNanos,
                                  int peakActiveJobs,
                                  int peakResultQueue,
                                  int peakDrainBatch,
                                  long windowNanos,
                                  long throttledSubmissions,
                                  long rejectedSubmissions) {
            this.captureCount = captureCount;
            this.captureNanos = captureNanos;
            this.asyncCount = asyncCount;
            this.asyncNanos = asyncNanos;
            this.applyCount = applyCount;
            this.applyNanos = applyNanos;
            this.peakActiveJobs = peakActiveJobs;
            this.peakResultQueue = peakResultQueue;
            this.peakDrainBatch = peakDrainBatch;
            this.windowNanos = windowNanos;
            this.throttledSubmissions = throttledSubmissions;
            this.rejectedSubmissions = rejectedSubmissions;
        }

        public boolean hasSamples() {
            return captureCount > 0 || asyncCount > 0 || applyCount > 0
                || throttledSubmissions > 0 || rejectedSubmissions > 0;
        }

        public double averageCaptureMillis() {
            return averageMillis(captureNanos, captureCount);
        }

        public double averageAsyncMillis() {
            return averageMillis(asyncNanos, asyncCount);
        }

        public double averageApplyMillis() {
            return averageMillis(applyNanos, applyCount);
        }

        public int peakActiveJobs() {
            return peakActiveJobs;
        }

        public int peakResultQueue() {
            return peakResultQueue;
        }

        public int peakDrainBatch() {
            return peakDrainBatch;
        }

        public long windowMillis() {
            return Math.max(0L, windowNanos / 1_000_000L);
        }

        public long throttledSubmissions() {
            return throttledSubmissions;
        }

        public long rejectedSubmissions() {
            return rejectedSubmissions;
        }

        public double backgroundShare() {
            double async = asyncNanos;
            double total = async + captureNanos + applyNanos;
            if (total <= 0.0D) {
                return 0.0D;
            }
            return async / total;
        }

        public double mainThreadShare() {
            double main = captureNanos + applyNanos;
            double total = main + asyncNanos;
            if (total <= 0.0D) {
                return 0.0D;
            }
            return main / total;
        }

        public double asyncToMainRatio() {
            double main = captureNanos + applyNanos;
            if (main <= 0.0D) {
                return asyncNanos > 0.0D ? Double.POSITIVE_INFINITY : 0.0D;
            }
            return asyncNanos / main;
        }

        public String toSummaryString() {
            return String.format(
                "window=%dms captureAvg=%.3fms asyncAvg=%.3fms applyAvg=%.3fms bgShare=%.2f mainShare=%.2f asyncMainRatio=%.2f peakJobs=%d peakQueue=%d peakDrain=%d throttled=%d rejected=%d",
                windowMillis(),
                averageCaptureMillis(),
                averageAsyncMillis(),
                averageApplyMillis(),
                backgroundShare(),
                mainThreadShare(),
                asyncToMainRatio(),
                peakActiveJobs,
                peakResultQueue,
                peakDrainBatch,
                throttledSubmissions,
                rejectedSubmissions
            );
        }

        private static double averageMillis(long totalNanos, long samples) {
            if (samples <= 0 || totalNanos <= 0L) {
                return 0.0D;
            }
            double nanosPerSample = (double) totalNanos / (double) samples;
            return nanosPerSample / 1_000_000.0D;
        }
    }
}
