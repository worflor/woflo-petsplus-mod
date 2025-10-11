package woflo.petsplus.ai.suggester.signal.feasibility;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.suggester.signal.FeasibilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.Map;

public class SocialProximityFeasibilitySignal implements FeasibilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "feasibility/social_proximity");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        if (goal.category() != GoalDefinition.Category.SOCIAL) {
            return SignalResult.identity();
        }

        if (!ctx.ownerNearby()) {
            return new SignalResult(0.0f, 0.0f, Map.of("reason", "owner_absent"));
        }

        float distance = ctx.distanceToOwner();
        float applied = Math.max(0.2f, 1.0f - (distance / 16.0f));
        return new SignalResult(applied, applied, Map.of("distance", distance));
    }
}
