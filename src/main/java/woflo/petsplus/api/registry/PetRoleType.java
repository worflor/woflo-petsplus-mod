package woflo.petsplus.api.registry;

import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.LevelReward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import java.util.Map;
import java.util.Objects;

/**
 * Describes metadata for a pet role. Registries expose this data so that
 * gameplay systems and data packs can reason about role defaults without
 * depending on enum ordinals or ad-hoc constants sprinkled throughout the
 * codebase.
 * 
 * <h2>Role Architecture Standardization</h2>
 * 
 * <p>This class provides a standardized template system for creating consistent
 * role definitions. All roles follow patterns based on their archetype.</p>
 * 
 * <h3>Role Archetypes</h3>
 * <ul>
 *   <li><b>TANK</b> - Defensive roles focused on survival and protection
 *       <br>Example: Guardian - high health scaling, defense focus
 *       <br>Use: When creating roles that protect owners and absorb damage</li>
 *   
 *   <li><b>DPS</b> - Offensive roles focused on damage output
 *       <br>Example: Striker - high attack scaling, aggression focus
 *       <br>Use: When creating roles that deal damage and eliminate threats</li>
 *   
 *   <li><b>SUPPORT</b> - Supportive roles focused on healing and buffs
 *       <br>Example: Support - balanced stats, aura mechanics
 *       <br>Use: When creating roles that help the owner through healing/buffs</li>
 *   
 *   <li><b>MOBILITY</b> - Mobile roles focused on speed and exploration
 *       <br>Example: Scout, Skyrider - speed/agility scaling
 *       <br>Use: When creating roles that move fast and explore</li>
 *   
 *   <li><b>UTILITY</b> - Unique mechanic roles with specialized abilities
 *       <br>Example: Enchantment Bound, Cursed One - minimal stat scaling
 *       <br>Use: When creating roles that rely on unique abilities over stats</li>
 * </ul>
 * 
 * <h3>Standard Stat Affinity Tiers</h3>
 * <p>Use {@link StatAffinityTiers} for consistent stat bonuses:</p>
 * <ul>
 *   <li><b>PRIMARY (0.05)</b> - Main role identity stats</li>
 *   <li><b>SECONDARY_HIGH (0.04)</b> - Important supporting stats</li>
 *   <li><b>SECONDARY (0.03)</b> - Supporting stats</li>
 *   <li><b>TERTIARY (0.02)</b> - Minor bonuses</li>
 *   <li><b>LEARNING_HIGH (0.06)</b> - High intelligence roles (magic, utility)</li>
 *   <li><b>LEARNING_MEDIUM (0.04)</b> - Average intelligence</li>
 *   <li><b>LEARNING_LOW (0.02)</b> - Basic intelligence</li>
 *   <li><b>LEARNING_MINIMAL (0.01)</b> - Low intelligence (sleepy roles)</li>
 * </ul>
 * 
 * <h3>Creating New Roles</h3>
 * <p>Use {@link RoleDefinitionTemplate} factory methods to create new roles:</p>
 * <pre>{@code
 * // For a tank role:
 * PetRoleType myTank = new PetRoleType(
 *     myId,
 *     RoleDefinitionTemplate.tankTemplate(myId)
 *         .withStatAffinity("learning", StatAffinityTiers.LEARNING_MEDIUM)
 *         .withVisual(new Visual(0xFF0000, 0x880000, "my_ambient", "my_role"))
 *         .withPresentation(defaultPresentation(...))
 *         .build()
 * );
 * 
 * // For a DPS role:
 * PetRoleType myDps = new PetRoleType(
 *     myId,
 *     RoleDefinitionTemplate.dpsTemplate(myId)
 *         .withStatAffinity("learning", StatAffinityTiers.LEARNING_LOW)
 *         .withVisual(new Visual(0x00FF00, 0x008800, "my_ambient", "my_role"))
 *         .withPresentation(defaultPresentation(...))
 *         .build()
 * );
 * 
 * // For a utility role with custom mechanics:
 * PetRoleType myUtility = new PetRoleType(
 *     myId,
 *     RoleDefinitionTemplate.utilityTemplate(myId, "my_scalar")
 *         .withStatAffinity("specialStat", StatAffinityTiers.PRIMARY)
 *         .withStatAffinity("learning", StatAffinityTiers.LEARNING_HIGH)
 *         // Utility roles often need custom attribute scaling
 *         .withAttributeScaling(AttributeScaling.builder()
 *             .healthBonusPerLevel(0.01f)
 *             .speedBonusPerLevel(0.005f)
 *             .build())
 *         .withVisual(new Visual(0x0000FF, 0x000088, "my_ambient", "my_role"))
 *         .withPresentation(defaultPresentation(...))
 *         .validate()  // Optional: validate before building
 *         .build()
 * );
 * }</pre>
 * 
 * <h3>Best Practices</h3>
 * <ul>
 *   <li>Always use templates as starting points - they provide consistent baselines</li>
 *   <li>Use {@link StatAffinityTiers} constants instead of magic numbers</li>
 *   <li>Call {@code .validate()} before {@code .build()} during development</li>
 *   <li>Document why roles deviate from templates (e.g., Skyrider prioritizes agility over speed)</li>
 *   <li>Use {@link AttributeScalingPresets} when possible for consistent progression</li>
 * </ul>
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

    /**
     * Role archetypes categorize roles by their primary gameplay focus.
     * This helps standardize initialization patterns and stat distributions.
     */
    public enum RoleArchetype {
        /** Defensive roles focused on survival and protection (e.g., Guardian) */
        TANK,
        /** Offensive roles focused on damage output (e.g., Striker) */
        DPS,
        /** Supportive roles focused on healing and buffs (e.g., Support) */
        SUPPORT,
        /** Mobile roles focused on speed and exploration (e.g., Scout, Skyrider) */
        MOBILITY,
        /** Utility roles with unique mechanics (e.g., Enchantment Bound, Cursed One) */
        UTILITY
    }

    /**
     * Standard stat affinity tiers for consistent balancing.
     * Primary: Main role identity (0.05)
     * Secondary: Supporting stats (0.03-0.04)
     * Tertiary: Minor bonuses (0.02)
     * Learning: Variable based on role complexity (0.01-0.06)
     */
    public static final class StatAffinityTiers {
        private StatAffinityTiers() {
            throw new UnsupportedOperationException("Utility class - do not instantiate");
        }

        public static final float PRIMARY = 0.05f;
        public static final float SECONDARY_HIGH = 0.04f;
        public static final float SECONDARY = 0.03f;
        public static final float TERTIARY = 0.02f;
        public static final float LEARNING_HIGH = 0.06f;
        public static final float LEARNING_MEDIUM = 0.04f;
        public static final float LEARNING_LOW = 0.02f;
        public static final float LEARNING_MINIMAL = 0.01f;
    }

    /**
     * Pre-configured attribute scaling patterns for common role archetypes.
     * Reduces boilerplate and ensures consistent progression curves.
     */
    public static final class AttributeScalingPresets {
        private AttributeScalingPresets() {
            throw new UnsupportedOperationException("Utility class - do not instantiate");
        }

        /** Standard health scaling for tank roles */
        public static final AttributeScaling TANK_HEALTH_FOCUS = AttributeScaling.builder()
            .healthBonusPerLevel(0.02f)
            .healthPostSoftcapBonusPerLevel(0.01f)
            .healthSoftcapLevel(20)
            .healthMaxBonus(2.0f)
            .build();

        /** Standard attack scaling for DPS roles */
        public static final AttributeScaling DPS_ATTACK_FOCUS = AttributeScaling.builder()
            .attackBonusPerLevel(0.02f)
            .attackPostSoftcapBonusPerLevel(0.01f)
            .attackSoftcapLevel(15)
            .attackMaxBonus(1.5f)
            .build();

        /** Standard speed scaling for mobility roles */
        public static final AttributeScaling MOBILITY_SPEED_FOCUS = AttributeScaling.builder()
            .speedBonusPerLevel(0.01f)
            .speedMaxBonus(0.6f)
            .build();

        /** Balanced scaling for support roles (minor health boost) */
        public static final AttributeScaling SUPPORT_BALANCED = AttributeScaling.builder()
            .healthBonusPerLevel(0.01f)
            .healthPostSoftcapBonusPerLevel(0.005f)
            .healthSoftcapLevel(20)
            .healthMaxBonus(1.5f)
            .build();

    /** Minimal scaling for utility roles that rely on abilities */
        public static final AttributeScaling UTILITY_MINIMAL = AttributeScaling.DEFAULT;
    }

    /**
     * Template factory for creating role definitions with standardized patterns.
     * Reduces boilerplate and ensures consistency across role definitions.
     */
    public static final class RoleDefinitionTemplate {
        private RoleDefinitionTemplate() {
            throw new UnsupportedOperationException("Utility class - do not instantiate");
        }

        private static final float BASE_STAT_SCALAR = 0.02f;

        /**
         * Creates a pre-configured builder for tank archetype roles.
         * Defaults: Health primary affinity, defense scalar, tank health scaling.
         */
        public static DefinitionBuilder tankTemplate(Identifier roleId) {
            return builder(roleId)
                .withArchetype(RoleArchetype.TANK)
                .withBaseStatScalar("defense", BASE_STAT_SCALAR)
                .withStatAffinity("health", StatAffinityTiers.PRIMARY)
                .withStatAffinity("defense", StatAffinityTiers.PRIMARY)
                .withAttributeScaling(AttributeScalingPresets.TANK_HEALTH_FOCUS);
        }

        /**
         * Creates a pre-configured builder for DPS archetype roles.
         * Defaults: Attack primary affinity, offense scalar, DPS attack scaling.
         */
        public static DefinitionBuilder dpsTemplate(Identifier roleId) {
            return builder(roleId)
                .withArchetype(RoleArchetype.DPS)
                .withBaseStatScalar("offense", BASE_STAT_SCALAR)
                .withStatAffinity("attack", StatAffinityTiers.PRIMARY)
                .withStatAffinity("speed", StatAffinityTiers.SECONDARY)
                .withAttributeScaling(AttributeScalingPresets.DPS_ATTACK_FOCUS);
        }

        /**
         * Creates a pre-configured builder for support archetype roles.
         * Defaults: Vitality primary affinity, aura scalar, support balanced scaling.
         */
        public static DefinitionBuilder supportTemplate(Identifier roleId) {
            return builder(roleId)
                .withArchetype(RoleArchetype.SUPPORT)
                .withBaseStatScalar("aura", BASE_STAT_SCALAR)
                .withStatAffinity("vitality", StatAffinityTiers.PRIMARY)
                .withStatAffinity("health", StatAffinityTiers.SECONDARY)
                .withAttributeScaling(AttributeScalingPresets.SUPPORT_BALANCED);
        }

        /**
         * Creates a pre-configured builder for mobility archetype roles.
         * Defaults: Speed and agility primary affinities, mobility scalar, speed scaling.
         */
        public static DefinitionBuilder mobilityTemplate(Identifier roleId) {
            return builder(roleId)
                .withArchetype(RoleArchetype.MOBILITY)
                .withBaseStatScalar("mobility", BASE_STAT_SCALAR)
                .withStatAffinity("speed", StatAffinityTiers.PRIMARY)
                .withStatAffinity("agility", StatAffinityTiers.PRIMARY)
                .withAttributeScaling(AttributeScalingPresets.MOBILITY_SPEED_FOCUS);
        }

        /**
         * Creates a pre-configured builder for utility archetype roles.
         * Defaults: Varied stats, utility scalar, minimal attribute scaling.
         * Utility roles should customize heavily based on their unique mechanics.
         */
        public static DefinitionBuilder utilityTemplate(Identifier roleId, String utilityScalarKey) {
            return builder(roleId)
                .withArchetype(RoleArchetype.UTILITY)
                .withBaseStatScalar(utilityScalarKey, BASE_STAT_SCALAR)
                .withAttributeScaling(AttributeScalingPresets.UTILITY_MINIMAL);
        }
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
        RoleDefinitionTemplate.tankTemplate(GUARDIAN_ID)
            .withStatAffinity("learning", StatAffinityTiers.TERTIARY)
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
        RoleDefinitionTemplate.dpsTemplate(STRIKER_ID)
            .withStatAffinity("learning", StatAffinityTiers.SECONDARY)
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
        RoleDefinitionTemplate.supportTemplate(SUPPORT_ID)
            .withStatAffinity("learning", StatAffinityTiers.SECONDARY_HIGH)
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
        RoleDefinitionTemplate.mobilityTemplate(SCOUT_ID)
            .withStatAffinity("learning", StatAffinityTiers.PRIMARY)
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
        RoleDefinitionTemplate.mobilityTemplate(SKYRIDER_ID)
            // Note: Skyrider already gets speed + agility from mobility template
            // Override agility to PRIMARY and speed to SECONDARY for specialization
            .withStatAffinity("agility", StatAffinityTiers.PRIMARY)
            .withStatAffinity("speed", StatAffinityTiers.SECONDARY)
            .withStatAffinity("learning", StatAffinityTiers.SECONDARY)
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
        RoleDefinitionTemplate.utilityTemplate(ENCHANTMENT_BOUND_ID, "echo")
            .withStatAffinity("vitality", StatAffinityTiers.SECONDARY)
            .withStatAffinity("agility", StatAffinityTiers.SECONDARY)
            .withStatAffinity("learning", StatAffinityTiers.LEARNING_HIGH)
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
        RoleDefinitionTemplate.utilityTemplate(CURSED_ONE_ID, "curse")
            .withStatAffinity("attack", StatAffinityTiers.SECONDARY)
            .withStatAffinity("vitality", StatAffinityTiers.SECONDARY)
            .withStatAffinity("learning", StatAffinityTiers.SECONDARY_HIGH)
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
        RoleDefinitionTemplate.utilityTemplate(EEPY_EEPER_ID, "slumber")
            .withStatAffinity("health", StatAffinityTiers.SECONDARY)
            .withStatAffinity("vitality", StatAffinityTiers.PRIMARY)
            .withStatAffinity("learning", StatAffinityTiers.LEARNING_MINIMAL)
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
        RoleDefinitionTemplate.utilityTemplate(ECLIPSED_ID, "disruption")
            .withStatAffinity("speed", StatAffinityTiers.SECONDARY)
            .withStatAffinity("attack", StatAffinityTiers.SECONDARY)
            .withStatAffinity("learning", StatAffinityTiers.PRIMARY)
            .withVisual(new Visual(0x3D1F4B, 0x12061A, "eclipsed_ambient", "eclipsed"))
            .withPresentation(defaultPresentation(
                "eclipsed_void_pulse",
                "Reality flickers around your eclipsed companion",
                List.of(
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

    /** Role archetype classification for runtime queries. */
    public RoleArchetype archetype() {
        return definition.archetype();
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
        private RoleArchetype archetype = null;
        private final Map<String, Float> scalars = new LinkedHashMap<>();
        private final Map<String, Float> statAffinities = new LinkedHashMap<>();
        private List<Identifier> defaultAbilities = List.of();
        private XpCurve xpCurve = DEFAULT_XP_CURVE;
        private Visual visual = Visual.DEFAULT;
        private AttributeScaling attributeScaling = AttributeScaling.DEFAULT;
        private List<PassiveAura> passiveAuras = List.of();
        private SupportPotionBehavior supportPotionBehavior = null;
        private List<MilestoneAdvancement> milestoneAdvancements = List.of();
        private Presentation presentation = Presentation.DEFAULT;
        private Map<Integer, List<LevelReward>> levelRewards = Map.of();

        DefinitionBuilder(Identifier id, String translationKey) {
            this.id = id;
            this.translationKey = translationKey;
        }

        public DefinitionBuilder withTranslationKey(String translationKey) {
            this.translationKey = translationKey;
            return this;
        }

        /**
         * Sets the archetype classification for this role.
         * Useful for runtime queries and filtering.
         * 
         * @param archetype the role archetype
         * @return this builder for chaining
         */
        public DefinitionBuilder withArchetype(RoleArchetype archetype) {
            this.archetype = archetype;
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

        /**
         * Convenience method to set multiple stat affinities at once.
         * Useful for roles with many affinities.
         * 
         * @param affinities map of stat name to affinity value
         * @return this builder for chaining
         */
        public DefinitionBuilder withStatAffinities(Map<String, Float> affinities) {
            if (affinities != null) {
                this.statAffinities.putAll(affinities);
            }
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
        
        public DefinitionBuilder withLevelRewards(Map<Integer, List<LevelReward>> levelRewards) {
            this.levelRewards = levelRewards == null ? Map.of() : levelRewards;
            return this;
        }

        /**
         * Validates the current builder configuration for common issues.
         * This helps catch misconfigurations early during development.
         * 
         * @return this builder for chaining
         * @throws IllegalStateException if critical validation fails
         */
        public DefinitionBuilder validate() {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            // Critical: Check for at least one stat affinity
            if (statAffinities.isEmpty()) {
                errors.add("No stat affinities defined - role will have no characteristic bonuses");
            } else {
                // Validate stat affinity values are reasonable
                for (Map.Entry<String, Float> entry : statAffinities.entrySet()) {
                    if (entry.getValue() < 0 || entry.getValue() > 0.1f) {
                        warnings.add(String.format(
                            "Unusual stat affinity '%s': %.3f (expected 0.01-0.06)",
                            entry.getKey(), entry.getValue()
                        ));
                    }
                }
            }

            // Critical: Check for base stat scalar
            if (scalars.isEmpty()) {
                errors.add("No base stat scalars defined - role will have no role-specific stat bonuses");
            } else {
                // Validate scalar values are reasonable
                for (Map.Entry<String, Float> entry : scalars.entrySet()) {
                    if (entry.getValue() < 0 || entry.getValue() > 0.1f) {
                        warnings.add(String.format(
                            "Unusual base scalar '%s': %.3f (expected around 0.02)",
                            entry.getKey(), entry.getValue()
                        ));
                    }
                }
            }

            // Critical: Check for visual data completeness
            if (visual == Visual.DEFAULT || visual == null) {
                errors.add("Visual data not set - role will use default colors and no particle effects");
            }

            // Critical: Check for presentation data
            if (presentation == Presentation.DEFAULT || presentation == null) {
                errors.add("Presentation data not set - role will have no petting feedback or epithets");
            }

            // Warning: Check attribute scaling patterns
            boolean hasHealthScaling = attributeScaling != null && attributeScaling.healthBonusPerLevel() > 0;
            boolean hasAttackScaling = attributeScaling != null && attributeScaling.attackBonusPerLevel() > 0;
            boolean hasSpeedScaling = attributeScaling != null && attributeScaling.speedBonusPerLevel() > 0;
            
            if (!hasHealthScaling && !hasAttackScaling && !hasSpeedScaling) {
                warnings.add("No attribute scaling configured - role relies purely on abilities");
            }

            // Warning: Check for translation key
            if (translationKey == null || translationKey.isBlank()) {
                warnings.add("No translation key set - using default from role ID");
            }

            // Log warnings
            if (!warnings.isEmpty()) {
                Petsplus.LOGGER.warn("Role '{}' validation warnings:\n  - {}",
                    id, String.join("\n  - ", warnings));
            }

            // Throw on errors
            if (!errors.isEmpty()) {
                String message = "Role definition validation failed for '" + id + "':\n  - " 
                    + String.join("\n  - ", errors);
                throw new IllegalStateException(message);
            }

            return this;
        }

        public Definition build() {
            return new Definition(
                id,
                translationKey,
                archetype,
                scalars,
                defaultAbilities,
                xpCurve,
                visual,
                statAffinities,
                attributeScaling,
                passiveAuras,
                supportPotionBehavior,
                milestoneAdvancements,
                presentation,
                levelRewards
            );
        }
    }

    /** Fully describes the data-driven definition for a role. */
    public record Definition(
        Identifier id,
        String translationKey,
        RoleArchetype archetype,
        Map<String, Float> baseStatScalars,
        List<Identifier> defaultAbilities,
        XpCurve xpCurve,
        Visual visual,
        Map<String, Float> statAffinities,
        AttributeScaling attributeScaling,
        List<PassiveAura> passiveAuras,
        SupportPotionBehavior supportPotionBehavior,
        List<MilestoneAdvancement> milestoneAdvancements,
        Presentation presentation,
        Map<Integer, List<LevelReward>> levelRewards
    ) {
        public Definition {
            Objects.requireNonNull(id, "id");
            translationKey = Objects.requireNonNullElseGet(translationKey, () ->
                Petsplus.MOD_ID + ".role." + id.getPath()
            );
            baseStatScalars = Collections.unmodifiableMap(new LinkedHashMap<>(baseStatScalars));
            defaultAbilities = List.copyOf(defaultAbilities);
            xpCurve = Objects.requireNonNullElse(xpCurve, DEFAULT_XP_CURVE);
            visual = Objects.requireNonNullElse(visual, Visual.DEFAULT);
            statAffinities = Collections.unmodifiableMap(new LinkedHashMap<>(statAffinities));
            attributeScaling = Objects.requireNonNullElse(attributeScaling, AttributeScaling.DEFAULT);
            passiveAuras = List.copyOf(passiveAuras == null ? List.of() : passiveAuras);
            supportPotionBehavior = supportPotionBehavior;
            milestoneAdvancements = List.copyOf(milestoneAdvancements == null ? List.of() : milestoneAdvancements);
            presentation = Objects.requireNonNullElse(presentation, Presentation.DEFAULT);
            levelRewards = Collections.unmodifiableMap(
                levelRewards == null ? Map.of() :
                levelRewards.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.copyOf(e.getValue()),
                        (a, b) -> a,
                        LinkedHashMap::new
                    ))
            );
        }
        
        /**
         * Get the rewards for a specific level.
         */
        public List<LevelReward> getRewardsForLevel(int level) {
            return levelRewards.getOrDefault(level, List.of());
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
