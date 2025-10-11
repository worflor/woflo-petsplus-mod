package woflo.petsplus.ai.goals;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.goals.GoalDefinition.Category;
import woflo.petsplus.ai.goals.GoalDefinition.IdleStaminaBias;
import woflo.petsplus.ai.goals.idle.*;
import woflo.petsplus.ai.goals.play.*;
import woflo.petsplus.ai.goals.social.*;
import woflo.petsplus.ai.goals.special.*;
import woflo.petsplus.ai.goals.wander.*;

import java.util.*;

/**
 * Central registry for adaptive AI goal definitions.
 */
public final class GoalRegistry {

    private static final Map<Identifier, GoalDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<Identifier, GoalDefinition> BUILT_IN_DEFAULTS = new LinkedHashMap<>();
    private static final Set<Identifier> DATA_DRIVEN = new HashSet<>();

    static {
        registerBuiltIn(new GoalDefinition(
            GoalIds.STRETCH_AND_YAW,
            Category.IDLE_QUIRK,
            28,
            5,
            80,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.LOW,
            false,
            StretchAndYawnGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.CIRCLE_SPOT,
            Category.IDLE_QUIRK,
            28,
            8,
            120,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.CENTERED,
            false,
            CircleSpotGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.TAIL_CHASE,
            Category.IDLE_QUIRK,
            28,
            15,
            200,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.HIGH,
            true,
            TailChaseGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.SNIFF_GROUND,
            Category.IDLE_QUIRK,
            28,
            12,
            100,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
                MobCapabilities.CapabilityRequirement.fromToken("prefers_land")
            )),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.CENTERED,
            false,
            SniffGroundGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.POUNCE_PRACTICE,
            Category.IDLE_QUIRK,
            28,
            10,
            150,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_jump"),
                MobCapabilities.CapabilityRequirement.fromToken("prefers_land")
            )),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.HIGH,
            false,
            PouncePracticeGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PERK_EARS_SCAN,
            Category.IDLE_QUIRK,
            28,
            8,
            90,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_make_sound"),
                MobCapabilities.CapabilityRequirement.fromToken("prefers_land")
            )),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.CENTERED,
            false,
            PerkEarsScanGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.SIT_SPHINX_POSE,
            Category.IDLE_QUIRK,
            28,
            15,
            180,
            MobCapabilities.CapabilityRequirement.fromToken("can_sit"),
            new Vec2f(0.0f, 0.4f),
            IdleStaminaBias.LOW,
            false,
            SitSphinxPoseGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PREEN_FEATHERS,
            Category.IDLE_QUIRK,
            28,
            10,
            150,
            MobCapabilities.CapabilityRequirement.fromToken("can_fly"),
            new Vec2f(0.0f, 0.4f),
            IdleStaminaBias.LOW,
            false,
            PreenFeathersGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.WING_FLUTTER,
            Category.IDLE_QUIRK,
            28,
            12,
            120,
            MobCapabilities.CapabilityRequirement.fromToken("can_fly"),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.HIGH,
            false,
            WingFlutterGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PERCH_HOP,
            Category.IDLE_QUIRK,
            27,
            8,
            100,
            MobCapabilities.CapabilityRequirement.fromToken("can_fly"),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.HIGH,
            true,
            PerchHopGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.FLOAT_IDLE,
            Category.IDLE_QUIRK,
            28,
            8,
            100,
            MobCapabilities.CapabilityRequirement.aquatic(),
            new Vec2f(0.0f, 0.4f),
            IdleStaminaBias.LOW,
            false,
            FloatIdleGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.BUBBLE_PLAY,
            Category.IDLE_QUIRK,
            28,
            10,
            120,
            MobCapabilities.CapabilityRequirement.aquatic(),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.HIGH,
            true,
            BubblePlayGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.SURFACE_BREATH,
            Category.IDLE_QUIRK,
            27,
            5,
            80,
            MobCapabilities.CapabilityRequirement.aquatic(),
            new Vec2f(0.0f, 1.0f),
            IdleStaminaBias.LOW,
            false,
            SurfaceBreathGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.CASUAL_WANDER,
            Category.WANDER,
            20,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            CasualWanderGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.AERIAL_PATROL,
            Category.WANDER,
            20,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_fly"),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            AerialPatrolGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.WATER_CRUISE,
            Category.WANDER,
            20,
            0,
            0,
            MobCapabilities.CapabilityRequirement.aquatic(),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            WaterCruiseGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.SCENT_TRAIL_FOLLOW,
            Category.WANDER,
            20,
            0,
            0,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
                MobCapabilities.CapabilityRequirement.fromToken("prefers_land")
            )),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            ScentTrailFollowGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PURPOSEFUL_PATROL,
            Category.WANDER,
            20,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            PurposefulPatrolGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.OWNER_ORBIT,
            Category.WANDER,
            20,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.0f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            OwnerOrbitGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.TOY_POUNCE,
            Category.PLAY,
            18,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_jump"),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            ToyPounceGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PARKOUR_CHALLENGE,
            Category.PLAY,
            18,
            0,
            0,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_jump"),
                MobCapabilities.CapabilityRequirement.fromToken("can_wander")
            )),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            ParkourChallengeGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.FETCH_ITEM,
            Category.PLAY,
            18,
            0,
            0,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_pick_up_items"),
                MobCapabilities.CapabilityRequirement.fromToken("has_owner")
            )),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            FetchItemGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.DIVE_PLAY,
            Category.PLAY,
            18,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_swim"),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            DivePlayGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.AERIAL_ACROBATICS,
            Category.PLAY,
            18,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_fly"),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            AerialAcrobaticsGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.WATER_SPLASH,
            Category.PLAY,
            18,
            0,
            0,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_swim"),
                MobCapabilities.CapabilityRequirement.fromToken("can_wander")
            )),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            WaterSplashGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.LEAN_AGAINST_OWNER,
            Category.SOCIAL,
            15,
            0,
            0,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
                MobCapabilities.CapabilityRequirement.fromToken("can_wander")
            )),
            new Vec2f(0.0f, 0.6f),
            IdleStaminaBias.NONE,
            false,
            LeanAgainstOwnerGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PARALLEL_PLAY,
            Category.SOCIAL,
            15,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.3f, 0.8f),
            IdleStaminaBias.NONE,
            false,
            ParallelPlayGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.SHOW_OFF_TRICK,
            Category.SOCIAL,
            15,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            ShowOffTrickGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.GIFT_BRINGING,
            Category.SOCIAL,
            15,
            0,
            0,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
                MobCapabilities.CapabilityRequirement.fromToken("can_pick_up_items")
            )),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            GiftBringingGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PERCH_ON_SHOULDER,
            Category.SOCIAL,
            15,
            0,
            0,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
                MobCapabilities.CapabilityRequirement.fromToken("can_fly"),
                MobCapabilities.CapabilityRequirement.fromToken("is_small_size")
            )),
            new Vec2f(0.0f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            PerchOnShoulderGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.ORBIT_SWIM,
            Category.SOCIAL,
            15,
            0,
            0,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
                MobCapabilities.CapabilityRequirement.fromToken("can_swim")
            )),
            new Vec2f(0.3f, 0.8f),
            IdleStaminaBias.NONE,
            false,
            OrbitSwimGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.EYE_CONTACT,
            Category.SOCIAL,
            25,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.0f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            EyeContactGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.CROUCH_APPROACH_RESPONSE,
            Category.SOCIAL,
            12,
            100,
            300,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.3f, 0.9f),
            IdleStaminaBias.NONE,
            false,
            CrouchApproachResponseGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.HIDE_AND_SEEK,
            Category.SPECIAL,
            16,
            0,
            0,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
                MobCapabilities.CapabilityRequirement.fromToken("can_wander")
            )),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            HideAndSeekGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.INVESTIGATE_BLOCK,
            Category.SPECIAL,
            16,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            InvestigateBlockGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.STARGAZING,
            Category.SPECIAL,
            16,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_sit"),
            new Vec2f(0.0f, 0.5f),
            IdleStaminaBias.NONE,
            false,
            StargazingGoal::new
        ));
    }

    private GoalRegistry() {
    }

    private static void registerBuiltIn(GoalDefinition definition) {
        Identifier id = definition.id();
        if (DEFINITIONS.containsKey(id)) {
            throw new IllegalStateException("Duplicate goal id registered: " + id);
        }
        DEFINITIONS.put(id, definition);
        BUILT_IN_DEFAULTS.put(id, definition);
        DATA_DRIVEN.remove(id);
    }

    public static GoalDefinition register(GoalDefinition definition) {
        Identifier id = definition.id();
        if (DEFINITIONS.containsKey(id)) {
            throw new IllegalStateException("Duplicate goal id registered: " + id);
        }
        DEFINITIONS.put(id, definition);
        BUILT_IN_DEFAULTS.putIfAbsent(id, definition);
        DATA_DRIVEN.remove(id);
        return definition;
    }

    public static GoalDefinition registerDataDriven(GoalDefinition definition) {
        Identifier id = definition.id();
        DEFINITIONS.put(id, definition);
        DATA_DRIVEN.add(id);
        return definition;
    }

    public static void clearDataDriven() {
        if (DATA_DRIVEN.isEmpty()) {
            return;
        }
        for (Identifier id : new ArrayList<>(DATA_DRIVEN)) {
            GoalDefinition fallback = BUILT_IN_DEFAULTS.get(id);
            if (fallback != null) {
                DEFINITIONS.put(id, fallback);
            } else {
                DEFINITIONS.remove(id);
            }
        }
        DATA_DRIVEN.clear();
    }

    public static Collection<GoalDefinition> all() {
        return Collections.unmodifiableCollection(DEFINITIONS.values());
    }

    public static Optional<GoalDefinition> get(Identifier id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static GoalDefinition require(Identifier id) {
        GoalDefinition definition = DEFINITIONS.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown goal id: " + id);
        }
        return definition;
    }
}
