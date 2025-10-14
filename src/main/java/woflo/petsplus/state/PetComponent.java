package woflo.petsplus.state;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.advancement.BestFriendTracker;
import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.context.perception.ContextSlice;
import woflo.petsplus.ai.context.perception.EnvironmentPerceptionBridge;
import woflo.petsplus.ai.context.perception.PerceptionBus;
import woflo.petsplus.ai.context.perception.PerceptionListener;
import woflo.petsplus.ai.context.perception.PerceptionStimulus;
import woflo.petsplus.ai.context.perception.PerceptionStimulusType;
import woflo.petsplus.ai.context.perception.PetContextCache;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.context.perception.StimulusTimeline;
import woflo.petsplus.ai.context.social.SocialSnapshot;
import woflo.petsplus.ai.feedback.ExperienceLog;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.component.PetsplusComponents;
import woflo.petsplus.mixin.EntityComponentAccessor;
import woflo.petsplus.mood.EmotionBaselineTracker;
import woflo.petsplus.mood.MoodService;
import woflo.petsplus.stats.PetImprint;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.coordination.PetWorkScheduler;
import woflo.petsplus.state.emotions.PetMood;
import woflo.petsplus.state.emotions.PetMoodEngine;
import woflo.petsplus.state.gossip.PetGossipLedger;
import woflo.petsplus.state.gossip.RumorEntry;
import woflo.petsplus.tags.PetsplusEntityTypeTags;
import woflo.petsplus.naming.AttributeKey;
import woflo.petsplus.history.HistoryEvent;
import woflo.petsplus.state.modules.*;
import woflo.petsplus.state.modules.impl.*;
import woflo.petsplus.state.relationships.SpeciesMemory;
import woflo.petsplus.util.BehaviorSeedUtil;
import woflo.petsplus.stats.nature.astrology.AstrologyRegistry;

import net.minecraft.util.math.ChunkSectionPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Deque;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.EnumSet;

/**
 * Coordinates pet state through specialized modules for characteristics, progression,
 * history, inventory, scheduling, and ownership. Delegates all domain-specific logic
 * to modules while managing core pet identity (role, perching) and module orchestration.
 */
public class PetComponent {
    private static final Map<MobEntity, PetComponent> COMPONENTS = new WeakHashMap<>();
    private static final Identifier DEFAULT_ROLE_ID = PetRoleType.GUARDIAN_ID;
    private static final String[] SPECIES_STATE_KEYS = {
        "context_species", "context_type", "tag_species", "tag_type", "context_entity"
    };
    private static final long MIN_GOSSIP_DECAY_DELAY = 40L;
    private static final int GOSSIP_OPT_OUT_MIN_DURATION = 120;
    private static final int GOSSIP_OPT_OUT_MAX_DURATION = 220;

    // Core pet identity
    private final MobEntity pet;
    private Identifier roleId;
    private final Map<String, Object> stateData;
    private long lastAttackTick;
    private boolean isPerched;
    
    // Specialized modules for domain-specific state
    private final PetMoodEngine moodEngine;
    private final PetGossipLedger gossipLedger;
    private final InventoryModule inventoryModule;
    private final CharacteristicsModule characteristicsModule;
    private final HistoryModule historyModule;
    private final ProgressionModule progressionModule;
    private final SchedulingModule schedulingModule;
    private final OwnerModule ownerModule;
    private final SpeciesMetadataModule speciesMetadataModule;
    private final RelationshipModule relationshipModule;
    private final StimulusTimeline stimulusTimeline;
    private final ExperienceLog experienceLog;
    private final PetAIState aiState = new PetAIState();

    private StateManager stateManager;

    private final PerceptionBus perceptionBus;
    private final PetContextCache contextCache;
    private final ContextSliceState contextSliceState;

    // Swarm spatial tracking
    private boolean swarmTrackingInitialized;
    private long lastSwarmCellKey = Long.MIN_VALUE;
    private double lastSwarmX;
    private double lastSwarmY;
    private double lastSwarmZ;
    
    // Follow behavior spacing
    private double followSpacingOffsetX;
    private double followSpacingOffsetZ;
    private float followSpacingPadding;
    private long followSpacingSampleTick = Long.MIN_VALUE;
    private long moodFollowHoldUntilTick = Long.MIN_VALUE;
    private float moodFollowDistanceBonus = 0.0f;
    // UI state
    private long xpFlashStartTick = -1;
    private static final int XP_FLASH_DURATION = 7; // 0.35 seconds

    
    /**
     * Pet mood states derived from interactions and environment.
     */
    public enum Mood {
        HAPPY(PetMood.HAPPY),
        PLAYFUL(PetMood.PLAYFUL),
        CURIOUS(PetMood.CURIOUS),
        BONDED(PetMood.BONDED),
        CALM(PetMood.CALM),
        PASSIONATE(PetMood.PASSIONATE),
        YUGEN(PetMood.YUGEN),
        FOCUSED(PetMood.FOCUSED),
        SISU(PetMood.SISU),
        SAUDADE(PetMood.SAUDADE),
        PROTECTIVE(PetMood.PROTECTIVE),
        RESTLESS(PetMood.RESTLESS),
        AFRAID(PetMood.AFRAID),
        ANGRY(PetMood.ANGRY),
        
        // Ultra-rare moods
        ECHOED_RESONANCE(PetMood.ECHOED_RESONANCE),
        ARCANE_OVERFLOW(PetMood.ARCANE_OVERFLOW),
        PACK_SPIRIT(PetMood.PACK_SPIRIT);

        public final Formatting primaryFormatting;
        public final Formatting secondaryFormatting;
        public final PetMood delegate;
        Mood(PetMood d) {
            this.delegate = d;
            this.primaryFormatting = d.primaryFormatting;
            this.secondaryFormatting = d.secondaryFormatting;
        }
    }

    /** Hidden, reactive emotions (de-duplicated list from spec) */
    public enum Emotion {
        CHEERFUL(0xFFD166),
        QUERECIA(0x3CC8C8),
        UBUNTU(0xFF9E40),
        KEFI(0xFF5DA2),
        ANGST(0x4A2C8C),
        FOREBODING(0x1F3A5F),
        GUARDIAN_VIGIL(0x3D6FA6),
        FRUSTRATION(0xE63946),
        STARTLE(0xFF5F5F),
        DISGUST(0x5B8C2A),
        REGRET(0x4B6F8C),
        MONO_NO_AWARE(0x9C6FB6),
        FERNWEH(0x5EC8FF),
        SOBREMESA(0xDFA86C),
        HANYAUKU(0xFF99AA),
        WABI_SABI(0x6B8F71),
        LAGOM(0x5FA08F),
        ENNUI(0x8A8D8F),
        YUGEN(0x2F3B73),
        SAUDADE(0x2E6F95),
        HIRAETH(0x2B8C7F),
        STOIC(0x5E6066),
        HOPEFUL(0x69D37A),
        RELIEF(0x66E0C6),
        GAMAN(0xA26F4B),
        CURIOUS(0x40C4FF),
        SISU(0x2A4D8F),
        FOCUSED(0xF2B03D),
        PRIDE(0x8C3BBF),
        VIGILANT(0x5470A6),
        WORRIED(0x765A8C),
        PROTECTIVE(0x2F577D),
        MELANCHOLY(0x445B8C),
        CONTENT(0x9CD08F),
        RESTLESS(0xFF7F3F),
        NOSTALGIA(0xCCAA7D),
        PLAYFULNESS(0x42E6A4),
        LOYALTY(0x244C7A),
        
        // Ultra-rare emotions
        ECHOED_RESONANCE(0x1A3A52),  // Deep dark teal - whispers from the depths
        ARCANE_OVERFLOW(0x9D4EDD),   // Vibrant purple - enchantment energy
        PACK_SPIRIT(0xFF6B35);       // Warm orange - unified pack strength

        private final EmotionColorProfile palette;

        Emotion(int baseRgb) {
            this.palette = new EmotionColorProfile(baseRgb);
        }

        Emotion(int baseRgb, int accentRgb) {
            this.palette = new EmotionColorProfile(baseRgb, accentRgb);
        }

        public EmotionColorProfile palette() {
            return palette;
        }

        public TextColor baseColor() {
            return palette.baseColor();
        }

        public TextColor accentColor() {
            return palette.accentColor();
        }
    }

    /** Structured color metadata for authored emotions. */
    public record EmotionColorProfile(TextColor baseColor, TextColor accentColor) {
        private static final float DEFAULT_ACCENT_LIGHTEN = 0.18f;

        public EmotionColorProfile(int baseRgb) {
            this(TextColor.fromRgb(baseRgb), lighten(baseRgb, DEFAULT_ACCENT_LIGHTEN));
        }

        public EmotionColorProfile(int baseRgb, int accentRgb) {
            this(TextColor.fromRgb(baseRgb), TextColor.fromRgb(accentRgb));
        }

        private static TextColor lighten(int rgb, float factor) {
            factor = MathHelper.clamp(factor, 0f, 1f);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            r = Math.min(255, Math.round(r + (255 - r) * factor));
            g = Math.min(255, Math.round(g + (255 - g) * factor));
            b = Math.min(255, Math.round(b + (255 - b) * factor));
            return TextColor.fromRgb((r << 16) | (g << 8) | b);
        }
    }

    public record NatureEmotionProfile(@Nullable Emotion majorEmotion, float majorStrength,
                                        @Nullable Emotion minorEmotion, float minorStrength,
                                        @Nullable Emotion quirkEmotion, float quirkStrength) {
        public static final NatureEmotionProfile EMPTY = new NatureEmotionProfile(null, 0f, null, 0f, null, 0f);

        public boolean isEmpty() {
            return (majorEmotion == null || majorStrength <= 0f)
                && (minorEmotion == null || minorStrength <= 0f)
                && (quirkEmotion == null || quirkStrength <= 0f);
        }
    }

    public record NatureGuardTelemetry(float relationshipGuard, float dangerWindow,
                                        float contagionCap) {
    }

    public enum FlightCapabilitySource {
        SPECIES_TAG(true),
        STATE_METADATA(true),
        SPECIES_KEYWORD(false),
        ROLE_OVERRIDE(false),
        NONE(false);

        private final boolean metadataDerived;

        FlightCapabilitySource(boolean metadataDerived) {
            this.metadataDerived = metadataDerived;
        }

        public boolean isMetadataDerived() {
            return metadataDerived;
        }
    }

    public record FlightCapability(boolean canFly, FlightCapabilitySource source) {
        private static final FlightCapability NONE = new FlightCapability(false, FlightCapabilitySource.NONE);
        private static final FlightCapability SPECIES_TAG = new FlightCapability(true, FlightCapabilitySource.SPECIES_TAG);
        private static final FlightCapability STATE_METADATA = new FlightCapability(true, FlightCapabilitySource.STATE_METADATA);
        private static final FlightCapability SPECIES_KEYWORD = new FlightCapability(true, FlightCapabilitySource.SPECIES_KEYWORD);
        private static final FlightCapability ROLE_OVERRIDE = new FlightCapability(true, FlightCapabilitySource.ROLE_OVERRIDE);

        public static FlightCapability none() {
            return NONE;
        }

        public static FlightCapability fromSource(FlightCapabilitySource source) {
            return switch (source) {
                case SPECIES_TAG -> SPECIES_TAG;
                case STATE_METADATA -> STATE_METADATA;
                case SPECIES_KEYWORD -> SPECIES_KEYWORD;
                case ROLE_OVERRIDE -> ROLE_OVERRIDE;
                case NONE -> NONE;
            };
        }
    }

    // Emotion slots are fully managed by PetMoodEngine
    
    public static final class StateKeys {
        public static final String TAMED_TICK = "tamed_tick";
        public static final String LAST_PET_TIME = "last_pet_time";
        public static final String PET_COUNT = "pet_count";
        public static final String LAST_SOCIAL_BUFFER_TICK = "social_buffer_tick";
        public static final String LAST_CROUCH_CUDDLE_TICK = "last_crouch_cuddle_tick";
        // Snuggle goal state
        public static final String SNUGGLE_LAST_START_TICK = "snuggle_last_start_tick";
        public static final String SNUGGLE_COOLDOWN_UNTIL_TICK = "snuggle_cooldown_until_tick";
        public static final String SOCIAL_JITTER_SEED = "social_jitter_seed";
        public static final String GOSSIP_OPT_OUT_UNTIL = "gossip_opt_out_until";
        public static final String GOSSIP_CLUSTER_CURSOR = "gossip_cluster_cursor";
        public static final String THREAT_LAST_TICK = "threat_last_tick";
        public static final String THREAT_SAFE_STREAK = "threat_safe_streak";
        public static final String THREAT_SENSITIZED_STREAK = "threat_sensitized_streak";
        public static final String THREAT_LAST_DANGER = "threat_last_danger";
        public static final String THREAT_LAST_RECOVERY_TICK = "threat_last_recovery_tick";
        public static final String OWNER_LAST_HURT_TICK = "owner_last_hurt_tick";
        public static final String OWNER_LAST_HURT_SEVERITY = "owner_last_hurt_severity";
        public static final String OWNER_LAST_HEALTH_RATIO = "owner_last_health_ratio";
        public static final String OWNER_LAST_LOW_HEALTH_TICK = "owner_last_low_health_tick";
        public static final String OWNER_LAST_STATUS_HAZARD_TICK = "owner_last_status_hazard_tick";
        public static final String OWNER_LAST_STATUS_HAZARD_SEVERITY = "owner_last_status_hazard_severity";
        public static final String OWNER_LAST_NEARBY_TICK = "owner_last_nearby_tick";
        public static final String OWNER_LAST_NEARBY_DISTANCE = "owner_last_nearby_distance";
        public static final String OWNER_LAST_SEEN_TICK = "owner_last_seen_tick";
        public static final String OWNER_LAST_SEEN_DISTANCE = "owner_last_seen_distance";
        public static final String OWNER_LAST_SEEN_DIMENSION = "owner_last_seen_dimension";
        public static final String HEALTH_LAST_LOW_TICK = "health_last_low_tick";
        public static final String HEALTH_RECOVERY_COOLDOWN = "health_recovery_cooldown";
        public static final String PACK_LAST_NEARBY_TICK = "pack_last_nearby_tick";
        public static final String PACK_LAST_NEARBY_STRENGTH = "pack_last_nearby_strength";
        public static final String PACK_LAST_NEARBY_WEIGHTED_STRENGTH = "pack_last_nearby_weighted_strength";
        public static final String PACK_LAST_NEARBY_ALLIES = "pack_last_nearby_allies";
        public static final String PACK_LAST_ROLE_DIVERSITY = "pack_last_role_diversity";
        public static final String ARCANE_LAST_ENCHANT_TICK = "arcane_last_enchant_time";
        public static final String ARCANE_ENCHANT_STREAK = "arcane_enchant_streak";
        public static final String ARCANE_LAST_SURGE_TICK = "arcane_last_surge_tick";
        public static final String ARCANE_SURGE_STRENGTH = "arcane_surge_strength";
        public static final String ARCANE_LAST_SCAN_TICK = "arcane_last_scan_tick";
        public static final String ARCANE_CACHED_AMBIENT_ENERGY = "arcane_cached_ambient_energy";
        public static final String ARCANE_LAST_SCAN_POS = "arcane_last_scan_pos";
        public static final String LAST_PLAY_INTERACTION_TICK = "last_play_interaction_tick";
        public static final String LAST_FEED_TICK = "last_feed_tick";
        public static final String LAST_GIFT_TICK = "last_gift_tick";
        public static final String BREEDING_BIRTH_TICK = "breeding_birth_tick";
        public static final String BREEDING_PARENT_A_UUID = "breeding_parent_a_uuid";
        public static final String BREEDING_PARENT_B_UUID = "breeding_parent_b_uuid";
        public static final String BREEDING_OWNER_UUID = "breeding_owner_uuid";
        public static final String BREEDING_PRIMARY_ROLE = "breeding_primary_role";
        public static final String BREEDING_PARTNER_ROLE = "breeding_partner_role";
        public static final String BREEDING_INHERITED_ROLE = "breeding_inherited_role";
        public static final String BREEDING_INHERITED_STATS = "breeding_inherited_stats";
        public static final String BREEDING_SOURCE = "breeding_source";
        public static final String BREEDING_BIRTH_TIME_OF_DAY = "breeding_birth_time_of_day";
        public static final String BREEDING_BIRTH_IS_DAYTIME = "breeding_birth_is_daytime";
        public static final String BREEDING_BIRTH_IS_INDOORS = "breeding_birth_is_indoors";
        public static final String BREEDING_BIRTH_IS_RAINING = "breeding_birth_is_raining";
        public static final String BREEDING_BIRTH_IS_THUNDERING = "breeding_birth_is_thundering";
        public static final String BREEDING_BIRTH_NEARBY_PLAYER_COUNT = "breeding_birth_nearby_player_count";
        public static final String BREEDING_BIRTH_NEARBY_PET_COUNT = "breeding_birth_nearby_pet_count";
        public static final String BREEDING_BIRTH_DIMENSION = "breeding_birth_dimension";
        public static final String BREEDING_ASSIGNED_NATURE = "breeding_assigned_nature";
        public static final String ASSIGNED_NATURE = "assigned_nature";
        public static final String WILD_ASSIGNED_NATURE = "wild_assigned_nature";
        public static final String ASTROLOGY_SIGN = "astrology_sign";

