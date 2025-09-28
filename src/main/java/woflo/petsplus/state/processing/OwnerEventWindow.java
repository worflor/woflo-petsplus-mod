package woflo.petsplus.state.processing;

/**
 * Tracks the next scheduled execution tick for an owner-scoped event. The
 * window remembers the earliest due tick and provides helpers for determining
 * whether the event should be processed on the current tick.
 */
final class OwnerEventWindow {
    private long nextTick = Long.MAX_VALUE;
    private long lastRunTick = Long.MIN_VALUE;

    void schedule(long tick) {
        if (tick < 0L) {
            tick = 0L;
        }
        if (tick <= lastRunTick) {
            tick = lastRunTick + 1;
        }
        if (tick < nextTick) {
            nextTick = tick;
        }
    }

    void clear() {
        nextTick = Long.MAX_VALUE;
    }

    void markRan(long tick) {
        lastRunTick = tick;
        nextTick = Long.MAX_VALUE;
    }

    boolean applyPrediction(long tick) {
        if (tick < 0L) {
            tick = 0L;
        }
        if (tick <= lastRunTick) {
            tick = lastRunTick + 1L;
        }
        if (nextTick == tick) {
            return false;
        }
        nextTick = tick;
        return true;
    }

    boolean isDue(long currentTick) {
        return currentTick >= nextTick;
    }

    boolean hasSchedule() {
        return nextTick != Long.MAX_VALUE;
    }

    long nextTick() {
        return nextTick;
    }

    long lastRunTick() {
        return lastRunTick;
    }
}
