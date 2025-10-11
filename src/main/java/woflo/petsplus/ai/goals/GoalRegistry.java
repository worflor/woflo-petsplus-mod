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
    private static final Set<Identifier> DATA_DRIVEN = new HashSet<>();

    public static final GoalDefinition STRETCH_AND_YAW = register(new GoalDefinition(
        id("stretch_and_yawn"),
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

    public static final GoalDefinition CIRCLE_SPOT = register(new GoalDefinition(
        id("circle_spot"),
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

    public static final GoalDefinition TAIL_CHASE = register(new GoalDefinition(
        id("tail_chase"),
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

    public static final GoalDefinition SNIFF_GROUND = register(new GoalDefinition(
        id("sniff_ground"),
        Category.IDLE_QUIRK,
        28,
        12,
        100,
        caps -> caps.canWander() && caps.prefersLand(),
        new Vec2f(0.3f, 0.7f),
        IdleStaminaBias.CENTERED,
        false,
        SniffGroundGoal::new
    ));

    public static final GoalDefinition POUNCE_PRACTICE = register(new GoalDefinition(
        id("pounce_practice"),
        Category.IDLE_QUIRK,
        28,
        10,
        150,
        caps -> caps.canJump() && caps.prefersLand(),
        new Vec2f(0.6f, 1.0f),
        IdleStaminaBias.HIGH,
        false,
        PouncePracticeGoal::new
    ));

    public static final GoalDefinition PERK_EARS_SCAN = register(new GoalDefinition(
        id("perk_ears_scan"),
        Category.IDLE_QUIRK,
        28,
        8,
        90,
        caps -> caps.canMakeSound() && caps.prefersLand(),
        new Vec2f(0.3f, 0.7f),
        IdleStaminaBias.CENTERED,
        false,
        PerkEarsScanGoal::new
    ));

    public static final GoalDefinition SIT_SPHINX_POSE = register(new GoalDefinition(
        id("sit_sphinx_pose"),
        Category.IDLE_QUIRK,
        28,
        15,
        180,
        caps -> caps.canSit(),
        new Vec2f(0.0f, 0.4f),
        IdleStaminaBias.LOW,
        false,
        SitSphinxPoseGoal::new
    ));

    public static final GoalDefinition PREEN_FEATHERS = register(new GoalDefinition(
        id("preen_feathers"),
        Category.IDLE_QUIRK,
        28,
        10,
        150,
        caps -> caps.canFly(),
        new Vec2f(0.0f, 0.4f),
        IdleStaminaBias.LOW,
        false,
        PreenFeathersGoal::new
    ));

    public static final GoalDefinition WING_FLUTTER = register(new GoalDefinition(
        id("wing_flutter"),
        Category.IDLE_QUIRK,
        28,
        12,
        120,
        caps -> caps.canFly(),
        new Vec2f(0.3f, 0.7f),
        IdleStaminaBias.HIGH,
        false,
        WingFlutterGoal::new
    ));

    public static final GoalDefinition PERCH_HOP = register(new GoalDefinition(
        id("perch_hop"),
        Category.IDLE_QUIRK,
        27,
        8,
        100,
        caps -> caps.canFly(),
        new Vec2f(0.6f, 1.0f),
        IdleStaminaBias.HIGH,
        true,
        PerchHopGoal::new
    ));

    public static final GoalDefinition FLOAT_IDLE = register(new GoalDefinition(
        id("float_idle"),
        Category.IDLE_QUIRK,
        28,
        8,
        100,
        caps -> caps.canSwim() && caps.prefersWater(),
        new Vec2f(0.0f, 0.4f),
        IdleStaminaBias.LOW,
        false,
        FloatIdleGoal::new
    ));

    public static final GoalDefinition BUBBLE_PLAY = register(new GoalDefinition(
        id("bubble_play"),
        Category.IDLE_QUIRK,
        28,
        10,
        120,
        caps -> caps.canSwim() && caps.prefersWater(),
        new Vec2f(0.6f, 1.0f),
        IdleStaminaBias.HIGH,
        true,
        BubblePlayGoal::new
    ));

    public static final GoalDefinition SURFACE_BREATH = register(new GoalDefinition(
        id("surface_breath"),
        Category.IDLE_QUIRK,
        27,
        5,
        80,
        caps -> caps.canSwim() && caps.prefersWater(),
        new Vec2f(0.0f, 1.0f),
        IdleStaminaBias.LOW,
        false,
        SurfaceBreathGoal::new
    ));

    public static final GoalDefinition CASUAL_WANDER = register(new GoalDefinition(
        id("casual_wander"),
        Category.WANDER,
        20,
        0,
        0,
        caps -> caps.canWander(),
        new Vec2f(0.3f, 0.7f),
        IdleStaminaBias.NONE,
        false,
        CasualWanderGoal::new
    ));

    public static final GoalDefinition AERIAL_PATROL = register(new GoalDefinition(
        id("aerial_patrol"),
        Category.WANDER,
        20,
        0,
        0,
        caps -> caps.canFly(),
        new Vec2f(0.3f, 0.7f),
        IdleStaminaBias.NONE,
        false,
        AerialPatrolGoal::new
    ));

    public static final GoalDefinition WATER_CRUISE = register(new GoalDefinition(
        id("water_cruise"),
        Category.WANDER,
        20,
        0,
        0,
        caps -> caps.canSwim() && caps.prefersWater(),
        new Vec2f(0.3f, 0.7f),
        IdleStaminaBias.NONE,
        false,
        WaterCruiseGoal::new
    ));

    public static final GoalDefinition SCENT_TRAIL_FOLLOW = register(new GoalDefinition(
        id("scent_trail_follow"),
        Category.WANDER,
        20,
        0,
        0,
        caps -> caps.canWander() && caps.prefersLand(),
        new Vec2f(0.3f, 0.7f),
        IdleStaminaBias.NONE,
        false,
        ScentTrailFollowGoal::new
    ));

    public static final GoalDefinition PURPOSEFUL_PATROL = register(new GoalDefinition(
        id("purposeful_patrol"),
        Category.WANDER,
        20,
        0,
        0,
        caps -> caps.canWander(),
        new Vec2f(0.6f, 1.0f),
        IdleStaminaBias.NONE,
        false,
        PurposefulPatrolGoal::new
    ));

    public static final GoalDefinition OWNER_ORBIT = register(new GoalDefinition(
        id("owner_orbit"),
        Category.WANDER,
        20,
        0,
        0,
        caps -> caps.hasOwner(),
        new Vec2f(0.0f, 1.0f),
        IdleStaminaBias.NONE,
        false,
        OwnerOrbitGoal::new
    ));

    public static final GoalDefinition TOY_POUNCE = register(new GoalDefinition(
        id("toy_pounce"),
        Category.PLAY,
        18,
        0,
        0,
        caps -> caps.canJump(),
        new Vec2f(0.6f, 1.0f),
        IdleStaminaBias.NONE,
        false,
        ToyPounceGoal::new
    ));

    public static final GoalDefinition PARKOUR_CHALLENGE = register(new GoalDefinition(
        id("parkour_challenge"),
        Category.PLAY,
        18,
        0,
        0,
        caps -> caps.canJump() && caps.canWander(),
        new Vec2f(0.6f, 1.0f),
        IdleStaminaBias.NONE,
        false,
        ParkourChallengeGoal::new
    ));

    public static final GoalDefinition FETCH_ITEM = register(new GoalDefinition(
        id("fetch_item"),
        Category.PLAY,
        18,
        0,
        0,
        caps -> caps.canPickUpItems() && caps.hasOwner(),
        new Vec2f(0.6f, 1.0f),
        IdleStaminaBias.NONE,
        false,
        FetchItemGoal::new
    ));

    public static final GoalDefinition DIVE_PLAY = register(new GoalDefinition(
        id("dive_play"),
        Category.PLAY,
        18,
        0,
        0,
        caps -> caps.canSwim(),
        new Vec2f(0.6f, 1.0f),
        IdleStaminaBias.NONE,
        false,
        DivePlayGoal::new
    ));

    public static final GoalDefinition AERIAL_ACROBATICS = register(new GoalDefinition(
        id("aerial_acrobatics"),
        Category.PLAY,
        18,
        0,
        0,
        caps -> caps.canFly(),
        new Vec2f(0.6f, 1.0f),
        IdleStaminaBias.NONE,
        false,
        AerialAcrobaticsGoal::new
    ));

    public static final GoalDefinition WATER_SPLASH = register(new GoalDefinition(
        id("water_splash"),
        Category.PLAY,
        18,
        0,
        0,
        caps -> caps.canSwim() && caps.canWander(),
        new Vec2f(0.6f, 1.0f),
        IdleStaminaBias.NONE,
        false,
        WaterSplashGoal::new
    ));

    public static final GoalDefinition LEAN_AGAINST_OWNER = register(new GoalDefinition(
        id("lean_against_owner"),
        Category.SOCIAL,
        15,
        0,
        0,
        caps -> caps.hasOwner() && caps.canWander(),
        new Vec2f(0.0f, 0.6f),
        IdleStaminaBias.NONE,
        false,
        LeanAgainstOwnerGoal::new
    ));

    public static final GoalDefinition PARALLEL_PLAY = register(new GoalDefinition(
        id("parallel_play"),
        Category.SOCIAL,
        15,
        0,
        0,
        caps -> caps.hasOwner(),
        new Vec2f(0.3f, 0.8f),
        IdleStaminaBias.NONE,
        false,
        ParallelPlayGoal::new
    ));

    public static final GoalDefinition SHOW_OFF_TRICK = register(new GoalDefinition(
        id("show_off_trick"),
        Category.SOCIAL,
        15,
        0,
        0,
        caps -> caps.hasOwner(),
        new Vec2f(0.6f, 1.0f),
        IdleStaminaBias.NONE,
        false,
        ShowOffTrickGoal::new
    ));

    public static final GoalDefinition GIFT_BRINGING = register(new GoalDefinition(
        id("gift_bringing"),
        Category.SOCIAL,
        15,
        0,
        0,
        caps -> caps.hasOwner() && caps.canPickUpItems(),
        new Vec2f(0.3f, 0.7f),
        IdleStaminaBias.NONE,
        false,
        GiftBringingGoal::new
    ));

    public static final GoalDefinition PERCH_ON_SHOULDER = register(new GoalDefinition(
        id("perch_on_shoulder"),
        Category.SOCIAL,
        15,
        0,
        0,
        caps -> caps.hasOwner() && caps.canFly() && caps.isSmallSize(),
        new Vec2f(0.0f, 0.7f),
        IdleStaminaBias.NONE,
        false,
        PerchOnShoulderGoal::new
    ));

    public static final GoalDefinition ORBIT_SWIM = register(new GoalDefinition(
        id("orbit_swim"),
        Category.SOCIAL,
        15,
        0,
        0,
        caps -> caps.hasOwner() && caps.canSwim(),
        new Vec2f(0.3f, 0.8f),
        IdleStaminaBias.NONE,
        false,
        OrbitSwimGoal::new
    ));

    public static final GoalDefinition EYE_CONTACT = register(new GoalDefinition(
        id("eye_contact"),
        Category.SOCIAL,
        25,
        0,
        0,
        caps -> caps.hasOwner(),
        new Vec2f(0.0f, 1.0f),
        IdleStaminaBias.NONE,
        false,
        EyeContactGoal::new
    ));

    public static final GoalDefinition CROUCH_APPROACH_RESPONSE = register(new GoalDefinition(
        id("crouch_approach_response"),
        Category.SOCIAL,
        12,
        100,
        300,
        caps -> caps.hasOwner(),
        new Vec2f(0.3f, 0.9f),
        IdleStaminaBias.NONE,
        false,
        CrouchApproachResponseGoal::new
    ));

    public static final GoalDefinition HIDE_AND_SEEK = register(new GoalDefinition(
        id("hide_and_seek"),
        Category.SPECIAL,
        16,
        0,
        0,
        caps -> caps.hasOwner() && caps.canWander(),
        new Vec2f(0.6f, 1.0f),
        IdleStaminaBias.NONE,
        false,
        HideAndSeekGoal::new
    ));

    public static final GoalDefinition INVESTIGATE_BLOCK = register(new GoalDefinition(
        id("investigate_block"),
        Category.SPECIAL,
        16,
        0,
        0,
        caps -> caps.canWander(),
        new Vec2f(0.3f, 0.7f),
        IdleStaminaBias.NONE,
        false,
        InvestigateBlockGoal::new
    ));

    public static final GoalDefinition STARGAZING = register(new GoalDefinition(
        id("stargazing"),
        Category.SPECIAL,
        16,
        0,
        0,
        caps -> caps.canSit(),
        new Vec2f(0.0f, 0.5f),
        IdleStaminaBias.NONE,
        false,
        StargazingGoal::new
    ));

    private GoalRegistry() {}

    private static Identifier id(String path) {
        return Identifier.of("petsplus", path);
    }

    public static GoalDefinition register(GoalDefinition definition) {
        return registerInternal(definition, false, false);
    }

    public static GoalDefinition registerDataDriven(GoalDefinition definition) {
        return registerInternal(definition, true, true);
    }

    public static void clearDataDriven() {
        if (DATA_DRIVEN.isEmpty()) {
            return;
        }
        for (Identifier id : new ArrayList<>(DATA_DRIVEN)) {
            DEFINITIONS.remove(id);
        }
        DATA_DRIVEN.clear();
    }

    private static GoalDefinition registerInternal(GoalDefinition definition, boolean dataDriven, boolean replace) {
        Identifier id = definition.id();
        if (!replace && DEFINITIONS.containsKey(id)) {
            throw new IllegalStateException("Duplicate goal id registered: " + id);
        }
        if (replace) {
            if (DEFINITIONS.containsKey(id) && !DATA_DRIVEN.contains(id) && dataDriven) {
                throw new IllegalStateException("Cannot replace built-in goal definition: " + id);
            }
            if (DEFINITIONS.containsKey(id)) {
                DEFINITIONS.remove(id);
                DATA_DRIVEN.remove(id);
            }
        }
        DEFINITIONS.put(id, definition);
        if (dataDriven) {
            DATA_DRIVEN.add(id);
        } else {
            DATA_DRIVEN.remove(id);
        }
        return definition;
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

