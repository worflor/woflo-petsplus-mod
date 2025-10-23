package woflo.petsplus.ai.suggester.signal;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.perception.ContextSlice;
import woflo.petsplus.ai.goals.GoalDefinition;

import java.util.EnumSet;

public interface FeasibilitySignal {
    Identifier id();

    SignalResult evaluate(GoalDefinition goal, PetContext context);

    default EnumSet<ContextSlice> observedSlices(GoalDefinition goal) {
        return EnumSet.of(ContextSlice.ALL);
    }
}
