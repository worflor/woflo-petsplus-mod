package net.woflo.testmod.pet.mood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PetMoodEngineTest {

    @Test
    void natureRecoveryScalingAppliesDuringNegativeStimuli() {
        PetNatureProfile profile = PetNatureProfile.builder()
                .stimulusBias(PetEmotionType.FEAR, 1f)
                .recoveryBias(PetEmotionType.FEAR, 1.5f)
                .weightBias(PetEmotionType.FEAR, 1f)
                .build();
        PetMoodEngine engine = new PetMoodEngine(profile);

        engine.pushEmotion(PetStimulus.positive(PetEmotionType.FEAR, 4f));
        engine.pushEmotion(PetStimulus.negative(PetEmotionType.FEAR, 2f));

        assertEquals(1f, engine.getEmotionIntensity(PetEmotionType.FEAR), 0.0001f,
                "Recovery bias should accelerate fear decay");
    }

    @Test
    void natureBiasInfluencesFinalMoodSelection() {
        PetNatureProfile profile = PetNatureProfile.builder()
                .stimulusBias(PetEmotionType.CALM, 1f)
                .stimulusBias(PetEmotionType.HAPPY, 1f)
                .weightBias(PetEmotionType.CALM, 1f)
                .weightBias(PetEmotionType.HAPPY, 1f)
                .moodBias(PetMood.CALM, 2f)
                .build();
        PetMoodEngine engine = new PetMoodEngine(profile);

        engine.pushEmotion(PetStimulus.positive(PetEmotionType.CALM, 3f));
        engine.pushEmotion(PetStimulus.positive(PetEmotionType.HAPPY, 3f));

        Map<PetMood, Float> blend = engine.calculateMoodBlend();
        assertTrue(blend.get(PetMood.CALM) > blend.get(PetMood.HAPPY),
                "Nature mood bias should break ties in favor of calm");
    }

    @Test
    void weightBiasOnlyAppliesOnceWhenAggregating() {
        PetNatureProfile profile = PetNatureProfile.builder()
                .stimulusBias(PetEmotionType.CALM, 1f)
                .stimulusBias(PetEmotionType.HAPPY, 1f)
                .weightBias(PetEmotionType.CALM, 1f)
                .weightBias(PetEmotionType.HAPPY, 2f)
                .build();
        PetMoodEngine engine = new PetMoodEngine(profile);

        engine.pushEmotion(PetStimulus.positive(PetEmotionType.CALM, 1f));
        engine.pushEmotion(PetStimulus.positive(PetEmotionType.HAPPY, 1f));

        Map<PetMood, Float> blend = engine.calculateMoodBlend();
        assertEquals(2f / 3f, blend.get(PetMood.HAPPY), 0.0001f,
                "Weight bias should influence the final ratio exactly once");
        assertEquals(1f / 3f, blend.get(PetMood.CALM), 0.0001f,
                "Unbiased mood should receive the remaining share");
    }

    @Test
    void protectiveEmotionPersistsWhileOwnerIsInDanger() {
        PetNatureProfile profile = PetNatureProfile.builder()
                .stimulusBias(PetEmotionType.PROTECTIVE, 1f)
                .weightBias(PetEmotionType.PROTECTIVE, 1f)
                .protectivePersistenceMultiplier(1f)
                .build();
        OwnerContext dangerContext = () -> 0.8f;
        PetMoodEngine engine = new PetMoodEngine(profile, dangerContext);

        engine.pushEmotion(PetStimulus.positive(PetEmotionType.PROTECTIVE, 0.5f));
        engine.pushEmotion(PetStimulus.negative(PetEmotionType.PROTECTIVE, 0.5f));
        engine.pushEmotion(PetStimulus.positive(PetEmotionType.CALM, 0.2f));

        Map<PetMood, Float> blend = engine.calculateMoodBlend();
        assertTrue(blend.get(PetMood.ALERT) > 0.5f,
                "Protective mood should stay elevated while the owner is unsafe");
    }

    @Test
    void negativeDangerLevelsAreTreatedAsSafe() {
        PetNatureProfile profile = PetNatureProfile.builder()
                .protectivePersistenceMultiplier(2f)
                .build();
        PetMoodEngine engine = new PetMoodEngine(profile, () -> -0.5f);

        Map<PetMood, Float> blend = engine.calculateMoodBlend();
        assertTrue(blend.isEmpty(),
                "Negative danger should be clamped to a safe state with no protective mood");
    }

    @Test
    void extremeDangerLevelsAreClampedBeforePersistence() {
        PetNatureProfile profile = PetNatureProfile.builder()
                .protectivePersistenceMultiplier(2f)
                .build();
        PetMoodEngine engine = new PetMoodEngine(profile, () -> 5f);

        Map<PetMood, Float> blend = engine.calculateMoodBlend();
        assertEquals(1f, blend.getOrDefault(PetMood.ALERT, -1f), 0.0001f,
                "Danger should clamp to unity before applying persistence to avoid runaway weights");
    }
}
