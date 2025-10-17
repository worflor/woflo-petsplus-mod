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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

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
    void preferredFenceNodesAreNoLongerHazards() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        PetComponent petComponent = mockPetComponent();
        PetsplusTameable tameable = null;
        TestableEnhancedFollowOwnerGoal goal = new TestableEnhancedFollowOwnerGoal(tameable, petComponent);

        WorldView world = null;
        BlockPos node = BlockPos.ORIGIN;

        goal.setNodeType(node, PathNodeType.FENCE);

        boolean hazardous = invokeIsNodeHazardous(goal, world, node);
        assertFalse(hazardous, "Preferred structural nodes should not be treated as hazards");
    }

    @Test
    void lavaNodesRemainHazardous() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        PetComponent petComponent = mockPetComponent();
        PetsplusTameable tameable = null;
        TestableEnhancedFollowOwnerGoal goal = new TestableEnhancedFollowOwnerGoal(tameable, petComponent);

        WorldView world = null;
        BlockPos node = BlockPos.ORIGIN;

        goal.setNodeType(node, PathNodeType.LAVA);

        boolean hazardous = invokeIsNodeHazardous(goal, world, node);
        assertTrue(hazardous, "Dangerous nodes like lava must still be rejected");
    }

    private boolean invokeIsNodeHazardous(EnhancedFollowOwnerGoal goal, WorldView world, BlockPos pos)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = EnhancedFollowOwnerGoal.class.getDeclaredMethod("isNodeHazardous", WorldView.class, BlockPos.class);
        method.setAccessible(true);
        return (boolean) method.invoke(goal, world, pos);
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
