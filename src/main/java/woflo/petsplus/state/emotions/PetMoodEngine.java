package woflo.petsplus.state.emotions;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.traits.SpeciesTraits;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.config.DebugSettings;
import woflo.petsplus.config.MoodEngineConfig;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.processing.AsyncWorkCoordinator;
import woflo.petsplus.ui.UIStyle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ReflectiveOperationException;

/**
 * Sophisticated emotional intelligence system for Minecraft pets that creates realistic,
 * personality-driven emotional responses and mood dynamics.
 *
 * <h2>System Architecture</h2>
 * <p>The engine implements a multi-layered emotional model:</p>
 * <ul>
 *   <li><b>Emotion Layer:</b> Lightweight records tracking intensity, impact, cadence, volatility,
 *       and contextual modulation for each active emotion (e.g., CHEERFUL, ANGST, SAUDADE)</li>
 *   <li><b>Mood Layer:</b> Weighted blends of emotions mapped to observable moods (e.g., HAPPY,
 *       PROTECTIVE, YUGEN) with smooth transitions and momentum-based stabilization</li>
 *   <li><b>Nature Integration:</b> Pet personality traits (from Natures like Lunaris, Abstract, Solace)
 *       modulate stimulus response, decay rates, habituation, and emotional expression</li>
 *   <li><b>Context Awareness:</b> Bond strength, danger proximity, health status, and ongoing
 *       conditions dynamically adjust emotional weights for situational realism</li>
 * </ul>
 *
 * <h2>Psychological Realism Features</h2>
 * <ul>
 *   <li><b>Habituation & Sensitization:</b> Repeated stimuli reduce sensitivity (habituation),
 *       while breaks re-sensitize pets to those emotions</li>
 *   <li><b>Negativity Bias:</b> Negative emotions (fear, regret) persist 2-3x longer than positive
 *       ones, mirroring real psychological threat-memory bias</li>
 *   <li><b>Condition-Aware Decay:</b> Emotions decay slower when triggering conditions remain
 *       (e.g., loneliness persists while owner is absent)</li>
 *   <li><b>Opponent Dynamics:</b> Conflicting emotions (joy vs sadness) suppress each other with
 *       nature-modulated transfer rates and resilience-based rebounds</li>
 *   <li><b>Adaptive Momentum:</b> Fresh spikes switch moods fast (responsive), persistent states
 *       drift slowly (stable), preventing emotional whiplash</li>
 *   <li><b>Buildup & Hysteresis:</b> Progressive resistance to level increases (must "earn" higher
 *       intensity), easier descent (smooth emotional release)</li>
 * </ul>
 *
 * <h2>Performance Optimizations</h2>
 * <ul>
 *   <li>Incremental level history tracking with O(1) amortized updates</li>
 *   <li>Quickselect for O(n) quantile calculations vs O(n log n) sorting</li>
 *   <li>Lazy config caching with generation tracking to avoid redundant parsing</li>
 *   <li>Smart update scheduling based on next emotion decay estimate</li>
 * </ul>
 *
 * <p>The result: Minecraft pets that feel alive, remember past experiences, develop unique
 * personalities, and respond authentically to their emotional journey with their owner.</p>
 */
