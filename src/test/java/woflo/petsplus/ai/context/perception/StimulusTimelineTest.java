package woflo.petsplus.ai.context.perception;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StimulusTimelineTest {

    @Test
    void snapshotOrdersStimuliNewestFirstAndExpiresOldEntries() {
        StimulusTimeline timeline = new StimulusTimeline();
        timeline.setCapacity(4);
        timeline.setTtlTicks(40);

        timeline.onStimulus(new PerceptionStimulus(
            Identifier.of("petsplus", "first"),
            10L,
            EnumSet.of(ContextSlice.MOOD),
            null
        ));
        timeline.onStimulus(new PerceptionStimulus(
            Identifier.of("petsplus", "second"),
            20L,
            EnumSet.of(ContextSlice.OWNER),
            null
        ));
        timeline.onStimulus(new PerceptionStimulus(
            Identifier.of("petsplus", "third"),
            30L,
            EnumSet.of(ContextSlice.CROWD),
            null
        ));

        StimulusSnapshot snapshot = timeline.snapshot(30L);
        assertEquals(3, snapshot.events().size(), "all stimuli should be present before expiry");
        assertEquals(Identifier.of("petsplus", "third"), snapshot.events().get(0).type(), "latest stimulus should be first");
        assertEquals(0L, snapshot.events().get(0).ageTicks());
        assertEquals(10L, snapshot.events().get(2).tick());
        assertTrue(snapshot.events().get(2).ageTicks() >= 20L, "oldest event should report positive age");

        StimulusSnapshot expired = timeline.snapshot(120L);
        assertEquals(1, expired.events().size(), "events beyond ttl should be trimmed");
        assertEquals(Identifier.of("petsplus", "third"), expired.events().get(0).type());
    }
}

