package woflo.petsplus.ai.goals.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.ai.goals.AdaptiveGoal;
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

        MobEntity mob = Mockito.mock(MobEntity.class);
        AdaptiveGoal goal = definition.createGoal(mob);
        assertNotNull(goal, "factory should create adaptive goal instance");
    }
}

