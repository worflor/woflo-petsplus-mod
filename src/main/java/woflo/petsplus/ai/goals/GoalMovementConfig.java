package woflo.petsplus.ai.goals;

import net.minecraft.util.math.MathHelper;

/**
 * Describes how an adaptive goal interacts with the {@link woflo.petsplus.state.PetComponent.MovementDirector}.
 * Goals that directly steer the pet's navigation are tagged as {@link MovementRole#ACTOR} while
 * goals that merely nudge the steering through modifiers are tagged as {@link MovementRole#INFLUENCER}.
 * Baseline actors can reclaim leadership when critical autopilot behaviour is required (e.g. follow owner).
 */
public record GoalMovementConfig(
    MovementRole role,
    int actorPriority,
    float influencerWeight,
    boolean baselineActor
) {

    public GoalMovementConfig {
        role = role == null ? MovementRole.NONE : role;
        influencerWeight = MathHelper.clamp(Float.isNaN(influencerWeight) ? 0f : influencerWeight, 0f, 32f);

        if (role != MovementRole.ACTOR) {
            actorPriority = Integer.MIN_VALUE;
            baselineActor = false;
        } else {
            actorPriority = Math.max(Integer.MIN_VALUE, actorPriority);
        }
    }

    /**
     * Configuration for a goal that does not participate in movement resolution.
     */
    public static GoalMovementConfig none() {
        return new GoalMovementConfig(MovementRole.NONE, Integer.MIN_VALUE, 0f, false);
    }

    /**
     * Configuration for a goal that acts as a primary actor and therefore receives an actor priority.
     */
    public static GoalMovementConfig actor(int priority) {
        return new GoalMovementConfig(MovementRole.ACTOR, priority, 0f, false);
    }

    /**
     * Configuration for a goal that acts as a baseline autopilot actor and therefore can reclaim leadership.
     */
    public static GoalMovementConfig baselineActor(int priority) {
        return new GoalMovementConfig(MovementRole.ACTOR, priority, 0f, true);
    }

    /**
     * Configuration for a goal that contributes modifiers without claiming direct movement.
     */
    public static GoalMovementConfig influencer(float weight) {
        return new GoalMovementConfig(MovementRole.INFLUENCER, Integer.MIN_VALUE, weight, false);
    }

    public enum MovementRole {
        NONE,
        ACTOR,
        INFLUENCER
    }
}