        private StateKeys() {}
    }

    public PetComponent(MobEntity pet) {
        this.pet = pet;
        this.roleId = null;
        this.stateData = new HashMap<>();
        this.lastAttackTick = 0;
        this.isPerched = false;

        this.perceptionBus = new PerceptionBus();
        this.contextCache = new PetContextCache();
        this.contextSliceState = new ContextSliceState();
        this.stimulusTimeline = new StimulusTimeline();
        this.contextCache.attachTo(perceptionBus);
        this.perceptionBus.subscribeAll(stimulusTimeline);
        this.perceptionBus.subscribe(PerceptionStimulusType.OWNER_NEARBY, contextSliceState);
        this.perceptionBus.subscribe(PerceptionStimulusType.CROWD_SUMMARY, contextSliceState);
        this.perceptionBus.subscribe(PerceptionStimulusType.ENVIRONMENTAL_SNAPSHOT, contextSliceState);
        this.perceptionBus.subscribe(PerceptionStimulusType.WORLD_TICK, contextSliceState);
        this.contextCache.markAllDirty();
        this.experienceLog = new ExperienceLog();
        this.aiState.reset();

        // Initialize modules
        this.moodEngine = new PetMoodEngine(this);
        this.gossipLedger = new PetGossipLedger();
        this.inventoryModule = new DefaultInventoryModule();
        this.characteristicsModule = new DefaultCharacteristicsModule();
        this.historyModule = new DefaultHistoryModule();
        this.progressionModule = new DefaultProgressionModule();
        this.schedulingModule = new DefaultSchedulingModule();
        this.ownerModule = new DefaultOwnerModule();
        this.speciesMetadataModule = new DefaultSpeciesMetadataModule();
        this.relationshipModule = new DefaultRelationshipModule();
        
        // Attach modules
        this.inventoryModule.onAttach(this);
        this.characteristicsModule.onAttach(this);
        this.historyModule.onAttach(this);
        this.progressionModule.onAttach(this);
        this.schedulingModule.onAttach(this);
        this.ownerModule.onAttach(this);
        this.speciesMetadataModule.onAttach(this);
        this.relationshipModule.onAttach(this);
    }

    public void attachStateManager(StateManager manager) {
        if (this.stateManager == manager) {
            return;
        }
        this.stateManager = manager;
    }

    public MobEntity getPetEntity() {
        return pet;
    }

    // Module getters
    public InventoryModule getInventoryModule() {
        return inventoryModule;
    }

    public CharacteristicsModule getCharacteristicsModule() {
        return characteristicsModule;
    }

    public HistoryModule getHistoryModule() {
        return historyModule;
    }

    public ProgressionModule getProgressionModule() {
        return progressionModule;
    }

    public SchedulingModule getSchedulingModule() {
        return schedulingModule;
    }

    public OwnerModule getOwnerModule() {
        return ownerModule;
    }

    public SpeciesMetadataModule getSpeciesMetadataModule() {
        return speciesMetadataModule;
    }
    
    public RelationshipModule getRelationshipModule() {
        return relationshipModule;
    }

    public PetGossipLedger getGossipLedger() {
        return gossipLedger;
    }

    public PetMoodEngine getMoodEngine() {
        return moodEngine;
    }

    public PetAIState getAIState() {
        return aiState;
    }

    public PerceptionBus getPerceptionBus() {
        return perceptionBus;
    }

    public PetContextCache getContextCache() {
        return contextCache;
    }

    @Nullable
    public PlayerEntity getCachedOwnerEntity() {
        return contextSliceState.owner;
    }

    public float getCachedOwnerDistance() {
        return contextSliceState.ownerDistance;
    }

    public boolean isCachedOwnerNearby() {
        return contextSliceState.ownerNearby;
    }

    public List<Entity> getCachedCrowdEntities() {
        return contextSliceState.crowdEntities;
    }

    public PetContextCrowdSummary getCachedCrowdSummary() {
        return contextSliceState.crowdSummary;
    }

    @Nullable
    public EnvironmentPerceptionBridge.EnvironmentSnapshot getCachedEnvironmentSnapshot() {
        return contextSliceState.environmentSnapshot;
    }

    @Nullable
    public EnvironmentPerceptionBridge.WorldSnapshot getCachedWorldSnapshot() {
        return contextSliceState.worldSnapshot;
    }

    public long getLastOwnerStimulusTick() {
        return contextSliceState.lastOwnerStimulusTick;
    }

    public long getLastCrowdStimulusTick() {
        return contextSliceState.lastCrowdStimulusTick;
    }

    public void resetContextSliceCache() {
        contextSliceState.reset();
    }

    public StimulusSnapshot snapshotStimuli(long worldTime) {
        return stimulusTimeline != null ? stimulusTimeline.snapshot(worldTime) : StimulusSnapshot.empty();
    }

    private final class ContextSliceState implements PerceptionListener {
        private static final float OWNER_RADIUS = 16.0f;

        private PlayerEntity owner;
        private float ownerDistance = Float.MAX_VALUE;
        private boolean ownerNearby;
        private List<Entity> crowdEntities = List.of();
        private PetContextCrowdSummary crowdSummary = PetContextCrowdSummary.empty();
        private long lastOwnerStimulusTick = Long.MIN_VALUE;
        private long lastCrowdStimulusTick = Long.MIN_VALUE;
        private EnvironmentPerceptionBridge.EnvironmentSnapshot environmentSnapshot;
        private EnvironmentPerceptionBridge.WorldSnapshot worldSnapshot;

        @Override
        public void onStimulus(PerceptionStimulus stimulus) {
            if (stimulus == null) {
                return;
            }
            Identifier type = stimulus.type();
            if (PerceptionStimulusType.OWNER_NEARBY.equals(type)) {
                handleOwnerStimulus(stimulus);
            } else if (PerceptionStimulusType.CROWD_SUMMARY.equals(type)) {
                handleCrowdStimulus(stimulus);
            } else if (PerceptionStimulusType.ENVIRONMENTAL_SNAPSHOT.equals(type)) {
                handleEnvironmentStimulus(stimulus);
            } else if (PerceptionStimulusType.WORLD_TICK.equals(type)) {
                handleWorldStimulus(stimulus);
            }
        }

        private void handleOwnerStimulus(PerceptionStimulus stimulus) {
            Object payload = stimulus.payload();
            PlayerEntity player = payload instanceof PlayerEntity ? (PlayerEntity) payload : null;
            if (player != null && player.isRemoved()) {
                player = null;
            }
            owner = player;
            ownerDistance = computeOwnerDistance(player);
            ownerNearby = ownerDistance < OWNER_RADIUS;
            lastOwnerStimulusTick = stimulus.tick();
        }

        private float computeOwnerDistance(@Nullable PlayerEntity player) {
            if (player == null) {
                return Float.MAX_VALUE;
            }
            if (pet == null || pet.getEntityWorld() == null) {
                return Float.MAX_VALUE;
            }
            double squared = pet.squaredDistanceTo(player);
            if (Double.isNaN(squared) || squared < 0.0D) {
                squared = 0.0D;
            }
            return (float) Math.sqrt(squared);
        }

        private void handleCrowdStimulus(PerceptionStimulus stimulus) {
            crowdEntities = convertCrowdPayload(stimulus.payload());
            crowdSummary = crowdEntities.isEmpty()
                ? PetContextCrowdSummary.empty()
                : PetContextCrowdSummary.fromEntities(pet, crowdEntities);
            lastCrowdStimulusTick = stimulus.tick();
        }

        private List<Entity> convertCrowdPayload(@Nullable Object payload) {
            if (!(payload instanceof List<?> list) || list.isEmpty()) {
                return List.of();
            }
            List<Entity> converted = new ArrayList<>(list.size());
            for (Object value : list) {
                if (value instanceof PetSwarmIndex.SwarmEntry entry) {
                    MobEntity other = entry.pet();
                    if (other != null && other != pet && !other.isRemoved()) {
                        converted.add(other);
                    }
                    continue;
                }
                if (value instanceof Entity entity && entity != pet && !entity.isRemoved()) {
                    converted.add(entity);
                }
            }
            return converted.isEmpty() ? List.of() : List.copyOf(converted);
        }

        private void handleEnvironmentStimulus(PerceptionStimulus stimulus) {
            Object payload = stimulus.payload();
            environmentSnapshot = payload instanceof EnvironmentPerceptionBridge.EnvironmentSnapshot snapshot
                ? snapshot
                : null;
        }

        private void handleWorldStimulus(PerceptionStimulus stimulus) {
            Object payload = stimulus.payload();
            worldSnapshot = payload instanceof EnvironmentPerceptionBridge.WorldSnapshot snapshot
                ? snapshot
                : null;
        }

        void reset() {
            owner = null;
            ownerDistance = Float.MAX_VALUE;
            ownerNearby = false;
            crowdEntities = List.of();
            crowdSummary = PetContextCrowdSummary.empty();
            lastOwnerStimulusTick = Long.MIN_VALUE;
            lastCrowdStimulusTick = Long.MIN_VALUE;
            environmentSnapshot = null;
            worldSnapshot = null;
        }
    }

    public ExperienceLog getExperienceLog() {
        return experienceLog;
    }

    public void recordExperience(Identifier goalId, float satisfaction, long tick) {
        experienceLog.record(goalId, satisfaction, tick);
    }

    public void markContextDirty(ContextSlice... slices) {
        if (contextCache == null || slices == null) {
            return;
        }
        for (ContextSlice slice : slices) {
            if (slice != null) {
                contextCache.markDirty(slice);
            }
        }
    }

    public void notifyMoodBlendUpdated() {
        markContextDirty(ContextSlice.MOOD);
        publishStimulus(PerceptionStimulusType.MOOD_BLEND, ContextSlice.MOOD, null);
    }

    public void notifyEmotionSampleUpdated() {
        markContextDirty(ContextSlice.EMOTIONS);
        publishStimulus(PerceptionStimulusType.EMOTION_SAMPLE, ContextSlice.EMOTIONS, null);
    }

    public void notifyEnergyProfileUpdated() {
        markContextDirty(ContextSlice.ENERGY);
        publishStimulus(PerceptionStimulusType.ENERGY_PROFILE, ContextSlice.ENERGY, null);
    }

    public void notifyEnvironmentUpdated(@Nullable EnvironmentPerceptionBridge.EnvironmentSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        markContextDirty(ContextSlice.ENVIRONMENT);
        publishStimulus(PerceptionStimulusType.ENVIRONMENTAL_SNAPSHOT, ContextSlice.ENVIRONMENT, snapshot);
    }

    public void notifyWorldTimeUpdated(@Nullable EnvironmentPerceptionBridge.WorldSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        markContextDirty(ContextSlice.WORLD);
        publishStimulus(PerceptionStimulusType.WORLD_TICK, ContextSlice.WORLD, snapshot);
    }

    private long currentWorldTime() {
        return pet.getEntityWorld() != null ? pet.getEntityWorld().getTime() : 0L;
    }

    private void publishStimulus(Identifier type, ContextSlice slice, @Nullable Object payload) {
        if (slice == null) {
            publishStimulus(type, EnumSet.of(ContextSlice.ALL), payload);
            return;
        }
        publishStimulus(type, EnumSet.of(slice), payload);
    }

    private void publishStimulus(Identifier type, EnumSet<ContextSlice> slices, @Nullable Object payload) {
        if (perceptionBus == null || type == null) {
            return;
        }
        if (contextCache != null) {
            contextCache.markDirty(ContextSlice.STIMULI);
        }
        perceptionBus.publish(new PerceptionStimulus(type, currentWorldTime(), slices, payload));
    }

    public void recordGoalStart(Identifier goalId) {
        aiState.recordGoalStart(goalId);
        if (goalId == null) {
            return;
        }
        markContextDirty(ContextSlice.HISTORY);
        publishStimulus(PerceptionStimulusType.GOAL_HISTORY, ContextSlice.HISTORY, goalId);
    }