public final class PetMoodEngine {
    private static final float EPSILON = 0.01f;
    private static final float DEFAULT_INTENSITY = 0f;
    private static final float DEFAULT_IMPACT_CAP = 4.0f;
    private static final float DEFAULT_WEIGHT_CAP = 6.0f;
    private static final float CADENCE_ALPHA = 0.35f;
    private static final float VOLATILITY_ALPHA = 0.25f;
    private static final float PEAK_ALPHA = 0.18f;
    private static final float HARMONY_BIAS_ALPHA = 0.35f;
    private static final float HABITUATION_BASE = 1200f;
    private static final float HALF_LIFE_MULTIPLIER = 1.35f;
    private static final float MIN_HALF_LIFE = 400f;
    private static final float MAX_HALF_LIFE = 3600f;
    private static final float HOMEOSTASIS_RECOVERY_HALF = 2400f;
    private static final float RELATIONSHIP_BASE = 1.0f;
    private static final float DANGER_BASE = 1.0f;
    private static final float APPRAISAL_BASE = 0.85f;
    // Novelty parameters for future emotional dynamics
    // private static final float NOVELTY_MIN = 0.05f;
    // private static final float NOVELTY_MAX = 0.35f;
    // private static final float NOVELTY_HALF_LIFE_FRACTION = 0.65f;
    private static final int MOMENTUM_HISTORY_SIZE = 10;
    private static final float OPPONENT_TRANSFER_MAX = 0.20f; // Phase 2 tuning
    private static final float REBOUND_GAIN = 0.12f; // Phase 2 tuning
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
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    };

    /**
     * Authored default emotion-to-mood mapping derived from the narrative clusters described in the
     * impact-weighted mood story. Each entry intentionally highlights the moods that should dominate
     * when the corresponding emotion is active so config-less installations still surface expressive
     * behaviour designers expect. The values are normalised so maintainers can audit or tweak the
     * blend coefficients without hunting through code paths.
     */
    private static final EnumMap<PetComponent.Emotion, EnumMap<PetComponent.Mood, Float>>
            DEFAULT_EMOTION_TO_MOOD = buildDefaultEmotionToMoodTable();

    private static volatile int cachedArcaneAmbientConfigGeneration = -1;
    private static final ConcurrentMap<String, Optional<Field>> ARCANE_REFLECTION_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Optional<Method>> ARCANE_REFLECTION_METHOD_CACHE = new ConcurrentHashMap<>();

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
    private Map<PetComponent.Mood, Float> cachedMoodBlendView = Collections.emptyMap();
    private Map<PetComponent.Emotion, Float> cachedActiveEmotionsView = Collections.emptyMap();
    private boolean moodBlendDirty = true;
    private boolean activeEmotionsDirty = true;
    private boolean dominantEmotionDirty = true;
    @Nullable
    private PetComponent.Emotion cachedDominantEmotion = null;
    @Nullable
    private PetComponent.Mood previousMoodSnapshot = null;

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

    private float[] scratchBudgets = new float[16];
    private int scratchBudgetCount = 0;

    private PetComponent.Mood currentMood = PetComponent.Mood.CALM;
    private int moodLevel = 0;
    private long lastMoodUpdate = 0L;
    @SuppressWarnings("unused") // Reserved for future stimulus timing optimization
    private long lastStimulusTime = 0L;
    private boolean dirty = false;
    private boolean asyncComputationInFlight = false;
    private boolean asyncRecomputeRequested = false;
    private long asyncRecomputeTimestamp = -1L;
    private boolean discardNextAsyncResult = false;
    private long lastAppliedTick = -1L;
    private final ArrayDeque<PendingApplyCallback> postApplyCallbacks = new ArrayDeque<>();
    
    // Behavioral Momentum - tracks activity level for AI regulation
    private float behavioralMomentum = 0.5f;           // 0=still, 1=hyperactive
    private float momentumInertia = 0f;                // Smoothing factor for momentum changes
    private long lastMomentumUpdate = 0L;

    // Multi-dimensional activity tracking for realistic behavior
    private float physicalActivity = 0f;               // Running, jumping, playing
    private float mentalActivity = 0f;                 // Puzzles, searching, tracking
    private float socialActivity = 0f;                 // Interactions with owner/pets
    private float restActivity = 0f;                   // Restorative downtime loops
    private float recentPhysicalBurst = 0f;            // Fast-decaying exertion pulse for stamina response

    // Derived behavioral batteries layered above the raw activity feeds
    private float socialCharge = 0.45f;                // 0=drained, 0.45=content introvert baseline, 1=pack euphoric
    private float physicalStamina = 0.65f;             // Resets toward rested while physical exertion depletes it
    private float mentalFocus = 0.6f;                  // High when mentally fresh, falls with cognitive strain

    private BehaviouralEnergyProfile cachedEnergyProfile = BehaviouralEnergyProfile.neutral();
    private boolean energyProfileDirty = true;

    // Text animation caching
    private Text cachedMoodText = null;
    private Text cachedMoodTextWithDebug = null;
    private long lastTextUpdateTime = -1L;
    private int cachedLastLevel = -1;

    // Config cache
    private int cachedConfigGeneration = -1;
    private JsonObject cachedMoodsSection;
    @SuppressWarnings("unused") // Reserved for future weighted mood calculations
    private JsonObject cachedWeightSection;
    private JsonObject cachedOpponentSection;
    private JsonObject cachedAnimationSection;
    private double cachedMomentum = 0.35d;
    private double cachedSwitchMargin = 0.05d;
    @SuppressWarnings("unused") // Reserved for future mood transition smoothing
    private int cachedHysteresisTicks = 60;
    private float cachedEpsilon = 0.05f;
    private float[] cachedLevelThresholds = new float[]{0.35f, 0.65f, 0.88f};
    private int cachedBaseAnimationUpdateInterval = 16;
    
    private final EnumMap<PetComponent.Mood, float[]> perMoodThresholds = new EnumMap<>(PetComponent.Mood.class);
    private double cachedAnimationSpeedMultiplier = 0.15d;
    private int cachedMinAnimationInterval = 4;
    private int cachedMaxAnimationInterval = 40;
    
    private final Map<String, Float> cachedEmotionDecayMultipliers = new java.util.HashMap<>();
    private final java.util.Set<String> cachedNegativeEmotions = new java.util.HashSet<>();
    private final java.util.Set<String> cachedPositiveEmotions = new java.util.HashSet<>();
    private final EnumMap<PetComponent.Emotion, Map<PetComponent.Mood, Float>> resolvedWeightCache =
            new EnumMap<>(PetComponent.Emotion.class);
    
    private float cachedHabituationBase = HABITUATION_BASE;
    private float cachedHalfLifeMultiplier = HALF_LIFE_MULTIPLIER;
    private float cachedMinHalfLife = MIN_HALF_LIFE;
    private float cachedMaxHalfLife = MAX_HALF_LIFE;
    private float cachedNegativePersistence = 2.0f;
    private float cachedConditionPresentMultiplier = 1.6f;
    private float cachedHomeostasisRecoveryHalf = HOMEOSTASIS_RECOVERY_HALF;
    private float cachedCadenceAlpha = CADENCE_ALPHA;
    private float cachedVolatilityAlpha = VOLATILITY_ALPHA;
    private float cachedPeakAlpha = PEAK_ALPHA;
    private float cachedOpponentTransferMax = OPPONENT_TRANSFER_MAX;
    private float cachedReboundGain = REBOUND_GAIN;
    private float cachedRelationshipVariance = RELATIONSHIP_VARIANCE;
    private float cachedCarePulseHalfLife = CARE_PULSE_HALF_LIFE;
    private float cachedDangerHalfLife = DANGER_HALF_LIFE;
    private double cachedPackSpiritRadius = 12.0d;
    private int cachedPackSpiritMinPackmates = 1;
    private long cachedPackSpiritGraceTicks = 140L;
    private float cachedPackSpiritBondBonus = 0.3f;
    private float cachedPackSpiritBaseContribution = 0.25f;
    private float cachedPackSpiritClosenessWeight = 0.55f;
    private float cachedPackSpiritCombatBonus = 0.25f;
    private float cachedPackSpiritProtectiveWeight = 0.35f;
    private float cachedPackSpiritFocusedWeight = 0.2f;
    private float cachedPackSpiritPlayfulWeight = 0.1f;
    private float cachedPackSpiritDiversityBonus = 0.15f;
    private float cachedPackSpiritEngagementMax = 1.35f;
    private final EnumMap<PetRoleType.RoleArchetype, Float> cachedPackSpiritRoleWeights =
            new EnumMap<>(PetRoleType.RoleArchetype.class);
    private int cachedOwnerDamageWindow = 160;
    private long cachedOwnerDangerGraceTicks = 200L;
    private long cachedOwnerLowHealthGraceTicks = 260L;
    private float cachedOwnerLowHealthThreshold = 0.45f;
    private float cachedOwnerCriticalHealthThreshold = 0.25f;
    private long cachedOwnerStatusHazardGraceTicks = 180L;
    private float cachedOwnerStatusHazardThreshold = 0.25f;
    private long cachedArcaneOverflowLingerTicks = 220L;
    private long cachedArcaneOverflowStreakGraceTicks = 200L;
    private long cachedArcaneOverflowStatusGraceTicks = 160L;
    private int cachedArcaneOverflowAmbientScanRadius = 4;
    private float cachedArcaneOverflowMinimumEnergy = 0.25f;
    private float cachedArcaneAmbientStructureBaseWeight = 0.35f;
    private float cachedArcaneAmbientStructureMaxEnergy = 0.85f;
    private float cachedArcaneAmbientDistanceExponent = 1.0f;
    private float cachedArcaneAmbientMysticBonus = 0.2f;
    private Map<Identifier, Float> cachedArcaneAmbientStructureWeights = new HashMap<>();
    private float cachedArcaneRespawnAnchorEmptyMultiplier = 0.0f;
    private float cachedArcaneRespawnAnchorChargeStep = 0.25f;
    private float cachedArcaneRespawnAnchorMaxMultiplier = 1.0f;
    private float cachedArcaneBeaconBaseMultiplier = 0.6f;
    private float cachedArcaneBeaconPerLevelMultiplier = 0.25f;
    private float cachedArcaneBeaconMaxMultiplier = 2.0f;
    private long cachedArcaneCatalystRecentBloomTicks = 400L;
    private float cachedArcaneCatalystActiveMultiplier = 1.0f;
    private float cachedArcaneCatalystInactiveMultiplier = 0.25f;
    private float cachedArcaneGearBaseWeight = 0.08f;
    private float cachedArcaneGearLevelWeight = 0.06f;
    private float cachedArcaneGearMaxContribution = 0.8f;
    private float cachedArcaneGearOwnerMultiplier = 0.75f;
    private float cachedArcaneGearPetMultiplier = 1.0f;
    private float cachedArcaneStatusBaseWeight = 0.12f;
    private float cachedArcaneStatusAmplifierWeight = 0.08f;
    private float cachedArcaneStatusDurationWeight = 0.05f;
    private float cachedArcaneStatusDurationCap = 3.0f;
    private int cachedArcaneStatusDurationReference = 200;
    private float cachedArcaneStatusMaxContribution = 0.6f;
    private float cachedArcaneStatusOwnerMultiplier = 1.0f;
    private float cachedArcaneStatusPetMultiplier = 1.15f;
    private double cachedLonelyComfortRadius = 8.0d;
    private double cachedLonelyComfortRadiusSquared = 64.0d;
    private double cachedLonelyDistanceThreshold = 24.0d;
    private double cachedLonelyDistanceThresholdSquared = 576.0d;
    private long cachedLonelySaudadeGraceTicks = 400L;
    private long cachedLonelyHiraethGraceTicks = 900L;
    private long cachedLonelyOfflineGraceTicks = 200L;
    private long cachedLonelyOfflineHiraethGraceTicks = 600L;
    private long cachedLonelySocialGraceTicks = 260L;
    private long cachedLonelyPackGraceTicks = 320L;
    private float cachedLonelyPackStrengthThreshold = 0.2f;
    private double cachedLonelyPackRadius = 16.0d;
    private double cachedLonelyPackRadiusSquared = 256.0d;
    private long cachedPositivePetGraceTicks = 200L;
    private long cachedPositiveCrouchGraceTicks = 160L;
    private long cachedPositiveSocialGraceTicks = 200L;
    private long cachedPositivePlayGraceTicks = 240L;
    private long cachedPositiveFeedGraceTicks = 260L;
    private long cachedPositiveGiftGraceTicks = 360L;
    private long cachedArcaneScanCooldownTicks = 80L;
    private double cachedArcaneScanMovementThreshold = 3.0d;
    private double cachedArcaneScanMovementThresholdSquared = 9.0d;

    private final EnumMap<PetComponent.Emotion, EnumSet<PetComponent.Emotion>> opponentPairs =
            new EnumMap<>(PetComponent.Emotion.class);

    public PetMoodEngine(PetComponent parent) {
        this.parent = parent;
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            moodBlend.put(mood, 0f);
            lastNormalizedWeights.put(mood, 0f);
        }
        buildDefaultOpponentPairs();
    }

    public PetComponent.Mood getCurrentMood() {
        update();
        return currentMood;
    }

    public int getMoodLevel() {
        update();
        return moodLevel;
    }

    private void invalidateCaches() {
        moodBlendDirty = true;
        markEmotionCachesDirty();
    }

    private void markStateDirty() {
        dirty = true;
        invalidateCaches();
    }

    private void markEmotionCachesDirty() {
        activeEmotionsDirty = true;
        dominantEmotionDirty = true;
        cachedDominantEmotion = null;
    }

    void update() {
        long now = parent.getPet().getEntityWorld() instanceof ServerWorld sw ? sw.getTime() : lastMoodUpdate;
        ensureFresh(now);
    }

    public void ensureFresh(long now) {
        if (!dirty && now - lastMoodUpdate < 20) {
            return;
        }

        if (!isAsyncPipelineEnabled()) {
            if (asyncComputationInFlight) {
                discardNextAsyncResult = true;
                asyncComputationInFlight = false;
                asyncRecomputeRequested = false;
                asyncRecomputeTimestamp = -1L;
            }
            updateEmotionStateAndMood(now);
            dirty = false;
            return;
        }

        if (asyncComputationInFlight) {
            asyncRecomputeRequested = true;
            asyncRecomputeTimestamp = Math.max(asyncRecomputeTimestamp, now);
            dirty = false;
            return;
        }

        if (scheduleAsyncComputation(now)) {
            lastMoodUpdate = now;
            dirty = false;
            return;
        }

        updateEmotionStateAndMood(now);
        dirty = false;
    }

    public long estimateNextWakeUp(long now) {
        if (dirty) {
            return 1L;
        }
        long soonest = Long.MAX_VALUE;
        for (EmotionRecord record : emotionRecords.values()) {
            float cadence = record.cadenceEMA > 0f ? record.cadenceEMA : cachedHabituationBase;
            float adaptiveHalf = MathHelper.clamp(cadence * cachedHalfLifeMultiplier, cachedMinHalfLife, cachedMaxHalfLife);
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
        long now = eventTime > 0 ? eventTime : parent.getPet().getEntityWorld().getTime();
        pushEmotion(delta.emotion(), delta.amount(), now);
        lastStimulusTime = now;
        markStateDirty();
    }

    public void onNatureTuningChanged() {
        markStateDirty();
    }

    public void onNatureEmotionProfileChanged(PetComponent.NatureEmotionProfile profile) {
        natureEmotionProfile = profile != null ? profile : PetComponent.NatureEmotionProfile.EMPTY;
        markStateDirty();
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
        sample *= parent.getHarmonyMoodMultiplier();
        sample = MathHelper.clamp(sample, 0f, 1f);
        float volatilityMultiplier = parent.getNatureVolatilityMultiplier();
        float resilienceMultiplier = parent.getNatureResilienceMultiplier();
        if (record.startTime <= 0) {
            record.startTime = now;
        }

        long delta = record.lastEventTime > 0 ? Math.max(1L, now - record.lastEventTime) : Math.max(1L, Math.round(cachedHabituationBase));
        record.lastEventTime = now;
        record.lastUpdateTime = now;

        if (record.cadenceEMA <= 0f) {
            record.cadenceEMA = delta;
        } else {
            record.cadenceEMA = MathHelper.lerp(cachedCadenceAlpha, record.cadenceEMA, delta);
        }
        float volatilitySample = Math.abs(sample - record.intensity) * volatilityMultiplier;
        volatilitySample = MathHelper.clamp(volatilitySample, 0f, 1f);
        record.volatilityEMA = MathHelper.clamp(
            MathHelper.lerp(cachedVolatilityAlpha, record.volatilityEMA, volatilitySample), 0f, 1f);
        record.peakEMA = MathHelper.lerp(cachedPeakAlpha, record.peakEMA, Math.max(record.peakEMA, sample));

        // Rekindle boost: bring intensity toward new sample with spike bias
        float spikeBias = MathHelper.clamp(0.25f + 0.35f * (float) Math.exp(-delta / Math.max(1f, record.cadenceEMA)), 0.25f, 0.60f);
        // Validate spikeBias is in reasonable range
        spikeBias = MathHelper.clamp(spikeBias, 0.0f, 1.0f);
        record.intensity = MathHelper.clamp(MathHelper.lerp(spikeBias, record.intensity, sample), 0f, 1f);

        // Impact budget accrues using rekindle-aware boost
        float rekindleBoost = 1.0f + Math.min(0.6f, record.sensitisationGain - 1.0f);
        // Validate rekindleBoost is in reasonable range
        rekindleBoost = MathHelper.clamp(rekindleBoost, 0.0f, 2.0f);
        float impactGain = sample * rekindleBoost * resilienceMultiplier;
        record.impactBudget = Math.min(getImpactCap(), record.impactBudget + impactGain);

        // Habituation & Sensitization (psychologically accurate):
        // - Frequent stimuli (fast cadence) → habituation → LOWER gain (desensitized)
        // - Rare stimuli (slow cadence) → sensitization → HIGHER gain (re-sensitized)
        // - Normal cadence → drift back to baseline (1.0)
        float cadenceRatio = record.cadenceEMA > 0f ? MathHelper.clamp(delta / record.cadenceEMA, 0f, 2.5f) : 1f;
        float sensitisationDelta;
        if (cadenceRatio < 0.8f) {
            // Stimuli arriving FASTER than expected (short gaps) → habituation
            // Reduce gain: pet becomes desensitized to repeated stimuli
            sensitisationDelta = -0.35f * (0.8f - cadenceRatio); // Phase 2 tuning
        } else if (cadenceRatio > 1.5f) {
            // Stimuli arriving SLOWER than expected (long gaps) → sensitization
            // Increase gain: pet becomes re-sensitized after break
            sensitisationDelta = 0.15f * Math.min(1f, cadenceRatio - 1.5f); // Phase 2 tuning
        } else {
            // Normal cadence → gentle drift toward baseline (1.0)
            sensitisationDelta = (1.0f - record.sensitisationGain) * 0.05f;
        }
        // Apply resilience modifier: resilient pets habituate slower/sensitize faster
        sensitisationDelta *= resilienceMultiplier;
        record.sensitisationGain = MathHelper.clamp(record.sensitisationGain + sensitisationDelta, 0.5f, 1.4f);

        if (record.habituationSlope <= 0f) {
            record.habituationSlope = Math.max(cachedHabituationBase, record.cadenceEMA * 1.1f);
        } else {
            float targetSlope = Math.max(cachedHabituationBase * 0.5f, record.cadenceEMA * 1.2f);
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
        long now = parent.getPet().getEntityWorld().getTime();
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
        markStateDirty();
    }

    public float getMoodStrength(PetComponent.Mood mood) {
        update();
        return MathHelper.clamp(moodBlend.getOrDefault(mood, 0f), 0f, 1f);
    }

    public Map<PetComponent.Mood, Float> getMoodBlend() {
        update();
        if (moodBlendDirty) {
            cachedMoodBlendView = Map.copyOf(moodBlend);
            moodBlendDirty = false;
        }
        return cachedMoodBlendView;
    }

    public Map<PetComponent.Emotion, Float> getActiveEmotions() {
        update();
        if (activeEmotionsDirty) {
            EnumMap<PetComponent.Emotion, Float> active = new EnumMap<>(PetComponent.Emotion.class);
            for (EmotionRecord record : emotionRecords.values()) {
                if (record.weight > EPSILON) {
                    active.put(record.emotion, MathHelper.clamp(record.weight, 0f, 1f));
                }
            }
            cachedActiveEmotionsView = Map.copyOf(active);
            activeEmotionsDirty = false;
        }
        return cachedActiveEmotionsView;
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
        if (dominantEmotionDirty) {
            cachedDominantEmotion = computeDominantEmotion();
            dominantEmotionDirty = false;
        }
        return cachedDominantEmotion;
    }

    String getDominantEmotionName() {
        PetComponent.Emotion dominant = getDominantEmotion();
        return dominant != null ? prettify(dominant.name()) : "None";
    }

    @Nullable
    private PetComponent.Emotion computeDominantEmotion() {
        PetComponent.Emotion bestEmotion = null;
        float bestWeight = Float.NEGATIVE_INFINITY;
        for (EmotionRecord record : emotionRecords.values()) {
            if (record.weight > bestWeight) {
                bestWeight = record.weight;
                bestEmotion = record.emotion;
            }
        }
        return bestEmotion;
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
    
    /**
     * Records behavioral activity from AI goals or interactions.
     * Accumulates until next momentum update.
     * 
     * @param intensity Activity intensity (0-1, where 1 is max exertion)
     * @param durationTicks How long the activity lasted
     */
    public void recordBehavioralActivity(float intensity, long durationTicks) {
        recordBehavioralActivity(intensity, durationTicks, ActivityType.PHYSICAL);
    }
    
    /**
     * Records typed behavioral activity for nuanced momentum tracking.
     * Different activity types contribute differently to overall momentum.
     */
    public void recordBehavioralActivity(float intensity, long durationTicks, ActivityType type) {
        if (intensity < EPSILON || durationTicks <= 0) return;
        
        float normalizedIntensity = MathHelper.clamp(intensity, 0f, 1f);
        float contribution = normalizedIntensity * durationTicks / 100f;
        
        // Apply activity with caps to prevent overflow from rapid recording
        switch (type) {
            case PHYSICAL -> {
                physicalActivity = Math.min(5f, physicalActivity + contribution * 1.2f);
                recentPhysicalBurst = Math.min(1f, recentPhysicalBurst + contribution * 2.5f);
            }
            case MENTAL -> mentalActivity = Math.min(5f, mentalActivity + contribution * 0.8f);
            case SOCIAL -> socialActivity = Math.min(5f, socialActivity + contribution * 0.9f);
            case REST -> restActivity = Math.min(5f, restActivity + contribution);
        }
        energyProfileDirty = true;
        markStateDirty();
    }
    
    /**
     * Activity types for behavioral momentum tracking.
     */
    public enum ActivityType {
        PHYSICAL,  // Movement, play, exercise
        MENTAL,    // Problem-solving, searching, tracking
        SOCIAL,    // Interactions with owner or other pets
        REST       // Calming, restorative loops and naps
    }
    
    /**
     * Gets current behavioral momentum level.
     * 0.0 = completely still/tired, 0.5 = neutral, 1.0 = hyperactive
     */
    public float getBehavioralMomentum() {
        update();
        return behavioralMomentum;
    }

    /**
     * Snapshot of the layered behavioural energy stack.
     */
    @Deprecated(forRemoval = false)
    public BehaviouralEnergyProfile getBehavioralEnergyProfile() {
        return getBehaviouralEnergyProfile();
    }

    /**
     * Snapshot of the layered behavioural energy stack.
     */
    public BehaviouralEnergyProfile getBehaviouralEnergyProfile() {
        update();
        if (energyProfileDirty) {
            refreshEnergyProfile();
        }
        return cachedEnergyProfile;
    }

    /**
     * Gets momentum-based movement speed multiplier for AI/animation.
     * Returns 0.7-1.3 range for natural speed variation.
     */
    public float getMomentumSpeedMultiplier() {
        // Use last-applied behavioralMomentum to avoid forcing a mood recompute every tick
        // Map 0.0-1.0 momentum to 0.7-1.3 speed multiplier
        return 0.7f + (behavioralMomentum * 0.6f);
    }
    
    /**
     * Gets momentum-based animation speed multiplier.
     * Returns 0.8-1.4 range for visible but not jarring animation changes.
     */
    public float getMomentumAnimationSpeed() {
        // Use last-applied behavioralMomentum to avoid forcing a mood recompute on client render ticks
        // Slightly more dramatic range than movement for visual feedback
        return 0.8f + (behavioralMomentum * 0.6f);
    }
    
    /**
     * Debug info for momentum state inspection.
     */
    public String getMomentumDebugString() {
        return String.format(
            "Momentum: %.2f | P:%.2f M:%.2f S:%.2f | Stamina:%.2f Focus:%.2f SocialCharge:%.2f | Inertia:%.3f",
            behavioralMomentum,
            physicalActivity,
            mentalActivity,
            socialActivity,
            physicalStamina,
            mentalFocus,
            socialCharge,
            momentumInertia);
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
        
        // Behavioral energy stack persists momentum and derived batteries
        nbt.putFloat("behavioralMomentum", behavioralMomentum);
        nbt.putFloat("socialCharge", socialCharge);
        nbt.putFloat("physicalStamina", physicalStamina);
        nbt.putFloat("mentalFocus", mentalFocus);
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
                        EmotionRecord record = new EmotionRecord(emotion, parent.getPet().getEntityWorld().getTime());
                        record.intensity = tag.getFloat("intensity").orElse(0f);
                        record.impactBudget = tag.getFloat("impact").orElse(0f);
                        record.cadenceEMA = tag.getFloat("cadence").orElse(0f);
                        record.volatilityEMA = tag.getFloat("volatility").orElse(0f);
                        record.peakEMA = tag.getFloat("peak").orElse(0f);
                        record.habituationSlope = tag.getFloat("habituation").orElse(cachedHabituationBase);
                        record.sensitisationGain = tag.getFloat("sensitisation").orElse(1.0f);
                        record.homeostasisBias = tag.getFloat("homeostasis").orElse(1.0f);
                        record.contagionShare = tag.getFloat("contagion").orElse(0f);
                        record.relationshipGuard = tag.getFloat("relationship").orElse(RELATIONSHIP_BASE);
                        record.dangerWindow = tag.getFloat("danger").orElse(DANGER_BASE);
                        record.appraisalConfidence = tag.getFloat("appraisal").orElse(APPRAISAL_BASE);
                        record.weight = tag.getFloat("weight").orElse(0f);
                        record.startTime = tag.getLong("start").orElse(parent.getPet().getEntityWorld().getTime());
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
        
        // Behavioral energy stack - restore momentum plus batteries, activities rebuild naturally
        behavioralMomentum = nbt.getFloat("behavioralMomentum").orElse(0.5f);
        socialCharge = MathHelper.clamp(nbt.getFloat("socialCharge").orElse(0.45f), 0.1f, 1f);
        physicalStamina = MathHelper.clamp(nbt.getFloat("physicalStamina").orElse(0.65f), 0.05f, 1f);
        mentalFocus = MathHelper.clamp(nbt.getFloat("mentalFocus").orElse(0.6f), 0.05f, 1f);
        // Activities intentionally not persisted - they should start fresh on load
        physicalActivity = 0f;
        mentalActivity = 0f;
        socialActivity = 0f;
        restActivity = 0f;
        recentPhysicalBurst = 0f;
        lastMomentumUpdate = 0L;
        energyProfileDirty = true;
        invalidateCaches();
    }

    // --------------------------------------------------------------------------------------------
    // Core interpretation pipeline
    // --------------------------------------------------------------------------------------------


    private void updateEmotionStateAndMood(long now) {
        if (now - lastMoodUpdate < 20) {
            return;
        }
        lastMoodUpdate = now;

        ComputationSnapshot snapshot = computeSnapshot(now);
        ComputationResult result = computeResult(snapshot);
        applyResult(snapshot, result);
    }

    private boolean isAsyncPipelineEnabled() {
        return PetsPlusConfig.getInstance().isAsyncMoodPipelineEnabled() || DebugSettings.isPipelineEnabled();
    }

    private boolean scheduleAsyncComputation(long now) {
        StateManager manager = parent.getStateManager();
        if (manager == null) {
            return false;
        }
        AsyncWorkCoordinator coordinator = manager.getAsyncWorkCoordinator();
        if (coordinator == null) {
            return false;
        }

        long captureStart = System.nanoTime();
        ComputationSnapshot snapshot = computeSnapshot(now);
        long captureDuration = System.nanoTime() - captureStart;
        if (captureDuration > 0) {
            coordinator.telemetry().recordCaptureDuration(captureDuration);
        }

        if (snapshot.activeRecords().isEmpty()) {
            asyncComputationInFlight = false;
            asyncRecomputeRequested = false;
            asyncRecomputeTimestamp = -1L;
            discardNextAsyncResult = false;
            applyResult(snapshot, computeResult(snapshot));
            return true;
        }

        String descriptor = "pet_mood/" + parent.getPet().getUuidAsString();
        asyncComputationInFlight = true;
        asyncRecomputeRequested = false;
        asyncRecomputeTimestamp = -1L;
        discardNextAsyncResult = false;

        CompletableFuture<ComputationResult> future = coordinator.submitStandalone(
            descriptor,
            () -> computeResult(snapshot),
            result -> applyAsyncResult(snapshot, result)
        );

        if (future.isCompletedExceptionally()) {
            asyncComputationInFlight = false;
            if (DebugSettings.isDebugEnabled()) {
                Petsplus.LOGGER.debug("Async mood job {} rejected immediately; running synchronously", descriptor);
            }
            return false;
        }

        future.exceptionally(throwable -> handleAsyncFailure(descriptor, snapshot, throwable));
        return true;
    }

    private void applyAsyncResult(ComputationSnapshot snapshot, ComputationResult result) {
        asyncComputationInFlight = false;
        if (discardNextAsyncResult) {
            discardNextAsyncResult = false;
            asyncRecomputeRequested = false;
            asyncRecomputeTimestamp = -1L;
            return;
        }

        applyResult(snapshot, result);

        long followUpNow = asyncRecomputeTimestamp > 0L
                ? asyncRecomputeTimestamp
                : resolveWorldTime(snapshot.timestamp());
        boolean needsFollowUp = asyncRecomputeRequested || dirty;
        asyncRecomputeRequested = false;
        asyncRecomputeTimestamp = -1L;

        if (!needsFollowUp) {
            return;
        }

        if (isAsyncPipelineEnabled()) {
            ensureFresh(followUpNow);
        } else {
            runSynchronousFallback(followUpNow);
        }
    }

    private ComputationResult handleAsyncFailure(String descriptor,
                                                 ComputationSnapshot snapshot,
                                                 Throwable throwable) {
        asyncComputationInFlight = false;
        asyncRecomputeRequested = false;
        asyncRecomputeTimestamp = -1L;
        discardNextAsyncResult = false;

        Throwable cause = throwable != null && throwable.getCause() != null ? throwable.getCause() : throwable;
        if (DebugSettings.isDebugEnabled()) {
            Petsplus.LOGGER.debug("Async mood job {} failed: {}", descriptor, cause != null ? cause.getMessage() : "unknown", cause);
        }

        if (cause instanceof RejectedExecutionException || cause instanceof CancellationException) {
            runSynchronousFallback(resolveWorldTime(snapshot.timestamp()));
            return null;
        }

        Petsplus.LOGGER.error("Async mood job {} encountered an unexpected failure; falling back to synchronous update", descriptor, cause);
        runSynchronousFallback(resolveWorldTime(snapshot.timestamp()));
        return null;
    }

    private void runSynchronousFallback(long now) {
        updateEmotionStateAndMood(now);
        dirty = false;
    }

    private long resolveWorldTime(long fallback) {
        World world = parent.getPet().getEntityWorld();
        if (world instanceof ServerWorld serverWorld) {
            return serverWorld.getTime();
        }
        return Math.max(fallback, lastMoodUpdate);
    }

    private ComputationSnapshot computeSnapshot(long now) {
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

        EnumMap<PetComponent.Mood, Float> baselineBlend = new EnumMap<>(PetComponent.Mood.class);
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            baselineBlend.put(mood, moodBlend.getOrDefault(mood, 0f));
        }

        BehaviorSnapshot behaviorSnapshot = captureBehaviorSnapshot(now, baselineBlend);
        Map<PetComponent.Emotion, Float> harmonyBiases = captureHarmonyBiases();

        List<SnapshotEmotionRecord> snapshots = new ArrayList<>(emotionRecords.size());
        for (EmotionRecord record : new ArrayList<>(emotionRecords.values())) {
            long delta = Math.max(1L, now - record.lastUpdateTime);
            refreshContextGuards(record, now, delta);
            boolean ongoingCondition = hasOngoingCondition(record.emotion, now);
            float contextModulation = MathHelper.clamp(
                    computeEmotionSpecificContextModulation(record, now),
                    -1.0f,
                    1.0f
            );
            float profileWeightBias = MathHelper.clamp(getNatureWeightBias(record.emotion), 0.0f, 2.0f);
            float recencyModifier = MathHelper.clamp(getNatureRecencyBoostModifier(record.emotion), 0.5f, 1.5f);
            float persistenceModifier = MathHelper.clamp(getNaturePersistenceModifier(record.emotion), 0.5f, 2.0f);

            snapshots.add(new SnapshotEmotionRecord(
                    record.emotion,
                    MathHelper.clamp(record.intensity, 0f, 1f),
                    Math.max(0f, record.impactBudget),
                    Math.max(0f, record.cadenceEMA),
                    Math.max(0f, record.volatilityEMA),
                    Math.max(0f, record.peakEMA),
                    MathHelper.clamp(record.homeostasisBias, 0.5f, 1.5f),
                    MathHelper.clamp(record.sensitisationGain, 0.25f, 2.0f),
                    Math.max(0f, record.habituationSlope),
                    MathHelper.clamp(record.contagionShare, -DEFAULT_IMPACT_CAP, DEFAULT_IMPACT_CAP),
                    Math.max(0f, record.relationshipGuard),
                    Math.max(0f, record.dangerWindow),
                    MathHelper.clamp(record.appraisalConfidence, 0f, 1f),
                    record.startTime,
                    record.lastEventTime,
                    record.lastUpdateTime,
                    ongoingCondition,
                    contextModulation,
                    profileWeightBias,
                    recencyModifier,
                    persistenceModifier
            ));
        }

        PetComponent.Mood previousMood = currentMood;
        float previousStrength = moodBlend.getOrDefault(previousMood, 0f);

        if (snapshots.isEmpty()) {
            return new ComputationSnapshot(
                    now,
                    epsilon,
                    List.of(),
                    baselineBlend,
                    previousMood,
                    previousStrength,
                    cachedMomentum,
                    cachedSwitchMargin,
                    DEFAULT_IMPACT_CAP,
                    MathHelper.clamp(parent.getNatureVolatilityMultiplier(), 0.1f, 3.0f),
                    MathHelper.clamp(parent.getNatureResilienceMultiplier(), 0.1f, 3.0f),
                    cachedOpponentTransferMax,
                    cachedReboundGain,
                    harmonyBiases,
                    behaviorSnapshot,
                    cachedHabituationBase,
                    cachedHalfLifeMultiplier,
                    cachedMinHalfLife,
                    cachedMaxHalfLife,
                    cachedNegativePersistence,
                    cachedConditionPresentMultiplier,
                    cachedHomeostasisRecoveryHalf,
                    new HashMap<>(cachedEmotionDecayMultipliers),
                    new HashSet<>(cachedNegativeEmotions),
                    cachedLonelySocialGraceTicks,
                    cachedLonelyPackGraceTicks,
                    cachedLonelyPackStrengthThreshold
            );
        }

        float impactCap = computeImpactCapFromSnapshots(snapshots);
        float volatility = MathHelper.clamp(parent.getNatureVolatilityMultiplier(), 0.1f, 3.0f);
        float resilience = MathHelper.clamp(parent.getNatureResilienceMultiplier(), 0.1f, 3.0f);

        return new ComputationSnapshot(
                now,
                epsilon,
                List.copyOf(snapshots),
                baselineBlend,
                previousMood,
                previousStrength,
                cachedMomentum,
                cachedSwitchMargin,
                impactCap,
                volatility,
                resilience,
                cachedOpponentTransferMax,
                cachedReboundGain,
                harmonyBiases,
                behaviorSnapshot,
                cachedHabituationBase,
                cachedHalfLifeMultiplier,
                cachedMinHalfLife,
                cachedMaxHalfLife,
                cachedNegativePersistence,
                cachedConditionPresentMultiplier,
                cachedHomeostasisRecoveryHalf,
                new HashMap<>(cachedEmotionDecayMultipliers),
                new HashSet<>(cachedNegativeEmotions),
                cachedLonelySocialGraceTicks,
                cachedLonelyPackGraceTicks,
                cachedLonelyPackStrengthThreshold
        );
    }

    private BehaviorSnapshot captureBehaviorSnapshot(long now, EnumMap<PetComponent.Mood, Float> baselineBlend) {
        MobEntity petEntity = parent.getPetEntity();
        boolean petIsBaby = petEntity != null && petEntity.isBaby();
        long timeOfDay = 0L;
        float healthRatio = 1f;
        if (petEntity != null) {
            World world = petEntity.getEntityWorld();
            if (world instanceof ServerWorld serverWorld) {
                timeOfDay = serverWorld.getTimeOfDay() % 24000L;
            }
            if (petEntity instanceof LivingEntity living) {
                float maxHealth = Math.max(1f, living.getMaxHealth());
                healthRatio = MathHelper.clamp(living.getHealth() / maxHealth, 0f, 1f);
            }
        }

        PlayerEntity owner = parent.getOwner();
        boolean ownerPresent = false;
        double ownerDistanceSq = Double.POSITIVE_INFINITY;
        if (owner != null && petEntity != null && owner.isAlive() && owner.getEntityWorld() == petEntity.getEntityWorld()) {
            ownerPresent = true;
            ownerDistanceSq = owner.squaredDistanceTo(petEntity);
        }

        long lastSocialTick = parent.getStateData(PetComponent.StateKeys.LAST_SOCIAL_BUFFER_TICK, Long.class, 0L);
        long lastPackTick = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_TICK, Long.class, 0L);
        float lastPackStrength = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_STRENGTH, Float.class, 0f);
        float lastPackWeighted = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_WEIGHTED_STRENGTH, Float.class, 0f);
        int lastPackAllies = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_ALLIES, Integer.class, 0);

        EnumMap<PetComponent.Mood, Float> moodSnapshot = new EnumMap<>(PetComponent.Mood.class);
        moodSnapshot.putAll(baselineBlend);

        return new BehaviorSnapshot(
                now,
                lastMomentumUpdate,
                behavioralMomentum,
                momentumInertia,
                physicalActivity,
                mentalActivity,
                socialActivity,
                restActivity,
                recentPhysicalBurst,
                physicalStamina,
                mentalFocus,
                socialCharge,
                energyProfileDirty,
                MathHelper.clamp(parent.getNatureResilienceMultiplier(), 0.1f, 3.0f),
                MathHelper.clamp(parent.getNatureVolatilityMultiplier(), 0.1f, 3.0f),
                petIsBaby,
                timeOfDay,
                healthRatio,
                normalizedBondStrength(),
                parent.getStablePerPetSeed(),
                lastSocialTick,
                lastPackTick,
                lastPackStrength,
                lastPackWeighted,
                lastPackAllies,
                ownerPresent,
                ownerDistanceSq,
                moodSnapshot
        );
    }

    private Map<PetComponent.Emotion, Float> captureHarmonyBiases() {
        PetComponent.HarmonyState harmonyState = parent.getHarmonyState();
        if (harmonyState == null) {
            return Map.of();
        }
        Map<PetComponent.Emotion, Float> biases = harmonyState.emotionBiases();
        if (biases == null || biases.isEmpty()) {
            return Map.of();
        }
        EnumMap<PetComponent.Emotion, Float> copy = new EnumMap<>(PetComponent.Emotion.class);
        for (Map.Entry<PetComponent.Emotion, Float> entry : biases.entrySet()) {
            PetComponent.Emotion emotion = entry.getKey();
            Float value = entry.getValue();
            if (emotion == null || value == null || Float.isNaN(value) || Float.isInfinite(value)) {
                continue;
            }
            copy.put(emotion, MathHelper.clamp(value, 0f, 1f));
        }
        return copy.isEmpty() ? Map.of() : copy;
    }

    private ComputationResult computeResult(ComputationSnapshot snapshot) {
        BehaviorResult behaviorResult = computeBehaviorResult(snapshot);
        ProcessedEmotionResult processed = processEmotionRecords(snapshot);
        List<ProcessedEmotionRecord> records = processed.records();

        if (records.isEmpty()) {
            EnumMap<PetComponent.Mood, Float> emptyBlend = new EnumMap<>(PetComponent.Mood.class);
            for (PetComponent.Mood mood : PetComponent.Mood.values()) {
                emptyBlend.put(mood, 0f);
            }
            return new ComputationResult(
                    true,
                    emptyBlend,
                    emptyBlend,
                    new EnumMap<>(PetComponent.Emotion.class),
                    Collections.emptyList(),
                    processed.updates(),
                    processed.removals(),
                    behaviorResult
            );
        }

        int activeSize = records.size();
        float[] cadences = new float[activeSize];
        for (int i = 0; i < activeSize; i++) {
            ProcessedEmotionRecord record = records.get(i);
            float cadence = record.cadenceEMA() > 0f ? record.cadenceEMA() : snapshot.habituationBase();
            cadences[i] = cadence;
        }
        float cadenceMedian = selectQuantile(cadences, cadences.length, 0.5f, snapshot.habituationBase());

        float[] signalScratch = new float[activeSize];
        ArrayList<Candidate> candidates = new ArrayList<>(activeSize);
        for (int i = 0; i < activeSize; i++) {
            ProcessedEmotionRecord record = records.get(i);
            float freshness = computeFreshness(record, snapshot.timestamp(), snapshot.habituationBase());
            float freq = record.cadenceEMA() > 0f
                    ? MathHelper.clamp(cadenceMedian / record.cadenceEMA(), 0f, 3.5f)
                    : 0f;
            if (!Float.isFinite(freq) || freq <= 0f) {
                freq = 0.25f;
            }

            float signal = computeSignal(record, freshness, freq);
            if (!Float.isFinite(signal)) {
                signal = 0f;
            }

            signalScratch[i] = signal;
            candidates.add(new Candidate(record, freshness, freq, signal));
        }

        if (candidates.isEmpty()) {
            EnumMap<PetComponent.Mood, Float> emptyBlend = new EnumMap<>(PetComponent.Mood.class);
            for (PetComponent.Mood mood : PetComponent.Mood.values()) {
                emptyBlend.put(mood, 0f);
            }
            return new ComputationResult(
                    true,
                    emptyBlend,
                    emptyBlend,
                    new EnumMap<>(PetComponent.Emotion.class),
                    Collections.emptyList(),
                    processed.updates(),
                    processed.removals(),
                    behaviorResult
            );
        }

        float medianSignal = selectQuantile(signalScratch, signalScratch.length, 0.5f, 0f);
        float threshold = medianSignal * 0.6f;
        List<Candidate> survivors = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            if (candidate.signal >= threshold) {
                survivors.add(candidate);
            }
        }
        if (survivors.isEmpty()) {
            Candidate best = Collections.max(candidates, Comparator.comparingDouble(c -> c.signal));
            survivors.add(best);
        }

        float impactCap = snapshot.impactCap() > 0f ? snapshot.impactCap() : DEFAULT_IMPACT_CAP;
        float weightCap = Math.max(impactCap * 1.5f, DEFAULT_WEIGHT_CAP);

        List<Candidate> weighted = new ArrayList<>(survivors.size());
        for (Candidate candidate : survivors) {
            ProcessedEmotionRecord record = candidate.record;
            float intensity = MathHelper.clamp(record.intensity(), 0f, 1f);

            float baseWeight = intensity * (1.0f + MathHelper.clamp(record.impactBudget() / impactCap, 0f, 1.5f));

            float lastAge = Math.max(0f, snapshot.timestamp() - record.lastEventTime());
            float recencyBoost = 0f;
            if (lastAge < 60f) {
                float baseBoost = 0.4f * (1.0f - lastAge / 60f);
                recencyBoost = baseBoost * record.recencyModifier();
            }
            recencyBoost = MathHelper.clamp(recencyBoost, 0.0f, 1.0f);

            float persistenceBonus = 0f;
            if (record.hasOngoingCondition()) {
                float basePersistence = 0.3f * intensity;
                persistenceBonus = basePersistence * record.persistenceModifier();
            }
            persistenceBonus = MathHelper.clamp(persistenceBonus, 0.0f, 1.5f);

            float habituationPenalty = 0f;
            if (record.cadenceEMA() > 0f && record.cadenceEMA() < 80f) {
                habituationPenalty = -0.35f * (1.0f - record.cadenceEMA() / 80f);
            }
            habituationPenalty = MathHelper.clamp(habituationPenalty, -1.0f, 0.0f);

            float rawWeight = (baseWeight + recencyBoost + persistenceBonus + habituationPenalty
                    + record.contextModulation() + record.contagionShare()) * record.profileWeightBias();
            rawWeight = MathHelper.clamp(rawWeight, 0f, weightCap);

            candidate.signal = rawWeight;
            weighted.add(candidate);
        }

        if (weighted.isEmpty()) {
            EnumMap<PetComponent.Mood, Float> emptyBlend = new EnumMap<>(PetComponent.Mood.class);
            for (PetComponent.Mood mood : PetComponent.Mood.values()) {
                emptyBlend.put(mood, 0f);
            }
            return new ComputationResult(
                    true,
                    emptyBlend,
                    emptyBlend,
                    new EnumMap<>(PetComponent.Emotion.class),
                    Collections.emptyList(),
                    processed.updates(),
                    processed.removals(),
                    behaviorResult
            );
        }

        applyOpponentTransfers(weighted, weightCap, snapshot.natureVolatility(), snapshot.natureResilience(),
                snapshot.opponentTransferMax(), snapshot.reboundGain());

        EnumMap<PetComponent.Emotion, Float> recordWeights = new EnumMap<>(PetComponent.Emotion.class);
        for (Candidate candidate : weighted) {
            recordWeights.put(candidate.record.emotion(), candidate.signal);
        }

        EnumMap<PetComponent.Mood, Float> targetBlend = new EnumMap<>(PetComponent.Mood.class);
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            targetBlend.put(mood, 0f);
        }
        for (Candidate candidate : weighted) {
            Map<PetComponent.Mood, Float> mapping = getEmotionToMoodWeights(candidate.record.emotion());
            for (Map.Entry<PetComponent.Mood, Float> entry : mapping.entrySet()) {
                float contribution = candidate.signal * entry.getValue();
                targetBlend.merge(entry.getKey(), Math.max(0f, contribution), Float::sum);
            }
        }

        float total = 0f;
        for (float value : targetBlend.values()) {
            total += Math.max(0f, value);
        }
        if (total <= snapshot.epsilon()) {
            EnumMap<PetComponent.Mood, Float> emptyBlend = new EnumMap<>(PetComponent.Mood.class);
            for (PetComponent.Mood mood : PetComponent.Mood.values()) {
                emptyBlend.put(mood, 0f);
            }
            return new ComputationResult(
                    true,
                    emptyBlend,
                    emptyBlend,
                    new EnumMap<>(PetComponent.Emotion.class),
                    Collections.emptyList(),
                    processed.updates(),
                    processed.removals(),
                    behaviorResult
            );
        }
        for (Map.Entry<PetComponent.Mood, Float> entry : targetBlend.entrySet()) {
            entry.setValue(Math.max(0f, entry.getValue()) / total);
        }

        EnumMap<PetComponent.Mood, Float> normalizedTarget = new EnumMap<>(targetBlend);

        float maxEmotionFreshness = 0f;
        float maxEmotionWeight = 0f;
        for (Candidate candidate : weighted) {
            maxEmotionFreshness = Math.max(maxEmotionFreshness, candidate.freshness);
            maxEmotionWeight = Math.max(maxEmotionWeight, candidate.signal);
        }

        float baseMomentum = (float) MathHelper.clamp(snapshot.cachedMomentum(), 0.0, 1.0) * 0.85f;
        float freshnessFactor = MathHelper.clamp(maxEmotionFreshness, 0f, 1f);
        float weightFactor = MathHelper.clamp(maxEmotionWeight / 4.0f, 0f, 1f);
        float combined = (freshnessFactor + weightFactor) / 2f;
        float adaptiveMomentum = baseMomentum * (1.35f - combined * 0.9f);
        adaptiveMomentum = MathHelper.clamp(adaptiveMomentum, 0.15f, 0.85f);

        EnumMap<PetComponent.Mood, Float> finalBlend = new EnumMap<>(PetComponent.Mood.class);
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            float cur = snapshot.baselineBlend().getOrDefault(mood, 0f);
            float tar = normalizedTarget.getOrDefault(mood, 0f);
            float blended = cur + (tar - cur) * adaptiveMomentum;
            finalBlend.put(mood, MathHelper.clamp(blended, 0f, 1f));
        }

        float momentumForBlend = behaviorResult != null ? behaviorResult.behavioralMomentum() : snapshot.behaviorSnapshot().behavioralMomentum();
        applyBehavioralMomentumInfluence(finalBlend, momentumForBlend);
        normalizeBlend(finalBlend);

        List<WeightedCandidate> presentation = weighted.stream()
                .map(c -> new WeightedCandidate(c.record.emotion(), Math.max(0f, c.signal), c.record.intensity()))
                .collect(Collectors.toList());

        return new ComputationResult(false, normalizedTarget, finalBlend, recordWeights, presentation,
                processed.updates(), processed.removals(), behaviorResult);
    }

    @Nullable
    private BehaviorResult computeBehaviorResult(ComputationSnapshot snapshot) {
        BehaviorSnapshot behavior = snapshot.behaviorSnapshot();
        if (behavior == null) {
            return null;
        }

        long delta = Math.max(0L, behavior.now() - behavior.lastMomentumUpdate());
        if (delta < 20L) {
            BehaviouralEnergyProfile profile = behavior.energyProfileDirty()
                    ? buildEnergyProfile(
                            behavior.behavioralMomentum(),
                            behavior.physicalActivity(),
                            behavior.mentalActivity(),
                            behavior.socialActivity(),
                            behavior.physicalStamina(),
                            behavior.mentalFocus(),
                            behavior.socialCharge())
                    : null;
            return new BehaviorResult(
                    behavior.lastMomentumUpdate(),
                    behavior.behavioralMomentum(),
                    behavior.momentumInertia(),
                    behavior.physicalActivity(),
                    behavior.mentalActivity(),
                    behavior.socialActivity(),
                    behavior.restActivity(),
                    behavior.recentPhysicalBurst(),
                    behavior.physicalStamina(),
                    behavior.mentalFocus(),
                    behavior.socialCharge(),
                    behavior.energyProfileDirty(),
                    profile
            );
        }

        float baseline = computeMomentumBaseline(behavior);
        float activityLevel = computeActivityLevel(behavior);
        float activityBoost = activityLevel * 0.4f;
        float restCalm = MathHelper.clamp(behavior.restActivity() * 0.3f, 0f, 0.35f);
        float targetMomentum = MathHelper.clamp(baseline + activityBoost - restCalm, 0f, 1f);

        float momentumGap = Math.abs(targetMomentum - behavior.behavioralMomentum());
        float inertiaFactor = computeInertiaFactor(delta, momentumGap, behavior);
        float momentumDelta = (targetMomentum - behavior.behavioralMomentum()) * inertiaFactor;
        float momentum = MathHelper.clamp(behavior.behavioralMomentum() + momentumDelta, 0f, 1f);
        float momentumInertia = MathHelper.lerp(0.1f, behavior.momentumInertia(), Math.abs(momentumDelta));
        momentum += generateOrganicVariation(behavior);
        momentum = MathHelper.clamp(momentum, 0f, 1f);

        float dt = MathHelper.clamp(delta / 20f, 0.05f, 5f);

        float physicalActivity = behavior.physicalActivity();
        float mentalActivity = behavior.mentalActivity();
        float socialActivity = behavior.socialActivity();
        float restActivity = behavior.restActivity();

        float recentPhysicalBurst = behavior.recentPhysicalBurst();
        float physicalStamina = behavior.physicalStamina();
        float mentalFocus = behavior.mentalFocus();
        float socialCharge = behavior.socialCharge();

        float normalizedPhysical = Math.min(1f, physicalActivity * 0.4f);
        float normalizedMental = Math.min(1f, mentalActivity * 0.3f);
        float normalizedSocial = Math.min(1f, socialActivity * 0.3f);
        float normalizedRest = Math.min(1f, restActivity * 0.5f);

        float ambientSocial = computeAmbientSocialComfort(behavior, snapshot);
        float bondFactor = behavior.bondStrengthNormalized();

        float physicalLoad = Math.max(normalizedPhysical, recentPhysicalBurst);
        float physicalDrain = physicalLoad * (0.45f + physicalLoad * 0.25f) * dt;
        float recoveryBase = 0.03f + (1f - physicalLoad) * 0.08f + socialCharge * 0.01f;
        float physicalRecovery = (recoveryBase + (0.04f + (1f - physicalStamina) * 0.12f) * normalizedRest) * dt;
        physicalStamina = MathHelper.clamp(physicalStamina + physicalRecovery - physicalDrain, 0.05f, 1f);
        recentPhysicalBurst = Math.max(0f, recentPhysicalBurst - dt * 0.6f);

        float calmMood = behavior.moodBlendSnapshot().getOrDefault(PetComponent.Mood.CALM, 0f);
        float focusMood = behavior.moodBlendSnapshot().getOrDefault(PetComponent.Mood.CURIOUS, 0f);
        float mentalDrain = normalizedMental * (0.5f + normalizedMental * 0.2f) * dt;
        float mentalRecovery = (0.025f + (1f - normalizedMental) * 0.05f + calmMood * 0.04f + focusMood * 0.02f) * dt;
        mentalFocus = MathHelper.clamp(mentalFocus + mentalRecovery - mentalDrain, 0.05f, 1f);

        float familiarityPenalty = MathHelper.clamp(0.25f - ambientSocial * 0.18f, 0.05f, 0.25f);
        float socialGain = (normalizedSocial * (0.55f + bondFactor * 0.25f)) * dt;
        float ambientBoost = ambientSocial * 0.05f * dt;
        float socialDecay = (0.02f + familiarityPenalty * normalizedSocial + (1f - ambientSocial) * 0.02f) * dt;
        socialCharge = MathHelper.clamp(socialCharge + socialGain + ambientBoost - socialDecay, 0.1f, 1f);

        float decayFactor = Math.max(0f, 1f - delta * 0.005f);
        float sqrtDecay = (float) Math.sqrt(decayFactor);
        physicalActivity = (float) (physicalActivity * Math.pow(decayFactor, 1.2));
        mentalActivity = (float) (mentalActivity * Math.pow(decayFactor, 0.8));
        socialActivity *= decayFactor;
        restActivity *= sqrtDecay;

        if (physicalActivity < 0.001f) physicalActivity = 0f;
        if (mentalActivity < 0.001f) mentalActivity = 0f;
        if (socialActivity < 0.001f) socialActivity = 0f;
        if (restActivity < 0.001f) restActivity = 0f;

        BehaviouralEnergyProfile energyProfile = buildEnergyProfile(
                momentum,
                physicalActivity,
                mentalActivity,
                socialActivity,
                physicalStamina,
                mentalFocus,
                socialCharge
        );

        return new BehaviorResult(
                behavior.now(),
                momentum,
                momentumInertia,
                physicalActivity,
                mentalActivity,
                socialActivity,
                restActivity,
                recentPhysicalBurst,
                physicalStamina,
                mentalFocus,
                socialCharge,
                true,
                energyProfile
        );
    }

    private ProcessedEmotionResult processEmotionRecords(ComputationSnapshot snapshot) {
        List<SnapshotEmotionRecord> snapshots = snapshot.activeRecords();
        EnumMap<PetComponent.Emotion, EmotionRecordUpdate> updates = new EnumMap<>(PetComponent.Emotion.class);
        List<ProcessedEmotionRecord> processed = new ArrayList<>(snapshots.size());
        Set<PetComponent.Emotion> removals = EnumSet.noneOf(PetComponent.Emotion.class);

        Map<PetComponent.Emotion, Float> harmonyBiases = snapshot.harmonyBiases();
        for (SnapshotEmotionRecord record : snapshots) {
            ProcessedEmotionRecord processedRecord = processEmotionRecord(snapshot, record, harmonyBiases);

            EmotionRecordUpdate update = new EmotionRecordUpdate(
                    processedRecord.emotion(),
                    processedRecord.intensity(),
                    processedRecord.impactBudget(),
                    processedRecord.cadenceEMA(),
                    processedRecord.volatilityEMA(),
                    processedRecord.peakEMA(),
                    processedRecord.homeostasisBias(),
                    processedRecord.sensitisationGain(),
                    processedRecord.habituationSlope(),
                    processedRecord.contagionShare(),
                    processedRecord.relationshipGuard(),
                    processedRecord.dangerWindow(),
                    processedRecord.appraisalConfidence(),
                    processedRecord.lastEventTime(),
                    processedRecord.lastUpdateTime()
            );
            updates.put(processedRecord.emotion(), update);

            float epsilon = snapshot.epsilon();
            if (processedRecord.intensity() <= epsilon
                    && processedRecord.impactBudget() <= epsilon
                    && Math.abs(processedRecord.contagionShare()) <= epsilon) {
                removals.add(processedRecord.emotion());
                continue;
            }

            processed.add(processedRecord);
        }

        return new ProcessedEmotionResult(List.copyOf(processed), updates, removals);
    }

    private ProcessedEmotionRecord processEmotionRecord(ComputationSnapshot snapshot,
                                                        SnapshotEmotionRecord record,
                                                        Map<PetComponent.Emotion, Float> harmonyBiases) {
        long now = snapshot.timestamp();

        float intensity = MathHelper.clamp(record.intensity(), 0f, 1f);
        float impactBudget = Math.max(0f, record.impactBudget());
        float cadenceEMA = Math.max(0f, record.cadenceEMA());
        float volatilityEMA = Math.max(0f, record.volatilityEMA());
        float peakEMA = Math.max(0f, record.peakEMA());
        float homeostasisBias = MathHelper.clamp(record.homeostasisBias(), 0.5f, 1.5f);
        float sensitisationGain = MathHelper.clamp(record.sensitisationGain(), 0.25f, 2.0f);
        float habituationSlope = Math.max(0f, record.habituationSlope());
        float contagionShare = MathHelper.clamp(record.contagionShare(), -DEFAULT_IMPACT_CAP, DEFAULT_IMPACT_CAP);
        float relationshipGuard = Math.max(0f, record.relationshipGuard());
        float dangerWindow = Math.max(0f, record.dangerWindow());
        float appraisalConfidence = MathHelper.clamp(record.appraisalConfidence(), 0f, 1f);
        long lastEventTime = record.lastEventTime();
        long lastUpdateTime = record.lastUpdateTime();

        long delta = Math.max(0L, now - lastUpdateTime);
        if (delta > 0L) {
            float cadence = cadenceEMA > 0f ? cadenceEMA : snapshot.habituationBase();
            float adaptiveHalf = MathHelper.clamp(cadence * snapshot.halfLifeMultiplier(),
                    snapshot.minHalfLife(), snapshot.maxHalfLife());
            String emotionKey = record.emotion().name().toLowerCase(Locale.ROOT);
            float emotionMultiplier = snapshot.emotionDecayMultipliers().getOrDefault(emotionKey, 1.0f);
            adaptiveHalf *= emotionMultiplier;
            if (snapshot.negativeEmotions().contains(emotionKey)) {
                adaptiveHalf *= snapshot.negativePersistence();
            }
            if (record.hasOngoingCondition()) {
                adaptiveHalf *= snapshot.conditionPresentMultiplier();
            }
            float decayRate = (float) (Math.log(2) / adaptiveHalf);
            float decay = (float) Math.exp(-decayRate * delta);
            intensity *= decay;
            impactBudget *= decay;
            homeostasisBias = MathHelper.lerp((float) Math.exp(-delta / snapshot.homeostasisRecoveryHalf()),
                    homeostasisBias, 1.1f);
            contagionShare *= Math.exp(-delta / 400f);
            lastUpdateTime = now;
        }

        Float bias = harmonyBiases.get(record.emotion());
        if (bias != null && bias > 0f) {
            float target = MathHelper.clamp(bias, 0f, 0.7f);
            intensity = MathHelper.clamp(MathHelper.lerp(HARMONY_BIAS_ALPHA, intensity, target), 0f, 1f);
            impactBudget = Math.max(impactBudget, target * 0.5f);
            lastEventTime = now;
            lastUpdateTime = now;
        }

        return new ProcessedEmotionRecord(
                record.emotion(),
                MathHelper.clamp(intensity, 0f, 1f),
                Math.max(0f, impactBudget),
                Math.max(0f, cadenceEMA),
                Math.max(0f, volatilityEMA),
                Math.max(0f, peakEMA),
                MathHelper.clamp(homeostasisBias, 0.5f, 1.5f),
                MathHelper.clamp(sensitisationGain, 0.25f, 2.0f),
                Math.max(0f, habituationSlope),
                MathHelper.clamp(contagionShare, -DEFAULT_IMPACT_CAP, DEFAULT_IMPACT_CAP),
                Math.max(0f, relationshipGuard),
                Math.max(0f, dangerWindow),
                MathHelper.clamp(appraisalConfidence, 0f, 1f),
                record.startTime(),
                lastEventTime,
                lastUpdateTime,
                record.hasOngoingCondition(),
                record.contextModulation(),
                record.profileWeightBias(),
                record.recencyModifier(),
                record.persistenceModifier()
        );
    }

    private float computeMomentumBaseline(BehaviorSnapshot behavior) {
        float baseline = 0.5f;
        baseline += behavior.moodBlendSnapshot().getOrDefault(PetComponent.Mood.PLAYFUL, 0f) * 0.2f;
        baseline += behavior.moodBlendSnapshot().getOrDefault(PetComponent.Mood.RESTLESS, 0f) * 0.25f;
        baseline += behavior.moodBlendSnapshot().getOrDefault(PetComponent.Mood.CURIOUS, 0f) * 0.15f;
        baseline -= behavior.moodBlendSnapshot().getOrDefault(PetComponent.Mood.CALM, 0f) * 0.3f;
        baseline -= behavior.moodBlendSnapshot().getOrDefault(PetComponent.Mood.SAUDADE, 0f) * 0.2f;

        if (behavior.petIsBaby()) {
            baseline += 0.2f;
        }

        baseline += (behavior.volatilityMultiplier() - 1.0f) * 0.15f;

        long timeOfDay = behavior.timeOfDay();
        if (timeOfDay >= 6000 && timeOfDay <= 18000) {
            baseline += 0.05f;
        } else {
            baseline -= 0.05f;
        }

        if (behavior.healthRatio() < 0.5f) {
            baseline -= (0.5f - behavior.healthRatio()) * 0.4f;
        }

        return MathHelper.clamp(baseline, 0.15f, 0.85f);
    }

    private float computeActivityLevel(BehaviorSnapshot behavior) {
        float physical = Math.min(1f, behavior.physicalActivity() * 0.4f);
        float mental = Math.min(1f, behavior.mentalActivity() * 0.3f);
        float social = Math.min(1f, behavior.socialActivity() * 0.3f);
        float total = physical + mental + social;
        return MathHelper.clamp(total / (1f + total * 0.3f), 0f, 1f);
    }

    private float computeInertiaFactor(long deltaTicks, float momentumGap, BehaviorSnapshot behavior) {
        float resilience = Math.max(0.1f, behavior.resilienceMultiplier());
        float baseInertia = 0.05f / resilience;
        float timeFactor = MathHelper.clamp(deltaTicks / 20f, 0.5f, 6f);
        float gapUrgency = MathHelper.clamp(momentumGap * 3.5f, 0f, 3.5f);
        float inertiaPulse = MathHelper.clamp(behavior.momentumInertia() * 12f, 0f, 3f);
        float urgency = 1f + gapUrgency + inertiaPulse;
        return MathHelper.clamp(baseInertia * timeFactor * urgency, 0.001f, 0.5f);
    }

    private float generateOrganicVariation(BehaviorSnapshot behavior) {
        double timeSeconds = behavior.now() * 0.001;
        double microPhase = timeSeconds * 60 + normalizedPhaseFromSeed(behavior.stableSeed(), 0);
        double mesoPhase = timeSeconds * 12 + normalizedPhaseFromSeed(behavior.stableSeed(), 21);
        double macroPhase = timeSeconds * 3 + normalizedPhaseFromSeed(behavior.stableSeed(), 42);

        float microVariation = (float) Math.sin(microPhase) * 0.015f;
        float mesoVariation = (float) Math.sin(mesoPhase) * 0.025f;
        float macroVariation = (float) Math.sin(macroPhase) * 0.035f;

        float variationScale = 0.5f + behavior.volatilityMultiplier() * 0.5f;
        return (microVariation + mesoVariation + macroVariation) * variationScale;
    }

    private float computeAmbientSocialComfort(BehaviorSnapshot behavior, ComputationSnapshot snapshot) {
        float comfort = 0f;
        if (hasRecentSocialComfort(behavior.lastSocialTick(), behavior.now(), snapshot.lonelySocialGraceTicks())) {
            comfort = Math.max(comfort, 0.4f);
        }

        if (hasRecentPackComfort(behavior.now(), behavior.lastPackTick(), behavior.lastPackStrength(),
                behavior.lastPackWeightedStrength(), behavior.lastPackAllies(), snapshot.lonelyPackGraceTicks(),
                snapshot.lonelyPackStrengthThreshold())) {
            float familiarity = Math.max(behavior.lastPackStrength(), behavior.lastPackWeightedStrength());
            comfort = Math.max(comfort, 0.55f + MathHelper.clamp(familiarity, 0f, 1f) * 0.35f);
        }

        if (behavior.ownerPresent() && behavior.ownerDistanceSquared() <= 144.0) {
            float proximityScale = (float) (1.0 - Math.min(1.0, behavior.ownerDistanceSquared() / 144.0));
            comfort = Math.max(comfort, (0.25f + behavior.bondStrengthNormalized() * 0.25f) * proximityScale);
        }

        return MathHelper.clamp(comfort, 0f, 1f);
    }

    private boolean hasRecentSocialComfort(long lastSocialTick, long now, long grace) {
        return lastSocialTick > 0L && (now - lastSocialTick) <= grace;
    }

    private boolean hasRecentPackComfort(long now,
                                         long lastPackTick,
                                         float lastPackStrength,
                                         float lastPackWeighted,
                                         int lastPackAllies,
                                         long graceTicks,
                                         float strengthThreshold) {
        if (lastPackTick <= 0L) {
            return false;
        }
        if ((now - lastPackTick) > graceTicks) {
            return false;
        }
        if (lastPackAllies > 0
                && (lastPackStrength >= strengthThreshold || lastPackWeighted >= strengthThreshold)) {
            return true;
        }
        return lastPackStrength >= strengthThreshold || lastPackWeighted >= strengthThreshold;
    }

    private BehaviouralEnergyProfile buildEnergyProfile(float momentum,
                                                        float physicalActivity,
                                                        float mentalActivity,
                                                        float socialActivity,
                                                        float physicalStamina,
                                                        float mentalFocus,
                                                        float socialCharge) {
        MobEntity petEntity = parent.getPetEntity();
        float multiplier = resolveEnergyMultiplier(petEntity);
        return applyEnergyMultiplier(multiplier,
                momentum,
                physicalActivity,
                mentalActivity,
                socialActivity,
                physicalStamina,
                mentalFocus,
                socialCharge);
    }

    private float resolveEnergyMultiplier(@Nullable MobEntity petEntity) {
        if (petEntity == null) {
            return 1f;
        }
        return SpeciesTraits.getEnergyMultiplier(petEntity);
    }

    private static BehaviouralEnergyProfile applyEnergyMultiplier(float multiplier,
                                                          float momentum,
                                                          float physicalActivity,
                                                          float mentalActivity,
                                                          float socialActivity,
                                                          float physicalStamina,
                                                          float mentalFocus,
                                                          float socialCharge) {
        float scaledMomentum = MathHelper.clamp(momentum * multiplier, 0f, 1f);
        float scaledPhysicalActivity = MathHelper.clamp(physicalActivity * multiplier, 0f, 1f);
        float scaledMentalActivity = MathHelper.clamp(mentalActivity * multiplier, 0f, 1f);
        float scaledSocialActivity = MathHelper.clamp(socialActivity * multiplier, 0f, 1f);
        float scaledPhysicalStamina = MathHelper.clamp(physicalStamina * multiplier, 0f, 1f);
        float scaledMentalFocus = MathHelper.clamp(mentalFocus * multiplier, 0f, 1f);
        float scaledSocialCharge = MathHelper.clamp(socialCharge * multiplier, 0f, 1f);

        float normalizedPhysical = MathHelper.clamp(scaledPhysicalActivity * 0.4f, 0f, 1f);
        float normalizedMental = MathHelper.clamp(scaledMentalActivity * 0.3f, 0f, 1f);
        float normalizedSocial = MathHelper.clamp(scaledSocialActivity * 0.3f, 0f, 1f);
        return new BehaviouralEnergyProfile(
                scaledMomentum,
                scaledPhysicalActivity,
                scaledMentalActivity,
                scaledSocialActivity,
                normalizedPhysical,
                normalizedMental,
                normalizedSocial,
                scaledPhysicalStamina,
                scaledMentalFocus,
                scaledSocialCharge
        );
    }

    private void applyResult(ComputationSnapshot snapshot, ComputationResult result) {
        if (result.resetToCalm()) {
            resetToCalmBaseline();
            applyBehaviorResult(result.behaviorResult());
            applyRecordUpdates(snapshot, result);
            lastAppliedTick = resolveWorldTime(snapshot.timestamp());
            runPostApplyCallbacks();
            return;
        }

        lastNormalizedWeights.clear();
        lastNormalizedWeights.putAll(result.normalizedTargetBlend());

        moodBlend.clear();
        moodBlend.putAll(result.finalBlend());
        invalidateCaches();

        for (Map.Entry<PetComponent.Emotion, Float> entry : result.recordWeights().entrySet()) {
            EmotionRecord record = emotionRecords.get(entry.getKey());
            if (record != null) {
                record.weight = entry.getValue();
            }
        }

        updateEmotionPalette(result.weightedCandidates());
        updateAnimationIntensity(result.weightedCandidates());

        applyRecordUpdates(snapshot, result);

        PetComponent.Mood previousMood = snapshot.previousMood();
        float previousStrength = snapshot.previousStrength();
        PetComponent.Mood bestMood = PetComponent.Mood.CALM;
        float bestStrength = 0f;
        for (Map.Entry<PetComponent.Mood, Float> entry : moodBlend.entrySet()) {
            float strength = entry.getValue();
            if (strength > bestStrength) {
                bestStrength = strength;
                bestMood = entry.getKey();
            }
        }

        float momentumBand = Math.max((float) snapshot.cachedSwitchMargin(), computeMomentumBand(previousStrength));
        if (bestMood != previousMood && (bestStrength - previousStrength) < momentumBand) {
            currentMood = previousMood;
        } else {
            currentMood = bestMood;
        }

        PetComponent.Emotion bestEmotion = computeDominantEmotion();
        cachedDominantEmotion = bestEmotion;
        dominantEmotionDirty = false;

        float currentStrength = moodBlend.getOrDefault(currentMood, 0f);
        updateDominantHistory(currentStrength);
        updateMoodLevel(currentStrength);
        parent.notifyMoodBlendUpdated();
        parent.notifyEmotionSampleUpdated();
        applyBehaviorResult(result.behaviorResult());
        lastAppliedTick = resolveWorldTime(snapshot.timestamp());
        runPostApplyCallbacks();
    }

    private void applyRecordUpdates(ComputationSnapshot snapshot, ComputationResult result) {
        boolean recordsMutated = false;
        if (result.recordUpdates() != null && !result.recordUpdates().isEmpty()) {
            for (Map.Entry<PetComponent.Emotion, EmotionRecordUpdate> entry : result.recordUpdates().entrySet()) {
                EmotionRecordUpdate update = entry.getValue();
                EmotionRecord record = emotionRecords.computeIfAbsent(entry.getKey(),
                        key -> new EmotionRecord(key, snapshot.timestamp()));
                record.intensity = update.intensity();
                record.impactBudget = update.impactBudget();
                record.cadenceEMA = update.cadenceEMA();
                record.volatilityEMA = update.volatilityEMA();
                record.peakEMA = update.peakEMA();
                record.homeostasisBias = update.homeostasisBias();
                record.sensitisationGain = update.sensitisationGain();
                record.habituationSlope = update.habituationSlope();
                record.contagionShare = update.contagionShare();
                record.relationshipGuard = update.relationshipGuard();
                record.dangerWindow = update.dangerWindow();
                record.appraisalConfidence = update.appraisalConfidence();
                record.lastEventTime = update.lastEventTime();
                record.lastUpdateTime = update.lastUpdateTime();
                recordsMutated = true;
            }
        }

        if (result.recordsToRemove() != null && !result.recordsToRemove().isEmpty()) {
            for (PetComponent.Emotion emotion : result.recordsToRemove()) {
                emotionRecords.remove(emotion);
                recordsMutated = true;
            }
        }

        if (recordsMutated) {
            markEmotionCachesDirty();
        }
    }

    private void applyBehaviorResult(@Nullable BehaviorResult behavior) {
        if (behavior == null) {
            return;
        }
        lastMomentumUpdate = behavior.lastMomentumUpdate();
        behavioralMomentum = behavior.behavioralMomentum();
        momentumInertia = behavior.momentumInertia();
        physicalActivity = behavior.physicalActivity();
        mentalActivity = behavior.mentalActivity();
        socialActivity = behavior.socialActivity();
        restActivity = behavior.restActivity();
        recentPhysicalBurst = behavior.recentPhysicalBurst();
        physicalStamina = behavior.physicalStamina();
        mentalFocus = behavior.mentalFocus();
        socialCharge = behavior.socialCharge();
        energyProfileDirty = behavior.energyProfileDirty();
        if (behavior.energyProfile() != null) {
            cachedEnergyProfile = behavior.energyProfile();
            energyProfileDirty = false;
            parent.notifyEnergyProfileUpdated();
        }
    }

    public boolean isAsyncComputationInFlight() {
        return asyncComputationInFlight;
    }

    public void onNextResultApplied(long thresholdTick, Runnable callback) {
        if (callback == null) {
            return;
        }
        long applied = lastAppliedTick;
        if (!asyncComputationInFlight) {
            if (!dirty) {
                callback.run();
                return;
            }
            long clampedThreshold = Math.max(0L, thresholdTick);
            postApplyCallbacks.addLast(new PendingApplyCallback(clampedThreshold, callback));
            return;
        }
        if (applied >= thresholdTick) {
            callback.run();
            return;
        }
        long clampedThreshold = Math.max(0L, thresholdTick);
        postApplyCallbacks.addLast(new PendingApplyCallback(clampedThreshold, callback));
    }

    public boolean shouldDeferStimulusDrain() {
        return isAsyncPipelineEnabled() && asyncComputationInFlight;
    }

    private void runPostApplyCallbacks() {
        if (postApplyCallbacks.isEmpty()) {
            return;
        }
        long applied = lastAppliedTick;
        Iterator<PendingApplyCallback> iterator = postApplyCallbacks.iterator();
        while (iterator.hasNext()) {
            PendingApplyCallback pending = iterator.next();
            if (applied >= pending.thresholdTick()) {
                try {
                    pending.callback().run();
                } finally {
                    iterator.remove();
                }
            }
        }
    }

    private record PendingApplyCallback(long thresholdTick, Runnable callback) {}


    private void resetToCalmBaseline() {
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            moodBlend.put(mood, 0f);
        }
        moodBlend.put(PetComponent.Mood.CALM, 1f);
        currentMood = PetComponent.Mood.CALM;
        moodLevel = 0;
        previousMoodSnapshot = currentMood;
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
        invalidateCaches();
    }

    private void updateMoodLevel(float currentStrength) {
        // Get mood-specific thresholds if available, otherwise use default
        PetComponent.Mood moodEvaluated = currentMood;
        float[] thresholds = getMoodSpecificThresholds(moodEvaluated);

        int lastLevel = moodLevel;

        // Apply buildup multiplier based on emotional momentum
        float buildupMultiplier = computeBuildupMultiplier(moodEvaluated, currentStrength);
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
        int newLevel = applyLevelHysteresis(rawLevel, effectiveStrength, thresholds, lastLevel);

        int clampedLevel = MathHelper.clamp(newLevel, 0, thresholds.length);
        // Update level history for habituation tracking (only if level actually changed)
        if (clampedLevel != lastLevel) {
            updateLevelHistory(clampedLevel, lastMoodUpdate);
        }

        moodLevel = clampedLevel;
        previousMoodLevel = clampedLevel;
        previousMoodStrength = currentStrength;
        previousMoodSnapshot = moodEvaluated;
    }

    private void updateDominantHistory(float strength) {
        if (dominantHistory.size() >= MOMENTUM_HISTORY_SIZE) {
            dominantHistory.removeFirst();
        }
        dominantHistory.add(MathHelper.clamp(strength, 0f, 1f));
    }

    private void refreshEnergyProfile() {
        cachedEnergyProfile = buildEnergyProfile(
            behavioralMomentum,
            physicalActivity,
            mentalActivity,
            socialActivity,
            physicalStamina,
            mentalFocus,
            socialCharge
        );
        energyProfileDirty = false;
        parent.notifyEnergyProfileUpdated();
    }

    private float normalizedBondStrength() {
        return MathHelper.clamp(parent.getBondStrength() / 4000f, 0f, 1f);
    }
    
    /**
     * Calculates inertia factor for smooth momentum transitions.
     */
    private static double normalizedPhaseFromSeed(long seed, int rotation) {
        long rotated = Long.rotateLeft(seed, rotation);
        long mask = (1L << 53) - 1;
        long fractionBits = rotated & mask;
        double normalized = fractionBits / (double)(1L << 53);
        return normalized * (Math.PI * 2);
    }
    
    /**
     * Applies behavioral momentum influence to mood blend.
     */
    private void applyBehavioralMomentumInfluence() {
        applyBehavioralMomentumInfluence(moodBlend, behavioralMomentum);
    }

    private void applyBehavioralMomentumInfluence(EnumMap<PetComponent.Mood, Float> blend, float momentum) {
        if (blend == null) {
            return;
        }
        // High momentum (hyperactive)
        if (momentum > 0.65f) {
            float hyperFactor = (momentum - 0.65f) / 0.35f;
            blend.compute(PetComponent.Mood.CALM, (k, v) ->
                v == null ? 0f : v * (1f - hyperFactor * 0.4f));
            blend.compute(PetComponent.Mood.RESTLESS, (k, v) ->
                (v == null ? 0f : v) + hyperFactor * 0.2f);
        }
        // Low momentum (tired)
        else if (momentum < 0.35f) {
            float tiredFactor = (0.35f - momentum) / 0.35f;
            blend.compute(PetComponent.Mood.CALM, (k, v) ->
                (v == null ? 0f : v) + tiredFactor * 0.3f);
            blend.compute(PetComponent.Mood.PLAYFUL, (k, v) ->
                v == null ? 0f : v * (1f - tiredFactor * 0.5f));
        }
    }
    
    private float computeMomentumBand(float previousStrength) {
        if (dominantHistory.isEmpty()) {
            return Math.max(0.08f, previousStrength * 0.25f);
        }
        double mean = dominantHistory.stream().mapToDouble(Float::floatValue).average().orElse(0.0);
        double variance = dominantHistory.stream()
            .mapToDouble(v -> {
                double diff = (v - mean);
                return diff * diff;
            })
            .average().orElse(0.0);
        // Safety: ensure variance is non-negative before sqrt
        variance = Math.max(0.0, variance);
        float stddev = (float) Math.sqrt(variance);
        // Clamp stddev to reasonable range for mood switching
        stddev = MathHelper.clamp(stddev, 0.01f, 0.5f);
        return Math.max(0.05f, stddev); // Phase 2 tuning
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

    private void updateEmotionPalette(List<WeightedCandidate> weighted) {
        if (weighted == null || weighted.isEmpty()) {
            if (!paletteBlend.isEmpty()) {
                paletteBlend.clear();
            }
            stagePalette(Collections.emptyList());
            return;
        }

        EnumMap<PetComponent.Emotion, Float> target = new EnumMap<>(PetComponent.Emotion.class);
        float total = 0f;
        for (WeightedCandidate candidate : weighted) {
            float weight = Math.max(0f, candidate.signal());
            if (weight <= EPSILON) {
                continue;
            }
            target.merge(candidate.emotion(), weight, Float::sum);
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
        // Defensive guard: EnumMap entry snapshots can surface null placeholders during async mood updates.
        entries.removeIf(entry -> entry == null || entry.getKey() == null || entry.getValue() == null);
        if (entries.isEmpty()) {
            stagePalette(Collections.emptyList());
            return;
        }
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

    private void updateAnimationIntensity(List<WeightedCandidate> weighted) {
        float total = 0f;
        float accum = 0f;
        for (WeightedCandidate candidate : weighted) {
            float weight = Math.max(0f, candidate.signal());
            if (weight <= EPSILON) {
                continue;
            }
            total += weight;
            accum += weight * MathHelper.clamp(candidate.intensity(), 0f, 1f);
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
        
        // Intensity scaling: higher intensity = faster breathing (more agitated)
        float minScale;
        if (index == 0) {
            minScale = 0.8f;   // Level 1: subtle variation
        } else if (index == 1) {
            minScale = 0.66f;  // Level 2: moderate variation
        } else {
            minScale = 0.55f;  // Level 3: dramatic variation
        }
        float scale = MathHelper.lerp(intensity, 1f, minScale);
        
        // Nature modifier: volatile pets breathe faster, calm pets slower
        float volatilityMod = parent.getNatureVolatilityMultiplier();
        scale *= (2.0f - volatilityMod); // Inverse: high volatility = lower scale = faster breathing
        scale = MathHelper.clamp(scale, 0.4f, 1.2f);
        
        int scaled = Math.round(base * scale);
        return Math.max(40, scaled);
    }

    private void applyOpponentTransfers(List<Candidate> weighted,
                                        float weightCap,
                                        float natureVolatility,
                                        float natureResilience,
                                        float opponentTransferMax,
                                        float reboundGain) {
        if (weighted.size() < 2) {
            return;
        }

        Map<PetComponent.Emotion, Candidate> lookup = weighted.stream()
                .collect(Collectors.toMap(c -> c.record.emotion(), c -> c));

        List<OpponentConflict> conflicts = new ArrayList<>();
        for (Candidate candidate : weighted) {
            EnumSet<PetComponent.Emotion> opponents = opponentPairs.get(candidate.record.emotion());
            if (opponents == null || opponents.isEmpty()) continue;
            for (PetComponent.Emotion opponent : opponents) {
                if (!lookup.containsKey(opponent)) continue;
                if (candidate.record.emotion().ordinal() >= opponent.ordinal()) continue;
                Candidate other = lookup.get(opponent);
                float combined = candidate.signal + other.signal;
                conflicts.add(new OpponentConflict(candidate, other, combined));
            }
        }

        conflicts.sort(Comparator.comparingDouble((OpponentConflict c) -> c.combinedWeight).reversed());

        float safeResilience = MathHelper.clamp(natureResilience, 0.1f, 3.0f);
        float safeVolatility = MathHelper.clamp(natureVolatility, 0.1f, 3.0f);

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

            float baseTransfer = Math.min(opponentTransferMax, 0.15f + 0.1f * difference);
            float natureScale = MathHelper.clamp(safeVolatility / safeResilience, 0.70f, 1.30f);
            float transferFactor = baseTransfer * MathHelper.clamp(natureScale, 0.70f, 1.30f); // Phase 2 tuning
            transferFactor = MathHelper.clamp(transferFactor, 0.05f, 0.45f);

            float transfer = donor.signal * transferFactor;
            if (transfer <= EPSILON) continue;

            float avgConfidence = (first.record.appraisalConfidence() + second.record.appraisalConfidence()) * 0.5f;
            float reboundBase = 0.2f + reboundGain * avgConfidence;
            float tunedRebound = reboundBase * safeResilience;
            float rebound = transfer * MathHelper.clamp(tunedRebound, 0.1f, 0.5f);

            donor.signal = Math.max(0f, donor.signal - transfer);
            receiver.signal = Math.min(weightCap, receiver.signal + transfer * 0.85f);
            donor.signal = Math.max(0f, donor.signal + rebound * 0.2f);
        }
    }

    private float computeFreshness(SnapshotEmotionRecord record, long now) {
        if (record.lastEventTime() <= 0L) {
            return 0f;
        }
        float cadence = record.cadenceEMA() > 0f ? record.cadenceEMA() : cachedHabituationBase;
        float age = Math.max(0f, now - record.lastEventTime());
        return (float) Math.exp(-age / Math.max(1f, cadence));
    }

    private float computeFreshness(ProcessedEmotionRecord record, long now, float fallbackCadence) {
        if (record.lastEventTime() <= 0L) {
            return 0f;
        }
        float cadence = record.cadenceEMA() > 0f ? record.cadenceEMA() : fallbackCadence;
        float age = Math.max(0f, now - record.lastEventTime());
        return (float) Math.exp(-age / Math.max(1f, cadence));
    }

    private float computeFreshness(EmotionRecord record, long now) {
        if (record.lastEventTime <= 0L) {
            return 0f;
        }
        float cadence = record.cadenceEMA > 0f ? record.cadenceEMA : cachedHabituationBase;
        float age = Math.max(0f, now - record.lastEventTime);
        return (float) Math.exp(-age / Math.max(1f, cadence));
    }

    private float computeSignal(SnapshotEmotionRecord record, float freshness, float frequency) {
        float intensity = MathHelper.clamp(record.intensity(), 0f, 1f);
        float base = intensity * (0.35f + 0.65f * MathHelper.clamp(freshness, 0f, 1f));
        float budget = Math.max(0f, record.impactBudget());
        float freqComponent = 0.3f * (float) Math.sqrt(Math.max(0f, frequency * budget));
        return base + freqComponent;
    }

    private float computeSignal(ProcessedEmotionRecord record, float freshness, float frequency) {
        float intensity = MathHelper.clamp(record.intensity(), 0f, 1f);
        float base = intensity * (0.35f + 0.65f * MathHelper.clamp(freshness, 0f, 1f));
        float budget = Math.max(0f, record.impactBudget());
        float freqComponent = 0.3f * (float) Math.sqrt(Math.max(0f, frequency * budget));
        return base + freqComponent;
    }

    private void refreshContextGuards(EmotionRecord record, long now, long delta) {
        long bond = parent.getBondStrength();
        float guardModifier = parent.getNatureGuardModifier();
        double bondZ = (bond - 2000.0) / cachedRelationshipVariance;
        float bondMultiplier = (float) MathHelper.clamp(1.0 + bondZ * 0.35, 0.75, 1.25);
        Long lastPet = parent.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class);
        float carePulse = 0f;
        if (lastPet != null && lastPet > 0) {
            float age = Math.max(0f, now - lastPet);
            carePulse = (float) Math.exp(-age / cachedCarePulseHalfLife);
        }
        float careMultiplier = 0.85f + 0.3f * carePulse;
        float relationshipTarget = MathHelper.clamp(bondMultiplier * careMultiplier, 0.75f, 1.25f);
        float guardBias = getNatureGuardBias(record.emotion);
        float tunedRelationship = MathHelper.clamp(relationshipTarget * guardModifier * guardBias, 0.6f, 1.6f);
        record.relationshipGuard = MathHelper.lerp(0.25f,
            record.relationshipGuard <= 0f ? RELATIONSHIP_BASE : record.relationshipGuard,
            tunedRelationship);

        long lastDangerTick = parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class, Long.MIN_VALUE);
        int dangerStreak = parent.getStateData(PetComponent.StateKeys.THREAT_SENSITIZED_STREAK, Integer.class, 0);
        float dangerMultiplier = 1.0f;
        if (lastDangerTick > Long.MIN_VALUE && lastDangerTick <= now) {
            float dangerAge = Math.max(0f, now - lastDangerTick);
            float streakBias = Math.min(0.35f, dangerStreak * 0.05f);
            float decay = (float) Math.exp(-dangerAge / cachedDangerHalfLife);
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
        if (length <= 0 || data == null) {
            return fallback;
        }
        // Safety: single element array
        if (length == 1) {
            return data[0];
        }
        // Clamp quantile to valid range [0, 1]
        quantile = MathHelper.clamp(quantile, 0f, 1f);
        int targetIndex = (int) MathHelper.clamp(Math.round((length - 1) * quantile), 0, length - 1);
        int left = 0;
        int right = length - 1;
        
        // Quickselect algorithm with safety bounds
        int iterations = 0;
        int maxIterations = length * 2; // Prevent infinite loops
        while (left < right && iterations < maxIterations) {
            int pivotIndex = partition(data, left, right, left + (right - left) / 2);
            if (targetIndex == pivotIndex) {
                return data[targetIndex];
            }
            if (targetIndex < pivotIndex) {
                right = pivotIndex - 1;
            } else {
                left = pivotIndex + 1;
            }
            iterations++;
        }
        // Bounds check before returning
        return (left >= 0 && left < length) ? data[left] : fallback;
    }

    private int partition(float[] data, int left, int right, int pivotIndex) {
        // Bounds validation
        if (data == null || left < 0 || right >= data.length || pivotIndex < left || pivotIndex > right) {
            return left; // Safe fallback
        }
        
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
        // Bounds check to prevent ArrayIndexOutOfBoundsException
        if (data == null || i < 0 || j < 0 || i >= data.length || j >= data.length) {
            return; // Silent fail for safety
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

    @SuppressWarnings("unused")
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

    private float computeImpactCapFromSnapshots(List<SnapshotEmotionRecord> records) {
        if (records == null || records.isEmpty()) {
            return DEFAULT_IMPACT_CAP;
        }
        float[] budgets = new float[records.size()];
        for (int i = 0; i < records.size(); i++) {
            budgets[i] = records.get(i).impactBudget();
        }
        float dynamic = selectQuantile(budgets, budgets.length, 0.95f, DEFAULT_IMPACT_CAP);
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
        float bias = getProfileScale(emotion, 0.55f, 0.35f, 0f, 0.20f, 1.60f); // Phase 2 tuning
        return MathHelper.clamp(bias, 0.0f, 2.0f);
    }

    private float getNatureWeightBias(PetComponent.Emotion emotion) {
        float bias = getProfileScale(emotion, 0.5f, 0.3f, 0.15f, 0.30f, 1.50f); // Phase 2 tuning
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
        // Quirk emotions decay contagion at different rates based on nature profile
        float modifier = getProfileScale(emotion, 0f, 0f, 0.6f, 0.6f, 1.6f);
        return MathHelper.clamp(modifier, 0.1f, 3.0f); // Ensure positive decay modifier
    }
    
    private float getNaturePersistenceModifier(PetComponent.Emotion emotion) {
        // How long emotions persist based on nature alignment
        float modifier = getProfileScale(emotion, 0.4f, 0.25f, 0.15f, 0.6f, 1.5f);
        return MathHelper.clamp(modifier, 0.5f, 2.0f);
    }
    
    private float getNatureRecencyBoostModifier(PetComponent.Emotion emotion) {
        // How much fresh emotions spike based on nature alignment
        float modifier = getProfileScale(emotion, 0.5f, 0.3f, 0.2f, 0.7f, 1.4f);
        return MathHelper.clamp(modifier, 0.5f, 1.5f);
    }

    /**
     * Check if the condition that triggers this emotion is still present.
     * Used for condition-aware decay and persistence bonuses.
     */
    private boolean hasOngoingCondition(PetComponent.Emotion emotion, long now) {
        ensureConfigCache();
        switch (emotion) {
            case SAUDADE:
            case HIRAETH:
                return checkLoneliness(emotion, now);

            case FOREBODING:
            case STARTLE:
            case ANGST:
                // Danger: check if danger occurred recently
                long lastDanger = parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class, Long.MIN_VALUE);
                if (lastDanger == Long.MIN_VALUE) return false;
                return (now - lastDanger) < DANGER_HALF_LIFE; // Within danger window

            case GUARDIAN_VIGIL:
            case PROTECTIVE:
                return checkOwnerDanger(now);

            case CONTENT:
            case CHEERFUL:
                return hasPositiveComfort(emotion, now);

            case ECHOED_RESONANCE:
                // Echoed Resonance persists when both bond AND danger are present
                long bondStrength = parent.getBondStrength();
                long lastDangerEchoed = parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class, Long.MIN_VALUE);
                return bondStrength > 3000 && (lastDangerEchoed != Long.MIN_VALUE && (now - lastDangerEchoed) < DANGER_HALF_LIFE * 1.5f);

            case PACK_SPIRIT:
                return checkPackSpiritProximity(now);

            case ARCANE_OVERFLOW:
                return hasArcaneMomentum(now);

            default:
                // Most emotions don't have persistent conditions
                return false;
        }
    }

    private boolean checkOwnerDanger(long now) {
        long lastHurtTick = parent.getStateData(PetComponent.StateKeys.OWNER_LAST_HURT_TICK, Long.class, 0L);
        float storedSeverity = parent.getStateData(PetComponent.StateKeys.OWNER_LAST_HURT_SEVERITY, Float.class, 0f);
        long lastLowHealthTick = parent.getStateData(PetComponent.StateKeys.OWNER_LAST_LOW_HEALTH_TICK, Long.class, 0L);
        long lastHazardTick = parent.getStateData(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_TICK, Long.class, 0L);
        float lastHazardSeverity = parent.getStateData(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_SEVERITY,
            Float.class, 0f);

        if (lastHurtTick > 0L) {
            long elapsed = now - lastHurtTick;
            if (elapsed <= cachedOwnerDangerGraceTicks) {
                if (elapsed <= cachedOwnerDamageWindow) {
                    return true;
                }
                if (MathHelper.clamp(storedSeverity, 0f, 1f) >= 0.35f) {
                    return true;
                }
            }
        }

        if (lastLowHealthTick > 0L && (now - lastLowHealthTick) <= cachedOwnerLowHealthGraceTicks) {
            return true;
        }

        PlayerEntity owner = parent.getOwner();
        if (owner != null) {
            float hazardSeverity = computeStatusHazardSeverity(owner);
            if (hazardSeverity > 0f) {
                parent.setStateData(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_TICK, now);
                parent.setStateData(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_SEVERITY, hazardSeverity);
                lastHazardTick = now;
                lastHazardSeverity = hazardSeverity;
            } else if (lastHazardTick > 0L && (now - lastHazardTick) > cachedOwnerStatusHazardGraceTicks) {
                parent.clearStateData(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_TICK);
                parent.clearStateData(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_SEVERITY);
                lastHazardTick = 0L;
                lastHazardSeverity = 0f;
            }
        }

        if (lastHazardTick > 0L && (now - lastHazardTick) <= cachedOwnerStatusHazardGraceTicks) {
            if (MathHelper.clamp(lastHazardSeverity, 0f, 1f) >= cachedOwnerStatusHazardThreshold) {
                return true;
            }
        }

        if (owner == null) {
            if (lastHazardTick > 0L && (now - lastHazardTick) > cachedOwnerStatusHazardGraceTicks) {
                parent.clearStateData(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_TICK);
                parent.clearStateData(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_SEVERITY);
            }
            return false;
        }
        if (!owner.isAlive()) {
            return true;
        }

        float ownerHealthRatio = getHealthRatio(owner);
        OwnerCombatState combatState = OwnerCombatState.get(owner);
        boolean recentlyDamaged = combatState != null && combatState.recentlyDamaged(now, cachedOwnerDamageWindow);
        boolean inCombat = combatState != null && combatState.isInCombat();

        if (ownerHealthRatio <= cachedOwnerCriticalHealthThreshold) {
            parent.setStateData(PetComponent.StateKeys.OWNER_LAST_LOW_HEALTH_TICK, now);
            parent.setStateData(PetComponent.StateKeys.OWNER_LAST_HEALTH_RATIO, ownerHealthRatio);
            if (recentlyDamaged) {
                float severity = MathHelper.clamp(1f - ownerHealthRatio, 0f, 1f);
                parent.setStateData(PetComponent.StateKeys.OWNER_LAST_HURT_TICK, now);
                parent.setStateData(PetComponent.StateKeys.OWNER_LAST_HURT_SEVERITY,
                    Math.max(storedSeverity, severity));
            }
            return true;
        }

        if (ownerHealthRatio <= cachedOwnerLowHealthThreshold && (recentlyDamaged || inCombat)) {
            parent.setStateData(PetComponent.StateKeys.OWNER_LAST_LOW_HEALTH_TICK, now);
            parent.setStateData(PetComponent.StateKeys.OWNER_LAST_HEALTH_RATIO, ownerHealthRatio);
            if (recentlyDamaged) {
                float severity = MathHelper.clamp(1f - ownerHealthRatio, 0f, 1f);
                parent.setStateData(PetComponent.StateKeys.OWNER_LAST_HURT_TICK, now);
                parent.setStateData(PetComponent.StateKeys.OWNER_LAST_HURT_SEVERITY,
                    Math.max(storedSeverity, severity));
            }
            return true;
        }

        if (recentlyDamaged) {
            float severity = MathHelper.clamp(1f - ownerHealthRatio, 0f, 1f);
            parent.setStateData(PetComponent.StateKeys.OWNER_LAST_HURT_TICK, now);
            parent.setStateData(PetComponent.StateKeys.OWNER_LAST_HURT_SEVERITY,
                Math.max(storedSeverity, severity));
            parent.setStateData(PetComponent.StateKeys.OWNER_LAST_HEALTH_RATIO, ownerHealthRatio);
            return true;
        }

        if (!inCombat && ownerHealthRatio >= (cachedOwnerLowHealthThreshold + 0.15f)) {
            parent.clearStateData(PetComponent.StateKeys.OWNER_LAST_LOW_HEALTH_TICK);
        }

        return false;
    }

    private boolean checkLoneliness(PetComponent.Emotion emotion, long now) {
        long lastNearTick = parent.getStateData(PetComponent.StateKeys.OWNER_LAST_NEARBY_TICK, Long.class, 0L);
        long lastSeenTick = parent.getStateData(PetComponent.StateKeys.OWNER_LAST_SEEN_TICK, Long.class, 0L);
        float lastSeenDistance = parent.getStateData(PetComponent.StateKeys.OWNER_LAST_SEEN_DISTANCE, Float.class,
            Float.MAX_VALUE);
        String lastSeenDimension = parent.getStateData(PetComponent.StateKeys.OWNER_LAST_SEEN_DIMENSION, String.class, null);
        long lastSocialTick = parent.getStateData(PetComponent.StateKeys.LAST_SOCIAL_BUFFER_TICK, Long.class, 0L);
        long lastPackTick = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_TICK, Long.class, 0L);
        float lastPackStrength = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_STRENGTH, Float.class, 0f);
        float lastPackWeighted = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_WEIGHTED_STRENGTH, Float.class, 0f);
        int lastPackAllies = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_ALLIES, Integer.class, 0);

        if (hasRecentSocialComfort(lastSocialTick, now)) {
            return false;
        }

        PlayerEntity owner = parent.getOwner();
        MobEntity petEntity = parent.getPetEntity();
        ServerWorld petWorld = petEntity != null && petEntity.getEntityWorld() instanceof ServerWorld sw ? sw : null;

        boolean packComfort = hasRecentPackComfort(now, lastPackTick, lastPackStrength, lastPackWeighted, lastPackAllies);
        if (!packComfort && petWorld != null && owner != null && owner.isAlive()) {
            packComfort = refreshPackCompanionship(petWorld, petEntity, owner, now);
            if (packComfort) {
                lastPackTick = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_TICK, Long.class, lastPackTick);
            }
        }

        if (packComfort) {
            return false;
        }

        long grace = emotion == PetComponent.Emotion.HIRAETH
            ? cachedLonelyHiraethGraceTicks
            : cachedLonelySaudadeGraceTicks;
        long offlineGrace = emotion == PetComponent.Emotion.HIRAETH
            ? cachedLonelyOfflineHiraethGraceTicks
            : cachedLonelyOfflineGraceTicks;

        if (owner != null && petEntity != null && owner.isAlive()
            && owner.getEntityWorld() == petEntity.getEntityWorld()) {
            double distanceSq = owner.squaredDistanceTo(petEntity);
            double distance = Math.sqrt(distanceSq);

            parent.setStateData(PetComponent.StateKeys.OWNER_LAST_SEEN_TICK, now);
            parent.setStateData(PetComponent.StateKeys.OWNER_LAST_SEEN_DISTANCE, (float) distance);
            parent.setStateData(PetComponent.StateKeys.OWNER_LAST_SEEN_DIMENSION,
                petWorld != null ? petWorld.getRegistryKey().getValue().toString() : null);

            if (distanceSq <= cachedLonelyComfortRadiusSquared) {
                parent.setStateData(PetComponent.StateKeys.OWNER_LAST_NEARBY_TICK, now);
                parent.setStateData(PetComponent.StateKeys.OWNER_LAST_NEARBY_DISTANCE, (float) distance);
                return false;
            }

            if (distanceSq <= cachedLonelyDistanceThresholdSquared) {
                long sinceProximity = now - Math.max(lastNearTick, lastSeenTick);
                if (sinceProximity <= grace) {
                    return false;
                }
            }

            lastSeenTick = now;
            lastSeenDistance = (float) distance;
        }

        long sinceNear = lastNearTick > 0L ? now - lastNearTick : Long.MAX_VALUE;
        if (sinceNear <= grace) {
            return false;
        }

        boolean ownerMissing = owner == null || owner.isRemoved()
            || petEntity == null
            || owner.getEntityWorld() != petEntity.getEntityWorld();

        if (ownerMissing) {
            long reference = Math.max(lastSeenTick, lastNearTick);
            if (reference <= 0L) {
                return now >= offlineGrace;
            }
            return now - reference >= offlineGrace;
        }

        float distance = Float.isFinite(lastSeenDistance) ? lastSeenDistance : Float.MAX_VALUE;
        boolean farEnough = distance >= cachedLonelyDistanceThreshold;

        if (!farEnough && lastSeenTick > 0L && (now - lastSeenTick) <= grace) {
            return false;
        }

        if (emotion == PetComponent.Emotion.HIRAETH) {
            if (petWorld != null && lastSeenDimension != null) {
                String currentDimension = petWorld.getRegistryKey().getValue().toString();
                if (!currentDimension.equals(lastSeenDimension)) {
                    long sinceSeen = now - Math.max(lastSeenTick, lastNearTick);
                    return sinceSeen >= offlineGrace;
                }
            }
            return farEnough && sinceNear >= grace;
        }

        return farEnough;
    }

    private boolean hasPositiveComfort(PetComponent.Emotion emotion, long now) {
        long lastPet = parent.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class, 0L);
        long lastCrouch = parent.getStateData(PetComponent.StateKeys.LAST_CROUCH_CUDDLE_TICK, Long.class, 0L);
        long lastSocial = parent.getStateData(PetComponent.StateKeys.LAST_SOCIAL_BUFFER_TICK, Long.class, 0L);
        long lastPlay = parent.getStateData(PetComponent.StateKeys.LAST_PLAY_INTERACTION_TICK, Long.class, 0L);
        long lastFeed = parent.getStateData(PetComponent.StateKeys.LAST_FEED_TICK, Long.class, 0L);
        long lastGift = parent.getStateData(PetComponent.StateKeys.LAST_GIFT_TICK, Long.class, 0L);

        boolean petting = hasRecentCue(lastPet, cachedPositivePetGraceTicks, now);
        boolean cuddle = hasRecentCue(lastCrouch, cachedPositiveCrouchGraceTicks, now);
        boolean social = hasRecentCue(lastSocial, cachedPositiveSocialGraceTicks, now);
        boolean play = hasRecentCue(lastPlay, cachedPositivePlayGraceTicks, now);
        boolean feed = hasRecentCue(lastFeed, cachedPositiveFeedGraceTicks, now);
        boolean gift = hasRecentCue(lastGift, cachedPositiveGiftGraceTicks, now);

        if (!(petting || cuddle || social || play || feed || gift)) {
            return false;
        }

        if (emotion == PetComponent.Emotion.CHEERFUL) {
            int strongCues = 0;
            if (petting) strongCues++;
            if (cuddle) strongCues++;
            if (play) strongCues++;
            if (feed) strongCues++;
            if (gift) strongCues++;
            return strongCues >= 2 || social;
        }

        return true;
    }

    private boolean hasRecentCue(long tick, long grace, long now) {
        return tick > 0L && (now - tick) <= grace;
    }

    private boolean hasRecentSocialComfort(long lastSocialTick, long now) {
        return lastSocialTick > 0L && (now - lastSocialTick) <= cachedLonelySocialGraceTicks;
    }

    private boolean hasRecentPackComfort(long now, long lastPackTick, float lastPackStrength,
                                         float lastPackWeighted, int lastPackAllies) {
        if (lastPackTick <= 0L) {
            return false;
        }
        if ((now - lastPackTick) > cachedLonelyPackGraceTicks) {
            return false;
        }
        if (lastPackAllies > 0
            && (lastPackStrength >= cachedLonelyPackStrengthThreshold
            || lastPackWeighted >= cachedLonelyPackStrengthThreshold)) {
            return true;
        }
        return lastPackStrength >= cachedLonelyPackStrengthThreshold
            || lastPackWeighted >= cachedLonelyPackStrengthThreshold;
    }

    private boolean refreshPackCompanionship(ServerWorld world,
                                             @Nullable MobEntity petEntity,
                                             PlayerEntity owner,
                                             long now) {
        if (petEntity == null || petEntity.isRemoved()) {
            return false;
        }

        StateManager manager = StateManager.forWorld(world);
        PetSwarmIndex swarmIndex = manager.getSwarmIndex();
        List<PetSwarmIndex.SwarmEntry> entries = swarmIndex.snapshotOwner(owner.getUuid());
        if (entries.isEmpty()) {
            return false;
        }

        Vec3d petPos = new Vec3d(petEntity.getX(), petEntity.getY(), petEntity.getZ());
        double radiusSq = cachedLonelyPackRadiusSquared;
        int allies = 0;
        float closenessSum = 0f;
        float closest = 0f;
        EnumSet<PetRoleType.RoleArchetype> diversity = EnumSet.noneOf(PetRoleType.RoleArchetype.class);

        for (PetSwarmIndex.SwarmEntry entry : entries) {
            MobEntity other = entry.pet();
            if (other == null || other == petEntity || !other.isAlive() || other.isRemoved()) {
                continue;
            }
            PetComponent component = entry.component();
            if (component == null || !component.isOwnedBy(owner)) {
                continue;
            }

            double dx = entry.x() - petPos.x;
            double dy = entry.y() - petPos.y;
            double dz = entry.z() - petPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > radiusSq) {
                continue;
            }

            PetRoleType roleType = component.getRoleType(false);
            if (roleType != null) {
                PetRoleType.RoleArchetype archetype = roleType.archetype();
                if (archetype != null) {
                    diversity.add(archetype);
                }
            }

            double distance = Math.sqrt(distSq);
            double normalized = cachedLonelyPackRadius <= 0d
                ? 0d
                : MathHelper.clamp(distance / cachedLonelyPackRadius, 0d, 1d);
            float closeness = MathHelper.clamp(1f - (float) normalized, 0f, 1f);
            closenessSum += closeness;
            closest = Math.max(closest, closeness);
            allies++;
        }

        if (allies <= 0) {
            return false;
        }

        float average = MathHelper.clamp(closenessSum / Math.max(1, allies), 0f, 1f);
        float weighted = MathHelper.clamp(closenessSum, 0f, allies);
        float strength = MathHelper.clamp(Math.max(average, closest), 0f, 1f);
        float diversityScore = MathHelper.clamp(diversity.size()
            / (float) PetRoleType.RoleArchetype.values().length, 0f, 1f);

        parent.setStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_TICK, now);
        parent.setStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_STRENGTH, strength);
        parent.setStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_WEIGHTED_STRENGTH, weighted);
        parent.setStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_ALLIES, allies);
        parent.setStateData(PetComponent.StateKeys.PACK_LAST_ROLE_DIVERSITY, diversityScore);

        return strength >= cachedLonelyPackStrengthThreshold
            || weighted >= cachedLonelyPackStrengthThreshold;
    }

    private boolean checkPackSpiritProximity(long now) {
        long lastPackTick = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_TICK, Long.class, 0L);
        float lastStrength = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_STRENGTH, Float.class, 0f);
        float lastWeighted = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_WEIGHTED_STRENGTH, Float.class, 0f);
        int lastAllies = parent.getStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_ALLIES, Integer.class, 0);

        MobEntity petEntity = parent.getPetEntity();
        if (petEntity == null || petEntity.getEntityWorld() == null) {
            return wasRecentPackPresence(now, lastPackTick, lastStrength, lastWeighted, lastAllies);
        }

        if (!(petEntity.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return wasRecentPackPresence(now, lastPackTick, lastStrength, lastWeighted, lastAllies);
        }

        PlayerEntity owner = parent.getOwner();
        if (owner == null) {
            return wasRecentPackPresence(now, lastPackTick, lastStrength, lastWeighted, lastAllies);
        }

        StateManager manager = StateManager.forWorld(serverWorld);
        PetSwarmIndex swarmIndex = manager.getSwarmIndex();
        List<PetSwarmIndex.SwarmEntry> entries = swarmIndex.snapshotOwner(owner.getUuid());
        if (entries.isEmpty()) {
            return wasRecentPackPresence(now, lastPackTick, lastStrength, lastWeighted, lastAllies);
        }

        Vec3d petPos = new Vec3d(petEntity.getX(), petEntity.getY(), petEntity.getZ());
        double radiusSq = cachedPackSpiritRadius * cachedPackSpiritRadius;
        int allies = 0;
        float engagementSum = 0f;
        EnumSet<PetRoleType.RoleArchetype> roleMix = EnumSet.noneOf(PetRoleType.RoleArchetype.class);

        for (PetSwarmIndex.SwarmEntry entry : entries) {
            MobEntity other = entry.pet();
            if (other == null || other == petEntity || !other.isAlive() || other.isRemoved()) {
                continue;
            }
            PetComponent component = entry.component();
            if (component == null || !component.isOwnedBy(owner)) {
                continue;
            }
            double dx = entry.x() - petPos.x;
            double dy = entry.y() - petPos.y;
            double dz = entry.z() - petPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > radiusSq) {
                continue;
            }

            PetRoleType roleType = component.getRoleType(false);
            PetRoleType.RoleArchetype archetype = roleType != null ? roleType.archetype() : PetRoleType.RoleArchetype.UTILITY;
            roleMix.add(archetype);

            float closeness = MathHelper.clamp(1f - (float) (distSq / radiusSq), 0f, 1f);
            float engagement = computePackmateEngagement(component, closeness, archetype);
            engagementSum += engagement;
            allies++;
        }

        if (allies >= cachedPackSpiritMinPackmates) {
            float bondBonus = computePackSpiritBondBonus(parent.getBondStrength());
            float diversity = roleMix.isEmpty() ? 0f : (float) roleMix.size() / PetRoleType.RoleArchetype.values().length;
            float averageEngagement = allies > 0 ? engagementSum / allies : 0f;
            averageEngagement = MathHelper.clamp(averageEngagement, 0f, cachedPackSpiritEngagementMax);
            float finalStrength = MathHelper.clamp(averageEngagement + (cachedPackSpiritDiversityBonus * diversity) + bondBonus,
                0f, 1f);

            parent.setStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_TICK, now);
            parent.setStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_STRENGTH, finalStrength);
            parent.setStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_WEIGHTED_STRENGTH,
                MathHelper.clamp(engagementSum, 0f, cachedPackSpiritEngagementMax));
            parent.setStateData(PetComponent.StateKeys.PACK_LAST_NEARBY_ALLIES, allies);
            parent.setStateData(PetComponent.StateKeys.PACK_LAST_ROLE_DIVERSITY,
                MathHelper.clamp(diversity, 0f, 1f));
            return true;
        }

        return wasRecentPackPresence(now, lastPackTick, lastStrength, lastWeighted, lastAllies);
    }

    private boolean wasRecentPackPresence(long now, long lastTick, float lastStrength, float lastWeighted, int lastAllies) {
        if (lastTick <= 0L) {
            return false;
        }
        if ((now - lastTick) > cachedPackSpiritGraceTicks) {
            return false;
        }
        if (lastAllies <= 0) {
            return lastStrength > 0.05f;
        }
        return lastStrength > 0.05f || lastWeighted > 0.05f;
    }

    private float computePackmateEngagement(PetComponent component, float closeness, PetRoleType.RoleArchetype archetype) {
        float roleWeight = cachedPackSpiritRoleWeights.getOrDefault(archetype, 1.0f);
        float protective = MathHelper.clamp(component.getMoodStrength(PetComponent.Mood.PROTECTIVE), 0f, 1f);
        float focused = MathHelper.clamp(component.getMoodStrength(PetComponent.Mood.FOCUSED), 0f, 1f);
        float playful = MathHelper.clamp(component.getMoodStrength(PetComponent.Mood.PLAYFUL), 0f, 1f);
        float moodContribution = protective * cachedPackSpiritProtectiveWeight
            + focused * cachedPackSpiritFocusedWeight
            + playful * cachedPackSpiritPlayfulWeight;
        float combatBonus = component.isInCombat() ? cachedPackSpiritCombatBonus : 0f;
        float engagement = cachedPackSpiritBaseContribution
            + (closeness * cachedPackSpiritClosenessWeight)
            + moodContribution
            + combatBonus;
        engagement *= roleWeight;
        return MathHelper.clamp(engagement, 0f, cachedPackSpiritEngagementMax);
    }

    private boolean hasArcaneMomentum(long now) {
        long lastEnchantTick = parent.getStateData(PetComponent.StateKeys.ARCANE_LAST_ENCHANT_TICK, Long.class, 0L);
        int enchantStreak = parent.getStateData(PetComponent.StateKeys.ARCANE_ENCHANT_STREAK, Integer.class, 0);
        long lastSurgeTick = parent.getStateData(PetComponent.StateKeys.ARCANE_LAST_SURGE_TICK, Long.class, 0L);
        float surgeStrength = parent.getStateData(PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, Float.class, 0f);

        if (lastSurgeTick > 0L && (now - lastSurgeTick) <= cachedArcaneOverflowLingerTicks) {
            if (surgeStrength >= cachedArcaneOverflowMinimumEnergy) {
                return true;
            }
        }

        if (lastEnchantTick > 0L && (now - lastEnchantTick) <= cachedArcaneOverflowStreakGraceTicks) {
            if (enchantStreak >= 2 || surgeStrength >= cachedArcaneOverflowMinimumEnergy * 0.5f) {
                return true;
            }
        }

        MobEntity petEntity = parent.getPetEntity();
        if (!(petEntity instanceof LivingEntity livingPet)) {
            return false;
        }

        ServerWorld serverWorld = livingPet.getEntityWorld() instanceof ServerWorld sw ? sw : null;
        PlayerEntity owner = parent.getOwner();

        float energy = 0f;

        if (enchantStreak > 0) {
            energy += MathHelper.clamp(enchantStreak / 4.0f, 0f, 1f) * 0.4f;
        }

        float petGear = computeEnchantedGearIntensity(livingPet);
        if (petGear > 0f) {
            energy += petGear * cachedArcaneGearPetMultiplier;
        }

        float petStatus = computeBeneficialStatusIntensity(livingPet);
        if (petStatus > 0f) {
            energy += petStatus * cachedArcaneStatusPetMultiplier;
        }

        if (owner != null) {
            float ownerGear = computeEnchantedGearIntensity(owner);
            if (ownerGear > 0f) {
                energy += ownerGear * cachedArcaneGearOwnerMultiplier;
            }
            float ownerStatus = computeBeneficialStatusIntensity(owner);
            if (ownerStatus > 0f) {
                energy += ownerStatus * cachedArcaneStatusOwnerMultiplier;
            }
        }

        float ambientEnergy = 0f;
        if (serverWorld != null) {
            BlockPos currentPos = livingPet.getBlockPos();
            long lastScanTick = parent.getStateData(PetComponent.StateKeys.ARCANE_LAST_SCAN_TICK, Long.class, 0L);
            float cachedAmbient = parent.getStateData(PetComponent.StateKeys.ARCANE_CACHED_AMBIENT_ENERGY, Float.class, 0f);
            BlockPos lastScanPos = parent.getStateData(PetComponent.StateKeys.ARCANE_LAST_SCAN_POS, BlockPos.class);

            boolean usedCache = false;
            if (lastScanTick > 0L && (now - lastScanTick) < cachedArcaneScanCooldownTicks) {
                if (lastScanPos != null) {
                    double movementSq = lastScanPos.toCenterPos().squaredDistanceTo(Vec3d.ofCenter(currentPos));
                    if (movementSq <= cachedArcaneScanMovementThresholdSquared) {
                        ambientEnergy = MathHelper.clamp(cachedAmbient, 0f, 1f);
                        usedCache = true;
                    }
                } else {
                    ambientEnergy = MathHelper.clamp(cachedAmbient, 0f, 1f);
                    usedCache = true;
                }
            }

            StateManager manager = StateManager.forWorld(serverWorld);
            if (!usedCache) {
                StateManager.ArcaneAmbientCache ambientCache = manager.getArcaneAmbientCache();
                StateManager.ArcaneAmbientCache.Sample sharedSample = ambientCache.tryGet(
                    ChunkSectionPos.from(currentPos), currentPos, cachedArcaneOverflowAmbientScanRadius,
                    now, cachedArcaneScanCooldownTicks, cachedArcaneScanMovementThresholdSquared);
                if (sharedSample != null) {
                    ambientEnergy = MathHelper.clamp(sharedSample.energy(), 0f, 1f);
                    parent.setStateData(PetComponent.StateKeys.ARCANE_LAST_SCAN_TICK, sharedSample.tick());
                    parent.setStateData(PetComponent.StateKeys.ARCANE_LAST_SCAN_POS, sharedSample.origin());
                    parent.setStateData(PetComponent.StateKeys.ARCANE_CACHED_AMBIENT_ENERGY, ambientEnergy);
                    usedCache = true;
                }
            }

            if (!usedCache) {
                ambientEnergy = sampleArcaneAmbient(serverWorld, currentPos);
                StateManager.ArcaneAmbientCache.Sample storedSample = manager.getArcaneAmbientCache().store(
                    ChunkSectionPos.from(currentPos), currentPos, cachedArcaneOverflowAmbientScanRadius,
                    now, cachedArcaneScanCooldownTicks, ambientEnergy);
                parent.setStateData(PetComponent.StateKeys.ARCANE_LAST_SCAN_TICK, storedSample.tick());
                parent.setStateData(PetComponent.StateKeys.ARCANE_LAST_SCAN_POS, storedSample.origin());
                parent.setStateData(PetComponent.StateKeys.ARCANE_CACHED_AMBIENT_ENERGY, storedSample.energy());
            }
        }

        energy = MathHelper.clamp(energy + MathHelper.clamp(ambientEnergy, 0f, 1f), 0f, 1f);

        if (energy > surgeStrength) {
            parent.setStateData(PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, energy);
        }

        if (energy >= cachedArcaneOverflowMinimumEnergy) {
            parent.setStateData(PetComponent.StateKeys.ARCANE_LAST_SURGE_TICK, now);
            return true;
        }

        if (lastSurgeTick > 0L && (now - lastSurgeTick) <= cachedArcaneOverflowStatusGraceTicks) {
            return surgeStrength >= cachedArcaneOverflowMinimumEnergy * 0.5f;
        }

        return false;
    }

    private float sampleArcaneAmbient(ServerWorld world, BlockPos origin) {
        float energy = collectArcaneStructureEnergy(world, origin, cachedArcaneOverflowAmbientScanRadius);
        if (isMysticEnvironment(world, origin)) {
            energy += cachedArcaneAmbientMysticBonus;
        }
        return MathHelper.clamp(energy, 0f, 1f);
    }

    private float getHealthRatio(LivingEntity entity) {
        float max = Math.max(1f, entity.getMaxHealth());
        return MathHelper.clamp(entity.getHealth() / max, 0f, 1f);
    }

    private float computePackSpiritBondBonus(long bondStrength) {
        if (bondStrength <= 0L) {
            return 0f;
        }
        float normalized = MathHelper.clamp(bondStrength / 6000f, 0f, 1f);
        return MathHelper.clamp(normalized * cachedPackSpiritBondBonus, 0f, 0.6f);
    }

    private float computeEnchantedGearIntensity(@Nullable LivingEntity entity) {
        if (entity == null) {
            return 0f;
        }

        float total = 0f;
        total += computeStackEnchantments(entity.getMainHandStack());
        total += computeStackEnchantments(entity.getOffHandStack());
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            total += computeStackEnchantments(entity.getEquippedStack(slot));
        }
        return MathHelper.clamp(total, 0f, cachedArcaneGearMaxContribution);
    }

    private float computeStackEnchantments(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0f;
        }
        ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) {
            return 0f;
        }

        float total = 0f;
        for (RegistryEntry<Enchantment> enchantment : enchantments.getEnchantments()) {
            int level = Math.max(0, enchantments.getLevel(enchantment));
            float contribution = cachedArcaneGearBaseWeight
                + cachedArcaneGearLevelWeight * Math.max(0, level);
            total += contribution;
        }
        return Math.max(0f, total);
    }

    private float computeBeneficialStatusIntensity(@Nullable LivingEntity entity) {
        if (entity == null) {
            return 0f;
        }

        float total = 0f;
        int reference = Math.max(1, cachedArcaneStatusDurationReference);
        for (StatusEffectInstance effect : entity.getStatusEffects()) {
            if (effect == null) {
                continue;
            }
            RegistryEntry<StatusEffect> type = effect.getEffectType();
            if (type == null) {
                continue;
            }
            StatusEffect status = type.value();
            if (status.getCategory() != StatusEffectCategory.BENEFICIAL) {
                continue;
            }
            int amplifier = Math.max(0, effect.getAmplifier());
            float durationRatio = MathHelper.clamp(effect.getDuration() / (float) reference, 0f, cachedArcaneStatusDurationCap);
            float contribution = cachedArcaneStatusBaseWeight
                + cachedArcaneStatusAmplifierWeight * (amplifier + 1)
                + cachedArcaneStatusDurationWeight * durationRatio;
            total += contribution;
        }

        return MathHelper.clamp(total, 0f, cachedArcaneStatusMaxContribution);
    }

    public static float computeStatusHazardSeverity(@Nullable LivingEntity entity) {
        if (entity == null) {
            return 0f;
        }

        float severity = 0f;

        if (entity.isOnFire() || entity.getFireTicks() > 0) {
            float fireSeverity = 0.45f + Math.min(0.35f, entity.getFireTicks() / 200f);
            severity = Math.max(severity, fireSeverity);
        }

        if (entity.getFrozenTicks() > 0) {
            float freezeSeverity = 0.35f + Math.min(0.3f, entity.getFrozenTicks() / 140f);
            severity = Math.max(severity, freezeSeverity);
        }

        World world = entity.getEntityWorld();
        if (world != null) {
            BlockPos basePos = entity.getBlockPos();
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            float contactHazard = 0f;

            mutable.set(basePos);
            contactHazard = Math.max(contactHazard, resolveContactHazardSeverity(world, mutable));

            mutable.set(basePos.down());
            contactHazard = Math.max(contactHazard, resolveContactHazardSeverity(world, mutable));

            for (Direction direction : Direction.Type.HORIZONTAL) {
                mutable.set(basePos.getX() + direction.getOffsetX(), basePos.getY(), basePos.getZ() + direction.getOffsetZ());
                contactHazard = Math.max(contactHazard, resolveContactHazardSeverity(world, mutable));
            }

            severity = Math.max(severity, contactHazard);
        }

        for (StatusEffectInstance effect : entity.getStatusEffects()) {
            if (effect == null) {
                continue;
            }
            RegistryEntry<StatusEffect> entry = effect.getEffectType();
            if (entry == null) {
                continue;
            }
            StatusEffect status = entry.value();
            if (status.getCategory() != StatusEffectCategory.HARMFUL) {
                continue;
            }

            int amplifier = Math.max(0, effect.getAmplifier());
            float base = 0.2f + 0.08f * (amplifier + 1);

            if (status == StatusEffects.WITHER) {
                base = 0.75f + 0.12f * (amplifier + 1);
            } else if (status == StatusEffects.POISON) {
                base = 0.55f + 0.08f * (amplifier + 1);
            } else if (status == StatusEffects.SLOWNESS || status == StatusEffects.MINING_FATIGUE) {
                base = 0.35f + 0.05f * amplifier;
            } else if (status == StatusEffects.WEAKNESS || status == StatusEffects.NAUSEA) {
                base = 0.3f + 0.05f * amplifier;
            } else if (status == StatusEffects.BLINDNESS || status == StatusEffects.DARKNESS) {
                base = 0.32f + 0.05f * amplifier;
            } else if (status == StatusEffects.BAD_OMEN) {
                base = 0.4f;
            } else if (status == StatusEffects.UNLUCK) {
                base = 0.28f;
            } else if (status == StatusEffects.HUNGER) {
                base = 0.25f + 0.04f * amplifier;
            } else if (status == StatusEffects.LEVITATION) {
                base = 0.45f + 0.05f * amplifier;
            }

            severity = Math.max(severity, MathHelper.clamp(base, 0f, 1f));
        }

        return MathHelper.clamp(severity, 0f, 1f);
    }

    private static float resolveContactHazardSeverity(World world, BlockPos pos) {
        return resolveContactHazardSeverity(world, pos, world.getBlockState(pos));
    }

    private static float resolveContactHazardSeverity(World world, BlockPos pos, BlockState state) {
        float severity = 0f;

        if (state.isOf(Blocks.CACTUS)) {
            severity = Math.max(severity, 0.55f);
        } else if (state.isOf(Blocks.SWEET_BERRY_BUSH)) {
            severity = Math.max(severity, 0.42f);
        } else if (state.isOf(Blocks.MAGMA_BLOCK)) {
            severity = Math.max(severity, 0.65f);
        } else if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) {
            severity = Math.max(severity, 0.7f);
        } else if (state.isOf(Blocks.POWDER_SNOW)) {
            severity = Math.max(severity, 0.38f);
        }

        if (state.getBlock() instanceof CampfireBlock) {
            if (state.contains(CampfireBlock.LIT) && state.get(CampfireBlock.LIT)) {
                severity = Math.max(severity, 0.5f);
            }
        }

        if (world.getFluidState(pos).isIn(FluidTags.LAVA)) {
            severity = Math.max(severity, 0.95f);
        }

        return severity;
    }

    private float collectArcaneStructureEnergy(ServerWorld world, BlockPos origin, int radius) {
        int clamped = MathHelper.clamp(radius, 1, 6);
        if (clamped <= 0) {
            return 0f;
        }
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        float accumulated = 0f;
        double normalization = Math.max(1.0d, clamped + 0.5d);
        double exponent = Math.max(0.25d, cachedArcaneAmbientDistanceExponent);

        double originCenterX = origin.getX() + 0.5d;
        double originCenterY = origin.getY() + 0.5d;
        double originCenterZ = origin.getZ() + 0.5d;

        for (int dx = -clamped; dx <= clamped; dx++) {
            for (int dy = -clamped; dy <= clamped; dy++) {
                for (int dz = -clamped; dz <= clamped; dz++) {
                    mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockPos samplePos = mutable.toImmutable();
                    BlockState state = world.getBlockState(samplePos);
                    float weight = resolveArcaneStructureWeight(world, samplePos, state);
                    if (weight <= 0f) {
                        continue;
                    }
                    double distance = Math.sqrt(samplePos.getSquaredDistance(originCenterX, originCenterY, originCenterZ));
                    double normalized = MathHelper.clamp(distance / normalization, 0.0d, 1.0d);
                    double falloff = Math.pow(1.0d - normalized, exponent);
                    accumulated += weight * (float) Math.max(0d, falloff);
                }
            }
        }

        return MathHelper.clamp(accumulated, 0f, cachedArcaneAmbientStructureMaxEnergy);
    }

    private float resolveArcaneStructureWeight(ServerWorld world, BlockPos pos, BlockState state) {
        if (state == null) {
            return 0f;
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        float baseWeight = 0f;
        if (id != null) {
            Float configured = cachedArcaneAmbientStructureWeights.get(id);
            if (configured != null) {
                baseWeight = Math.max(0f, configured);
            }
        }
        if (baseWeight <= 0f && isArcaneBlock(state)) {
            baseWeight = Math.max(0f, cachedArcaneAmbientStructureBaseWeight);
        }
        if (baseWeight <= 0f) {
            return 0f;
        }

        if (state.isOf(Blocks.RESPAWN_ANCHOR)) {
            return baseWeight * Math.max(0f, resolveRespawnAnchorMultiplier(state));
        }
        if (state.isOf(Blocks.BEACON)) {
            return baseWeight * Math.max(0f, resolveBeaconMultiplier(world, pos));
        }
        if (state.isOf(Blocks.SCULK_CATALYST)) {
            return baseWeight * Math.max(0f, resolveSculkCatalystMultiplier(world, pos));
        }
        return baseWeight;
    }

    private static Optional<Field> lookupArcaneField(Class<?> type, String name) {
        String key = type.getName() + "#" + name;
        return ARCANE_REFLECTION_FIELD_CACHE.computeIfAbsent(key, ignored -> {
            Class<?> current = type;
            while (current != null) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return Optional.of(field);
                } catch (NoSuchFieldException | SecurityException exception) {
                    current = current.getSuperclass();
                }
            }
            return Optional.empty();
        });
    }

    private static Optional<Method> lookupArcaneZeroArgMethod(Class<?> type, String name) {
        String key = type.getName() + "#" + name;
        return ARCANE_REFLECTION_METHOD_CACHE.computeIfAbsent(key, ignored -> {
            Class<?> current = type;
            while (current != null) {
                try {
                    Method method = current.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return Optional.of(method);
                } catch (NoSuchMethodException | SecurityException exception) {
                    current = current.getSuperclass();
                }
            }
            try {
                Method method = type.getMethod(name);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (ReflectiveOperationException | SecurityException exception) {
                return Optional.empty();
            }
        });
    }

    private float resolveRespawnAnchorMultiplier(BlockState state) {
        if (!state.contains(RespawnAnchorBlock.CHARGES)) {
            return cachedArcaneRespawnAnchorEmptyMultiplier;
        }
        int charges = Math.max(0, state.get(RespawnAnchorBlock.CHARGES));
        if (charges <= 0) {
            return cachedArcaneRespawnAnchorEmptyMultiplier;
        }
        float scaled = cachedArcaneRespawnAnchorEmptyMultiplier + charges * cachedArcaneRespawnAnchorChargeStep;
        return MathHelper.clamp(scaled, 0f, cachedArcaneRespawnAnchorMaxMultiplier);
    }

    private float resolveBeaconMultiplier(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return cachedArcaneBeaconBaseMultiplier;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        int level = 0;
        if (blockEntity != null && blockEntity.getType() == BlockEntityType.BEACON) {
            level = Math.max(0, tryExtractBeaconLevel(blockEntity));
        }
        float multiplier = cachedArcaneBeaconBaseMultiplier + cachedArcaneBeaconPerLevelMultiplier * level;
        return MathHelper.clamp(multiplier, 0f, cachedArcaneBeaconMaxMultiplier);
    }

    private int tryExtractBeaconLevel(BlockEntity blockEntity) {
        int fromField = extractBeaconLevelFromField(blockEntity);
        if (fromField >= 0) {
            return fromField;
        }
        int fromBeaconGetter = extractBeaconLevelFromMethod(blockEntity, "getBeaconLevel");
        if (fromBeaconGetter >= 0) {
            return fromBeaconGetter;
        }
        return Math.max(0, extractBeaconLevelFromMethod(blockEntity, "getLevel"));
    }

    private int extractBeaconLevelFromField(BlockEntity blockEntity) {
        Optional<Field> field = lookupArcaneField(blockEntity.getClass(), "level");
        if (field.isEmpty()) {
            return -1;
        }
        try {
            Object value = field.get().get(blockEntity);
            if (value instanceof Number number) {
                return Math.max(0, number.intValue());
            }
        } catch (IllegalAccessException ignored) {
        }
        return -1;
    }

    private int extractBeaconLevelFromMethod(BlockEntity blockEntity, String methodName) {
        Optional<Method> method = lookupArcaneZeroArgMethod(blockEntity.getClass(), methodName);
        if (method.isEmpty()) {
            return -1;
        }
        try {
            Object result = method.get().invoke(blockEntity);
            if (result instanceof Number number) {
                return Math.max(0, number.intValue());
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return -1;
    }

    private float resolveSculkCatalystMultiplier(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return cachedArcaneCatalystInactiveMultiplier;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity == null || blockEntity.getType() != BlockEntityType.SCULK_CATALYST) {
            return cachedArcaneCatalystInactiveMultiplier;
        }
        long lastBloomTick = extractSculkCatalystBloomTick(blockEntity);
        if (lastBloomTick == Long.MIN_VALUE) {
            return cachedArcaneCatalystInactiveMultiplier;
        }
        long now = world.getTime();
        long window = Math.max(1L, cachedArcaneCatalystRecentBloomTicks);
        long delta = Math.max(0L, now - lastBloomTick);
        if (delta == 0L) {
            return MathHelper.clamp(cachedArcaneCatalystActiveMultiplier, 0f, Math.max(cachedArcaneCatalystActiveMultiplier, cachedArcaneCatalystInactiveMultiplier));
        }
        if (delta >= window) {
            return cachedArcaneCatalystInactiveMultiplier;
        }
        double progress = 1.0d - ((double) delta / (double) window);
        float blended = cachedArcaneCatalystInactiveMultiplier
            + (float) progress * (cachedArcaneCatalystActiveMultiplier - cachedArcaneCatalystInactiveMultiplier);
        float max = Math.max(cachedArcaneCatalystActiveMultiplier, cachedArcaneCatalystInactiveMultiplier);
        return MathHelper.clamp(blended, 0f, max);
    }

    private long extractSculkCatalystBloomTick(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return Long.MIN_VALUE;
        }
        Optional<Method> lastBloomTickMethod = lookupArcaneZeroArgMethod(blockEntity.getClass(), "getLastBloomTick");
        if (lastBloomTickMethod.isPresent()) {
            try {
                Object result = lastBloomTickMethod.get().invoke(blockEntity);
                if (result instanceof Number number) {
                    return number.longValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        Optional<Method> lastBloomTimeMethod = lookupArcaneZeroArgMethod(blockEntity.getClass(), "getLastBloomTime");
        if (lastBloomTimeMethod.isPresent()) {
            try {
                Object result = lastBloomTimeMethod.get().invoke(blockEntity);
                if (result instanceof Number number) {
                    return number.longValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        Optional<Field> lastBloomTickField = lookupArcaneField(blockEntity.getClass(), "lastBloomTick");
        if (lastBloomTickField.isPresent()) {
            try {
                Object value = lastBloomTickField.get().get(blockEntity);
                if (value instanceof Number number) {
                    return number.longValue();
                }
            } catch (IllegalAccessException ignored) {
            }
        }

        Optional<Field> lastBloomTimeField = lookupArcaneField(blockEntity.getClass(), "lastBloomTime");
        if (lastBloomTimeField.isPresent()) {
            try {
                Object value = lastBloomTimeField.get().get(blockEntity);
                if (value instanceof Number number) {
                    return number.longValue();
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return Long.MIN_VALUE;
    }

    private boolean isArcaneBlock(BlockState state) {
        return state.isOf(Blocks.ENCHANTING_TABLE)
            || state.isOf(Blocks.AMETHYST_BLOCK)
            || state.isOf(Blocks.RESPAWN_ANCHOR)
            || state.isOf(Blocks.BEACON)
            || state.isOf(Blocks.BREWING_STAND)
            || state.isOf(Blocks.END_PORTAL_FRAME)
            || state.isOf(Blocks.SCULK_CATALYST);
    }

    private boolean isMysticEnvironment(ServerWorld world, BlockPos pos) {
        if (world.getRegistryKey() == World.END || world.getRegistryKey() == World.NETHER) {
            return true;
        }
        RegistryEntry<Biome> biome = world.getBiome(pos);
        if (biome.isIn(BiomeTags.IS_END) || biome.isIn(BiomeTags.IS_NETHER)) {
            return true;
        }
        return biome.matchesKey(BiomeKeys.LUSH_CAVES) || biome.matchesKey(BiomeKeys.DEEP_DARK);
    }

    /**
     * Compute emotion-specific context modulation based on bond, danger, health, etc.
     * Replaces the old uniform multiplication with targeted adjustments.
     */
    private float computeEmotionSpecificContextModulation(EmotionRecord record, long now) {
        float modulation = 0f;
        PetComponent.Emotion emotion = record.emotion;
        
        // Nature modifiers for context-aware scaling
        float resilienceMod = parent.getNatureResilienceMultiplier();

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
                
            // All other emotions: no bond-based modulation
            default:
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

            case GUARDIAN_VIGIL:
            case PACK_SPIRIT:
                // Danger increases Guardian Vigil resolve and pack unity
                modulation += 0.35f * Math.max(0f, dangerBoost);
                break;
                
            case ECHOED_RESONANCE:
                // Echoed Resonance is born from danger - strongly amplified
                modulation += 0.5f * Math.max(0f, dangerBoost);
                break;

            case LAGOM:
            case CONTENT:
            case QUERECIA:
                // Danger suppresses calm/peaceful emotions
                modulation -= 0.3f * Math.max(0f, dangerBoost);
                break;
                
            case ARCANE_OVERFLOW:
                // Arcane Overflow unaffected by mundane danger
                break;
                
            // All other emotions: no danger-based modulation
            default:
                break;
        }

        // Health effects - nonlinear scaling for dramatic storytelling
        float healthRatio = parent.getPet().getHealth() / parent.getPet().getMaxHealth();
        healthRatio = MathHelper.clamp(healthRatio, 0.0f, 1.0f);
        
        // Critical health (< 40%): significant emotional impact
        if (healthRatio < 0.4f) {
            float healthPenalty = (0.4f - healthRatio) / 0.4f; // 0 at 40%, 1 at 0% health
            healthPenalty = MathHelper.clamp(healthPenalty, 0.0f, 1.0f);
            
            // Scale penalty by current emotional level for dramatic effect
            int currentLevel = getMoodLevel();
            float levelScale = 1.0f + (currentLevel * 0.15f); // Level 3 = 1.45x effect

            switch (emotion) {
                case ANGST:
                case FOREBODING:
                    // Low health amplifies distress emotions
                    modulation += 0.3f * healthPenalty * levelScale;
                    break;

                case CHEERFUL:
                case KEFI:
                case PLAYFULNESS:
                    // Low health suppresses energetic emotions
                    modulation -= 0.4f * healthPenalty;
                    break;
                    
                case ARCANE_OVERFLOW:
                    // Arcane overflow: mystical energy persists despite physical state
                    // Slight boost when near death (dramatic magical surge)
                    if (healthRatio < 0.2f) {
                        modulation += 0.15f * healthPenalty;
                    }
                    break;
                    
                case ECHOED_RESONANCE:
                case PACK_SPIRIT:
                    // Ultra-rare bond states: strengthen when wounded together
                    modulation += 0.2f * healthPenalty;
                    break;
                    
                case SISU:
                case GAMAN:
                case STOIC:
                    // Endurance emotions RISE when injured (defiant resilience)
                    modulation += 0.35f * healthPenalty * resilienceMod;
                    break;
                    
                default:
                    // Other emotions moderately affected by low health
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
        // Null safety: handle missing or empty profiles gracefully
        if (natureEmotionProfile == null || natureEmotionProfile.isEmpty() || emotion == null) {
            return 1f;
        }
        
        // Validate scale parameters to prevent extreme modulation
        majorScale = MathHelper.clamp(majorScale, -1f, 2f);
        minorScale = MathHelper.clamp(minorScale, -1f, 2f);
        quirkScale = MathHelper.clamp(quirkScale, -1f, 2f);
        
        float factor = 1f;
        if (emotion == natureEmotionProfile.majorEmotion()) {
            factor += natureEmotionProfile.majorStrength() * majorScale;
        } else if (emotion == natureEmotionProfile.minorEmotion()) {
            factor += natureEmotionProfile.minorStrength() * minorScale;
        } else if (emotion == natureEmotionProfile.quirkEmotion()) {
            factor += natureEmotionProfile.quirkStrength() * quirkScale;
        }
        
        // Ensure final factor stays within specified bounds
        factor = MathHelper.clamp(factor, min, max);
        // Final safety clamp to prevent any extreme values
        return MathHelper.clamp(factor, 0.0f, 1.40f); // Phase 2 tuning
    }

    private Map<PetComponent.Mood, Float> getEmotionToMoodWeights(PetComponent.Emotion emotion) {
        ensureConfigCache();
        Map<PetComponent.Mood, Float> cached = resolvedWeightCache.get(emotion);
        if (cached != null) {
            return cached;
        }
        EnumMap<PetComponent.Mood, Float> resolved = resolveEmotionToMoodWeights(cachedMoodsSection, emotion);
        Map<PetComponent.Mood, Float> immutable = Collections.unmodifiableMap(resolved);
        resolvedWeightCache.put(emotion, immutable);
        return immutable;
    }

    private void ensureConfigCache() {
        MoodEngineConfig moodConfig = MoodEngineConfig.get();
        int generation = moodConfig.getGeneration();
        if (generation == cachedConfigGeneration && cachedMoodsSection != null) {
            return;
        }
        cachedConfigGeneration = generation;
        cachedMoodsSection = moodConfig.getMoodsSection();
        resolvedWeightCache.clear();
        invalidateArcaneAmbientCachesIfNeeded(generation);
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

        JsonObject guardianSection = getObject(cachedMoodsSection, "guardianVigil");
        cachedOwnerDamageWindow = Math.max(20, RegistryJsonHelper.getInt(guardianSection, "recentDamageWindow", 160));
        cachedOwnerDangerGraceTicks = Math.max(cachedOwnerDamageWindow,
            RegistryJsonHelper.getLong(guardianSection, "dangerGraceTicks", 200L));
        cachedOwnerLowHealthGraceTicks = Math.max(40L,
            RegistryJsonHelper.getLong(guardianSection, "lowHealthGraceTicks", 260L));
        cachedOwnerLowHealthThreshold = MathHelper.clamp(
            RegistryJsonHelper.getFloat(guardianSection, "lowHealthThreshold", 0.45f), 0.05f, 0.95f);
        cachedOwnerCriticalHealthThreshold = MathHelper.clamp(
            RegistryJsonHelper.getFloat(guardianSection, "criticalHealthThreshold", 0.25f), 0.0f,
            cachedOwnerLowHealthThreshold);
        cachedOwnerStatusHazardGraceTicks = Math.max(40L,
            RegistryJsonHelper.getLong(guardianSection, "statusHazardGraceTicks", 180L));
        cachedOwnerStatusHazardThreshold = MathHelper.clamp(
            RegistryJsonHelper.getFloat(guardianSection, "statusHazardSeverityThreshold", 0.25f), 0.05f, 1.0f);

        JsonObject packSpiritSection = getObject(cachedMoodsSection, "packSpirit");
        cachedPackSpiritRadius = Math.max(2.0d, RegistryJsonHelper.getDouble(packSpiritSection, "radius", 12.0d));
        cachedPackSpiritMinPackmates = Math.max(1,
            RegistryJsonHelper.getInt(packSpiritSection, "minPackmates", 1));
        cachedPackSpiritGraceTicks = Math.max(20L,
            RegistryJsonHelper.getLong(packSpiritSection, "lingerTicks", 140L));
        cachedPackSpiritBondBonus = MathHelper.clamp(
            RegistryJsonHelper.getFloat(packSpiritSection, "bondBonus", 0.3f), 0f, 1f);
        cachedPackSpiritBaseContribution = MathHelper.clamp(
            RegistryJsonHelper.getFloat(packSpiritSection, "baseContribution", cachedPackSpiritBaseContribution), 0f, 2f);
        JsonObject engagementSection = getObject(packSpiritSection, "engagement");
        cachedPackSpiritRoleWeights.clear();
        for (PetRoleType.RoleArchetype archetype : PetRoleType.RoleArchetype.values()) {
            cachedPackSpiritRoleWeights.put(archetype, 1.0f);
        }
        if (engagementSection != null) {
            cachedPackSpiritBaseContribution = MathHelper.clamp(
                RegistryJsonHelper.getFloat(engagementSection, "baseContribution", cachedPackSpiritBaseContribution), 0f, 2f);
            cachedPackSpiritClosenessWeight = MathHelper.clamp(
                RegistryJsonHelper.getFloat(engagementSection, "closenessWeight", cachedPackSpiritClosenessWeight), 0f, 3f);
            cachedPackSpiritCombatBonus = MathHelper.clamp(
                RegistryJsonHelper.getFloat(engagementSection, "combatBonus", cachedPackSpiritCombatBonus), 0f, 1f);
            cachedPackSpiritProtectiveWeight = MathHelper.clamp(
                RegistryJsonHelper.getFloat(engagementSection, "protectiveMoodWeight", cachedPackSpiritProtectiveWeight), 0f, 1.5f);
            cachedPackSpiritFocusedWeight = MathHelper.clamp(
                RegistryJsonHelper.getFloat(engagementSection, "focusedMoodWeight", cachedPackSpiritFocusedWeight), 0f, 1.0f);
            cachedPackSpiritPlayfulWeight = MathHelper.clamp(
                RegistryJsonHelper.getFloat(engagementSection, "playfulMoodWeight", cachedPackSpiritPlayfulWeight), 0f, 1.0f);
            cachedPackSpiritDiversityBonus = MathHelper.clamp(
                RegistryJsonHelper.getFloat(engagementSection, "diversityBonus", cachedPackSpiritDiversityBonus), 0f, 1.0f);
            cachedPackSpiritEngagementMax = MathHelper.clamp(
                RegistryJsonHelper.getFloat(engagementSection, "maxContribution", cachedPackSpiritEngagementMax), 0.1f, 3.0f);
            JsonObject roleWeights = getObject(engagementSection, "roleWeights");
            if (roleWeights != null) {
                for (PetRoleType.RoleArchetype archetype : PetRoleType.RoleArchetype.values()) {
                    JsonElement element = roleWeights.get(archetype.name());
                    if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                        cachedPackSpiritRoleWeights.put(archetype,
                            MathHelper.clamp(element.getAsFloat(), 0f, 3.0f));
                    }
                }
            }
        }

        JsonObject arcaneSection = getObject(cachedMoodsSection, "arcaneOverflow");
        cachedArcaneOverflowLingerTicks = Math.max(40L,
            RegistryJsonHelper.getLong(arcaneSection, "lingerTicks", 220L));
        cachedArcaneOverflowStreakGraceTicks = Math.max(40L,
            RegistryJsonHelper.getLong(arcaneSection, "streakGraceTicks", 200L));
        cachedArcaneOverflowStatusGraceTicks = Math.max(40L,
            RegistryJsonHelper.getLong(arcaneSection, "statusGraceTicks", 160L));
        cachedArcaneOverflowAmbientScanRadius = Math.max(1,
            RegistryJsonHelper.getInt(arcaneSection, "ambientScanRadius", 4));
        cachedArcaneOverflowMinimumEnergy = MathHelper.clamp(
            RegistryJsonHelper.getFloat(arcaneSection, "minimumEnergy", 0.25f), 0.05f, 1.0f);
        cachedArcaneAmbientStructureBaseWeight = MathHelper.clamp(
            RegistryJsonHelper.getFloat(arcaneSection, "structureBaseWeight", 0.35f), 0f, 1.5f);
        cachedArcaneAmbientStructureMaxEnergy = MathHelper.clamp(
            RegistryJsonHelper.getFloat(arcaneSection, "structureMaxEnergy", 0.85f), 0f, 2.0f);
        cachedArcaneAmbientDistanceExponent = Math.max(0.25f,
            RegistryJsonHelper.getFloat(arcaneSection, "structureDistanceExponent", 1.0f));
        cachedArcaneAmbientMysticBonus = MathHelper.clamp(
            RegistryJsonHelper.getFloat(arcaneSection, "mysticEnvironmentBonus", 0.2f), 0f, 1.0f);
        cachedArcaneAmbientStructureWeights = new HashMap<>();
        JsonObject arcaneWeights = getObject(arcaneSection, "structureWeights");
        if (arcaneWeights != null) {
            for (Map.Entry<String, JsonElement> entry : arcaneWeights.entrySet()) {
                JsonElement value = entry.getValue();
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    continue;
                }
                Identifier id = Identifier.tryParse(entry.getKey());
                if (id == null) {
                    continue;
                }
                if (Registries.BLOCK.containsId(id)) {
                    cachedArcaneAmbientStructureWeights.put(id,
                        MathHelper.clamp(value.getAsFloat(), 0f, 2.0f));
                }
            }
        }
        JsonObject stateModifiers = getObject(arcaneSection, "structureState");
        JsonObject respawnConfig = getObject(stateModifiers, "respawnAnchor");
        cachedArcaneRespawnAnchorEmptyMultiplier = MathHelper.clamp(
            RegistryJsonHelper.getFloat(respawnConfig, "emptyMultiplier", cachedArcaneRespawnAnchorEmptyMultiplier), 0f, 2.0f);
        cachedArcaneRespawnAnchorChargeStep = MathHelper.clamp(
            RegistryJsonHelper.getFloat(respawnConfig, "chargeStep", cachedArcaneRespawnAnchorChargeStep), 0f, 2.0f);
        cachedArcaneRespawnAnchorMaxMultiplier = MathHelper.clamp(
            RegistryJsonHelper.getFloat(respawnConfig, "maxMultiplier", cachedArcaneRespawnAnchorMaxMultiplier), 0.1f, 4.0f);
        JsonObject beaconConfig = getObject(stateModifiers, "beacon");
        cachedArcaneBeaconBaseMultiplier = MathHelper.clamp(
            RegistryJsonHelper.getFloat(beaconConfig, "baseMultiplier", cachedArcaneBeaconBaseMultiplier), 0f, 3.0f);
        cachedArcaneBeaconPerLevelMultiplier = MathHelper.clamp(
            RegistryJsonHelper.getFloat(beaconConfig, "perLevel", cachedArcaneBeaconPerLevelMultiplier), 0f, 2.0f);
        cachedArcaneBeaconMaxMultiplier = MathHelper.clamp(
            RegistryJsonHelper.getFloat(beaconConfig, "maxMultiplier", cachedArcaneBeaconMaxMultiplier), 0.1f, 6.0f);
        JsonObject sculkConfig = getObject(stateModifiers, "sculkCatalyst");
        cachedArcaneCatalystRecentBloomTicks = Math.max(20L,
            RegistryJsonHelper.getLong(sculkConfig, "recentBloomTicks", cachedArcaneCatalystRecentBloomTicks));
        cachedArcaneCatalystActiveMultiplier = MathHelper.clamp(
            RegistryJsonHelper.getFloat(sculkConfig, "activeMultiplier", cachedArcaneCatalystActiveMultiplier), 0f, 4.0f);
        cachedArcaneCatalystInactiveMultiplier = MathHelper.clamp(
            RegistryJsonHelper.getFloat(sculkConfig, "inactiveMultiplier", cachedArcaneCatalystInactiveMultiplier), 0f, 4.0f);
        JsonObject gearSection = getObject(arcaneSection, "gearWeights");
        cachedArcaneGearBaseWeight = MathHelper.clamp(
            RegistryJsonHelper.getFloat(gearSection, "base", cachedArcaneGearBaseWeight), 0f, 1f);
        cachedArcaneGearLevelWeight = MathHelper.clamp(
            RegistryJsonHelper.getFloat(gearSection, "perLevel", cachedArcaneGearLevelWeight), 0f, 1f);
        cachedArcaneGearMaxContribution = MathHelper.clamp(
            RegistryJsonHelper.getFloat(gearSection, "maxContribution", cachedArcaneGearMaxContribution), 0.1f, 4f);
        cachedArcaneGearOwnerMultiplier = MathHelper.clamp(
            RegistryJsonHelper.getFloat(gearSection, "ownerMultiplier", cachedArcaneGearOwnerMultiplier), 0f, 3.0f);
        cachedArcaneGearPetMultiplier = MathHelper.clamp(
            RegistryJsonHelper.getFloat(gearSection, "petMultiplier", cachedArcaneGearPetMultiplier), 0f, 3.0f);
        cachedArcaneScanCooldownTicks = Math.max(20L,
            RegistryJsonHelper.getLong(arcaneSection, "scanCooldownTicks", 80L));
        cachedArcaneScanMovementThreshold = Math.max(0.0d,
            RegistryJsonHelper.getDouble(arcaneSection, "scanMovementThreshold", 3.0d));
        cachedArcaneScanMovementThresholdSquared = cachedArcaneScanMovementThreshold
            * cachedArcaneScanMovementThreshold;
        JsonObject statusSection = getObject(arcaneSection, "statusWeights");
        cachedArcaneStatusBaseWeight = MathHelper.clamp(
            RegistryJsonHelper.getFloat(statusSection, "base", cachedArcaneStatusBaseWeight), 0f, 1f);
        cachedArcaneStatusAmplifierWeight = MathHelper.clamp(
            RegistryJsonHelper.getFloat(statusSection, "amplifier", cachedArcaneStatusAmplifierWeight), 0f, 1f);
        cachedArcaneStatusDurationReference = Math.max(20,
            RegistryJsonHelper.getInt(statusSection, "durationReferenceTicks", cachedArcaneStatusDurationReference));
        cachedArcaneStatusDurationWeight = MathHelper.clamp(
            RegistryJsonHelper.getFloat(statusSection, "durationWeight", cachedArcaneStatusDurationWeight), 0f, 1f);
        cachedArcaneStatusDurationCap = MathHelper.clamp(
            RegistryJsonHelper.getFloat(statusSection, "durationCap", cachedArcaneStatusDurationCap), 0.25f, 10f);
        cachedArcaneStatusMaxContribution = MathHelper.clamp(
            RegistryJsonHelper.getFloat(statusSection, "maxContribution", cachedArcaneStatusMaxContribution), 0.1f, 2.0f);
        cachedArcaneStatusOwnerMultiplier = MathHelper.clamp(
            RegistryJsonHelper.getFloat(statusSection, "ownerMultiplier", cachedArcaneStatusOwnerMultiplier), 0f, 3.0f);
        cachedArcaneStatusPetMultiplier = MathHelper.clamp(
            RegistryJsonHelper.getFloat(statusSection, "petMultiplier", cachedArcaneStatusPetMultiplier), 0f, 3.0f);

        JsonObject lonelinessSection = getObject(cachedMoodsSection, "loneliness");
        cachedLonelyComfortRadius = Math.max(1.0d,
            RegistryJsonHelper.getDouble(lonelinessSection, "comfortRadius", 8.0d));
        cachedLonelyDistanceThreshold = Math.max(cachedLonelyComfortRadius,
            RegistryJsonHelper.getDouble(lonelinessSection, "distanceThreshold", 24.0d));
        cachedLonelySaudadeGraceTicks = Math.max(40L,
            RegistryJsonHelper.getLong(lonelinessSection, "saudadeGraceTicks", 400L));
        cachedLonelyHiraethGraceTicks = Math.max(cachedLonelySaudadeGraceTicks,
            RegistryJsonHelper.getLong(lonelinessSection, "hiraethGraceTicks", 900L));
        cachedLonelyOfflineGraceTicks = Math.max(40L,
            RegistryJsonHelper.getLong(lonelinessSection, "offlineGraceTicks", 200L));
        cachedLonelyOfflineHiraethGraceTicks = Math.max(cachedLonelyOfflineGraceTicks,
            RegistryJsonHelper.getLong(lonelinessSection, "offlineHiraethGraceTicks", 600L));
        cachedLonelyComfortRadiusSquared = cachedLonelyComfortRadius * cachedLonelyComfortRadius;
        cachedLonelyDistanceThresholdSquared = cachedLonelyDistanceThreshold * cachedLonelyDistanceThreshold;
        cachedLonelySocialGraceTicks = Math.max(20L,
            RegistryJsonHelper.getLong(lonelinessSection, "socialGraceTicks", cachedLonelySocialGraceTicks));
        cachedLonelyPackGraceTicks = Math.max(20L,
            RegistryJsonHelper.getLong(lonelinessSection, "packGraceTicks", cachedLonelyPackGraceTicks));
        cachedLonelyPackStrengthThreshold = MathHelper.clamp(
            RegistryJsonHelper.getFloat(lonelinessSection, "packStrengthThreshold", cachedLonelyPackStrengthThreshold), 0f, 1f);
        cachedLonelyPackRadius = Math.max(1.0d,
            RegistryJsonHelper.getDouble(lonelinessSection, "packRadius",
                Math.max(cachedLonelyPackRadius, cachedLonelyDistanceThreshold)));
        cachedLonelyPackRadiusSquared = cachedLonelyPackRadius * cachedLonelyPackRadius;

        JsonObject positiveSection = getObject(cachedMoodsSection, "positiveCues");
        cachedPositivePetGraceTicks = Math.max(20L,
            RegistryJsonHelper.getLong(positiveSection, "pettingGraceTicks", 200L));
        cachedPositiveCrouchGraceTicks = Math.max(20L,
            RegistryJsonHelper.getLong(positiveSection, "crouchGraceTicks", 160L));
        cachedPositiveSocialGraceTicks = Math.max(20L,
            RegistryJsonHelper.getLong(positiveSection, "socialBufferGraceTicks", 200L));
        cachedPositivePlayGraceTicks = Math.max(20L,
            RegistryJsonHelper.getLong(positiveSection, "playGraceTicks", 240L));
        cachedPositiveFeedGraceTicks = Math.max(20L,
            RegistryJsonHelper.getLong(positiveSection, "feedingGraceTicks", 260L));
        cachedPositiveGiftGraceTicks = Math.max(20L,
            RegistryJsonHelper.getLong(positiveSection, "giftGraceTicks", 360L));

        loadEmotionDecayMultipliers();
        loadEmotionValence();
        loadMoodThresholds();
        loadAdvancedSettings();
        rebuildOpponentPairs();
    }

    private void invalidateArcaneAmbientCachesIfNeeded(int generation) {
        if (generation != cachedArcaneAmbientConfigGeneration) {
            cachedArcaneAmbientConfigGeneration = generation;
            StateManager.invalidateArcaneAmbientCaches();
        }
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
    
    private void loadEmotionDecayMultipliers() {
        cachedEmotionDecayMultipliers.clear();
        JsonObject multipliers = getObject(cachedMoodsSection, "emotionDecayMultipliers");
        if (multipliers != null) {
            for (String key : multipliers.keySet()) {
                JsonElement el = multipliers.get(key);
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                    cachedEmotionDecayMultipliers.put(key, el.getAsFloat());
                }
            }
        }
    }
    
    private void loadEmotionValence() {
        cachedNegativeEmotions.clear();
        cachedPositiveEmotions.clear();
        JsonObject valence = getObject(cachedMoodsSection, "emotionValence");
        if (valence != null) {
            JsonElement negative = valence.get("negative");
            if (negative != null && negative.isJsonArray()) {
                negative.getAsJsonArray().forEach(el -> {
                    if (el.isJsonPrimitive()) {
                        cachedNegativeEmotions.add(el.getAsString());
                    }
                });
            }
            JsonElement positive = valence.get("positive");
            if (positive != null && positive.isJsonArray()) {
                positive.getAsJsonArray().forEach(el -> {
                    if (el.isJsonPrimitive()) {
                        cachedPositiveEmotions.add(el.getAsString());
                    }
                });
            }
        }
    }
    
    private void loadMoodThresholds() {
        perMoodThresholds.clear();
        JsonObject moodThresholds = getObject(cachedMoodsSection, "moodThresholds");
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            String key = mood.name().toLowerCase();
            if (moodThresholds != null && moodThresholds.has(key)) {
                JsonElement el = moodThresholds.get(key);
                if (el.isJsonArray()) {
                    float[] thresholds = parseThresholdsArray(el.getAsJsonArray());
                    if (thresholds != null) {
                        perMoodThresholds.put(mood, thresholds);
                        continue;
                    }
                }
            }
            perMoodThresholds.put(mood, cachedLevelThresholds);
        }
    }
    
    private float[] parseThresholdsArray(com.google.gson.JsonArray array) {
        List<Float> values = new ArrayList<>();
        array.forEach(el -> {
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                values.add(el.getAsFloat());
            }
        });
        if (values.isEmpty()) return null;
        float[] out = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i);
        }
        return out;
    }
    
    private void loadAdvancedSettings() {
        JsonObject root = MoodEngineConfig.get().getRoot();
        JsonObject advanced = getObject(root, "advanced");
        if (advanced == null) return;
        
        JsonObject decay = getObject(advanced, "decay");
        if (decay != null) {
            cachedHabituationBase = RegistryJsonHelper.getFloat(decay, "habituation_base", HABITUATION_BASE);
            cachedHalfLifeMultiplier = RegistryJsonHelper.getFloat(decay, "half_life_multiplier", HALF_LIFE_MULTIPLIER);
            cachedMinHalfLife = RegistryJsonHelper.getFloat(decay, "min_half_life", MIN_HALF_LIFE);
            cachedMaxHalfLife = RegistryJsonHelper.getFloat(decay, "max_half_life", MAX_HALF_LIFE);
            cachedNegativePersistence = RegistryJsonHelper.getFloat(decay, "negative_persistence", 2.0f);
            cachedConditionPresentMultiplier = RegistryJsonHelper.getFloat(decay, "condition_present_multiplier", 3.5f);
            cachedHomeostasisRecoveryHalf = RegistryJsonHelper.getFloat(decay, "homeostasis_recovery_half", HOMEOSTASIS_RECOVERY_HALF);
        }
        
        JsonObject habituation = getObject(advanced, "habituation");
        if (habituation != null) {
            cachedCadenceAlpha = RegistryJsonHelper.getFloat(habituation, "cadence_alpha", CADENCE_ALPHA);
            cachedVolatilityAlpha = RegistryJsonHelper.getFloat(habituation, "volatility_alpha", VOLATILITY_ALPHA);
            cachedPeakAlpha = RegistryJsonHelper.getFloat(habituation, "peak_alpha", PEAK_ALPHA);
        }
        
        JsonObject opponent = getObject(advanced, "opponent");
        if (opponent != null) {
            cachedOpponentTransferMax = RegistryJsonHelper.getFloat(opponent, "transfer_max", OPPONENT_TRANSFER_MAX);
            cachedReboundGain = RegistryJsonHelper.getFloat(opponent, "rebound_gain", REBOUND_GAIN);
        }
        
        JsonObject context = getObject(advanced, "context");
        if (context != null) {
            cachedRelationshipVariance = RegistryJsonHelper.getFloat(context, "relationship_variance", RELATIONSHIP_VARIANCE);
            cachedCarePulseHalfLife = RegistryJsonHelper.getFloat(context, "care_pulse_half_life", CARE_PULSE_HALF_LIFE);
            cachedDangerHalfLife = RegistryJsonHelper.getFloat(context, "danger_half_life", DANGER_HALF_LIFE);
        }
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
                Map.entry(PetComponent.Mood.HAPPY, 0.62f),
                Map.entry(PetComponent.Mood.PLAYFUL, 0.23f),
                Map.entry(PetComponent.Mood.CURIOUS, 0.15f)));
        table.put(PetComponent.Emotion.QUERECIA, weights(
                Map.entry(PetComponent.Mood.HAPPY, 0.35f),
                Map.entry(PetComponent.Mood.BONDED, 0.4f),
                Map.entry(PetComponent.Mood.CALM, 0.25f)));
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
        table.put(PetComponent.Emotion.GUARDIAN_VIGIL, weights(
                Map.entry(PetComponent.Mood.PROTECTIVE, 0.60f),
                Map.entry(PetComponent.Mood.BONDED, 0.25f),
                Map.entry(PetComponent.Mood.FOCUSED, 0.15f)));
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
        long currentTime = parent.getPet().getEntityWorld().getTime();
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

        boolean hasGradientPalette = palette != null && palette.size() >= 2;

        Text baseText = hasGradientPalette
                ? switch (Math.max(level, 0)) {
                    case 0 -> createStaticPaletteText(label, palette, primaryColor);
                    case 1 -> createBreathingText(label, palette, primaryColor, secondaryColor, 1);
                    case 2 -> createBreathingGradient(label, palette, primaryColor, secondaryColor, 2);
                    case 3 -> createBreathingMovingGradient(label, palette, primaryColor, secondaryColor, 3);
                    default -> createBreathingMovingGradient(label, palette, primaryColor, secondaryColor, level);
                }
                : createGentleSingleStopText(label, palette, primaryColor, secondaryColor, level);

        if (!withDebug) {
            return baseText;
        }
        
        // Enhanced debug output showing emotional buildup system
        MutableText debugText = baseText.copy();
        debugText.append(Text.literal(" [" + level + "]").styled(s -> s.withColor(TextColor.fromFormatting(Formatting.GRAY))));
        
        // Show buildup metrics
        float currentStrength = moodBlend.getOrDefault(currentMood, 0f);
        float buildupMult = computeBuildupMultiplier(currentMood, currentStrength);
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
        long worldTime = parent.getPet().getEntityWorld().getTime();
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
        long worldTime = parent.getPet().getEntityWorld().getTime();
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
        long worldTime = parent.getPet().getEntityWorld().getTime();
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

    private Text createGentleSingleStopText(String text,
            List<PetComponent.WeightedEmotionColor> palette,
            TextColor fallbackPrimary, TextColor fallbackSecondary, int level) {
        TextColor paletteColor = (palette == null || palette.isEmpty()) ? null : palette.get(0).color();
        TextColor softenedBase = paletteColor != null
                ? blendWithFallback(fallbackPrimary, paletteColor, 0.4f)
                : fallbackPrimary;

        int safeLevel = Math.max(level, 0);
        if (safeLevel == 0) {
            return Text.literal(text).styled(style -> style.withColor(softenedBase));
        }

        return createGentleSingleStopBreathing(text, softenedBase, fallbackSecondary, safeLevel);
    }

    private MutableText createGentleSingleStopBreathing(String text, TextColor baseColor,
            TextColor fallbackSecondary, int level) {
        long worldTime = parent.getPet().getEntityWorld().getTime();
        int breathingSpeed = computeBreathingDuration(Math.max(level, 1), BASE_BREATHING_SPEEDS);
        int updateInterval = getAnimationUpdateInterval(Math.max(level, 1));
        long animationTime = (worldTime / updateInterval) * updateInterval;
        double breathingPhase = (animationTime % breathingSpeed) / (double) breathingSpeed;
        double breathingIntensity = (Math.sin(breathingPhase * 2 * Math.PI) + 1) / 2;

        double[] minIntensities = {0.10, 0.14, 0.18};
        double[] maxIntensities = {0.25, 0.30, 0.34};
        int levelIndex = Math.min(Math.max(level, 1) - 1, minIntensities.length - 1);
        double minIntensity = minIntensities[levelIndex];
        double maxIntensity = maxIntensities[levelIndex];
        double scaledIntensity = minIntensity + (breathingIntensity * (maxIntensity - minIntensity));

        TextColor lightenTarget = UIStyle.interpolateColor(baseColor, TextColor.fromRgb(0xFFFFFF), 0.25f);
        TextColor darkenTarget = UIStyle.interpolateColor(baseColor, TextColor.fromRgb(0x000000), 0.18f);
        TextColor accentBase = fallbackSecondary != null
                ? UIStyle.interpolateColor(baseColor, fallbackSecondary, 0.15f)
                : baseColor;

        MutableText out = Text.empty();
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            double charPhase = (i * 0.35) + (breathingPhase * 1.2);
            double charBreathing = (Math.sin(charPhase) + 1) / 2;
            double finalPulse = (scaledIntensity + charBreathing * 0.2) / 1.2;
            float pulseBlend = MathHelper.clamp((float) finalPulse, 0f, 1f);
            TextColor gentleHighlight = UIStyle.interpolateColor(accentBase, lightenTarget, pulseBlend * 0.6f);
            TextColor finalColor = UIStyle.interpolateColor(darkenTarget, gentleHighlight, pulseBlend);
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
        // Progressive acceleration: higher levels update faster for more dynamic visuals
        // Level 0: base interval (slow)
        // Level 1: slightly faster
        // Level 2: noticeably faster
        // Level 3: very fast, dramatic
        double speedScale = 1.0 - (Math.min(level, 3) * cachedAnimationSpeedMultiplier);
        speedScale = MathHelper.clamp(speedScale, 0.25, 1.0); // Never slower than 1/4 base speed
        double interval = cachedBaseAnimationUpdateInterval * speedScale;
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
        String sanitized = value.replace('_', ' ');
        if (sanitized.isEmpty()) {
            return sanitized;
        }

        String lower = sanitized.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetter(c)) {
                if (capitalizeNext) {
                    builder.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    builder.append(c);
                }
            } else if (Character.isDigit(c)) {
                builder.append(c);
                capitalizeNext = false;
            } else {
                builder.append(c);
                capitalizeNext = true;
            }

            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
        }
        return builder.toString();
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
        registerOpponentPair(PetComponent.Emotion.SAUDADE, PetComponent.Emotion.CONTENT);
        registerOpponentPair(PetComponent.Emotion.DISGUST, PetComponent.Emotion.UBUNTU);
        registerOpponentPair(PetComponent.Emotion.STOIC, PetComponent.Emotion.CHEERFUL);
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
            @SuppressWarnings("unused") // Set is populated via computeIfAbsent, used indirectly
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
            float cadence = cadenceEMA > 0f ? cadenceEMA : engine.cachedHabituationBase;
            float adaptiveHalf = MathHelper.clamp(cadence * engine.cachedHalfLifeMultiplier, engine.cachedMinHalfLife, engine.cachedMaxHalfLife);
            
            String emotionKey = emotion.name().toLowerCase();
            float emotionMultiplier = engine.cachedEmotionDecayMultipliers.getOrDefault(emotionKey, 1.0f);
            adaptiveHalf *= emotionMultiplier;
            
            if (engine.cachedNegativeEmotions.contains(emotionKey)) {
                adaptiveHalf *= engine.cachedNegativePersistence;
            }
            
            if (engine != null && engine.hasOngoingCondition(emotion, now)) {
                adaptiveHalf *= engine.cachedConditionPresentMultiplier;
            }
            
            float decayRate = (float) (Math.log(2) / adaptiveHalf);
            float decay = (float) Math.exp(-decayRate * delta);
            intensity *= decay;
            impactBudget *= decay;
            
            homeostasisBias = MathHelper.lerp((float) Math.exp(-delta / engine.cachedHomeostasisRecoveryHalf), homeostasisBias, 1.1f);
            contagionShare *= Math.exp(-delta / 400f);
            lastUpdateTime = now;
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
    
    private float[] getMoodSpecificThresholds(PetComponent.Mood mood) {
        float[] specific = perMoodThresholds.get(mood);
        return specific != null ? specific : getLevelThresholds();
    }
    
    /**
     * Compute buildup multiplier based on emotional momentum.
     * Rising emotions get boosted (feels responsive), falling get gentle resistance.
     * This creates a "feeling the buildup" experience where escalating emotions accelerate.
     */
    private float computeBuildupMultiplier(PetComponent.Mood currentMood, float currentStrength) {
        if (currentMood == null) {
            return 1.0f;
        }

        if (previousMoodSnapshot == null || previousMoodSnapshot != currentMood) {
            return 1.0f;
        }

        // Need at least one previous reading for this mood to compare
        if (previousMoodStrength <= 0f || previousMoodLevel < 0) {
            return 1.0f;  // First update or mood switch, no trend available
        }
        
        // Calculate trend: positive = rising, negative = falling
        float trend = currentStrength - previousMoodStrength;
        
        // Nature modifiers: volatile pets escalate faster, calm pets slower
        float volatilityMod = parent.getNatureVolatilityMultiplier(); // 0.5-1.5 range
        float resilienceMod = parent.getNatureResilienceMultiplier();  // 0.5-1.5 range
        
        // Only apply multiplier if trend is significant (avoid noise)
        if (trend > BUILDUP_TREND_THRESHOLD) {
            // Rising emotion: BOOST toward next level (responsive, exciting)
            // Volatile natures escalate faster, resilient slower
            float risingMult = BUILDUP_RISING_MULTIPLIER * volatilityMod;
            return MathHelper.clamp(risingMult, 1.0f, 1.5f);
        } else if (trend < -BUILDUP_TREND_THRESHOLD) {
            // Falling emotion: gentle resistance (prevents whiplash, smooth decay)
            // Resilient natures recover faster
            float fallingMult = BUILDUP_FALLING_MULTIPLIER * (2.0f - resilienceMod);
            return MathHelper.clamp(fallingMult, 0.7f, 1.0f);
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
    private int applyLevelHysteresis(int rawLevel, float effectiveStrength, float[] thresholds, int lastLevel) {
        // No previous level data or no change - skip hysteresis
        if (lastLevel < 0 || rawLevel == lastLevel) {
            return rawLevel;
        }

        if (rawLevel > lastLevel) {
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
                return lastLevel;
            }
        } else {
            // GOING DOWN: need to fall below threshold by small margin (easier than going up)
            // The threshold we're falling below is at index (lastLevel - 1)
            int thresholdIndex = lastLevel - 1;
            
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
                return lastLevel;
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
    @SuppressWarnings("unused")
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

    private record ComputationSnapshot(
            long timestamp,
            float epsilon,
            List<SnapshotEmotionRecord> activeRecords,
            EnumMap<PetComponent.Mood, Float> baselineBlend,
            PetComponent.Mood previousMood,
            float previousStrength,
            double cachedMomentum,
            double cachedSwitchMargin,
            float impactCap,
            float natureVolatility,
            float natureResilience,
            float opponentTransferMax,
            float reboundGain,
            Map<PetComponent.Emotion, Float> harmonyBiases,
            BehaviorSnapshot behaviorSnapshot,
            float habituationBase,
            float halfLifeMultiplier,
            float minHalfLife,
            float maxHalfLife,
            float negativePersistence,
            float conditionPresentMultiplier,
            float homeostasisRecoveryHalf,
            Map<String, Float> emotionDecayMultipliers,
            Set<String> negativeEmotions,
            long lonelySocialGraceTicks,
            long lonelyPackGraceTicks,
            float lonelyPackStrengthThreshold
    ) {
    }

    private record ComputationResult(
            boolean resetToCalm,
            EnumMap<PetComponent.Mood, Float> normalizedTargetBlend,
            EnumMap<PetComponent.Mood, Float> finalBlend,
            EnumMap<PetComponent.Emotion, Float> recordWeights,
            List<WeightedCandidate> weightedCandidates,
            EnumMap<PetComponent.Emotion, EmotionRecordUpdate> recordUpdates,
            Set<PetComponent.Emotion> recordsToRemove,
            BehaviorResult behaviorResult
    ) {
        static ComputationResult reset() {
            return new ComputationResult(
                    true,
                    new EnumMap<>(PetComponent.Mood.class),
                    new EnumMap<>(PetComponent.Mood.class),
                    new EnumMap<>(PetComponent.Emotion.class),
                    Collections.emptyList(),
                    new EnumMap<>(PetComponent.Emotion.class),
                    EnumSet.noneOf(PetComponent.Emotion.class),
                    null
            );
        }
    }

    private static final class Candidate {
        final ProcessedEmotionRecord record;
        final float freshness;
        final float frequency;
        float signal;

        Candidate(ProcessedEmotionRecord record, float freshness, float frequency, float signal) {
            this.record = record;
            this.freshness = freshness;
            this.frequency = frequency;
            this.signal = signal;
        }
    }

    private record OpponentConflict(Candidate a, Candidate b, float combinedWeight) {
    }

    private record BehaviorSnapshot(
            long now,
            long lastMomentumUpdate,
            float behavioralMomentum,
            float momentumInertia,
            float physicalActivity,
            float mentalActivity,
            float socialActivity,
            float restActivity,
            float recentPhysicalBurst,
            float physicalStamina,
            float mentalFocus,
            float socialCharge,
            boolean energyProfileDirty,
            float resilienceMultiplier,
            float volatilityMultiplier,
            boolean petIsBaby,
            long timeOfDay,
            float healthRatio,
            float bondStrengthNormalized,
            long stableSeed,
            long lastSocialTick,
            long lastPackTick,
            float lastPackStrength,
            float lastPackWeightedStrength,
            int lastPackAllies,
            boolean ownerPresent,
            double ownerDistanceSquared,
            EnumMap<PetComponent.Mood, Float> moodBlendSnapshot
    ) {
    }

    private record BehaviorResult(
            long lastMomentumUpdate,
            float behavioralMomentum,
            float momentumInertia,
            float physicalActivity,
            float mentalActivity,
            float socialActivity,
            float restActivity,
            float recentPhysicalBurst,
            float physicalStamina,
            float mentalFocus,
            float socialCharge,
            boolean energyProfileDirty,
            BehaviouralEnergyProfile energyProfile
    ) {
    }

    private record SnapshotEmotionRecord(
            PetComponent.Emotion emotion,
            float intensity,
            float impactBudget,
            float cadenceEMA,
            float volatilityEMA,
            float peakEMA,
            float homeostasisBias,
            float sensitisationGain,
            float habituationSlope,
            float contagionShare,
            float relationshipGuard,
            float dangerWindow,
            float appraisalConfidence,
            long startTime,
            long lastEventTime,
            long lastUpdateTime,
            boolean hasOngoingCondition,
            float contextModulation,
            float profileWeightBias,
            float recencyModifier,
            float persistenceModifier
    ) {
    }

    private record EmotionRecordUpdate(
            PetComponent.Emotion emotion,
            float intensity,
            float impactBudget,
            float cadenceEMA,
            float volatilityEMA,
            float peakEMA,
            float homeostasisBias,
            float sensitisationGain,
            float habituationSlope,
            float contagionShare,
            float relationshipGuard,
            float dangerWindow,
            float appraisalConfidence,
            long lastEventTime,
            long lastUpdateTime
    ) {
    }

    private record ProcessedEmotionRecord(
            PetComponent.Emotion emotion,
            float intensity,
            float impactBudget,
            float cadenceEMA,
            float volatilityEMA,
            float peakEMA,
            float homeostasisBias,
            float sensitisationGain,
            float habituationSlope,
            float contagionShare,
            float relationshipGuard,
            float dangerWindow,
            float appraisalConfidence,
            long startTime,
            long lastEventTime,
            long lastUpdateTime,
            boolean hasOngoingCondition,
            float contextModulation,
            float profileWeightBias,
            float recencyModifier,
            float persistenceModifier
    ) {
    }

    private record ProcessedEmotionResult(
            List<ProcessedEmotionRecord> records,
            EnumMap<PetComponent.Emotion, EmotionRecordUpdate> updates,
            Set<PetComponent.Emotion> removals
    ) {
    }

    private record WeightedCandidate(PetComponent.Emotion emotion, float signal, float intensity) {
    }
}



