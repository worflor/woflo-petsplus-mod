package woflo.petsplus.api.registry;

import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Describes metadata for a pet role. Registries expose this data so that
 * gameplay systems and data packs can reason about role defaults without
 * depending on enum ordinals or ad-hoc constants sprinkled throughout the
 * codebase.
 */
public final class PetRoleType {
    /** Shared XP curve metadata for built-in definitions. */
    private static final XpCurve DEFAULT_XP_CURVE = new XpCurve(
        30,
        List.of(3, 7, 12, 17, 23, 27),
        List.of(10, 20, 30),
        20,
        8,
        0.75f
    );

    private static final Map<Integer, Identifier> DEFAULT_TRIBUTES = Map.of(
        10, Identifier.of("minecraft", "gold_ingot"),
        20, Identifier.of("minecraft", "diamond"),
        30, Identifier.of("minecraft", "netherite_scrap")
    );

    /**
     * Default tribute item mapping shared across registry-driven definitions and config fallbacks.
     */
    public static Map<Integer, Identifier> defaultTributeItems() {
        return DEFAULT_TRIBUTES;
    }

    public static final Identifier GUARDIAN_ID = Identifier.of(Petsplus.MOD_ID, "guardian");
    public static final Identifier STRIKER_ID = Identifier.of(Petsplus.MOD_ID, "striker");
    public static final Identifier SUPPORT_ID = Identifier.of(Petsplus.MOD_ID, "support");
    public static final Identifier SCOUT_ID = Identifier.of(Petsplus.MOD_ID, "scout");
    public static final Identifier SKYRIDER_ID = Identifier.of(Petsplus.MOD_ID, "skyrider");
    public static final Identifier ENCHANTMENT_BOUND_ID = Identifier.of(Petsplus.MOD_ID, "enchantment_bound");
    public static final Identifier CURSED_ONE_ID = Identifier.of(Petsplus.MOD_ID, "cursed_one");
    public static final Identifier EEPY_EEPER_ID = Identifier.of(Petsplus.MOD_ID, "eepy_eeper");
    public static final Identifier ECLIPSED_ID = Identifier.of(Petsplus.MOD_ID, "eclipsed");

    public static final List<Identifier> BUILTIN_ORDER = List.of(
        GUARDIAN_ID,
        STRIKER_ID,
        SUPPORT_ID,
        SCOUT_ID,
        SKYRIDER_ID,
        ENCHANTMENT_BOUND_ID,
        CURSED_ONE_ID,
        EEPY_EEPER_ID,
        ECLIPSED_ID
    );

    public static final Map<Identifier, String> DEFAULT_DESCRIPTIONS = Map.ofEntries(
        Map.entry(GUARDIAN_ID, "Protective tank that redirects damage and provides stability"),
        Map.entry(STRIKER_ID, "Aggressive fighter that marks weakened enemies for execution"),
        Map.entry(SUPPORT_ID, "Healing companion that stores and shares beneficial effects"),
        Map.entry(SCOUT_ID, "Fast explorer that reveals threats and attracts loot"),
        Map.entry(SKYRIDER_ID, "Aerial specialist that controls air movement and prevents falls"),
        Map.entry(ENCHANTMENT_BOUND_ID, "Magic-focused pet that enhances gear enchantments"),
        Map.entry(CURSED_ONE_ID, "Dark magic wielder with high risk and reward mechanics"),
        Map.entry(EEPY_EEPER_ID, "Sleep specialist that provides rest-based healing and protection"),
        Map.entry(ECLIPSED_ID, "Shadow manipulator that brands enemies and provides stealth")
    );

