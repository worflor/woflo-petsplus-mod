package woflo.petsplus.ai.goals;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import woflo.petsplus.ai.capability.MobCapabilities;

import static org.junit.jupiter.api.Assertions.*;

class GoalRegistryDataDrivenTest {

    private static final Identifier DATA_ID = Identifier.of("test", "data_goal");

    @AfterEach
    void cleanup() {
        GoalRegistry.clearDataDriven();
    }

    @Test
    void dataDrivenOverrideSupersedesBuiltInAndRestoresOnClear() {
        GoalDefinition builtIn = GoalRegistry.require(GoalIds.STARGAZING);

        GoalDefinition override = new GoalDefinition(
            GoalIds.STARGAZING,
            GoalDefinition.Category.SPECIAL,
            99,
            5,
            10,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.0f, 1.0f),
            GoalDefinition.IdleStaminaBias.NONE,
            false,
            mob -> null
        );

        GoalRegistry.registerDataDriven(override);

        GoalDefinition active = GoalRegistry.require(GoalIds.STARGAZING);
        assertSame(override, active, "data-driven override should be the active definition");

        GoalRegistry.clearDataDriven();

        GoalDefinition restored = GoalRegistry.require(GoalIds.STARGAZING);
        assertSame(builtIn, restored, "clearing data-driven entries should restore the built-in definition");
    }

    @Test
    void dataDrivenGoalWithNewIdIsRemovedOnClear() {
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
        assertFalse(GoalRegistry.get(DATA_ID).isPresent(), "clearDataDriven should remove custom goal definitions");
    }
}
