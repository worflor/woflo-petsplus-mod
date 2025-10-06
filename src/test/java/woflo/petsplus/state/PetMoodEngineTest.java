package woflo.petsplus.state;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import woflo.petsplus.state.emotions.PetMoodEngine;
import woflo.petsplus.config.PetsPlusConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PetMoodEngineTest {

    @Test
    void frustrationDefaultsFavorAngryMood() throws Exception {
        Map<PetComponent.Mood, Float> defaults =
                callAuthoredEmotionDefaults(PetComponent.Emotion.FRUSTRATION);

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
    void resolveUsesDefaultsWhenConfigMissing() throws Exception {
        EnumMap<PetComponent.Mood, Float> resolved =
                callResolveEmotionToMoodWeights(PetComponent.Emotion.FRUSTRATION);
        Map<PetComponent.Mood, Float> defaults =
                callAuthoredEmotionDefaults(PetComponent.Emotion.FRUSTRATION);

        assertEquals(defaults, resolved,
                "Resolved weights should fall back to authored defaults when config is absent");
    }

    @Test
    void contagionHelperClampsToDynamicCap() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);
        when(parent.getBondStrength()).thenReturn(3200L);

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
        stubCommonParentInteractions(parent);
        when(parent.getBondStrength()).thenReturn(2400L);

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
        stubCommonParentInteractions(parent);
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
    void contagionShareMarksEngineDirtyAndStimulusTime() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);
        when(parent.getBondStrength()).thenReturn(2400L);

        PetMoodEngine engine = new PetMoodEngine(parent);

        var dirtyField = PetMoodEngine.class.getDeclaredField("dirty");
        dirtyField.setAccessible(true);
        dirtyField.setBoolean(engine, false);

        var stimulusField = PetMoodEngine.class.getDeclaredField("lastStimulusTime");
        stimulusField.setAccessible(true);
        stimulusField.setLong(engine, 0L);

        engine.addContagionShare(PetComponent.Emotion.CHEERFUL, 0.25f, 640L, 0.95f);

        assertTrue(dirtyField.getBoolean(engine),
                "Contagion injections should flag the engine for refresh");
        assertEquals(640L, stimulusField.getLong(engine),
                "Contagion should advance the last stimulus timestamp for hysteresis smoothing");
    }

    @Test
    void majorEmotionReceivesStimulusBias() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);
        when(parent.getBondStrength()).thenReturn(2600L);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.CHEERFUL, 0.4f,
                PetComponent.Emotion.RELIEF, 0.3f,
                PetComponent.Emotion.GLEE, 0.2f));

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
        stubCommonParentInteractions(parent);
        when(parent.getBondStrength()).thenReturn(2400L);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.CHEERFUL, 0.35f,
                PetComponent.Emotion.RELIEF, 0.25f,
                PetComponent.Emotion.GLEE, 0.2f));

        engine.addContagionShare(PetComponent.Emotion.CHEERFUL, 0.25f, 1200L, 0.9f);
        engine.addContagionShare(PetComponent.Emotion.ANGST, 0.25f, 1200L, 0.9f);

        Object majorRecord = getEmotionRecord(engine, PetComponent.Emotion.CHEERFUL);
        Object neutralRecord = getEmotionRecord(engine, PetComponent.Emotion.ANGST);

        float majorShare = getContagionShare(majorRecord);
        float neutralShare = getContagionShare(neutralRecord);

        assertTrue(majorShare > neutralShare,
                "Major emotion contagion should accumulate faster than neutral emotions when profile bias is active");
    }

    @Test
    void calmProfileRecoversFasterThanNeutral() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);
        when(parent.getBondStrength()).thenReturn(2400L);

        PetMoodEngine profiled = new PetMoodEngine(parent);
        PetMoodEngine neutral = new PetMoodEngine(parent);

        long now = 100L;
        profiled.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.LAGOM, 0.4f), now);
        neutral.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.LAGOM, 0.4f), now);

        profiled.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.LAGOM, 0.5f,
                null, 0f,
                null, 0f));

        long later = now + 20L;
        profiled.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.LAGOM, -0.1f), later);
        neutral.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.LAGOM, -0.1f), later);

        Object profiledRecord = getEmotionRecord(profiled, PetComponent.Emotion.LAGOM);
        Object neutralRecord = getEmotionRecord(neutral, PetComponent.Emotion.LAGOM);

        float profiledIntensity = getIntensity(profiledRecord);
        float neutralIntensity = getIntensity(neutralRecord);

        assertTrue(profiledIntensity < neutralIntensity,
                "Nature recovery bias should cause profiled emotions to shed intensity faster under calming stimuli");
    }

    @Test
    void volatileProfileRecoversMoreSlowlyThanNeutral() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);
        when(parent.getBondStrength()).thenReturn(2400L);

        PetMoodEngine profiled = new PetMoodEngine(parent);
        PetMoodEngine neutral = new PetMoodEngine(parent);

        long now = 100L;
        profiled.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.ANGST, 0.4f), now);
        neutral.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.ANGST, 0.4f), now);

        profiled.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.ANGST, 0.6f,
                null, 0f,
                null, 0f));

        long later = now + 20L;
        profiled.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.ANGST, -0.1f), later);
        neutral.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.ANGST, -0.1f), later);

        Object profiledRecord = getEmotionRecord(profiled, PetComponent.Emotion.ANGST);
        Object neutralRecord = getEmotionRecord(neutral, PetComponent.Emotion.ANGST);

        float profiledIntensity = getIntensity(profiledRecord);
        float neutralIntensity = getIntensity(neutralRecord);

        assertTrue(profiledIntensity > neutralIntensity,
                "Volatile profiled emotions should shed intensity slower than neutral profiles under calming stimuli");
    }

    @Test
    void spacedStimuliPreserveNoveltyBoost() throws Exception {
        PetComponent rapidParent = mock(PetComponent.class);
        PetComponent spacedParent = mock(PetComponent.class);
        stubCommonParentInteractions(rapidParent);
        stubCommonParentInteractions(spacedParent);
        when(rapidParent.getBondStrength()).thenReturn(2400L);
        when(spacedParent.getBondStrength()).thenReturn(2400L);

        try (MockedStatic<FabricLoader> loaderMock = mockStatic(FabricLoader.class)) {
            FabricLoader loader = mock(FabricLoader.class);
            loaderMock.when(FabricLoader::getInstance).thenReturn(loader);
            Path configDir = Files.createTempDirectory("petsplus-config");
            when(loader.getConfigDir()).thenReturn(configDir);

            try (MockedStatic<PetsPlusConfig> configMock = mockStatic(PetsPlusConfig.class)) {
                PetsPlusConfig config = mock(PetsPlusConfig.class);
                configMock.when(PetsPlusConfig::getInstance).thenReturn(config);
                when(config.getConfigGeneration()).thenReturn(1);
                JsonObject moods = new JsonObject();
                JsonObject weights = new JsonObject();
                JsonObject opponents = new JsonObject();
                JsonObject animation = new JsonObject();
                moods.add("weight", weights);
                moods.add("opponents", opponents);
                moods.add("animation", animation);
                when(config.getSection("moods")).thenReturn(moods);
                when(config.getSection("weight")).thenReturn(weights);
                when(config.getSection("opponents")).thenReturn(opponents);
                when(config.getSection("animation")).thenReturn(animation);

                PetMoodEngine rapid = new PetMoodEngine(rapidParent);
                PetMoodEngine spaced = new PetMoodEngine(spacedParent);

                long base = 200L;
                PetComponent.Emotion emotion = PetComponent.Emotion.GLEE;
                rapid.applyStimulus(new PetComponent.EmotionDelta(emotion, 0.4f), base);
                rapid.applyStimulus(new PetComponent.EmotionDelta(emotion, 0.4f), base + 5L);

                spaced.applyStimulus(new PetComponent.EmotionDelta(emotion, 0.4f), base);
                spaced.applyStimulus(new PetComponent.EmotionDelta(emotion, 0.4f), base + 200L);

                ServerWorld rapidWorld = (ServerWorld) rapidParent.getPet().getWorld();
                ServerWorld spacedWorld = (ServerWorld) spacedParent.getPet().getWorld();
                when(rapidWorld.getTime()).thenReturn(base + 60L);
                when(spacedWorld.getTime()).thenReturn(base + 60L);

                rapid.getMoodBlend();
                spaced.getMoodBlend();

                Object rapidRecord = getEmotionRecord(rapid, emotion);
                Object spacedRecord = getEmotionRecord(spaced, emotion);

                float rapidNovelty = getNovelty(rapidRecord);
                float spacedNovelty = getNovelty(spacedRecord);
                assertTrue(spacedNovelty > rapidNovelty,
                        "Long gaps should rebuild novelty more than rapid pulses (rapid="
                                + rapidNovelty + ", spaced=" + spacedNovelty + ")");

                float rapidWeight = getWeight(rapidRecord);
                float spacedWeight = getWeight(spacedRecord);
                assertTrue(spacedWeight > rapidWeight,
                        "Novelty boost should translate into higher emotion weight for spaced stimuli (rapid="
                                + rapidWeight + ", spaced=" + spacedWeight + ")");
            }
        }
    }

    @Test
    void ensureConfigCacheToleratesMissingSubsections() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);
        when(parent.getBondStrength()).thenReturn(2000L);

        try (MockedStatic<FabricLoader> loaderMock = mockStatic(FabricLoader.class)) {
            FabricLoader loader = mock(FabricLoader.class);
            loaderMock.when(FabricLoader::getInstance).thenReturn(loader);
            Path configDir = Files.createTempDirectory("petsplus-config");
            when(loader.getConfigDir()).thenReturn(configDir);

            try (MockedStatic<PetsPlusConfig> configMock = mockStatic(PetsPlusConfig.class)) {
                PetsPlusConfig config = mock(PetsPlusConfig.class);
                configMock.when(PetsPlusConfig::getInstance).thenReturn(config);
                when(config.getConfigGeneration()).thenReturn(3);
                JsonObject moods = new JsonObject();
                when(config.getSection("moods")).thenReturn(moods);
                when(config.getSection("weight")).thenReturn(null);
                when(config.getSection("opponents")).thenReturn(null);
                when(config.getSection("animation")).thenReturn(null);

                PetMoodEngine engine = new PetMoodEngine(parent);

                var ensureMethod = PetMoodEngine.class.getDeclaredMethod("ensureConfigCache");
                ensureMethod.setAccessible(true);
                ensureMethod.invoke(engine);

                var animationSectionField = PetMoodEngine.class.getDeclaredField("cachedAnimationSection");
                animationSectionField.setAccessible(true);
                assertNull(animationSectionField.get(engine),
                        "Animation section should remain null when config omits it");

                var weightSectionField = PetMoodEngine.class.getDeclaredField("cachedWeightSection");
                weightSectionField.setAccessible(true);
                assertNull(weightSectionField.get(engine),
                        "Weight section should remain null when config omits it");

                var baseIntervalField = PetMoodEngine.class.getDeclaredField("cachedBaseAnimationUpdateInterval");
                baseIntervalField.setAccessible(true);
                assertEquals(16, baseIntervalField.getInt(engine),
                        "Missing animation config should fall back to default base interval");

                var speedField = PetMoodEngine.class.getDeclaredField("cachedAnimationSpeedMultiplier");
                speedField.setAccessible(true);
                assertEquals(0.15d, speedField.getDouble(engine), 1.0e-6,
                        "Missing animation config should fall back to default speed multiplier");

                var minIntervalField = PetMoodEngine.class.getDeclaredField("cachedMinAnimationInterval");
                minIntervalField.setAccessible(true);
                assertEquals(4, minIntervalField.getInt(engine),
                        "Missing animation config should fall back to default min interval");

                var maxIntervalField = PetMoodEngine.class.getDeclaredField("cachedMaxAnimationInterval");
                maxIntervalField.setAccessible(true);
                assertEquals(40, maxIntervalField.getInt(engine),
                        "Missing animation config should fall back to default max interval");

                var defaultWeightField = PetMoodEngine.class.getDeclaredField("cachedDefaultWeightMax");
                defaultWeightField.setAccessible(true);
                assertEquals(6.0f, defaultWeightField.getFloat(engine), 1.0e-4f,
                        "Missing weight config should fall back to default max weight");

                var saturationField = PetMoodEngine.class.getDeclaredField("cachedSaturationAlpha");
                saturationField.setAccessible(true);
                assertEquals(0.12f, saturationField.getFloat(engine), 1.0e-4f,
                        "Missing weight config should retain default saturation alpha");

                var perEmotionField = PetMoodEngine.class.getDeclaredField("cachedPerEmotionWeightMax");
                perEmotionField.setAccessible(true);
                @SuppressWarnings("unchecked")
                EnumMap<PetComponent.Emotion, Float> perEmotion =
                        (EnumMap<PetComponent.Emotion, Float>) perEmotionField.get(engine);
                assertTrue(perEmotion.isEmpty(),
                        "Missing weight config should not populate per-emotion caps");
            }
        }
    }

    @Test
    void weightConfigurationClampsEmotionWeights() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);
        when(parent.getBondStrength()).thenReturn(3200L);

        try (MockedStatic<FabricLoader> loaderMock = mockStatic(FabricLoader.class)) {
            FabricLoader loader = mock(FabricLoader.class);
            loaderMock.when(FabricLoader::getInstance).thenReturn(loader);
            Path configDir = Files.createTempDirectory("petsplus-config");
            when(loader.getConfigDir()).thenReturn(configDir);

            try (MockedStatic<PetsPlusConfig> configMock = mockStatic(PetsPlusConfig.class)) {
                PetsPlusConfig config = mock(PetsPlusConfig.class);
                configMock.when(PetsPlusConfig::getInstance).thenReturn(config);
                when(config.getConfigGeneration()).thenReturn(2);
                JsonObject moods = new JsonObject();
                JsonObject weights = new JsonObject();
                weights.addProperty("defaultMax", 4.0f);
                weights.addProperty("saturationAlpha", 0.25f);
                JsonObject perEmotion = new JsonObject();
                perEmotion.addProperty("glee", 1.0f);
                weights.add("perEmotionMax", perEmotion);
                JsonObject opponents = new JsonObject();
                JsonObject animation = new JsonObject();
                moods.add("weight", weights);
                moods.add("opponents", opponents);
                moods.add("animation", animation);
                when(config.getSection("moods")).thenReturn(moods);
                when(config.getSection("weight")).thenReturn(weights);
                when(config.getSection("opponents")).thenReturn(opponents);
                when(config.getSection("animation")).thenReturn(animation);

                PetMoodEngine engine = new PetMoodEngine(parent);

                long base = 120L;
                PetComponent.Emotion emotion = PetComponent.Emotion.GLEE;
                engine.applyStimulus(new PetComponent.EmotionDelta(emotion, 0.6f), base);
                engine.applyStimulus(new PetComponent.EmotionDelta(emotion, 0.6f), base + 10L);
                engine.applyStimulus(new PetComponent.EmotionDelta(emotion, 0.6f), base + 20L);

                ServerWorld world = (ServerWorld) parent.getPet().getWorld();
                when(world.getTime()).thenReturn(base + 40L);

                engine.getMoodBlend();

                Object record = getEmotionRecord(engine, emotion);
                float weight = getWeight(record);
                assertTrue(weight > 0f, "Emotion weight should accumulate after multiple stimuli");
                assertTrue(weight <= 1.05f,
                        "Configured per-emotion max should cap weights even when stimuli try to exceed it (weight="
                                + weight + ")");
            }
        }
    }

    @Test
    void defaultWeightCapFromConfigExtendsGlobalLimit() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);

        try (MockedStatic<FabricLoader> loaderMock = mockStatic(FabricLoader.class)) {
            FabricLoader loader = mock(FabricLoader.class);
            loaderMock.when(FabricLoader::getInstance).thenReturn(loader);
            Path configDir = Files.createTempDirectory("petsplus-config");
            when(loader.getConfigDir()).thenReturn(configDir);

            try (MockedStatic<PetsPlusConfig> configMock = mockStatic(PetsPlusConfig.class)) {
                PetsPlusConfig config = mock(PetsPlusConfig.class);
                configMock.when(PetsPlusConfig::getInstance).thenReturn(config);
                when(config.getConfigGeneration()).thenReturn(3);

                JsonObject moods = new JsonObject();
                JsonObject weights = new JsonObject();
                weights.addProperty("defaultMax", 10.0f);
                JsonObject opponents = new JsonObject();
                JsonObject animation = new JsonObject();
                moods.add("weight", weights);
                moods.add("opponents", opponents);
                moods.add("animation", animation);
                when(config.getSection("moods")).thenReturn(moods);
                when(config.getSection("weight")).thenReturn(weights);
                when(config.getSection("opponents")).thenReturn(opponents);
                when(config.getSection("animation")).thenReturn(animation);

                PetMoodEngine engine = new PetMoodEngine(parent);

                long base = 200L;
                PetComponent.Emotion emotion = PetComponent.Emotion.CHEERFUL;
                engine.applyStimulus(new PetComponent.EmotionDelta(emotion, 0.4f), base);

                ServerWorld world = (ServerWorld) parent.getPet().getWorld();
                long updateTime = base + 160L;
                when(world.getTime()).thenReturn(updateTime);

                Object record = getEmotionRecord(engine, emotion);
                setFloatField(record, "impactBudget", 8.0f);
                setFloatField(record, "intensity", 1.0f);
                setFloatField(record, "contagionShare", 24.0f);
                setLongField(record, "lastUpdateTime", updateTime);

                engine.getMoodBlend();

                float weight = getWeight(record);
                assertTrue(weight > 6.0f,
                        "Configured default max should allow weights above the hard-coded fallback (weight="
                                + weight + ")");
                assertTrue(weight <= 10.0001f,
                        "Weights should still respect the configured default cap (weight=" + weight + ")");
            }
        }
    }

    @Test
    void defaultWeightCapFromConfigLowersGlobalLimit() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);

        try (MockedStatic<FabricLoader> loaderMock = mockStatic(FabricLoader.class)) {
            FabricLoader loader = mock(FabricLoader.class);
            loaderMock.when(FabricLoader::getInstance).thenReturn(loader);
            Path configDir = Files.createTempDirectory("petsplus-config");
            when(loader.getConfigDir()).thenReturn(configDir);

            try (MockedStatic<PetsPlusConfig> configMock = mockStatic(PetsPlusConfig.class)) {
                PetsPlusConfig config = mock(PetsPlusConfig.class);
                configMock.when(PetsPlusConfig::getInstance).thenReturn(config);
                when(config.getConfigGeneration()).thenReturn(4);

                JsonObject moods = new JsonObject();
                JsonObject weights = new JsonObject();
                weights.addProperty("defaultMax", 4.0f);
                JsonObject opponents = new JsonObject();
                JsonObject animation = new JsonObject();
                moods.add("weight", weights);
                moods.add("opponents", opponents);
                moods.add("animation", animation);
                when(config.getSection("moods")).thenReturn(moods);
                when(config.getSection("weight")).thenReturn(weights);
                when(config.getSection("opponents")).thenReturn(opponents);
                when(config.getSection("animation")).thenReturn(animation);

                PetMoodEngine engine = new PetMoodEngine(parent);

                long base = 280L;
                PetComponent.Emotion emotion = PetComponent.Emotion.HOPEFUL;
                engine.applyStimulus(new PetComponent.EmotionDelta(emotion, 0.5f), base);

                ServerWorld world = (ServerWorld) parent.getPet().getWorld();
                long updateTime = base + 200L;
                when(world.getTime()).thenReturn(updateTime);

                Object record = getEmotionRecord(engine, emotion);
                setFloatField(record, "impactBudget", 7.5f);
                setFloatField(record, "intensity", 1.0f);
                setFloatField(record, "contagionShare", 18.0f);
                setLongField(record, "lastUpdateTime", updateTime);

                engine.getMoodBlend();

                float weight = getWeight(record);
                assertTrue(weight >= 3.0f,
                        "Weight should build toward the configured cap before clamping (weight=" + weight + ")");
                assertTrue(weight <= 4.0001f,
                        "Configured default max should clamp the global weight when lowered (weight=" + weight + ")");
            }
        }
    }

    @Test
    void stimulusTimingInfluencesTransitionMargin() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);

        PetMoodEngine engine = new PetMoodEngine(parent);

        var hysteresisField = PetMoodEngine.class.getDeclaredField("cachedHysteresisTicks");
        hysteresisField.setAccessible(true);
        hysteresisField.setInt(engine, 200);

        var switchField = PetMoodEngine.class.getDeclaredField("cachedSwitchMargin");
        switchField.setAccessible(true);
        switchField.setDouble(engine, 0.08d);

        var stimulusField = PetMoodEngine.class.getDeclaredField("lastStimulusTime");
        stimulusField.setAccessible(true);

        var smoothingMethod = PetMoodEngine.class.getDeclaredMethod("computeStimulusSmoothing", long.class);
        smoothingMethod.setAccessible(true);
        var marginMethod = PetMoodEngine.class.getDeclaredMethod("computeTransitionMargin", float.class, float.class, float.class);
        marginMethod.setAccessible(true);

        stimulusField.setLong(engine, 390L);
        float recentSmoothing = (float) smoothingMethod.invoke(engine, 400L);
        float recentMargin = (float) marginMethod.invoke(engine, recentSmoothing, 0f, 0f);

        stimulusField.setLong(engine, 120L);
        float lullSmoothing = (float) smoothingMethod.invoke(engine, 400L);
        float lullMargin = (float) marginMethod.invoke(engine, lullSmoothing, 0f, 0f);

        assertTrue(recentMargin > lullMargin,
                "Short gaps after stimuli should expand transition margins compared to long lulls (recent="
                        + recentMargin + ", lull=" + lullMargin + ")");
        float scaledBase = 0.08f * (200f / 60f);
        assertTrue(lullMargin <= scaledBase,
                "Extended downtime should reduce the required margin toward the configured baseline (lull="
                        + lullMargin + ", baseline=" + scaledBase + ")");
    }

    @Test
    void transitionMarginRespondsToDynamics() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);

        PetMoodEngine engine = new PetMoodEngine(parent);

        var marginMethod = PetMoodEngine.class.getDeclaredMethod("computeTransitionMargin",
                float.class, float.class, float.class);
        marginMethod.setAccessible(true);

        float baseline = (float) marginMethod.invoke(engine, 1f, 0.1f, 0.1f);
        float volatileMargin = (float) marginMethod.invoke(engine, 1f, 0.8f, 0.1f);
        float peakedMargin = (float) marginMethod.invoke(engine, 1f, 0.1f, 0.75f);

        assertTrue(volatileMargin > baseline,
                "High volatility should expand the transition margin to resist jitter (volatile="
                        + volatileMargin + ", baseline=" + baseline + ")");
        assertTrue(peakedMargin < baseline,
                "Strong emotional peaks should narrow the transition margin for decisive switches (peaked="
                        + peakedMargin + ", baseline=" + baseline + ")");
    }

    @Test
    void animationIntensityRespondsToDynamics() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);

        PetMoodEngine engine = new PetMoodEngine(parent);

        var intensityField = PetMoodEngine.class.getDeclaredField("animationIntensity");
        intensityField.setAccessible(true);
        intensityField.setFloat(engine, 0f);

        var method = PetMoodEngine.class.getDeclaredMethod("updateAnimationIntensity",
                float.class, float.class, float.class);
        method.setAccessible(true);

        method.invoke(engine, 0.4f, 0.1f, 0.1f);
        float calmIntensity = intensityField.getFloat(engine);

        intensityField.setFloat(engine, 0f);
        method.invoke(engine, 0.4f, 0.6f, 0.7f);
        float vibrantIntensity = intensityField.getFloat(engine);

        assertTrue(vibrantIntensity > calmIntensity,
                "Higher volatility and peaks should translate into stronger animation intensity (calm="
                        + calmIntensity + ", vibrant=" + vibrantIntensity + ")");
    }

    @Test
    void naturePreferenceSwaysMoodBlendTowardProfiledMoods() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.UBUNTU, 0.6f,
                PetComponent.Emotion.LOYALTY, 0.35f,
                PetComponent.Emotion.PLAYFULNESS, 0.25f));

        try (MockedStatic<FabricLoader> loaderMock = mockStatic(FabricLoader.class)) {
            FabricLoader loader = mock(FabricLoader.class);
            loaderMock.when(FabricLoader::getInstance).thenReturn(loader);
            Path configDir = Files.createTempDirectory("petsplus-config");
            when(loader.getConfigDir()).thenReturn(configDir);

            try (MockedStatic<PetsPlusConfig> configMock = mockStatic(PetsPlusConfig.class)) {
                PetsPlusConfig config = mock(PetsPlusConfig.class);
                configMock.when(PetsPlusConfig::getInstance).thenReturn(config);
                when(config.getConfigGeneration()).thenReturn(1);
                JsonObject moods = new JsonObject();
                JsonObject weights = new JsonObject();
                JsonObject opponents = new JsonObject();
                JsonObject animation = new JsonObject();
                moods.add("weight", weights);
                moods.add("opponents", opponents);
                moods.add("animation", animation);
                when(config.getSection("moods")).thenReturn(moods);
                when(config.getSection("weight")).thenReturn(weights);
                when(config.getSection("opponents")).thenReturn(opponents);
                when(config.getSection("animation")).thenReturn(animation);

                EnumMap<PetComponent.Mood, Float> blend = new EnumMap<>(PetComponent.Mood.class);
                for (PetComponent.Mood mood : PetComponent.Mood.values()) {
                    blend.put(mood, 0f);
                }
                blend.put(PetComponent.Mood.HAPPY, 0.5f);
                blend.put(PetComponent.Mood.CALM, 0.5f);

                EnumMap<PetComponent.Emotion, Float> neutralSignals = new EnumMap<>(PetComponent.Emotion.class);
                neutralSignals.put(PetComponent.Emotion.UBUNTU, 2.0f);
                neutralSignals.put(PetComponent.Emotion.LOYALTY, 1.5f);
                neutralSignals.put(PetComponent.Emotion.PLAYFULNESS, 1.2f);
                float totalNeutralSignal = 4.7f;

                var method = PetMoodEngine.class.getDeclaredMethod("applyNatureMoodPreference",
                        EnumMap.class, EnumMap.class, float.class);
                method.setAccessible(true);
                method.invoke(engine, blend, neutralSignals, totalNeutralSignal);

                float bonded = blend.getOrDefault(PetComponent.Mood.BONDED, 0f);
                assertTrue(bonded > 0f, "Nature preference should project onto bonded mood weights");

                float total = 0f;
                for (float value : blend.values()) {
                    total += Math.max(0f, value);
                }
                assertEquals(1f, total, 1.0e-4f, "Blended mood weights should remain normalised");
            }
        }
    }

    @Test
    void naturePreferenceBacksOffWhenEvidenceAbsent() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.UBUNTU, 0.6f,
                PetComponent.Emotion.LOYALTY, 0.35f,
                PetComponent.Emotion.PLAYFULNESS, 0.25f));

        try (MockedStatic<FabricLoader> loaderMock = mockStatic(FabricLoader.class)) {
            FabricLoader loader = mock(FabricLoader.class);
            loaderMock.when(FabricLoader::getInstance).thenReturn(loader);
            Path configDir = Files.createTempDirectory("petsplus-config");
            when(loader.getConfigDir()).thenReturn(configDir);

            try (MockedStatic<PetsPlusConfig> configMock = mockStatic(PetsPlusConfig.class)) {
                PetsPlusConfig config = mock(PetsPlusConfig.class);
                configMock.when(PetsPlusConfig::getInstance).thenReturn(config);
                when(config.getConfigGeneration()).thenReturn(1);
                JsonObject moods = new JsonObject();
                JsonObject weights = new JsonObject();
                JsonObject opponents = new JsonObject();
                JsonObject animation = new JsonObject();
                moods.add("weight", weights);
                moods.add("opponents", opponents);
                moods.add("animation", animation);
                when(config.getSection("moods")).thenReturn(moods);
                when(config.getSection("weight")).thenReturn(weights);
                when(config.getSection("opponents")).thenReturn(opponents);
                when(config.getSection("animation")).thenReturn(animation);

                EnumMap<PetComponent.Mood, Float> blend = new EnumMap<>(PetComponent.Mood.class);
                for (PetComponent.Mood mood : PetComponent.Mood.values()) {
                    blend.put(mood, 0f);
                }
                blend.put(PetComponent.Mood.HAPPY, 0.55f);
                blend.put(PetComponent.Mood.CALM, 0.45f);

                EnumMap<PetComponent.Emotion, Float> neutralSignals = new EnumMap<>(PetComponent.Emotion.class);
                neutralSignals.put(PetComponent.Emotion.ANGST, 3.0f);
                neutralSignals.put(PetComponent.Emotion.FRUSTRATION, 2.0f);
                float totalNeutralSignal = 5.0f;

                var method = PetMoodEngine.class.getDeclaredMethod("applyNatureMoodPreference",
                        EnumMap.class, EnumMap.class, float.class);
                method.setAccessible(true);

                EnumMap<PetComponent.Mood, Float> before = new EnumMap<>(blend);
                method.invoke(engine, blend, neutralSignals, totalNeutralSignal);

                for (PetComponent.Mood mood : PetComponent.Mood.values()) {
                    assertEquals(before.getOrDefault(mood, 0f), blend.getOrDefault(mood, 0f), 1.0e-4f,
                            "Nature preference should not meaningfully sway moods when evidence is absent");
                }
            }
        }
    }

    @Test
    void naturePreferenceRespectsDominantNeutralSignals() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.UBUNTU, 0.55f,
                PetComponent.Emotion.LOYALTY, 0.35f,
                null, 0f));

        try (MockedStatic<FabricLoader> loaderMock = mockStatic(FabricLoader.class)) {
            FabricLoader loader = mock(FabricLoader.class);
            loaderMock.when(FabricLoader::getInstance).thenReturn(loader);
            Path configDir = Files.createTempDirectory("petsplus-config");
            when(loader.getConfigDir()).thenReturn(configDir);

            try (MockedStatic<PetsPlusConfig> configMock = mockStatic(PetsPlusConfig.class)) {
                PetsPlusConfig config = mock(PetsPlusConfig.class);
                configMock.when(PetsPlusConfig::getInstance).thenReturn(config);
                when(config.getConfigGeneration()).thenReturn(1);
                JsonObject moods = new JsonObject();
                JsonObject weights = new JsonObject();
                JsonObject opponents = new JsonObject();
                JsonObject animation = new JsonObject();
                moods.add("weight", weights);
                moods.add("opponents", opponents);
                moods.add("animation", animation);
                when(config.getSection("moods")).thenReturn(moods);
                when(config.getSection("weight")).thenReturn(weights);
                when(config.getSection("opponents")).thenReturn(opponents);
                when(config.getSection("animation")).thenReturn(animation);

                EnumMap<PetComponent.Mood, Float> blend = new EnumMap<>(PetComponent.Mood.class);
                for (PetComponent.Mood mood : PetComponent.Mood.values()) {
                    blend.put(mood, 0f);
                }
                blend.put(PetComponent.Mood.ANGRY, 0.7f);
                blend.put(PetComponent.Mood.CALM, 0.3f);

                EnumMap<PetComponent.Emotion, Float> neutralSignals = new EnumMap<>(PetComponent.Emotion.class);
                neutralSignals.put(PetComponent.Emotion.UBUNTU, 0.3f);
                float totalNeutralSignal = 4.3f; // Dominant neutral emotions outweigh profiled ones.

                var method = PetMoodEngine.class.getDeclaredMethod("applyNatureMoodPreference",
                        EnumMap.class, EnumMap.class, float.class);
                method.setAccessible(true);

                method.invoke(engine, blend, neutralSignals, totalNeutralSignal);

                float angry = blend.getOrDefault(PetComponent.Mood.ANGRY, 0f);
                float bonded = blend.getOrDefault(PetComponent.Mood.BONDED, 0f);

                assertTrue(angry > 0.6f,
                        "Neutral emotions should remain dominant when their raw signal eclipses profiled ones");
                assertTrue(bonded < 0.15f,
                        "Limited nature evidence should not heavily project bonded moods");
            }
        }
    }

    @Test
    void naturePreferenceUsesOnlyProfileEmotionsWithEvidence() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.UBUNTU, 0.6f,
                PetComponent.Emotion.FRUSTRATION, 0.45f,
                null, 0f));

        try (MockedStatic<FabricLoader> loaderMock = mockStatic(FabricLoader.class)) {
            FabricLoader loader = mock(FabricLoader.class);
            loaderMock.when(FabricLoader::getInstance).thenReturn(loader);
            Path configDir = Files.createTempDirectory("petsplus-config");
            when(loader.getConfigDir()).thenReturn(configDir);

            try (MockedStatic<PetsPlusConfig> configMock = mockStatic(PetsPlusConfig.class)) {
                PetsPlusConfig config = mock(PetsPlusConfig.class);
                configMock.when(PetsPlusConfig::getInstance).thenReturn(config);
                when(config.getConfigGeneration()).thenReturn(1);
                JsonObject moods = new JsonObject();
                JsonObject weights = new JsonObject();
                JsonObject opponents = new JsonObject();
                JsonObject animation = new JsonObject();
                moods.add("weight", weights);
                moods.add("opponents", opponents);
                moods.add("animation", animation);
                when(config.getSection("moods")).thenReturn(moods);
                when(config.getSection("weight")).thenReturn(weights);
                when(config.getSection("opponents")).thenReturn(opponents);
                when(config.getSection("animation")).thenReturn(animation);

                EnumMap<PetComponent.Mood, Float> blend = new EnumMap<>(PetComponent.Mood.class);
                for (PetComponent.Mood mood : PetComponent.Mood.values()) {
                    blend.put(mood, 0f);
                }
                blend.put(PetComponent.Mood.ANGRY, 0.6f);
                blend.put(PetComponent.Mood.RESTLESS, 0.25f);
                blend.put(PetComponent.Mood.PASSIONATE, 0.15f);

                EnumMap<PetComponent.Emotion, Float> neutralSignals = new EnumMap<>(PetComponent.Emotion.class);
                neutralSignals.put(PetComponent.Emotion.FRUSTRATION, 2.0f);
                float totalNeutralSignal = 2.0f;

                var method = PetMoodEngine.class.getDeclaredMethod("applyNatureMoodPreference",
                        EnumMap.class, EnumMap.class, float.class);
                method.setAccessible(true);

                EnumMap<PetComponent.Mood, Float> before = new EnumMap<>(blend);
                method.invoke(engine, blend, neutralSignals, totalNeutralSignal);

                float bonded = blend.getOrDefault(PetComponent.Mood.BONDED, 0f);
                assertEquals(0f, bonded, 1.0e-4f,
                        "Major profile moods should not appear without matching evidence");

                float clusterTotal = blend.getOrDefault(PetComponent.Mood.ANGRY, 0f)
                        + blend.getOrDefault(PetComponent.Mood.RESTLESS, 0f)
                        + blend.getOrDefault(PetComponent.Mood.PASSIONATE, 0f);
                assertEquals(1f, clusterTotal, 1.0e-4f,
                        "Minor-only evidence should concentrate weight in its mapped moods");
            }
        }
    }

    @Test
    void stackedNatureSlotsAccumulateScaleBonuses() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.CHEERFUL, 0.6f,
                PetComponent.Emotion.CHEERFUL, 0.4f,
                PetComponent.Emotion.CHEERFUL, 0.3f));

        var stimulusMethod = PetMoodEngine.class.getDeclaredMethod("getNatureStimulusBias", PetComponent.Emotion.class);
        var weightMethod = PetMoodEngine.class.getDeclaredMethod("getNatureWeightBias", PetComponent.Emotion.class);
        var guardMethod = PetMoodEngine.class.getDeclaredMethod("getNatureGuardBias", PetComponent.Emotion.class);
        stimulusMethod.setAccessible(true);
        weightMethod.setAccessible(true);
        guardMethod.setAccessible(true);

        float stimulusBias = (float) stimulusMethod.invoke(engine, PetComponent.Emotion.CHEERFUL);
        float weightBias = (float) weightMethod.invoke(engine, PetComponent.Emotion.CHEERFUL);
        float guardBias = (float) guardMethod.invoke(engine, PetComponent.Emotion.CHEERFUL);

        assertEquals(1.47f, stimulusBias, 1.0e-3f,
                "Stimulus bias should sum major, minor, and quirk contributions for stacked slots");
        assertEquals(1.465f, weightBias, 1.0e-3f,
                "Weight bias should accumulate scale bonuses across all matching slots");
        assertEquals(1.24f, guardBias, 1.0e-3f,
                "Guard bias should include every slot's scaled presence when duplicated");
    }

    @Test
    void opponentSuppressionReducesNatureMoodBias() throws Exception {
        try (MockedStatic<FabricLoader> loaderMock = mockStatic(FabricLoader.class)) {
            FabricLoader loader = mock(FabricLoader.class);
            loaderMock.when(FabricLoader::getInstance).thenReturn(loader);
            Path configDir = Files.createTempDirectory("petsplus-config");
            when(loader.getConfigDir()).thenReturn(configDir);

            try (MockedStatic<PetsPlusConfig> configMock = mockStatic(PetsPlusConfig.class)) {
                PetsPlusConfig config = mock(PetsPlusConfig.class);
                configMock.when(PetsPlusConfig::getInstance).thenReturn(config);
                when(config.getConfigGeneration()).thenReturn(1);
                JsonObject moods = new JsonObject();
                JsonObject weights = new JsonObject();
                JsonObject opponents = new JsonObject();
                JsonObject animation = new JsonObject();
                moods.add("weight", weights);
                moods.add("opponents", opponents);
                moods.add("animation", animation);
                when(config.getSection("moods")).thenReturn(moods);
                when(config.getSection("weight")).thenReturn(weights);
                when(config.getSection("opponents")).thenReturn(opponents);
                when(config.getSection("animation")).thenReturn(animation);

                long now = 100L;
                long later = now + 40L;

                PetComponent suppressedParent = mock(PetComponent.class);
                stubCommonParentInteractions(suppressedParent);
                when(suppressedParent.getBondStrength()).thenReturn(2400L);
                MobEntity suppressedPet = suppressedParent.getPet();
                ServerWorld suppressedWorld = (ServerWorld) suppressedPet.getWorld();
                when(suppressedWorld.getTime()).thenReturn(later);

                PetMoodEngine suppressed = new PetMoodEngine(suppressedParent);
                suppressed.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                        PetComponent.Emotion.ANGST, 0.7f,
                        null, 0f,
                        null, 0f));

                suppressed.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.ANGST, 0.45f), now);
                suppressed.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.CHEERFUL, 0.6f), now);
                suppressed.ensureFresh(later);

                EnumMap<PetComponent.Mood, Float> suppressedBlend = getLastNormalizedBlend(suppressed);

                PetComponent soloParent = mock(PetComponent.class);
                stubCommonParentInteractions(soloParent);
                when(soloParent.getBondStrength()).thenReturn(2400L);
                MobEntity soloPet = soloParent.getPet();
                ServerWorld soloWorld = (ServerWorld) soloPet.getWorld();
                when(soloWorld.getTime()).thenReturn(later);

                PetMoodEngine solo = new PetMoodEngine(soloParent);
                solo.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                        PetComponent.Emotion.ANGST, 0.7f,
                        null, 0f,
                        null, 0f));

                solo.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.ANGST, 0.45f), now);
                solo.ensureFresh(later);

                EnumMap<PetComponent.Mood, Float> soloBlend = getLastNormalizedBlend(solo);

                float suppressedAfraid = suppressedBlend.getOrDefault(PetComponent.Mood.AFRAID, 0f);
                float soloAfraid = soloBlend.getOrDefault(PetComponent.Mood.AFRAID, 0f);

                assertTrue(suppressedAfraid <= soloAfraid + 1.0e-4f,
                        "Opponent suppression should not increase the nature-driven afraid mood weight (solo="
                                + soloAfraid + ", suppressed=" + suppressedAfraid + ")");

                float suppressedHappy = suppressedBlend.getOrDefault(PetComponent.Mood.HAPPY, 0f);
                float soloHappy = soloBlend.getOrDefault(PetComponent.Mood.HAPPY, 0f);
                assertTrue(suppressedHappy >= soloHappy - 1.0e-4f,
                        "Opponent suppression should reallocate weight toward cheerful moods (solo="
                                + soloHappy + ", suppressed=" + suppressedHappy + ")");

                float suppressedTotal = (float) suppressedBlend.values().stream()
                        .mapToDouble(Float::doubleValue)
                        .sum();
                assertEquals(1f, suppressedTotal, 1.0e-4f, "Suppressed blend should remain normalised");
            }
        }
    }

    @Test
    void protectiveConditionRespectsOwnerDangerTelemetry() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        stubCommonParentInteractions(parent);
        when(parent.getBondStrength()).thenReturn(2400L);

        PetMoodEngine engine = new PetMoodEngine(parent);
        PetComponent.OwnerDangerTelemetry dangerTelemetry = new PetComponent.OwnerDangerTelemetry(
                true, 0.4f, 0.8f, 0.6f, true, true);
        when(parent.getOwnerDangerTelemetry()).thenReturn(dangerTelemetry);

        var method = PetMoodEngine.class.getDeclaredMethod("hasOngoingCondition",
                PetComponent.Emotion.class, long.class);
        method.setAccessible(true);

        boolean persists = (boolean) method.invoke(engine, PetComponent.Emotion.PROTECTIVENESS, 200L);
        assertTrue(persists, "Protectiveness should persist while owner danger telemetry reports active threat");

        when(parent.getOwnerDangerTelemetry()).thenReturn(PetComponent.OwnerDangerTelemetry.ABSENT);
        boolean cleared = (boolean) method.invoke(engine, PetComponent.Emotion.PROTECTIVENESS, 400L);
        assertFalse(cleared,
                "Protectiveness should not persist once owner danger telemetry indicates safety");
    }

    private static EnumMap<PetComponent.Mood, Float> callResolveEmotionToMoodWeights(PetComponent.Emotion emotion) throws Exception {
        var method = PetMoodEngine.class.getDeclaredMethod(
                "resolveEmotionToMoodWeights", JsonObject.class, PetComponent.Emotion.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        EnumMap<PetComponent.Mood, Float> weights =
                (EnumMap<PetComponent.Mood, Float>) method.invoke(null, null, emotion);
        return weights;
    }

    private static Map<PetComponent.Mood, Float> callAuthoredEmotionDefaults(PetComponent.Emotion emotion) throws Exception {
        var method = PetMoodEngine.class.getDeclaredMethod(
                "getAuthoredEmotionToMoodDefaults", PetComponent.Emotion.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<PetComponent.Mood, Float> defaults =
                (Map<PetComponent.Mood, Float>) method.invoke(null, emotion);
        return defaults;
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

    private static float getWeight(Object record) throws Exception {
        var weightField = record.getClass().getDeclaredField("weight");
        weightField.setAccessible(true);
        return weightField.getFloat(record);
    }

    private static void setFloatField(Object target, String name, float value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setFloat(target, value);
    }

    private static void setLongField(Object target, String name, long value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(target, value);
    }

    private static float getNovelty(Object record) throws Exception {
        var noveltyField = record.getClass().getDeclaredField("novelty");
        noveltyField.setAccessible(true);
        return noveltyField.getFloat(record);
    }

    private static EnumMap<PetComponent.Mood, Float> getLastNormalizedBlend(PetMoodEngine engine) throws Exception {
        var field = PetMoodEngine.class.getDeclaredField("lastNormalizedWeights");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        EnumMap<PetComponent.Mood, Float> stored = new EnumMap<>((EnumMap<PetComponent.Mood, Float>) field.get(engine));
        return stored;
    }

    private static float invokeContagionCap(PetMoodEngine engine, float bondFactor) throws Exception {
        var method = PetMoodEngine.class.getDeclaredMethod("computeContagionCap", float.class);
        method.setAccessible(true);
        return (float) method.invoke(engine, bondFactor);
    }

    private static void stubCommonParentInteractions(PetComponent parent) {
        when(parent.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class)).thenReturn(null);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class, Long.MIN_VALUE))
                .thenReturn(Long.MIN_VALUE);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_SENSITIZED_STREAK, Integer.class, 0))
                .thenReturn(0);
        when(parent.getNatureVolatilityMultiplier()).thenReturn(1f);
        when(parent.getNatureResilienceMultiplier()).thenReturn(1f);
        when(parent.getNatureContagionModifier()).thenReturn(1f);
        when(parent.getNatureGuardModifier()).thenReturn(1f);
        when(parent.getOwnerDangerTelemetry()).thenReturn(PetComponent.OwnerDangerTelemetry.ABSENT);
        ServerWorld world = mock(ServerWorld.class);
        when(world.getTime()).thenReturn(0L);
        MobEntity pet = mock(MobEntity.class);
        when(pet.getWorld()).thenReturn(world);
        when(pet.getHealth()).thenReturn(20f);
        when(pet.getMaxHealth()).thenReturn(20f);
        when(parent.getPet()).thenReturn(pet);
    }
}
