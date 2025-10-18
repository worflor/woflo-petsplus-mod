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

    @Test
    void selfPreservationOverridesFollowAndShelter() {
        GoalMovementConfig followConfig = GoalRegistry.movementConfig(GoalIds.FOLLOW_OWNER);
        GoalMovementConfig shelterConfig = GoalRegistry.movementConfig(GoalIds.SHELTER_FROM_RAIN);
        GoalMovementConfig fearConfig = GoalRegistry.movementConfig(GoalIds.SELF_PRESERVATION);

        assertNotNull(followConfig, "Follow owner movement config should be registered");
        assertNotNull(shelterConfig, "Shelter movement config should be registered");
        assertNotNull(fearConfig, "Self-preservation movement config should be registered");

        assertTrue(fearConfig.actorPriority() <= followConfig.actorPriority(), "Fear priority must outrank follow");
        assertTrue(fearConfig.actorPriority() <= shelterConfig.actorPriority(), "Fear priority must outrank shelter");

        PetComponent.MovementDirector director = new PetComponent.MovementDirector(null);

        director.activateActor(GoalIds.FOLLOW_OWNER, followConfig.actorPriority());
        assertTrue(director.isActorLeading(GoalIds.FOLLOW_OWNER));

        assertTrue(director.canActivateActor(GoalIds.SELF_PRESERVATION, fearConfig.actorPriority()), "Fear should activate over follow");
        director.activateActor(GoalIds.SELF_PRESERVATION, fearConfig.actorPriority());
        assertTrue(director.isActorLeading(GoalIds.SELF_PRESERVATION));

        director.deactivateActor(GoalIds.SELF_PRESERVATION);
        director.activateActor(GoalIds.SHELTER_FROM_RAIN, shelterConfig.actorPriority());
        assertTrue(director.isActorLeading(GoalIds.SHELTER_FROM_RAIN));

        assertTrue(director.canActivateActor(GoalIds.SELF_PRESERVATION, fearConfig.actorPriority()), "Fear should activate over shelter");
    }
}
