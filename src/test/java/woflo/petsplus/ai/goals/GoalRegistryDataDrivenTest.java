package woflo.petsplus.ai.goals;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import woflo.petsplus.ai.capability.MobCapabilities;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalRegistryDataDrivenTest {

    private static final Identifier DATA_ID = Identifier.of("test", "data_goal");

    @AfterEach
    void cleanup() {
        GoalRegistry.clearDataDriven();
    }

    @Test
    void registerDataDrivenGoalAddsAndClearsSuccessfully() {
        GoalDefinition definition = new GoalDefinition(
            DATA_ID,
            GoalDefinition.Category.PLAY,
            12,
            20,
            40,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.2f, 0.8f),
            GoalDefinition.IdleStaminaBias.CENTERED,
            false,
            mob -> null
        );

        GoalRegistry.registerDataDriven(definition);
        assertTrue(GoalRegistry.get(DATA_ID).isPresent(), "data-driven goal should be registered");

        GoalRegistry.clearDataDriven();
        assertFalse(GoalRegistry.get(DATA_ID).isPresent(), "clearDataDriven should remove registered goal");
    }
}

