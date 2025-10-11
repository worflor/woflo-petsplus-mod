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
        trimOld(Long.MAX_VALUE);
    }

    public void setTtlTicks(long ttlTicks) {
        this.ttlTicks = Math.max(1L, ttlTicks);
        trimOld(Long.MAX_VALUE);
    }

    @Override
    public synchronized void onStimulus(PerceptionStimulus stimulus) {
        stimuli.addLast(stimulus);
        trimOld(stimulus.tick());
    }

    public synchronized StimulusSnapshot snapshot(long currentTick) {
        trimOld(currentTick);
        if (stimuli.isEmpty()) {
            return StimulusSnapshot.empty();
        }
        List<StimulusSnapshot.Event> events = new ArrayList<>(stimuli.size());
        for (PerceptionStimulus stimulus : stimuli) {
            long age = Math.max(0L, currentTick - stimulus.tick());
            events.add(new StimulusSnapshot.Event(
                stimulus.type(),
                stimulus.tick(),
                age,
                stimulus.slices(),
                stimulus.payload()
            ));
        }
        // Reverse newest-first ordering by inserting from oldest to newest into
        // the list and then reversing.
        java.util.Collections.reverse(events);
        return new StimulusSnapshot(events);
    }

    private void trimOld(long currentTick) {
        while (!stimuli.isEmpty() && stimuli.size() > capacity) {
            stimuli.removeFirst();
        }
        while (!stimuli.isEmpty()) {
            PerceptionStimulus head = stimuli.peekFirst();
            if (head == null) {
                break;
            }
            if ((currentTick - head.tick()) <= ttlTicks) {
                break;
            }
            stimuli.removeFirst();
        }
    }
}

