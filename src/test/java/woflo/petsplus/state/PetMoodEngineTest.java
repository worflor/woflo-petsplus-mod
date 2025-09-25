package woflo.petsplus.state;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetMoodEngineTest {

    @Test
    void frustrationDefaultsFavorAngryMood() {
        Map<PetComponent.Mood, Float> defaults =
                PetMoodEngine.getAuthoredEmotionToMoodDefaults(PetComponent.Emotion.FRUSTRATION);

        assertNotNull(defaults, "Defaults should exist for frustration emotion");

        float angryWeight = defaults.getOrDefault(PetComponent.Mood.ANGRY, 0f);
        float strongestOther = defaults.entrySet().stream()
                .filter(entry -> entry.getKey() != PetComponent.Mood.ANGRY)
                .map(Map.Entry::getValue)
                .max(Float::compare)
                .orElse(0f);

        assertTrue(angryWeight > strongestOther,
                "Angry defaults should prioritise the angry mood over other moods");

        float total = (float) defaults.values().stream()
                .mapToDouble(Float::doubleValue)
                .sum();
        assertEquals(1f, total, 1.0e-4f, "Default weights should normalise to 1");
    }

    @Test
    void resolveUsesDefaultsWhenConfigMissing() {
        EnumMap<PetComponent.Mood, Float> resolved =
                PetMoodEngine.resolveEmotionToMoodWeights(null, PetComponent.Emotion.FRUSTRATION);
        Map<PetComponent.Mood, Float> defaults =
                PetMoodEngine.getAuthoredEmotionToMoodDefaults(PetComponent.Emotion.FRUSTRATION);

        assertEquals(defaults, resolved,
                "Resolved weights should fall back to authored defaults when config is absent");
    }
}
