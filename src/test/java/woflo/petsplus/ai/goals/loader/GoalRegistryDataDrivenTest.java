package woflo.petsplus.ai.goals.loader;

import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalMovementConfig;
import woflo.petsplus.ai.goals.GoalMovementConfig.MovementRole;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.movement.MovementCommand;
import woflo.petsplus.state.PetComponent;

import static org.junit.jupiter.api.Assertions.*;

class GoalRegistryDataDrivenTest {

    private static final Identifier TEST_ID = Identifier.of("petsplus", "test_actor_goal");

    @AfterEach
    void tearDown() {
        GoalRegistry.clearDataDriven();
    }

    @Test
    void dataDrivenGoalReceivesActorMovementRole() {
        JsonObject json = new JsonObject();
        json.addProperty("category", "special");
        json.addProperty("priority", 7);

        JsonObject cooldown = new JsonObject();
        cooldown.addProperty("min", 0);
        cooldown.addProperty("max", 0);
        json.add("cooldown", cooldown);

        json.addProperty("requirement", "has_owner");
        json.addProperty("goal_factory", "woflo.petsplus.ai.goals.follow.FollowOwnerAdaptiveGoal");
        json.addProperty("movement_role", "actor");

        GoalDataParser.ParsedGoal parsed = GoalDataParser.parse(TEST_ID, json);
        assertNotNull(parsed.movementConfig());

        GoalRegistry.registerDataDriven(parsed.definition(), parsed.movementConfig());

        GoalMovementConfig config = GoalRegistry.movementConfig(TEST_ID);
        assertEquals(MovementRole.ACTOR, config.role());
        assertEquals(7, config.actorPriority());

        PetComponent.MovementDirector director = new PetComponent.MovementDirector(null);

        Vec3d target = new Vec3d(1.0, 0.0, 0.0);
        double desiredDistance = 2.0;
        double speed = 0.6;

        assertTrue(director.canActivateActor(TEST_ID, config.actorPriority()));
        director.activateActor(TEST_ID, config.actorPriority());
        assertTrue(director.isActorLeading(TEST_ID));

        MovementCommand resolved = director.resolveMovement(TEST_ID, target, desiredDistance, speed);
        assertEquals(speed, resolved.speed(), 1.0e-6);
        assertEquals(target.x, resolved.targetPosition().x, 1.0e-6);
        assertEquals(target.y, resolved.targetPosition().y, 1.0e-6);
        assertEquals(target.z, resolved.targetPosition().z, 1.0e-6);
    }

    @Test
    void dataDrivenPriorityOverrideRefreshesActorPriority() {
        GoalMovementConfig initialConfig = GoalRegistry.movementConfig(GoalIds.FOLLOW_OWNER);
        assertEquals(GoalMovementConfig.MovementRole.ACTOR, initialConfig.role());

        int overriddenPriority = initialConfig.actorPriority() + 4;

        JsonObject json = new JsonObject();
        json.addProperty("category", "special");
        json.addProperty("priority", overriddenPriority);

        JsonObject cooldown = new JsonObject();
        cooldown.addProperty("min", 0);
        cooldown.addProperty("max", 0);
        json.add("cooldown", cooldown);

        json.addProperty("requirement", "has_owner");
        json.addProperty("goal_factory", "woflo.petsplus.ai.goals.follow.FollowOwnerAdaptiveGoal");

        GoalDataParser.ParsedGoal parsed = GoalDataParser.parse(GoalIds.FOLLOW_OWNER, json);
        assertNull(parsed.movementConfig());

        GoalRegistry.registerDataDriven(parsed.definition(), parsed.movementConfig());

        GoalMovementConfig refreshedConfig = GoalRegistry.movementConfig(GoalIds.FOLLOW_OWNER);
        assertEquals(GoalMovementConfig.MovementRole.ACTOR, refreshedConfig.role());
        assertEquals(overriddenPriority, refreshedConfig.actorPriority());

        PetComponent.MovementDirector director = new PetComponent.MovementDirector(null);
        assertTrue(director.canActivateActor(GoalIds.FOLLOW_OWNER, refreshedConfig.actorPriority()));
        director.activateActor(GoalIds.FOLLOW_OWNER, refreshedConfig.actorPriority());
        assertTrue(director.isActorLeading(GoalIds.FOLLOW_OWNER));
    }
}
