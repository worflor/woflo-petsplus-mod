package woflo.petsplus.state.emotions;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.UIStyle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of the impact-weighted emotional intensity system described in the design doc.
 *
 * <p>The engine is responsible for maintaining lightweight emotion records, translating them into
 * mood weights on demand, and exposing the resulting blend/labels to the rest of the mod.</p>
 */
public final class PetMoodEngine {
    private static final float EPSILON = 0.01f;
    private static final float DEFAULT_INTENSITY = 0f;
    private static final float DEFAULT_IMPACT_CAP = 4.0f;
    private static final float DEFAULT_WEIGHT_CAP = 6.0f;
    private static final float CADENCE_ALPHA = 0.35f;
    private static final float VOLATILITY_ALPHA = 0.25f;
    private static final float PEAK_ALPHA = 0.18f;
    private static final float HABITUATION_BASE = 1200f;
    private static final float HALF_LIFE_MULTIPLIER = 1.35f;
    private static final float MIN_HALF_LIFE = 400f;
    private static final float MAX_HALF_LIFE = 3600f;
    private static final float HOMEOSTASIS_RECOVERY_HALF = 2400f;
    private static final float RELATIONSHIP_BASE = 1.0f;
    private static final float DANGER_BASE = 1.0f;
    private static final float APPRAISAL_BASE = 0.85f;
    private static final float NOVELTY_MIN = 0.05f;
    private static final float NOVELTY_MAX = 0.35f;
    private static final float NOVELTY_HALF_LIFE_FRACTION = 0.65f;
    private static final int MOMENTUM_HISTORY_SIZE = 10;
    private static final float OPPONENT_TRANSFER_MAX = 0.30f;
    private static final float REBOUND_GAIN = 0.18f;
    private static final float RELATIONSHIP_VARIANCE = 2200f;
    private static final float CARE_PULSE_HALF_LIFE = 3600f;
    private static final float DANGER_HALF_LIFE = 1200f;
    
    // Emotional buildup & level transition system
    private static final int LEVEL_HISTORY_SIZE = 30;  // Track ~600 ticks (30 seconds)
    private static final float BUILDUP_RISING_MULTIPLIER = 1.2f;
    private static final float BUILDUP_FALLING_MULTIPLIER = 0.85f;
    private static final float BUILDUP_TREND_THRESHOLD = 0.05f;

    private static final int[] BASE_BREATHING_SPEEDS = {300, 200, 120};
    private static final int[] BASE_GRADIENT_SPEEDS = {280, 180, 100};
    private static final int[] BASE_SHIMMER_SPEEDS = {240, 150, 80};

    /**
     * Authored default emotion-to-mood mapping derived from the narrative clusters described in the
     * impact-weighted mood story. Each entry intentionally highlights the moods that should dominate
     * when the corresponding emotion is active so config-less installations still surface expressive
     * behaviour designers expect. The values are normalised so maintainers can audit or tweak the
     * blend coefficients without hunting through code paths.
     */
    private static final EnumMap<PetComponent.Emotion, EnumMap<PetComponent.Mood, Float>>
            DEFAULT_EMOTION_TO_MOOD = buildDefaultEmotionToMoodTable();

    private final PetComponent parent;

    private final EnumMap<PetComponent.Emotion, EmotionRecord> emotionRecords =
            new EnumMap<>(PetComponent.Emotion.class);
    private final EnumMap<PetComponent.Mood, Float> moodBlend =
            new EnumMap<>(PetComponent.Mood.class);
    private final EnumMap<PetComponent.Mood, Float> lastNormalizedWeights =
            new EnumMap<>(PetComponent.Mood.class);
    private final ArrayDeque<Float> dominantHistory = new ArrayDeque<>();
    
    // Emotional buildup tracking for momentum-based leveling
    private final ArrayDeque<LevelSnapshot> levelHistory = new ArrayDeque<>();
    private float recentLevel23Time = 0f;
    private int previousMoodLevel = 0;
    private float previousMoodStrength = 0f;

    private final EnumMap<PetComponent.Emotion, Float> paletteBlend =
            new EnumMap<>(PetComponent.Emotion.class);
    private List<PetComponent.WeightedEmotionColor> currentPaletteStops = Collections.emptyList();
    private List<PetComponent.WeightedEmotionColor> stagedPaletteStops = Collections.emptyList();
    private boolean hasPendingPalette = false;
    private boolean paletteCommittedOnce = false;
    private long lastPaletteCommitTime = 0L;
    private int paletteGeneration = 0;
    private int lastRenderedPaletteGeneration = -1;
    private float animationIntensity = 0f;
    private PetComponent.NatureEmotionProfile natureEmotionProfile = PetComponent.NatureEmotionProfile.EMPTY;

    private float lastRelationshipGuardObserved = RELATIONSHIP_BASE;
    private float lastDangerWindowObserved = DANGER_BASE;
    private float lastContagionCap = MathHelper.clamp(DEFAULT_IMPACT_CAP * 0.1f, 0.05f, 0.6f);

    private float[] scratchCadences = new float[16];
    private int scratchCadenceCount = 0;
    private float[] scratchIntensities = new float[16];
    private int scratchIntensityCount = 0;
    private float[] scratchSignals = new float[16];
    private int scratchSignalCount = 0;
    private float[] scratchFrequencies = new float[16];
    private int scratchFrequencyCount = 0;
    private float[] scratchBudgets = new float[16];
    private int scratchBudgetCount = 0;
    private final ArrayList<Candidate> scratchSurvivors = new ArrayList<>();

    private PetComponent.Mood currentMood = PetComponent.Mood.CALM;
    private int moodLevel = 0;
    private long lastMoodUpdate = 0L;
    private long lastStimulusTime = 0L;
    private boolean dirty = false;

    // Text animation caching
    private Text cachedMoodText = null;
    private Text cachedMoodTextWithDebug = null;
    private long lastTextUpdateTime = -1L;
    private int cachedLastLevel = -1;

    // Config cache
    private int cachedConfigGeneration = -1;
    private JsonObject cachedMoodsSection;
    private JsonObject cachedWeightSection;
    private JsonObject cachedOpponentSection;
    private JsonObject cachedAnimationSection;
    private double cachedMomentum = 0.35d;
    private double cachedSwitchMargin = 0.05d;
    private int cachedHysteresisTicks = 60;
    private float cachedEpsilon = 0.05f;
    private float[] cachedLevelThresholds = new float[]{0.35f, 0.65f, 0.88f};  // Improved distribution
    private int cachedBaseAnimationUpdateInterval = 16;
    
    // Per-mood threshold adjustments (configurable)
    private final EnumMap<PetComponent.Mood, float[]> perMoodThresholds = new EnumMap<>(PetComponent.Mood.class);
    private double cachedAnimationSpeedMultiplier = 0.15d;
    private int cachedMinAnimationInterval = 4;
    private int cachedMaxAnimationInterval = 40;

    private final EnumMap<PetComponent.Emotion, EnumSet<PetComponent.Emotion>> opponentPairs =
            new EnumMap<>(PetComponent.Emotion.class);

