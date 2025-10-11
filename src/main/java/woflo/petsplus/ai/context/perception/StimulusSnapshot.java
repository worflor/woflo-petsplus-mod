package woflo.petsplus.ai.context.perception;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Immutable snapshot of the recent perception stimuli observed by a pet.
 * The snapshot stores events in newest-first order so callers can iterate the
 * latest stimuli without additional copying. Payload objects are passed
 * through as-is; consumers should defensively copy mutable payloads if they
 * intend to mutate them.
 */
public final class StimulusSnapshot {

    private static final StimulusSnapshot EMPTY = new StimulusSnapshot(List.of());

    private final List<Event> events;

    public StimulusSnapshot(List<Event> events) {
        if (events == null || events.isEmpty()) {
            this.events = List.of();
            return;
        }
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }

    public static StimulusSnapshot empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public List<Event> events() {
        return events;
    }

    /**
     * Represents a single stimulus entry. Ages are reported relative to the
     * snapshot tick so a value of {@code 0} indicates the stimulus occurred on
     * the same tick the snapshot was captured.
     */
    public record Event(
        Identifier type,
        long tick,
        long ageTicks,
        EnumSet<ContextSlice> slices,
        Object payload
    ) {
        public Event {
            if (type == null) {
                throw new IllegalArgumentException("type");
            }
            if (slices == null || slices.isEmpty()) {
                slices = EnumSet.of(ContextSlice.ALL);
            } else {
                slices = EnumSet.copyOf(slices);
            }
            if (ageTicks < 0) {
                ageTicks = 0;
            }
        }
    }
}

