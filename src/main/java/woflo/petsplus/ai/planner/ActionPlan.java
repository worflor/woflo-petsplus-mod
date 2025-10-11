package woflo.petsplus.ai.planner;

import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;

/**
 * Immutable sequence of fragments associated with a particular goal definition.
 */
public record ActionPlan(
    Identifier id,
    Identifier goalId,
    List<Step> steps,
    boolean requiresOwnerGroup
) {
    public ActionPlan {
        if (id == null) {
            throw new IllegalArgumentException("id");
        }
        if (goalId == null) {
            throw new IllegalArgumentException("goalId");
        }
        steps = steps == null || steps.isEmpty() ? List.of() : List.copyOf(steps);
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public Step firstStep() {
        return steps.isEmpty() ? null : steps.get(0);
    }

    public record Step(
        Identifier fragmentId,
        List<Identifier> variantPool,
        List<String> tags
    ) {
        public Step {
            if (fragmentId == null) {
                throw new IllegalArgumentException("fragmentId");
            }
            variantPool = variantPool == null || variantPool.isEmpty()
                ? List.of()
                : Collections.unmodifiableList(variantPool);
            tags = tags == null || tags.isEmpty()
                ? List.of()
                : List.copyOf(tags);
        }
    }
}