    /** Static mirrors so existing call sites continue to function. */
    public static final PetRoleType GUARDIAN = new PetRoleType(
        GUARDIAN_ID,
        builder(GUARDIAN_ID)
            .withBaseStatScalar("defense", 0.02f)
            .withStatAffinity("health", 0.05f)
            .withStatAffinity("defense", 0.05f)
            .withStatAffinity("learning", 0.02f)
            .withAttributeScaling(AttributeScaling.builder()
                .healthBonusPerLevel(0.02f)
                .healthPostSoftcapBonusPerLevel(0.01f)
                .healthSoftcapLevel(20)
                .healthMaxBonus(2.0f)
                .build()
            )
            .withDefaultAbilities(List.of(ability("shield_bash_rider")))
            .withVisual(new Visual(0x4AA3F0, 0x1F6DB5, "guardian_ambient", "guardian"))
            .withPresentation(defaultPresentation(
                "guardian_protection_stance",
                "Your guardian stands proudly, ready to protect",
                List.of(
                    "The Steadfast Shield",
                    "The Loyal Protector",
                    "The Unbreaking Wall",
                    "The Gentle Guardian"
                ),
                DEFAULT_DESCRIPTIONS.get(GUARDIAN_ID)
            ))
            .build()
    );

    public static final PetRoleType STRIKER = new PetRoleType(
        STRIKER_ID,
        builder(STRIKER_ID)
            .withBaseStatScalar("offense", 0.02f)
            .withStatAffinity("attack", 0.05f)
            .withStatAffinity("speed", 0.03f)
            .withStatAffinity("learning", 0.03f)
            .withAttributeScaling(AttributeScaling.builder()
                .attackBonusPerLevel(0.02f)
                .attackPostSoftcapBonusPerLevel(0.01f)
                .attackSoftcapLevel(15)
                .attackMaxBonus(1.5f)
                .build()
            )
            .withDefaultAbilities(List.of(ability("finisher_mark")))
            .withVisual(new Visual(0xE23A3A, 0x8B1E1E, "striker_ambient", "striker"))
            .withPresentation(defaultPresentation(
                "striker_eagerness",
                "Your striker's eyes gleam with hunting intent",
                List.of(
                    "The Swift Strike",
                    "The Final Word",
                    "The Hunter's Mark",
                    "The Decisive Blow"
                ),
                DEFAULT_DESCRIPTIONS.get(STRIKER_ID)
            ))
            .build()
    );

    public static final PetRoleType SUPPORT = new PetRoleType(
        SUPPORT_ID,
        builder(SUPPORT_ID)
            .withBaseStatScalar("aura", 0.02f)
            .withStatAffinity("vitality", 0.05f)
            .withStatAffinity("health", 0.03f)
            .withStatAffinity("learning", 0.04f)
            .withDefaultAbilities(List.of(
                ability("perch_potion_efficiency"),
                ability("mounted_cone_aura")
            ))
            .withVisual(new Visual(0x64F5A4, 0x2E9B66, "support_ambient", "support"))
            .withPresentation(defaultPresentation(
                "support_gentle_aura",
                "Your support radiates warmth and comfort",
                List.of(
                    "The Healing Heart",
                    "The Caring Soul",
                    "The Gentle Mender",
                    "The Life Giver"
                ),
                DEFAULT_DESCRIPTIONS.get(SUPPORT_ID)
            ))
            .build()
    );

    public static final PetRoleType SCOUT = new PetRoleType(
        SCOUT_ID,
        builder(SCOUT_ID)
            .withBaseStatScalar("mobility", 0.02f)
            .withStatAffinity("speed", 0.05f)
            .withStatAffinity("agility", 0.05f)
            .withStatAffinity("learning", 0.05f)
            .withAttributeScaling(AttributeScaling.builder()
                .speedBonusPerLevel(0.01f)
                .speedMaxBonus(0.6f)
                .build()
            )
            .withDefaultAbilities(List.of(ability("loot_wisp")))
            .withVisual(new Visual(0xF7C64C, 0xA37516, "scout_ambient", "scout"))
            .withPresentation(defaultPresentation(
                "scout_alertness",
                "Your scout's senses sharpen, ears perked",
                List.of(
                    "The Keen Eye",
                    "The Path Finder",
                    "The Bright Lantern",
                    "The Treasure Seeker"
                ),
                DEFAULT_DESCRIPTIONS.get(SCOUT_ID)
            ))
            .build()
    );