    public void recordGoalCompletion(Identifier goalId, long worldTime) {
        aiState.recordGoalCompletion(goalId, worldTime);
        if (goalId == null) {
            return;
        }
        markContextDirty(ContextSlice.HISTORY);
        publishStimulus(PerceptionStimulusType.GOAL_HISTORY, ContextSlice.HISTORY, goalId);
    }

    public Deque<Identifier> getRecentGoalsSnapshot() {
        return aiState.getRecentGoalsSnapshot();
    }

    public Map<Identifier, Long> getGoalExecutionTimestamps() {
        return aiState.getGoalExecutionTimestamps();
    }

    public Map<String, Integer> getQuirkCountersSnapshot() {
        return aiState.getQuirkCountersSnapshot();
    }

    public SocialSnapshot snapshotSocialGraph() {
        if (relationshipModule == null) {
            return SocialSnapshot.empty();
        }
        return SocialSnapshot.fromRelationships(relationshipModule.getAllRelationships());
    }

    public boolean isDormant() {
        if (pet == null) {
            return false;
        }
        if (pet.isAiDisabled()) {
            return true;
        }
        var world = pet.getEntityWorld();
        if (world == null) {
            return false;
        }
        if (world.getPlayers().isEmpty()) {
            return true;
        }
        double activationRadiusSq = 128.0 * 128.0;
        for (var player : world.getPlayers()) {
            if (player == null) {
                continue;
            }
            if (player.squaredDistanceTo(pet) <= activationRadiusSq) {
                return false;
            }
        }
        return true;
    }

    public void setQuirkCounter(String key, int value) {
        if (key == null) {
            return;
        }
        aiState.setQuirkCounter(key, value);
        markContextDirty(ContextSlice.HISTORY);
        publishStimulus(PerceptionStimulusType.GOAL_HISTORY, ContextSlice.HISTORY, key);
    }

    public void incrementQuirkCounter(String key) {
        if (key == null) {
            return;
        }
        aiState.incrementQuirkCounter(key);
        markContextDirty(ContextSlice.HISTORY);
        publishStimulus(PerceptionStimulusType.GOAL_HISTORY, ContextSlice.HISTORY, key);
    }

    public void recordRumor(long topicId, float intensity, float confidence, long currentTick) {
        recordRumor(topicId, intensity, confidence, currentTick, null, null);
    }

    public void recordRumor(long topicId, float intensity, float confidence, long currentTick,
                            @Nullable UUID sourceUuid, @Nullable Text paraphrased) {
        recordRumor(topicId, intensity, confidence, currentTick, sourceUuid, paraphrased, false);
    }

    public void recordRumor(long topicId, float intensity, float confidence, long currentTick,
                            @Nullable UUID sourceUuid, @Nullable Text paraphrased, boolean witnessed) {
        gossipLedger.recordRumor(topicId, intensity, confidence, currentTick, sourceUuid, paraphrased, witnessed);
        scheduleNextGossipDecay(currentTick + Math.max(MIN_GOSSIP_DECAY_DELAY, gossipLedger.scheduleNextDecayDelay()));
    }

    public boolean isGossipOptedOut(long currentTick) {
        Long until = getStateData(StateKeys.GOSSIP_OPT_OUT_UNTIL, Long.class);
        if (until == null) {
            return false;
        }
        if (currentTick >= until) {
            clearStateData(StateKeys.GOSSIP_OPT_OUT_UNTIL);
            return false;
        }
        return true;
    }

    public void markGossipOptOut(long untilTick) {
        setStateData(StateKeys.GOSSIP_OPT_OUT_UNTIL, Math.max(0L, untilTick));
    }

    public void optOutOfGossip(long currentTick) {
        long duration = pickOptOutDuration();
        long until = currentTick + duration;
        Long existing = getStateData(StateKeys.GOSSIP_OPT_OUT_UNTIL, Long.class);
        if (existing != null && existing > until) {
            until = existing;
        }
        markGossipOptOut(until);
        requestGossipOptOutWander();
    }

    private int pickOptOutDuration() {
        return GOSSIP_OPT_OUT_MIN_DURATION
            + pet.getRandom().nextInt(Math.max(1, GOSSIP_OPT_OUT_MAX_DURATION - GOSSIP_OPT_OUT_MIN_DURATION + 1));
    }

    public void requestGossipOptOutWander() {
        if (pet == null || pet.getEntityWorld() == null || pet.getEntityWorld().isClient()) {
            return;
        }
        if (pet.isAiDisabled() || pet.getNavigation() == null) {
            return;
        }
        if (!(pet instanceof PathAwareEntity pathAware)) {
            return;
        }
        Vec3d target = NoPenaltyTargeting.find(pathAware, 6, 3);
        if (target != null) {
            pet.getNavigation().startMovingTo(target.x, target.y, target.z, 1.05D);
        }
    }

    public void decayRumors(long currentTick) {
        gossipLedger.tickDecay(currentTick);
    }

    public boolean hasShareableRumors(long currentTick) {
        return gossipLedger.hasShareableRumors(currentTick)
            || gossipLedger.hasAbstractTopicsReady(currentTick);
    }

    public GossipShareSnapshot snapshotGossipShareables(long currentTick, int maxSamples) {
        int limit = Math.max(1, maxSamples);
        boolean optedOut = isGossipOptedOut(currentTick);
        if (optedOut) {
            return GossipShareSnapshot.empty();
        }
        List<RumorEntry> fresh = gossipLedger.peekFreshRumors(limit, currentTick);
        int remaining = Math.max(0, limit - fresh.size());
        List<RumorEntry> abstracts = remaining > 0
            ? gossipLedger.peekAbstractRumors(remaining, currentTick)
            : List.of();
        return new GossipShareSnapshot(
            false,
            fresh == null || fresh.isEmpty() ? List.of() : List.copyOf(fresh),
            abstracts == null || abstracts.isEmpty() ? List.of() : List.copyOf(abstracts)
        );
    }

    public Stream<RumorEntry> streamRumors() {
        return gossipLedger.stream();
    }

    public static final class GossipShareSnapshot {
        private static final GossipShareSnapshot EMPTY = new GossipShareSnapshot(true, List.of(), List.of());

        private final boolean optedOut;
        private final List<RumorEntry> freshRumors;
        private final List<RumorEntry> abstractRumors;

        private GossipShareSnapshot(boolean optedOut,
                                    List<RumorEntry> freshRumors,
                                    List<RumorEntry> abstractRumors) {
            this.optedOut = optedOut;
            this.freshRumors = freshRumors == null || freshRumors.isEmpty() ? List.of() : freshRumors;
            this.abstractRumors = abstractRumors == null || abstractRumors.isEmpty() ? List.of() : abstractRumors;
        }

        public static GossipShareSnapshot empty() {
            return EMPTY;
        }

        public boolean optedOut() {
            return optedOut;
        }

        public List<RumorEntry> freshRumors() {
            return freshRumors;
        }

        public List<RumorEntry> abstractRumors() {
            return abstractRumors;
        }
    }

    public static PetComponent getOrCreate(MobEntity pet) {
        if (pet.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            return StateManager.forWorld(serverWorld).getPetComponent(pet);
        }
        return COMPONENTS.computeIfAbsent(pet, PetComponent::new);
    }
    
    @Nullable
    public static PetComponent get(MobEntity pet) {
        if (pet == null) {
            return null;
        }
        if (pet.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            StateManager manager = StateManager.forWorld(serverWorld);
            return manager.getAllPetComponents().get(pet);
        }
        return COMPONENTS.get(pet);
    }
    
    public static void set(MobEntity pet, PetComponent component) {
        COMPONENTS.put(pet, component);
    }
    
    public static void remove(MobEntity pet) {
        if (pet.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            StateManager.forWorld(serverWorld).removePet(pet);
        }
        COMPONENTS.remove(pet);
    }
    
    public MobEntity getPet() {
        return pet;
    }

    // Name-based attributes methods
    public List<AttributeKey> getNameAttributes() {
        return characteristicsModule.getNameAttributes();
    }

    public void setNameAttributes(List<AttributeKey> attributes) {
        characteristicsModule.setNameAttributes(attributes != null ? attributes : new ArrayList<>());
    }

    public void addNameAttribute(AttributeKey attribute) {
        characteristicsModule.addNameAttribute(attribute);
    }

    public void removeNameAttribute(AttributeKey attribute) {
        characteristicsModule.removeNameAttribute(attribute);
    }

    public void clearNameAttributes() {
        characteristicsModule.setNameAttributes(new ArrayList<>());
    }

    public void resetRoleAffinityBonuses() {
        characteristicsModule.resetRoleAffinityBonuses();
    }

    public void applyRoleAffinityBonuses(Identifier roleId, String[] statKeys, float[] bonuses) {
        characteristicsModule.applyRoleAffinityBonuses(roleId, statKeys, bonuses);
    }

    public void addRoleAffinityBonus(@Nullable PetRoleType roleType, float bonus) {
        if (roleType == null) {
            return;
        }
        String[] keys;
        if (roleType.statAffinities().isEmpty()) {
            keys = PetImprint.statKeyArray();
        } else {
            keys = roleType.statAffinities().keySet().toArray(String[]::new);
        }
        float[] values = new float[keys.length];
        for (int i = 0; i < keys.length; i++) {
            values[i] = bonus;
        }
        characteristicsModule.applyRoleAffinityBonuses(roleType.id(), keys, values);
    }

    public void addRoleAffinityBonus(PetRoleType[] roles, float bonus) {
        if (roles == null || roles.length == 0) {
            return;
        }
        for (PetRoleType role : roles) {
            addRoleAffinityBonus(role, bonus);
        }
    }


    private boolean isCharacteristicsDataEmpty(CharacteristicsModule.Data data) {
        if (data == null) {
            return true;
        }

        boolean hasImprint = data.imprint() != null;
        boolean hasEmotionProfile = data.natureEmotionProfile() != null && !data.natureEmotionProfile().isEmpty();
        boolean hasNameAttributes = data.nameAttributes() != null && !data.nameAttributes().isEmpty();
        boolean hasRoleAffinity = data.roleAffinityBonuses() != null && !data.roleAffinityBonuses().isEmpty();

        boolean hasNonDefaultTuning = Float.compare(data.natureVolatilityMultiplier(), 1.0f) != 0
            || Float.compare(data.natureResilienceMultiplier(), 1.0f) != 0
            || Float.compare(data.natureContagionModifier(), 1.0f) != 0
            || Float.compare(data.natureGuardModifier(), 1.0f) != 0;

        return !(hasImprint || hasEmotionProfile || hasNameAttributes || hasRoleAffinity || hasNonDefaultTuning);
    }

    public boolean hasNameAttribute(String type) {
        return characteristicsModule.getNameAttributes().stream()
            .anyMatch(attr -> attr.normalizedType().equals(type.toLowerCase()));
    }

    public void refreshSpeciesDescriptor() {
        speciesMetadataModule.markSpeciesDirty();
        speciesMetadataModule.markFlightDirty();
    }

    public void ensureSpeciesDescriptorInitialized() {
        // Module handles lazy initialization
        getSpeciesDescriptor();
    }

    public Identifier getSpeciesDescriptor() {
        return speciesMetadataModule.getSpeciesDescriptor();
    }

    public boolean matchesSpeciesKeyword(String... keywords) {
        if (keywords == null || keywords.length == 0) {
            return false;
        }

        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            String normalized = keyword.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }

