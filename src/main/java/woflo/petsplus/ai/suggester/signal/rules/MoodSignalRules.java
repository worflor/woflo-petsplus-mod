package woflo.petsplus.ai.suggester.signal.rules;

import woflo.petsplus.state.PetComponent;

import java.util.Map;

/**
 * Aggregates the declarative rule sets that mood and emotion desirability
 * signals use.
 */
public record MoodSignalRules(
    Map<PetComponent.Mood, SignalRuleSet> moodRules,
    Map<PetComponent.Emotion, SignalRuleSet> emotionRules
) {
    public static MoodSignalRules empty() {
        return new MoodSignalRules(Map.of(), Map.of());
    }
}