    public static final PetRoleType SKYRIDER = new PetRoleType(
        SKYRIDER_ID,
        builder(SKYRIDER_ID)
            .withBaseStatScalar("mobility", 0.02f)
            .withStatAffinity("agility", 0.05f)
            .withStatAffinity("speed", 0.03f)
            .withStatAffinity("learning", 0.03f)
            .withAttributeScaling(AttributeScaling.builder()
                .speedBonusPerLevel(0.01f)
                .speedMaxBonus(0.6f)
                .build()
            )
            .withDefaultAbilities(List.of(ability("windlash_rider")))
            .withVisual(new Visual(0xFFFFFF, 0xD8E7FF, "skyrider_ambient", "skyrider"))
            .withPresentation(defaultPresentation(
                "skyrider_wind_dance",
                "The air stirs around your skyrider",
                List.of(
                    "The Wind Walker",
                    "The Sky Dancer",
                    "The Cloud Rider",
                    "The Storm Caller"
                ),
                DEFAULT_DESCRIPTIONS.get(SKYRIDER_ID)
            ))
            .build()
    );

    public static final PetRoleType ENCHANTMENT_BOUND = new PetRoleType(
        ENCHANTMENT_BOUND_ID,
        builder(ENCHANTMENT_BOUND_ID)
            .withBaseStatScalar("echo", 0.02f)
            .withStatAffinity("vitality", 0.03f)
            .withStatAffinity("agility", 0.03f)
            .withStatAffinity("learning", 0.06f)
            .withVisual(new Visual(0xBC73FF, 0x6A1FBF, "enchantment_bound_ambient", "enchantment_bound"))
            .withPresentation(defaultPresentation(
                "enchantment_sparkle",
                "Arcane energies swirl gently around your companion",
                List.of(
                    "The Mystic Echo",
                    "The Arcane Bond",
                    "The Magic Weaver",
                    "The Spell Keeper"
                ),
                DEFAULT_DESCRIPTIONS.get(ENCHANTMENT_BOUND_ID)
            ))
            .build()
    );

    public static final PetRoleType CURSED_ONE = new PetRoleType(
        CURSED_ONE_ID,
        builder(CURSED_ONE_ID)
            .withBaseStatScalar("curse", 0.02f)
            .withDefaultAbilities(List.of(ability("doom_echo")))
            .withStatAffinity("attack", 0.03f)
            .withStatAffinity("vitality", 0.03f)
            .withStatAffinity("learning", 0.04f)
            .withVisual(new Visual(0x4B1F1F, 0xA01919, "cursed_one_ambient", "cursed_one"))
            .withPresentation(defaultPresentation(
                "cursed_dark_affection",
                "Your cursed companion's eyes glow with twisted loyalty",
                List.of(
                    "The Beautiful Curse",
                    "The Dark Blessing",
                    "The Shadowed Light",
                    "The Grim Fortune"
                ),
                DEFAULT_DESCRIPTIONS.get(CURSED_ONE_ID)
            ))
            .build()
    );

    public static final PetRoleType EEPY_EEPER = new PetRoleType(
        EEPY_EEPER_ID,
        builder(EEPY_EEPER_ID)
            .withBaseStatScalar("slumber", 0.02f)
            .withStatAffinity("health", 0.03f)
            .withStatAffinity("vitality", 0.05f)
            .withStatAffinity("learning", 0.01f)
            .withVisual(new Visual(0xD18AFD, 0x6F3BAE, "eepy_eeper_ambient", "eepy_eeper"))
            .withPresentation(defaultPresentation(
                "eepy_sleepy_contentment",
                "Your sleepy companion purrs drowsily",
                List.of(
                    "The Dreaming Spirit",
                    "The Restful Soul",
                    "The Sleepy Guardian",
                    "The Cozy Companion"
                ),
                DEFAULT_DESCRIPTIONS.get(EEPY_EEPER_ID)
            ))
            .build()
    );

