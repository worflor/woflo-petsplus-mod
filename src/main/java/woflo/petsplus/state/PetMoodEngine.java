package woflo.petsplus.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Encapsulates all mood/emotion logic for a PetComponent: emotion slots, blending, momentum,
 * hysteresis, config helpers, and NBT serialization.
 */
final class PetMoodEngine {
    private final PetComponent parent;

    // Fixed-size emotion slots
    private static final int DEFAULT_EMOTION_SLOT_COUNT = 5;
    private static final float DEFAULT_MAX_EMOTION_WEIGHT = 5.0f;
    private static final float DEFAULT_WEIGHT_SATURATION_ALPHA = 0.4f;
    private static final double DEFAULT_EVICTION_AGE_COEFF = 0.0005d;
    private static final double DEFAULT_EVICTION_WEIGHT_COEFF = 1.0d;
    private static final double DEFAULT_EVICTION_PARKED_BIAS = 1.0d;

    private static class EmotionSlot {
        PetComponent.Emotion emotion;
        float weight;
        float decayRate; // multiplier per decay tick (e.g., 0.96)
        long addedTick;
        long lastUpdatedTick;
        boolean parked;
    }

    // Blended mood state
    private EnumMap<PetComponent.Mood, Float> moodBlend = new EnumMap<>(PetComponent.Mood.class);
    private PetComponent.Mood currentMood = PetComponent.Mood.ZEN;
    private int moodLevel = 0;
    private long lastMoodUpdate = 0;
    private long lastEmotionDecayTick = 0;
    private int moodPendingCounter = 0;
    private PetComponent.Mood moodPendingCandidate = null;
    private EmotionSlot[] emotionSlots;

    PetMoodEngine(PetComponent parent) {
        this.parent = parent;
        for (PetComponent.Mood m : PetComponent.Mood.values()) moodBlend.put(m, 0f);
        int slotCount = getMoodsInt("slotCount", DEFAULT_EMOTION_SLOT_COUNT);
        this.emotionSlots = new EmotionSlot[Math.max(1, slotCount)];
    }

    // Public API
    PetComponent.Mood getCurrentMood() { update(); return currentMood; }
    int getMoodLevel() { update(); return moodLevel; }
    void update() { updateEmotionStateAndMood(); }

    void pushEmotion(PetComponent.Emotion emotion, float amount) {
        if (amount <= 0) return;
        long now = parent.getPet().getWorld().getTime();
        float epsilon = getEpsilon();
        int bestFreeIdx = -1;
        for (int i = 0; i < emotionSlots.length; i++) {
            EmotionSlot slot = emotionSlots[i];
            if (slot != null && slot.emotion == emotion) {
                slot.weight = saturateWeight(slot.weight, amount, emotion);
                slot.lastUpdatedTick = now;
                slot.parked = false;
                return;
            }
            if (bestFreeIdx >= 0) continue;
            if (slot == null || slot.parked || slot.weight <= epsilon) bestFreeIdx = i;
        }
        if (bestFreeIdx >= 0) {
            emotionSlots[bestFreeIdx] = createSlot(emotion, amount, now);
            return;
        }
        int evictIdx = selectEvictionIndex(now);
        emotionSlots[evictIdx] = createSlot(emotion, amount, now);
    }

    float getMoodStrength(PetComponent.Mood mood) {
        update();
        return moodBlend != null ? Math.max(0f, Math.min(1f, moodBlend.getOrDefault(mood, 0f))) : 0f;
    }

    Map<PetComponent.Mood, Float> getMoodBlend() {
        update();
        return Collections.unmodifiableMap(new EnumMap<>(moodBlend));
    }

    boolean hasMoodAbove(PetComponent.Mood mood, float threshold) {
        return getMoodStrength(mood) >= threshold;
    }