            if (matchesSpeciesKeywordInternal(normalized)) {
                return true;
            }
        }

        return false;
    }

    public FlightCapability getFlightCapability() {
        return speciesMetadataModule.getFlightCapability();
    }

    public boolean isFlightCapable() {
        return getFlightCapability().canFly();
    }

    public FlightCapability computeFlightCapability() {
        if (hasSpeciesTag(PetsplusEntityTypeTags.FLYERS)) {
            return FlightCapability.fromSource(FlightCapabilitySource.SPECIES_TAG);
        }

        if (stateDataIndicatesFlight()) {
            return FlightCapability.fromSource(FlightCapabilitySource.STATE_METADATA);
        }

        if (matchesSpeciesKeyword("fly", "flying", "avian", "bird", "winged", "sky", "airborne")) {
            return FlightCapability.fromSource(FlightCapabilitySource.SPECIES_KEYWORD);
        }

        if (PetRoleType.SKYRIDER_ID.equals(getRoleId())) {
            return FlightCapability.fromSource(FlightCapabilitySource.ROLE_OVERRIDE);
        }

        return FlightCapability.none();
    }

    private boolean stateDataIndicatesFlight() {
        if (stateData.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, Object> entry : stateData.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!(key.startsWith("tag_") || key.startsWith("context_"))) {
                continue;
            }

            Object value = entry.getValue();
            if (key.contains("flight") || key.contains("fly") || key.contains("air")) {
                if (valueIndicatesFlight(value)) {
                    return true;
                }
            }

            if (key.contains("movement") || key.contains("capability") || key.contains("ability")) {
                if (valueIndicatesFlight(value)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean valueIndicatesFlight(@Nullable Object value) {
        if (value == null) {
            return false;
        }

        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value instanceof String str) {
            String normalized = str.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                return false;
            }
            return normalized.contains("fly") || normalized.contains("wing") || normalized.contains("airborne");
        }

        if (value instanceof Identifier id) {
            return identifierContains(id, "fly") || identifierContains(id, "wing") || identifierContains(id, "air");
        }

        return false;
    }

    private boolean matchesSpeciesKeywordInternal(String keyword) {
        if (keyword.isEmpty()) {
            return false;
        }

        if (identifierContains(getSpeciesDescriptor(), keyword)) {
            return true;
        }

        Identifier typeId = Registries.ENTITY_TYPE.getId(pet.getType());
        if (identifierContains(typeId, keyword)) {
            return true;
        }

        for (String key : SPECIES_STATE_KEYS) {
            Identifier storedId = getStateData(key, Identifier.class);
            if (identifierContains(storedId, keyword)) {
                return true;
            }

            String storedString = getStateData(key, String.class);
            if (storedString != null && storedString.toLowerCase(Locale.ROOT).contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private boolean identifierContains(@Nullable Identifier identifier, String keyword) {
        if (identifier == null) {
            return false;
        }
        String path = identifier.getPath();
        return path != null && path.toLowerCase(Locale.ROOT).contains(keyword);
    }

    public boolean hasSpeciesTag(TagKey<EntityType<?>> tag) {
        if (tag == null) {
            return false;
        }

        EntityType<?> type = pet.getType();
        if (type.isIn(tag)) {
            return true;
        }

        Identifier descriptor = getSpeciesDescriptor();
        if (descriptor == null || descriptor.equals(Registries.ENTITY_TYPE.getId(type))) {
            return false;
        }

        if (!Registries.ENTITY_TYPE.containsId(descriptor)) {
            return false;
        }

        EntityType<?> descriptorType = Registries.ENTITY_TYPE.get(descriptor);
        return descriptorType.isIn(tag);
    }

    public Identifier getRoleId() {
        return roleId != null ? roleId : DEFAULT_ROLE_ID;
    }

    @Nullable
    public Identifier getAssignedRoleId() {
        return roleId;
    }

    public boolean hasAssignedRole() {
        return roleId != null;
    }

    public boolean hasRole(@Nullable Identifier id) {
        if (id == null) {
            return false;
        }
        return getRoleId().equals(id);
    }

    public boolean hasRole(@Nullable PetRoleType type) {
        return type != null && hasRole(type.id());
    }

    public boolean hasRole(@Nullable RegistryEntry<PetRoleType> entry) {
        return entry != null && hasRole(entry.value());
    }

    public void setRoleId(@Nullable Identifier id) {
        Identifier newId = id != null ? id : DEFAULT_ROLE_ID;
        this.roleId = newId;
        speciesMetadataModule.markFlightDirty();

        // Apply attribute modifiers when role changes
        try {
            woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(this.pet, this);
        } catch (Throwable error) {
            Petsplus.LOGGER.debug("Skipping attribute refresh during role update", error);
        }

        // Apply AI enhancements when role changes
        if (this.pet.getEntityWorld() instanceof ServerWorld) {
            try {
                woflo.petsplus.ai.PetAIEnhancements.enhancePetAI(this.pet, this);
            } catch (Throwable error) {
                Petsplus.LOGGER.debug("Skipping AI enhancement during role update", error);
            }
        }

        if (this.pet.getEntityWorld() instanceof ServerWorld serverWorld) {
            resetTickScheduling(serverWorld.getTime());
        }
        
    }

    public void setRoleType(@Nullable PetRoleType type) {
        setRoleId(type != null ? type.id() : DEFAULT_ROLE_ID);
    }

    public void setRoleEntry(@Nullable RegistryEntry<PetRoleType> entry) {
        if (entry == null) {
            setRoleId(DEFAULT_ROLE_ID);
            return;
        }

        setRoleType(entry.value());
    }

    @Nullable
    public RegistryEntry<PetRoleType> getRoleEntry() {
        try {
            Registry<PetRoleType> registry = PetsPlusRegistries.petRoleTypeRegistry();
            PetRoleType type = registry.get(getRoleId());
            if (type != null) {
                return registry.getEntry(type);
            }

            PetRoleType fallback = registry.get(DEFAULT_ROLE_ID);
            return fallback != null ? registry.getEntry(fallback) : null;
        } catch (Throwable error) {
            return null;
        }
    }

    public PetRoleType getRoleType() {
        return getRoleType(true);
    }

    public PetRoleType getRoleType(boolean logMissing) {
        try {
            Registry<PetRoleType> registry = PetsPlusRegistries.petRoleTypeRegistry();
            Identifier roleId = getRoleId();
            PetRoleType type = registry.get(roleId);
            if (type != null) {
                return type;
            }

            if (logMissing) {
                Petsplus.LOGGER.warn("Pet {} references missing role {}; defaulting to {}", pet.getUuid(), roleId, DEFAULT_ROLE_ID);
            }

            PetRoleType fallback = registry.get(DEFAULT_ROLE_ID);
            return fallback != null ? fallback : PetRoleType.GUARDIAN;
        } catch (Throwable error) {
            if (logMissing) {
                Petsplus.LOGGER.debug("Using guardian role fallback due to registry access failure", error);
            }
            return PetRoleType.GUARDIAN;
        }
    }

    @Nullable
    public PlayerEntity getOwner() {
        if (!(pet.getEntityWorld() instanceof ServerWorld world)) return null;
        return ownerModule.getOwner(world);
    }

    public void setOwner(@Nullable PlayerEntity owner) {
        ownerModule.setOwner(owner);
    }

    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        ownerModule.setOwnerUuid(ownerUuid);
    }

    public void ensureSchedulingInitialized(long currentTick) {
        if (schedulingModule.isInitialized()) {
            return;
        }
        if (!(pet.getEntityWorld() instanceof ServerWorld) || stateManager == null) {
            return;
        }
        schedulingModule.markInitialized();
        scheduleNextIntervalTick(currentTick);
        scheduleNextAuraCheck(currentTick);
        scheduleNextSupportPotionScan(currentTick);
        scheduleNextParticleCheck(currentTick);
        scheduleNextGossipDecay(currentTick + Math.max(MIN_GOSSIP_DECAY_DELAY, gossipLedger.scheduleNextDecayDelay()));
        scheduleNextMoodProviderTick(currentTick);
    }

    public void resetTickScheduling(long currentTick) {
        if (stateManager != null) {
            stateManager.unscheduleAllTasks(this);
        }
        markSchedulingUninitialized();
        ensureSchedulingInitialized(currentTick);
    }

    public void scheduleNextIntervalTick(long nextTick) {
        submitScheduledTask(PetWorkScheduler.TaskType.INTERVAL, nextTick);
    }

    public void scheduleNextAuraCheck(long nextTick) {
        submitScheduledTask(PetWorkScheduler.TaskType.AURA, nextTick);
    }

    public void scheduleNextSupportPotionScan(long nextTick) {
        submitScheduledTask(PetWorkScheduler.TaskType.SUPPORT_POTION, nextTick);
    }

    public void scheduleNextParticleCheck(long nextTick) {
        submitScheduledTask(PetWorkScheduler.TaskType.PARTICLE, nextTick);
    }

    public void scheduleNextGossipDecay(long nextTick) {
        submitScheduledTask(PetWorkScheduler.TaskType.GOSSIP_DECAY, nextTick);
    }

    public void scheduleNextMoodProviderTick(long nextTick) {
        submitScheduledTask(PetWorkScheduler.TaskType.MOOD_PROVIDER, nextTick);
    }

    private void submitScheduledTask(PetWorkScheduler.TaskType type, long nextTick) {
        if (!(pet.getEntityWorld() instanceof ServerWorld) || stateManager == null) {
            return;
        }
        long sanitized = nextTick == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, nextTick);
        stateManager.schedulePetTask(this, type, sanitized);
    }

    public void onTaskScheduled(PetWorkScheduler.TaskType type, long tick) {
        schedulingModule.scheduleTask(type, tick);
    }

    public void onTaskUnschedule(PetWorkScheduler.TaskType type) {
        schedulingModule.unscheduleTask(type);
    }

    public void tickGossipLedger(long currentTick) {
        gossipLedger.tickDecay(currentTick);
        long nextDelay = Math.max(MIN_GOSSIP_DECAY_DELAY, gossipLedger.scheduleNextDecayDelay());
        if (stateManager != null) {
            nextDelay = stateManager.scaleInterval(nextDelay);
        }
        scheduleNextGossipDecay(currentTick + nextDelay);
    }



    public boolean hasScheduledWork() {
        return schedulingModule.hasScheduledWork();
    }

    public boolean hasDueWork(long currentTick) {
        return schedulingModule.hasDueWork(currentTick);
    }

    public void markSchedulingUninitialized() {
        schedulingModule.reset();
    }

    public void invalidateSwarmTracking() {
        swarmTrackingInitialized = false;
        lastSwarmCellKey = Long.MIN_VALUE;
    }

    public void recordGoalSuggestion(@Nullable Identifier goalId, float score, long tick) {
        aiState.recordGoalSuggestion(goalId, score, tick);
    }

    @Nullable
    public Identifier getLastSuggestedGoalId() {
        return aiState.getLastSuggestedGoalId();
    }

    public float getLastSuggestionScore() {
        return aiState.getLastSuggestionScore();
    }

    public long getLastSuggestionTick() {
        return aiState.getLastSuggestionTick();
    }

    public SwarmStateSnapshot snapshotSwarmState() {
        return new SwarmStateSnapshot(swarmTrackingInitialized, lastSwarmCellKey, lastSwarmX, lastSwarmY, lastSwarmZ);
    }

    public void applySwarmUpdate(PetSwarmIndex index, long cellKey, double x, double y, double z) {
        swarmTrackingInitialized = true;
        lastSwarmCellKey = cellKey;
        lastSwarmX = x;
        lastSwarmY = y;
        lastSwarmZ = z;
        index.updatePet(pet, this);
    }

    /**
     * Updates the cached swarm tracking entry if the pet has moved a significant
     * amount since the last update. Returns {@code true} when the underlying
     * spatial index was refreshed so callers can trigger owner-scoped events
     * only when necessary.
     */
    public boolean updateSwarmTrackingIfMoved(PetSwarmIndex index) {
        if (!(pet.getEntityWorld() instanceof ServerWorld)) {
            return false;
        }
        double x = pet.getX();
        double y = pet.getY();
        double z = pet.getZ();
        long cellKey = ChunkSectionPos.asLong(
            ChunkSectionPos.getSectionCoord(MathHelper.floor(x)),
            ChunkSectionPos.getSectionCoord(MathHelper.floor(y)),
            ChunkSectionPos.getSectionCoord(MathHelper.floor(z))
        );

        double dx = x - lastSwarmX;
        double dy = y - lastSwarmY;
        double dz = z - lastSwarmZ;
        double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);

        final double movementThresholdSq = 0.0625; // ~0.25 blocks of travel

        if (!swarmTrackingInitialized || cellKey != lastSwarmCellKey || distanceSq >= movementThresholdSq) {
            swarmTrackingInitialized = true;
            lastSwarmCellKey = cellKey;
            lastSwarmX = x;
            lastSwarmY = y;
            lastSwarmZ = z;
            index.updatePet(pet, this);
            return true;
        }
        return false;
    }

    public record SwarmStateSnapshot(boolean initialized, long cellKey, double x, double y, double z) {
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerModule.getOwnerUuid();
    }
    
    public boolean isOwnedBy(@Nullable PlayerEntity player) {
        return ownerModule.isOwnedBy(player);
    }
    
    public boolean isOnCooldown(String key) {
        return schedulingModule.isOnCooldown(key, pet.getEntityWorld().getTime());
    }

    public void setCooldown(String key, int ticks) {
        schedulingModule.setCooldown(key, pet.getEntityWorld().getTime() + ticks);
    }

    public void clearCooldown(String key) {
        schedulingModule.clearCooldown(key);
    }

    /**
     * Produces an immutable snapshot of the pet's cooldown map for use in
     * asynchronous processing. The snapshot is safe to share across threads as
     * it does not retain references to the live mutable state.
     */
    public Map<String, Long> copyCooldownSnapshot() {
        return Map.copyOf(schedulingModule.getAllCooldowns());
    }

    /**
     * Updates cooldowns managed by the scheduling module.
     * Particle effects are triggered by game systems when cooldowns expire.
     */
    public void updateCooldowns() {
        // SchedulingModule manages cooldown state and expiration
    }

    public void applyCooldownExpirations(List<String> keys, long currentTick) {
        if (!(pet.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return;
        }
        if (keys == null || keys.isEmpty()) {
            return;
        }
        boolean anyExpired = false;
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            if (!schedulingModule.isOnCooldown(key, currentTick)) {
                schedulingModule.clearCooldown(key);
                anyExpired = true;
            }
        }
        if (anyExpired) {
            woflo.petsplus.ui.CooldownParticleManager.triggerCooldownRefresh(serverWorld, pet);
        }
    }

    public long getRemainingCooldown(String key) {
        Map<String, Long> cooldowns = schedulingModule.getAllCooldowns();
        Long cooldownEnd = cooldowns.get(key);
        if (cooldownEnd == null) return 0;
        return Math.max(0, cooldownEnd - pet.getEntityWorld().getTime());
    }

    public void setFollowSpacingSample(double offsetX, double offsetZ, float padding, long sampleTick) {
        this.followSpacingOffsetX = offsetX;
        this.followSpacingOffsetZ = offsetZ;
        this.followSpacingPadding = padding;
        this.followSpacingSampleTick = sampleTick;
    }

    public double getFollowSpacingOffsetX() {
        return followSpacingOffsetX;
    }

    public double getFollowSpacingOffsetZ() {
        return followSpacingOffsetZ;
    }

    public float getFollowSpacingPadding() {
        return followSpacingPadding;
    }

    public long getFollowSpacingSampleTick() {
        return followSpacingSampleTick;
    }

    public void requestMoodFollowHold(long now, int holdTicks, float distanceBonus) {
        if (holdTicks <= 0) {
            return;
        }
        long candidateExpiry = now + holdTicks;
        if (candidateExpiry > moodFollowHoldUntilTick) {
            moodFollowHoldUntilTick = candidateExpiry;
        }
        moodFollowDistanceBonus = Math.max(moodFollowDistanceBonus, Math.max(0.0f, distanceBonus));
    }

    public boolean isMoodFollowHoldActive(long now) {
        if (now >= moodFollowHoldUntilTick) {
            if (moodFollowHoldUntilTick != Long.MIN_VALUE) {
                moodFollowHoldUntilTick = Long.MIN_VALUE;
                moodFollowDistanceBonus = 0.0f;
            }
            return false;
        }
        return true;
    }

    public float getMoodFollowDistanceBonus(long now) {
        if (now >= moodFollowHoldUntilTick) {
            if (moodFollowHoldUntilTick != Long.MIN_VALUE) {
                moodFollowHoldUntilTick = Long.MIN_VALUE;
                moodFollowDistanceBonus = 0.0f;
            }
            return 0.0f;
        }
        return moodFollowDistanceBonus;
    }

    public void clearMoodFollowHold() {
        moodFollowHoldUntilTick = Long.MIN_VALUE;
        moodFollowDistanceBonus = 0.0f;
    }

    public int getOrCreateSocialJitterSeed() {
        Integer stored = getStateData(StateKeys.SOCIAL_JITTER_SEED, Integer.class);
        if (stored != null) {
            return stored;
        }
        int seed = (int) (Math.abs(mixStableSeed(0x5EED5C1AL) & 0x7FFFFFFFL));
        setStateData(StateKeys.SOCIAL_JITTER_SEED, seed);
        return seed;
    }

    public void beginCrouchCuddle(UUID ownerId, long expiryTick) {
        ownerModule.recordCrouchCuddle(ownerId, expiryTick);
        if (pet.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            setStateData(StateKeys.LAST_CROUCH_CUDDLE_TICK, serverWorld.getTime());
        }
    }

    public void refreshCrouchCuddle(UUID ownerId, long expiryTick) {
        ownerModule.recordCrouchCuddle(ownerId, expiryTick);
        if (pet.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            setStateData(StateKeys.LAST_CROUCH_CUDDLE_TICK, serverWorld.getTime());
        }
    }

    public void endCrouchCuddle(UUID ownerId) {
        ownerModule.clearCrouchCuddle(ownerId);
    }

    public boolean isCrouchCuddleActiveWith(@Nullable PlayerEntity owner, long currentTick) {
        return ownerModule.hasActiveCrouchCuddle(currentTick, owner != null ? owner.getUuid() : null);
    }

    public boolean isCrouchCuddleActive(long currentTick) {
        return ownerModule.hasActiveCrouchCuddle(currentTick, null);
    }

    @Nullable
    public UUID getCrouchCuddleOwnerId() {
        return ownerModule.getCrouchCuddleOwnerId();
    }
    
    public void setStateData(String key, Object value) {
        setStateDataInternal(key, value, true);
    }

    public void clearStateData(String key) {
        if (!stateData.containsKey(key)) {
            return;
        }

        stateData.remove(key);

        if (shouldInvalidateSpeciesDescriptor(key)) {
            refreshSpeciesDescriptor();
        }
        if (shouldInvalidateFlightCapability(key)) {
            speciesMetadataModule.markFlightDirty();
        }
        markContextDirty(ContextSlice.STATE_DATA);
        publishStimulus(PerceptionStimulusType.STATE_DATA, ContextSlice.STATE_DATA, key);
    }

    private void setStateDataSilently(String key, Object value) {
        setStateDataInternal(key, value, false);
    }

    private Optional<NbtCompound> serializeStateDataCompound() {
        if (stateData.isEmpty()) {
            return Optional.empty();
        }

        NbtCompound serialized = new NbtCompound();
        int storedEntries = 0;

        for (Map.Entry<String, Object> entry : stateData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }

            Optional<NbtCompound> encoded = encodeStateDataValue(value);
            if (encoded.isPresent()) {
                serialized.put(key, encoded.get());
                storedEntries++;
            } else {
                Petsplus.LOGGER.warn("Skipping state data entry '{}' of unsupported type {} during serialization", key, value.getClass().getName());
            }
        }

        if (storedEntries == 0 || serialized.getKeys().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(serialized);
    }

    private Optional<NbtCompound> encodeStateDataValue(Object value) {
        if (value instanceof List<?> listValue) {
            return encodeStateDataList(listValue);
        }

        NbtCompound tag = new NbtCompound();

        if (value instanceof String stringValue) {
            tag.putString("type", "string");
            tag.putString("value", stringValue);
            return Optional.of(tag);
        }

        if (value instanceof Integer intValue) {
            tag.putString("type", "int");
            tag.putInt("value", intValue);
            return Optional.of(tag);
        }

        if (value instanceof Long longValue) {
            tag.putString("type", "long");
            tag.putLong("value", longValue);
            return Optional.of(tag);
        }

        if (value instanceof Boolean boolValue) {
            tag.putString("type", "boolean");
            tag.putBoolean("value", boolValue);
            return Optional.of(tag);
        }

        if (value instanceof Float floatValue) {
            tag.putString("type", "float");
            tag.putFloat("value", floatValue);
            return Optional.of(tag);
        }

        if (value instanceof Double doubleValue) {
            tag.putString("type", "double");
            tag.putDouble("value", doubleValue);
            return Optional.of(tag);
        }

        if (value instanceof Identifier identifierValue) {
            tag.putString("type", "identifier");
            tag.putString("value", identifierValue.toString());
            return Optional.of(tag);
        }

        if (value instanceof UUID uuidValue) {
            tag.putString("type", "uuid");
            tag.putString("value", uuidValue.toString());
            return Optional.of(tag);
        }

        if (value instanceof BlockPos blockPos) {
            tag.putString("type", "block_pos");
            tag.putInt("x", blockPos.getX());
            tag.putInt("y", blockPos.getY());
            tag.putInt("z", blockPos.getZ());
            return Optional.of(tag);
        }

        if (value instanceof Byte byteValue) {
            tag.putString("type", "int");
            tag.putInt("value", byteValue.intValue());
            return Optional.of(tag);
        }

        if (value instanceof Short shortValue) {
            tag.putString("type", "int");
            tag.putInt("value", shortValue.intValue());
            return Optional.of(tag);
        }

        return Optional.empty();
    }

    private Optional<NbtCompound> encodeStateDataList(List<?> listValue) {
        NbtCompound tag = new NbtCompound();
        tag.putString("type", "list");

        int stored = 0;
        for (Object element : listValue) {
            if (element == null) {
                continue;
            }
            Optional<NbtCompound> encodedElement = encodeStateDataValue(element);
            if (encodedElement.isEmpty()) {
                Petsplus.LOGGER.warn("Skipping unsupported list element of type {} while serializing state data", element.getClass().getName());
                continue;
            }
            tag.put("value_" + stored, encodedElement.get());
            stored++;
        }

        tag.putInt("size", stored);
        return Optional.of(tag);
    }

    private void deserializeStateDataCompound(NbtCompound stateNbt) {
        stateData.clear();
        for (String key : stateNbt.getKeys()) {
            stateNbt.getCompound(key).ifPresent(compound -> {
                Optional<String> typeId = compound.getString("type");
                if (typeId.isEmpty()) {
                    Petsplus.LOGGER.warn("Skipping state data entry '{}' missing type information", key);
                    return;
                }

                Optional<Object> decoded = decodeStateDataValue(compound);
                if (decoded.isPresent()) {
                    setStateDataSilently(key, decoded.get());
                } else {
                    Petsplus.LOGGER.warn("Unable to decode state data entry '{}' of type {}", key, typeId.get());
                }
            });
        }
    }

    private Optional<Object> decodeStateDataValue(NbtCompound compound) {
        Optional<String> typeId = compound.getString("type");
        if (typeId.isEmpty()) {
            return Optional.empty();
        }

        String type = typeId.get();
        switch (type) {
            case "string":
                return compound.getString("value").map(value -> value);
            case "int": {
                Optional<Integer> value = compound.getInt("value");
                return value.map(Integer::valueOf);
            }
            case "long": {
                Optional<Long> value = compound.getLong("value");
                return value.map(Long::valueOf);
            }
            case "boolean":
                return compound.getBoolean("value").map(Boolean::valueOf);
            case "float":
                return compound.getFloat("value").map(Float::valueOf);
            case "double": {
                Optional<Double> value = compound.getDouble("value");
                return value.map(Double::valueOf);
            }
            case "identifier":
                return compound.getString("value").map(raw -> {
                    Identifier parsed = Identifier.tryParse(raw);
                    return parsed != null ? parsed : raw;
                });
            case "uuid":
                return compound.getString("value").flatMap(raw -> {
                    try {
                        return Optional.of(UUID.fromString(raw));
                    } catch (IllegalArgumentException ignored) {
                        Petsplus.LOGGER.warn("Invalid UUID '{}' encountered while decoding state data", raw);
                        return Optional.<Object>empty();
                    }
                });
            case "block_pos": {
                Optional<Integer> xOpt = compound.getInt("x");
                Optional<Integer> yOpt = compound.getInt("y");
                Optional<Integer> zOpt = compound.getInt("z");
                if (xOpt.isPresent() && yOpt.isPresent() && zOpt.isPresent()) {
                    return Optional.of(new BlockPos(xOpt.get(), yOpt.get(), zOpt.get()));
                }
                return Optional.empty();
            }
            case "list":
                return decodeStateDataList(compound).map(value -> value);
            default:
                return Optional.empty();
        }
    }

    private Optional<List<Object>> decodeStateDataList(NbtCompound compound) {
        Optional<Integer> sizeOpt = compound.getInt("size");
        int size = sizeOpt.map(value -> Math.max(0, value)).orElse(0);
        if (size == 0) {
            return Optional.of(List.of());
        }

        List<Object> decoded = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String elementKey = "value_" + i;
            compound.getCompound(elementKey).ifPresent(child -> decodeStateDataValue(child).ifPresent(decoded::add));
        }
        return Optional.of(decoded);
    }

    private void setStateDataInternal(String key, Object value, boolean invalidateCaches) {
        stateData.put(key, value);
        if (!invalidateCaches) {
            return;
        }

        if (shouldInvalidateSpeciesDescriptor(key)) {
            refreshSpeciesDescriptor();
        }
        if (shouldInvalidateFlightCapability(key)) {
            speciesMetadataModule.markFlightDirty();
        }
        markContextDirty(ContextSlice.STATE_DATA);
        publishStimulus(PerceptionStimulusType.STATE_DATA, ContextSlice.STATE_DATA, key);
    }

    private boolean shouldInvalidateSpeciesDescriptor(@Nullable String key) {
        if (key == null) {
            return false;
        }

        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.startsWith("context_") || normalized.startsWith("tag_");
    }

    private boolean shouldInvalidateFlightCapability(@Nullable String key) {
        if (key == null) {
            return false;
        }

        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("context_") || normalized.startsWith("tag_")) {
            return true;
        }
        return normalized.contains("flight") || normalized.contains("fly");
    }

    @SuppressWarnings("unchecked")
    public <T> T getStateData(String key, Class<T> type) {
        Object value = stateData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Get typed state data with a default value when missing or wrong type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getStateData(String key, Class<T> type, T defaultValue) {
        Object value = stateData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return defaultValue;
    }

    /**
     * Returns the currently assigned pet nature, if any.
     */
    public @Nullable Identifier getNatureId() {
        Identifier direct = getStateData(StateKeys.ASSIGNED_NATURE, Identifier.class);
        if (direct != null) {
            return direct;
        }

        String stored = getStateData(StateKeys.ASSIGNED_NATURE, String.class);
        if (stored == null || stored.isBlank()) {
            return null;
        }

        return Identifier.tryParse(stored);
    }

    /**
     * Persist a nature identifier for the pet. Passing {@code null} clears the nature.
     */
    public void setNatureId(@Nullable Identifier natureId) {
        Identifier current = getNatureId();
        if (Objects.equals(current, natureId)) {
            return;
        }

        if (natureId == null) {
            clearStateData(StateKeys.ASSIGNED_NATURE);
        } else {
            setStateData(StateKeys.ASSIGNED_NATURE, natureId.toString());
        }

        if (natureId == null || !natureId.equals(AstrologyRegistry.LUNARIS_NATURE_ID)) {
            setAstrologySignId(null);
        }

        if (pet.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld && characteristicsModule.getImprint() != null) {
            woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(this.pet, this);
        }
    }

    public @Nullable Identifier getAstrologySignId() {
        Identifier direct = getStateData(StateKeys.ASTROLOGY_SIGN, Identifier.class);
        if (direct != null) {
            return direct;
        }
        String stored = getStateData(StateKeys.ASTROLOGY_SIGN, String.class);
        if (stored == null || stored.isBlank()) {
            return null;
        }
        return Identifier.tryParse(stored);
    }

    public void setAstrologySignId(@Nullable Identifier signId) {
        Identifier current = getAstrologySignId();
        if (Objects.equals(current, signId)) {
            return;
        }
        if (signId == null) {
            clearStateData(StateKeys.ASTROLOGY_SIGN);
        } else {
            setStateData(StateKeys.ASTROLOGY_SIGN, signId.toString());
        }
    }

    public Identifier computeSpeciesDescriptor() {
        Identifier fromState = resolveDescriptorFromStateData();
        if (fromState != null) {
            return fromState;
        }

        return Registries.ENTITY_TYPE.getId(pet.getType());
    }

    @Nullable
    private Identifier resolveDescriptorFromStateData() {
        for (String key : SPECIES_STATE_KEYS) {
            Identifier storedId = getStateData(key, Identifier.class);
            if (storedId != null) {
                return storedId;
            }

            String value = getStateData(key, String.class);
            Identifier parsed = parseSpeciesIdentifier(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    @Nullable
    private Identifier parseSpeciesIdentifier(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        Identifier parsed = Identifier.tryParse(raw);
        if (parsed != null) {
            return parsed;
        }

        if (!raw.contains(":")) {
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            Identifier fallback = Identifier.of("minecraft", normalized);
            if (Registries.ENTITY_TYPE.containsId(fallback)) {
                return fallback;
            }
        }

        return null;
    }

    /**
     * Retrieve a persistent inventory for this pet, resizing as needed.
     *
     * @param key  unique identifier for the inventory
     * @param size desired size in slots
     * @return backing list representing the inventory contents
     */
    public DefaultedList<ItemStack> getInventory(String key, int size) {
        return inventoryModule.getInventory(key, size);
    }

    @Nullable
    public DefaultedList<ItemStack> getInventoryIfPresent(String key) {
        return inventoryModule.getInventoryIfPresent(key);
    }

    /**
     * Store a persistent inventory backing list for this pet.
     */
    public void setInventory(String key, DefaultedList<ItemStack> inventory) {
        inventoryModule.setInventory(key, inventory);
    }

    /** Convenience accessor for the stored tame tick, writing a default if missing. */
    public long getTamedTick() {
        Long stored = getStateData(StateKeys.TAMED_TICK, Long.class);
        if (stored != null) {
            return stored;
        }
        long now = pet.getEntityWorld().getTime();
        setStateData(StateKeys.TAMED_TICK, now);
        return now;
    }

    public float getBondAgeDays() {
        return getBondAgeDays(pet.getEntityWorld().getTime());
    }

    public float getBondAgeDays(long currentTick) {
        Long tamed = getStateData(StateKeys.TAMED_TICK, Long.class);
        if (tamed == null) {
            setStateData(StateKeys.TAMED_TICK, currentTick);
            return 0f;
        }
        if (currentTick <= tamed) {
            return 0f;
        }
        return (currentTick - tamed) / 24000f;
    }

    public float computeBondResilience() {
        return computeBondResilience(pet.getEntityWorld().getTime());
    }

    public float computeBondResilience(long currentTick) {
        float ageFactor = MathHelper.clamp(getBondAgeDays(currentTick) / 30f, 0f, 1f);

        Integer petCount = getStateData(StateKeys.PET_COUNT, Integer.class);
        float depthFactor = petCount != null
            ? MathHelper.clamp(petCount / 120f, 0f, 1f)
            : 0f;

        Long lastPet = getStateData(StateKeys.LAST_PET_TIME, Long.class);
        float recentCare = 0f;
        if (lastPet != null) {
            if (currentTick <= lastPet) {
                recentCare = 1f;
            } else {
                float daysSincePet = (currentTick - lastPet) / 24000f;
                recentCare = MathHelper.clamp(1f - (daysSincePet / 3f), 0f, 1f);
            }
        }

        float careFactor = (depthFactor * 0.5f) + (recentCare * 0.5f);
        float resilience = 0.25f + ageFactor * 0.4f + careFactor * 0.6f;
        return MathHelper.clamp(resilience, 0.25f, 1.0f);
    }

    public void setNatureEmotionTuning(float volatilityMultiplier, float resilienceMultiplier,
                                       float contagionModifier, float guardModifier) {
        float clampedVolatility = MathHelper.clamp(volatilityMultiplier, 0.3f, 1.75f);
        float clampedResilience = MathHelper.clamp(resilienceMultiplier, 0.5f, 1.5f);
        float clampedContagion = MathHelper.clamp(contagionModifier, 0.5f, 1.5f);
        float clampedGuard = MathHelper.clamp(guardModifier, 0.5f, 1.5f);

        if (characteristicsModule.updateNatureTuning(clampedVolatility, clampedResilience, clampedContagion, clampedGuard)) {
            moodEngine.onNatureTuningChanged();
        }
    }

    public void setNatureEmotionProfile(@Nullable NatureEmotionProfile profile) {
        NatureEmotionProfile sanitized = sanitizeNatureEmotionProfile(profile);
        if (characteristicsModule.setNatureEmotionProfile(sanitized)) {
            moodEngine.onNatureEmotionProfileChanged(sanitized);
        }
    }

    public NatureEmotionProfile getNatureEmotionProfile() {
        return characteristicsModule.getNatureEmotionProfile();
    }

    private NatureEmotionProfile sanitizeNatureEmotionProfile(@Nullable NatureEmotionProfile profile) {
        if (profile == null) {
            return NatureEmotionProfile.EMPTY;
        }

        Emotion major = profile.majorEmotion();
        float majorStrength = clampEmotionStrength(profile.majorStrength());
        if (major == null || majorStrength <= 0f) {
            major = null;
            majorStrength = 0f;
        }

        Emotion minor = profile.minorEmotion();
        float minorStrength = clampEmotionStrength(profile.minorStrength());
        if (minor == null || minorStrength <= 0f) {
            minor = null;
            minorStrength = 0f;
        }

        Emotion quirk = profile.quirkEmotion();
        float quirkStrength = clampEmotionStrength(profile.quirkStrength());
        if (quirk == null || quirkStrength <= 0f) {
            quirk = null;
            quirkStrength = 0f;
        }

        if (major == null && minor == null && quirk == null) {
            return NatureEmotionProfile.EMPTY;
        }

        return new NatureEmotionProfile(major, majorStrength, minor, minorStrength, quirk, quirkStrength);
    }

    private static float clampEmotionStrength(float value) {
        return MathHelper.clamp(value, 0f, 1f);
    }

    public float getNatureVolatilityMultiplier() {
        return characteristicsModule.getNatureVolatility();
    }

    public float getNatureResilienceMultiplier() {
        return characteristicsModule.getNatureResilience();
    }

    public float getNatureContagionModifier() {
        return characteristicsModule.getNatureContagion();
    }

    public float getNatureGuardModifier() {
        return characteristicsModule.getNatureGuardModifier();
    }

    public NatureGuardTelemetry getNatureGuardTelemetry() {
        return moodEngine.getNatureGuardTelemetry();
    }
    
    public long getLastAttackTick() {
        return lastAttackTick;
    }
    
    public void setLastAttackTick(long tick) {
        this.lastAttackTick = tick;
        // Update mood when combat occurs
        updateMood();
    }
    
    public boolean isPerched() {
        return isPerched;
    }
    
    public void setPerched(boolean perched) {
        this.isPerched = perched;
    }
    
    public int getLevel() {
        return progressionModule.getLevel();
    }
    
    public void setLevel(int level) {
        progressionModule.setLevel(level);
    }
    
    public int getExperience() {
        return (int) progressionModule.getExperience();
    }

    public void setExperience(int experience) {
        long sanitized = Math.max(0L, experience);
        int xpForNext = getXpNeededForNextLevel();
        if (xpForNext > 0) {
            sanitized = Math.min(sanitized, xpForNext);
        }
        progressionModule.setExperience(sanitized);
        markEntityDirty();
    }
    
    // ===== Level Reward System =====
    
    /**
     * Unlock an ability for this pet. Called by AbilityUnlockReward.
     */
    public void unlockAbility(Identifier abilityId) {
        if (abilityId == null) {
            Petsplus.LOGGER.warn("Attempted to unlock null ability for pet {}", pet.getUuidAsString());
            return;
        }
        progressionModule.unlockAbility(abilityId);
        markEntityDirty();
        Petsplus.LOGGER.debug("Unlocked ability {} for pet {}", abilityId, pet.getUuidAsString());
    }
    
    /**
     * Check if an ability is unlocked.
     */
    public boolean isAbilityUnlocked(Identifier abilityId) {
        return progressionModule.hasAbility(abilityId);
    }
    
    /**
     * Add a permanent stat boost. Called by StatBoostReward.
     * These boosts stack if the same stat is boosted multiple times.
     */
    public void addPermanentStatBoost(String statName, float amount) {
        if (statName == null || statName.isEmpty()) {
            Petsplus.LOGGER.warn("Attempted to add stat boost with null/empty stat name for pet {}", pet.getUuidAsString());
            return;
        }
        progressionModule.addPermanentStatBoost(statName, amount);
    }
    
    /**
     * Get the total permanent boost for a given stat.
     */
    public float getPermanentStatBoost(String statName) {
        return progressionModule.getPermanentStatBoost(statName);
    }
    
    /**
     * Set a tribute milestone requirement. Called by TributeRequiredReward.
     */
    public void setTributeMilestone(int level, Identifier itemId) {
        if (itemId == null) {
            Petsplus.LOGGER.warn("Attempted to set tribute milestone with null item ID at level {} for pet {}", level, pet.getUuidAsString());
            return;
        }
        progressionModule.setTributeMilestone(level, itemId);
        Petsplus.LOGGER.debug("Set tribute milestone at level {} to {} for pet {}", level, itemId, pet.getUuidAsString());
    }
    
    /**
     * Get the tribute item required for a specific level, if any.
     */
    @Nullable
    public Identifier getTributeMilestone(int level) {
        return progressionModule.getTributeMilestone(level);
    }
    
    /**
     * Check if a level has a tribute requirement.
     */
    public boolean hasTributeMilestone(int level) {
        return progressionModule.hasTributeMilestone(level);
    }
    
    /**
     * Get the pet's unique imprint (individual stat variance).
     */
    @Nullable
    public PetImprint getImprint() {
        return characteristicsModule.getImprint();
    }
    
    /**
     * Set the pet's imprint (usually generated once when first tamed).
     */
    public void setImprint(@Nullable PetImprint imprint) {
        characteristicsModule.setImprint(imprint);
        syncCharacteristicAffinityLookup();
        markEntityDirty();
    }

    private void syncCharacteristicAffinityLookup() {
        // Role affinity bonuses will be handled by RoleScaling system.
        // Placeholder retained for compatibility with existing integration points.
    }


    
    // ===== EMOTION–MOOD SYSTEM (delegated) =====
    public Mood getCurrentMood() { return moodEngine.getCurrentMood(); }
    public int getMoodLevel() { return moodEngine.getMoodLevel(); }
    public void updateMood() {
        long now = pet.getEntityWorld() instanceof ServerWorld sw ? sw.getTime() : System.currentTimeMillis();
        moodEngine.ensureFresh(now);
    }

    public long estimateNextEmotionUpdate(long now) { return moodEngine.estimateNextWakeUp(now); }

    // ===== Emotions API =====

    public record EmotionDelta(Emotion emotion, float amount) {}

    /** Push an emotion with additive weight; creates or refreshes a slot. */
    public void pushEmotion(Emotion emotion, float amount) {
        long now = pet.getEntityWorld() instanceof ServerWorld sw ? sw.getTime() : 0L;
        moodEngine.applyStimulus(new EmotionDelta(emotion, amount), now);
        if (!MoodService.getInstance().isInStimulusDispatch()) {
            EmotionBaselineTracker.recordDirectChange(this);
        }
    }

    /** Apply mirrored pack contagion influence for an emotion. */
    public void addContagionShare(Emotion emotion, float amount) { moodEngine.addContagionShare(emotion, amount); }

    // All slot management lives in PetMoodEngine

    // Mood scoring delegated to PetMoodEngine for emotion-based aggregation
    

    
    /**
     * Get mood display text with symbol and formatting.
     */
    public Text getMoodText() { return moodEngine.getMoodText(); }

    /**
     * Get the current weighted emotion palette driving mood presentation.
     */
    public java.util.List<WeightedEmotionColor> getEmotionPalette() {
        return moodEngine.getCurrentEmotionPalette();
    }

    /**
     * Get the smoothed animation intensity that drives mood breathing speed.
     */
    public float getMoodBreathingIntensity() { return moodEngine.getAnimationIntensity(); }

    /**
     * Get mood display text with debug information showing power level.
     */
    public Text getMoodTextWithDebug() { return moodEngine.getMoodTextWithDebug(); }
    
    /**
     * Get boss bar color based on XP flash and progression.
     */
    public BossBar.Color getMoodBossBarColor() {
        // Priority 1: XP flash (overrides everything)
        if (isXpFlashing()) {
            return BossBar.Color.GREEN; // Light green flash
        }

        // Priority 2: Progression-based color (default)
        return getProgressionBossBarColor();
    }

    /**
     * Check if pet is currently in combat or recently took damage.
     */
    public boolean isInCombat() {
        // Check active combat
        if (pet.getAttacking() != null || pet.getAttacker() != null) {
            return true;
        }

        // Check recent damage (within last 3 seconds)
        long currentTick = pet.getEntityWorld().getTime();
        return (currentTick - lastAttackTick) < 60; // 60 ticks = 3 seconds
    }

    /**
     * Check if XP flash animation is active.
     */
    public boolean isXpFlashing() {
        if (xpFlashStartTick < 0) return false;
        long currentTick = pet.getEntityWorld().getTime();
        return (currentTick - xpFlashStartTick) < XP_FLASH_DURATION;
    }

    /**
     * Get the tick when XP flash started (for UI animation timing).
     */
    public long getXpFlashStartTick() {
        return xpFlashStartTick;
    }

    /**
     * Get BossBar color based on XP progression (black to white gradient concept).
     */
    private BossBar.Color getProgressionBossBarColor() {
        float progress = getXpProgress();

        // Map progress to black->gray->white using available BossBar colors
        if (progress < 0.33f) {
            return BossBar.Color.PURPLE; // Darkest available (representing black)
        } else if (progress < 0.66f) {
            return BossBar.Color.BLUE; // Medium (representing gray)
        } else {
            return BossBar.Color.WHITE; // Lightest (representing white)
        }
    }

    // ===== Blend API =====
    public float getMoodStrength(Mood mood) { return moodEngine.getMoodStrength(mood); }

    public Map<Mood, Float> getMoodBlend() { return moodEngine.getMoodBlend(); }

    public Map<Emotion, Float> getActiveEmotions() {
        if (moodEngine == null) {
            return Collections.emptyMap();
        }
        return moodEngine.getActiveEmotions();
    }

    public boolean hasMoodAbove(Mood mood, float threshold) { return moodEngine.hasMoodAbove(mood, threshold); }

    public Mood getDominantMood() { return moodEngine.getDominantMood(); }
    
    // Helper methods for config access
    // ===== Config helpers for new moods section =====
    // Config helpers and mood calculations are implemented in PetMoodEngine

    /** Expose dominant emotion for API consumers; may return null if none. */
    public @Nullable Emotion getDominantEmotion() { return moodEngine.getDominantEmotion(); }

    /** Debug access to emotion pool for debugging */
    public java.util.List<EmotionDebugInfo> getEmotionPoolDebug() {
        return moodEngine.getEmotionPoolDebug();
    }

    /** Debug record for emotion information */
    public record EmotionDebugInfo(Emotion emotion, float weight, boolean parked) {}

    /** Weighted color stop derived from the live emotion palette. */
    public record WeightedEmotionColor(Emotion emotion, float weight, TextColor color) {}

    /** Quick access to the authored base color for an emotion. */
    public static TextColor getEmotionColor(Emotion emotion) {
        return emotion.baseColor();
    }

    /** Quick access to the authored accent color for an emotion. */
    public static TextColor getEmotionAccentColor(Emotion emotion) {
        return emotion.accentColor();
    }

    /**
     * Sample a palette of weighted emotion colors, returning the interpolated stop at the provided
     * position. The weights are normalized before interpolation so callers can pass raw weights.
     */
    public static TextColor sampleEmotionPalette(List<WeightedEmotionColor> palette, float position,
            TextColor fallback) {
        if (palette == null || palette.isEmpty()) {
            return fallback;
        }
        float total = 0f;
        for (WeightedEmotionColor stop : palette) {
            total += Math.max(0f, stop.weight());
        }
        if (total <= 0f) {
            return fallback;
        }

        position = MathHelper.clamp(position, 0f, 1f);
        float prevEdge = 0f;
        WeightedEmotionColor prevStop = palette.get(0);
        for (int i = 0; i < palette.size(); i++) {
            WeightedEmotionColor stop = palette.get(i);
            float weight = Math.max(0f, stop.weight());
            float normalized = weight / total;
            float nextEdge = Math.min(1f, prevEdge + normalized);
            if (position <= nextEdge || i == palette.size() - 1) {
                if (i == 0) {
                    return stop.color();
                }
                float span = Math.max(1.0e-6f, nextEdge - prevEdge);
                float local = MathHelper.clamp((position - prevEdge) / span, 0f, 1f);
                return interpolateColor(prevStop.color(), stop.color(), local);
            }
            prevEdge = nextEdge;
            prevStop = stop;
        }
        return palette.get(palette.size() - 1).color();
    }

    private static TextColor interpolateColor(TextColor from, TextColor to, float percent) {
        percent = MathHelper.clamp(percent, 0f, 1f);
        int fromRgb = from.getRgb();
        int toRgb = to.getRgb();
        int fromR = (fromRgb >> 16) & 0xFF;
        int fromG = (fromRgb >> 8) & 0xFF;
        int fromB = fromRgb & 0xFF;
        int toR = (toRgb >> 16) & 0xFF;
        int toG = (toRgb >> 8) & 0xFF;
        int toB = toRgb & 0xFF;
        int r = Math.round(fromR + (toR - fromR) * percent);
        int g = Math.round(fromG + (toG - fromG) * percent);
        int b = Math.round(fromB + (toB - fromB) * percent);
        return TextColor.fromRgb((r << 16) | (g << 8) | b);
    }

    // capitalize/prettify handled by engine
    
    /**
     * Generate and set imprint for this pet if it doesn't exist.
     * Should be called when a pet is first tamed.
     */
    public void ensureImprint() {
        if (characteristicsModule.getImprint() == null) {
            long tameTime = pet.getEntityWorld().getTime();
            if (getStateData(StateKeys.TAMED_TICK, Long.class) == null) {
                setStateData(StateKeys.TAMED_TICK, tameTime);
            }
            characteristicsModule.setImprint(PetImprint.generateForNewPet(pet, tameTime));

        // Apply attribute modifiers with the new imprint
        woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(this.pet, this);
        
        markEntityDirty();
        }
    }

    /**
     * Deterministic per-pet seed for lightweight randomization.
     * <p>
     * Systems that need a stable random offset (idle mood refresh, ambient
     * particles, etc.) should lean on this helper so variations align with the
     * pet's imprint rolls. The imprint seed anchors the value
     * once imprint exists; before that we fold in the pet's UUID and
     * stored tame tick so behavior stays deterministic until imprint
     * is generated.
     */
    public long getStablePerPetSeed() {
        PetImprint imprint = characteristicsModule.getImprint();
        if (imprint != null) {
            return imprint.getImprintSeed();
        }

        UUID uuid = pet.getUuid();
        long seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        Long tamedTick = getStateData(StateKeys.TAMED_TICK, Long.class);
        if (tamedTick == null) {
            tamedTick = cacheTamedTickFallback();
        }
        return tamedTick != null ? seed ^ tamedTick : seed;
    }

    /**
     * When characteristics have not yet been generated we may still need a deterministic
     * timestamp to stabilize the fallback seed. Persist one if it is currently absent so
     * subsequent calls (and future loads) remain in lockstep.
     */
    private Long cacheTamedTickFallback() {
        if (!(pet.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }
        long tameTick = serverWorld.getTime();
        setStateData(StateKeys.TAMED_TICK, tameTick);
        return tameTick;
    }

    /**
     * Produce a SplitMix64-style hash tied to this pet's stable seed.
     * <p>
     * The {@code salt} parameter allows independent systems to derive distinct
     * yet deterministic streams (e.g., idle emotion jitter vs. ambient effects)
     * without stepping on each other while still matching the pet's
     * characteristic-driven identity.
     */
    public long mixStableSeed(long salt) {
        return splitMix64(getStablePerPetSeed() ^ salt);
    }

    /**
     * Deterministically pick an index within {@code bound} using the pet's stable seed.
     * <p>
     * This is a convenience wrapper over {@link #mixStableSeed(long)} for systems that
     * only need a bounded selector (e.g., choosing from a short jitter table).
     *
     * @param salt  domain separator so callers can derive independent streams
     * @param bound exclusive upper bound for the returned index; must be positive
     * @return stable index in the range {@code [0, bound)}
     */
    public int pickStableIndex(long salt, int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        long hashed = mixStableSeed(salt);
        return (int) Math.floorMod(hashed, bound);
    }

    private static long splitMix64(long seed) {
        long mixed = seed + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = BehaviorSeedUtil.mixBehaviorSeed(mixed, mixed >>> 24);
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    /**
     * Calculate XP required for a specific level.
     * Feature levels are: 3, 7, 12, 17, 23, 27
     * Uses exponential scaling similar to Minecraft player XP but much more gentle.
     */
    public int getXpRequiredForLevel(int level) {
        if (level <= 1) {
            return 0;
        }

        PetRoleType.XpCurve curve = getRoleType().xpCurve();
        int baseXp = (level - 1) * curve.baseLinearPerLevel()
            + (level - 1) * (level - 1) * curve.quadraticFactor();

        if (curve.isFeatureLevel(level)) {
            baseXp = Math.round(baseXp * curve.featureLevelBonusMultiplier());
        }

        return Math.max(1, baseXp);
    }

    /**
     * Get total XP required from level 1 to reach target level.
     */
    public int getTotalXpForLevel(int level) {
        int total = 0;
        for (int i = 2; i <= level; i++) {
            total += getXpRequiredForLevel(i);
        }
        return total;
    }
    
    /**
     * Add experience to the pet and handle level ups.
     * @param xpGained Amount of XP to add
     * @return true if the pet leveled up
     */
    public boolean addExperience(int xpGained) {
        if (xpGained < 0) return false;

        int oldLevel = progressionModule.getLevel();
        if (pet.getEntityWorld() instanceof ServerWorld serverWorld) {
            progressionModule.addExperience(xpGained, serverWorld, pet.getEntityWorld().getTime());
        }

        // Trigger XP flash animation when new experience is earned
        if (xpGained > 0) {
            this.xpFlashStartTick = pet.getEntityWorld().getTime();
        }
        
        boolean leveledUp = progressionModule.getLevel() > oldLevel;
        
        // Mark dirty on XP gain or level up
        if (xpGained > 0 || leveledUp) {
            markEntityDirty();
        }

        return leveledUp;
    }
    
    /**
     * Get XP progress toward next level as a percentage (0.0 to 1.0).
     */
    public float getXpProgress() {
        int level = progressionModule.getLevel();
        PetRoleType.XpCurve curve = getRoleType().xpCurve();
        if (level >= curve.maxLevel()) {
            return 1.0f;
        }

        int xpForThisLevel = getXpNeededForNextLevel();
        if (xpForThisLevel <= 0) {
            return 0f;
        }

        float remainder = (float) progressionModule.getExperience();
        return MathHelper.clamp(remainder / xpForThisLevel, 0f, 1f);
    }

    public int getXpNeededForNextLevel() {
        int level = progressionModule.getLevel();
        PetRoleType.XpCurve curve = getRoleType().xpCurve();
        if (level >= curve.maxLevel()) {
            return 0;
        }
        return getXpRequiredForLevel(level + 1);
    }
    
    /**
     * Check if pet has reached a feature level (3, 7, 12, 17, 23, 27).
     */
    public boolean isFeatureLevel() {
        return getRoleType().xpCurve().isFeatureLevel(progressionModule.getLevel());
    }
    
    /**
     * Check if pet is at least the required level for an ability.
     */
    public boolean hasLevel(int requiredLevel) {
        return progressionModule.getLevel() >= requiredLevel;
    }

    /**
     * Check if a milestone level has been unlocked with tribute.
     */
    public boolean isMilestoneUnlocked(int milestoneLevel) {
        return progressionModule.hasMilestone(milestoneLevel);
    }

    /**
     * Unlock a milestone level (tribute paid).
     */
    public void unlockMilestone(int milestoneLevel) {
        progressionModule.unlockMilestone(milestoneLevel);
        markEntityDirty();
    }

    /**
     * Check if pet is waiting for tribute at current level.
     */
    public boolean isWaitingForTribute() {
        int level = progressionModule.getLevel();
        PetRoleType.XpCurve curve = getRoleType().xpCurve();
        for (int milestone : curve.tributeMilestones()) {
            if (level == milestone && !isMilestoneUnlocked(milestone)) {
                return true;
            }
        }
        return false;
    }
    
    // ============================================================================
    // RELATIONSHIP SYSTEM - Multi-dimensional bonds with any entity
    // ============================================================================
    
    /**
     * Record an interaction with any living entity.
     * Builds/damages multi-dimensional relationship (trust, affection, respect).
     * 
     * @param entityId UUID of interacting entity
     * @param interactionType type of interaction
     * @param trustMultiplier contextual scaling for trust (species, nature, etc)
     * @param affectionMultiplier contextual scaling for affection
     * @param respectMultiplier contextual scaling for respect
     */
    public void recordEntityInteraction(
        UUID entityId,
        woflo.petsplus.state.relationships.InteractionType interactionType,
        float trustMultiplier,
        float affectionMultiplier,
        float respectMultiplier
    ) {
        if (entityId == null || interactionType == null) {
            return;
        }
        
        long currentTick = pet.getEntityWorld().getTime();
        relationshipModule.recordInteraction(
            entityId,
            interactionType,
            currentTick,
            trustMultiplier,
            affectionMultiplier,
            respectMultiplier
        );
        markEntityDirty();
    }
    
    /**
     * Convenience: record interaction with default multipliers (1.0).
     */
    public void recordEntityInteraction(
        UUID entityId,
        woflo.petsplus.state.relationships.InteractionType interactionType
    ) {
        recordEntityInteraction(entityId, interactionType, 1.0f, 1.0f, 1.0f);
    }
    
    /**
     * Get relationship profile with a specific entity.
     */
    public woflo.petsplus.state.relationships.RelationshipProfile getRelationshipWith(UUID entityId) {
        return relationshipModule.getRelationship(entityId);
    }
    
    /**
     * Get trust level with an entity (-1.0 to 1.0).
     */
    public float getTrustWith(UUID entityId) {
        return relationshipModule.getTrust(entityId);
    }
    
    /**
     * Get affection level with an entity (0.0 to 1.0).
     */
    public float getAffectionWith(UUID entityId) {
        var profile = relationshipModule.getRelationship(entityId);
        return profile != null ? profile.affection() : 0.0f;
    }
    
    /**
     * Get respect level with an entity (0.0 to 1.0).
     */
    public float getRespectWith(UUID entityId) {
        var profile = relationshipModule.getRelationship(entityId);
        return profile != null ? profile.respect() : 0.0f;
    }
    
    /**
     * Get computed comfort level with an entity (0.0 to 1.0).
     */
    public float getComfortWith(UUID entityId) {
        var profile = relationshipModule.getRelationship(entityId);
        return profile != null ? profile.getComfort() : 0.0f;
    }
    
    /**
     * Get relationship type with an entity.
     */
    public woflo.petsplus.state.relationships.RelationshipType getRelationshipType(UUID entityId) {
        return relationshipModule.getRelationshipType(entityId);
    }
    
    /**
     * Get owner relationship (for backwards compatibility).
     * Returns trust level scaled to 0-10000 range like old bondStrength.
     * 
     * @deprecated Use getTrustWith(getOwnerUuid()) for direct trust value
     */
    @Deprecated
    public long getBondStrength() {
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid == null) {
            return 0L;
        }
        
        float trust = getTrustWith(ownerUuid);
        float affection = getAffectionWith(ownerUuid);
        
        // Combine trust and affection for "bond strength" equivalent
        // Scale -1 to 1 trust + 0 to 1 affection to 0-10000 range
        float normalizedTrust = (trust + 1.0f) / 2.0f; // 0.0 to 1.0
        float combined = (normalizedTrust * 0.6f) + (affection * 0.4f); // Weighted
        
        return (long) (combined * 10000f);
    }
    
    /**
     * Add bond strength to owner (for backwards compatibility).
     * Converts to dimensional relationship interactions.
     * 
     * @deprecated Use recordEntityInteraction with appropriate InteractionType
     */
    @Deprecated
    public void addBondStrength(long strengthToAdd) {
        if (strengthToAdd <= 0) return;
        
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid == null) {
            return;
        }
        
        // Convert old bond strength to dimensional increases
        float normalized = strengthToAdd / 10000f;
        
        // Simulate positive interaction
        recordEntityInteraction(
            ownerUuid,
            woflo.petsplus.state.relationships.InteractionType.PETTING,
            normalized * 0.8f,  // trust
            normalized * 1.2f,  // affection
            normalized * 0.5f   // respect
        );
    }
    
    // ============================================================================
    // SPECIES MEMORY - Learned behaviors and preferences toward wild species
    // ============================================================================
    
    /**
     * Record an interaction with a wild animal species.
     * Builds hunting preferences, fears, and caution toward specific species.
     */
    public void recordSpeciesInteraction(
        net.minecraft.entity.EntityType<?> species,
        woflo.petsplus.state.relationships.SpeciesMemory.InteractionContext context
    ) {
        if (species == null || context == null) {
            return;
        }
        
        // Use direct method if available
        if (relationshipModule instanceof woflo.petsplus.state.modules.impl.DefaultRelationshipModule drm) {
            drm.recordSpeciesInteractionDirect(species, context);
        }
    }
    
    /**
     * Get fear level toward a species (0.0 = no fear, 1.0 = terrified).
     */
    public float getSpeciesFear(net.minecraft.entity.EntityType<?> species) {
        if (relationshipModule instanceof woflo.petsplus.state.modules.impl.DefaultRelationshipModule drm) {
            return drm.getSpeciesFearDirect(species);
        }
        return 0.0f;
    }
    
    /**
     * Get hunting preference toward a species (0.0 = neutral, 1.0 = loves hunting).
     */
    public float getSpeciesHuntingPreference(net.minecraft.entity.EntityType<?> species) {
        if (relationshipModule instanceof woflo.petsplus.state.modules.impl.DefaultRelationshipModule drm) {
            return drm.getSpeciesHuntingPreferenceDirect(species);
        }
        return 0.0f;
    }
    
    /**
     * Get caution level toward a species (0.0 = dismissive, 1.0 = very cautious).
     */
    public float getSpeciesCaution(net.minecraft.entity.EntityType<?> species) {
        if (relationshipModule instanceof woflo.petsplus.state.modules.impl.DefaultRelationshipModule drm) {
            return drm.getSpeciesCautionDirect(species);
        }
        return 0.0f;
    }
    
    /**
     * Check if pet has significant memory of a species.
     */
    public boolean hasMemoryOfSpecies(net.minecraft.entity.EntityType<?> species) {
        if (relationshipModule instanceof woflo.petsplus.state.modules.impl.DefaultRelationshipModule drm) {
            return drm.hasMemoryOfSpeciesDirect(species);
        }
        return false;
    }
    
    /**
     * Apply decay to species memories (call periodically, e.g. on tick).
     */
    public void decaySpeciesMemories(long currentTick) {
        relationshipModule.applySpeciesMemoryDecay(currentTick, 0.1f);
    }
    
    /**
     * Adds a history event to the pet's history.
     * Limits history size to prevent memory issues.
     */
    public void addHistoryEvent(HistoryEvent event) {
        historyModule.recordEvent(event);
        markEntityDirty();
    }
    
    /**
     * Gets the pet's complete history.
     */
    public List<HistoryEvent> getHistory() {
        return historyModule.getEvents();
    }
    
    /**
     * Gets history events for a specific owner.
     */
    public List<HistoryEvent> getHistoryForOwner(UUID ownerUuid) {
        return historyModule.getEventsForOwner(ownerUuid);
    }
    
    /**
     * Gets history events of a specific type.
     * @deprecated Use historyModule.getEvents() with filtering
     */
    @Deprecated
    public List<HistoryEvent> getHistoryByType(String eventType) {
        if (eventType == null) {
            return new ArrayList<>();
        }
        return historyModule.getEvents().stream()
            .filter(event -> event.isType(eventType))
            .collect(Collectors.toList());
    }
    
    /**
     * Counts events of a specific type for a specific owner.
     * @deprecated Use historyModule.countEvents()
     */
    @Deprecated
    public int getEventCount(String eventType, UUID ownerUuid) {
        return (int) historyModule.countEvents(eventType, ownerUuid);
    }
    
    /**
     * Counts events of a specific type across all owners.
     * @deprecated Use historyModule.countEvents()
     */
    @Deprecated
    public int getEventCount(String eventType) {
        return (int) historyModule.countEvents(eventType, null);
    }
    
    /**
     * Counts how many times a specific achievement was earned with a specific owner.
     * @deprecated - kept for external callers, use historyModule directly
     */
    @Deprecated
    public long getAchievementCount(String achievementType, UUID ownerUuid) {
        if (achievementType == null) {
            return 0;
        }
        return historyModule.getEvents().stream()
            .filter(event -> event.isType(HistoryEvent.EventType.ACHIEVEMENT))
            .filter(event -> ownerUuid == null || event.isWithOwner(ownerUuid))
            .filter(event -> {
                String data = event.eventData();
                return data != null && data.contains("\"achievement_type\":\"" + achievementType + "\"");
            })
            .count();
    }
    
    /**
     * Gets the total damage redirected by a Guardian pet for a specific owner.
     * @deprecated - kept for external callers, use historyModule directly
     */
    @Deprecated
    public double getTotalGuardianDamageForOwner(UUID ownerUuid) {
        if (ownerUuid == null) {
            return 0.0;
        }
        return historyModule.getEvents().stream()
            .filter(event -> event.isType(HistoryEvent.EventType.ACHIEVEMENT))
            .filter(event -> event.isWithOwner(ownerUuid))
            .filter(event -> {
                String data = event.eventData();
                return data != null && data.contains("\"achievement_type\":\"" + HistoryEvent.AchievementType.GUARDIAN_PROTECTION + "\"");
            })
            .mapToDouble(event -> {
                try {
                    String data = event.eventData();
                    int damageIdx = data.indexOf("\"damage\":");
                    if (damageIdx != -1) {
                        int start = damageIdx + 9;
                        int end = data.indexOf(',', start);
                        if (end == -1) end = data.indexOf('}', start);
                        String damageStr = data.substring(start, end).trim();
                        return Double.parseDouble(damageStr);
                    }
                } catch (Exception e) {
                    // Ignore
                }
                return 0.0;
            })
            .sum();
    }
    
    /**
     * Gets the set of unique allies healed on a specific day by a Support pet.
     * @deprecated - kept for external callers, use historyModule directly
     */
    @Deprecated
    public java.util.Set<UUID> getUniqueAlliesHealedOnDay(UUID ownerUuid, long day) {
        if (ownerUuid == null) {
            return new java.util.HashSet<>();
        }
        return historyModule.getEvents().stream()
            .filter(event -> event.isType(HistoryEvent.EventType.ACHIEVEMENT))
            .filter(event -> event.isWithOwner(ownerUuid))
            .filter(event -> {
                String data = event.eventData();
                if (data == null || !data.contains("\"achievement_type\":\"" + HistoryEvent.AchievementType.ALLY_HEALED + "\"")) {
                    return false;
                }
                int dayIdx = data.indexOf("\"day\":");
                if (dayIdx != -1) {
                    try {
                        int start = dayIdx + 6;
                        int end = data.indexOf('}', start);
                        String dayStr = data.substring(start, end).trim();
                        long eventDay = Long.parseLong(dayStr);
                        return eventDay == day;
                    } catch (Exception e) {
                        return false;
                    }
                }
                return false;
            })
            .map(event -> {
                try {
                    String data = event.eventData();
                    int allyIdx = data.indexOf("\"ally\":\"");
                    if (allyIdx != -1) {
                        int start = allyIdx + 8;
                        int end = data.indexOf('"', start);
                        String allyUuidStr = data.substring(start, end);
                        return UUID.fromString(allyUuidStr);
                    }
                } catch (Exception e) {
                    // Ignore
                }
                return null;
            })
            .filter(uuid -> uuid != null)
            .collect(Collectors.toSet());
    }
    
    /**
     * Gets XP needed for next level.
     * @deprecated Use progressionModule.getXpForNextLevel()
     */
    @Deprecated
    public int getXpForNextLevel() {
        return (int) progressionModule.getXpForNextLevel();
    }
    
    /**
     * Called when owner changes - notifies BestFriendTracker.
     * @deprecated Internal callback, should not be called externally
     */
    @Deprecated
    public void onOwnerChanged(UUID previousOwnerUuid, UUID newOwnerUuid) {
        // Clear previous best friend tracking
        if (previousOwnerUuid != null && pet.getEntityWorld() instanceof ServerWorld serverWorld) {
            BestFriendTracker tracker = BestFriendTracker.get(serverWorld);
            tracker.clearIfBestFriend(previousOwnerUuid, pet.getUuid());
        }
    }
    
    /**
     * Resets all pet abilities and return to base state.
     * Used by respec tokens to allow re-allocation of progression.
     */
    public void resetAbilities() {
        // Reset level to 1 while preserving experience tracking structure
        progressionModule.setLevel(1);
        progressionModule.setExperience(0);
        progressionModule.clearProgressionUnlocks();

        // Milestone persistence allows pets to retain achievement history

        // Reset state data related to abilities but preserve bond strength and metadata
        Map<String, Object> preservedData = new HashMap<>();
        preservedData.put("bondStrength", getStateData("bondStrength", Long.class, 0L));
        preservedData.put("isSpecial", getStateData("isSpecial", Boolean.class, false));
        Long tamedTick = getStateData(StateKeys.TAMED_TICK, Long.class);
        if (tamedTick != null) {
            preservedData.put(StateKeys.TAMED_TICK, tamedTick);
        }
        Long lastPet = getStateData(StateKeys.LAST_PET_TIME, Long.class);
        if (lastPet != null) {
            preservedData.put(StateKeys.LAST_PET_TIME, lastPet);
        }
        Integer petCount = getStateData(StateKeys.PET_COUNT, Integer.class);
        if (petCount != null) {
            preservedData.put(StateKeys.PET_COUNT, petCount);
        }
        
        // Preserve custom tags
        for (String key : stateData.keySet()) {
            if (key.startsWith("tag_")) {
                preservedData.put(key, stateData.get(key));
            }
        }
        
        // Clear all state data and restore preserved items
        stateData.clear();
        preservedData.forEach((key, value) -> setStateDataInternal(key, value, false));
        refreshSpeciesDescriptor();
        
        // Reset attributes to base values
        woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(this.pet, this);
    }
    
    /**
     * Serialize pet data to component storage for persistence.
     */
    public PetsplusComponents.PetData toComponentData() {
        PetsplusComponents.PetData data = PetsplusComponents.PetData.empty()
            .withLastAttackTick(lastAttackTick)
            .withPerched(isPerched)
            .withXpFlashStartTick(xpFlashStartTick)
            .withRole(getRoleId());

        Map<String, Long> cooldowns = schedulingModule.getAllCooldowns();
        if (cooldowns != null && !cooldowns.isEmpty()) {
            for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
                data = data.withCooldown(entry.getKey(), entry.getValue());
            }
        }

        ProgressionModule.Data progressionData = progressionModule.toData();
        if (progressionData != null) {
            data = data.withProgression(progressionData);
        }

        HistoryModule.Data historyData = historyModule.toData();
        if (historyData != null) {
            data = data.withHistory(historyData);
        }

        InventoryModule.Data inventoryData = inventoryModule.toData();
        if (inventoryData != null) {
            data = data.withInventories(inventoryData);
        }

        OwnerModule.Data ownerData = ownerModule.toData();
        if (ownerData != null) {
            data = data.withOwner(ownerData);
        }

        RelationshipModule.Data relationshipData = relationshipModule.toData();
        if (relationshipData != null && !isRelationshipDataEmpty(relationshipData)) {
            data = data.withRelationships(relationshipData);
        }

        SchedulingModule.Data schedulingData = schedulingModule.toData();
        if (schedulingData != null) {
            data = data.withScheduling(schedulingData);
        }

        CharacteristicsModule.Data characteristicsData = characteristicsModule.toData();
        if (characteristicsData != null && !isCharacteristicsDataEmpty(characteristicsData)) {
            data = data.withCharacteristics(characteristicsData);
        }

        if (!gossipLedger.isEmpty()) {
            data = data.withGossip(gossipLedger);
        }

        NbtCompound moodNbt = new NbtCompound();
        moodEngine.writeToNbt(moodNbt);
        data = data.withMood(moodNbt);

        Optional<NbtCompound> stateDataNbt = serializeStateDataCompound();
        if (stateDataNbt.isPresent()) {
            data = data.withStateData(stateDataNbt.get().copy());
        }

        data = data.withSchemaVersion(PetsplusComponents.PetData.CURRENT_SCHEMA_VERSION);

        return data;
    }

    /**
     * Deserialize pet data from component storage after loading.
     */
    public void fromComponentData(PetsplusComponents.PetData data) {
        data.progression().ifPresentOrElse(
            progressionModule::fromData,
            this::resetProgressionModule
        );
        data.history().ifPresentOrElse(
            historyModule::fromData,
            () -> historyModule.fromData(null)
        );
        data.inventories().ifPresentOrElse(
            inventoryModule::fromData,
            () -> inventoryModule.fromData(null)
        );
        data.owner().ifPresentOrElse(
            ownerModule::fromData,
            this::clearOwnerModule
        );

        data.relationships().ifPresentOrElse(
            relationshipModule::fromData,
            () -> relationshipModule.fromData(emptyRelationshipData())
        );

        data.scheduling().ifPresentOrElse(
            schedulingModule::fromData,
            () -> {
                schedulingModule.reset();
                clearAllCooldowns();
            }
        );
        replaceSchedulingCooldowns(data.cooldowns());

        if (data.characteristics().isPresent()) {
            characteristicsModule.fromData(data.characteristics().get());
            moodEngine.onNatureTuningChanged();
            moodEngine.onNatureEmotionProfileChanged(characteristicsModule.getNatureEmotionProfile());
        } else {
            resetCharacteristicsModule();
            moodEngine.onNatureTuningChanged();
            moodEngine.onNatureEmotionProfileChanged(characteristicsModule.getNatureEmotionProfile());
        }

        data.stateData().ifPresentOrElse(
            stateNbt -> deserializeStateDataCompound(stateNbt.copy()),
            this::clearAllStateData
        );
        refreshSpeciesDescriptor();

        data.gossip().ifPresentOrElse(gossipLedger::copyFrom, gossipLedger::clear);

        data.mood().ifPresentOrElse(
            moodData -> moodEngine.readFromNbt(moodData.copy()),
            () -> moodEngine.readFromNbt(new NbtCompound())
        );

        this.isPerched = data.isPerched();
        this.lastAttackTick = data.lastAttackTick();
        this.xpFlashStartTick = data.xpFlashStartTick();
        data.role().ifPresent(this::setRoleId);

        syncCharacteristicAffinityLookup();
    }

    private void resetProgressionModule() {
        progressionModule.setLevel(1);
        progressionModule.setExperience(0);
        progressionModule.clearProgressionUnlocks();
    }

    private void clearOwnerModule() {
        ownerModule.clearCrouchCuddle();
        ownerModule.setOwnerUuid(null);
    }

    private void resetCharacteristicsModule() {
        characteristicsModule.setImprint(null);
        characteristicsModule.updateNatureTuning(1.0f, 1.0f, 1.0f, 1.0f);
        characteristicsModule.setNatureEmotionProfile(NatureEmotionProfile.EMPTY);
        characteristicsModule.setNameAttributes(List.of());
        characteristicsModule.resetRoleAffinityBonuses();
    }

    private void clearAllCooldowns() {
        Map<String, Long> existing = schedulingModule.getAllCooldowns();
        if (existing.isEmpty()) {
            return;
        }
        for (String key : existing.keySet()) {
            schedulingModule.clearCooldown(key);
        }
    }

    private void replaceSchedulingCooldowns(@Nullable Map<String, Long> newCooldowns) {
        clearAllCooldowns();
        if (newCooldowns == null || newCooldowns.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Long> entry : newCooldowns.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            schedulingModule.setCooldown(entry.getKey(), entry.getValue());
        }
    }

    private void clearAllStateData() {
        if (stateData.isEmpty()) {
            return;
        }
        stateData.clear();
        speciesMetadataModule.markFlightDirty();
    }

    private static RelationshipModule.Data emptyRelationshipData() {
        return new RelationshipModule.Data(
            List.of(),
            0L,
            new SpeciesMemory.Data(List.of(), 0L)
        );
    }

    private static boolean isRelationshipDataEmpty(@Nullable RelationshipModule.Data data) {
        if (data == null) {
            return true;
        }
        boolean hasRelationships = data.relationships() != null && !data.relationships().isEmpty();
        var speciesMemory = data.speciesMemory();
        boolean hasSpeciesMemory = speciesMemory != null
            && speciesMemory.memories() != null
            && !speciesMemory.memories().isEmpty();
        boolean hasDecayState = data.lastDecayTick() > 0L;
        return !hasRelationships && !hasSpeciesMemory && !hasDecayState;
    }

    /**
     * Save component data to entity using component system.
     */
    public void saveToEntity() {
        PetsplusComponents.PetData data = toComponentData();
        try {
            pet.setComponent(PetsplusComponents.PET_DATA, data);
        } catch (Throwable error) {
            Petsplus.LOGGER.warn("Failed to save pet component data for " + pet.getUuidAsString(), error);
        }
    }

    /**
     * Loads component data from entity using the component system.
     */
    public void loadFromEntity() {
        try {
            if (!(pet instanceof EntityComponentAccessor accessor)) {
                Petsplus.LOGGER.warn("Entity " + pet.getUuidAsString() + " is missing component accessor mixin");
                return;
            }
            PetsplusComponents.PetData stored = null;
            try {
                stored = accessor.petsplus$castComponentValue(PetsplusComponents.PET_DATA, null);
            } catch (ClassCastException castError) {
                Petsplus.LOGGER.warn("Failed to cast pet component data for " + pet.getUuidAsString(), castError);
            }
            if (stored != null) {
                fromComponentData(stored);
            }
        } catch (Throwable error) {
            Petsplus.LOGGER.warn("Failed to load pet component data for " + pet.getUuidAsString(), error);
        }
    }
    
    /**
     * Marks the entity's chunk as dirty, signaling Minecraft to prioritize
     * saving this chunk during the next autosave cycle. This ensures critical
     * pet data (level, XP, inventory, owner, etc.) is persisted quickly after
     * important state changes, minimizing potential data loss from crashes.
     * 
     * Note: In Minecraft 1.21+, chunk marking is handled automatically by
     * the component system, so this is now a no-op.
     */
    private void markEntityDirty() {
        // Chunk dirty marking is handled automatically by the component system
    }
}

