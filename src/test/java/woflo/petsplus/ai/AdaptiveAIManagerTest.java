package woflo.petsplus.ai;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.goals.GoalType;
import woflo.petsplus.ai.goals.social.EyeContactGoal;
import woflo.petsplus.ai.goals.social.ParallelPlayGoal;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.mixin.MobEntityAccessor;
import woflo.petsplus.state.PetComponent;

import java.util.LinkedHashSet;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdaptiveAIManagerTest {

    @Test
    void reinitializeAddsSocialGoalsWhenOwnerOffline() {
        MobEntity pet = mock(MobEntity.class, withSettings().extraInterfaces(MobEntityAccessor.class, PetsplusTameable.class));
        PetsplusTameable petsplus = (PetsplusTameable) pet;
        GoalSelector goalSelector = mock(GoalSelector.class);
        GoalSelector targetSelector = mock(GoalSelector.class);

        Set<Object> goals = new LinkedHashSet<>();
        when(goalSelector.getGoals()).thenReturn((Set) goals);
        when(targetSelector.getGoals()).thenReturn((Set) new LinkedHashSet<>());

        when(((MobEntityAccessor) pet).getGoalSelector()).thenReturn(goalSelector);
        when(((MobEntityAccessor) pet).getTargetSelector()).thenReturn(targetSelector);

        UUID ownerUuid = UUID.randomUUID();
        when(petsplus.petsplus$isTamed()).thenReturn(true);
        when(petsplus.petsplus$getOwnerUuid()).thenReturn(null);
        when(petsplus.petsplus$getOwner()).thenReturn(null);

        World world = new World(null, false);
        when(pet.getEntityWorld()).thenReturn(world);

        PetComponent component = mock(PetComponent.class);
        when(component.getOwnerUuid()).thenReturn(ownerUuid);

        try (MockedStatic<PetComponent> components = Mockito.mockStatic(PetComponent.class);
             MockedStatic<MobCapabilities> capabilities = Mockito.mockStatic(MobCapabilities.class)) {
            components.when(() -> PetComponent.get(pet)).thenReturn(component);

            MobCapabilities.CapabilityProfile profile = new MobCapabilities.CapabilityProfile(
                true,  // canWander
                false, // canFly
                false, // canSwim
                false, // canJump
                true,  // hasOwner
                false, // canPickUpItems
                false, // hasInventory
                false, // canSit
                false, // canMakeSound
                false, // prefersLand
                false, // prefersWater
                false, // prefersAir
                false  // isSmallSize
            );
            capabilities.when(() -> MobCapabilities.analyze(pet)).thenReturn(profile);

            AdaptiveAIManager.reinitializeAdaptiveAI(pet);

            verify(goalSelector, atLeastOnce()).add(anyInt(), any());

            verify(goalSelector, atLeastOnce()).add(anyInt(), argThat(goal -> goal instanceof ParallelPlayGoal));
            verify(goalSelector).add(eq(GoalType.EYE_CONTACT.getPriority()), argThat(goal ->
                goal instanceof EyeContactGoal eyeContact &&
                    eyeContact.getControls().equals(EnumSet.of(Goal.Control.LOOK))
            ));
        }
    }
}

