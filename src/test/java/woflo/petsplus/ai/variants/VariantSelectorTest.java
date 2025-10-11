package woflo.petsplus.ai.variants;

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
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariantSelectorTest {

    @AfterEach
    void cleanup() {
        BehaviorVariantRegistry.clear();
    }

    @Test
    void selectsMatchingVariantByDeterministicOrder() {
        GoalDefinition goal = new GoalDefinition(
            Identifier.of("test", "goal"),
            GoalDefinition.Category.PLAY,
            5,
            0,
            0,
            profile -> true,
            new net.minecraft.util.math.Vec2f(0.0f, 1.0f),
            GoalDefinition.IdleStaminaBias.CENTERED,
            false,
            mob -> null
        );

        BehaviorVariant matching = new BehaviorVariant() {
            @Override
            public Identifier id() { return Identifier.of("test", "match"); }
            @Override
            public boolean matches(GoalDefinition g, PetContext ctx) { return g == goal; }
            @Override
            public String poseTag() { return "pose"; }
        };
        BehaviorVariant other = new BehaviorVariant() {
            @Override
            public Identifier id() { return Identifier.of("test", "other"); }
            @Override
            public boolean matches(GoalDefinition g, PetContext ctx) { return false; }
            @Override
            public String poseTag() { return "pose"; }
        };

        BehaviorVariantRegistry.register(other);
        BehaviorVariantRegistry.register(matching);

        VariantSelector selector = new VariantSelector();
        PetContext context = new PetContext(
            Mockito.mock(MobEntity.class),
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
            0L,
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

        assertTrue(selector.select(goal, context).isPresent());
        assertEquals(Identifier.of("test", "match"), selector.select(goal, context).get().id());
        assertEquals(Identifier.of("test", "match"), selector.select(goal, context, List.of(Identifier.of("test", "match"))).get().id());
    }
}

