package woflo.petsplus.ai.movement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalMovementConfig;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.state.PetComponent;

import static org.junit.jupiter.api.Assertions.*;

class MovementDirectorLeadershipTest {

    @AfterEach
    void resetDataDriven() {
        GoalRegistry.clearDataDriven();
    }

    @Test
    void combatAssistPreemptsShelterMovement() {
        GoalMovementConfig shelterConfig = GoalRegistry.movementConfig(GoalIds.SHELTER_FROM_RAIN);
        GoalMovementConfig assistConfig = GoalRegistry.movementConfig(GoalIds.OWNER_ASSIST_ATTACK);

        assertNotNull(shelterConfig, "Shelter movement config should be registered");
        assertNotNull(assistConfig, "Owner assist movement config should be registered");
        assertTrue(assistConfig.actorPriority() < shelterConfig.actorPriority(), "Combat assist priority must outrank shelter");

        PetComponent.MovementDirector director = new PetComponent.MovementDirector(null);

        assertTrue(director.canActivateActor(GoalIds.SHELTER_FROM_RAIN, shelterConfig.actorPriority()));
        director.activateActor(GoalIds.SHELTER_FROM_RAIN, shelterConfig.actorPriority());
        assertTrue(director.isActorLeading(GoalIds.SHELTER_FROM_RAIN));

        assertTrue(director.canActivateActor(GoalIds.OWNER_ASSIST_ATTACK, assistConfig.actorPriority()));
        director.activateActor(GoalIds.OWNER_ASSIST_ATTACK, assistConfig.actorPriority());
        assertTrue(director.isActorLeading(GoalIds.OWNER_ASSIST_ATTACK));

        director.deactivateActor(GoalIds.SHELTER_FROM_RAIN);
        assertFalse(director.canActivateActor(GoalIds.SHELTER_FROM_RAIN, shelterConfig.actorPriority()));

        director.deactivateActor(GoalIds.OWNER_ASSIST_ATTACK);
        assertTrue(director.canActivateActor(GoalIds.SHELTER_FROM_RAIN, shelterConfig.actorPriority()));
    }
}