    public static final PetRoleType ECLIPSED = new PetRoleType(
        ECLIPSED_ID,
        builder(ECLIPSED_ID)
            .withBaseStatScalar("disruption", 0.02f)
            .withDefaultAbilities(List.of(
                ability("voidbrand"),
                ability("phase_partner"),
                ability("perch_ping")
            ))
            .withStatAffinity("speed", 0.03f)
            .withStatAffinity("attack", 0.03f)
            .withStatAffinity("learning", 0.05f)
            .withVisual(new Visual(0x3D1F4B, 0x12061A, "eclipsed_ambient", "eclipsed"))
            .withPresentation(defaultPresentation(
                "eclipsed_void_pulse",
                "Reality flickers around your eclipsed companion",
                List.of(
                    "The Void Touched",
                    "The Shadow Walker",
                    "The Eclipse Born",
                    "The Dark Star"
                ),
                DEFAULT_DESCRIPTIONS.get(ECLIPSED_ID)
            ))
            .build()
    );

    private final Identifier id;
    private volatile Definition definition;

    public PetRoleType(Identifier id, Definition definition) {
        this.id = Objects.requireNonNull(id, "id");
        applyDefinition(Objects.requireNonNull(definition, "definition"));
    }

    /** Identifier for this role type. */
    public Identifier id() {
        return id;
    }

    /** Translation key used for UI strings describing the role. */
    public String translationKey() {
        return definition.translationKey();
    }

    /** Baseline scalar bonuses for role-specific stat buckets. */
    public Map<String, Float> baseStatScalars() {
        return definition.baseStatScalars();
    }

    /** Role-specific stat affinities used by characteristic calculations. */
    public Map<String, Float> statAffinities() {
        return definition.statAffinities();
    }

    /** Ability identifiers that should be unlocked by default for this role. */
    public List<Identifier> defaultAbilities() {
        return definition.defaultAbilities();
    }

    /** Default tribute items required to bypass milestone caps. */
    public TributeDefaults tributeDefaults() {
        return definition.tributeDefaults();
    }

    /** XP progression metadata backing {@link woflo.petsplus.state.PetComponent}. */
    public XpCurve xpCurve() {
        return definition.xpCurve();
    }

    /** Visual metadata consumed by UI and particle helpers. */
    public Visual visual() {
        return definition.visual();
    }

    /** Attribute scaling behavior applied on top of global level formulas. */
    public AttributeScaling attributeScaling() {
        return definition.attributeScaling();
    }

    /** Presentation metadata used for UI, chat, and admin helpers. */
    public Presentation presentation() {
        return definition.presentation();
    }

    /** Passive aura descriptors configured for this role. */
    public List<PassiveAura> passiveAuras() {
        return definition.passiveAuras();
    }

    /** Support potion behavior configuration, if any. */
    public SupportPotionBehavior supportPotionBehavior() {
        return definition.supportPotionBehavior();
    }

    /** Milestone advancement hooks triggered on level-up. */
    public List<MilestoneAdvancement> milestoneAdvancements() {
        return definition.milestoneAdvancements();
    }

    public Definition definition() {
        return definition;
    }

