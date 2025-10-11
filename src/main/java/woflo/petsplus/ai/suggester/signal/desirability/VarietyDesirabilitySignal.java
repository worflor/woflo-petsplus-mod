package woflo.petsplus.ai.suggester.signal.desirability;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.HashMap;
import java.util.Map;

public class VarietyDesirabilitySignal implements DesirabilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "desirability/variety");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        long ticksSince = ctx.ticksSince(goal);
        Identifier goalId = goal.id();
        float modifier = 1.0f;

        if (ticksSince < 100) {
            modifier = 0.3f;
        } else if (ticksSince < 300) {
            modifier = 0.7f;
        } else if (ticksSince < 600) {
            modifier = 0.9f;
        }

        if (modifier == 1.0f && ctx.recentGoals().contains(goalId)) {
            int position = 0;
            for (Identifier recent : ctx.recentGoals()) {
                if (recent.equals(goalId)) {
                    break;
                }
                position++;
            }

            if (position == 0) {
                modifier = 0.3f;
            } else if (position == 1) {
                modifier = 0.6f;
            } else if (position == 2) {
                modifier = 0.8f;
            }
        }

        Map<String, Object> trace = new HashMap<>();
        trace.put("ticksSince", ticksSince);
        trace.put("recent", ctx.recentGoals());
        trace.put("finalMultiplier", modifier);
        return new SignalResult(modifier, modifier, trace);
    }
}
