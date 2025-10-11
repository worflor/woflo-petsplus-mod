package woflo.petsplus.ai.planner;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for planner fragments and plans. Supports runtime data reload for pack-defined plans.
 */
public final class PlanRegistry {

    private static final Map<Identifier, ActionFragment> FRAGMENTS = new LinkedHashMap<>();
    private static final Map<Identifier, ActionPlan> PLANS = new LinkedHashMap<>();
    private static final Map<Identifier, Identifier> PLAN_BY_GOAL = new HashMap<>();

    private PlanRegistry() {}

    public static void clearDataDriven() {
        FRAGMENTS.clear();
        PLANS.clear();
        PLAN_BY_GOAL.clear();
    }

    public static void registerFragment(ActionFragment fragment) {
        FRAGMENTS.put(fragment.id(), fragment);
    }

    public static void registerPlan(ActionPlan plan) {
        PLANS.put(plan.id(), plan);
        PLAN_BY_GOAL.put(plan.goalId(), plan.id());
    }

    public static Optional<ActionPlan> getPlanForGoal(Identifier goalId) {
        Identifier planId = PLAN_BY_GOAL.get(goalId);
        if (planId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(PLANS.get(planId));
    }

    public static Collection<ActionFragment> fragments() {
        return Collections.unmodifiableCollection(FRAGMENTS.values());
    }
}

