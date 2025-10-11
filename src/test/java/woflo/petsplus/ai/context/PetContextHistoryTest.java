package woflo.petsplus.ai.context;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;
import woflo.petsplus.state.emotions.PetMoodEngine;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.context.social.SocialSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PetContextHistoryTest {

    @Test
    void captureUsesComponentHistorySnapshots() {
        MobEntity mob = Mockito.mock(MobEntity.class);
        World world = Mockito.mock(World.class);

        Mockito.when(mob.getEntityWorld()).thenReturn(world);
        Mockito.when(mob.getBoundingBox()).thenReturn(new Box(0, 0, 0, 1, 1, 1));
        Mockito.when(world.getOtherEntities(Mockito.eq(mob), Mockito.any(Box.class), Mockito.any())).thenReturn(List.of());
        Mockito.when(world.getTime()).thenReturn(1200L);
        Mockito.when(world.isDay()).thenReturn(true);
        Mockito.when(mob.getBlockPos()).thenReturn(BlockPos.ORIGIN);

        PetComponent component = Mockito.mock(PetComponent.class);
        PetMoodEngine moodEngine = Mockito.mock(PetMoodEngine.class);
        Mockito.when(component.getCurrentMood()).thenReturn(PetComponent.Mood.HAPPY);
        Mockito.when(component.getMoodLevel()).thenReturn(3);
        Mockito.when(component.getMoodBlend()).thenReturn(Map.of(PetComponent.Mood.HAPPY, 1.0f));
        Mockito.when(component.getActiveEmotions()).thenReturn(Map.of(PetComponent.Emotion.PLAYFULNESS, 0.6f));
        Mockito.when(component.getRecentGoalsSnapshot()).thenReturn(new ArrayDeque<>(List.of(Identifier.of("petsplus", "play"))));
        Mockito.when(component.getGoalExecutionTimestamps()).thenReturn(Map.of(Identifier.of("petsplus", "play"), 900L));
        Mockito.when(component.getQuirkCountersSnapshot()).thenReturn(Map.of("quirk", 2));
        Mockito.when(component.getRoleType()).thenReturn(null);
        Mockito.when(component.getNatureId()).thenReturn(null);
        Mockito.when(component.getNatureEmotionProfile()).thenReturn(null);
        Mockito.when(component.getLevel()).thenReturn(5);
        Mockito.when(component.getBondStrength()).thenReturn(5000L);
        Mockito.when(component.getMoodEngine()).thenReturn(moodEngine);
        Mockito.when(moodEngine.getBehaviouralEnergyProfile()).thenReturn(BehaviouralEnergyProfile.neutral());

        PetContext context = PetContext.capture(mob, component);

        assertEquals(Identifier.of("petsplus", "play"), context.recentGoals().peekFirst());
        assertEquals(900L, context.lastExecuted().getOrDefault(Identifier.of("petsplus", "play"), -1L));
        assertEquals(2, context.quirkCounters().getOrDefault("quirk", 0));
    }

    @Test
    void ticksSinceReflectsHistoryMap() {
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
            1000L,
            true,
            StimulusSnapshot.empty(),
            SocialSnapshot.empty(),
            false,
            new ArrayDeque<>(List.of(Identifier.of("petsplus", "play"))),
            Map.of(Identifier.of("petsplus", "play"), 400L),
            Map.of(),
            0.5f,
            BehaviouralEnergyProfile.neutral()
        );

        GoalDefinition dummy = new GoalDefinition(
            Identifier.of("petsplus", "play"),
            GoalDefinition.Category.PLAY,
            0,
            0,
            0,
            profile -> true,
            new net.minecraft.util.math.Vec2f(0.0f, 1.0f),
            GoalDefinition.IdleStaminaBias.CENTERED,
            false,
            mob -> null
        );

        assertEquals(600L, context.ticksSince(dummy));
    }
}
