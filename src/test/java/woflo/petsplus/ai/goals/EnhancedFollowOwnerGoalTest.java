package woflo.petsplus.ai.goals;

import org.junit.jupiter.api.Test;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetAIState;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnhancedFollowOwnerGoalTest {

    @Test
    void majorActivityBlocksFollow() {
        PetAIState aiState = new PetAIState();
        aiState.setActiveMajorGoal(GoalIds.FETCH_ITEM);
        EnhancedFollowOwnerGoal goal = createGoal(aiState);

        assertTrue(goal.isMajorActivityActive(), "Expected major activity to suppress follow");
    }

    @Test
    void nonMajorActivityDoesNotBlockFollow() {
        PetAIState aiState = new PetAIState();
        aiState.setActiveMajorGoal(GoalIds.CASUAL_WANDER);
        EnhancedFollowOwnerGoal goal = createGoal(aiState);

        assertFalse(goal.isMajorActivityActive(), "Non-major activity should not suppress follow");
    }

    @Test
    void followResumesAfterMajorActivityClears() {
        PetAIState aiState = new PetAIState();
        aiState.setActiveMajorGoal(GoalIds.FETCH_ITEM);
        EnhancedFollowOwnerGoal goal = createGoal(aiState);
        assertTrue(goal.isMajorActivityActive());

        aiState.setActiveMajorGoal(null);

        assertFalse(goal.isMajorActivityActive(), "Follow should resume once the major activity clears");
    }

    private EnhancedFollowOwnerGoal createGoal(PetAIState aiState) {
        PetComponent petComponent = mock(PetComponent.class);
        when(petComponent.getAIState()).thenReturn(aiState);
        PetsplusTameable tameable = mock(PetsplusTameable.class);
        return new EnhancedFollowOwnerGoal(null, tameable, petComponent, 1.0, 3.0f, 12.0f);
    }
}
