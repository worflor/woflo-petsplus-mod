package woflo.petsplus.ai.suggester.signal.feasibility;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.perception.ContextSlice;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.suggester.signal.FeasibilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.EnumSet;

public class ActiveGoalFeasibilitySignal implements FeasibilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "feasibility/active_goal");
    private final long gracePeriodTicks;

    public ActiveGoalFeasibilitySignal() {
        this(60L);
    }

    public ActiveGoalFeasibilitySignal(long gracePeriodTicks) {
        this.gracePeriodTicks = Math.max(0L, gracePeriodTicks);
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        Identifier activeGoalId = ctx.activeAdaptiveGoalId();
        if (activeGoalId == null || goal == null) {
            return SignalResult.identity();
        }

        if (goal.id().equals(activeGoalId)) {
            return SignalResult.identity();
        }

        long startTick = ctx.activeAdaptiveGoalStartTick();
        if (startTick == Long.MIN_VALUE) {
            return SignalResult.identity();
        }

        long now = ctx.worldTime();
        long elapsed = Math.max(0L, now - startTick);
        if (elapsed <= gracePeriodTicks) {
            return new SignalResult(0.0f, 0.0f, "active_goal_grace");
        }

        return SignalResult.identity();
    }

    @Override
    public EnumSet<ContextSlice> observedSlices(GoalDefinition goal) {
        return EnumSet.of(ContextSlice.HISTORY, ContextSlice.STATE_DATA);
    }
}
