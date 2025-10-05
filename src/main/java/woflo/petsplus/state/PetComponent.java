package woflo.petsplus.state;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.player.PlayerEntity;
import com.mojang.serialization.DataResult;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.advancement.BestFriendTracker;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.component.PetsplusComponents;
import woflo.petsplus.mood.EmotionBaselineTracker;
import woflo.petsplus.mood.MoodService;
import woflo.petsplus.stats.PetCharacteristics;
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
import woflo.petsplus.util.BehaviorSeedUtil;

import net.minecraft.util.math.ChunkSectionPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import java.util.stream.Collectors;

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

    private StateManager stateManager;

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
        GLEE(0xFFF275),
        BLISSFUL(0xFFB3C6),
        UBUNTU(0xFF9E40),
        KEFI(0xFF5DA2),
        ANGST(0x4A2C8C),
        FOREBODING(0x1F3A5F),
        PROTECTIVENESS(0x3D6FA6),
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
        EMPATHY(0xC38DFF),
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
        public static final String SOCIAL_JITTER_SEED = "social_jitter_seed";
        public static final String GOSSIP_OPT_OUT_UNTIL = "gossip_opt_out_until";
        public static final String GOSSIP_CLUSTER_CURSOR = "gossip_cluster_cursor";
        public static final String THREAT_LAST_TICK = "threat_last_tick";
        public static final String THREAT_SAFE_STREAK = "threat_safe_streak";
        public static final String THREAT_SENSITIZED_STREAK = "threat_sensitized_streak";
        public static final String THREAT_LAST_DANGER = "threat_last_danger";
        public static final String THREAT_LAST_RECOVERY_TICK = "threat_last_recovery_tick";
        public static final String HEALTH_LAST_LOW_TICK = "health_last_low_tick";
        public static final String HEALTH_RECOVERY_COOLDOWN = "health_recovery_cooldown";
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

        private StateKeys() {}
    }

    public PetComponent(MobEntity pet) {
        this.pet = pet;
        this.roleId = DEFAULT_ROLE_ID;
        this.stateData = new HashMap<>();
        this.lastAttackTick = 0;
        this.isPerched = false;

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
        
        // Attach modules
        this.inventoryModule.onAttach(this);
        this.characteristicsModule.onAttach(this);
        this.historyModule.onAttach(this);
        this.progressionModule.onAttach(this);
        this.schedulingModule.onAttach(this);
        this.ownerModule.onAttach(this);
        this.speciesMetadataModule.onAttach(this);
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

    public PetGossipLedger getGossipLedger() {
        return gossipLedger;
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
        if (pet == null || pet.getWorld() == null || pet.getWorld().isClient()) {
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
        if (pet.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            return StateManager.forWorld(serverWorld).getPetComponent(pet);
        }
        return COMPONENTS.computeIfAbsent(pet, PetComponent::new);
    }
    
    @Nullable
    public static PetComponent get(MobEntity pet) {
        if (pet == null) {
            return null;
        }
        if (pet.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            StateManager manager = StateManager.forWorld(serverWorld);
            return manager.getAllPetComponents().get(pet);
        }
        return COMPONENTS.get(pet);
    }
    
    public static void set(MobEntity pet, PetComponent component) {
        COMPONENTS.put(pet, component);
    }
    
    public static void remove(MobEntity pet) {
        if (pet.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
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
            keys = PetCharacteristics.statKeyArray();
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

    private void syncCharacteristicAffinityLookup() {
        PetCharacteristics characteristics = characteristicsModule.getCharacteristics();
        if (characteristics != null) {
            characteristics.setRoleAffinityLookup(characteristicsModule::resolveRoleAffinityBonus);
        }
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
        woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(this.pet, this);

        // Apply AI enhancements when role changes
        if (!this.pet.getWorld().isClient) {
            woflo.petsplus.ai.PetAIEnhancements.enhancePetAI(this.pet, this);
        }

        if (this.pet.getWorld() instanceof ServerWorld serverWorld) {
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
        Registry<PetRoleType> registry = PetsPlusRegistries.petRoleTypeRegistry();
        PetRoleType type = registry.get(getRoleId());
        if (type != null) {
            return registry.getEntry(type);
        }

        PetRoleType fallback = registry.get(DEFAULT_ROLE_ID);
        return fallback != null ? registry.getEntry(fallback) : null;
    }

    public PetRoleType getRoleType() {
        return getRoleType(true);
    }

    public PetRoleType getRoleType(boolean logMissing) {
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
    }

    @Nullable
    public PlayerEntity getOwner() {
        if (!(pet.getWorld() instanceof ServerWorld world)) return null;
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
        if (!(pet.getWorld() instanceof ServerWorld) || stateManager == null) {
            return;
        }
        schedulingModule.markInitialized();
        scheduleNextIntervalTick(currentTick);
        scheduleNextAuraCheck(currentTick);
        scheduleNextSupportPotionScan(currentTick);
        scheduleNextParticleCheck(currentTick);
        scheduleNextGossipDecay(currentTick + Math.max(MIN_GOSSIP_DECAY_DELAY, gossipLedger.scheduleNextDecayDelay()));
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

    private void submitScheduledTask(PetWorkScheduler.TaskType type, long nextTick) {
        if (!(pet.getWorld() instanceof ServerWorld) || stateManager == null) {
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
        if (!(pet.getWorld() instanceof ServerWorld)) {
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
        return schedulingModule.isOnCooldown(key, pet.getWorld().getTime());
    }

    public void setCooldown(String key, int ticks) {
        schedulingModule.setCooldown(key, pet.getWorld().getTime() + ticks);
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
        return schedulingModule.getAllCooldowns();
    }

    /**
     * Updates cooldowns managed by the scheduling module.
     * Particle effects are triggered by game systems when cooldowns expire.
     */
    public void updateCooldowns() {
        // SchedulingModule manages cooldown state and expiration
    }

    public void applyCooldownExpirations(List<String> keys, long currentTick) {
        if (!(pet.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
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
        return Math.max(0, cooldownEnd - pet.getWorld().getTime());
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
        if (pet.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            setStateData(StateKeys.LAST_CROUCH_CUDDLE_TICK, serverWorld.getTime());
        }
    }

    public void refreshCrouchCuddle(UUID ownerId, long expiryTick) {
        ownerModule.recordCrouchCuddle(ownerId, expiryTick);
        if (pet.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
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
    }

    private void setStateDataSilently(String key, Object value) {
        setStateDataInternal(key, value, false);
    }

    private void migrateLegacyStateData() {
        migrateLegacyDevCrownFlag();
    }

    private void migrateLegacyDevCrownFlag() {
        if (Boolean.TRUE.equals(getStateData("special_tag_creator", Boolean.class, null))) {
            return;
        }

        boolean promote = false;
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Object> entry : new ArrayList<>(stateData.entrySet())) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }

            String normalized = key.toLowerCase(Locale.ROOT);
            if (!normalized.contains("dev_crown")) {
                continue;
            }

            if (!toRemove.contains(key)) {
                toRemove.add(key);
            }

            if (promote) {
                continue;
            }

            Object value = entry.getValue();
            if (value instanceof Boolean boolValue) {
                promote = boolValue;
            } else if (value instanceof Number numberValue) {
                promote = Math.abs(numberValue.doubleValue()) > 1.0e-6;
            } else if (value instanceof String stringValue) {
                promote = parseLegacyBoolean(stringValue);
            }
        }

        for (String legacyKey : toRemove) {
            stateData.remove(legacyKey);
        }

        if (promote) {
            setStateDataSilently("special_tag_creator", true);
        }
    }

    private static boolean parseLegacyBoolean(String value) {
        if (value == null) {
            return false;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }

        return normalized.equals("true")
            || normalized.equals("yes")
            || normalized.equals("on")
            || normalized.equals("1");
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

        if (pet.getWorld() instanceof net.minecraft.server.world.ServerWorld && characteristicsModule.getCharacteristics() != null) {
            woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(this.pet, this);
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

    private RegistryOps<NbtElement> getRegistryOps() {
        if (pet.getWorld() instanceof ServerWorld serverWorld) {
            return RegistryOps.of(NbtOps.INSTANCE, serverWorld.getRegistryManager());
        }
        return RegistryOps.of(NbtOps.INSTANCE, DynamicRegistryManager.EMPTY);
    }

    /** Convenience accessor for the stored tame tick, writing a default if missing. */
    public long getTamedTick() {
        Long stored = getStateData(StateKeys.TAMED_TICK, Long.class);
        if (stored != null) {
            return stored;
        }
        long now = pet.getWorld().getTime();
        setStateData(StateKeys.TAMED_TICK, now);
        return now;
    }

    public float getBondAgeDays() {
        return getBondAgeDays(pet.getWorld().getTime());
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
        return computeBondResilience(pet.getWorld().getTime());
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

    private static Optional<Emotion> parseEmotionOptional(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Emotion.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
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
        if (pet.getWorld() instanceof ServerWorld world) {
            progressionModule.addExperience(experience - progressionModule.getExperience(), world, world.getTime());
        }
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
     * Get the pet's unique characteristics.
     */
    @Nullable
    public PetCharacteristics getCharacteristics() {
        return characteristicsModule.getCharacteristics();
    }
    
    /**
     * Set the pet's characteristics (usually generated once when first tamed).
     */
    public void setCharacteristics(@Nullable PetCharacteristics characteristics) {
        characteristicsModule.setCharacteristics(characteristics);
        syncCharacteristicAffinityLookup();
    }
    

    
    // ===== EMOTION–MOOD SYSTEM (delegated) =====
    public Mood getCurrentMood() { return moodEngine.getCurrentMood(); }
    public int getMoodLevel() { return moodEngine.getMoodLevel(); }
    public void updateMood() {
        long now = pet.getWorld() instanceof ServerWorld sw ? sw.getTime() : System.currentTimeMillis();
        moodEngine.ensureFresh(now);
    }

    public long estimateNextEmotionUpdate(long now) { return moodEngine.estimateNextWakeUp(now); }

    // ===== Emotions API =====

    public record EmotionDelta(Emotion emotion, float amount) {}

    /** Push an emotion with additive weight; creates or refreshes a slot. */
    public void pushEmotion(Emotion emotion, float amount) {
        long now = pet.getWorld() instanceof ServerWorld sw ? sw.getTime() : 0L;
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
        long currentTick = pet.getWorld().getTime();
        return (currentTick - lastAttackTick) < 60; // 60 ticks = 3 seconds
    }

    /**
     * Check if XP flash animation is active.
     */
    public boolean isXpFlashing() {
        if (xpFlashStartTick < 0) return false;
        long currentTick = pet.getWorld().getTime();
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
     * Generate and set characteristics for this pet if they don't exist.
     * Should be called when a pet is first tamed.
     */
    public void ensureCharacteristics() {
        if (characteristicsModule.getCharacteristics() == null) {
            long tameTime = pet.getWorld().getTime();
            if (getStateData(StateKeys.TAMED_TICK, Long.class) == null) {
                setStateData(StateKeys.TAMED_TICK, tameTime);
            }
            characteristicsModule.setCharacteristics(PetCharacteristics.generateForNewPet(pet, tameTime));

        // Apply attribute modifiers with the new characteristics
        woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(this.pet, this);
        }
    }

    /**
     * Deterministic per-pet seed for lightweight randomization.
     * <p>
     * Systems that need a stable random offset (idle mood refresh, ambient
     * particles, etc.) should lean on this helper so variations align with the
     * pet's characteristic rolls. The characteristic seed anchors the value
     * once characteristics exist; before that we fold in the pet's UUID and
     * stored tame tick so behavior stays deterministic until characteristics
     * are generated.
     */
    public long getStablePerPetSeed() {
        PetCharacteristics characteristics = characteristicsModule.getCharacteristics();
        if (characteristics != null) {
            return characteristics.getCharacteristicSeed();
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
        if (!(pet.getWorld() instanceof ServerWorld serverWorld)) {
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
        if (xpGained <= 0) return false;

        int oldLevel = progressionModule.getLevel();
        if (pet.getWorld() instanceof ServerWorld serverWorld) {
            progressionModule.addExperience(xpGained, serverWorld, pet.getWorld().getTime());
        }

        // Trigger XP flash animation
        this.xpFlashStartTick = pet.getWorld().getTime();

        return progressionModule.getLevel() > oldLevel;
    }
    
    /**
     * Get XP progress toward next level as a percentage (0.0 to 1.0).
     */
    public float getXpProgress() {
        int level = progressionModule.getLevel();
        long experience = progressionModule.getExperience();
        PetRoleType.XpCurve curve = getRoleType().xpCurve();
        if (level >= curve.maxLevel()) return 1.0f;

        int currentLevelTotalXp = getTotalXpForLevel(level);
        int nextLevelTotalXp = getTotalXpForLevel(level + 1);
        int xpForThisLevel = Math.max(1, nextLevelTotalXp - currentLevelTotalXp);
        int currentXpInLevel = (int) (experience - currentLevelTotalXp);

        return Math.max(0f, Math.min(1f, (float)currentXpInLevel / xpForThisLevel));
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
    
    /**
     * Set pet metadata from an item component (for metadata tag items).
     * This updates the pet's display name, bond strength, and custom tags.
     */
    public void setPetMetadata(woflo.petsplus.component.PetsplusComponents.PetMetadata metadata) {
        if (metadata == null) return;
        
        // Update display name if provided
        if (metadata.customDisplayName().isPresent()) {
            String customName = metadata.customDisplayName().get();
            pet.setCustomName(net.minecraft.text.Text.literal(customName));
            pet.setCustomNameVisible(true);
        }
        
        // Add bond strength
        addBondStrength(metadata.bondStrength());
        
        // Store custom tags in state data
        for (Map.Entry<String, String> tag : metadata.customTags().entrySet()) {
            setStateData("tag_" + tag.getKey(), tag.getValue());
        }
        
        // Mark as special if indicated
        if (metadata.isSpecial()) {
            setStateData("isSpecial", true);
        }

        refreshSpeciesDescriptor();
    }
    
    /**
     * Add bond strength to the pet, which can provide stat bonuses.
     * Bond strength is stored as state data and influences combat effectiveness.
     */
    public void addBondStrength(long strengthToAdd) {
        if (strengthToAdd <= 0) return;
        
        long currentBond = getStateData("bondStrength", Long.class, 0L);
        long newBond = Math.min(10000L, currentBond + strengthToAdd); // Cap at 10,000
        setStateData("bondStrength", newBond);
        
        // Apply attribute bonuses based on bond strength
        // Every 1000 bond strength = +5% health and damage
        if (newBond >= 1000 && currentBond < 1000) {
            woflo.petsplus.stats.PetAttributeManager.applyAttributeModifiers(this.pet, this);
        }
    }
    
    /**
     * Get current bond strength level.
     */
    public long getBondStrength() {
        return getStateData("bondStrength", Long.class, 0L);
    }
    
    /**
     * Adds a history event to the pet's history.
     * Limits history size to prevent memory issues.
     */
    public void addHistoryEvent(HistoryEvent event) {
        historyModule.recordEvent(event);
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
        if (previousOwnerUuid != null && pet.getWorld() instanceof ServerWorld serverWorld) {
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
        progressionModule.addExperience(-progressionModule.getExperience(), 
            (ServerWorld) pet.getWorld(), pet.getWorld().getTime());
        
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
    
    // NEW: Save to codec-backed component (Phase 3)
    public PetsplusComponents.PetData toComponentData() {
        return PetsplusComponents.PetData.empty()
            .withRole(getRoleId())
            .withProgression(progressionModule.toData())
            .withHistory(historyModule.toData())
            .withInventories(inventoryModule.toData())
            .withOwner(ownerModule.toData())
            .withScheduling(schedulingModule.toData())
            .withPerched(isPerched)
            .withLastAttackTick(lastAttackTick);
    }
    
    // NEW: Load from codec-backed component (Phase 3)
    public void fromComponentData(PetsplusComponents.PetData data) {
        data.progression().ifPresent(progressionModule::fromData);
        data.history().ifPresent(historyModule::fromData);
        data.inventories().ifPresent(inventoryModule::fromData);
        data.owner().ifPresent(ownerModule::fromData);
        data.scheduling().ifPresent(schedulingModule::fromData);
        this.isPerched = data.isPerched();
        this.lastAttackTick = data.lastAttackTick();
        data.role().ifPresent(this::setRoleId);
    }
    
    public void writeToNbt(NbtCompound nbt) {
        nbt.putString("role", getRoleId().toString());
        nbt.putString("petUuid", pet.getUuidAsString());
        nbt.putLong("lastAttackTick", lastAttackTick);
        nbt.putBoolean("isPerched", isPerched);
        nbt.putInt("level", progressionModule.getLevel());
        nbt.putLong("experience", progressionModule.getExperience());
        nbt.putLong("xpFlashStartTick", xpFlashStartTick);

        NbtCompound tuningNbt = new NbtCompound();
        tuningNbt.putFloat("volatility", characteristicsModule.getNatureVolatility());
        tuningNbt.putFloat("resilience", characteristicsModule.getNatureResilience());
        tuningNbt.putFloat("contagion", characteristicsModule.getNatureContagion());
        tuningNbt.putFloat("guard", characteristicsModule.getNatureGuardModifier());
        nbt.put("natureTuning", tuningNbt);

        NatureEmotionProfile profile = characteristicsModule.getNatureEmotionProfile();
        if (!profile.isEmpty()) {
            NbtCompound emotionNbt = new NbtCompound();
            if (profile.majorEmotion() != null) {
                emotionNbt.putString("major", profile.majorEmotion().name());
                emotionNbt.putFloat("majorStrength", profile.majorStrength());
            }
            if (profile.minorEmotion() != null) {
                emotionNbt.putString("minor", profile.minorEmotion().name());
                emotionNbt.putFloat("minorStrength", profile.minorStrength());
            }
            if (profile.quirkEmotion() != null) {
                emotionNbt.putString("quirk", profile.quirkEmotion().name());
                emotionNbt.putFloat("quirkStrength", profile.quirkStrength());
            }
            nbt.put("natureEmotions", emotionNbt);
        }

        // Mood system persistence handled by engine
        moodEngine.writeToNbt(nbt);

        gossipLedger.encodeToNbt().result().ifPresent(element -> {
            if (element instanceof NbtCompound compound) {
                nbt.put("gossipLedger", compound);
            }
        });

        // Save progression module data
        ProgressionModule.Data progressionData = progressionModule.toData();
        NbtCompound milestonesNbt = new NbtCompound();
        for (Integer milestone : progressionData.unlockedMilestones().keySet()) {
            milestonesNbt.putBoolean(milestone.toString(), true);
        }
        nbt.put("milestones", milestonesNbt);
        
        // Save unlocked abilities
        if (!progressionData.unlockedAbilities().isEmpty()) {
            NbtCompound abilitiesNbt = new NbtCompound();
            for (Identifier abilityId : progressionData.unlockedAbilities().keySet()) {
                abilitiesNbt.putBoolean(abilityId.toString(), true);
            }
            nbt.put("unlockedAbilities", abilitiesNbt);
        }
        
        // Save permanent stat boosts
        if (!progressionData.permanentStatBoosts().isEmpty()) {
            NbtCompound statBoostsNbt = new NbtCompound();
            for (Map.Entry<String, Integer> entry : progressionData.permanentStatBoosts().entrySet()) {
                statBoostsNbt.putInt(entry.getKey(), entry.getValue());
            }
            nbt.put("permanentStatBoosts", statBoostsNbt);
        }
        
        // Save tribute milestones
        if (!progressionData.tributeMilestones().isEmpty()) {
            NbtCompound tributesNbt = new NbtCompound();
            for (Map.Entry<Integer, Identifier> entry : progressionData.tributeMilestones().entrySet()) {
                tributesNbt.putString(entry.getKey().toString(), entry.getValue().toString());
            }
            nbt.put("tributeMilestones", tributesNbt);
        }
        
        // Save state data
        if (!stateData.isEmpty()) {
            NbtCompound stateNbt = new NbtCompound();
            for (Map.Entry<String, Object> entry : stateData.entrySet()) {
                Object value = entry.getValue();
                String key = entry.getKey();
                
                // Save based on value type
                if (value instanceof String) {
                    stateNbt.putString(key, (String) value);
                } else if (value instanceof Integer) {
                    stateNbt.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    stateNbt.putLong(key, (Long) value);
                } else if (value instanceof Boolean) {
                    stateNbt.putBoolean(key, (Boolean) value);
                } else if (value instanceof Float) {
                    stateNbt.putFloat(key, (Float) value);
                } else if (value instanceof Double) {
                    stateNbt.putDouble(key, (Double) value);
                } else if (value instanceof java.util.List) {
                    // Handle lists by converting to string representation
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> list = (java.util.List<Object>) value;
                    NbtCompound listNbt = new NbtCompound();
                    for (int i = 0; i < list.size(); i++) {
                        listNbt.putString(String.valueOf(i), list.get(i).toString());
                    }
                    listNbt.putInt("size", list.size());
                    stateNbt.put(key, listNbt);
                }
                // Add more type handling as needed
            }
            nbt.put("stateData", stateNbt);
        }
        
        

        
        // Save inventories via module
        InventoryModule.Data inventoryData = inventoryModule.toData();
        if (!inventoryData.inventories().isEmpty()) {
            NbtCompound inventoriesNbt = new NbtCompound();
            for (Map.Entry<String, List<ItemStack>> entry : inventoryData.inventories().entrySet()) {
                List<ItemStack> list = entry.getValue();
                if (list == null || list.isEmpty()) {
                    continue;
                }

                NbtCompound inventoryNbt = new NbtCompound();
                inventoryNbt.putInt("size", list.size());

                NbtList items = new NbtList();
                RegistryOps<NbtElement> ops = getRegistryOps();
                for (ItemStack stack : list) {
                    DataResult<NbtElement> encoded = ItemStack.CODEC.encodeStart(ops, stack);
                    NbtElement element = encoded.result().orElseGet(NbtCompound::new);
                    items.add(element);
                }
                inventoryNbt.put("items", items);

                inventoriesNbt.put(entry.getKey(), inventoryNbt);
            }

            if (!inventoriesNbt.getKeys().isEmpty()) {
                nbt.put("inventories", inventoriesNbt);
            }
        }

        // Save characteristics via module
        PetCharacteristics characteristics = characteristicsModule.getCharacteristics();
        if (characteristics != null) {
            NbtCompound characteristicsNbt = new NbtCompound();
            characteristics.writeToNbt(characteristicsNbt);
            nbt.put("characteristics", characteristicsNbt);
        }
        
        // Save pet history - delegate to module
        List<HistoryEvent> history = historyModule.getEvents();
        if (!history.isEmpty()) {
            NbtList historyNbt = new NbtList();
            for (HistoryEvent event : history) {
                NbtCompound eventNbt = new NbtCompound();
                eventNbt.putLong("t", event.timestamp());
                eventNbt.putString("e", event.eventType());
                eventNbt.putString("o", event.ownerUuid().toString());
                eventNbt.putString("n", event.ownerName());
                eventNbt.putString("d", event.eventData());
                historyNbt.add(eventNbt);
            }
            nbt.put("petHistory", historyNbt);
        }
    }
    
    public void readFromNbt(NbtCompound nbt) {
        if (nbt.contains("role")) {
            nbt.getString("role").ifPresent(roleKey -> {
                Identifier parsed = Identifier.tryParse(roleKey);
                if (parsed == null && !roleKey.contains(":")) {
                    parsed = PetRoleType.normalizeId(roleKey);
                }

                if (parsed != null) {
                    setRoleId(parsed);
                }
            });
        }
        
        if (nbt.contains("lastAttackTick")) {
            nbt.getLong("lastAttackTick").ifPresent(tick -> this.lastAttackTick = tick);
        }
        if (nbt.contains("isPerched")) {
            nbt.getBoolean("isPerched").ifPresent(perched -> this.isPerched = perched);
        }
        if (nbt.contains("level")) {
            nbt.getInt("level").ifPresent(level -> progressionModule.setLevel(Math.max(1, level)));
        }
        if (nbt.contains("experience")) {
            nbt.getLong("experience").ifPresent(xp -> {
                // Set experience by calculating delta
                long current = progressionModule.getExperience();
                long delta = Math.max(0, xp) - current;
                if (pet.getWorld() instanceof ServerWorld sw) {
                    progressionModule.addExperience(delta, sw, pet.getWorld().getTime());
                }
            });
        }
        if (nbt.contains("xpFlashStartTick")) {
            nbt.getLong("xpFlashStartTick").ifPresent(tick -> this.xpFlashStartTick = tick);
        }

        if (nbt.contains("natureTuning")) {
            nbt.getCompound("natureTuning").ifPresent(tuning -> {
                float volatility = tuning.getFloat("volatility").orElse(1.0f);
                float resilience = tuning.getFloat("resilience").orElse(1.0f);
                float contagion = tuning.getFloat("contagion").orElse(1.0f);
                float guard = tuning.getFloat("guard").orElse(1.0f);
                setNatureEmotionTuning(volatility, resilience, contagion, guard);
            });
        } else {
            setNatureEmotionTuning(1.0f, 1.0f, 1.0f, 1.0f);
        }

        if (nbt.contains("natureEmotions")) {
            nbt.getCompound("natureEmotions").ifPresent(emotions -> {
                Emotion major = emotions.getString("major")
                    .flatMap(PetComponent::parseEmotionOptional)
                    .orElse(null);
                float majorStrength = emotions.getFloat("majorStrength").orElse(0f);
                Emotion minor = emotions.getString("minor")
                    .flatMap(PetComponent::parseEmotionOptional)
                    .orElse(null);
                float minorStrength = emotions.getFloat("minorStrength").orElse(0f);
                Emotion quirk = emotions.getString("quirk")
                    .flatMap(PetComponent::parseEmotionOptional)
                    .orElse(null);
                float quirkStrength = emotions.getFloat("quirkStrength").orElse(0f);
                setNatureEmotionProfile(new NatureEmotionProfile(major, majorStrength, minor, minorStrength,
                    quirk, quirkStrength));
            });
        } else {
            setNatureEmotionProfile(NatureEmotionProfile.EMPTY);
        }

        // Mood system persistence handled by engine
        moodEngine.readFromNbt(nbt);

        gossipLedger.clear();
        if (nbt.contains("gossipLedger")) {
            nbt.getCompound("gossipLedger").ifPresent(ledgerNbt ->
                PetGossipLedger.CODEC.parse(NbtOps.INSTANCE, ledgerNbt).result().ifPresent(gossipLedger::copyFrom)
            );
        }

        if (nbt.contains("milestones")) {
            nbt.getCompound("milestones").ifPresent(milestonesNbt -> {
                for (String key : milestonesNbt.getKeys()) {
                    try {
                        int level = Integer.parseInt(key);
                        milestonesNbt.getBoolean(key).ifPresent(unlocked -> {
                            if (unlocked) {
                                progressionModule.unlockMilestone(level);
                            }
                        });
                    } catch (NumberFormatException ignored) {
                        // Skip invalid milestone keys
                    }
                }
            });
        }
        
        // Load unlocked abilities
        if (nbt.contains("unlockedAbilities")) {
            nbt.getCompound("unlockedAbilities").ifPresent(abilitiesNbt -> {
                for (String key : abilitiesNbt.getKeys()) {
                    Identifier abilityId = Identifier.tryParse(key);
                    if (abilityId != null) {
                        abilitiesNbt.getBoolean(key).ifPresent(unlocked -> {
                            if (unlocked) {
                                progressionModule.unlockAbility(abilityId);
                            }
                        });
                    }
                }
            });
        }
        
        // Load permanent stat boosts
        if (nbt.contains("permanentStatBoosts")) {
            nbt.getCompound("permanentStatBoosts").ifPresent(statBoostsNbt -> {
                for (String key : statBoostsNbt.getKeys()) {
                    statBoostsNbt.getInt(key).ifPresent(boost ->
                        progressionModule.addPermanentStatBoost(key, boost));
                }
            });
        }
        
        // Load tribute milestones
        if (nbt.contains("tributeMilestones")) {
            nbt.getCompound("tributeMilestones").ifPresent(tributesNbt -> {
                for (String key : tributesNbt.getKeys()) {
                    try {
                        int level = Integer.parseInt(key);
                        tributesNbt.getString(key).ifPresent(itemIdStr -> {
                            Identifier itemId = Identifier.tryParse(itemIdStr);
                            if (itemId != null) {
                                progressionModule.setTributeMilestone(level, itemId);
                            }
                        });
                    } catch (NumberFormatException ignored) {
                        // Skip invalid tribute level keys
                    }
                }
            });
        }
        
        // Load stateData lists
        if (nbt.contains("stateData")) {
            nbt.getCompound("stateData").ifPresent(stateDataNbt -> {
                stateData.clear();
                for (String key : stateDataNbt.getKeys()) {
                    var listOpt = stateDataNbt.getCompound(key);
                    if (listOpt.isPresent()) {
                        var listNbt = listOpt.get();
                        listNbt.getInt("size").ifPresent(size -> {
                            java.util.List<String> list = new java.util.ArrayList<>();
                            for (int i = 0; i < size; i++) {
                                listNbt.getString(String.valueOf(i)).ifPresent(list::add);
                            }
                            setStateDataSilently(key, list);
                        });
                        continue;
                    }

                    stateDataNbt.getString(key).ifPresent(value -> setStateDataSilently(key, value));
                    stateDataNbt.getInt(key).ifPresent(value -> setStateDataSilently(key, value));
                    stateDataNbt.getLong(key).ifPresent(value -> setStateDataSilently(key, value));
                    stateDataNbt.getDouble(key).ifPresent(value -> setStateDataSilently(key, value));
                    stateDataNbt.getFloat(key).ifPresent(value -> setStateDataSilently(key, value));
                    stateDataNbt.getBoolean(key).ifPresent(value -> setStateDataSilently(key, value));
                }
            });
        }
        migrateLegacyStateData();
        refreshSpeciesDescriptor();

        String ownerId = getStateData("petsplus:owner_uuid", String.class, "");
        if (ownerId != null && !ownerId.isEmpty()) {
            try {
                setOwnerUuid(UUID.fromString(ownerId));
            } catch (IllegalArgumentException ignored) {
                setOwnerUuid(null);
            }
        } else {
            setOwnerUuid(null);
        }
        
        

        
        // Load inventories via module
        if (nbt.contains("inventories")) {
            nbt.getCompound("inventories").ifPresent(inventoriesNbt -> {
                for (String key : inventoriesNbt.getKeys()) {
                    inventoriesNbt.getCompound(key).ifPresent(inventoryNbt -> {
                        int size = inventoryNbt.getInt("size").orElse(0);
                        if (size <= 0) {
                            inventoryModule.setInventory(key, DefaultedList.ofSize(0, ItemStack.EMPTY));
                            return;
                        }

                        DefaultedList<ItemStack> list = DefaultedList.ofSize(size, ItemStack.EMPTY);
                        RegistryOps<NbtElement> ops = getRegistryOps();
                        inventoryNbt.getList("items").ifPresent(items -> {
                            for (int i = 0; i < items.size() && i < list.size(); i++) {
                                NbtElement element = items.get(i);
                                ItemStack decoded = ItemStack.CODEC.parse(ops, element).result().orElse(ItemStack.EMPTY);
                                list.set(i, decoded);
                            }
                        });
                        inventoryModule.setInventory(key, list);
                    });
                }
            });
        }

        // Load characteristics via module
        if (nbt.contains("characteristics")) {
            nbt.getCompound("characteristics").ifPresent(characteristicsNbt -> {
                characteristicsModule.setCharacteristics(PetCharacteristics.readFromNbt(characteristicsNbt));
            });
        }

        syncCharacteristicAffinityLookup();
        
        // Load pet history - delegate to module
        if (nbt.contains("petHistory")) {
            nbt.getList("petHistory").ifPresent(historyNbt -> {
                List<HistoryEvent> events = new ArrayList<>();
                for (int i = 0; i < historyNbt.size(); i++) {
                    historyNbt.getCompound(i).ifPresent(eventNbt -> {
                        try {
                            long timestamp = eventNbt.getLong("t").orElse(0L);
                            String eventType = eventNbt.getString("e").orElse("");
                            String ownerUuidStr = eventNbt.getString("o").orElse("");
                            UUID ownerUuid = null;
                            if (!ownerUuidStr.isEmpty()) {
                                try {
                                    ownerUuid = UUID.fromString(ownerUuidStr);
                                } catch (IllegalArgumentException e) {
                                    Petsplus.LOGGER.warn("Invalid UUID in history event: " + ownerUuidStr);
                                    ownerUuid = null; // Use null for invalid UUIDs
                                }
                            }
                            String ownerName = eventNbt.getString("n").orElse("");
                            String eventData = eventNbt.getString("d").orElse("");
                            
                            HistoryEvent event = new HistoryEvent(timestamp, eventType, ownerUuid, ownerName, eventData);
                            events.add(event);
                        } catch (Exception e) {
                            Petsplus.LOGGER.warn("Failed to load history event for pet " + pet.getUuidAsString(), e);
                        }
                    });
                }
                historyModule.fromData(new HistoryModule.Data(events));
            });
        }
    }
    
    /**
     * Save component data to entity using component system.
     */
    public void saveToEntity() {
        PetsplusComponents.PetData data = PetsplusComponents.PetData.empty()
            .withRole(getRoleId());

        data = data.withLastAttackTick(lastAttackTick)
                  .withPerched(isPerched);
            
        // Include cooldowns from scheduling module
        for (Map.Entry<String, Long> entry : schedulingModule.getAllCooldowns().entrySet()) {
            data = data.withCooldown(entry.getKey(), entry.getValue());
        }
        
        // Component data prepared for codec-based serialization
        // NBT persistence handles actual storage through writeToNbt/readFromNbt
    }
    
    /**
     * Loads component data from entity using the component system.
     * NBT persistence handles deserialization through readFromNbt.
     */
    public void loadFromEntity() {
        // Reserved for future codec-based entity component integration
    }
}