    public PetMoodEngine(PetComponent parent) {
        this.parent = parent;
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            moodBlend.put(mood, 0f);
            lastNormalizedWeights.put(mood, 0f);
        }
        buildDefaultOpponentPairs();
        initializePerMoodThresholds();
    }

    public PetComponent.Mood getCurrentMood() {
        update();
        return currentMood;
    }

    public int getMoodLevel() {
        update();
        return moodLevel;
    }

    void update() {
        long now = parent.getPet().getWorld() instanceof ServerWorld sw ? sw.getTime() : lastMoodUpdate;
        ensureFresh(now);
    }

    public void ensureFresh(long now) {
        if (dirty || now - lastMoodUpdate >= 20) {
            updateEmotionStateAndMood(now);
            dirty = false;
        }
    }

    public long estimateNextWakeUp(long now) {
        if (dirty) {
            return 1L;
        }
        long soonest = Long.MAX_VALUE;
        for (EmotionRecord record : emotionRecords.values()) {
            float cadence = record.cadenceEMA > 0f ? record.cadenceEMA : HABITUATION_BASE;
            float adaptiveHalf = MathHelper.clamp(cadence * HALF_LIFE_MULTIPLIER, MIN_HALF_LIFE, MAX_HALF_LIFE);
            long elapsed = Math.max(0L, now - record.lastUpdateTime);
            long next = Math.max(1L, Math.round(adaptiveHalf - elapsed));
            if (next < soonest) {
                soonest = next;
            }
        }
        if (soonest == Long.MAX_VALUE) {
            return 200L;
        }
        return Math.max(20L, soonest);
    }

    public List<PetComponent.EmotionDebugInfo> getEmotionPoolDebug() {
        update();
        List<PetComponent.EmotionDebugInfo> debug = new ArrayList<>();
        for (EmotionRecord record : emotionRecords.values()) {
            if (record.intensity > EPSILON || record.weight > EPSILON) {
                debug.add(new PetComponent.EmotionDebugInfo(record.emotion, record.weight, false));
            }
        }
        debug.sort(Comparator.comparingDouble((PetComponent.EmotionDebugInfo info) -> info.weight()).reversed());
        return debug;
    }

    public void applyStimulus(PetComponent.EmotionDelta delta, long eventTime) {
        if (delta == null) {
            return;
        }
        long now = eventTime > 0 ? eventTime : parent.getPet().getWorld().getTime();
        pushEmotion(delta.emotion(), delta.amount(), now);
        lastStimulusTime = now;
        dirty = true;
    }

    public void onNatureTuningChanged() {
        dirty = true;
    }

    public void onNatureEmotionProfileChanged(PetComponent.NatureEmotionProfile profile) {
        natureEmotionProfile = profile != null ? profile : PetComponent.NatureEmotionProfile.EMPTY;
        dirty = true;
    }

    public PetComponent.NatureGuardTelemetry getNatureGuardTelemetry() {
        return new PetComponent.NatureGuardTelemetry(
            lastRelationshipGuardObserved,
            lastDangerWindowObserved,
            lastContagionCap);
    }

    private void pushEmotion(PetComponent.Emotion emotion, float amount, long now) {
        if (Math.abs(amount) < EPSILON) {
            emotionRecords.remove(emotion);
            return;
        }

        EmotionRecord record = emotionRecords.computeIfAbsent(emotion, e -> new EmotionRecord(e, now));
        record.applyDecay(now, this);

        if (amount < 0f) {
            float reduction = Math.abs(amount);
            record.intensity = Math.max(0f, record.intensity - reduction);
            record.impactBudget = Math.max(0f, record.impactBudget - reduction * 0.5f);
            record.lastEventTime = now;
            record.lastUpdateTime = now;
            return;
        }

        // Global intensity cap at 0.5f to prevent overwhelming
        float sample = MathHelper.clamp(amount, 0f, 0.5f);
        sample *= getNatureStimulusBias(emotion);
        sample = MathHelper.clamp(sample, 0f, 1f);
        float volatilityMultiplier = parent.getNatureVolatilityMultiplier();
        float resilienceMultiplier = parent.getNatureResilienceMultiplier();
        if (record.startTime <= 0) {
            record.startTime = now;
        }

        long delta = record.lastEventTime > 0 ? Math.max(1L, now - record.lastEventTime) : Math.max(1L, Math.round(HABITUATION_BASE));
        record.lastEventTime = now;
        record.lastUpdateTime = now;

        // Update cadence & volatility EMAs
        if (record.cadenceEMA <= 0f) {
            record.cadenceEMA = delta;
        } else {
            record.cadenceEMA = MathHelper.lerp(CADENCE_ALPHA, record.cadenceEMA, delta);
        }
        float volatilitySample = Math.abs(sample - record.intensity) * volatilityMultiplier;
        volatilitySample = MathHelper.clamp(volatilitySample, 0f, 1f);
        record.volatilityEMA = MathHelper.clamp(
            MathHelper.lerp(VOLATILITY_ALPHA, record.volatilityEMA, volatilitySample), 0f, 1f);
        record.peakEMA = MathHelper.lerp(PEAK_ALPHA, record.peakEMA, Math.max(record.peakEMA, sample));

        // Rekindle boost: bring intensity toward new sample with spike bias
        float spikeBias = MathHelper.clamp(0.55f + 0.45f * (float) Math.exp(-delta / Math.max(1f, record.cadenceEMA)), 0.55f, 0.95f);
        // Validate spikeBias is in reasonable range
        spikeBias = MathHelper.clamp(spikeBias, 0.0f, 1.0f);
        record.intensity = MathHelper.clamp(MathHelper.lerp(spikeBias, record.intensity, sample), 0f, 1f);

        // Impact budget accrues using rekindle-aware boost
        float rekindleBoost = 1.0f + Math.min(0.6f, record.sensitisationGain - 1.0f);
        // Validate rekindleBoost is in reasonable range
        rekindleBoost = MathHelper.clamp(rekindleBoost, 0.0f, 2.0f);
        float impactGain = sample * rekindleBoost * resilienceMultiplier;
        record.impactBudget = Math.min(getImpactCap(), record.impactBudget + impactGain);

        // Habituation: Frequent stimuli DECREASE sensitivity (habituate), rare stimuli INCREASE it (sensitize)
        // This is now psychologically accurate - the previous logic was reversed
        float cadenceRatio = record.cadenceEMA > 0f ? MathHelper.clamp(delta / record.cadenceEMA, 0f, 2.5f) : 1f;
        float sensitisationDelta;
        if (cadenceRatio < 0.8f) {
            // Stimuli arriving faster than expected → habituation (decrease gain)
            // Previously this decreased gain correctly, keeping this
            sensitisationDelta = -0.12f * (0.8f - cadenceRatio);
        } else if (cadenceRatio > 1.5f) {
            // Long gap before stimulus → sensitization (increase gain)
            // Previously this increased gain correctly, keeping this
            sensitisationDelta = 0.08f * Math.min(1f, cadenceRatio - 1.5f);
        } else {
            // Normal cadence → slow return to baseline (1.0)
            sensitisationDelta = (1.0f - record.sensitisationGain) * 0.05f;
        }
        sensitisationDelta *= resilienceMultiplier;
        record.sensitisationGain = MathHelper.clamp(record.sensitisationGain + sensitisationDelta, 0.5f, 1.4f);

        // Habituation slope adapts slowly toward cadence
        if (record.habituationSlope <= 0f) {
            record.habituationSlope = Math.max(HABITUATION_BASE, record.cadenceEMA * 1.1f);
        } else {
            float targetSlope = Math.max(HABITUATION_BASE * 0.5f, record.cadenceEMA * 1.2f);
            record.habituationSlope = MathHelper.lerp(0.1f, record.habituationSlope, targetSlope);
        }

        // Homeostasis bias trends back toward baseline when intensity decreases
        float towardBaseline = record.intensity < record.homeostasisBias ? 0.12f : -0.06f;
        float tunedTowardBaseline = towardBaseline * resilienceMultiplier
                * getNatureQuirkReboundModifier(emotion);
        record.homeostasisBias = MathHelper.clamp(record.homeostasisBias + tunedTowardBaseline, 0.75f, 1.35f);

        refreshContextGuards(record, now, delta);
    }

    public void addContagionShare(PetComponent.Emotion emotion, float amount) {
        long now = parent.getPet().getWorld().getTime();
        float bondFactor = parent.computeBondResilience(now);
        addContagionShare(emotion, amount, now, bondFactor);
    }

    public void addContagionShare(PetComponent.Emotion emotion, float amount, long now, float bondFactor) {
        if (Math.abs(amount) < EPSILON) {
            return;
        }
        EmotionRecord record = emotionRecords.computeIfAbsent(emotion, e -> new EmotionRecord(e, now));
        record.applyDecay(now, this);

        float spreadBias = getNatureContagionSpreadBias(emotion);
        // Validate spreadBias is in reasonable range
        spreadBias = MathHelper.clamp(spreadBias, 0.0f, 2.0f);
        float tunedAmount = amount * spreadBias;
        // Validate bondFactor is in reasonable range
        bondFactor = MathHelper.clamp(bondFactor, 0.0f, 2.0f);
        float cap = computeContagionCap(bondFactor);
        float tunedCap = cap * MathHelper.clamp(spreadBias, 0.7f, 1.35f);
        float minCap = Math.min(cap, 0.05f);
        float maxCap = Math.max(cap, 0.6f);
        tunedCap = MathHelper.clamp(tunedCap, minCap, maxCap);
        lastContagionCap = tunedCap;
        float updated = MathHelper.clamp(record.contagionShare + tunedAmount, -tunedCap, tunedCap);
        record.contagionShare = updated;
        record.lastUpdateTime = now;
    }

    public float getMoodStrength(PetComponent.Mood mood) {
        update();
        return MathHelper.clamp(moodBlend.getOrDefault(mood, 0f), 0f, 1f);
    }

    public Map<PetComponent.Mood, Float> getMoodBlend() {
        update();
        return Collections.unmodifiableMap(new EnumMap<>(moodBlend));
    }

    public boolean hasMoodAbove(PetComponent.Mood mood, float threshold) {
        return getMoodStrength(mood) >= threshold;
    }

    public PetComponent.Mood getDominantMood() {
        update();
        return currentMood;
    }

    @Nullable
    public PetComponent.Emotion getDominantEmotion() {
        update();
        return emotionRecords.values().stream()
                .max(Comparator.comparingDouble(r -> r.weight))
                .map(record -> record.emotion)
                .orElse(null);
    }

    String getDominantEmotionName() {
        PetComponent.Emotion dominant = getDominantEmotion();
        return dominant != null ? prettify(dominant.name()) : "None";
    }

    public Text getMoodText() {
        return getCachedMoodText(false);
    }

    public Text getMoodTextWithDebug() {
        return getCachedMoodText(true);
    }

    public List<PetComponent.WeightedEmotionColor> getCurrentEmotionPalette() {
        update();
        return currentPaletteStops;
    }

    public float getAnimationIntensity() {
        update();
        return animationIntensity;
    }

    public void writeToNbt(NbtCompound nbt) {
        NbtCompound emotions = new NbtCompound();
        for (EmotionRecord record : emotionRecords.values()) {
            NbtCompound tag = new NbtCompound();
            tag.putFloat("intensity", record.intensity);
            tag.putFloat("impact", record.impactBudget);
            tag.putFloat("cadence", record.cadenceEMA);
            tag.putFloat("volatility", record.volatilityEMA);
            tag.putFloat("peak", record.peakEMA);
            tag.putFloat("habituation", record.habituationSlope);
            tag.putFloat("sensitisation", record.sensitisationGain);
            tag.putFloat("homeostasis", record.homeostasisBias);
            tag.putFloat("contagion", record.contagionShare);
            tag.putFloat("relationship", record.relationshipGuard);
            tag.putFloat("danger", record.dangerWindow);
            tag.putFloat("appraisal", record.appraisalConfidence);
            tag.putFloat("weight", record.weight);
            tag.putLong("start", record.startTime);
            tag.putLong("lastEvent", record.lastEventTime);
            tag.putLong("lastUpdate", record.lastUpdateTime);
            emotions.put(record.emotion.name(), tag);
        }
        nbt.put("emotionRecords", emotions);

        NbtCompound blend = new NbtCompound();
        for (Map.Entry<PetComponent.Mood, Float> entry : moodBlend.entrySet()) {
            blend.putFloat(entry.getKey().name(), entry.getValue());
        }
        nbt.put("moodBlend", blend);
        nbt.putString("currentMood", currentMood.name());
        nbt.putInt("moodLevel", moodLevel);

        NbtCompound history = new NbtCompound();
        int idx = 0;
        for (Float value : dominantHistory) {
            history.putFloat(Integer.toString(idx++), value);
        }
        nbt.put("dominantHistory", history);
    }

    public void readFromNbt(NbtCompound nbt) {
        emotionRecords.clear();
        if (nbt.contains("emotionRecords")) {
            nbt.getCompound("emotionRecords").ifPresent(emotions -> {
                for (String key : emotions.getKeys()) {
                    PetComponent.Emotion emotion;
                    try {
                        emotion = PetComponent.Emotion.valueOf(key);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    emotions.getCompound(key).ifPresent(tag -> {
                        EmotionRecord record = new EmotionRecord(emotion, parent.getPet().getWorld().getTime());
                        record.intensity = tag.getFloat("intensity").orElse(0f);
                        record.impactBudget = tag.getFloat("impact").orElse(0f);
                        record.cadenceEMA = tag.getFloat("cadence").orElse(0f);
                        record.volatilityEMA = tag.getFloat("volatility").orElse(0f);
                        record.peakEMA = tag.getFloat("peak").orElse(0f);
                        record.habituationSlope = tag.getFloat("habituation").orElse(HABITUATION_BASE);
                        record.sensitisationGain = tag.getFloat("sensitisation").orElse(1.0f);
                        record.homeostasisBias = tag.getFloat("homeostasis").orElse(1.0f);
                        record.contagionShare = tag.getFloat("contagion").orElse(0f);
                        record.relationshipGuard = tag.getFloat("relationship").orElse(RELATIONSHIP_BASE);
                        record.dangerWindow = tag.getFloat("danger").orElse(DANGER_BASE);
                        record.appraisalConfidence = tag.getFloat("appraisal").orElse(APPRAISAL_BASE);
                        record.weight = tag.getFloat("weight").orElse(0f);
                        record.startTime = tag.getLong("start").orElse(parent.getPet().getWorld().getTime());
                        record.lastEventTime = tag.getLong("lastEvent").orElse(record.startTime);
                        record.lastUpdateTime = tag.getLong("lastUpdate").orElse(record.lastEventTime);
                        emotionRecords.put(emotion, record);
                    });
                }
            });
        }

        moodBlend.clear();
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            moodBlend.put(mood, 0f);
        }
        if (nbt.contains("moodBlend")) {
            nbt.getCompound("moodBlend").ifPresent(blend -> {
                float sum = 0f;
                for (String key : blend.getKeys()) {
                    try {
                        PetComponent.Mood mood = PetComponent.Mood.valueOf(key);
                        float value = blend.getFloat(key).orElse(0f);
                        if (value > 0f) {
                            moodBlend.put(mood, value);
                            sum += value;
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (sum > 0f) {
                    for (Map.Entry<PetComponent.Mood, Float> entry : moodBlend.entrySet()) {
                        entry.setValue(entry.getValue() / sum);
                    }
                }
            });
        }
        nbt.getString("currentMood").ifPresent(value -> {
            try {
                currentMood = PetComponent.Mood.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                currentMood = PetComponent.Mood.CALM;
            }
        });
        nbt.getInt("moodLevel").ifPresent(value -> moodLevel = value);

        lastNormalizedWeights.clear();
        lastNormalizedWeights.putAll(moodBlend);

        dominantHistory.clear();
        if (nbt.contains("dominantHistory")) {
            nbt.getCompound("dominantHistory").ifPresent(history -> {
                List<String> keys = new ArrayList<>(history.getKeys());
                keys.sort(Comparator.naturalOrder());
                for (String key : keys) {
                    history.getFloat(key).ifPresent(value -> dominantHistory.add(value));
                }
            });
        }
        paletteBlend.clear();
        currentPaletteStops = Collections.emptyList();
        stagedPaletteStops = Collections.emptyList();
        hasPendingPalette = false;
        paletteCommittedOnce = false;
        lastPaletteCommitTime = 0L;
        paletteGeneration++;
    }

    // --------------------------------------------------------------------------------------------
    // Core interpretation pipeline
    // --------------------------------------------------------------------------------------------

    private void updateEmotionStateAndMood(long now) {
        if (now - lastMoodUpdate < 20) {
            return;
        }
        lastMoodUpdate = now;

        ensureConfigCache();
        float epsilon = Math.max(EPSILON, cachedEpsilon);
        float guardModifier = MathHelper.clamp(parent.getNatureGuardModifier(), 0.1f, 3.0f);
        float contagionModifier = MathHelper.clamp(parent.getNatureContagionModifier(), 0.1f, 3.0f);
        lastRelationshipGuardObserved = MathHelper.clamp(RELATIONSHIP_BASE * guardModifier, 0.6f, 1.6f);
        lastDangerWindowObserved = MathHelper.clamp(DANGER_BASE * guardModifier, 0.6f, 1.7f);
        float baseCap = DEFAULT_IMPACT_CAP * 0.1f * contagionModifier;
        float minCap = 0.05f * Math.min(1f, contagionModifier);
        float maxCap = 0.6f * Math.max(1f, contagionModifier);
        lastContagionCap = MathHelper.clamp(baseCap, minCap, maxCap);

        // Decay + cleanup pass
        List<EmotionRecord> active = collectActiveRecords(now, epsilon);

        if (active.isEmpty()) {
            resetToCalmBaseline();
            return;
        }

        int activeSize = active.size();

        scratchCadences = ensureCapacity(scratchCadences, activeSize);
        scratchCadenceCount = activeSize;
        for (int i = 0; i < activeSize; i++) {
            EmotionRecord record = active.get(i);
            scratchCadences[i] = record.cadenceEMA > 0f ? record.cadenceEMA : HABITUATION_BASE;
        }
        float cadenceMedian = selectQuantile(scratchCadences, scratchCadenceCount, 0.5f, HABITUATION_BASE);

        scratchIntensities = ensureCapacity(scratchIntensities, activeSize);
        scratchIntensityCount = activeSize;
        for (int i = 0; i < activeSize; i++) {
            scratchIntensities[i] = active.get(i).intensity;
        }
        float quietFloor = selectQuantile(scratchIntensities, scratchIntensityCount, 0.2f, 0.12f);
        float quietCeil = selectQuantile(scratchIntensities, scratchIntensityCount, 0.65f, 0.6f);

        ArrayList<Candidate> candidates = new ArrayList<>(activeSize);
        scratchSignals = ensureCapacity(scratchSignals, activeSize);
        scratchSignalCount = activeSize;
        for (int i = 0; i < activeSize; i++) {
            EmotionRecord record = active.get(i);
            float freshness = computeFreshness(record, now);
            float freq = record.cadenceEMA > 0f ? MathHelper.clamp(cadenceMedian / record.cadenceEMA, 0f, 3.5f) : 0f;
            // Validate frequency is in reasonable range
            freq = MathHelper.clamp(freq, 0.0f, 5.0f);
            float signal = (record.intensity * (0.35f + 0.65f * freshness))
                    + (0.3f * (float) Math.sqrt(Math.max(0f, freq * record.impactBudget)));
            scratchSignals[i] = signal;
            candidates.add(new Candidate(record, freshness, freq, signal));
        }

        float medianSignal = selectQuantile(scratchSignals, scratchSignalCount, 0.5f, 0f);
        float threshold = medianSignal * 0.6f;
        ArrayList<Candidate> survivors = scratchSurvivors;
        survivors.clear();
        for (Candidate candidate : candidates) {
            if (candidate.signal >= threshold) {
                survivors.add(candidate);
            }
        }
        if (survivors.isEmpty() && !candidates.isEmpty()) {
            Candidate best = Collections.max(candidates, Comparator.comparingDouble(c -> c.signal));
            survivors.add(best);
        }

        // Derived stats for weighting
        scratchFrequencies = ensureCapacity(scratchFrequencies, survivors.size());
        scratchFrequencyCount = survivors.size();
        for (int i = 0; i < scratchFrequencyCount; i++) {
            scratchFrequencies[i] = survivors.get(i).frequency;
        }
        float freqMedian = selectQuantile(scratchFrequencies, scratchFrequencyCount, 0.5f, 1f);
        float freqHigh = selectQuantile(scratchFrequencies, scratchFrequencyCount, 0.9f,
                Math.max(1.2f, freqMedian + 0.5f));
        float recencyScale = selectQuantile(scratchCadences, scratchCadenceCount, 0.7f, 160f);
        float impactCap = computeImpactCap(active);
        float weightCap = Math.max(impactCap * 1.5f, DEFAULT_WEIGHT_CAP);

        // Weight synthesis - Simplified additive model based on psychological realism
        List<Candidate> weighted = new ArrayList<>();
        for (Candidate candidate : survivors) {
            EmotionRecord record = candidate.record;
            float intensity = MathHelper.clamp(record.intensity, 0f, 1f);
            // Double-check intensity bounds
            intensity = MathHelper.clamp(intensity, 0.0f, 1.0f);

            // Base weight: intensity scaled by accumulated impact
            float baseWeight = intensity * (1.0f + MathHelper.clamp(record.impactBudget / impactCap, 0f, 1.5f));

            // Recency boost: Fresh emotions get a spike, then decay naturally
            float lastAge = Math.max(0f, (float) (now - record.lastEventTime));
            float recencyBoost = 0f;
            if (lastAge < 60f) {
                // Strong boost for very fresh emotions (< 3 seconds)
                recencyBoost = 0.4f * (1.0f - lastAge / 60f);
            }
            // Validate recencyBoost is in reasonable range
            recencyBoost = MathHelper.clamp(recencyBoost, 0.0f, 1.0f);

            // Persistence bonus: Ongoing conditions maintain weight
            float persistenceBonus = 0f;
            if (hasOngoingCondition(record.emotion, now)) {
                // Condition still present, add sustained weight
                persistenceBonus = 0.3f * intensity;
            }
            // Validate persistenceBonus is in reasonable range
            persistenceBonus = MathHelper.clamp(persistenceBonus, 0.0f, 1.0f);

            // Habituation penalty: Reduce weight if stimuli are too frequent
            float habituationPenalty = 0f;
            if (record.cadenceEMA > 0f && record.cadenceEMA < 100f) {
                // Very frequent stimuli (< 5 seconds) cause habituation
                habituationPenalty = -0.2f * (1.0f - record.cadenceEMA / 100f);
            }
            // Validate habituationPenalty is in reasonable range
            habituationPenalty = MathHelper.clamp(habituationPenalty, -1.0f, 0.0f);

            // Context modulation: Emotion-specific boosts/penalties based on bond, danger, etc.
            refreshContextGuards(record, now, Math.max(1L, now - record.lastUpdateTime));
            float contextModulation = computeEmotionSpecificContextModulation(record, now);
            // Validate contextModulation is in reasonable range
            contextModulation = MathHelper.clamp(contextModulation, -1.0f, 1.0f);

            // Nature profile weighting
            float profileWeightBias = getNatureWeightBias(record.emotion);
            // Validate profileWeightBias is in reasonable range
            profileWeightBias = MathHelper.clamp(profileWeightBias, 0.0f, 2.0f);

            // Additive formula: sum components, then scale by profile
            float rawWeight = (baseWeight + recencyBoost + persistenceBonus + habituationPenalty + contextModulation + record.contagionShare) * profileWeightBias;
            rawWeight = MathHelper.clamp(rawWeight, 0f, weightCap);

            record.weight = rawWeight;
            weighted.add(new Candidate(record, candidate.freshness, candidate.frequency, rawWeight));
        }

        if (weighted.isEmpty()) {
            resetToCalmBaseline();
            return;
        }

        // Opponent transfer
        applyOpponentTransfers(weighted, weightCap);

        // Sync the record weights with any transfer-adjusted signals so downstream
        // consumers (dominant mood queries, persistence, debugging) reflect the
        // post-transfer values.
        for (Candidate candidate : weighted) {
            candidate.record.weight = candidate.signal;
        }

        // Build mood vector
        EnumMap<PetComponent.Mood, Float> targetBlend = new EnumMap<>(PetComponent.Mood.class);
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            targetBlend.put(mood, 0f);
        }
        for (Candidate candidate : weighted) {
            Map<PetComponent.Mood, Float> mapping = getEmotionToMoodWeights(candidate.record.emotion);
            for (Map.Entry<PetComponent.Mood, Float> entry : mapping.entrySet()) {
                float contribution = candidate.signal * entry.getValue();
                targetBlend.merge(entry.getKey(), Math.max(0f, contribution), Float::sum);
            }
        }

        float total = 0f;
        for (float value : targetBlend.values()) {
            total += Math.max(0f, value);
        }
        if (total <= epsilon) {
            resetToCalmBaseline();
            return;
        }
        for (Map.Entry<PetComponent.Mood, Float> entry : targetBlend.entrySet()) {
            entry.setValue(Math.max(0f, entry.getValue()) / total);
        }

        lastNormalizedWeights.clear();
        lastNormalizedWeights.putAll(targetBlend);

        // Dual-timescale blending: fast for fresh spikes, slow for persistent drifts
        // Strong recent emotions should switch quickly, weak persistent ones should drift slowly
        float maxEmotionFreshness = 0f;
        float maxEmotionWeight = 0f;
        for (Candidate c : weighted) {
            maxEmotionFreshness = Math.max(maxEmotionFreshness, c.freshness);
            maxEmotionWeight = Math.max(maxEmotionWeight, c.signal);
        }
        
        // Adaptive momentum: low for fresh strong emotions (fast switch), high for weak/old ones (slow drift)
        // Fresh spike (freshness > 0.7, strong weight) -> momentum ~0.3 (fast)
        // Persistent weak (freshness < 0.3, weak weight) -> momentum ~0.75 (slow)
        float baseMomentum = (float) MathHelper.clamp(cachedMomentum, 0.0, 1.0);
        float adaptiveMomentum;
        if (maxEmotionFreshness > 0.7f && maxEmotionWeight > 2.0f) {
            // Strong fresh spike: switch fast
            adaptiveMomentum = baseMomentum * 0.5f;
        } else if (maxEmotionFreshness < 0.3f) {
            // Old persistent emotion: drift slow
            adaptiveMomentum = baseMomentum * 1.3f;
        } else {
            // Normal case: use base momentum
            adaptiveMomentum = baseMomentum;
        }
        adaptiveMomentum = MathHelper.clamp(adaptiveMomentum, 0.15f, 0.85f);
        // Final validation of adaptiveMomentum
        adaptiveMomentum = MathHelper.clamp(adaptiveMomentum, 0.0f, 1.0f);
        
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            float cur = moodBlend.getOrDefault(mood, 0f);
            float tar = targetBlend.getOrDefault(mood, 0f);
            float blended = cur + (tar - cur) * adaptiveMomentum;
            moodBlend.put(mood, MathHelper.clamp(blended, 0f, 1f));
        }

        normalizeBlend(moodBlend);

        updateEmotionPalette(weighted);
        updateAnimationIntensity(weighted);

        PetComponent.Mood previousMood = currentMood;
        float previousStrength = moodBlend.getOrDefault(previousMood, 0f);
        PetComponent.Mood bestMood = moodBlend.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(PetComponent.Mood.CALM);
        float bestStrength = moodBlend.getOrDefault(bestMood, 0f);

        float momentumBand = Math.max((float) cachedSwitchMargin, computeMomentumBand(previousStrength));
        if (bestMood != previousMood && (bestStrength - previousStrength) < momentumBand) {
            currentMood = previousMood;
        } else {
            currentMood = bestMood;
        }

        float currentStrength = moodBlend.getOrDefault(currentMood, 0f);
        updateDominantHistory(currentStrength);
        updateMoodLevel(currentStrength);
    }

    private List<EmotionRecord> collectActiveRecords(long now, float epsilon) {
        List<EmotionRecord> active = new ArrayList<>();
        for (EmotionRecord record : new ArrayList<>(emotionRecords.values())) {
            record.applyDecay(now, this);
            float contagionMagnitude = Math.abs(record.contagionShare);
            if (record.intensity <= epsilon && record.impactBudget <= epsilon
                    && contagionMagnitude <= epsilon) {
                emotionRecords.remove(record.emotion);
                continue;
            }
            active.add(record);
        }
        return active;
    }

    private void resetToCalmBaseline() {
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            moodBlend.put(mood, 0f);
        }
        moodBlend.put(PetComponent.Mood.CALM, 1f);
        currentMood = PetComponent.Mood.CALM;
        moodLevel = 0;
        updateDominantHistory(0f);
        lastNormalizedWeights.clear();
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            lastNormalizedWeights.put(mood, moodBlend.getOrDefault(mood, 0f));
        }
        if (!paletteBlend.isEmpty() || !currentPaletteStops.isEmpty()) {
            paletteBlend.clear();
            currentPaletteStops = Collections.emptyList();
            paletteGeneration++;
        }
    }

    private void updateMoodLevel(float currentStrength) {
        // Get mood-specific thresholds if available, otherwise use default
        float[] thresholds = getMoodSpecificThresholds(currentMood);
        
        // Apply buildup multiplier based on emotional momentum
        float buildupMultiplier = computeBuildupMultiplier(currentStrength);
        float effectiveStrength = MathHelper.clamp(currentStrength * buildupMultiplier, 0f, 1f);
        
        // Calculate raw level from thresholds (simple linear scan - O(n) where n=3, very cheap)
        int rawLevel = 0;
        for (int i = 0; i < thresholds.length; i++) {
            if (effectiveStrength >= thresholds[i]) {
                rawLevel++;
            } else {
                break;  // Thresholds are sorted, can early exit
            }
        }
        
        // Apply hysteresis to prevent level jitter
        int newLevel = applyLevelHysteresis(rawLevel, effectiveStrength, thresholds);
        
        // Update level history for habituation tracking (only if level actually changed)
        if (newLevel != previousMoodLevel) {
            updateLevelHistory(newLevel, lastMoodUpdate);
        }
        
        previousMoodLevel = moodLevel;
        previousMoodStrength = currentStrength;
        moodLevel = MathHelper.clamp(newLevel, 0, thresholds.length);
    }

    private void updateDominantHistory(float strength) {
        if (dominantHistory.size() >= MOMENTUM_HISTORY_SIZE) {
            dominantHistory.removeFirst();
        }
        dominantHistory.add(MathHelper.clamp(strength, 0f, 1f));
    }

    private float computeMomentumBand(float previousStrength) {
        if (dominantHistory.isEmpty()) {
            return Math.max(0.08f, previousStrength * 0.25f);
        }
        double mean = dominantHistory.stream().mapToDouble(Float::floatValue).average().orElse(0.0);
        double variance = dominantHistory.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0.0);
        float stddev = (float) Math.sqrt(Math.max(variance, 0.0));
        return Math.max(0.08f, stddev);
    }

    private void normalizeBlend(EnumMap<PetComponent.Mood, Float> blend) {
        float total = 0f;
        for (float value : blend.values()) {
            total += Math.max(0f, value);
        }
        if (total <= EPSILON) {
            for (PetComponent.Mood mood : PetComponent.Mood.values()) {
                blend.put(mood, mood == PetComponent.Mood.CALM ? 1f : 0f);
            }
            return;
        }
        for (Map.Entry<PetComponent.Mood, Float> entry : blend.entrySet()) {
            entry.setValue(Math.max(0f, entry.getValue()) / total);
        }
    }

    private void normalizePalette(EnumMap<PetComponent.Emotion, Float> blend) {
        float total = 0f;
        for (float value : blend.values()) {
            total += Math.max(0f, value);
        }
        if (total <= EPSILON) {
            blend.clear();
            return;
        }
        for (Map.Entry<PetComponent.Emotion, Float> entry : blend.entrySet()) {
            entry.setValue(Math.max(0f, entry.getValue()) / total);
        }
    }

    private float computePaletteDelta(List<PetComponent.WeightedEmotionColor> previous,
            List<PetComponent.WeightedEmotionColor> next) {
        EnumMap<PetComponent.Emotion, Float> previousMap = new EnumMap<>(PetComponent.Emotion.class);
        if (previous != null) {
            for (PetComponent.WeightedEmotionColor stop : previous) {
                previousMap.put(stop.emotion(), stop.weight());
            }
        }
        EnumMap<PetComponent.Emotion, Float> nextMap = new EnumMap<>(PetComponent.Emotion.class);
        if (next != null) {
            for (PetComponent.WeightedEmotionColor stop : next) {
                nextMap.put(stop.emotion(), stop.weight());
            }
        }
        float delta = 0f;
        for (Map.Entry<PetComponent.Emotion, Float> entry : nextMap.entrySet()) {
            float previousValue = previousMap.getOrDefault(entry.getKey(), 0f);
            delta += Math.abs(entry.getValue() - previousValue);
        }
        for (Map.Entry<PetComponent.Emotion, Float> entry : previousMap.entrySet()) {
            if (!nextMap.containsKey(entry.getKey())) {
                delta += Math.abs(entry.getValue());
            }
        }
        return delta;
    }

    private void updateEmotionPalette(List<Candidate> weighted) {
        if (weighted == null || weighted.isEmpty()) {
            if (!paletteBlend.isEmpty()) {
                paletteBlend.clear();
            }
            stagePalette(Collections.emptyList());
            return;
        }

        EnumMap<PetComponent.Emotion, Float> target = new EnumMap<>(PetComponent.Emotion.class);
        float total = 0f;
        for (Candidate candidate : weighted) {
            float weight = Math.max(0f, candidate.signal);
            if (weight <= EPSILON) {
                continue;
            }
            target.merge(candidate.record.emotion, weight, Float::sum);
            total += weight;
        }

        if (total <= EPSILON) {
            paletteBlend.clear();
            stagePalette(Collections.emptyList());
            return;
        }

        for (Map.Entry<PetComponent.Emotion, Float> entry : target.entrySet()) {
            entry.setValue(entry.getValue() / total);
        }

        double momentum = MathHelper.clamp((float) cachedMomentum, 0f, 1f);
        EnumMap<PetComponent.Emotion, Float> blended = new EnumMap<>(PetComponent.Emotion.class);
        for (PetComponent.Emotion emotion : PetComponent.Emotion.values()) {
            float previous = paletteBlend.getOrDefault(emotion, 0f);
            float targetWeight = target.getOrDefault(emotion, 0f);
            float updated = previous + (targetWeight - previous) * (float) momentum;
            if (updated > EPSILON * 0.25f) {
                blended.put(emotion, updated);
            }
        }

        paletteBlend.clear();
        paletteBlend.putAll(blended);
        normalizePalette(paletteBlend);

        if (paletteBlend.isEmpty()) {
            stagePalette(Collections.emptyList());
            return;
        }

        List<Map.Entry<PetComponent.Emotion, Float>> entries = new ArrayList<>(paletteBlend.entrySet());
        entries.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        int limit = Math.min(entries.size(), 4);
        List<PetComponent.WeightedEmotionColor> stops = new ArrayList<>(limit);
        float stopsTotal = 0f;
        for (int i = 0; i < limit; i++) {
            Map.Entry<PetComponent.Emotion, Float> entry = entries.get(i);
            float weight = Math.max(0f, entry.getValue());
            stopsTotal += weight;
            TextColor color = PetComponent.getEmotionColor(entry.getKey());
            stops.add(new PetComponent.WeightedEmotionColor(entry.getKey(), weight, color));
        }

        if (stops.isEmpty() || stopsTotal <= EPSILON) {
            stagePalette(Collections.emptyList());
            return;
        }

        float invTotal = 1f / stopsTotal;
        List<PetComponent.WeightedEmotionColor> normalized = new ArrayList<>(stops.size());
        for (PetComponent.WeightedEmotionColor stop : stops) {
            normalized.add(new PetComponent.WeightedEmotionColor(stop.emotion(), stop.weight() * invTotal, stop.color()));
        }

        List<PetComponent.WeightedEmotionColor> finalStops = List.copyOf(normalized);
        stagePalette(finalStops);
    }

    private void stagePalette(List<PetComponent.WeightedEmotionColor> palette) {
        List<PetComponent.WeightedEmotionColor> target = palette == null
                ? Collections.emptyList()
                : palette;
        boolean matchesCurrent = computePaletteDelta(currentPaletteStops, target) <= EPSILON
                && target.size() == currentPaletteStops.size()
                && target.equals(currentPaletteStops);
        if (matchesCurrent) {
            stagedPaletteStops = target;
            hasPendingPalette = false;
            return;
        }

        boolean matchesStaged = computePaletteDelta(stagedPaletteStops, target) <= EPSILON * 0.05f
                && target.size() == stagedPaletteStops.size()
                && target.equals(stagedPaletteStops);
        if (matchesStaged && hasPendingPalette) {
            return;
        }

        stagedPaletteStops = target;
        hasPendingPalette = true;
    }

    private void commitPendingPaletteIfReady(int level, int updateInterval, long currentTime) {
        if (!hasPendingPalette) {
            return;
        }
        if (!paletteCommittedOnce || currentTime < lastPaletteCommitTime) {
            applyPendingPalette(currentTime);
            return;
        }

        int breathingCycle = getBreathingCycleLength(level);
        if (breathingCycle <= 0) {
            applyPendingPalette(currentTime);
            return;
        }

        long elapsed = currentTime - lastPaletteCommitTime;
        if (elapsed < 0) {
            applyPendingPalette(currentTime);
            return;
        }

        long animationTime = updateInterval > 0 ? (currentTime / updateInterval) * updateInterval : currentTime;
        long phase = breathingCycle > 0 ? animationTime % breathingCycle : 0L;
        boolean atCycleBoundary = phase < updateInterval;

        if ((elapsed >= breathingCycle && atCycleBoundary) || elapsed >= (long) breathingCycle * 3L) {
            applyPendingPalette(currentTime);
        }
    }

    private void applyPendingPalette(long currentTime) {
        currentPaletteStops = stagedPaletteStops;
        hasPendingPalette = false;
        paletteGeneration++;
        paletteCommittedOnce = true;
        lastPaletteCommitTime = currentTime;
    }

    private void updateAnimationIntensity(List<Candidate> weighted) {
        float total = 0f;
        float accum = 0f;
        for (Candidate candidate : weighted) {
            float weight = Math.max(0f, candidate.signal);
            if (weight <= EPSILON) {
                continue;
            }
            total += weight;
            accum += weight * MathHelper.clamp(candidate.record.intensity, 0f, 1f);
        }
        float target = total > EPSILON ? accum / total : 0f;
        target = MathHelper.clamp(target, 0f, 1f);
        animationIntensity = MathHelper.lerp(0.35f, animationIntensity, target);
    }

    private int getBreathingCycleLength(int level) {
        return computeBreathingDuration(level, BASE_BREATHING_SPEEDS);
    }

    private int computeBreathingDuration(int level, int[] baseDurations) {
        if (level <= 0 || baseDurations.length == 0) {
            return 0;
        }
        int index = Math.min(level - 1, baseDurations.length - 1);
        int base = baseDurations[index];
        float intensity = MathHelper.clamp(animationIntensity, 0f, 1f);
        float minScale;
        if (index == 0) {
            minScale = 0.8f;
        } else if (index == 1) {
            minScale = 0.66f;
        } else {
            minScale = 0.55f;
        }
        float scale = MathHelper.lerp(intensity, 1f, minScale);
        int scaled = Math.round(base * scale);
        return Math.max(40, scaled);
    }

    private void applyOpponentTransfers(List<Candidate> weighted, float weightCap) {
        if (weighted.size() < 2) {
            return;
        }

        Map<PetComponent.Emotion, Candidate> lookup = weighted.stream()
                .collect(Collectors.toMap(c -> c.record.emotion, c -> c));

        List<OpponentConflict> conflicts = new ArrayList<>();
        for (Candidate candidate : weighted) {
            EnumSet<PetComponent.Emotion> opponents = opponentPairs.get(candidate.record.emotion);
            if (opponents == null || opponents.isEmpty()) continue;
            for (PetComponent.Emotion opponent : opponents) {
                if (!lookup.containsKey(opponent)) continue;
                if (candidate.record.emotion.ordinal() >= opponent.ordinal()) continue;
                Candidate other = lookup.get(opponent);
                float combined = candidate.signal + other.signal;
                conflicts.add(new OpponentConflict(candidate, other, combined));
            }
        }

        conflicts.sort(Comparator.comparingDouble((OpponentConflict c) -> c.combinedWeight).reversed());
        for (OpponentConflict conflict : conflicts) {
            Candidate first = conflict.a;
            Candidate second = conflict.b;
            float difference = Math.abs(first.signal - second.signal);
            if (difference <= EPSILON) {
                continue;
            }

            Candidate donor;
            Candidate receiver;
            if (first.signal <= second.signal) {
                donor = first;
                receiver = second;
            } else {
                donor = second;
                receiver = first;
            }

            float transferFactor = Math.min(OPPONENT_TRANSFER_MAX, 0.15f + 0.1f * difference);
            // Validate transferFactor is in reasonable range
            transferFactor = MathHelper.clamp(transferFactor, 0.0f, 1.0f);
            float transfer = donor.signal * transferFactor;
            if (transfer <= EPSILON) continue;

            donor.signal = Math.max(0f, donor.signal - transfer);
            float rebound = transfer * (0.2f + REBOUND_GAIN * (first.record.appraisalConfidence + second.record.appraisalConfidence) * 0.5f);
            receiver.signal = Math.min(weightCap, receiver.signal + transfer * 0.85f);
            donor.signal = Math.max(0f, donor.signal + rebound * 0.2f);
        }
    }

    private float computeFreshness(EmotionRecord record, long now) {
        if (record.lastEventTime <= 0L) {
            return 0f;
        }
        float cadence = record.cadenceEMA > 0f ? record.cadenceEMA : HABITUATION_BASE;
        float age = Math.max(0f, now - record.lastEventTime);
        return (float) Math.exp(-age / Math.max(1f, cadence));
    }

    private void refreshContextGuards(EmotionRecord record, long now, long delta) {
        // Relationship guard uses bond strength and recent care pulse.
        long bond = parent.getBondStrength();
        float guardModifier = parent.getNatureGuardModifier();
        double bondZ = (bond - 2000.0) / RELATIONSHIP_VARIANCE;
        float bondMultiplier = (float) MathHelper.clamp(1.0 + bondZ * 0.35, 0.75, 1.25);
        Long lastPet = parent.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class);
        float carePulse = 0f;
        if (lastPet != null && lastPet > 0) {
            float age = Math.max(0f, now - lastPet);
            carePulse = (float) Math.exp(-age / CARE_PULSE_HALF_LIFE);
        }
        float careMultiplier = 0.85f + 0.3f * carePulse;
        float relationshipTarget = MathHelper.clamp(bondMultiplier * careMultiplier, 0.75f, 1.25f);
        float guardBias = getNatureGuardBias(record.emotion);
        float tunedRelationship = MathHelper.clamp(relationshipTarget * guardModifier * guardBias, 0.6f, 1.6f);
        record.relationshipGuard = MathHelper.lerp(0.25f,
            record.relationshipGuard <= 0f ? RELATIONSHIP_BASE : record.relationshipGuard,
            tunedRelationship);

        // Danger guard from stored threat telemetry
        long lastDangerTick = parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class, Long.MIN_VALUE);
        int dangerStreak = parent.getStateData(PetComponent.StateKeys.THREAT_SENSITIZED_STREAK, Integer.class, 0);
        float dangerMultiplier = 1.0f;
        if (lastDangerTick > Long.MIN_VALUE && lastDangerTick <= now) {
            float dangerAge = Math.max(0f, now - lastDangerTick);
            float streakBias = Math.min(0.35f, dangerStreak * 0.05f);
            float decay = (float) Math.exp(-dangerAge / DANGER_HALF_LIFE);
            dangerMultiplier = 0.85f + streakBias + 0.2f * decay;
        }
        float dangerTarget = MathHelper.clamp(dangerMultiplier, 0.75f, 1.35f);
        float tunedDanger = MathHelper.clamp(dangerTarget * guardModifier * guardBias, 0.6f, 1.7f);
        record.dangerWindow = MathHelper.lerp(0.25f,
            record.dangerWindow <= 0f ? DANGER_BASE : record.dangerWindow,
            tunedDanger);

        // Appraisal confidence trends toward baseline with volatility awareness
        float volatility = record.volatilityEMA;
        float volatilityPenalty = MathHelper.clamp(volatility * 0.6f, 0f, 0.35f);
        float confidenceTarget = MathHelper.clamp(APPRAISAL_BASE - volatilityPenalty + 0.1f * (record.relationshipGuard - 1f), 0.4f, 0.95f);
        record.appraisalConfidence = MathHelper.lerp(0.1f, record.appraisalConfidence <= 0f ? APPRAISAL_BASE : record.appraisalConfidence, confidenceTarget);

        // Contagion share decays naturally
        float decayStretch = getNatureQuirkContagionDecayModifier(record.emotion);
        float contagionDecay = (float) Math.exp(-delta / Math.max(60f, 240f * decayStretch));
        record.contagionShare *= contagionDecay;

        lastRelationshipGuardObserved = Math.max(lastRelationshipGuardObserved, record.relationshipGuard);
        lastDangerWindowObserved = Math.max(lastDangerWindowObserved, record.dangerWindow);
    }

    private float selectQuantile(float[] data, int length, float quantile, float fallback) {
        if (length <= 0) {
            return fallback;
        }
        int targetIndex = (int) MathHelper.clamp(Math.round((length - 1) * quantile), 0, length - 1);
        int left = 0;
        int right = length - 1;
        while (left < right) {
            int pivotIndex = partition(data, left, right, left + (right - left) / 2);
            if (targetIndex == pivotIndex) {
                return data[targetIndex];
            }
            if (targetIndex < pivotIndex) {
                right = pivotIndex - 1;
            } else {
                left = pivotIndex + 1;
            }
        }
        return data[left];
    }

    private int partition(float[] data, int left, int right, int pivotIndex) {
        float pivotValue = data[pivotIndex];
        swap(data, pivotIndex, right);
        int storeIndex = left;
        for (int i = left; i < right; i++) {
            if (data[i] < pivotValue) {
                swap(data, storeIndex, i);
                storeIndex++;
            }
        }
        swap(data, right, storeIndex);
        return storeIndex;
    }

    private void swap(float[] data, int i, int j) {
        if (i == j) {
            return;
        }
        float temp = data[i];
        data[i] = data[j];
        data[j] = temp;
    }

    private float[] ensureCapacity(float[] data, int required) {
        if (required <= data.length) {
            return data;
        }
        int newSize = data.length > 0 ? data.length : 1;
        while (newSize < required) {
            newSize <<= 1;
            if (newSize <= 0) {
                newSize = required;
                break;
            }
        }
        if (newSize < required) {
            newSize = required;
        }
        return Arrays.copyOf(data, newSize);
    }

    private float smoothstep(float edge0, float edge1, float x) {
        if (Math.abs(edge0 - edge1) < EPSILON) {
            return x < edge0 ? 0f : 1f;
        }
        float t = MathHelper.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private float computeImpactCap(List<EmotionRecord> active) {
        scratchBudgets = ensureCapacity(scratchBudgets, active.size());
        scratchBudgetCount = active.size();
        for (int i = 0; i < scratchBudgetCount; i++) {
            scratchBudgets[i] = active.get(i).impactBudget;
        }
        float dynamic = selectQuantile(scratchBudgets, scratchBudgetCount, 0.95f, DEFAULT_IMPACT_CAP);
        return Math.max(DEFAULT_IMPACT_CAP, dynamic);
    }

    private float getImpactCap() {
        return computeImpactCap(new ArrayList<>(emotionRecords.values()));
    }

    private float computeContagionCap(float bondFactor) {
        float impactCap = getImpactCap();
        float contagionModifier = parent.getNatureContagionModifier();
        float scaled = impactCap * 0.1f * bondFactor * contagionModifier;
        float min = 0.05f * Math.min(1f, contagionModifier);
        float max = 0.6f * Math.max(1f, contagionModifier);
        return MathHelper.clamp(scaled, min, max);
    }

    private float[] getLevelThresholds() {
        ensureConfigCache();
        return cachedLevelThresholds;
    }

    private float getNatureStimulusBias(PetComponent.Emotion emotion) {
        float bias = getProfileScale(emotion, 0.55f, 0.35f, 0f, 0.65f, 1.75f);
        return MathHelper.clamp(bias, 0.0f, 2.0f);
    }

    private float getNatureWeightBias(PetComponent.Emotion emotion) {
        float bias = getProfileScale(emotion, 0.5f, 0.3f, 0.15f, 0.6f, 1.65f);
        return MathHelper.clamp(bias, 0.0f, 2.0f);
    }

    private float getNatureContagionSpreadBias(PetComponent.Emotion emotion) {
        float bias = getProfileScale(emotion, 0.45f, 0.25f, 0.2f, 0.6f, 1.6f);
        return MathHelper.clamp(bias, 0.0f, 2.0f);
    }

    private float getNatureGuardBias(PetComponent.Emotion emotion) {
        float bias = getProfileScale(emotion, 0.25f, 0.15f, 0.1f, 0.7f, 1.4f);
        return MathHelper.clamp(bias, 0.0f, 2.0f);
    }

    private float getNatureQuirkReboundModifier(PetComponent.Emotion emotion) {
        float modifier = getProfileScale(emotion, 0f, 0f, -0.45f, 0.55f, 1.1f);
        return MathHelper.clamp(modifier, -1.0f, 2.0f);
    }

    private float getNatureQuirkContagionDecayModifier(PetComponent.Emotion emotion) {
        float modifier = getProfileScale(emotion, 0f, 0f, 0.6f, 0.6f, 1.6f);
        return MathHelper.clamp(modifier, 0.1f, 3.0f); // Ensure positive decay modifier
    }

    /**
     * Check if the condition that triggers this emotion is still present.
     * Used for condition-aware decay and persistence bonuses.
     */
    private boolean hasOngoingCondition(PetComponent.Emotion emotion, long now) {
        switch (emotion) {
            case SAUDADE:
            case HIRAETH:
                // Owner absence: check if owner is far or hasn't interacted recently
                Long lastPetTime = parent.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class);
                if (lastPetTime == null) return false;
                return (now - lastPetTime) > 600; // >30 seconds without petting

            case FOREBODING:
            case STARTLE:
            case ANGST:
                // Danger: check if danger occurred recently
                long lastDanger = parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class, Long.MIN_VALUE);
                if (lastDanger == Long.MIN_VALUE) return false;
                return (now - lastDanger) < DANGER_HALF_LIFE; // Within danger window

            case PROTECTIVENESS:
            case PROTECTIVE:
                // Owner in danger or low health - simplified check
                // Would need to check owner entity if available in parent
                return false; // TODO: Implement owner health check if API available

            case BLISSFUL:
            case GLEE:
            case CHEERFUL:
                // Positive stimuli: check recent petting or play
                Long recentPet = parent.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class);
                if (recentPet == null) return false;
                return (now - recentPet) < 200; // <10 seconds since petting

            default:
                // Most emotions don't have persistent conditions
                return false;
        }
    }

    /**
     * Compute emotion-specific context modulation based on bond, danger, health, etc.
     * Replaces the old uniform multiplication with targeted adjustments.
     */
    private float computeEmotionSpecificContextModulation(EmotionRecord record, long now) {
        float modulation = 0f;
        PetComponent.Emotion emotion = record.emotion;

        // Bond strength effects (high bond amplifies attachment emotions)
        float bondStrength = parent.getBondStrength();
        float bondFactor = MathHelper.clamp(bondStrength / RELATIONSHIP_VARIANCE, 0f, 2f);
        // Validate bondFactor is in reasonable range
        bondFactor = MathHelper.clamp(bondFactor, 0.0f, 3.0f);

        switch (emotion) {
            case SAUDADE:
            case HIRAETH:
            case REGRET:
                // High bond amplifies longing/regret
                modulation += 0.3f * (bondFactor - 1.0f);
                break;

            case UBUNTU:
            case QUERECIA:
            case PACK_SPIRIT:
                // High bond amplifies connection emotions and pack unity
                modulation += 0.25f * (bondFactor - 1.0f);
                break;

            case RELIEF:
                // Relief stronger when bond is high (you care more)
                modulation += 0.2f * (bondFactor - 1.0f);
                break;
                
            case ECHOED_RESONANCE:
                // Echoed Resonance strengthened by deep bonds forged through danger
                modulation += 0.35f * (bondFactor - 1.0f);
                break;
        }

        // Danger window effects
        float dangerRecency = record.dangerWindow; // Already calculated in refreshContextGuards
        float dangerBoost = MathHelper.clamp((dangerRecency - 1.0f), -0.5f, 0.8f);
        // Validate dangerBoost is in reasonable range
        dangerBoost = MathHelper.clamp(dangerBoost, -1.0f, 1.0f);

        switch (emotion) {
            case FOREBODING:
            case STARTLE:
            case ANGST:
                // Danger amplifies fear emotions
                modulation += 0.4f * Math.max(0f, dangerBoost);
                break;

            case PROTECTIVENESS:
            case PACK_SPIRIT:
                // Danger increases protectiveness and pack unity
                modulation += 0.35f * Math.max(0f, dangerBoost);
                break;
                
            case ECHOED_RESONANCE:
                // Echoed Resonance is born from danger - strongly amplified
                modulation += 0.5f * Math.max(0f, dangerBoost);
                break;

            case BLISSFUL:
            case LAGOM:
            case CONTENT:
                // Danger suppresses calm/peaceful emotions
                modulation -= 0.3f * Math.max(0f, dangerBoost);
                break;
                
            case ARCANE_OVERFLOW:
                // Arcane Overflow unaffected by mundane danger
                break;
                
            default:
                // Other emotions not significantly affected by danger
                break;
        }

        // Health effects
        float healthRatio = parent.getPet().getHealth() / parent.getPet().getMaxHealth();
        // Validate healthRatio is in reasonable range
        healthRatio = MathHelper.clamp(healthRatio, 0.0f, 1.0f);
        if (healthRatio < 0.4f) {
            float healthPenalty = (0.4f - healthRatio) / 0.4f; // 0 at 40% health, 1 at 0% health
            // Validate healthPenalty is in reasonable range
            healthPenalty = MathHelper.clamp(healthPenalty, 0.0f, 1.0f);

            switch (emotion) {
                case ANGST:
                case FOREBODING:
                    // Low health amplifies distress
                    modulation += 0.3f * healthPenalty;
                    break;

                case GLEE:
                case KEFI:
                case PLAYFULNESS:
                    // Low health suppresses energetic emotions
                    modulation -= 0.4f * healthPenalty;
                    break;
                    
                case ARCANE_OVERFLOW:
                    // Arcane overflow persists regardless of physical state
                    break;
                    
                case ECHOED_RESONANCE:
                case PACK_SPIRIT:
                    // These ultra-rare states resist health penalties
                    modulation += 0.2f * healthPenalty; // Slight boost when wounded but standing
                    break;
                    
                default:
                    // Other emotions not significantly affected by low health
                    break;
            }
        }

        return modulation;
    }

    private float getProfileScale(PetComponent.Emotion emotion,
                                  float majorScale,
                                  float minorScale,
                                  float quirkScale,
                                  float min,
                                  float max) {
        if (natureEmotionProfile == null || natureEmotionProfile.isEmpty() || emotion == null) {
            return 1f;
        }
        float factor = 1f;
        if (emotion == natureEmotionProfile.majorEmotion()) {
            factor += natureEmotionProfile.majorStrength() * majorScale;
        } else if (emotion == natureEmotionProfile.minorEmotion()) {
            factor += natureEmotionProfile.minorStrength() * minorScale;
        } else if (emotion == natureEmotionProfile.quirkEmotion()) {
            factor += natureEmotionProfile.quirkStrength() * quirkScale;
        }
        factor = MathHelper.clamp(factor, min, max);
        // Additional validation to ensure factor is in reasonable range
        return MathHelper.clamp(factor, 0.0f, 3.0f);
    }

    private Map<PetComponent.Mood, Float> getEmotionToMoodWeights(PetComponent.Emotion emotion) {
        ensureConfigCache();
        return resolveEmotionToMoodWeights(cachedMoodsSection, emotion);
    }

    private void ensureConfigCache() {
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        int generation = config.getConfigGeneration();
        if (generation == cachedConfigGeneration && cachedMoodsSection != null) {
            return;
        }
        cachedConfigGeneration = generation;
        cachedMoodsSection = config.getSection("moods");
        cachedWeightSection = getObject(cachedMoodsSection, "weight");
        cachedOpponentSection = getObject(cachedMoodsSection, "opponents");
        cachedAnimationSection = getObject(cachedMoodsSection, "animation");
        cachedMomentum = RegistryJsonHelper.getDouble(cachedMoodsSection, "momentum", 0.35d);
        cachedSwitchMargin = RegistryJsonHelper.getDouble(cachedMoodsSection, "switchMargin", 0.05d);
        cachedHysteresisTicks = RegistryJsonHelper.getInt(cachedMoodsSection, "hysteresisTicks", 60);
        cachedEpsilon = RegistryJsonHelper.getFloat(cachedMoodsSection, "epsilon", 0.05f);
        cachedLevelThresholds = parseLevelThresholds(cachedMoodsSection);
        cachedBaseAnimationUpdateInterval = RegistryJsonHelper.getInt(cachedAnimationSection, "baseAnimationUpdateInterval", 16);
        cachedAnimationSpeedMultiplier = RegistryJsonHelper.getDouble(cachedAnimationSection, "animationSpeedMultiplier", 0.15d);
        cachedMinAnimationInterval = RegistryJsonHelper.getInt(cachedAnimationSection, "minAnimationInterval", 4);
        cachedMaxAnimationInterval = RegistryJsonHelper.getInt(cachedAnimationSection, "maxAnimationInterval", 40);
        rebuildOpponentPairs();
    }

    private float[] parseLevelThresholds(JsonObject section) {
        if (section == null || !section.has("levelThresholds")) {
            return new float[]{0.35f, 0.65f, 0.88f};
        }
        JsonElement array = section.get("levelThresholds");
        if (!array.isJsonArray()) {
            return new float[]{0.35f, 0.65f, 0.88f};
        }
        List<Float> values = new ArrayList<>();
        array.getAsJsonArray().forEach(el -> {
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                values.add(el.getAsFloat());
            }
        });
        if (values.isEmpty()) {
            return new float[]{0.35f, 0.65f, 0.88f};
        }
        float[] out = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    static EnumMap<PetComponent.Mood, Float> resolveEmotionToMoodWeights(@Nullable JsonObject moodsSection,
                                                                         PetComponent.Emotion emotion) {
        EnumMap<PetComponent.Mood, Float> weights = copyDefaultEmotionToMoodWeights(emotion);
        JsonObject section = getObject(moodsSection, "emotionToMoodWeights");
        if (section == null) {
            return weights;
        }
        JsonObject emotionObj = getObject(section, emotion.name().toLowerCase());
        if (emotionObj == null) {
            return weights;
        }
        boolean sawOverride = false;
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            float override = RegistryJsonHelper.getFloat(emotionObj, mood.name().toLowerCase(), Float.NaN);
            if (!Float.isNaN(override)) {
                // Validate override values are in reasonable range
                override = MathHelper.clamp(override, 0.0f, 10.0f);
                weights.put(mood, Math.max(0f, override));
                sawOverride = true;
            }
        }
        if (!sawOverride) {
            return weights;
        }
        float total = 0f;
        for (float value : weights.values()) {
            total += Math.max(0f, value);
        }
        if (total <= 0f) {
            return copyDefaultEmotionToMoodWeights(emotion);
        }
        for (Map.Entry<PetComponent.Mood, Float> entry : weights.entrySet()) {
            entry.setValue(Math.max(0f, entry.getValue()) / total);
        }
        return weights;
    }

    static Map<PetComponent.Mood, Float> getAuthoredEmotionToMoodDefaults(PetComponent.Emotion emotion) {
        return copyDefaultEmotionToMoodWeights(emotion);
    }

    private static EnumMap<PetComponent.Mood, Float> copyDefaultEmotionToMoodWeights(PetComponent.Emotion emotion) {
        EnumMap<PetComponent.Mood, Float> defaults = DEFAULT_EMOTION_TO_MOOD.get(emotion);
        if (defaults == null) {
            EnumMap<PetComponent.Mood, Float> uniform = new EnumMap<>(PetComponent.Mood.class);
            float base = 1f / PetComponent.Mood.values().length;
            for (PetComponent.Mood mood : PetComponent.Mood.values()) {
                uniform.put(mood, base);
            }
            return uniform;
        }
        return new EnumMap<>(defaults);
    }

    private static EnumMap<PetComponent.Emotion, EnumMap<PetComponent.Mood, Float>> buildDefaultEmotionToMoodTable() {
        EnumMap<PetComponent.Emotion, EnumMap<PetComponent.Mood, Float>> table =
                new EnumMap<>(PetComponent.Emotion.class);

        table.put(PetComponent.Emotion.CHEERFUL, weights(
                Map.entry(PetComponent.Mood.HAPPY, 0.6f),
                Map.entry(PetComponent.Mood.PLAYFUL, 0.25f),
                Map.entry(PetComponent.Mood.CURIOUS, 0.15f)));
        table.put(PetComponent.Emotion.QUERECIA, weights(
                Map.entry(PetComponent.Mood.HAPPY, 0.35f),
                Map.entry(PetComponent.Mood.BONDED, 0.4f),
                Map.entry(PetComponent.Mood.CALM, 0.25f)));
        table.put(PetComponent.Emotion.GLEE, weights(
                Map.entry(PetComponent.Mood.HAPPY, 0.65f),
                Map.entry(PetComponent.Mood.PLAYFUL, 0.2f),
                Map.entry(PetComponent.Mood.CURIOUS, 0.15f)));
        table.put(PetComponent.Emotion.BLISSFUL, weights(
                Map.entry(PetComponent.Mood.HAPPY, 0.4f),
                Map.entry(PetComponent.Mood.CALM, 0.35f),
                Map.entry(PetComponent.Mood.BONDED, 0.25f)));
        table.put(PetComponent.Emotion.UBUNTU, weights(
                Map.entry(PetComponent.Mood.BONDED, 0.6f),
                Map.entry(PetComponent.Mood.HAPPY, 0.2f),
                Map.entry(PetComponent.Mood.PROTECTIVE, 0.2f)));
        table.put(PetComponent.Emotion.KEFI, weights(
                Map.entry(PetComponent.Mood.PLAYFUL, 0.45f),
                Map.entry(PetComponent.Mood.PASSIONATE, 0.30f),
                Map.entry(PetComponent.Mood.HAPPY, 0.25f)));
        table.put(PetComponent.Emotion.ANGST, weights(
                Map.entry(PetComponent.Mood.RESTLESS, 0.35f),
                Map.entry(PetComponent.Mood.AFRAID, 0.35f),
                Map.entry(PetComponent.Mood.SAUDADE, 0.3f)));
        table.put(PetComponent.Emotion.FOREBODING, weights(
                Map.entry(PetComponent.Mood.AFRAID, 0.55f),
                Map.entry(PetComponent.Mood.RESTLESS, 0.25f),
                Map.entry(PetComponent.Mood.PROTECTIVE, 0.20f)));
        table.put(PetComponent.Emotion.PROTECTIVENESS, weights(
                Map.entry(PetComponent.Mood.PROTECTIVE, 0.7f),
                Map.entry(PetComponent.Mood.ANGRY, 0.15f),
                Map.entry(PetComponent.Mood.BONDED, 0.15f)));
        table.put(PetComponent.Emotion.FRUSTRATION, weights(
                Map.entry(PetComponent.Mood.ANGRY, 0.5f),
                Map.entry(PetComponent.Mood.RESTLESS, 0.3f),
                Map.entry(PetComponent.Mood.PASSIONATE, 0.2f)));
        table.put(PetComponent.Emotion.STARTLE, weights(
                Map.entry(PetComponent.Mood.AFRAID, 0.6f),
                Map.entry(PetComponent.Mood.RESTLESS, 0.3f),
                Map.entry(PetComponent.Mood.ANGRY, 0.1f)));
        table.put(PetComponent.Emotion.DISGUST, weights(
                Map.entry(PetComponent.Mood.ANGRY, 0.5f),
                Map.entry(PetComponent.Mood.AFRAID, 0.3f),
                Map.entry(PetComponent.Mood.RESTLESS, 0.2f)));
        table.put(PetComponent.Emotion.REGRET, weights(
                Map.entry(PetComponent.Mood.SAUDADE, 0.55f),
                Map.entry(PetComponent.Mood.CALM, 0.20f),
                Map.entry(PetComponent.Mood.RESTLESS, 0.25f)));
        table.put(PetComponent.Emotion.MONO_NO_AWARE, weights(
                Map.entry(PetComponent.Mood.YUGEN, 0.5f),
                Map.entry(PetComponent.Mood.SAUDADE, 0.3f),
                Map.entry(PetComponent.Mood.CALM, 0.2f)));
        table.put(PetComponent.Emotion.FERNWEH, weights(
                Map.entry(PetComponent.Mood.SAUDADE, 0.5f),
                Map.entry(PetComponent.Mood.RESTLESS, 0.3f),
                Map.entry(PetComponent.Mood.CURIOUS, 0.2f)));
        table.put(PetComponent.Emotion.SOBREMESA, weights(
                Map.entry(PetComponent.Mood.BONDED, 0.50f),
                Map.entry(PetComponent.Mood.CALM, 0.30f),
                Map.entry(PetComponent.Mood.HAPPY, 0.20f)));
        table.put(PetComponent.Emotion.HANYAUKU, weights(
                Map.entry(PetComponent.Mood.PLAYFUL, 0.5f),
                Map.entry(PetComponent.Mood.HAPPY, 0.3f),
                Map.entry(PetComponent.Mood.RESTLESS, 0.2f)));
        table.put(PetComponent.Emotion.WABI_SABI, weights(
                Map.entry(PetComponent.Mood.CALM, 0.4f),
                Map.entry(PetComponent.Mood.YUGEN, 0.35f),
                Map.entry(PetComponent.Mood.SAUDADE, 0.25f)));
        table.put(PetComponent.Emotion.LAGOM, weights(
                Map.entry(PetComponent.Mood.CALM, 0.50f),
                Map.entry(PetComponent.Mood.BONDED, 0.25f),
                Map.entry(PetComponent.Mood.HAPPY, 0.25f)));
        table.put(PetComponent.Emotion.ENNUI, weights(
                Map.entry(PetComponent.Mood.RESTLESS, 0.50f),
                Map.entry(PetComponent.Mood.SAUDADE, 0.30f),
                Map.entry(PetComponent.Mood.CALM, 0.20f)));
        table.put(PetComponent.Emotion.YUGEN, weights(
                Map.entry(PetComponent.Mood.YUGEN, 0.35f),
                Map.entry(PetComponent.Mood.CALM, 0.40f),
                Map.entry(PetComponent.Mood.SAUDADE, 0.25f)));
        table.put(PetComponent.Emotion.SAUDADE, weights(
                Map.entry(PetComponent.Mood.SAUDADE, 0.7f),
                Map.entry(PetComponent.Mood.YUGEN, 0.15f),
                Map.entry(PetComponent.Mood.CALM, 0.15f)));
        table.put(PetComponent.Emotion.HIRAETH, weights(
                Map.entry(PetComponent.Mood.SAUDADE, 0.60f),
                Map.entry(PetComponent.Mood.RESTLESS, 0.30f),
                Map.entry(PetComponent.Mood.CALM, 0.10f)));
        table.put(PetComponent.Emotion.STOIC, weights(
                Map.entry(PetComponent.Mood.SISU, 0.4f),
                Map.entry(PetComponent.Mood.CALM, 0.35f),
                Map.entry(PetComponent.Mood.FOCUSED, 0.25f)));
        table.put(PetComponent.Emotion.HOPEFUL, weights(
                Map.entry(PetComponent.Mood.CURIOUS, 0.3f),
                Map.entry(PetComponent.Mood.HAPPY, 0.25f),
                Map.entry(PetComponent.Mood.FOCUSED, 0.25f),
                Map.entry(PetComponent.Mood.PASSIONATE, 0.2f)));
        table.put(PetComponent.Emotion.RELIEF, weights(
                Map.entry(PetComponent.Mood.CALM, 0.45f),
                Map.entry(PetComponent.Mood.HAPPY, 0.3f),
                Map.entry(PetComponent.Mood.BONDED, 0.25f)));
        table.put(PetComponent.Emotion.GAMAN, weights(
                Map.entry(PetComponent.Mood.SISU, 0.6f),
                Map.entry(PetComponent.Mood.FOCUSED, 0.25f),
                Map.entry(PetComponent.Mood.CALM, 0.15f)));
        table.put(PetComponent.Emotion.CURIOUS, weights(
                Map.entry(PetComponent.Mood.CURIOUS, 0.6f),
                Map.entry(PetComponent.Mood.PLAYFUL, 0.25f),
                Map.entry(PetComponent.Mood.FOCUSED, 0.15f)));
        table.put(PetComponent.Emotion.SISU, weights(
                Map.entry(PetComponent.Mood.SISU, 0.65f),
                Map.entry(PetComponent.Mood.FOCUSED, 0.2f),
                Map.entry(PetComponent.Mood.PASSIONATE, 0.15f)));
        table.put(PetComponent.Emotion.FOCUSED, weights(
                Map.entry(PetComponent.Mood.FOCUSED, 0.65f),
                Map.entry(PetComponent.Mood.SISU, 0.2f),
                Map.entry(PetComponent.Mood.CURIOUS, 0.15f)));
        table.put(PetComponent.Emotion.PRIDE, weights(
                Map.entry(PetComponent.Mood.PASSIONATE, 0.4f),
                Map.entry(PetComponent.Mood.HAPPY, 0.2f),
                Map.entry(PetComponent.Mood.BONDED, 0.2f),
                Map.entry(PetComponent.Mood.FOCUSED, 0.2f)));
        table.put(PetComponent.Emotion.VIGILANT, weights(
                Map.entry(PetComponent.Mood.PROTECTIVE, 0.4f),
                Map.entry(PetComponent.Mood.AFRAID, 0.3f),
                Map.entry(PetComponent.Mood.RESTLESS, 0.3f)));
        table.put(PetComponent.Emotion.WORRIED, weights(
                Map.entry(PetComponent.Mood.AFRAID, 0.5f),
                Map.entry(PetComponent.Mood.RESTLESS, 0.3f),
                Map.entry(PetComponent.Mood.PROTECTIVE, 0.2f)));
        table.put(PetComponent.Emotion.PROTECTIVE, weights(
                Map.entry(PetComponent.Mood.PROTECTIVE, 0.65f),
                Map.entry(PetComponent.Mood.AFRAID, 0.2f),
                Map.entry(PetComponent.Mood.BONDED, 0.15f)));
        table.put(PetComponent.Emotion.MELANCHOLY, weights(
                Map.entry(PetComponent.Mood.SAUDADE, 0.55f),
                Map.entry(PetComponent.Mood.CALM, 0.25f),
                Map.entry(PetComponent.Mood.RESTLESS, 0.2f)));
        table.put(PetComponent.Emotion.CONTENT, weights(
                Map.entry(PetComponent.Mood.CALM, 0.45f),
                Map.entry(PetComponent.Mood.HAPPY, 0.3f),
                Map.entry(PetComponent.Mood.BONDED, 0.25f)));
        table.put(PetComponent.Emotion.RESTLESS, weights(
                Map.entry(PetComponent.Mood.RESTLESS, 0.6f),
                Map.entry(PetComponent.Mood.CURIOUS, 0.2f),
                Map.entry(PetComponent.Mood.AFRAID, 0.2f)));
        table.put(PetComponent.Emotion.EMPATHY, weights(
                Map.entry(PetComponent.Mood.BONDED, 0.5f),
                Map.entry(PetComponent.Mood.PROTECTIVE, 0.25f),
                Map.entry(PetComponent.Mood.HAPPY, 0.25f)));
        table.put(PetComponent.Emotion.NOSTALGIA, weights(
                Map.entry(PetComponent.Mood.SAUDADE, 0.6f),
                Map.entry(PetComponent.Mood.BONDED, 0.2f),
                Map.entry(PetComponent.Mood.HAPPY, 0.2f)));
        table.put(PetComponent.Emotion.PLAYFULNESS, weights(
                Map.entry(PetComponent.Mood.PLAYFUL, 0.65f),
                Map.entry(PetComponent.Mood.HAPPY, 0.25f),
                Map.entry(PetComponent.Mood.CURIOUS, 0.1f)));
        table.put(PetComponent.Emotion.LOYALTY, weights(
                Map.entry(PetComponent.Mood.BONDED, 0.5f),
                Map.entry(PetComponent.Mood.PROTECTIVE, 0.3f),
                Map.entry(PetComponent.Mood.CALM, 0.2f)));
        
        // Ultra-rare emotion mappings
        table.put(PetComponent.Emotion.ECHOED_RESONANCE, weights(
                Map.entry(PetComponent.Mood.ECHOED_RESONANCE, 0.70f),
                Map.entry(PetComponent.Mood.SISU, 0.20f),
                Map.entry(PetComponent.Mood.PROTECTIVE, 0.10f)));
        table.put(PetComponent.Emotion.ARCANE_OVERFLOW, weights(
                Map.entry(PetComponent.Mood.ARCANE_OVERFLOW, 0.75f),
                Map.entry(PetComponent.Mood.PASSIONATE, 0.15f),
                Map.entry(PetComponent.Mood.CURIOUS, 0.10f)));
        table.put(PetComponent.Emotion.PACK_SPIRIT, weights(
                Map.entry(PetComponent.Mood.PACK_SPIRIT, 0.70f),
                Map.entry(PetComponent.Mood.BONDED, 0.20f),
                Map.entry(PetComponent.Mood.PROTECTIVE, 0.10f)));

        return table;
    }

    @SafeVarargs
    private static EnumMap<PetComponent.Mood, Float> weights(Map.Entry<PetComponent.Mood, Float>... entries) {
        EnumMap<PetComponent.Mood, Float> map = new EnumMap<>(PetComponent.Mood.class);
        float total = 0f;
        for (Map.Entry<PetComponent.Mood, Float> entry : entries) {
            float value = Math.max(0f, entry.getValue());
            map.put(entry.getKey(), value);
            total += value;
        }
        if (total > 0f) {
            for (Map.Entry<PetComponent.Mood, Float> entry : map.entrySet()) {
                entry.setValue(entry.getValue() / total);
            }
        }
        return map;
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        return RegistryJsonHelper.getObject(parent, key);
    }

    // --------------------------------------------------------------------------------------------
    // Text rendering helpers (breathing/animation reused from previous implementation)
    // --------------------------------------------------------------------------------------------

    private Text getCachedMoodText(boolean withDebug) {
        long currentTime = parent.getPet().getWorld().getTime();
        int level = getMoodLevel();
        int updateInterval = getAnimationUpdateInterval(level);
        commitPendingPaletteIfReady(level, updateInterval, currentTime);
        boolean levelChanged = (cachedMoodText != null || cachedMoodTextWithDebug != null)
                && !isCurrentLevelMatchingCache(level);
        boolean paletteChanged = lastRenderedPaletteGeneration != paletteGeneration;
        boolean needsUpdate = cachedMoodText == null
                || cachedMoodTextWithDebug == null
                || levelChanged
                || paletteChanged
                || (currentTime - lastTextUpdateTime) >= updateInterval;
        if (needsUpdate) {
            lastTextUpdateTime = currentTime;
            cachedMoodText = generateMoodText(false, level);
            cachedMoodTextWithDebug = generateMoodText(true, level);
            cachedLastLevel = level;
            lastRenderedPaletteGeneration = paletteGeneration;
        }
        return withDebug ? cachedMoodTextWithDebug : cachedMoodText;
    }

    private boolean isCurrentLevelMatchingCache(int level) {
        return cachedLastLevel == level;
    }

    private Text generateMoodText(boolean withDebug, int level) {
        PetComponent.Mood mood = getCurrentMood();
        boolean showEmotion = getMoodsBoolean("showDominantEmotion", false);
        String label = showEmotion ? getDominantEmotionName() : capitalize(mood.name());
        TextColor primaryColor = TextColor.fromFormatting(mood.primaryFormatting);
        TextColor secondaryColor = TextColor.fromFormatting(mood.secondaryFormatting);
        List<PetComponent.WeightedEmotionColor> palette = currentPaletteStops;

        Text baseText = switch (Math.max(level, 0)) {
            case 0 -> createStaticPaletteText(label, palette, primaryColor);
            case 1 -> createBreathingText(label, palette, primaryColor, secondaryColor, 1);
            case 2 -> createBreathingGradient(label, palette, primaryColor, secondaryColor, 2);
            case 3 -> createBreathingMovingGradient(label, palette, primaryColor, secondaryColor, 3);
            default -> createBreathingMovingGradient(label, palette, primaryColor, secondaryColor, level);
        };

        if (!withDebug) {
            return baseText;
        }
        
        // Enhanced debug output showing emotional buildup system
        MutableText debugText = baseText.copy();
        debugText.append(Text.literal(" [" + level + "]").styled(s -> s.withColor(TextColor.fromFormatting(Formatting.GRAY))));
        
        // Show buildup metrics
        float currentStrength = moodBlend.getOrDefault(currentMood, 0f);
        float buildupMult = computeBuildupMultiplier(currentStrength);
        int activeEmotionCount = (int) emotionRecords.values().stream()
            .filter(r -> r.intensity > EPSILON)
            .count();
        
        debugText.append(Text.literal(" (s:" + String.format("%.2f", currentStrength))
            .styled(s -> s.withColor(TextColor.fromFormatting(Formatting.DARK_GRAY))));
        
        if (buildupMult != 1.0f) {
            Formatting buildupColor = buildupMult > 1.0f ? Formatting.GREEN : Formatting.YELLOW;
            debugText.append(Text.literal(" b:" + String.format("%.2f", buildupMult))
                .styled(s -> s.withColor(TextColor.fromFormatting(buildupColor))));
        }
        
        if (activeEmotionCount > 1) {
            debugText.append(Text.literal(" e:" + activeEmotionCount)
                .styled(s -> s.withColor(TextColor.fromFormatting(Formatting.AQUA))));
        }
        
        debugText.append(Text.literal(")")
            .styled(s -> s.withColor(TextColor.fromFormatting(Formatting.DARK_GRAY))));
        
        return debugText;
    }

    private Text createStaticPaletteText(String text, List<PetComponent.WeightedEmotionColor> palette, TextColor fallbackPrimary) {
        TextColor paletteColor = (palette == null || palette.isEmpty()) ? fallbackPrimary : palette.get(0).color();
        float emphasis = (palette == null || palette.isEmpty()) ? 0f : 0.85f;
        TextColor finalColor = blendWithFallback(fallbackPrimary, paletteColor, emphasis);
        return Text.literal(text).styled(s -> s.withColor(finalColor));
    }

    private MutableText createBreathingText(String text,
            List<PetComponent.WeightedEmotionColor> palette,
            TextColor fallbackPrimary, TextColor fallbackSecondary, int level) {
        long worldTime = parent.getPet().getWorld().getTime();
        int breathingSpeed = computeBreathingDuration(level, BASE_BREATHING_SPEEDS);
        int updateInterval = getAnimationUpdateInterval(level);
        long animationTime = (worldTime / updateInterval) * updateInterval;
        double breathingPhase = (animationTime % breathingSpeed) / (double) breathingSpeed;
        double breathingIntensity = (Math.sin(breathingPhase * 2 * Math.PI) + 1) / 2;
        double[] minIntensities = {0.30, 0.25, 0.15};
        double[] maxIntensities = {0.50, 0.65, 0.75};
        int levelIndex = Math.min(level - 1, minIntensities.length - 1);
        double minIntensity = minIntensities[levelIndex];
        double maxIntensity = maxIntensities[levelIndex];
        double scaledIntensity = minIntensity + (breathingIntensity * (maxIntensity - minIntensity));
        MutableText out = Text.empty();
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            double charPhase = (i * 0.4) + (breathingPhase * 1.5);
            double charBreathing = (Math.sin(charPhase) + 1) / 2;
            double finalIntensity = (scaledIntensity + charBreathing * 0.3) / 1.3;
            TextColor base = finalIntensity > 0.5 ? fallbackSecondary : fallbackPrimary;
            float gradientAnchor = MathHelper.clamp(
                    (float) ((breathingPhase * 0.45) + (i / (float) Math.max(1, chars.length - 1)) * 0.55
                            + charBreathing * 0.1),
                    0f, 1f);
            TextColor paletteColor = PetComponent.sampleEmotionPalette(palette, gradientAnchor, base);
            float emphasis = MathHelper.clamp(0.55f + (float) finalIntensity * 0.35f, 0f, 1f);
            TextColor finalColor = blendWithFallback(base, paletteColor, emphasis);
            out.append(Text.literal(String.valueOf(chars[i])).styled(s -> s.withColor(finalColor)));
        }
        return out;
    }

    private MutableText createBreathingGradient(String text,
            List<PetComponent.WeightedEmotionColor> palette,
            TextColor fallbackPrimary, TextColor fallbackSecondary, int level) {
        long worldTime = parent.getPet().getWorld().getTime();
        int breathingSpeed = computeBreathingDuration(level, BASE_GRADIENT_SPEEDS);
        int updateInterval = getAnimationUpdateInterval(level);
        long animationTime = (worldTime / updateInterval) * updateInterval;
        double breathingPhase = (animationTime % breathingSpeed) / (double) breathingSpeed;
        double breathingIntensity = (Math.sin(breathingPhase * 2 * Math.PI) + 1) / 2;
        MutableText out = Text.empty();
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            double charPhase = (i * 0.45) + breathingPhase * 1.1;
            double charBreathing = (Math.sin(charPhase) + 1) / 2;
            double blend = (breathingIntensity * 0.6 + charBreathing * 0.4);
            TextColor base = UIStyle.interpolateColor(fallbackPrimary, fallbackSecondary, MathHelper.clamp((float) blend, 0f, 1f));
            float gradientAnchor = MathHelper.clamp(
                    (float) (breathingPhase * 0.35 + (i / (float) Math.max(1, chars.length - 1)) * 0.65),
                    0f, 1f);
            TextColor paletteColor = PetComponent.sampleEmotionPalette(palette, gradientAnchor, base);
            float emphasis = MathHelper.clamp(0.6f + (float) blend * 0.35f, 0f, 1f);
            TextColor finalColor = blendWithFallback(base, paletteColor, emphasis);
            out.append(Text.literal(String.valueOf(chars[i])).styled(s -> s.withColor(finalColor)));
        }
        return out;
    }

    private MutableText createBreathingMovingGradient(String text,
            List<PetComponent.WeightedEmotionColor> palette,
            TextColor fallbackPrimary, TextColor fallbackSecondary, int level) {
        long worldTime = parent.getPet().getWorld().getTime();
        int breathingSpeed = computeBreathingDuration(level, BASE_SHIMMER_SPEEDS);
        int updateInterval = getAnimationUpdateInterval(level);
        long animationTime = (worldTime / updateInterval) * updateInterval;
        double breathingPhase = (animationTime % breathingSpeed) / (double) breathingSpeed;
        MutableText out = Text.empty();
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            double wave = Math.sin((i * 0.6) + (breathingPhase * 2 * Math.PI));
            double mix = (wave + 1) / 2;
            double shimmer = (Math.sin((breathingPhase * 2 * Math.PI) + i * 0.25) + 1) / 2;
            TextColor base = UIStyle.interpolateColor(fallbackPrimary, fallbackSecondary, MathHelper.clamp((float) mix, 0f, 1f));
            float gradientAnchor = MathHelper.clamp((float) (mix * 0.5 + shimmer * 0.5), 0f, 1f);
            TextColor paletteColor = PetComponent.sampleEmotionPalette(palette, gradientAnchor, base);
            float emphasis = MathHelper.clamp(0.65f + (float) shimmer * 0.3f, 0f, 1f);
            TextColor finalColor = blendWithFallback(base, paletteColor, emphasis);
            out.append(Text.literal(String.valueOf(chars[i])).styled(s -> s.withColor(finalColor)));
        }
        return out;
    }

    private TextColor blendWithFallback(TextColor fallback, TextColor paletteColor, float emphasis) {
        if (paletteColor == null) {
            return fallback;
        }
        emphasis = MathHelper.clamp(emphasis, 0f, 1f);
        return UIStyle.interpolateColor(fallback, paletteColor, emphasis);
    }

    private int getAnimationUpdateInterval(int level) {
        ensureConfigCache();
        double interval = cachedBaseAnimationUpdateInterval
                - ((level - 1) * cachedAnimationSpeedMultiplier * cachedBaseAnimationUpdateInterval);
        int result = (int) Math.round(interval);
        return Math.max(cachedMinAnimationInterval, Math.min(cachedMaxAnimationInterval, result));
    }

    private boolean getMoodsBoolean(String key, boolean defaultValue) {
        ensureConfigCache();
        if (cachedMoodsSection == null || !cachedMoodsSection.has(key)) {
            return defaultValue;
        }
        JsonElement element = cachedMoodsSection.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return defaultValue;
        }
        return element.getAsBoolean();
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String prettify(String value) {
        return capitalize(value.replace('_', ' '));
    }

    private void buildDefaultOpponentPairs() {
        opponentPairs.clear();
        registerOpponentPair(PetComponent.Emotion.ANGST, PetComponent.Emotion.CHEERFUL);
        registerOpponentPair(PetComponent.Emotion.FOREBODING, PetComponent.Emotion.LAGOM);
        registerOpponentPair(PetComponent.Emotion.STARTLE, PetComponent.Emotion.RELIEF);
        registerOpponentPair(PetComponent.Emotion.FRUSTRATION, PetComponent.Emotion.PLAYFULNESS);
        registerOpponentPair(PetComponent.Emotion.REGRET, PetComponent.Emotion.HOPEFUL);
        registerOpponentPair(PetComponent.Emotion.SAUDADE, PetComponent.Emotion.BLISSFUL);
        registerOpponentPair(PetComponent.Emotion.DISGUST, PetComponent.Emotion.UBUNTU);
        registerOpponentPair(PetComponent.Emotion.STOIC, PetComponent.Emotion.GLEE);
    }

    private void rebuildOpponentPairs() {
        buildDefaultOpponentPairs();
        if (cachedOpponentSection == null) {
            return;
        }
        for (String emotionKey : cachedOpponentSection.keySet()) {
            PetComponent.Emotion source;
            try {
                source = PetComponent.Emotion.valueOf(emotionKey.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            JsonElement element = cachedOpponentSection.get(emotionKey);
            if (!element.isJsonArray()) {
                continue;
            }
            EnumSet<PetComponent.Emotion> set = opponentPairs.computeIfAbsent(source, s -> EnumSet.noneOf(PetComponent.Emotion.class));
            element.getAsJsonArray().forEach(jsonElement -> {
                if (!jsonElement.isJsonPrimitive()) {
                    return;
                }
                String targetName = jsonElement.getAsString().toUpperCase();
                try {
                    PetComponent.Emotion target = PetComponent.Emotion.valueOf(targetName);
                    registerOpponentPair(source, target);
                } catch (IllegalArgumentException ignored) {
                }
            });
        }
    }

    private void registerOpponentPair(PetComponent.Emotion a, PetComponent.Emotion b) {
        opponentPairs.computeIfAbsent(a, key -> EnumSet.noneOf(PetComponent.Emotion.class)).add(b);
        opponentPairs.computeIfAbsent(b, key -> EnumSet.noneOf(PetComponent.Emotion.class)).add(a);
    }

    // --------------------------------------------------------------------------------------------
    // Supporting data structures
    // --------------------------------------------------------------------------------------------

    private static final class EmotionRecord {
        final PetComponent.Emotion emotion;
        float intensity = DEFAULT_INTENSITY;
        float impactBudget = 0f;
        float cadenceEMA = 0f;
        float volatilityEMA = 0f;
        float peakEMA = 0f;
        float habituationSlope = HABITUATION_BASE;
        float sensitisationGain = 1.0f;
        float homeostasisBias = 1.0f;
        float contagionShare = 0f;
        float relationshipGuard = RELATIONSHIP_BASE;
        float dangerWindow = DANGER_BASE;
        float appraisalConfidence = APPRAISAL_BASE;
        float weight = 0f;
        long startTime;
        long lastEventTime;
        long lastUpdateTime;

        EmotionRecord(PetComponent.Emotion emotion, long now) {
            this.emotion = emotion;
            this.startTime = now;
            this.lastEventTime = now;
            this.lastUpdateTime = now;
        }

        void applyDecay(long now, PetMoodEngine engine) {
            if (lastUpdateTime >= now) {
                return;
            }
            float delta = Math.max(0f, now - lastUpdateTime);
            float cadence = cadenceEMA > 0f ? cadenceEMA : HABITUATION_BASE;
            float adaptiveHalf = MathHelper.clamp(cadence * HALF_LIFE_MULTIPLIER, MIN_HALF_LIFE, MAX_HALF_LIFE);
            
            // Configure special decay rates for cultural emotions
            if (emotion == PetComponent.Emotion.ENNUI) {
                // Ennui decays very fast (boredom dissipates quickly)
                adaptiveHalf *= 0.5f;  // Half the normal half-life (0.9995/tick effect)
            } else if (emotion == PetComponent.Emotion.HIRAETH) {
                // Hiraeth decays slowly (homesickness lingers)
                adaptiveHalf *= 2.0f;  // Double the normal half-life (0.998/tick effect)
            } else if (emotion == PetComponent.Emotion.SOBREMESA || emotion == PetComponent.Emotion.YUGEN) {
                // Sobremesa and Yugen have moderate slower decay
                adaptiveHalf *= 1.3f;
            } else if (emotion == PetComponent.Emotion.KEFI) {
                // Kefi has slightly faster decay (energy bursts don't last)
                adaptiveHalf *= 0.8f;
            }
            
            // Valence bias: negative emotions persist 2-3x longer than positive (negativity bias)
            // This is psychologically realistic - threats/losses have stronger memory traces
            boolean isNegative = isNegativeEmotion(emotion);
            if (isNegative) {
                // Negative emotions: slower decay (2x half-life)
                adaptiveHalf *= 2.0f;
            } else if (isPositiveEmotion(emotion)) {
                // Positive emotions: normal or slightly faster decay
                // Keep as-is, or slightly faster for very positive emotions
                if (emotion == PetComponent.Emotion.GLEE || emotion == PetComponent.Emotion.BLISSFUL) {
                    adaptiveHalf *= 0.85f; // Intense joy fades faster
                }
            }
            
            // Condition-aware decay: if triggering condition still exists, decay much slower
            // This prevents emotions from fading while their cause is still present
            if (engine != null && engine.hasOngoingCondition(emotion, now)) {
                // Condition still present: decay 3-4x slower
                adaptiveHalf *= 3.5f;
            }
            
            float decayRate = (float) (Math.log(2) / adaptiveHalf);
            float decay = (float) Math.exp(-decayRate * delta);
            intensity *= decay;
            impactBudget *= decay;
            
            // HomeostasisBias recovery for idle states  
            homeostasisBias = MathHelper.lerp((float) Math.exp(-delta / HOMEOSTASIS_RECOVERY_HALF), homeostasisBias, 1.1f);  // x1.1f for recovery
            contagionShare *= Math.exp(-delta / 400f);
            lastUpdateTime = now;
        }
        
        private boolean isNegativeEmotion(PetComponent.Emotion emotion) {
            // Negative valence emotions that should persist longer
            switch (emotion) {
                case ANGST:
                case FOREBODING:
                case STARTLE:
                case FRUSTRATION:
                case REGRET:
                case SAUDADE:
                case DISGUST:
                case HIRAETH:
                case ENNUI:
                    return true;
                default:
                    return false;
            }
        }
        
        private boolean isPositiveEmotion(PetComponent.Emotion emotion) {
            // Positive valence emotions
            switch (emotion) {
                case CHEERFUL:
                case BLISSFUL:
                case GLEE:
                case RELIEF:
                case HOPEFUL:
                case PLAYFULNESS:
                case LAGOM:
                case UBUNTU:
                case KEFI:
                case CONTENT:
                    return true;
                default:
                    return false;
            }
        }
    }

    // ===== LEVEL TRACKING & BUILDUP SYSTEM =====
    
    private static final class LevelSnapshot {
        final int level;
        final long timestamp;
        
        LevelSnapshot(int level, long timestamp) {
            this.level = level;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Initialize per-mood threshold adjustments for fine-tuned emotional pacing.
     * Different moods have different rarity/intensity profiles.
     */
    private void initializePerMoodThresholds() {
        // Common baseline moods - easier to access, rarely go to level 3
        perMoodThresholds.put(PetComponent.Mood.CALM, new float[]{0.30f, 0.60f, 0.85f});
        perMoodThresholds.put(PetComponent.Mood.HAPPY, new float[]{0.35f, 0.65f, 0.88f});
        perMoodThresholds.put(PetComponent.Mood.CURIOUS, new float[]{0.35f, 0.68f, 0.90f});
        
        // Engagement moods - standard distribution
        perMoodThresholds.put(PetComponent.Mood.PLAYFUL, new float[]{0.35f, 0.65f, 0.88f});
        perMoodThresholds.put(PetComponent.Mood.BONDED, new float[]{0.35f, 0.65f, 0.88f});
        perMoodThresholds.put(PetComponent.Mood.FOCUSED, new float[]{0.38f, 0.68f, 0.88f});
        perMoodThresholds.put(PetComponent.Mood.PASSIONATE, new float[]{0.40f, 0.70f, 0.90f});
        
        // Emotional depth moods - easier to hit level 2 for storytelling
        perMoodThresholds.put(PetComponent.Mood.PROTECTIVE, new float[]{0.30f, 0.60f, 0.85f});
        perMoodThresholds.put(PetComponent.Mood.SAUDADE, new float[]{0.35f, 0.62f, 0.88f});
        perMoodThresholds.put(PetComponent.Mood.YUGEN, new float[]{0.40f, 0.68f, 0.88f});
        perMoodThresholds.put(PetComponent.Mood.SISU, new float[]{0.38f, 0.65f, 0.88f});
        
        // Negative moods - clear escalation paths
        perMoodThresholds.put(PetComponent.Mood.RESTLESS, new float[]{0.35f, 0.65f, 0.88f});
        perMoodThresholds.put(PetComponent.Mood.AFRAID, new float[]{0.30f, 0.60f, 0.85f});
        perMoodThresholds.put(PetComponent.Mood.ANGRY, new float[]{0.38f, 0.70f, 0.92f});
        
        // Ultra-rare moods - very hard to reach high levels
        perMoodThresholds.put(PetComponent.Mood.ECHOED_RESONANCE, new float[]{0.45f, 0.75f, 0.95f});
        perMoodThresholds.put(PetComponent.Mood.ARCANE_OVERFLOW, new float[]{0.40f, 0.72f, 0.92f});
        perMoodThresholds.put(PetComponent.Mood.PACK_SPIRIT, new float[]{0.38f, 0.68f, 0.90f});
    }
    
    /**
     * Get mood-specific thresholds if available, otherwise default.
     */
    private float[] getMoodSpecificThresholds(PetComponent.Mood mood) {
        float[] specific = perMoodThresholds.get(mood);
        return specific != null ? specific : getLevelThresholds();
    }
    
    /**
     * Compute buildup multiplier based on emotional momentum.
     * Rising emotions get boosted (feels responsive), falling get gentle resistance.
     * This creates a "feeling the buildup" experience where escalating emotions accelerate.
     */
    private float computeBuildupMultiplier(float currentStrength) {
        // Need at least one previous reading AND must be same mood to compare
        if (previousMoodStrength <= 0f || previousMoodLevel < 0) {
            return 1.0f;  // First update or mood switch, no trend available
        }
        
        // Calculate trend: positive = rising, negative = falling
        float trend = currentStrength - previousMoodStrength;
        
        // Only apply multiplier if trend is significant (avoid noise)
        if (trend > BUILDUP_TREND_THRESHOLD) {
            // Rising emotion: BOOST toward next level (responsive, exciting)
            return BUILDUP_RISING_MULTIPLIER;  // 1.2x
        } else if (trend < -BUILDUP_TREND_THRESHOLD) {
            // Falling emotion: gentle resistance (prevents whiplash, smooth decay)
            return BUILDUP_FALLING_MULTIPLIER;  // 0.85x
        }
        
        // Steady state or minor fluctuation: normal scaling
        return 1.0f;
    }
    
    /**
     * Apply hysteresis to level transitions to prevent jitter.
     * Requires extra push to go UP (progressive resistance), less to go DOWN (smooth decay).
     * Uses effectiveStrength which already includes buildup multiplier.
     * 
     * @param rawLevel The level calculated from thresholds
     * @param effectiveStrength The mood strength after buildup multiplier (0-1)
     * @param thresholds The threshold array for current mood
     * @return The final level after hysteresis
     */
    private int applyLevelHysteresis(int rawLevel, float effectiveStrength, float[] thresholds) {
        // No previous level data or no change - skip hysteresis
        if (previousMoodLevel < 0 || rawLevel == previousMoodLevel) {
            return rawLevel;
        }
        
        if (rawLevel > previousMoodLevel) {
            // GOING UP: need to exceed threshold by extra margin (creates "earning it" feel)
            // The threshold we're trying to cross is at index (rawLevel - 1)
            int thresholdIndex = rawLevel - 1;
            
            // Bounds check: ensure valid threshold index
            if (thresholdIndex < 0 || thresholdIndex >= thresholds.length) {
                return rawLevel;  // Edge case: trust the raw calculation
            }
            
            float crossingThreshold = thresholds[thresholdIndex];
            
            // Progressive resistance: harder to reach higher levels
            // Level 0→1: +0.03+0.02 = 0.05 margin
            // Level 1→2: +0.03+0.04 = 0.07 margin  
            // Level 2→3: +0.03+0.06 = 0.09 margin (hardest!)
            float requiredMargin = 0.03f + (0.02f * rawLevel);
            float actualMargin = effectiveStrength - crossingThreshold;
            
            if (actualMargin < requiredMargin) {
                // Not enough push - stay at current level
                return previousMoodLevel;
            }
        } else {
            // GOING DOWN: need to fall below threshold by small margin (easier than going up)
            // The threshold we're falling below is at index (previousMoodLevel - 1)
            int thresholdIndex = previousMoodLevel - 1;
            
            // Bounds check
            if (thresholdIndex < 0 || thresholdIndex >= thresholds.length) {
                return rawLevel;  // Edge case: trust the raw calculation
            }
            
            float crossingThreshold = thresholds[thresholdIndex];
            
            // Small margin prevents rapid oscillation but allows smooth decay
            float requiredMargin = 0.02f;
            float actualMargin = crossingThreshold - effectiveStrength;
            
            if (actualMargin < requiredMargin) {
                // Haven't fallen enough - stay at current level
                return previousMoodLevel;
            }
        }
        
        // Passed hysteresis checks - commit to new level
        return rawLevel;
    }
    
    /**
     * Update level history for habituation tracking.
     * Tracks time spent at high levels to add resistance if camping at level 2-3.
     * Optimized for O(1) amortized cost by using incremental updates.
     */
    private void updateLevelHistory(int currentLevel, long now) {
        // Incremental update: calculate contribution of the period we're adding
        if (!levelHistory.isEmpty()) {
            LevelSnapshot last = levelHistory.getLast();
            long periodDuration = now - last.timestamp;
            
            // If previous level was 2-3, add this period to high-level time
            if (last.level >= 2 && periodDuration > 0) {
                recentLevel23Time += periodDuration;
            }
        }
        
        // Add new snapshot
        levelHistory.add(new LevelSnapshot(currentLevel, now));
        
        // Trim old entries and adjust high-level time accordingly
        while (levelHistory.size() > LEVEL_HISTORY_SIZE) {
            LevelSnapshot removed = levelHistory.removeFirst();
            
            // Subtract the removed period from high-level time if it was level 2-3
            if (levelHistory.size() > 0) {
                LevelSnapshot nextAfterRemoved = levelHistory.getFirst();
                long removedPeriod = nextAfterRemoved.timestamp - removed.timestamp;
                
                if (removed.level >= 2 && removedPeriod > 0) {
                    recentLevel23Time = Math.max(0f, recentLevel23Time - removedPeriod);
                }
            }
        }
    }
    
    /**
     * Get habituation drag - reduces strength if pet has been at high levels too long.
     * Prevents "ceiling camping" where pets stay at level 3 constantly.
     */
    private float getHabituationDrag() {
        if (levelHistory.size() < 10) {
            return 0f;  // Not enough history yet
        }
        
        // Calculate what fraction of recent time was spent at level 2-3
        long totalTime = 0;
        if (!levelHistory.isEmpty()) {
            totalTime = levelHistory.getLast().timestamp - levelHistory.getFirst().timestamp;
        }
        
        if (totalTime <= 0) {
            return 0f;
        }
        
        float highLevelRatio = recentLevel23Time / Math.max(1f, totalTime);
        
        // If spent >50% of recent time at high levels, add resistance
        if (highLevelRatio > 0.5f) {
            return 0.10f * (highLevelRatio - 0.5f);  // Max -0.05 penalty
        }
        
        return 0f;
    }

    private static final class Candidate {
        final EmotionRecord record;
        final float freshness;
        final float frequency;
        float signal;

        Candidate(EmotionRecord record, float freshness, float frequency, float signal) {
            this.record = record;
            this.freshness = freshness;
            this.frequency = frequency;
            this.signal = signal;
        }
    }

    private record OpponentConflict(Candidate a, Candidate b, float combinedWeight) {
    }
}


