package woflo.petsplus.ai.suggester.signal.feasibility;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.suggester.signal.FeasibilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.Map;

public class CombatLockoutFeasibilitySignal implements FeasibilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "feasibility/combat_lockout");

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

        if (mob.getAttacker() != null || mob.getAttacking() != null) {
            return new SignalResult(0.0f, 0.0f, Map.of("reason", "combat"));
        }

        return SignalResult.identity();
    }
}
