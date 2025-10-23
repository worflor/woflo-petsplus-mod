package woflo.petsplus.ai.suggester.signal.desirability;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.perception.ContextSlice;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.suggester.signal.DesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;
import woflo.petsplus.ai.suggester.signal.rules.NatureSignalRules;
import woflo.petsplus.ai.suggester.signal.rules.SignalRuleRegistry;
import woflo.petsplus.ai.suggester.signal.rules.SignalRuleSet;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

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
            return SignalResult.identity();
        }

        PetComponent.NatureEmotionProfile profile = context.natureProfile();
        if (profile == null || profile.isEmpty()) {
            return SignalResult.identity();
        }

        NatureSignalRules rules = SignalRuleRegistry.natureRules();
        float modifier = 1.0f;

        modifier = applySlot(profile.majorEmotion(), profile.majorStrength(), rules.majorWeight(), rules.minIntensity(), goal, modifier, rules);
        modifier = applySlot(profile.minorEmotion(), profile.minorStrength(), rules.minorWeight(), rules.minIntensity(), goal, modifier, rules);
        modifier = applySlot(profile.quirkEmotion(), profile.quirkStrength(), rules.quirkWeight(), rules.minIntensity(), goal, modifier, rules);

        return new SignalResult(modifier, modifier, null);
    }

    @Override
    public EnumSet<ContextSlice> observedSlices(GoalDefinition goal) {
        return EnumSet.of(ContextSlice.STATE_DATA, ContextSlice.EMOTIONS);
    }

    private float applySlot(
        PetComponent.Emotion emotion,
        float strength,
        float slotWeight,
        float minIntensity,
        GoalDefinition goal,
        float modifier,
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
        }

        for (float multiplier : rule.goalMultipliersFor(goal.id())) {
            float applied = blendMultiplier(multiplier, intensity);
            result *= applied;
        }

        return result;
    }

    private static float blendMultiplier(float targetMultiplier, float intensity) {
        if (intensity <= 0f || targetMultiplier == 1.0f) {
            return 1.0f;
        }
        return 1.0f + (targetMultiplier - 1.0f) * intensity;
    }

}
