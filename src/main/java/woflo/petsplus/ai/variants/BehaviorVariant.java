package woflo.petsplus.ai.variants;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;

/**
 * Represents a high level animation/behaviour variant that can be picked for a plan step.
 */
public interface BehaviorVariant {
    Identifier id();

    boolean matches(GoalDefinition goal, PetContext context);

    String poseTag();
}

