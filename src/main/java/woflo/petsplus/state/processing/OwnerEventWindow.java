package woflo.petsplus.state.processing;

/**
 * Tracks the next scheduled execution tick for an owner-scoped event. The
 * window remembers the earliest due tick and provides helpers for determining
 * whether the event should be processed on the current tick.
 */
final class OwnerEventWindow {
    private long nextTick = Long.MAX_VALUE;
    private long lastRunTick = Long.MIN_VALUE;
    private boolean nextTickIsPrediction;

    void schedule(long tick) {
        tick = normalizeTick(tick);
        if (!hasSchedule() || tick <= nextTick || nextTickIsPrediction) {
            nextTick = tick;
            nextTickIsPrediction = false;
        }
    }

    void clear() {
        nextTick = Long.MAX_VALUE;
        nextTickIsPrediction = false;
    }

    void markRan(long tick) {
        lastRunTick = tick;
        nextTick = Long.MAX_VALUE;
        nextTickIsPrediction = false;
    }

    boolean applyPrediction(long tick) {
        tick = normalizeTick(tick);
        if (!hasSchedule()) {
            nextTick = tick;
            nextTickIsPrediction = true;
            return true;
        }
        if (tick < nextTick) {
            nextTick = tick;
            nextTickIsPrediction = true;
            return true;
        }
        if (nextTickIsPrediction && tick > nextTick) {
            nextTick = tick;
            return true;
        }
        return false;
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

    private long normalizeTick(long tick) {
        if (tick < 0L) {
            tick = 0L;
        }
        if (tick <= lastRunTick) {
            tick = lastRunTick + 1L;
        }
        return tick;
    }
}
