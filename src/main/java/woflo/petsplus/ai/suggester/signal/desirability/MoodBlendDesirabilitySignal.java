package woflo.petsplus.ai.suggester.signal.desirability;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;
import woflo.petsplus.ai.suggester.signal.rules.MoodSignalRules;
import woflo.petsplus.ai.suggester.signal.rules.SignalRuleRegistry;
import woflo.petsplus.ai.suggester.signal.rules.SignalRuleSet;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoodBlendDesirabilitySignal implements DesirabilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "desirability/mood_blend");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        if (!ctx.hasPetsPlusComponent()) {
            return new SignalResult(1.0f, 1.0f, Map.of());
        }

        MoodSignalRules rules = SignalRuleRegistry.moodRules();
        float modifier = 1.0f;
        List<Map<String, Object>> contributions = new ArrayList<>();

        modifier = applyMoodRules(ctx, goal, modifier, contributions, rules);
        modifier = applyEmotionRules(ctx, goal, modifier, contributions, rules);

        Map<String, Object> trace = new HashMap<>();
        trace.put("finalMultiplier", modifier);
        if (!contributions.isEmpty()) {
            trace.put("entries", List.copyOf(contributions));
        }

        return new SignalResult(modifier, modifier, trace);
    }

    private float applyMoodRules(
        PetContext ctx,
        GoalDefinition goal,
        float modifier,
        List<Map<String, Object>> contributions,
        MoodSignalRules rules
    ) {
        float result = modifier;
        for (Map.Entry<PetComponent.Mood, SignalRuleSet> entry : rules.moodRules().entrySet()) {
            SignalRuleSet rule = entry.getValue();
            if (!ctx.hasMoodInBlend(entry.getKey(), rule.threshold())) {
                continue;
            }
            result = applyRuleSet("mood", entry.getKey().name(), goal, result, contributions, rule);
        }
        return result;
    }

    private float applyEmotionRules(
        PetContext ctx,
        GoalDefinition goal,
        float modifier,
        List<Map<String, Object>> contributions,
        MoodSignalRules rules
    ) {
        float result = modifier;
        for (Map.Entry<PetComponent.Emotion, SignalRuleSet> entry : rules.emotionRules().entrySet()) {
            SignalRuleSet rule = entry.getValue();
            if (!ctx.hasEmotionAbove(entry.getKey(), rule.threshold())) {
                continue;
            }
            result = applyRuleSet("emotion", entry.getKey().name(), goal, result, contributions, rule);
        }
        return result;
    }

    private float applyRuleSet(
        String sourceType,
        String sourceName,
        GoalDefinition goal,
        float modifier,
        List<Map<String, Object>> contributions,
        SignalRuleSet rule
    ) {
        float result = modifier;
        for (float multiplier : rule.categoryMultipliersFor(goal.category())) {
            result *= multiplier;
            addContribution(contributions, sourceType, sourceName, "category", goal.category().name(), multiplier);
        }
        for (float multiplier : rule.goalMultipliersFor(goal.id())) {
            result *= multiplier;
            addContribution(contributions, sourceType, sourceName, "goal", goal.id().toString(), multiplier);
        }
        return result;
    }

    private static void addContribution(List<Map<String, Object>> contributions, String sourceType, String sourceName, String targetType, String target, float multiplier) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("sourceType", sourceType);
        entry.put("source", sourceName);
        entry.put("targetType", targetType);
        entry.put("target", target);
        entry.put("multiplier", multiplier);
        contributions.add(entry);
    }

}