    /** Replace this role's definition at runtime (e.g., datapack reload). */
    public synchronized void applyDefinition(Definition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    private static DefinitionBuilder builder(Identifier id) {
        return new DefinitionBuilder(id, translationKey(id));
    }

    private static String translationKey(Identifier id) {
        return Petsplus.MOD_ID + ".role." + id.getPath();
    }

    private static Identifier ability(String path) {
        return Identifier.of(Petsplus.MOD_ID, path);
    }

    /**
     * Normalize a raw string into an identifier within the PetsPlus namespace when possible.
     * Legacy enum values (e.g., "GUARDIAN") are mapped to their lowercase path equivalents.
     */
    public static Identifier normalizeId(String raw) {
        if (raw == null) {
            return null;
        }

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        Identifier parsed = Identifier.tryParse(trimmed);
        if (parsed != null) {
            return parsed;
        }

        String normalized = trimmed
            .toLowerCase(Locale.ROOT)
            .replace(' ', '_')
            .replace('-', '_');

        if (normalized.contains(":")) {
            return Identifier.tryParse(normalized);
        }

        return Identifier.of(Petsplus.MOD_ID, normalized);
    }

    public static int builtinIndex(Identifier id) {
        return BUILTIN_ORDER.indexOf(id);
    }

    public static String defaultDescription(Identifier id) {
        return DEFAULT_DESCRIPTIONS.getOrDefault(id, id.toString());
    }

    public static String fallbackName(Identifier id) {
        String path = id.getPath();
        String[] parts = path.split("[_:]");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() == 0 ? path : builder.toString();
    }

    private static TributeDefaults defaultTributes() {
        return new TributeDefaults(DEFAULT_TRIBUTES);
    }

    private static XpCurve defaultXpCurve() {
        return DEFAULT_XP_CURVE;
    }

    private static Presentation defaultPresentation(
        String feedbackEvent,
        String pettingFallback,
        List<String> epithetFallbacks,
        String adminSummaryFallback
    ) {
        List<Message> epithets = new ArrayList<>();
        if (epithetFallbacks != null) {
            for (String fallback : epithetFallbacks) {
                if (fallback != null && !fallback.isBlank()) {
                    epithets.add(messageFallback(fallback));
                }
            }
        }

        Message adminSummary = adminSummaryFallback == null || adminSummaryFallback.isBlank()
            ? Message.EMPTY
            : messageFallback(adminSummaryFallback);

        Message pettingMessage = pettingFallback == null || pettingFallback.isBlank()
            ? Message.EMPTY
            : messageFallback(pettingFallback);

        return new Presentation(
            new Petting(pettingMessage, feedbackEvent == null ? "" : feedbackEvent),
            epithets,
            adminSummary
        );
    }

    private static Message messageFallback(String fallback) {
        return new Message(null, fallback);
    }

    /** Builder used internally to construct the static definitions. */
    public static final class DefinitionBuilder {
        private final Identifier id;
        private String translationKey;
        private final Map<String, Float> scalars = new LinkedHashMap<>();
        private final Map<String, Float> statAffinities = new LinkedHashMap<>();
        private List<Identifier> defaultAbilities = List.of();
        private TributeDefaults tributeDefaults = defaultTributes();
        private XpCurve xpCurve = defaultXpCurve();
        private Visual visual = Visual.DEFAULT;
        private AttributeScaling attributeScaling = AttributeScaling.DEFAULT;
        private List<PassiveAura> passiveAuras = List.of();
        private SupportPotionBehavior supportPotionBehavior = null;
        private List<MilestoneAdvancement> milestoneAdvancements = List.of();
        private Presentation presentation = Presentation.DEFAULT;

        DefinitionBuilder(Identifier id, String translationKey) {
            this.id = id;
            this.translationKey = translationKey;
        }

        public DefinitionBuilder withTranslationKey(String translationKey) {
            this.translationKey = translationKey;
            return this;
        }

        public DefinitionBuilder withBaseStatScalar(String key, float value) {
            this.scalars.put(key, value);
            return this;
        }

        public DefinitionBuilder withStatAffinity(String key, float value) {
            this.statAffinities.put(key, value);
            return this;
        }

        public DefinitionBuilder withAttributeScaling(AttributeScaling scaling) {
            this.attributeScaling = scaling == null ? AttributeScaling.DEFAULT : scaling;
            return this;
        }

        public DefinitionBuilder withDefaultAbilities(List<Identifier> abilities) {
            this.defaultAbilities = List.copyOf(abilities);
            return this;
        }

        public DefinitionBuilder withTributeDefaults(Map<Integer, Identifier> tributes) {
            this.tributeDefaults = new TributeDefaults(tributes);
            return this;
        }

        public DefinitionBuilder withXpCurve(XpCurve curve) {
            this.xpCurve = curve;
            return this;
        }

        public DefinitionBuilder withVisual(Visual visual) {
            this.visual = visual == null ? Visual.DEFAULT : visual;
            return this;
        }

        public DefinitionBuilder withPassiveAuras(List<PassiveAura> passiveAuras) {
            this.passiveAuras = passiveAuras == null ? List.of() : List.copyOf(passiveAuras);
            return this;
        }

        public DefinitionBuilder withSupportPotionBehavior(SupportPotionBehavior behavior) {
            this.supportPotionBehavior = behavior;
            return this;
        }

        public DefinitionBuilder withMilestoneAdvancements(List<MilestoneAdvancement> advancements) {
            this.milestoneAdvancements = advancements == null ? List.of() : List.copyOf(advancements);
            return this;
        }

        public DefinitionBuilder withPresentation(Presentation presentation) {
            this.presentation = presentation == null ? Presentation.DEFAULT : presentation;
            return this;
        }

        public Definition build() {
            return new Definition(
                id,
                translationKey,
                scalars,
                defaultAbilities,
                tributeDefaults,
                xpCurve,
                visual,
                statAffinities,
                attributeScaling,
                passiveAuras,
                supportPotionBehavior,
                milestoneAdvancements,
                presentation
            );
        }
    }

    /** Fully describes the data-driven definition for a role. */
    public record Definition(
        Identifier id,
        String translationKey,
        Map<String, Float> baseStatScalars,
        List<Identifier> defaultAbilities,
        TributeDefaults tributeDefaults,
        XpCurve xpCurve,
        Visual visual,
        Map<String, Float> statAffinities,
        AttributeScaling attributeScaling,
        List<PassiveAura> passiveAuras,
        SupportPotionBehavior supportPotionBehavior,
        List<MilestoneAdvancement> milestoneAdvancements,
        Presentation presentation
    ) {
        public Definition {
            Objects.requireNonNull(id, "id");
            translationKey = Objects.requireNonNullElseGet(translationKey, () ->
                Petsplus.MOD_ID + ".role." + id.getPath()
            );
            baseStatScalars = Collections.unmodifiableMap(new LinkedHashMap<>(baseStatScalars));
            defaultAbilities = List.copyOf(defaultAbilities);
            tributeDefaults = Objects.requireNonNullElseGet(tributeDefaults, PetRoleType::defaultTributes);
            xpCurve = Objects.requireNonNullElseGet(xpCurve, PetRoleType::defaultXpCurve);
            visual = Objects.requireNonNullElse(visual, Visual.DEFAULT);
            statAffinities = Collections.unmodifiableMap(new LinkedHashMap<>(statAffinities));
            attributeScaling = Objects.requireNonNullElse(attributeScaling, AttributeScaling.DEFAULT);
            passiveAuras = List.copyOf(passiveAuras == null ? List.of() : passiveAuras);
            supportPotionBehavior = supportPotionBehavior;
            milestoneAdvancements = List.copyOf(milestoneAdvancements == null ? List.of() : milestoneAdvancements);
            presentation = Objects.requireNonNullElse(presentation, Presentation.DEFAULT);
        }
    }

    /**
     * Default tribute metadata for a role. Stored as a dedicated type to make
     * future expansion (grace periods, per-level overrides, etc.) easier.
     */
    public record TributeDefaults(Map<Integer, Identifier> milestoneItems) {
        public TributeDefaults {
            milestoneItems = Collections.unmodifiableMap(new LinkedHashMap<>(milestoneItems));
        }

        public Identifier itemForLevel(int level) {
            return milestoneItems.get(level);
        }
    }

    /** Describes XP progression parameters for a role. */
    public record XpCurve(
        int maxLevel,
        List<Integer> featureLevels,
        List<Integer> tributeMilestones,
        int baseLinearPerLevel,
        int quadraticFactor,
        float featureLevelBonusMultiplier
    ) {
        public XpCurve {
            featureLevels = List.copyOf(featureLevels);
            tributeMilestones = List.copyOf(tributeMilestones);
        }

        public boolean isFeatureLevel(int level) {
            return featureLevels.contains(level);
        }
    }

    /** Visual metadata consumed by particle and UI helpers. */
    public record Visual(int primaryColor, int secondaryColor, String ambientEvent, String abilityEventPrefix) {
        public static final Visual DEFAULT = new Visual(0xFFFFFF, 0xFFFFFF, "", "");

        public Visual {
            ambientEvent = ambientEvent == null ? "" : ambientEvent;
            abilityEventPrefix = abilityEventPrefix == null ? "" : abilityEventPrefix;
        }
    }

    /** Attribute scaling modifiers layered on top of global level formulas. */
    public record AttributeScaling(
        float healthBonusPerLevel,
        float healthPostSoftcapBonusPerLevel,
        int healthSoftcapLevel,
        float healthMaxBonus,
        float speedBonusPerLevel,
        float speedMaxBonus,
        float attackBonusPerLevel,
        float attackPostSoftcapBonusPerLevel,
        int attackSoftcapLevel,
        float attackMaxBonus
    ) {
        public static final AttributeScaling DEFAULT = new AttributeScaling(0f, 0f, 20, 2.0f, 0f, 0.6f, 0f, 0f, 15, 1.5f);

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private float healthBonusPerLevel = 0f;
            private float healthPostSoftcapBonusPerLevel = 0f;
            private int healthSoftcapLevel = DEFAULT.healthSoftcapLevel();
            private float healthMaxBonus = DEFAULT.healthMaxBonus();
            private float speedBonusPerLevel = 0f;
            private float speedMaxBonus = DEFAULT.speedMaxBonus();
            private float attackBonusPerLevel = 0f;
            private float attackPostSoftcapBonusPerLevel = 0f;
            private int attackSoftcapLevel = DEFAULT.attackSoftcapLevel();
            private float attackMaxBonus = DEFAULT.attackMaxBonus();

            public Builder healthBonusPerLevel(float value) {
                this.healthBonusPerLevel = value;
                return this;
            }

            public Builder healthPostSoftcapBonusPerLevel(float value) {
                this.healthPostSoftcapBonusPerLevel = value;
                return this;
            }

            public Builder healthSoftcapLevel(int level) {
                this.healthSoftcapLevel = level;
                return this;
            }

            public Builder healthMaxBonus(float value) {
                this.healthMaxBonus = value;
                return this;
            }

            public Builder speedBonusPerLevel(float value) {
                this.speedBonusPerLevel = value;
                return this;
            }

            public Builder speedMaxBonus(float value) {
                this.speedMaxBonus = value;
                return this;
            }

            public Builder attackBonusPerLevel(float value) {
                this.attackBonusPerLevel = value;
                return this;
            }

            public Builder attackPostSoftcapBonusPerLevel(float value) {
                this.attackPostSoftcapBonusPerLevel = value;
                return this;
            }

            public Builder attackSoftcapLevel(int level) {
                this.attackSoftcapLevel = level;
                return this;
            }

            public Builder attackMaxBonus(float value) {
                this.attackMaxBonus = value;
                return this;
            }

            public AttributeScaling build() {
                return new AttributeScaling(
                    healthBonusPerLevel,
                    healthPostSoftcapBonusPerLevel,
                    healthSoftcapLevel,
                    healthMaxBonus,
                    speedBonusPerLevel,
                    speedMaxBonus,
                    attackBonusPerLevel,
                    attackPostSoftcapBonusPerLevel,
                    attackSoftcapLevel,
                    attackMaxBonus
                );
            }
        }
    }

