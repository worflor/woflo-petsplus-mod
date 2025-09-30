package woflo.petsplus.ai;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import woflo.petsplus.state.PetComponent;

import static org.junit.jupiter.api.Assertions.*;

class MoodGoalThrottlesTest {
    @BeforeEach
    void resetThrottles() {
        MoodGoalThrottles.reloadFromConfig();
    }

    @Test
    void missingGoalsSectionLeavesDefaultsUnchanged() {
        MoodGoalThrottleConfig original = MoodGoalThrottles.getConfig(PetComponent.Mood.HAPPY);

        JsonObject emptySection = new JsonObject();
        MoodGoalThrottles.applyOverridesFromJson(emptySection);

        assertSame(original, MoodGoalThrottles.getConfig(PetComponent.Mood.HAPPY));
    }

    @Test
    void partialOverridesOnlyAffectSpecifiedMood() {
        MoodGoalThrottleConfig originalHappy = MoodGoalThrottles.getConfig(PetComponent.Mood.HAPPY);
        MoodGoalThrottleConfig originalCalm = MoodGoalThrottles.getConfig(PetComponent.Mood.CALM);

        JsonObject overrides = new JsonObject();
        JsonObject goals = new JsonObject();
        JsonObject happyOverride = new JsonObject();
        happyOverride.addProperty("min_cooldown_ticks", originalHappy.minCooldownTicks() + 10);
        happyOverride.addProperty("min_active_ticks", originalHappy.minActiveTicks() + 5);
        goals.add("happy", happyOverride);
        overrides.add("goals", goals);

        MoodGoalThrottles.applyOverridesFromJson(overrides);

        MoodGoalThrottleConfig updatedHappy = MoodGoalThrottles.getConfig(PetComponent.Mood.HAPPY);
        MoodGoalThrottleConfig updatedCalm = MoodGoalThrottles.getConfig(PetComponent.Mood.CALM);

        assertNotSame(originalHappy, updatedHappy);
        assertEquals(originalHappy.minCooldownTicks() + 10, updatedHappy.minCooldownTicks());
        assertEquals(originalHappy.minActiveTicks() + 5, updatedHappy.minActiveTicks());
        assertSame(originalCalm, updatedCalm);
    }
}
