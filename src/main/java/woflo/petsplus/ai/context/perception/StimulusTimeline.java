package woflo.petsplus.ai.context.perception;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Tracks recent perception stimuli with a simple sliding window so adaptive
 * scoring can access short-term history without polling the bus. The timeline
 * is safe to use from the main-thread perception dispatch and from snapshot
 * capture as both access patterns synchronise on the internal deque.
 */
public final class StimulusTimeline implements PerceptionListener {

    private static final int DEFAULT_CAPACITY = 48;
    private static final long DEFAULT_TTL_TICKS = 200L;

    private final Deque<PerceptionStimulus> stimuli = new ArrayDeque<>();
    private int capacity = DEFAULT_CAPACITY;
    private long ttlTicks = DEFAULT_TTL_TICKS;

    public void setCapacity(int capacity) {
        this.capacity = Math.max(1, capacity);
        // Only enforce capacity here; TTL is evaluated relative to snapshot() currentTick.
        trimOldForCapacityOnly();
    }

    public void setTtlTicks(long ttlTicks) {
        this.ttlTicks = Math.max(1L, ttlTicks);
        // Do not prune by TTL here; TTL pruning is snapshot-relative (avoids off-by-one).
    }

    @Override
    public synchronized void onStimulus(PerceptionStimulus stimulus) {
        if (stimulus == null) {
            return;
        }
        stimuli.addLast(stimulus);
        // Capacity-only pruning on append to avoid premature TTL removal.
        // TTL is evaluated relative to snapshot's currentTick to ensure deterministic boundary behavior.
        while (!stimuli.isEmpty() && stimuli.size() > capacity) {
            stimuli.removeFirst();
        }
    }

    public synchronized StimulusSnapshot snapshot(long currentTick) {
        // Compute TTL cutoff in ticks using long-only arithmetic.
        // Inclusive boundary: retain events where eventTick >= cutoff; boundary entries (== cutoff) are kept.
        final long cutoff = currentTick - ttlTicks;

        // Build newest-first (descending by eventTick) while preserving insertion order for equal timestamps.
        // We iterate the deque from tail (newest) to head (oldest) to naturally produce newest-first.
        // Since ArrayDeque iteration from tail to head via descendingIterator() is stable for equals,
        // equal timestamps preserve insertion order.
        List<StimulusSnapshot.Event> events = new ArrayList<>(stimuli.size());
        for (java.util.Iterator<PerceptionStimulus> it = stimuli.descendingIterator(); it.hasNext(); ) {
            PerceptionStimulus s = it.next();
            if (s == null) {
                continue;
            }
            if (s.tick() >= cutoff) { // inclusive cutoff check
                long age = Math.max(0L, currentTick - s.tick()); // long-only math
                events.add(new StimulusSnapshot.Event(
                    s.type(),
                    s.tick(),
                    age,
                    s.slices(),
                    s.payload()
                ));
            }
        }

        // Optional post-snapshot pruning: remove strictly older entries from the head
        // using the same cutoff, but do NOT remove boundary entries (== cutoff).
        // This keeps the backing store tidy without affecting snapshot semantics.
        while (!stimuli.isEmpty()) {
            PerceptionStimulus head = stimuli.peekFirst();
            if (head == null || head.tick() >= cutoff) {
                break; // stop at boundary or newer
            }
            stimuli.removeFirst();
        }

        if (events.isEmpty()) {
            return StimulusSnapshot.empty();
        }
        return new StimulusSnapshot(events);
    }

    private void trimOldForCapacityOnly() {
        // Capacity pruning (remove oldest first until within capacity).
        // No TTL logic here; TTL is applied in snapshot() with inclusive boundary semantics.
        while (!stimuli.isEmpty() && stimuli.size() > capacity) {
            stimuli.removeFirst();
        }
    }
}

