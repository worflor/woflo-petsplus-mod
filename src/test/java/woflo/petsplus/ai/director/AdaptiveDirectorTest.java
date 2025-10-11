package woflo.petsplus.ai.director;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.context.social.SocialSnapshot;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.planner.ActionFragment;
import woflo.petsplus.ai.planner.ActionPlan;
import woflo.petsplus.ai.planner.PlanResolution;
import woflo.petsplus.ai.planner.DeterministicPlanner;
import woflo.petsplus.ai.planner.PlanRegistry;
import woflo.petsplus.ai.suggester.GoalSuggester;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdaptiveDirectorTest {

    @AfterEach
    void reset() {
        PlanRegistry.clearDataDriven();
    }

    @Test
    void directorResolvesPlanForTopSuggestion() {
        GoalDefinition definition = new GoalDefinition(
            Identifier.of("test", "goal"),
            GoalDefinition.Category.PLAY,
            10,
            0,
            0,
            profile -> true,
            new net.minecraft.util.math.Vec2f(0.0f, 1.0f),
            GoalDefinition.IdleStaminaBias.CENTERED,
            false,
            mob -> null
        );

        PlanRegistry.registerFragment(new ActionFragment(Identifier.of("test", "step1"), "", List.of()));
        PlanRegistry.registerPlan(new ActionPlan(
            Identifier.of("test", "plan"),
            definition.id(),
            List.of(new ActionPlan.Step(Identifier.of("test", "step1"), List.of(), List.of())),
            false
        ));

        GoalSuggester suggester = Mockito.mock(GoalSuggester.class);
        GoalSuggester.Suggestion suggestion = new GoalSuggester.Suggestion(definition, 1.0f, 1.0f, "test", Map.of());
        Mockito.when(suggester.suggest(Mockito.any())).thenReturn(List.of(suggestion));

        AdaptiveDirector director = new AdaptiveDirector(suggester, new DeterministicPlanner());
        MobEntity mob = Mockito.mock(MobEntity.class);
        PetContext context = new PetContext(
            mob,
            null,
            null,
            0,
            Map.of(),
            Map.of(),
            null,
            null,
            null,
            1,
            0f,
            0L,
            null,
            false,
            Float.MAX_VALUE,
            List.<Entity>of(),
            PetContextCrowdSummary.empty(),
            BlockPos.ORIGIN,
            1000L,
            true,
            StimulusSnapshot.empty(),
            SocialSnapshot.empty(),
            false,
            new ArrayDeque<>(),
            Map.of(),
            Map.of(),
            0.5f,
            BehaviouralEnergyProfile.neutral()
        );

        DirectorDecision decision = director.decide(mob, context);
        PlanResolution resolution = decision.resolution();
        assertNotNull(resolution);
        assertEquals(Identifier.of("test", "plan"), resolution.plan().id());
        assertEquals(1, resolution.steps().size());
    }
}

