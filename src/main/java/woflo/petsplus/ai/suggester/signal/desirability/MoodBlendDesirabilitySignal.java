package woflo.petsplus.ai.suggester.signal.desirability;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.perception.ContextSlice;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;
import woflo.petsplus.ai.suggester.signal.rules.MoodSignalRules;
import woflo.petsplus.ai.suggester.signal.rules.SignalRuleRegistry;
import woflo.petsplus.ai.suggester.signal.rules.SignalRuleSet;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
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
            return SignalResult.identity();
        }

        MoodSignalRules rules = SignalRuleRegistry.moodRules();
        float modifier = 1.0f;

        modifier = applyMoodRules(ctx, goal, modifier, rules);
        modifier = applyEmotionRules(ctx, goal, modifier, rules);

        return new SignalResult(modifier, modifier, null);
    }

    @Override
    public EnumSet<ContextSlice> observedSlices(GoalDefinition goal) {
        return EnumSet.of(ContextSlice.MOOD, ContextSlice.EMOTIONS);
    }

    private float applyMoodRules(
        PetContext ctx,
        GoalDefinition goal,
        float modifier,
        MoodSignalRules rules
    ) {
        float result = modifier;
        for (Map.Entry<PetComponent.Mood, SignalRuleSet> entry : rules.moodRules().entrySet()) {
            SignalRuleSet rule = entry.getValue();
            if (!ctx.hasMoodInBlend(entry.getKey(), rule.threshold())) {
                continue;
            }
            result = applyRuleSet(goal, result, rule);
        }
        return result;
    }

    private float applyEmotionRules(
        PetContext ctx,
        GoalDefinition goal,
        float modifier,
        MoodSignalRules rules
    ) {
        float result = modifier;
        for (Map.Entry<PetComponent.Emotion, SignalRuleSet> entry : rules.emotionRules().entrySet()) {
            SignalRuleSet rule = entry.getValue();
            if (!ctx.hasEmotionAbove(entry.getKey(), rule.threshold())) {
                continue;
            }
            result = applyRuleSet(goal, result, rule);
        }
        return result;
    }

    private float applyRuleSet(
        GoalDefinition goal,
        float modifier,
        SignalRuleSet rule
    ) {
        float result = modifier;
        for (float multiplier : rule.categoryMultipliersFor(goal.category())) {
            result *= multiplier;
        }
        for (float multiplier : rule.goalMultipliersFor(goal.id())) {
            result *= multiplier;
        }
        return result;
    }

}
