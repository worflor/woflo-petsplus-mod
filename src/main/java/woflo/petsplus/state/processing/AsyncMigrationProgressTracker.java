package woflo.petsplus.state.processing;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks completion of the asynchronous migration phases so integration tests
 * and diagnostics can confirm the pipeline has moved entirely off the main
 * thread. Each phase can be reported independently and the tracker provides a
 * thread-safe snapshot for telemetry consumers.
 */
public final class AsyncMigrationProgressTracker {

    private static final AtomicInteger COMPLETION_MASK = new AtomicInteger();
    private static final int ALL_PHASES_MASK;

    static {
        int mask = 0;
        for (Phase phase : Phase.values()) {
            mask |= (1 << phase.ordinal());
        }
        ALL_PHASES_MASK = mask;
    }

    private AsyncMigrationProgressTracker() {
    }

    public static void reset() {
        COMPLETION_MASK.set(0);
    }

    public static void markComplete(Phase phase) {
        if (phase == null) {
            return;
        }
        int bit = 1 << phase.ordinal();
        COMPLETION_MASK.getAndUpdate(current -> current | bit);
    }

    public static boolean isComplete(Phase phase) {
        if (phase == null) {
            return false;
        }
        int bit = 1 << phase.ordinal();
        return (COMPLETION_MASK.get() & bit) == bit;
    }

    public static boolean allComplete() {
        return COMPLETION_MASK.get() == ALL_PHASES_MASK;
    }

    public static int completedCount() {
        return Integer.bitCount(COMPLETION_MASK.get());
    }

    public static double completionRatio() {
        int total = Phase.values().length;
        if (total == 0) {
            return 0.0D;
        }
        return (double) completedCount() / (double) total;
    }

    public static String progressSummary() {
        PhaseStatus status = snapshot();
        int total = Phase.values().length;
        int completed = status.completedCount();
        double percentage = status.completionPercentage();
        return String.format(
            "Async migration progress: %d/%d phases complete (%.0f%%) %s",
            completed,
            total,
            percentage,
            status.completedPhases()
        );
    }

    public static PhaseStatus snapshot() {
        int mask = COMPLETION_MASK.get();
        EnumSet<Phase> phases = EnumSet.noneOf(Phase.class);
        for (Phase phase : Phase.values()) {
            int bit = 1 << phase.ordinal();
            if ((mask & bit) == bit) {
                phases.add(phase);
            }
        }
        return new PhaseStatus(phases);
    }

    public enum Phase {
        CORE_EMOTION,
        PET_STATE,
        OWNER_PROCESSING,
        ADVANCED_SYSTEMS
    }

    public static final class PhaseStatus {
        private final EnumSet<Phase> completed;

        private PhaseStatus(EnumSet<Phase> completed) {
            this.completed = completed.isEmpty()
                ? EnumSet.noneOf(Phase.class)
                : EnumSet.copyOf(completed);
        }

        public boolean isComplete(Phase phase) {
            Objects.requireNonNull(phase, "phase");
            return completed.contains(phase);
        }

        public Set<Phase> completedPhases() {
            return Collections.unmodifiableSet(completed);
        }

        public boolean allComplete() {
            return completed.containsAll(EnumSet.allOf(Phase.class));
        }

        public int completedCount() {
            return completed.size();
        }

        public double completionRatio() {
            int total = Phase.values().length;
            if (total == 0) {
                return 0.0D;
            }
            return (double) completedCount() / (double) total;
        }

        public double completionPercentage() {
            return completionRatio() * 100.0D;
        }

        @Override
        public String toString() {
            return "AsyncMigrationPhaseStatus" + completed;
        }
    }
}
