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

import woflo.petsplus.config.PetsPlusConfig;

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
    private static final float[] DEFAULT_LEVEL_THRESHOLDS = new float[]{0.20f, 0.40f, 0.60f}; // Higher thresholds for less sensitive mood levels
    private static final Map<PetComponent.Mood, Float> DEFAULT_CALM_WEIGHTS = singletonMoodMap(PetComponent.Mood.CALM);
    private static final EnumMap<PetComponent.Emotion, Map<PetComponent.Mood, Float>> DEFAULT_EMOTION_TO_MOOD_WEIGHTS = buildDefaultEmotionWeights();

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
    private PetComponent.Mood currentMood = PetComponent.Mood.CALM;
    private int moodLevel = 0;
    private long lastMoodUpdate = 0;
    private long lastEmotionDecayTick = 0;
    private int moodPendingCounter = 0;
    private PetComponent.Mood moodPendingCandidate = null;
    private EmotionSlot[] emotionSlots;

    private int cachedConfigGeneration = -1;
    private JsonObject cachedMoodsSection;
    private JsonObject cachedWeightSection;
    private JsonObject cachedPerEmotionDecaySection;
    private JsonObject cachedEmotionToMoodWeightsSection;
    private JsonObject cachedPerEmotionMaxSection;
    private JsonObject cachedEvictionSection;
    private JsonObject cachedEvictionTypeBiasSection;

    private int cachedSlotCount = DEFAULT_EMOTION_SLOT_COUNT;
    private int cachedDecayTickInterval = 40;
    private float cachedEpsilon = 0.05f;
    private double cachedDefaultDecayRate = 0.96d;
    private double cachedMomentum = 0.25d;
    private double cachedSwitchMargin = 0.05d;
    private int cachedHysteresisTicks = 60;
    private float[] cachedLevelThresholds = DEFAULT_LEVEL_THRESHOLDS.clone();
    private float cachedWeightSaturationAlpha = DEFAULT_WEIGHT_SATURATION_ALPHA;
    private float cachedDefaultMaxWeight = DEFAULT_MAX_EMOTION_WEIGHT;
    private double cachedEvictionAgeCoeff = DEFAULT_EVICTION_AGE_COEFF;
    private double cachedEvictionLowWeightCoeff = DEFAULT_EVICTION_WEIGHT_COEFF;
    private double cachedEvictionParkedBias = DEFAULT_EVICTION_PARKED_BIAS;
    private boolean cachedUseLastUpdatedForEviction = true;
    
    // Animation configuration
    private int cachedBaseAnimationUpdateInterval = 16; // Base interval in ticks (0.8 seconds at 20 TPS)
    private double cachedAnimationSpeedMultiplier = 0.15; // 15% faster per level
    private int cachedMinAnimationInterval = 4; // Minimum interval (0.2 seconds at 20 TPS)
    private int cachedMaxAnimationInterval = 40; // Maximum interval (2 seconds at 20 TPS)

    private final EnumMap<PetComponent.Emotion, Double> cachedPerEmotionDecay = new EnumMap<>(PetComponent.Emotion.class);
    private final EnumMap<PetComponent.Emotion, Float> cachedMaxWeight = new EnumMap<>(PetComponent.Emotion.class);
    private final EnumMap<PetComponent.Emotion, Map<PetComponent.Mood, Float>> cachedEmotionToMoodWeights = new EnumMap<>(PetComponent.Emotion.class);
    private final EnumMap<PetComponent.Emotion, Double> cachedEvictionTypeBias = new EnumMap<>(PetComponent.Emotion.class);

    // Text animation caching
    private Text cachedMoodText = null;
    private Text cachedMoodTextWithDebug = null;
    private long lastTextUpdateTime = -1;
    private int cachedLastLevel = -1; // Track last level used for caching

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

    // Debug API for emotion pool inspection
    public java.util.List<PetComponent.EmotionDebugInfo> getEmotionPoolDebug() {
        update();
        java.util.List<PetComponent.EmotionDebugInfo> result = new java.util.ArrayList<>();
        if (emotionSlots == null) return result;

        for (EmotionSlot slot : emotionSlots) {
            if (slot != null && slot.weight > 0.01f) {
                result.add(new PetComponent.EmotionDebugInfo(slot.emotion, slot.weight, slot.parked));
            }
        }
        return result;
    }

    void pushEmotion(PetComponent.Emotion emotion, float amount) {
        long now = parent.getPet().getWorld().getTime();
        float epsilon = getEpsilon();
        int bestFreeIdx = -1;
        int existingIdx = -1;
        EmotionSlot existing = null;

        for (int i = 0; i < emotionSlots.length; i++) {
            EmotionSlot slot = emotionSlots[i];
            if (slot != null && slot.emotion == emotion) {
                existing = slot;
                existingIdx = i;
                break;
            }
            if (bestFreeIdx >= 0) continue;
            if (slot == null || slot.parked || slot.weight <= epsilon) bestFreeIdx = i;
        }

        if (amount == 0f) {
            if (existingIdx >= 0) {
                emotionSlots[existingIdx] = null;
            }
            return;
        }

        if (amount < 0f) {
            if (existing != null) {
                existing.weight = Math.max(0f, existing.weight + amount);
                existing.lastUpdatedTick = now;
                if (existing.weight <= epsilon) {
                    emotionSlots[existingIdx] = null;
                } else {
                    existing.parked = false;
                }
            }
            return;
        }

        if (existing != null) {
            existing.weight = saturateWeight(existing.weight, amount, emotion);
            existing.lastUpdatedTick = now;
            existing.parked = false;
            return;
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
        PetComponent.Mood best = PetComponent.Mood.CALM;
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
        return getCachedMoodText(false);
    }
    
    private Text getCachedMoodText(boolean withDebug) {
        long currentTime = parent.getPet().getWorld().getTime();
        int level = getMoodLevel();
        int updateInterval = getAnimationUpdateInterval(level);
        
        // Check if we need to update the cached text
        // Force update if level changed to ensure animation speed matches displayed level
        boolean levelChanged = (cachedMoodText != null || cachedMoodTextWithDebug != null) && 
                              !isCurrentLevelMatchingCache(level);
        boolean needsUpdate = cachedMoodText == null || 
                            cachedMoodTextWithDebug == null ||
                            levelChanged ||
                            (currentTime - lastTextUpdateTime) >= updateInterval;
        
        if (needsUpdate) {
            lastTextUpdateTime = currentTime;
            // Pass the level to ensure consistent usage
            cachedMoodText = generateMoodText(false, level);
            cachedMoodTextWithDebug = generateMoodText(true, level);
            cachedLastLevel = level; // Store for level change detection
        }
        
        return withDebug ? cachedMoodTextWithDebug : cachedMoodText;
    }
    
    private boolean isCurrentLevelMatchingCache(int level) {
        return cachedLastLevel == level;
    }
    
    private Text generateMoodText(boolean withDebug) {
        return generateMoodText(withDebug, getMoodLevel());
    }
    
    private Text generateMoodText(boolean withDebug, int level) {
        PetComponent.Mood mood = getCurrentMood();

        boolean showEmotion = getMoodsBoolean("showDominantEmotion", false);
        String label = showEmotion ? getDominantEmotionName() : capitalize(mood.name());

        Formatting primary = mood.primaryFormatting;
        Formatting secondary = mood.secondaryFormatting;
        


        // Progressive display based on mood intensity level (0-3)
        Text baseText = switch (level) {
            case 0 -> {
                // Level 0: Full base color only
                yield Text.literal(label).styled(s -> s.withColor(TextColor.fromFormatting(primary)));
            }
            case 1 -> {
                // Level 1: Light breathing effect with secondary color
                yield createBreathingText(label, primary, secondary, 1);
            }
            case 2 -> {
                // Level 2: Gradient with breathing effect
                yield createBreathingGradient(label, primary, secondary, 2);
            }
            case 3 -> {
                // Level 3: Moving gradient with fast breathing
                yield createBreathingMovingGradient(label, primary, secondary, 3);
            }
            default -> Text.literal(label).styled(s -> s.withColor(TextColor.fromFormatting(primary)));
        };
        
        if (withDebug) {
            if (level == 0) {
                String debugLabel = label + " [" + level + "]";
                return Text.literal(debugLabel).styled(s -> s.withColor(TextColor.fromFormatting(primary)));
            } else {
                // For animated levels, add debug info to the animated text
                MutableText debugText = (MutableText) baseText;
                debugText.append(Text.literal(" [" + level + "]").styled(s -> s.withColor(TextColor.fromFormatting(Formatting.GRAY))));
                return debugText;
            }
        }
        
        return baseText;
    }

    /**
     * Get mood text with debug information showing power level.
     */
    Text getMoodTextWithDebug() {
        return getCachedMoodText(true);
    }

    /**
     * Create breathing text with dynamic intensity based on level.
     * Uses consistent template across all levels with increasing intensity and speed.
     */
    private MutableText createBreathingText(String text, Formatting primary, Formatting secondary, int level) {
        long worldTime = parent.getPet().getWorld().getTime();
        
        // Level-based animation speed: faster with higher levels
        // Level 1: 300 ticks, Level 2: 200 ticks, Level 3: 120 ticks
        int[] breathingSpeeds = {300, 200, 120};
        int breathingSpeed = breathingSpeeds[Math.min(level - 1, breathingSpeeds.length - 1)];
        
        // Use update interval for visual update smoothness  
        int updateInterval = getAnimationUpdateInterval(level);
        long animationTime = (worldTime / updateInterval) * updateInterval;
        
        // Breathing pattern based on actual breathing speed
        double breathingPhase = (animationTime % breathingSpeed) / (double) breathingSpeed;
        
        // Create smooth breathing curve (sine wave)
        double breathingIntensity = (Math.sin(breathingPhase * 2 * Math.PI) + 1) / 2; // 0 to 1
        
        // Level-based intensity thresholds - same template, increasing effect
        // Level 1: subtle (30-50%), Level 2: moderate (25-65%), Level 3: strong (15-75%)
        double[] minIntensities = {0.30, 0.25, 0.15};
        double[] maxIntensities = {0.50, 0.65, 0.75};
        
        int levelIndex = Math.min(level - 1, minIntensities.length - 1);
        double minIntensity = minIntensities[levelIndex];
        double maxIntensity = maxIntensities[levelIndex];
        double scaledIntensity = minIntensity + (breathingIntensity * (maxIntensity - minIntensity));
        
        MutableText out = Text.empty();
        char[] chars = text.toCharArray();
        
        // Use same template: breathing intensity determines color blend
        for (int i = 0; i < chars.length; i++) {
            // Per-character variation based on position (same for all levels)
            double charPhase = (i * 0.4) + (breathingPhase * 1.5);
            double charBreathing = (Math.sin(charPhase) + 1) / 2;
            
            // Combine global and character breathing
            double finalIntensity = (scaledIntensity + charBreathing * 0.3) / 1.3;
            
            // Same threshold logic across all levels
            boolean useSecondary = finalIntensity > 0.5;
            Formatting f = useSecondary ? secondary : primary;
            out.append(Text.literal(String.valueOf(chars[i])).styled(s -> s.withColor(TextColor.fromFormatting(f))));
        }
        
        return out;
    }

    /**
     * Calculate animation update interval based on level and configuration.
     * Higher levels result in faster animations (lower intervals).
     */
    private int getAnimationUpdateInterval(int level) {
        ensureConfigCache();
        
        // Calculate interval: base interval reduced by (level - 1) * speedMultiplier
        double interval = cachedBaseAnimationUpdateInterval - ((level - 1) * cachedAnimationSpeedMultiplier * cachedBaseAnimationUpdateInterval);
        
        // Clamp to min/max bounds
        int result = (int) Math.round(interval);
        return Math.max(cachedMinAnimationInterval, Math.min(cachedMaxAnimationInterval, result));
    }

    /**
     * Create breathing gradient with alternating pattern and breathing intensity.
     * Uses consistent template with increasing intensity and complexity.
     */
    private MutableText createBreathingGradient(String text, Formatting primary, Formatting secondary, int level) {
        long worldTime = parent.getPet().getWorld().getTime();
        
        // Level-based animation speed: faster with higher levels
        int[] breathingSpeeds = {280, 180, 100};
        int breathingSpeed = breathingSpeeds[Math.min(level - 1, breathingSpeeds.length - 1)];
        
        // Use update interval for visual update smoothness
        int updateInterval = getAnimationUpdateInterval(level);
        long animationTime = (worldTime / updateInterval) * updateInterval;
        
        // Breathing pattern 
        double breathingPhase = (animationTime % breathingSpeed) / (double) breathingSpeed;
        double breathingIntensity = (Math.sin(breathingPhase * 2 * Math.PI) + 1) / 2; // 0 to 1
        
        MutableText out = Text.empty();
        char[] chars = text.toCharArray();
        
        for (int i = 0; i < chars.length; i++) {
            // Base gradient pattern - same template for all levels
            boolean baseGradient = (i % 2 == 0);
            
            // Level-based breathing effect intensity
            // Level 1: subtle override (threshold 0.7), Level 2: moderate (0.6), Level 3: strong (0.5)
            double[] breathingThresholds = {0.70, 0.60, 0.50};
            int levelIndex = Math.min(level - 1, breathingThresholds.length - 1);
            double threshold = breathingThresholds[levelIndex];
            
            // Character-specific breathing variation (consistent template)
            double charPhase = (i * 0.6) + (breathingPhase * 3);
            double charIntensity = breathingIntensity + (Math.sin(charPhase) * 0.25);
            
            // Same decision logic template, but different thresholds by level
            boolean useBreathingOverride = charIntensity > threshold;
            
            Formatting f;
            if (useBreathingOverride) {
                f = secondary; // Breathing override color
            } else {
                f = baseGradient ? primary : secondary; // Base alternating gradient
            }
            
            out.append(Text.literal(String.valueOf(chars[i])).styled(s -> s.withColor(TextColor.fromFormatting(f))));
        }
        
        return out;
    }

    /**
     * Create breathing moving gradient with wave motion and breathing intensity.
     * Uses consistent template with increasing intensity and wave complexity.
     */
    private MutableText createBreathingMovingGradient(String text, Formatting primary, Formatting secondary, int level) {
        long worldTime = parent.getPet().getWorld().getTime();
        
        // Level-based animation speeds: faster with higher levels  
        int[] breathingSpeeds = {240, 150, 80};
        int[] waveSpeeds = {32, 20, 12};
        
        int levelIndex = Math.min(level - 1, breathingSpeeds.length - 1);
        int breathingSpeed = breathingSpeeds[levelIndex];
        int waveSpeed = waveSpeeds[levelIndex];
        
        // Use update interval for visual update smoothness
        int updateInterval = getAnimationUpdateInterval(level);
        long animationTime = (worldTime / updateInterval) * updateInterval;
        
        // Breathing pattern
        double breathingPhase = (animationTime % breathingSpeed) / (double) breathingSpeed;
        double globalBreathingIntensity = (Math.sin(breathingPhase * 2 * Math.PI) + 1) / 2;
        
        // Moving wave pattern - same template, different speeds
        int waveOffset = (int) (animationTime / waveSpeed) % 4;
        
        MutableText out = Text.empty();
        char[] chars = text.toCharArray();
        
        for (int i = 0; i < chars.length; i++) {
            // Base moving wave pattern (consistent template)
            boolean waveGradient = ((i + waveOffset) % 3) != 0;
            
            // Level-based breathing effect intensity  
            // Level 1: subtle (threshold 0.65), Level 2: moderate (0.55), Level 3: strong (0.4)
            double[] breathingThresholds = {0.65, 0.55, 0.40};
            double threshold = breathingThresholds[levelIndex];
            
            // Per-character breathing variation (consistent template)
            double charPhase = (i * 0.4) + (breathingPhase * 2.5);
            double charIntensity = (Math.sin(charPhase) + 1) / 2;
            
            // Combine breathing effects using level-specific threshold
            double combinedIntensity = (globalBreathingIntensity + charIntensity * 0.4) / 1.4;
            boolean useBreathingOverride = combinedIntensity > threshold;
            
            // Same decision template: wave gradient or breathing override
            Formatting f;
            if (useBreathingOverride) {
                f = secondary; // Breathing override
            } else {
                f = waveGradient ? secondary : primary; // Base wave pattern
            }
            
            out.append(Text.literal(String.valueOf(chars[i])).styled(s -> s.withColor(TextColor.fromFormatting(f))));
        }
        
        return out;
    }

    BossBar.Color getMoodBossBarColor() {
        return switch (getCurrentMood()) {
            case HAPPY -> BossBar.Color.YELLOW;       // Joyful brightness
            case AFRAID -> BossBar.Color.RED;         // Scared - danger signal
            case ANGRY -> BossBar.Color.RED;          // Mad - intense red band
            case SAUDADE -> BossBar.Color.BLUE;       // Nostalgic - deep blue
            case CALM -> BossBar.Color.GREEN;         // Peaceful - natural greens
            case PASSIONATE -> BossBar.Color.PINK;    // Enthusiastic - vibrant pinks
            case YUGEN -> BossBar.Color.PURPLE;       // Subtle awe - dusky purple
            case FOCUSED -> BossBar.Color.BLUE;       // Concentrated - crisp blue focus
            case SISU -> BossBar.Color.WHITE;         // Resilient resolve - tempered white
            case PLAYFUL -> BossBar.Color.GREEN;      // Energetic fun - lively green
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
                catch (IllegalArgumentException e) { currentMood = PetComponent.Mood.CALM; }
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
                    s.decayRate = snbt.getFloat("d").orElse((float) getDefaultDecayRate());
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
                float rate = slot.decayRate > 0 ? slot.decayRate : (float) getDefaultDecayRate();
                slot.weight *= rate;
                if (slot.weight < epsilon) { emotionSlots[i] = null; }
            }
        }

        if (now - lastMoodUpdate < 20) return;
        lastMoodUpdate = now;

        // Get active emotion slots and sort by weight
        List<EmotionSlot> activeSlots = new ArrayList<>();
        float epsilon = getEpsilon();
        for (EmotionSlot slot : emotionSlots) {
            if (slot != null && !slot.parked && slot.weight > epsilon) {
                activeSlots.add(slot);
            }
        }
        
        // Sort by weight descending
        activeSlots.sort((a, b) -> Float.compare(b.weight, a.weight));
        
        // Only consider top N slots for mood calculation, where N = slotCount - 1 (exclude only the weakest)
        int topSlotsToConsider = Math.max(1, configuredSlotCount - 1);
        List<EmotionSlot> topSlots = activeSlots.subList(0, Math.min(topSlotsToConsider, activeSlots.size()));

        float[] moodScores = new float[PetComponent.Mood.values().length];
        for (EmotionSlot slot : topSlots) {
            Map<PetComponent.Mood, Float> weights = getEmotionToMoodWeights(slot.emotion);
            for (Map.Entry<PetComponent.Mood, Float> e : weights.entrySet()) {
                moodScores[e.getKey().ordinal()] += slot.weight * e.getValue();
            }
        }
        float sum = 0f;
        for (float s : moodScores) sum += Math.max(0f, s);
        EnumMap<PetComponent.Mood, Float> target = new EnumMap<>(PetComponent.Mood.class);
        if (sum <= epsilon) {
            // When no emotions are active, provide a small baseline distributed across all moods
            // This prevents complete mood decay while avoiding heavy CALM bias
            float baselineStrength = 0.1f; // Small baseline to maintain some mood presence
            int moodCount = PetComponent.Mood.values().length;
            float perMoodBaseline = baselineStrength / moodCount;
            
            for (PetComponent.Mood m : PetComponent.Mood.values()) {
                target.put(m, perMoodBaseline);
            }
        } else {
            for (int i = 0; i < moodScores.length; i++) {
                target.put(PetComponent.Mood.values()[i], Math.max(0f, moodScores[i]) / sum);
            }
        }

        float momentum = (float) getMoodsDouble("momentum", 0.5); // Increased from 0.25 to 0.5 for more responsive mood changes
        if (moodBlend == null) moodBlend = new EnumMap<>(PetComponent.Mood.class);
        for (PetComponent.Mood m : PetComponent.Mood.values()) {
            // Initialize all moods equally instead of biasing toward CALM
            float cur = moodBlend.getOrDefault(m, 0f);
            float tar = target.getOrDefault(m, 0f);
            float next = cur + (tar - cur) * Math.max(0f, Math.min(1f, momentum));
            moodBlend.put(m, next);
        }

        PetComponent.Mood bestMood = null;
        float bestVal = -1f;
        for (Map.Entry<PetComponent.Mood, Float> e : moodBlend.entrySet()) {
            if (e.getValue() > bestVal) { bestVal = e.getValue(); bestMood = e.getKey(); }
        }
        
        // Only fallback to CALM if no mood has any strength
        if (bestMood == null || bestVal <= 0f) {
            bestMood = PetComponent.Mood.CALM;
            bestVal = 0f;
        }

        double switchMargin = getMoodsDouble("switchMargin", 0.05); // Reduced from 0.15 to 0.05 for easier mood transitions
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
        
        // Calculate level based on how much above baseline the mood strength is
        // When no emotions are active, baseline is 0.1f / moodCount (~0.007 for 14 moods)
        // But momentum keeps values higher, so we need to account for this
        
        // Check if we have any active emotions with significant weight
        boolean hasActiveEmotions = false;
        float totalEmotionWeight = 0f;
        for (EmotionSlot slot : emotionSlots) {
            if (slot != null && !slot.parked && slot.weight > epsilon) {
                hasActiveEmotions = true;
                totalEmotionWeight += slot.weight;
            }
        }
        
        if (!hasActiveEmotions || totalEmotionWeight < epsilon) {
            // No active emotions - should be level 0 regardless of momentum artifacts
            level = 0;
        } else {
            // Has active emotions - use normal threshold calculation
            // But adjust thresholds to account for the fact that bestVal is a blend proportion, not raw emotion weight
            if (bestVal >= thresholds[2]) level = 3;
            else if (bestVal >= thresholds[1]) level = 2;
            else if (bestVal >= thresholds[0]) level = 1;
            else level = 0;
        }
        
        this.moodLevel = level;
    }

    // helpers
    private EmotionSlot createSlot(PetComponent.Emotion emotion, float amount, long now) {
        EmotionSlot s = new EmotionSlot();
        s.emotion = emotion;
        s.weight = saturateWeight(0f, amount, emotion);
        s.decayRate = (float) getPerEmotionDecay(emotion);
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
    private void ensureConfigCache() {
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        int generation = config.getConfigGeneration();
        if (generation == cachedConfigGeneration && cachedMoodsSection != null) {
            return;
        }

        cachedConfigGeneration = generation;
        cachedEmotionToMoodWeights.clear();
        cachedPerEmotionDecay.clear();
        cachedMaxWeight.clear();
        cachedEvictionTypeBias.clear();

        cachedMoodsSection = config.getSection("moods");
        cachedSlotCount = readInt(cachedMoodsSection, "slotCount", DEFAULT_EMOTION_SLOT_COUNT);
        cachedDecayTickInterval = readInt(cachedMoodsSection, "decayTickInterval", 40);
        cachedEpsilon = (float) readDouble(cachedMoodsSection, "epsilon", 0.05);
        cachedDefaultDecayRate = readDouble(cachedMoodsSection, "defaultDecayRate", 0.96);
        cachedMomentum = readDouble(cachedMoodsSection, "momentum", 0.25);
        cachedSwitchMargin = readDouble(cachedMoodsSection, "switchMargin", 0.05);
        cachedHysteresisTicks = readInt(cachedMoodsSection, "hysteresisTicks", 60);
        cachedLevelThresholds = parseLevelThresholds(cachedMoodsSection);

        // Animation configuration
        cachedBaseAnimationUpdateInterval = readInt(cachedMoodsSection, "baseAnimationUpdateInterval", 16);
        cachedAnimationSpeedMultiplier = readDouble(cachedMoodsSection, "animationSpeedMultiplier", 0.15);
        cachedMinAnimationInterval = readInt(cachedMoodsSection, "minAnimationInterval", 4);
        cachedMaxAnimationInterval = readInt(cachedMoodsSection, "maxAnimationInterval", 40);

        cachedPerEmotionDecaySection = getObject(cachedMoodsSection, "perEmotionDecay");
        cachedEmotionToMoodWeightsSection = getObject(cachedMoodsSection, "emotionToMoodWeights");

        cachedWeightSection = getObject(cachedMoodsSection, "weight");
        cachedWeightSaturationAlpha = readFloat(cachedWeightSection, "saturationAlpha", DEFAULT_WEIGHT_SATURATION_ALPHA);
        cachedDefaultMaxWeight = readFloat(cachedWeightSection, "defaultMax", DEFAULT_MAX_EMOTION_WEIGHT);
        cachedPerEmotionMaxSection = getObject(cachedWeightSection, "perEmotionMax");

        cachedEvictionSection = getObject(cachedMoodsSection, "eviction");
        cachedEvictionAgeCoeff = readDouble(cachedEvictionSection, "ageCoeff", DEFAULT_EVICTION_AGE_COEFF);
        cachedEvictionLowWeightCoeff = readDouble(cachedEvictionSection, "lowWeightCoeff", DEFAULT_EVICTION_WEIGHT_COEFF);
        cachedEvictionParkedBias = readDouble(cachedEvictionSection, "parkedBias", DEFAULT_EVICTION_PARKED_BIAS);
        cachedUseLastUpdatedForEviction = readBoolean(cachedEvictionSection, "useLastUpdated", true);
        cachedEvictionTypeBiasSection = getObject(cachedEvictionSection, "typeBias");
    }

    private double getMoodsDouble(String key, double def) {
        ensureConfigCache();
        return switch (key) {
            case "epsilon" -> cachedEpsilon;
            case "defaultDecayRate" -> cachedDefaultDecayRate;
            case "momentum" -> cachedMomentum;
            case "switchMargin" -> cachedSwitchMargin;
            case "animationSpeedMultiplier" -> cachedAnimationSpeedMultiplier;
            default -> readDouble(cachedMoodsSection, key, def);
        };
    }

    private int getMoodsInt(String key, int def) {
        ensureConfigCache();
        return switch (key) {
            case "slotCount" -> cachedSlotCount;
            case "decayTickInterval" -> cachedDecayTickInterval;
            case "hysteresisTicks" -> cachedHysteresisTicks;
            case "baseAnimationUpdateInterval" -> cachedBaseAnimationUpdateInterval;
            case "minAnimationInterval" -> cachedMinAnimationInterval;
            case "maxAnimationInterval" -> cachedMaxAnimationInterval;
            default -> readInt(cachedMoodsSection, key, def);
        };
    }

    private boolean getMoodsBoolean(String key, boolean def) {
        ensureConfigCache();
        return readBoolean(cachedMoodsSection, key, def);
    }

    private float getEpsilon() {
        ensureConfigCache();
        return cachedEpsilon;
    }

    private double getPerEmotionDecay(PetComponent.Emotion emotion) {
        ensureConfigCache();
        if (emotion == null) {
            return cachedDefaultDecayRate;
        }
        return cachedPerEmotionDecay.computeIfAbsent(emotion, emo -> {
            if (cachedPerEmotionDecaySection != null) {
                String key = emo.name().toLowerCase();
                if (cachedPerEmotionDecaySection.has(key) && cachedPerEmotionDecaySection.get(key).isJsonPrimitive()) {
                    try { return cachedPerEmotionDecaySection.get(key).getAsDouble(); } catch (Exception ignored) {}
                }
            }
            return cachedDefaultDecayRate;
        });
    }

    private double getDefaultDecayRate() {
        ensureConfigCache();
        return cachedDefaultDecayRate;
    }

    private float[] getLevelThresholds() {
        ensureConfigCache();
        return cachedLevelThresholds.clone();
    }

    private float getWeightSaturationAlpha() {
        ensureConfigCache();
        return cachedWeightSaturationAlpha;
    }

    private float getMaxWeight(PetComponent.Emotion emotion) {
        ensureConfigCache();
        if (emotion == null) {
            return cachedDefaultMaxWeight;
        }
        return cachedMaxWeight.computeIfAbsent(emotion, this::lookupMaxWeight);
    }

    private double getEvictionAgeCoeff() {
        ensureConfigCache();
        return cachedEvictionAgeCoeff;
    }

    private double getEvictionLowWeightCoeff() {
        ensureConfigCache();
        return cachedEvictionLowWeightCoeff;
    }

    private double getEvictionParkedBias() {
        ensureConfigCache();
        return cachedEvictionParkedBias;
    }

    private boolean useLastUpdatedForEviction() {
        ensureConfigCache();
        return cachedUseLastUpdatedForEviction;
    }

    private double getEvictionTypeBias(PetComponent.Emotion emotion) {
        ensureConfigCache();
        if (emotion == null) {
            return 0d;
        }
        return cachedEvictionTypeBias.computeIfAbsent(emotion, this::lookupEvictionTypeBias);
    }

    private Map<PetComponent.Mood, Float> getEmotionToMoodWeights(PetComponent.Emotion emotion) {
        ensureConfigCache();
        if (emotion == null) {
            return DEFAULT_CALM_WEIGHTS;
        }
        return cachedEmotionToMoodWeights.computeIfAbsent(emotion, this::lookupEmotionToMoodWeights);
    }

    private Map<PetComponent.Mood, Float> parseMoodWeights(JsonObject json) {
        EnumMap<PetComponent.Mood, Float> res = new EnumMap<>(PetComponent.Mood.class);
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            try {
                PetComponent.Mood m = PetComponent.Mood.valueOf(e.getKey().toUpperCase());
                float w = e.getValue().getAsFloat();
                if (w > 0f) {
                    res.put(m, w);
                }
            } catch (Exception ignored) {}
        }
        if (res.isEmpty()) {
            return DEFAULT_CALM_WEIGHTS;
        }
        return Collections.unmodifiableMap(res);
    }

    private Map<PetComponent.Mood, Float> defaultMoodWeights(PetComponent.Emotion emotion) {
        return DEFAULT_EMOTION_TO_MOOD_WEIGHTS.getOrDefault(emotion, DEFAULT_CALM_WEIGHTS);
    }

    private float[] parseLevelThresholds(JsonObject moods) {
        float[] thresholds = DEFAULT_LEVEL_THRESHOLDS.clone();
        if (moods != null && moods.has("levelThresholds") && moods.get("levelThresholds").isJsonArray()) {
            var arr = moods.getAsJsonArray("levelThresholds");
            for (int i = 0; i < Math.min(3, arr.size()); i++) {
                try { thresholds[i] = arr.get(i).getAsFloat(); } catch (Exception ignored) {}
            }
            if (thresholds[0] > 1f || thresholds[1] > 1f || thresholds[2] > 1f) {
                thresholds = DEFAULT_LEVEL_THRESHOLDS.clone();
            }
        }
        return thresholds;
    }

    private float lookupMaxWeight(PetComponent.Emotion emotion) {
        if (cachedPerEmotionMaxSection != null) {
            String key = emotion.name().toLowerCase();
            if (cachedPerEmotionMaxSection.has(key) && cachedPerEmotionMaxSection.get(key).isJsonPrimitive()) {
                try { return cachedPerEmotionMaxSection.get(key).getAsFloat(); } catch (Exception ignored) {}
            }
        }
        return cachedDefaultMaxWeight;
    }

    private double lookupEvictionTypeBias(PetComponent.Emotion emotion) {
        if (cachedEvictionTypeBiasSection != null) {
            String key = emotion.name().toLowerCase();
            if (cachedEvictionTypeBiasSection.has(key) && cachedEvictionTypeBiasSection.get(key).isJsonPrimitive()) {
                try { return cachedEvictionTypeBiasSection.get(key).getAsDouble(); } catch (Exception ignored) {}
            }
        }
        return 0d;
    }

    private Map<PetComponent.Mood, Float> lookupEmotionToMoodWeights(PetComponent.Emotion emotion) {
        if (cachedEmotionToMoodWeightsSection != null) {
            String key = emotion.name().toLowerCase();
            if (cachedEmotionToMoodWeightsSection.has(key) && cachedEmotionToMoodWeightsSection.get(key).isJsonObject()) {
                return parseMoodWeights(cachedEmotionToMoodWeightsSection.getAsJsonObject(key));
            }
        }
        return defaultMoodWeights(emotion);
    }

    private static double readDouble(JsonObject obj, String key, double def) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try { return obj.get(key).getAsDouble(); } catch (Exception ignored) {}
        }
        return def;
    }

    private static int readInt(JsonObject obj, String key, int def) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try { return obj.get(key).getAsInt(); } catch (Exception ignored) {}
        }
        return def;
    }

    private static float readFloat(JsonObject obj, String key, float def) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try { return obj.get(key).getAsFloat(); } catch (Exception ignored) {}
        }
        return def;
    }

    private static boolean readBoolean(JsonObject obj, String key, boolean def) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try { return obj.get(key).getAsBoolean(); } catch (Exception ignored) {}
        }
        return def;
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        if (parent != null && parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        return null;
    }

    private static EnumMap<PetComponent.Emotion, Map<PetComponent.Mood, Float>> buildDefaultEmotionWeights() {
        EnumMap<PetComponent.Emotion, Map<PetComponent.Mood, Float>> weights = new EnumMap<>(PetComponent.Emotion.class);
        weights.put(PetComponent.Emotion.CHEERFUL, moodWeights(
            PetComponent.Mood.HAPPY, 0.75f,
            PetComponent.Mood.PLAYFUL, 0.25f
        ));
        weights.put(PetComponent.Emotion.QUERECIA, moodWeights(
            PetComponent.Mood.BONDED, 0.45f,
            PetComponent.Mood.CALM, 0.55f
        ));
        weights.put(PetComponent.Emotion.GLEE, moodWeights(
            PetComponent.Mood.PLAYFUL, 0.7f,
            PetComponent.Mood.HAPPY, 0.3f
        ));
        weights.put(PetComponent.Emotion.BLISSFUL, moodWeights(
            PetComponent.Mood.CALM, 0.7f,
            PetComponent.Mood.HAPPY, 0.3f
        ));
        weights.put(PetComponent.Emotion.UBUNTU, moodWeights(
            PetComponent.Mood.BONDED, 0.4f,
            PetComponent.Mood.PROTECTIVE, 0.6f
        ));
        weights.put(PetComponent.Emotion.KEFI, moodWeights(
            PetComponent.Mood.PASSIONATE, 0.65f,
            PetComponent.Mood.PLAYFUL, 0.35f
        ));
        weights.put(PetComponent.Emotion.ANGST, moodWeights(
            PetComponent.Mood.AFRAID, 0.55f,
            PetComponent.Mood.RESTLESS, 0.45f
        ));
        weights.put(PetComponent.Emotion.FOREBODING, moodWeights(
            PetComponent.Mood.AFRAID, 0.7f,
            PetComponent.Mood.PROTECTIVE, 0.3f
        ));
        weights.put(PetComponent.Emotion.PROTECTIVENESS, moodWeights(
            PetComponent.Mood.PROTECTIVE, 0.7f,
            PetComponent.Mood.BONDED, 0.3f
        ));
        weights.put(PetComponent.Emotion.FRUSTRATION, moodWeights(
            PetComponent.Mood.ANGRY, 0.7f,
            PetComponent.Mood.RESTLESS, 0.3f
        ));
        weights.put(PetComponent.Emotion.STARTLE, moodWeights(
            PetComponent.Mood.AFRAID, 0.7f,
            PetComponent.Mood.RESTLESS, 0.3f
        ));
        weights.put(PetComponent.Emotion.DISGUST, moodWeights(
            PetComponent.Mood.ANGRY, 0.65f,
            PetComponent.Mood.PROTECTIVE, 0.35f
        ));
        weights.put(PetComponent.Emotion.REGRET, moodWeights(
            PetComponent.Mood.SAUDADE, 0.7f,
            PetComponent.Mood.CALM, 0.3f
        ));
        weights.put(PetComponent.Emotion.MONO_NO_AWARE, moodWeights(
            PetComponent.Mood.YUGEN, 0.65f,
            PetComponent.Mood.SAUDADE, 0.35f
        ));
        weights.put(PetComponent.Emotion.FERNWEH, moodWeights(
            PetComponent.Mood.CURIOUS, 0.65f,
            PetComponent.Mood.SAUDADE, 0.35f
        ));
        weights.put(PetComponent.Emotion.SOBREMESA, moodWeights(
            PetComponent.Mood.BONDED, 0.5f,
            PetComponent.Mood.CALM, 0.5f
        ));
        weights.put(PetComponent.Emotion.HANYAUKU, moodWeights(
            PetComponent.Mood.PLAYFUL, 0.7f,
            PetComponent.Mood.HAPPY, 0.3f
        ));
        weights.put(PetComponent.Emotion.WABI_SABI, moodWeights(
            PetComponent.Mood.YUGEN, 0.6f,
            PetComponent.Mood.CALM, 0.4f
        ));
        weights.put(PetComponent.Emotion.LAGOM, moodWeights(
            PetComponent.Mood.CALM, 0.6f,
            PetComponent.Mood.FOCUSED, 0.4f
        ));
        weights.put(PetComponent.Emotion.ENNUI, moodWeights(
            PetComponent.Mood.RESTLESS, 0.65f,
            PetComponent.Mood.SAUDADE, 0.35f
        ));
        weights.put(PetComponent.Emotion.YUGEN, moodWeights(
            PetComponent.Mood.YUGEN, 0.7f,
            PetComponent.Mood.CURIOUS, 0.3f
        ));
        weights.put(PetComponent.Emotion.SAUDADE, moodWeights(
            PetComponent.Mood.SAUDADE, 0.7f,
            PetComponent.Mood.BONDED, 0.3f
        ));
        weights.put(PetComponent.Emotion.HIRAETH, moodWeights(
            PetComponent.Mood.SAUDADE, 0.65f,
            PetComponent.Mood.BONDED, 0.35f
        ));
        weights.put(PetComponent.Emotion.STOIC, moodWeights(
            PetComponent.Mood.SISU, 0.6f,
            PetComponent.Mood.FOCUSED, 0.4f
        ));
        weights.put(PetComponent.Emotion.HOPEFUL, moodWeights(
            PetComponent.Mood.PASSIONATE, 0.55f,
            PetComponent.Mood.SISU, 0.45f
        ));
        weights.put(PetComponent.Emotion.RELIEF, moodWeights(
            PetComponent.Mood.CALM, 0.6f,
            PetComponent.Mood.HAPPY, 0.4f
        ));
        weights.put(PetComponent.Emotion.GAMAN, moodWeights(
            PetComponent.Mood.SISU, 0.7f,
            PetComponent.Mood.FOCUSED, 0.3f
        ));
        weights.put(PetComponent.Emotion.CURIOUS, moodWeights(
            PetComponent.Mood.CURIOUS, 0.65f,
            PetComponent.Mood.PLAYFUL, 0.35f
        ));
        weights.put(PetComponent.Emotion.SISU, moodWeights(
            PetComponent.Mood.SISU, 0.7f,
            PetComponent.Mood.FOCUSED, 0.3f
        ));
        weights.put(PetComponent.Emotion.FOCUSED, moodWeights(
            PetComponent.Mood.FOCUSED, 0.8f,
            PetComponent.Mood.CALM, 0.2f
        ));
        weights.put(PetComponent.Emotion.PRIDE, moodWeights(
            PetComponent.Mood.PASSIONATE, 0.6f,
            PetComponent.Mood.HAPPY, 0.4f
        ));
        weights.put(PetComponent.Emotion.VIGILANT, moodWeights(
            PetComponent.Mood.FOCUSED, 0.7f,
            PetComponent.Mood.PROTECTIVE, 0.3f
        ));
        weights.put(PetComponent.Emotion.WORRIED, moodWeights(
            PetComponent.Mood.AFRAID, 0.6f,
            PetComponent.Mood.PROTECTIVE, 0.4f
        ));
        weights.put(PetComponent.Emotion.PROTECTIVE, moodWeights(
            PetComponent.Mood.PROTECTIVE, 0.8f,
            PetComponent.Mood.FOCUSED, 0.2f
        ));
        weights.put(PetComponent.Emotion.MELANCHOLY, moodWeights(
            PetComponent.Mood.SAUDADE, 0.7f,
            PetComponent.Mood.CALM, 0.3f
        ));
        weights.put(PetComponent.Emotion.CONTENT, moodWeights(
            PetComponent.Mood.CALM, 0.6f,
            PetComponent.Mood.HAPPY, 0.4f
        ));
        weights.put(PetComponent.Emotion.RESTLESS, moodWeights(
            PetComponent.Mood.RESTLESS, 0.8f,
            PetComponent.Mood.CURIOUS, 0.2f
        ));
        weights.put(PetComponent.Emotion.EMPATHY, moodWeights(
            PetComponent.Mood.BONDED, 0.6f,
            PetComponent.Mood.PROTECTIVE, 0.4f
        ));
        weights.put(PetComponent.Emotion.NOSTALGIA, moodWeights(
            PetComponent.Mood.SAUDADE, 0.7f,
            PetComponent.Mood.YUGEN, 0.3f
        ));
        weights.put(PetComponent.Emotion.PLAYFULNESS, moodWeights(
            PetComponent.Mood.PLAYFUL, 0.8f,
            PetComponent.Mood.HAPPY, 0.2f
        ));
        weights.put(PetComponent.Emotion.LOYALTY, moodWeights(
            PetComponent.Mood.BONDED, 0.7f,
            PetComponent.Mood.PROTECTIVE, 0.3f
        ));
        for (PetComponent.Emotion emotion : PetComponent.Emotion.values()) {
            weights.putIfAbsent(emotion, DEFAULT_CALM_WEIGHTS);
        }
        return weights;
    }

    private static Map<PetComponent.Mood, Float> moodWeights(Object... args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Mood weights must be provided as mood/weight pairs");
        }
        EnumMap<PetComponent.Mood, Float> map = new EnumMap<>(PetComponent.Mood.class);
        float sum = 0f;
        for (int i = 0; i < args.length; i += 2) {
            Object moodObj = args[i];
            Object weightObj = args[i + 1];
            if (!(moodObj instanceof PetComponent.Mood mood) || !(weightObj instanceof Number number)) {
                continue;
            }
            float weight = number.floatValue();
            if (weight <= 0f) {
                continue;
            }
            map.put(mood, weight);
            sum += weight;
        }
        if (map.isEmpty()) {
            return DEFAULT_CALM_WEIGHTS;
        }
        EnumMap<PetComponent.Mood, Float> normalized = new EnumMap<>(PetComponent.Mood.class);
        float normalizer = sum > 0f ? (1f / sum) : 1f;
        for (Map.Entry<PetComponent.Mood, Float> entry : map.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() * normalizer);
        }
        return Collections.unmodifiableMap(normalized);
    }

    private static Map<PetComponent.Mood, Float> singletonMoodMap(PetComponent.Mood mood) {
        EnumMap<PetComponent.Mood, Float> map = new EnumMap<>(PetComponent.Mood.class);
        map.put(mood, 1.0f);
        return Collections.unmodifiableMap(map);
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
