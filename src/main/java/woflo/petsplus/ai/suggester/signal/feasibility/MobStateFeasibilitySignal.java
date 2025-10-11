package woflo.petsplus.ai.suggester.signal.feasibility;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.suggester.signal.FeasibilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.Map;

public class MobStateFeasibilitySignal implements FeasibilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "feasibility/mob_state");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        MobEntity mob = ctx.mob();
        if (mob == null) {
            return SignalResult.identity();
        }

        float modifier = 1.0f;
        if (!mob.isOnGround() && !mob.isTouchingWater() && !mob.hasVehicle()) {
            modifier *= 0.3f;
        }

        return new SignalResult(modifier, modifier, Map.of());
    }
}
