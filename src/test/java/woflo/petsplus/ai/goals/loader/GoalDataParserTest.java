package woflo.petsplus.ai.goals.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.goals.GoalDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GoalDataParserTest {

    @Test
    void parsesGoalDefinitionAndInstantiatesFactory() {
        Identifier id = Identifier.of("petsplus", "data_fetch_item");
        JsonObject json = JsonParser.parseString("""
            {
              "category": "play",
              "priority": 22,
              "cooldown": {"min": 50, "max": 180},
              "requirement": "tamed",
              "energy_range": {"min": 0.3, "max": 0.9},
              "idle_stamina_bias": "centered",
              "social_idle_bias": true,
              "goal_factory": "woflo.petsplus.ai.goals.play.FetchItemGoal"
            }
        """).getAsJsonObject();

        GoalDefinition definition = GoalDataParser.parse(id, json);

        assertEquals(id, definition.id());
        assertEquals(GoalDefinition.Category.PLAY, definition.category());
        assertEquals(22, definition.priority());

        assertNotNull(definition.factory(), "definition should expose goal factory");
    }

    @Test
    void parsesCapabilityTokenRequirement() {
        GoalDefinition definition = parseWithRequirement("\"can_wander\"");

        MobCapabilities.CapabilityRequirement expected = profile -> profile.canWander();
        assertMatches(expected, definition.requirement(),
            profile(true, false, false, false, false, false, false, false, false, true, false, false, false),
            profile(false, false, false, false, false, false, false, false, false, false, false, false, false)
        );
    }

    @Test
    void parsesAllOfRequirementObject() {
        GoalDefinition definition = parseWithRequirement("""
            {"all_of": ["can_swim", "prefers_water"]}
        """);

        MobCapabilities.CapabilityRequirement expected = profile -> profile.canSwim() && profile.prefersWater();
        assertMatches(expected, definition.requirement(),
            profile(false, false, true, false, false, false, false, false, false, false, true, false, false),
            profile(false, false, true, false, false, false, false, false, false, false, false, false, false),
            profile(false, false, false, false, false, false, false, false, false, false, true, false, false)
        );
    }

    @Test
    void parsesNestedAnyOfRequirement() {
        GoalDefinition definition = parseWithRequirement("""
            {"all_of": [{"any_of": ["can_fly", "can_swim"]}, {"not": "prefers_land"}]}
        """);

        MobCapabilities.CapabilityRequirement expected = profile ->
            (profile.canFly() || profile.canSwim()) && !profile.prefersLand();
        assertMatches(expected, definition.requirement(),
            profile(false, true, false, false, false, false, false, false, false, false, false, true, false),
            profile(false, false, true, false, false, false, false, false, false, false, true, false, false),
            profile(true, false, false, false, false, false, false, false, false, true, false, false, false)
        );
    }

    @Test
    void parsesNotRequirement() {
        GoalDefinition definition = parseWithRequirement("""
            {"not": "can_fly"}
        """);

        MobCapabilities.CapabilityRequirement expected = profile -> !profile.canFly();
        assertMatches(expected, definition.requirement(),
            profile(false, false, false, false, false, false, false, false, false, false, false, false, false),
            profile(false, true, false, false, false, false, false, false, false, false, false, true, false)
        );
    }

    private static GoalDefinition parseWithRequirement(String requirementJson) {
        JsonObject json = JsonParser.parseString("""
            {
              "category": "play",
              "priority": 10,
              "cooldown": {"min": 0, "max": 0},
              "requirement": %s,
              "energy_range": {"min": 0.0, "max": 1.0},
              "goal_factory": "woflo.petsplus.ai.goals.play.FetchItemGoal"
            }
        """.formatted(requirementJson)).getAsJsonObject();
        return GoalDataParser.parse(Identifier.of("petsplus", "test_requirement"), json);
    }

    private static MobCapabilities.CapabilityProfile profile(
        boolean canWander,
        boolean canFly,
        boolean canSwim,
        boolean canJump,
        boolean hasOwner,
        boolean canPickUpItems,
        boolean hasInventory,
        boolean canSit,
        boolean canMakeSound,
        boolean prefersLand,
        boolean prefersWater,
        boolean prefersAir,
        boolean isSmallSize
    ) {
        return new MobCapabilities.CapabilityProfile(
            canWander,
            canFly,
            canSwim,
            canJump,
            hasOwner,
            canPickUpItems,
            hasInventory,
            canSit,
            canMakeSound,
            prefersLand,
            prefersWater,
            prefersAir,
            isSmallSize
        );
    }

    private static void assertMatches(
        MobCapabilities.CapabilityRequirement expected,
        MobCapabilities.CapabilityRequirement actual,
        MobCapabilities.CapabilityProfile... profiles
    ) {
        for (MobCapabilities.CapabilityProfile profile : profiles) {
            assertEquals(
                expected.test(profile),
                actual.test(profile),
                "Requirement mismatch for profile " + profile
            );
        }
    }
}