    /** Metadata describing a passive aura pulse. */
    public record PassiveAura(
        String id,
        int intervalTicks,
        double radius,
        int minLevel,
        boolean requireSitting,
        List<AuraEffect> effects,
        Message message,
        SoundCue sound,
        String particleEvent
    ) {
        public PassiveAura {
            id = (id == null || id.isBlank()) ? "default" : id;
            intervalTicks = Math.max(1, intervalTicks);
            radius = Math.max(0.0, radius);
            minLevel = Math.max(1, minLevel);
            requireSitting = requireSitting;
            effects = List.copyOf(effects == null ? List.of() : effects);
            message = message == null ? Message.EMPTY : message;
            sound = sound == null ? SoundCue.NONE : sound;
            particleEvent = particleEvent == null ? "" : particleEvent;
        }

        public boolean hasEffects() {
            return !effects.isEmpty();
        }
    }

    /** Individual status effect application for a passive aura pulse. */
    public record AuraEffect(
        Identifier effectId,
        AuraTarget target,
        int durationTicks,
        int amplifier,
        int minLevel
    ) {
        public AuraEffect {
            effectId = Objects.requireNonNull(effectId, "effectId");
            target = Objects.requireNonNullElse(target, AuraTarget.OWNER);
            durationTicks = Math.max(1, durationTicks);
            amplifier = Math.max(0, amplifier);
            minLevel = Math.max(1, minLevel);
        }
    }

