package woflo.petsplus.ai.planner;

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
import woflo.petsplus.ai.variants.BehaviorVariant;
import woflo.petsplus.ai.variants.BehaviorVariantRegistry;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeterministicPlannerTest {

    @AfterEach
    void cleanup() {
        BehaviorVariantRegistry.clear();
        PlanRegistry.clearDataDriven();
    }

    @Test
    void resolvesVariantsAndGroup() {
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

        Identifier fragmentId = Identifier.of("test", "fragment");
        PlanRegistry.registerFragment(new ActionFragment(fragmentId, "", List.of()));
        PlanRegistry.registerPlan(new ActionPlan(
            Identifier.of("test", "plan"),
            definition.id(),
            List.of(new ActionPlan.Step(fragmentId, List.of(Identifier.of("test", "variant")), List.of())),
            true
        ));

        BehaviorVariant variant = new BehaviorVariant() {
            @Override
            public Identifier id() {
                return Identifier.of("test", "variant");
            }

            @Override
            public boolean matches(GoalDefinition goal, PetContext context) {
                return goal == definition;
            }

            @Override
            public String poseTag() {
                return "pose.variant";
            }
        };
        BehaviorVariantRegistry.register(variant);

        DeterministicPlanner planner = new DeterministicPlanner();

        MobEntity mob = Mockito.mock(MobEntity.class);
        PetComponent component = Mockito.mock(PetComponent.class);
        Mockito.when(component.getOwnerUuid()).thenReturn(java.util.UUID.randomUUID());
        Mockito.when(component.getContextCache()).thenReturn(null);
        Mockito.when(component.getMoodEngine()).thenReturn(null);

        PetContext context = new PetContext(
            mob,
            component,
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
            true,
            0f,
            List.<Entity>of(),
            PetContextCrowdSummary.empty(),
            BlockPos.ORIGIN,
            200L,
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

        PlanResolution resolution = planner.resolvePlanWithContext(definition, context).orElse(null);
        assertNotNull(resolution);
        assertEquals(Identifier.of("test", "plan"), resolution.plan().id());
        assertEquals(1, resolution.steps().size());
        assertEquals(variant, resolution.steps().get(0).variant());
        assertNull(resolution.groupContext());
    }
}
