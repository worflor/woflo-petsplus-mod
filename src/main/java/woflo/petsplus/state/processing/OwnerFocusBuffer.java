package woflo.petsplus.state.processing;

import org.jetbrains.annotations.Nullable;

/**
 * Maintains the latest owner focus snapshots alongside the last busy tick so
 * callers can query whether courtesy holds should remain active without having
 * to duplicate bookkeeping logic. The buffer accepts sparse updates and keeps
 * the most recent busy snapshot available until the configured window elapses.
 */
public final class OwnerFocusBuffer {

    private OwnerFocusSnapshot currentSnapshot = OwnerFocusSnapshot.idle();
    private OwnerFocusSnapshot lastBusySnapshot = OwnerFocusSnapshot.idle();
    private long lastBusyTick = Long.MIN_VALUE;

    /**
     * Applies a new focus snapshot that was observed on the provided tick.
     * Busy snapshots refresh the stored busy metadata while idle ones simply
     * update the current state without clearing the busy history.
     */
    public void update(@Nullable OwnerFocusSnapshot snapshot, long tick) {
        OwnerFocusSnapshot sanitized = snapshot == null ? OwnerFocusSnapshot.idle() : snapshot;
        currentSnapshot = sanitized;
        if (sanitized.isBusy()) {
            lastBusySnapshot = sanitized;
            lastBusyTick = tick;
        }
    }

    /**
     * @return the most recent snapshot that flagged the owner as busy.
     */
    public OwnerFocusSnapshot lastBusySnapshot() {
        return lastBusySnapshot;
    }

    /**
     * @return the most recently observed snapshot regardless of busy status.
     */
    public OwnerFocusSnapshot currentSnapshot() {
        return currentSnapshot;
    }

    /**
     * @return the tick the owner was last seen in a busy state, or
     *         {@link Long#MIN_VALUE} if the owner has never been busy.
     */
    public long lastBusyTick() {
        return lastBusyTick;
    }

    /**
     * Determines whether the owner should be treated as busy for courtesy
     * spacing given the supplied window and the latest tick.
     */
    public boolean isBusy(long now, long courtesyWindow) {
        long window = Math.max(0L, courtesyWindow);
        if (window <= 0L) {
            return currentSnapshot.isBusy();
        }
        if (currentSnapshot.isBusy()) {
            return true;
        }
        if (lastBusyTick == Long.MIN_VALUE) {
            return false;
        }
        return now - lastBusyTick <= window;
    }

    /**
     * Returns the snapshot that should inform courtesy decisions at the given
     * tick. When the live snapshot is idle but still within the courtesy
     * window, the last busy snapshot is returned so downstream logic can scale
     * spacing based on the interaction that just finished.
     */
    public OwnerFocusSnapshot courtesySnapshot(long now, long courtesyWindow) {
        return isBusy(now, courtesyWindow) ? (currentSnapshot.isBusy() ? currentSnapshot : lastBusySnapshot)
            : OwnerFocusSnapshot.idle();
    }

    /**
     * @return the number of ticks elapsed since the owner last registered as
     *         busy. {@code Long.MAX_VALUE} is returned when no busy snapshot has
     *         been observed yet.
     */
    public long ticksSinceBusy(long now) {
        if (lastBusyTick == Long.MIN_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, now - lastBusyTick);
    }

    /**
     * Resets the buffer to an idle state.
     */
    public void reset() {
        currentSnapshot = OwnerFocusSnapshot.idle();
        lastBusySnapshot = OwnerFocusSnapshot.idle();
        lastBusyTick = Long.MIN_VALUE;
    }
}