    /** Supported aura targeting strategies. */
    public enum AuraTarget {
        OWNER,
        PET,
        OWNER_AND_PET,
        OWNER_AND_ALLIES,
        NEARBY_PLAYERS,
        NEARBY_ALLIES
    }

    /** Structured behavior for support potion aura broadcasting. */
    public record SupportPotionBehavior(
        String id,
        int intervalTicks,
        double radius,
        int minLevel,
        boolean requireSitting,
        Identifier fallbackEffect,
        int effectDurationTicks,
        boolean applyToPet,
        Message message,
        SoundCue sound,
        String particleEvent
    ) {
        public SupportPotionBehavior {
            id = (id == null || id.isBlank()) ? "stored_potion" : id;
            intervalTicks = Math.max(1, intervalTicks);
            radius = Math.max(0.0, radius);
            minLevel = Math.max(1, minLevel);
            fallbackEffect = fallbackEffect;
            effectDurationTicks = Math.max(20, effectDurationTicks);
            message = message == null ? Message.EMPTY : message;
            sound = sound == null ? SoundCue.NONE : sound;
            particleEvent = particleEvent == null ? "" : particleEvent;
        }
    }

    /** Declarative milestone advancement hooks for role level-ups. */
    public record MilestoneAdvancement(
        int level,
        Identifier advancementId,
        Message message,
        SoundCue sound
    ) {
        public MilestoneAdvancement {
            level = Math.max(1, level);
            advancementId = advancementId;
            message = message == null ? Message.EMPTY : message;
            sound = sound == null ? SoundCue.NONE : sound;
        }
    }

