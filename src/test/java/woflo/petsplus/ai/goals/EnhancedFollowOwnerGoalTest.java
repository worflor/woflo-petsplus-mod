package woflo.petsplus.ai.goals;

import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.state.PetAIState;
import woflo.petsplus.state.PetComponent;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void preferredFenceNodesAreNoLongerHazards() {
        PetComponent petComponent = mockPetComponent();
        PetsplusTameable tameable = null;
        TestableEnhancedFollowOwnerGoal goal = new TestableEnhancedFollowOwnerGoal(tameable, petComponent);

        EnhancedFollowOwnerGoal.HazardLevel hazard = goal.classifyNodeType(PathNodeType.FENCE);
        assertEquals(EnhancedFollowOwnerGoal.HazardLevel.SAFE, hazard,
            "Preferred structural nodes should not be treated as hazards");
    }

    @Test
    void lavaNodesRemainHazardous() {
        PetComponent petComponent = mockPetComponent();
        PetsplusTameable tameable = null;
        TestableEnhancedFollowOwnerGoal goal = new TestableEnhancedFollowOwnerGoal(tameable, petComponent);

        EnhancedFollowOwnerGoal.HazardLevel hazard = goal.classifyNodeType(PathNodeType.LAVA);
        assertEquals(EnhancedFollowOwnerGoal.HazardLevel.SEVERE, hazard,
            "Dangerous nodes like lava must still be rejected");
    }

    @Test
    void environmentalTrapsRemainHazardous() {
        PetComponent petComponent = mockPetComponent();
        PetsplusTameable tameable = null;
        TestableEnhancedFollowOwnerGoal goal = new TestableEnhancedFollowOwnerGoal(tameable, petComponent);

        EnhancedFollowOwnerGoal.HazardLevel hazard = goal.classifyNodeType(PathNodeType.DANGER_OTHER);
        assertEquals(EnhancedFollowOwnerGoal.HazardLevel.SEVERE, hazard,
            "Environmental traps should continue to be treated as severe hazards");
    }

    @Test
    void stickyTerrainCountsAsSoftHazard() {
        PetComponent petComponent = mockPetComponent();
        PetsplusTameable tameable = null;
        TestableEnhancedFollowOwnerGoal goal = new TestableEnhancedFollowOwnerGoal(tameable, petComponent);

        EnhancedFollowOwnerGoal.HazardLevel hazard = goal.classifyNodeType(PathNodeType.STICKY_HONEY);
        assertEquals(EnhancedFollowOwnerGoal.HazardLevel.SOFT, hazard,
            "Sticky blocks should slow pets without fully blocking movement");
    }

    private EnhancedFollowOwnerGoal createGoal(PetAIState aiState) {
        PetComponent petComponent = mockPetComponent();
        when(petComponent.getAIState()).thenReturn(aiState);
        PetsplusTameable tameable = null;
        return new EnhancedFollowOwnerGoal(null, tameable, petComponent, 1.0, 3.0f, 12.0f);
    }

    private PetComponent mockPetComponent() {
        return mock(PetComponent.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
    }

    private static final class TestableEnhancedFollowOwnerGoal extends EnhancedFollowOwnerGoal {
        private final Map<BlockPos, PathNodeType> overrides = new HashMap<>();

        TestableEnhancedFollowOwnerGoal(PetsplusTameable tameable, PetComponent petComponent) {
            super(null, tameable, petComponent, 1.0, 3.0f, 12.0f);
        }

        void setNodeType(BlockPos pos, PathNodeType type) {
            overrides.put(pos, type);
        }

        @Override
        protected PathNodeType resolveNodeType(WorldView world, BlockPos pos) {
            return overrides.getOrDefault(pos, PathNodeType.OPEN);
        }
    }

}
