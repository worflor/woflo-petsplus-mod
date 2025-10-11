package woflo.petsplus.ai.planner;

import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.group.GroupContext;
import woflo.petsplus.ai.variants.BehaviorVariant;

import java.util.List;

/**
 * Immutable result produced by the deterministic planner that resolves the
 * action plan, variant selections, and any group coordination context. The
 * resolution is cached by {@link DeterministicPlanner} so repeated scenarios
 * can reuse previous computations deterministically.
 */
public record PlanResolution(
    ActionPlan plan,
    List<ResolvedStep> steps,
    @Nullable GroupContext groupContext,
    String signature
) {
    public PlanResolution {
        steps = steps == null || steps.isEmpty() ? List.of() : List.copyOf(steps);
    }

    public record ResolvedStep(ActionPlan.Step step, @Nullable BehaviorVariant variant) {
        public ResolvedStep {
            if (step == null) {
                throw new IllegalArgumentException("step");
            }
        }
    }
}
