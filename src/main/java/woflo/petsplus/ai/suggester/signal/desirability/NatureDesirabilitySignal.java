package woflo.petsplus.ai.suggester.signal.desirability;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;
import woflo.petsplus.ai.suggester.signal.rules.NatureSignalRules;
import woflo.petsplus.ai.suggester.signal.rules.SignalRuleRegistry;
import woflo.petsplus.ai.suggester.signal.rules.SignalRuleSet;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies declarative desirability modifiers sourced from a pet's nature
 * profile. Each emotion slot (major/minor/quirk) scales authored rules by the
 * rolled strength so naturally strong traits have a meaningful influence while
 * faint quirks remain subtle.
 */
public class NatureDesirabilitySignal implements DesirabilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "desirability/nature");


    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext context) {
        if (!context.hasPetsPlusComponent()) {
            return new SignalResult(1.0f, 1.0f, Map.of());
        }

        PetComponent.NatureEmotionProfile profile = context.natureProfile();
        if (profile == null || profile.isEmpty()) {
            return new SignalResult(1.0f, 1.0f, Map.of());
        }

        NatureSignalRules rules = SignalRuleRegistry.natureRules();
        float modifier = 1.0f;
        List<Map<String, Object>> contributions = new ArrayList<>();

        modifier = applySlot("major", profile.majorEmotion(), profile.majorStrength(), rules.majorWeight(), rules.minIntensity(), goal, modifier, contributions, rules);
        modifier = applySlot("minor", profile.minorEmotion(), profile.minorStrength(), rules.minorWeight(), rules.minIntensity(), goal, modifier, contributions, rules);
        modifier = applySlot("quirk", profile.quirkEmotion(), profile.quirkStrength(), rules.quirkWeight(), rules.minIntensity(), goal, modifier, contributions, rules);

        Map<String, Object> trace = new HashMap<>();
        trace.put("finalMultiplier", modifier);
        if (!contributions.isEmpty()) {
            trace.put("entries", List.copyOf(contributions));
        }

        return new SignalResult(modifier, modifier, trace);
    }

    private float applySlot(
        String slot,
        PetComponent.Emotion emotion,
        float strength,
        float slotWeight,
        float minIntensity,
        GoalDefinition goal,
        float modifier,
        List<Map<String, Object>> contributions,
        NatureSignalRules rules
    ) {
        if (emotion == null) {
            return modifier;
        }

        SignalRuleSet rule = rules.rules().get(emotion);
        if (rule == null) {
            return modifier;
        }

        float normalizedStrength = MathHelper.clamp(strength, 0f, 1f);
        if (normalizedStrength <= minIntensity) {
            return modifier;
        }

        float intensity = MathHelper.clamp(normalizedStrength * slotWeight, 0f, 1f);
        if (intensity <= minIntensity) {
            return modifier;
        }

        float result = modifier;
        for (float multiplier : rule.categoryMultipliersFor(goal.category())) {
            float applied = blendMultiplier(multiplier, intensity);
            result *= applied;
            addContribution(contributions, slot, emotion, "category", goal.category().name(), multiplier, intensity, applied);
        }

        for (float multiplier : rule.goalMultipliersFor(goal.id())) {
            float applied = blendMultiplier(multiplier, intensity);
            result *= applied;
            addContribution(contributions, slot, emotion, "goal", goal.id().toString(), multiplier, intensity, applied);
        }

        return result;
    }

    private static float blendMultiplier(float targetMultiplier, float intensity) {
        if (intensity <= 0f || targetMultiplier == 1.0f) {
            return 1.0f;
        }
        return 1.0f + (targetMultiplier - 1.0f) * intensity;
    }

    private static void addContribution(
        List<Map<String, Object>> contributions,
        String slot,
        PetComponent.Emotion emotion,
        String targetType,
        String target,
        float targetMultiplier,
        float intensity,
        float applied
    ) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("slot", slot);
        entry.put("emotion", emotion.name());
        entry.put("targetType", targetType);
        entry.put("target", target);
        entry.put("targetMultiplier", targetMultiplier);
        entry.put("intensity", intensity);
        entry.put("appliedMultiplier", applied);
        contributions.add(entry);
    }
}
