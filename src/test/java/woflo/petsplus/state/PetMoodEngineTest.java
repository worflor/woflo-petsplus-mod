package woflo.petsplus.state;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

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
        PetMoodEngine engine = new PetMoodEngine(mock(PetComponent.class));
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

    private static float invokeContagionCap(PetMoodEngine engine, float bondFactor) throws Exception {
        var method = PetMoodEngine.class.getDeclaredMethod("computeContagionCap", float.class);
        method.setAccessible(true);
        return (float) method.invoke(engine, bondFactor);
    }
}
