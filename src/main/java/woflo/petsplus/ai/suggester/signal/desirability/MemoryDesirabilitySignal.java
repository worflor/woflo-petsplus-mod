package woflo.petsplus.ai.suggester.signal.desirability;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.perception.ContextSlice;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.EnumSet;

public class MemoryDesirabilitySignal implements DesirabilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "desirability/memory");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        // Placeholder until Phase 5 introduces actual goal memory data.
        return SignalResult.identity();
    }

    @Override
    public EnumSet<ContextSlice> observedSlices(GoalDefinition goal) {
        return EnumSet.of(ContextSlice.HISTORY);
    }
}
