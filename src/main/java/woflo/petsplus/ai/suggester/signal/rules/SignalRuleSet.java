package woflo.petsplus.ai.suggester.signal.rules;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.goals.GoalDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Declarative rule describing how a specific mood or emotion influences a
 * goal's desirability. The rule stores per-category and per-goal multipliers so
 * callers can multiply them in a deterministic order while still reporting each
 * contribution in signal traces.
 */
public record SignalRuleSet(
    float threshold,
    Map<GoalDefinition.Category, List<Float>> categoryMultipliers,
    Map<Identifier, List<Float>> goalMultipliers
) {
    public static SignalRuleSet empty() {
        return new SignalRuleSet(0.0f, Map.of(), Map.of());
    }

    public List<Float> categoryMultipliersFor(GoalDefinition.Category category) {
        List<Float> values = categoryMultipliers.get(category);
        return values != null ? values : Collections.emptyList();
    }

    public List<Float> goalMultipliersFor(Identifier goalId) {
        List<Float> values = goalMultipliers.get(goalId);
        return values != null ? values : Collections.emptyList();
    }
}
