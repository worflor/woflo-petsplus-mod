package woflo.petsplus.ai.suggester.signal;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;

public interface FeasibilitySignal {
    Identifier id();

    SignalResult evaluate(GoalDefinition goal, PetContext context);
}
