package woflo.petsplus.ai.suggester.signal.rules;

import woflo.petsplus.state.PetComponent;

import java.util.Map;

/**
 * Declarative configuration describing how a pet's nature emotions influence
 * desirability.
 */
public record NatureSignalRules(
    float minIntensity,
    float majorWeight,
    float minorWeight,
    float quirkWeight,
    Map<PetComponent.Emotion, SignalRuleSet> rules
) {
    public static NatureSignalRules defaults() {
        return new NatureSignalRules(0.05f, 1.0f, 0.65f, 0.35f, Map.of());
    }
}
