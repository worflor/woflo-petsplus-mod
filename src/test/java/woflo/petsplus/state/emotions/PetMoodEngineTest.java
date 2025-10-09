package woflo.petsplus.state.emotions;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import woflo.petsplus.state.PetComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void contagionHelperClampsToDynamicCap() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        when(parent.getBondStrength()).thenReturn(3200L);
        when(parent.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class)).thenReturn(null);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class, Long.MIN_VALUE))
                .thenReturn(Long.MIN_VALUE);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_SENSITIZED_STREAK, Integer.class, 0))
                .thenReturn(0);
        when(parent.getNatureVolatilityMultiplier()).thenReturn(1f);
        when(parent.getNatureResilienceMultiplier()).thenReturn(1f);
        when(parent.getNatureContagionModifier()).thenReturn(1f);
        when(parent.getNatureGuardModifier()).thenReturn(1f);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.addContagionShare(PetComponent.Emotion.UBUNTU, 1.0f, 1200L, 0.8f);

        Object record = getEmotionRecord(engine, PetComponent.Emotion.UBUNTU);
        float share = getContagionShare(record);

        float cap = invokeContagionCap(engine, 0.8f);
        assertEquals(cap, share, 1.0e-4f, "Contagion share should clamp to dynamic cap");
    }

    @Test
    void contagionShareDecaysWhenNoPackNearby() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        when(parent.getBondStrength()).thenReturn(2400L);
        when(parent.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class)).thenReturn(null);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class, Long.MIN_VALUE))
                .thenReturn(Long.MIN_VALUE);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_SENSITIZED_STREAK, Integer.class, 0))
                .thenReturn(0);
        when(parent.getNatureVolatilityMultiplier()).thenReturn(1f);
        when(parent.getNatureResilienceMultiplier()).thenReturn(1f);
        when(parent.getNatureContagionModifier()).thenReturn(1f);
        when(parent.getNatureGuardModifier()).thenReturn(1f);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.addContagionShare(PetComponent.Emotion.CHEERFUL, 0.2f, 2000L, 0.7f);

        Object record = getEmotionRecord(engine, PetComponent.Emotion.CHEERFUL);
        float initial = getContagionShare(record);

        var refreshMethod = PetMoodEngine.class.getDeclaredMethod("refreshContextGuards",
                record.getClass(), long.class, long.class);
        refreshMethod.setAccessible(true);
        refreshMethod.invoke(engine, record, 2240L, 240L);

        float after = getContagionShare(record);
        assertTrue(after < initial, "Contagion share should decay when no new contributions arrive");
    }

    @Test
    void cleanupPreservesContagionOnlyEmotions() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        when(parent.getNatureVolatilityMultiplier()).thenReturn(1f);
        when(parent.getNatureResilienceMultiplier()).thenReturn(1f);
        when(parent.getNatureContagionModifier()).thenReturn(1f);
        when(parent.getNatureGuardModifier()).thenReturn(1f);
        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.addContagionShare(PetComponent.Emotion.UBUNTU, 0.3f, 1000L, 0.9f);

        var cachedEpsilonField = PetMoodEngine.class.getDeclaredField("cachedEpsilon");
        cachedEpsilonField.setAccessible(true);
        float cachedEpsilon = cachedEpsilonField.getFloat(engine);
        var baseEpsilonField = PetMoodEngine.class.getDeclaredField("EPSILON");
        baseEpsilonField.setAccessible(true);
        float baseEpsilon = baseEpsilonField.getFloat(null);
        float epsilon = Math.max(baseEpsilon, cachedEpsilon);

        var collectMethod = PetMoodEngine.class.getDeclaredMethod("collectActiveRecords", long.class, float.class);
        collectMethod.setAccessible(true);
        collectMethod.invoke(engine, 1240L, epsilon);

        Object record = getEmotionRecord(engine, PetComponent.Emotion.UBUNTU);
        float share = getContagionShare(record);
        assertTrue(share > 0f,
                "Contagion share should persist through cleanup when it is the only contribution");
    }

    @Test
    void majorEmotionReceivesStimulusBias() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        when(parent.getBondStrength()).thenReturn(2600L);
        when(parent.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class)).thenReturn(null);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class, Long.MIN_VALUE))
                .thenReturn(Long.MIN_VALUE);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_SENSITIZED_STREAK, Integer.class, 0))
                .thenReturn(0);
        when(parent.getNatureVolatilityMultiplier()).thenReturn(1f);
        when(parent.getNatureResilienceMultiplier()).thenReturn(1f);
        when(parent.getNatureContagionModifier()).thenReturn(1f);
        when(parent.getNatureGuardModifier()).thenReturn(1f);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.CHEERFUL, 0.4f,
                PetComponent.Emotion.RELIEF, 0.3f,
                PetComponent.Emotion.CHEERFUL, 0.2f));

        engine.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.CHEERFUL, 0.6f), 100L);
        engine.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.ANGST, 0.6f), 100L);

        Object majorRecord = getEmotionRecord(engine, PetComponent.Emotion.CHEERFUL);
        Object neutralRecord = getEmotionRecord(engine, PetComponent.Emotion.ANGST);

        float majorIntensity = getIntensity(majorRecord);
        float neutralIntensity = getIntensity(neutralRecord);

        assertTrue(majorIntensity > neutralIntensity,
                "Major emotion should accumulate more intensity than neutral emotions when profile bias is applied");

        var weightBiasMethod = PetMoodEngine.class.getDeclaredMethod("getNatureWeightBias", PetComponent.Emotion.class);
        weightBiasMethod.setAccessible(true);

        float majorWeightBias = (float) weightBiasMethod.invoke(engine, PetComponent.Emotion.CHEERFUL);
        float neutralWeightBias = (float) weightBiasMethod.invoke(engine, PetComponent.Emotion.ANGST);

        assertTrue(majorWeightBias > neutralWeightBias,
                "Major emotion weight bias should exceed neutral emotion bias");
    }

    @Test
    void majorEmotionBoostsContagionSpread() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        when(parent.getBondStrength()).thenReturn(2400L);
        when(parent.getNatureVolatilityMultiplier()).thenReturn(1f);
        when(parent.getNatureResilienceMultiplier()).thenReturn(1f);
        when(parent.getNatureContagionModifier()).thenReturn(1f);
        when(parent.getNatureGuardModifier()).thenReturn(1f);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.CHEERFUL, 0.35f,
                PetComponent.Emotion.RELIEF, 0.25f,
                PetComponent.Emotion.CHEERFUL, 0.2f));

        engine.addContagionShare(PetComponent.Emotion.CHEERFUL, 0.25f, 1200L, 0.9f);
        engine.addContagionShare(PetComponent.Emotion.ANGST, 0.25f, 1200L, 0.9f);

        Object majorRecord = getEmotionRecord(engine, PetComponent.Emotion.CHEERFUL);
        Object neutralRecord = getEmotionRecord(engine, PetComponent.Emotion.ANGST);

        float majorShare = getContagionShare(majorRecord);
        float neutralShare = getContagionShare(neutralRecord);

        assertTrue(majorShare > neutralShare,
                "Major emotion contagion should accumulate faster than neutral emotions when profile bias is active");
    }

    private static Object getEmotionRecord(PetMoodEngine engine, PetComponent.Emotion emotion) throws Exception {
        var field = PetMoodEngine.class.getDeclaredField("emotionRecords");
        field.setAccessible(true);
        Map<PetComponent.Emotion, ?> records = (Map<PetComponent.Emotion, ?>) field.get(engine);
        Object record = records.get(emotion);
        assertNotNull(record, "Emotion record should exist after contagion share");
        return record;
    }

    private static float getContagionShare(Object record) throws Exception {
        var contagionField = record.getClass().getDeclaredField("contagionShare");
        contagionField.setAccessible(true);
        return contagionField.getFloat(record);
    }

    private static float getIntensity(Object record) throws Exception {
        var intensityField = record.getClass().getDeclaredField("intensity");
        intensityField.setAccessible(true);
        return intensityField.getFloat(record);
    }

    private static float invokeContagionCap(PetMoodEngine engine, float bondFactor) throws Exception {
        var method = PetMoodEngine.class.getDeclaredMethod("computeContagionCap", float.class);
        method.setAccessible(true);
        return (float) method.invoke(engine, bondFactor);
    }
}