    /** Wrapper for user-facing text metadata. */
    public record Message(String translationKey, String fallback) {
        public static final Message EMPTY = new Message(null, null);

        public boolean isPresent() {
            return (translationKey != null && !translationKey.isBlank())
                || (fallback != null && !fallback.isBlank());
        }
    }

    /** Optional sound cue metadata. */
    public record SoundCue(Identifier soundId, float volume, float pitch) {
        public static final SoundCue NONE = new SoundCue(null, 1.0f, 1.0f);

        public SoundCue {
            volume = Math.max(0f, volume);
            pitch = Math.max(0f, pitch);
        }

        public boolean isPresent() {
            return soundId != null;
        }
    }

    /** Presentation strings and events used by UI components. */
    public record Presentation(Petting petting, List<Message> memorialEpithets, Message adminSummary) {
        public static final Presentation DEFAULT = new Presentation(Petting.EMPTY, List.of(), Message.EMPTY);

        public Presentation {
            petting = petting == null ? Petting.EMPTY : petting;
            memorialEpithets = List.copyOf(memorialEpithets == null ? List.of() : memorialEpithets);
            adminSummary = adminSummary == null ? Message.EMPTY : adminSummary;
        }

        public boolean hasMemorialEpithets() {
            return !memorialEpithets.isEmpty();
        }
    }

    /** Petting presentation metadata for chat feedback. */
    public record Petting(Message message, String feedbackEvent) {
        public static final Petting EMPTY = new Petting(Message.EMPTY, "");

        public Petting {
            message = message == null ? Message.EMPTY : message;
            feedbackEvent = feedbackEvent == null ? "" : feedbackEvent;
        }

        public boolean hasFeedbackEvent() {
            return !feedbackEvent.isBlank();
        }
    }
}