    PetComponent.Mood getDominantMood() {
        update();
        PetComponent.Mood best = PetComponent.Mood.ZEN;
        float bestVal = -1f;
        for (Map.Entry<PetComponent.Mood, Float> e : moodBlend.entrySet()) {
            if (e.getValue() > bestVal) { bestVal = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    @Nullable PetComponent.Emotion getDominantEmotion() {
        update();
        float best = 0f;
        PetComponent.Emotion bestE = null;
        for (EmotionSlot s : emotionSlots) {
            if (s == null) continue;
            if (s.weight > best) { best = s.weight; bestE = s.emotion; }
        }
        return bestE;
    }

    String getDominantEmotionName() {
        float best = 0f;
        PetComponent.Emotion bestE = null;
        for (EmotionSlot s : emotionSlots) {
            if (s == null) continue;
            if (s.weight > best) { best = s.weight; bestE = s.emotion; }
        }
        return bestE != null ? prettify(bestE.name()) : "None";
    }

    Text getMoodText() {
        PetComponent.Mood mood = getCurrentMood();
        int level = getMoodLevel();

        boolean showEmotion = getMoodsBoolean("showDominantEmotion", false);
        String label = showEmotion ? getDominantEmotionName() : capitalize(mood.name());

        Formatting primary = mood.primaryFormatting;
        Formatting secondary = mood.secondaryFormatting;

        // Progressive display based on mood intensity level (0-3)
        return switch (level) {
            case 0, 1 -> {
                // Level 0-1: Base color only
                yield Text.literal(label).styled(s -> s.withColor(TextColor.fromFormatting(primary)));
            }
            case 2 -> {
                // Level 2: Static gradient (alternating characters)
                MutableText out = Text.empty();
                char[] chars = label.toCharArray();
                for (int i = 0; i < chars.length; i++) {
                    Formatting f = (i % 2 == 0) ? primary : secondary;
                    out.append(Text.literal(String.valueOf(chars[i])).styled(s -> s.withColor(TextColor.fromFormatting(f))));
                }
                yield out;
            }
            case 3 -> {
                // Level 3: Moving gradient (shifts over time)
                yield createMovingGradient(label, primary, secondary);
            }
            default -> Text.literal(label).styled(s -> s.withColor(TextColor.fromFormatting(primary)));
        };
    }

    /**
     * Get mood text with debug information showing power level.
     */
    Text getMoodTextWithDebug() {
        PetComponent.Mood mood = getCurrentMood();
        int level = getMoodLevel();

        boolean showEmotion = getMoodsBoolean("showDominantEmotion", false);
        String label = showEmotion ? getDominantEmotionName() : capitalize(mood.name());

        // Add debug power level indicator
        String debugLabel = label + " [" + level + "]";

        Formatting primary = mood.primaryFormatting;
        Formatting secondary = mood.secondaryFormatting;

        // Progressive display based on mood intensity level (0-3) with debug info
        return switch (level) {
            case 0, 1 -> {
                // Level 0-1: Base color only
                yield Text.literal(debugLabel).styled(s -> s.withColor(TextColor.fromFormatting(primary)));
            }
            case 2 -> {
                // Level 2: Static gradient (alternating characters) - only apply gradient to mood name, not debug info
                MutableText out = Text.empty();
                char[] chars = label.toCharArray();
                for (int i = 0; i < chars.length; i++) {
                    Formatting f = (i % 2 == 0) ? primary : secondary;
                    out.append(Text.literal(String.valueOf(chars[i])).styled(s -> s.withColor(TextColor.fromFormatting(f))));
                }
                // Add debug info in gray
                out.append(Text.literal(" [" + level + "]").styled(s -> s.withColor(TextColor.fromFormatting(Formatting.GRAY))));
                yield out;
            }
            case 3 -> {
                // Level 3: Moving gradient for mood name, gray debug info
                MutableText gradientText = createMovingGradient(label, primary, secondary);
                gradientText.append(Text.literal(" [" + level + "]").styled(s -> s.withColor(TextColor.fromFormatting(Formatting.GRAY))));
                yield gradientText;
            }
            default -> Text.literal(debugLabel).styled(s -> s.withColor(TextColor.fromFormatting(primary)));
        };
    }

    /**
     * Create a moving gradient that shifts colors based on world time.
     */
    private MutableText createMovingGradient(String text, Formatting primary, Formatting secondary) {
        MutableText out = Text.empty();
        char[] chars = text.toCharArray();

        // Use world time to create movement (every 10 ticks = 0.5 seconds shift)
        long worldTime = parent.getPet().getWorld().getTime();
        int timeOffset = (int) (worldTime / 10) % 4; // 4-step animation cycle

        for (int i = 0; i < chars.length; i++) {
            // Create moving wave pattern
            boolean usePrimary = ((i + timeOffset) % 3) != 0;
            Formatting f = usePrimary ? primary : secondary;
            out.append(Text.literal(String.valueOf(chars[i])).styled(s -> s.withColor(TextColor.fromFormatting(f))));
        }

        return out;
    }

    BossBar.Color getMoodBossBarColor() {
        return switch (getCurrentMood()) {
            case JOYFUL -> BossBar.Color.YELLOW;      // Happy - bright
            case FEARFUL -> BossBar.Color.RED;        // Scared - danger
            case WRATHFUL -> BossBar.Color.RED;       // Angry - intense (keeping red for both fear/anger)
            case SAUDADE -> BossBar.Color.BLUE;       // Melancholic - deep blue
            case ZEN -> BossBar.Color.GREEN;          // Calm - natural
            case ZEALOUS -> BossBar.Color.YELLOW;     // Passionate - fire (keeping yellow for energy)
            case YUGEN -> BossBar.Color.PURPLE;       // Mysterious - purple
            case TARAB -> BossBar.Color.PINK;         // Ecstatic - vibrant pink
            case KINTSUGI -> BossBar.Color.WHITE;     // Healing - pure white
            case PLAYFUL -> BossBar.Color.GREEN;      // Fun - lively green
            case CURIOUS -> BossBar.Color.BLUE;       // Wonder - bright blue (different from protective)
            case PROTECTIVE -> BossBar.Color.BLUE;    // Guardian - steady blue
            case BONDED -> BossBar.Color.PURPLE;      // Connection - deep purple
            case RESTLESS -> BossBar.Color.RED;       // Agitated - hot red
        };
    }

    // NBT
    void writeToNbt(NbtCompound nbt) {
        nbt.putString("currentMood", currentMood.name());
        nbt.putInt("moodLevel", moodLevel);
        nbt.putLong("lastMoodUpdate", lastMoodUpdate);
        nbt.putLong("lastEmotionDecayTick", lastEmotionDecayTick);

        if (moodBlend != null && !moodBlend.isEmpty()) {
            NbtCompound blend = new NbtCompound();
            for (Map.Entry<PetComponent.Mood, Float> e : moodBlend.entrySet()) {
                blend.putFloat(e.getKey().name(), e.getValue());
            }
            nbt.put("moodBlend", blend);
        }

        if (emotionSlots != null && emotionSlots.length > 0) {
            NbtCompound emotionsNbt = new NbtCompound();
            emotionsNbt.putInt("slotCount", emotionSlots.length);
            for (int i = 0; i < emotionSlots.length; i++) {
                EmotionSlot s = emotionSlots[i];
                if (s == null) continue;
                NbtCompound slot = new NbtCompound();
                slot.putString("id", s.emotion.name());
                slot.putFloat("w", s.weight);
                slot.putFloat("d", s.decayRate);
                slot.putLong("added", s.addedTick);
                slot.putLong("updated", s.lastUpdatedTick);
                slot.putBoolean("parked", s.parked);
                emotionsNbt.put("slot_" + i, slot);
            }
            nbt.put("emotions", emotionsNbt);
        }
    }

    void readFromNbt(NbtCompound nbt) {
        if (nbt.contains("currentMood")) {
            nbt.getString("currentMood").ifPresent(value -> {
                try { currentMood = PetComponent.Mood.valueOf(value); }
                catch (IllegalArgumentException e) { currentMood = PetComponent.Mood.ZEN; }
            });
        }
        if (nbt.contains("moodLevel")) {
            nbt.getInt("moodLevel").ifPresent(value -> moodLevel = value);
        }
        if (nbt.contains("lastMoodUpdate")) {
            nbt.getLong("lastMoodUpdate").ifPresent(value -> lastMoodUpdate = value);
        }
        if (nbt.contains("lastEmotionDecayTick")) {
            nbt.getLong("lastEmotionDecayTick").ifPresent(value -> lastEmotionDecayTick = value);
        }

        // Load mood blend
        this.moodBlend = new EnumMap<>(PetComponent.Mood.class);
        for (PetComponent.Mood m : PetComponent.Mood.values()) this.moodBlend.put(m, 0f);
        if (nbt.contains("moodBlend")) {
            nbt.getCompound("moodBlend").ifPresent(blend -> {
                float sum = 0f;
                for (PetComponent.Mood m : PetComponent.Mood.values()) {
                    float v = blend.getFloat(m.name()).orElse(0f);
                    if (v > 0) {
                        moodBlend.put(m, v);
                        sum += v;
                    }
                }
                if (sum > 0f) {
                    for (PetComponent.Mood m : PetComponent.Mood.values()) {
                        moodBlend.put(m, moodBlend.getOrDefault(m, 0f) / sum);
                    }
                } else {
                    moodBlend.put(currentMood, 1f);
                }
            });
        } else {
            moodBlend.put(currentMood, 1f);
        }

        // Load emotion slots
        if (nbt.contains("emotions")) {
            nbt.getCompound("emotions").ifPresent(em -> {
                int count = em.getInt("slotCount").orElse(DEFAULT_EMOTION_SLOT_COUNT);
                emotionSlots = new EmotionSlot[Math.max(1, count)];
                for (int i = 0; i < count; i++) {
                    var slotOpt = em.getCompound("slot_" + i);
                    if (slotOpt.isEmpty()) continue;
                    var snbt = slotOpt.get();
                    EmotionSlot s = new EmotionSlot();
                    snbt.getString("id").ifPresent(id -> {
                        try { s.emotion = PetComponent.Emotion.valueOf(id); } catch (Exception ignored) {}
                    });
                    s.weight = snbt.getFloat("w").orElse(0f);
                    s.decayRate = snbt.getFloat("d").orElse((float) getMoodsDouble("defaultDecayRate", 0.96));
                    s.addedTick = snbt.getLong("added").orElse(0L);
                    s.lastUpdatedTick = snbt.getLong("updated").orElse(0L);
                    s.parked = snbt.getBoolean("parked").orElse(false);
                    emotionSlots[i] = s.emotion != null ? s : null;
                }
            });
        }
    }

    // Core update
    private void updateEmotionStateAndMood() {
        long now = parent.getPet().getWorld().getTime();

        int configuredSlotCount = Math.max(1, getMoodsInt("slotCount", DEFAULT_EMOTION_SLOT_COUNT));
        if (emotionSlots == null || emotionSlots.length == 0) {
            emotionSlots = new EmotionSlot[configuredSlotCount];
        } else if (emotionSlots.length != configuredSlotCount) {
            emotionSlots = resizeEmotionSlots(emotionSlots, configuredSlotCount);
        }

        int decayInterval = getMoodsInt("decayTickInterval", 40);
        if (now - lastEmotionDecayTick >= decayInterval) {
            lastEmotionDecayTick = now;
            float epsilon = getEpsilon();
            for (int i = 0; i < emotionSlots.length; i++) {
                EmotionSlot slot = emotionSlots[i];
                if (slot == null) continue;
                if (slot.parked) { emotionSlots[i] = null; continue; }
                float rate = slot.decayRate > 0 ? slot.decayRate : (float) getMoodsDouble("defaultDecayRate", 0.96);
                slot.weight *= rate;
                if (slot.weight < epsilon) { emotionSlots[i] = null; }
            }
        }

        if (now - lastMoodUpdate < 20) return;
        lastMoodUpdate = now;

        float[] moodScores = new float[PetComponent.Mood.values().length];
        float epsilon = getEpsilon();
        for (EmotionSlot slot : emotionSlots) {
            if (slot == null || slot.parked || slot.weight <= epsilon) continue;
            Map<PetComponent.Mood, Float> weights = getEmotionToMoodWeights(slot.emotion);
            for (Map.Entry<PetComponent.Mood, Float> e : weights.entrySet()) {
                moodScores[e.getKey().ordinal()] += slot.weight * e.getValue();
            }
        }
        float sum = 0f;
        for (float s : moodScores) sum += Math.max(0f, s);
        EnumMap<PetComponent.Mood, Float> target = new EnumMap<>(PetComponent.Mood.class);
        if (sum <= epsilon) {
            for (PetComponent.Mood m : PetComponent.Mood.values()) target.put(m, m == PetComponent.Mood.ZEN ? 1f : 0f);
        } else {
            for (int i = 0; i < moodScores.length; i++) {
                target.put(PetComponent.Mood.values()[i], Math.max(0f, moodScores[i]) / sum);
            }
        }

        float momentum = (float) getMoodsDouble("momentum", 0.25);
        if (moodBlend == null) moodBlend = new EnumMap<>(PetComponent.Mood.class);
        for (PetComponent.Mood m : PetComponent.Mood.values()) {
            float cur = moodBlend.getOrDefault(m, m == PetComponent.Mood.ZEN ? 1f : 0f);
            float tar = target.getOrDefault(m, 0f);
            float next = cur + (tar - cur) * Math.max(0f, Math.min(1f, momentum));
            moodBlend.put(m, next);
        }

        PetComponent.Mood bestMood = PetComponent.Mood.ZEN;
        float bestVal = -1f;
        for (Map.Entry<PetComponent.Mood, Float> e : moodBlend.entrySet()) {
            if (e.getValue() > bestVal) { bestVal = e.getValue(); bestMood = e.getKey(); }
        }

        double switchMargin = getMoodsDouble("switchMargin", 0.15);
        int hysteresisTicks = getMoodsInt("hysteresisTicks", 60);
        int requiredUpdates = Math.max(1, hysteresisTicks / 20);
        float currentStrength = moodBlend.getOrDefault(currentMood, 0f);
        boolean hasMargin = bestVal > (currentStrength * (1.0 + switchMargin));
        if (bestMood != currentMood && !hasMargin) {
            if (moodPendingCandidate == bestMood) {
                moodPendingCounter++;
            } else {
                moodPendingCandidate = bestMood;
                moodPendingCounter = 1;
            }
            if (moodPendingCounter >= requiredUpdates) {
                currentMood = bestMood;
                moodPendingCandidate = null;
                moodPendingCounter = 0;
            }
        } else {
            currentMood = bestMood;
            moodPendingCandidate = null;
            moodPendingCounter = 0;
        }

        float[] thresholds = getLevelThresholds();
        int level = 0;
        if (bestVal >= thresholds[2]) level = 3;
        else if (bestVal >= thresholds[1]) level = 2;
        else if (bestVal >= thresholds[0]) level = 1;
        this.moodLevel = level;
    }

    // helpers
    private EmotionSlot createSlot(PetComponent.Emotion emotion, float amount, long now) {
        EmotionSlot s = new EmotionSlot();
        s.emotion = emotion;
        s.weight = saturateWeight(0f, amount, emotion);
        s.decayRate = (float) getPerEmotionDecay(emotion, getMoodsDouble("defaultDecayRate", 0.96));
        s.addedTick = now;
        s.lastUpdatedTick = now;
        s.parked = false;
        return s;
    }

    private int selectEvictionIndex(long now) {
        int idx = 0;
        double worstScore = Double.NEGATIVE_INFINITY;
        double ageCoeff = getEvictionAgeCoeff();
        double lowWeightCoeff = getEvictionLowWeightCoeff();
        double parkedBias = getEvictionParkedBias();
        boolean useLastUpdated = useLastUpdatedForEviction();
        float epsilon = getEpsilon();
        for (int i = 0; i < emotionSlots.length; i++) {
            EmotionSlot s = emotionSlots[i];
            if (s == null) { return i; }
            if (s.parked || s.weight <= epsilon) { return i; }
            double referenceTick = useLastUpdated ? s.lastUpdatedTick : s.addedTick;
            double ageTicks = Math.max(0d, now - referenceTick);
            double ageScore = ageTicks * ageCoeff;
            float maxWeight = getMaxWeight(s.emotion);
            float effectiveMax = maxWeight > 0f ? maxWeight : Math.max(1f, s.weight);
            double normalizedWeight = Math.min(1.0, s.weight / effectiveMax);
            double lowWeightScore = (1.0 - normalizedWeight) * lowWeightCoeff;
            double parkedScore = s.parked ? parkedBias : 0.0;
            double typeBias = getEvictionTypeBias(s.emotion);
            double score = ageScore + lowWeightScore + parkedScore + typeBias;
            if (score > worstScore) { worstScore = score; idx = i; }
        }
        return idx;
    }

    private float saturateWeight(float currentWeight, float delta, PetComponent.Emotion emotion) {
        float maxWeight = getMaxWeight(emotion);
        float alpha = getWeightSaturationAlpha();
        float clampedCurrent = maxWeight > 0f ? Math.min(Math.max(0f, currentWeight), maxWeight) : Math.max(0f, currentWeight);
        float deltaPositive = Math.max(0f, delta);
        if (maxWeight <= 0f || deltaPositive <= 0f) {
            return clampedCurrent + deltaPositive;
        }
        if (alpha <= 0f) {
            return Math.min(maxWeight, clampedCurrent + deltaPositive);
        }
        float remaining = Math.max(0f, maxWeight - clampedCurrent);
        if (remaining <= 0f) { return maxWeight; }
        float easedAlpha = Math.max(0f, Math.min(1f, alpha));
        if (easedAlpha >= 1f) { return maxWeight; }
        double factor = Math.pow(1.0 - easedAlpha, deltaPositive);
        float gained = remaining * (float) (1.0 - factor);
        return Math.min(maxWeight, clampedCurrent + Math.max(0f, Math.min(remaining, gained)));
    }

    private EmotionSlot[] resizeEmotionSlots(EmotionSlot[] existing, int targetCount) {
        if (targetCount <= 0) return new EmotionSlot[0];
        if (existing == null || existing.length == 0) return new EmotionSlot[targetCount];
        if (existing.length == targetCount) return existing;
        EmotionSlot[] resized = new EmotionSlot[targetCount];
        if (targetCount > existing.length) {
            System.arraycopy(existing, 0, resized, 0, existing.length);
            return resized;
        }
        List<EmotionSlot> slots = new ArrayList<>(existing.length);
        float epsilon = getEpsilon();
        for (EmotionSlot slot : existing) {
            if (slot == null) continue;
            if (slot.weight <= epsilon) continue;
            slots.add(slot);
        }
        slots.sort(
            Comparator.comparingInt((EmotionSlot s) -> s.parked ? 1 : 0)
                .thenComparingDouble(s -> -s.weight)
                .thenComparingLong(s -> -s.lastUpdatedTick)
        );
        for (int i = 0; i < targetCount && i < slots.size(); i++) {
            resized[i] = slots.get(i);
        }
        return resized;
    }

    // Config helpers
    private JsonObject moodsSection() {
        try {
            return woflo.petsplus.config.PetsPlusConfig.getInstance().getSection("moods");
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private double getMoodsDouble(String key, double def) {
        JsonObject o = moodsSection();
        if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) {
            try { return o.get(key).getAsDouble(); } catch (Exception ignored) {}
        }
        return def;
    }

    private int getMoodsInt(String key, int def) {
        JsonObject o = moodsSection();
        if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) {
            try { return o.get(key).getAsInt(); } catch (Exception ignored) {}
        }
        return def;
    }

    private boolean getMoodsBoolean(String key, boolean def) {
        JsonObject o = moodsSection();
        if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) {
            try { return o.get(key).getAsBoolean(); } catch (Exception ignored) {}
        }
        return def;
    }

    private float getEpsilon() {
        return (float) getMoodsDouble("epsilon", 0.05);
    }

    private double getPerEmotionDecay(PetComponent.Emotion emotion, double def) {
        JsonObject o = moodsSection();
        if (o != null && o.has("perEmotionDecay") && o.get("perEmotionDecay").isJsonObject()) {
            JsonObject m = o.getAsJsonObject("perEmotionDecay");
            String key = emotion.name().toLowerCase();
            if (m.has(key)) {
                try { return m.get(key).getAsDouble(); } catch (Exception ignored) {}
            }
        }
        return def;
    }

    private float[] getLevelThresholds() {
        float[] d = new float[]{0.33f, 0.66f, 0.85f};
        JsonObject o = moodsSection();
        if (o != null && o.has("levelThresholds") && o.get("levelThresholds").isJsonArray()) {
            var arr = o.getAsJsonArray("levelThresholds");
            for (int i = 0; i < Math.min(3, arr.size()); i++) {
                try { d[i] = arr.get(i).getAsFloat(); } catch (Exception ignored) {}
            }
            if (d[0] > 1f || d[1] > 1f || d[2] > 1f) {
                d = new float[]{0.33f, 0.66f, 0.85f};
            }
        }
        return d;
    }

    private JsonObject weightSection() {
        JsonObject moods = moodsSection();
        if (moods != null && moods.has("weight") && moods.get("weight").isJsonObject()) {
            return moods.getAsJsonObject("weight");
        }
        return null;
    }

    private float getWeightSaturationAlpha() {
        JsonObject weight = weightSection();
        if (weight != null && weight.has("saturationAlpha") && weight.get("saturationAlpha").isJsonPrimitive()) {
            try { return weight.get("saturationAlpha").getAsFloat(); } catch (Exception ignored) {}
        }
        return DEFAULT_WEIGHT_SATURATION_ALPHA;
    }

    private float getMaxWeight(PetComponent.Emotion emotion) {
        JsonObject weight = weightSection();
        float defaultMax = DEFAULT_MAX_EMOTION_WEIGHT;
        if (weight != null && weight.has("defaultMax") && weight.get("defaultMax").isJsonPrimitive()) {
            try { defaultMax = weight.get("defaultMax").getAsFloat(); } catch (Exception ignored) {}
        }
        if (emotion == null || weight == null) {
            return defaultMax;
        }
        if (weight.has("perEmotionMax") && weight.get("perEmotionMax").isJsonObject()) {
            JsonObject per = weight.getAsJsonObject("perEmotionMax");
            String key = emotion.name().toLowerCase();
            if (per.has(key) && per.get(key).isJsonPrimitive()) {
                try { return per.get(key).getAsFloat(); } catch (Exception ignored) {}
            }
        }
        return defaultMax;
    }

    private JsonObject evictionSection() {
        JsonObject moods = moodsSection();
        if (moods != null && moods.has("eviction") && moods.get("eviction").isJsonObject()) {
            return moods.getAsJsonObject("eviction");
        }
        return null;
    }

    private double getEvictionAgeCoeff() {
        JsonObject eviction = evictionSection();
        if (eviction != null && eviction.has("ageCoeff") && eviction.get("ageCoeff").isJsonPrimitive()) {
            try { return eviction.get("ageCoeff").getAsDouble(); } catch (Exception ignored) {}
        }
        return DEFAULT_EVICTION_AGE_COEFF;
    }

    private double getEvictionLowWeightCoeff() {
        JsonObject eviction = evictionSection();
        if (eviction != null && eviction.has("lowWeightCoeff") && eviction.get("lowWeightCoeff").isJsonPrimitive()) {
            try { return eviction.get("lowWeightCoeff").getAsDouble(); } catch (Exception ignored) {}
        }
        return DEFAULT_EVICTION_WEIGHT_COEFF;
    }

    private double getEvictionParkedBias() {
        JsonObject eviction = evictionSection();
        if (eviction != null && eviction.has("parkedBias") && eviction.get("parkedBias").isJsonPrimitive()) {
            try { return eviction.get("parkedBias").getAsDouble(); } catch (Exception ignored) {}
        }
        return DEFAULT_EVICTION_PARKED_BIAS;
    }

    private boolean useLastUpdatedForEviction() {
        JsonObject eviction = evictionSection();
        if (eviction != null && eviction.has("useLastUpdated") && eviction.get("useLastUpdated").isJsonPrimitive()) {
            try { return eviction.get("useLastUpdated").getAsBoolean(); } catch (Exception ignored) {}
        }
        return true;
    }

    private double getEvictionTypeBias(PetComponent.Emotion emotion) {
        JsonObject eviction = evictionSection();
        if (eviction != null && eviction.has("typeBias") && eviction.get("typeBias").isJsonObject() && emotion != null) {
            JsonObject map = eviction.getAsJsonObject("typeBias");
            String key = emotion.name().toLowerCase();
            if (map.has(key) && map.get(key).isJsonPrimitive()) {
                try { return map.get(key).getAsDouble(); } catch (Exception ignored) {}
            }
        }
        return 0d;
    }

    private Map<PetComponent.Mood, Float> getEmotionToMoodWeights(PetComponent.Emotion emotion) {
        JsonObject o = moodsSection();
        if (o != null && o.has("emotionToMoodWeights") && o.get("emotionToMoodWeights").isJsonObject()) {
            JsonObject map = o.getAsJsonObject("emotionToMoodWeights");
            String emoKey = emotion.name().toLowerCase();
            if (map.has(emoKey) && map.get(emoKey).isJsonObject()) {
                return parseMoodWeights(map.getAsJsonObject(emoKey));
            }
        }
        return defaultMoodWeights(emotion);
    }

    private Map<PetComponent.Mood, Float> parseMoodWeights(JsonObject json) {
        Map<PetComponent.Mood, Float> res = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            try {
                PetComponent.Mood m = PetComponent.Mood.valueOf(e.getKey().toUpperCase());
                float w = e.getValue().getAsFloat();
                if (w > 0) res.put(m, w);
            } catch (Exception ignored) {}
        }
        if (res.isEmpty()) res.put(PetComponent.Mood.ZEN, 1.0f);
        return res;
    }

    private Map<PetComponent.Mood, Float> defaultMoodWeights(PetComponent.Emotion e) {
        Map<PetComponent.Mood, Float> m = new HashMap<>();
        switch (e) {
            // Pure Joy & Happiness
            case FROHLICH, GLEE, KEFI -> m.put(PetComponent.Mood.JOYFUL, 1.0f);

            // Bonding & Connection
            case QUERENCIA, GEZELLIG, UBUNTU -> m.put(PetComponent.Mood.BONDED, 1.0f);

            // Calm & Peaceful
            case ANANDA, RELIEF -> m.put(PetComponent.Mood.ZEN, 1.0f);

            // Fear & Anxiety
            case ANGST, FOREBODING, STARTLE, DISGUST -> m.put(PetComponent.Mood.FEARFUL, 1.0f);

            // Protective Instincts (FIXED - no longer maps to wrathful)
            case PROTECTIVENESS -> m.put(PetComponent.Mood.PROTECTIVE, 1.0f);

            // Anger & Aggression
            case FRUSTRATION, WELTSCHMERZ -> m.put(PetComponent.Mood.WRATHFUL, 1.0f);

            // Longing & Melancholy
            case REGRET, SAUDADE, HIRAETH -> m.put(PetComponent.Mood.SAUDADE, 1.0f);

            // Curiosity & Wonder (NEW)
            case FERNWEH -> m.put(PetComponent.Mood.CURIOUS, 1.0f); // wanderlust = curiosity

            // Restless Energy (NEW)
            case AMAL -> m.put(PetComponent.Mood.RESTLESS, 1.0f); // hope/energy = restless drive

            // Playful Energy (NEW) - distribute some zen emotions to playful
            case FJELLVANT -> m.put(PetComponent.Mood.PLAYFUL, 1.0f); // mountain joy = playful

            // Contemplative & Mysterious
            case YUGEN, SOBREMESA, ENNUI -> m.put(PetComponent.Mood.YUGEN, 1.0f);

            // Musical/Ecstatic Joy
            case MONO_NO_AWARE -> m.put(PetComponent.Mood.TARAB, 1.0f); // bittersweet awareness = ecstatic

            // Resilience & Growth
            case SISU, GAMAN -> m.put(PetComponent.Mood.KINTSUGI, 1.0f);

            // Enthusiastic Energy
            case HANYAUKU -> m.put(PetComponent.Mood.ZEALOUS, 1.0f); // gratitude energy = zealous

            // Balanced/Content (reduced zen usage)
            case LAGOM, WABI_SABI -> m.put(PetComponent.Mood.ZEN, 1.0f);

            default -> m.put(PetComponent.Mood.ZEN, 1.0f);
        }
        return m;
    }

    private static String capitalize(String s) {
        String lc = s.toLowerCase();
        return Character.toUpperCase(lc.charAt(0)) + lc.substring(1);
    }

    private static String prettify(String enumName) {
        String s = enumName.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
