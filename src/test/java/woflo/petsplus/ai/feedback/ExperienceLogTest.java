package woflo.petsplus.ai.feedback;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperienceLogTest {

    @Test
    void recordsAndClampsEntries() {
        ExperienceLog log = new ExperienceLog();
        for (int i = 0; i < 40; i++) {
            log.record(Identifier.of("test", "goal" + i), i / 10f, i);
        }
        assertEquals(32, log.entries().size());
        assertEquals(Identifier.of("test", "goal39"), log.entries().get(0).goalId());
    }
}

