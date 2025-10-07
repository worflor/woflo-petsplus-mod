package woflo.petsplus.ai;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import woflo.petsplus.mixin.MobEntityAccessor;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.api.entity.PetsplusTameable;

import java.util.LinkedHashSet;

import static org.mockito.Mockito.*;

class PetAIEnhancementsTest {

    @Test
    void enhancePetAIReinitializesAdaptiveGoalsEachTime() {
        MobEntity pet = mock(MobEntity.class, withSettings().extraInterfaces(MobEntityAccessor.class, PetsplusTameable.class));
        World world = new World(null, false);
        when(pet.getWorld()).thenReturn(world);

        PetComponent component = mock(PetComponent.class);
        when(component.getRoleId()).thenReturn(Identifier.of("petsplus", "guardian"));

        GoalSelector goalSelector = mock(GoalSelector.class);
        when(goalSelector.getGoals()).thenReturn(new LinkedHashSet<>());

        GoalSelector targetSelector = mock(GoalSelector.class);
        when(targetSelector.getGoals()).thenReturn(new LinkedHashSet<>());

        when(((MobEntityAccessor) pet).getGoalSelector()).thenReturn(goalSelector);
        when(((MobEntityAccessor) pet).getTargetSelector()).thenReturn(targetSelector);

        try (MockedStatic<AdaptiveAIManager> adaptive = Mockito.mockStatic(AdaptiveAIManager.class)) {
            PetAIEnhancements.enhancePetAI(pet, component);
            PetAIEnhancements.enhancePetAI(pet, component);

            adaptive.verify(() -> AdaptiveAIManager.reinitializeAdaptiveAI(pet), times(2));
            adaptive.verify(() -> AdaptiveAIManager.initializeAdaptiveAI(pet), never());
        }
    }
}
