package woflo.petsplus.ai.goals;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.goals.GoalDefinition.Category;
import woflo.petsplus.ai.goals.GoalDefinition.IdleStaminaBias;
import woflo.petsplus.ai.goals.idle.*;
import woflo.petsplus.ai.goals.follow.*;
import woflo.petsplus.ai.goals.play.*;
import woflo.petsplus.ai.goals.social.*;
import woflo.petsplus.ai.goals.special.*;
import woflo.petsplus.ai.goals.wander.*;
// Species tags: multi-tag gating (new environmental goals)
import woflo.petsplus.ai.goals.environmental.*;
// Subtle behavior: P1 (world interactions)
import woflo.petsplus.ai.goals.world.*;
// Combat goals
import woflo.petsplus.ai.goals.combat.SelfPreservationGoal;
// Special goals
import woflo.petsplus.ai.goals.special.CuriosityGoal;
// Idle goals
import woflo.petsplus.ai.goals.idle.PerformAmbientAnimationGoal;
// Play goals
import woflo.petsplus.ai.goals.play.SimpleFetchItemGoal;

import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Central registry for adaptive AI goal definitions.
 */
public final class GoalRegistry {

    private static final Map<Identifier, GoalDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<Identifier, GoalDefinition> BUILT_IN_DEFAULTS = new LinkedHashMap<>();
    private static final Set<Identifier> DATA_DRIVEN = new HashSet<>();
    private static final Map<Identifier, GoalMovementConfig> MOVEMENT_CONFIGS = new HashMap<>();
    private static final Map<Identifier, GoalMovementConfig> BUILT_IN_MOVEMENT_CONFIGS = new HashMap<>();

    private static int secondsToTicks(int seconds) {
        return Math.max(0, seconds) * 20;
    }

    static {
        registerBuiltIn(new GoalDefinition(
            GoalIds.FOLLOW_OWNER,
            Category.SPECIAL,
            5,
            0,
            0,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
                MobCapabilities.CapabilityRequirement.fromToken("can_wander")
            )),
            new Vec2f(0.0f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            false,
            FollowOwnerAdaptiveGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.MAINTAIN_PACK_SPACING,
            Category.SOCIAL,
            6,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.0f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            false,
            MaintainPackSpacingGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.HESITATE_IN_COMBAT,
            Category.SOCIAL,
            5,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.0f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            false,
            HesitateInCombatGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.SHELTER_FROM_RAIN,
            Category.SPECIAL,
            4,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.0f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            false,
            ShelterFromRainGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.OWNER_ASSIST_ATTACK,
            Category.SPECIAL,
            4,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.0f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            true,
            OwnerAssistAttackGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.STRETCH_AND_YAW,
            Category.IDLE_QUIRK,
            28,
            25,
            160,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.LOW,
            false,
            false,
            StretchAndYawnGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.CIRCLE_SPOT,
            Category.IDLE_QUIRK,
            28,
            45,
            200,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.CENTERED,
            false,
            false,
            CircleSpotGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.TAIL_CHASE,
            Category.IDLE_QUIRK,
            28,
            60,
            240,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.HIGH,
            true,
            false,
            TailChaseGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.SNIFF_GROUND,
            Category.IDLE_QUIRK,
            28,
            40,
            180,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
                MobCapabilities.CapabilityRequirement.fromToken("prefers_land")
            )),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.CENTERED,
            false,
            false,
            SniffGroundGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.POUNCE_PRACTICE,
            Category.IDLE_QUIRK,
            28,
            50,
            220,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_jump"),
                MobCapabilities.CapabilityRequirement.fromToken("prefers_land")
            )),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.HIGH,
            false,
            false,
            PouncePracticeGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PERK_EARS_SCAN,
            Category.IDLE_QUIRK,
            28,
            35,
            170,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_make_sound"),
                MobCapabilities.CapabilityRequirement.fromToken("prefers_land")
            )),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.CENTERED,
            false,
            false,
            PerkEarsScanGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.SIT_SPHINX_POSE,
            Category.IDLE_QUIRK,
            28,
            40,
            240,
            MobCapabilities.CapabilityRequirement.fromToken("can_sit"),
            new Vec2f(0.0f, 0.4f),
            IdleStaminaBias.LOW,
            false,
            false,
            SitSphinxPoseGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PREEN_FEATHERS,
            Category.IDLE_QUIRK,
            28,
            35,
            220,
            MobCapabilities.CapabilityRequirement.fromToken("can_fly"),
            new Vec2f(0.0f, 0.4f),
            IdleStaminaBias.LOW,
            false,
            false,
            PreenFeathersGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.WING_FLUTTER,
            Category.IDLE_QUIRK,
            28,
            40,
            200,
            MobCapabilities.CapabilityRequirement.fromToken("can_fly"),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.HIGH,
            false,
            false,
            WingFlutterGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PERCH_HOP,
            Category.IDLE_QUIRK,
            27,
            35,
            180,
            MobCapabilities.CapabilityRequirement.fromToken("can_fly"),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.HIGH,
            true,
            false,
            PerchHopGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.FLOAT_IDLE,
            Category.IDLE_QUIRK,
            28,
            35,
            180,
            MobCapabilities.CapabilityRequirement.aquatic(),
            new Vec2f(0.0f, 0.4f),
            IdleStaminaBias.LOW,
            false,
            false,
            FloatIdleGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.BUBBLE_PLAY,
            Category.IDLE_QUIRK,
            28,
            40,
            200,
            MobCapabilities.CapabilityRequirement.aquatic(),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.HIGH,
            true,
            false,
            BubblePlayGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.SURFACE_BREATH,
            Category.IDLE_QUIRK,
            27,
            25,
            160,
            MobCapabilities.CapabilityRequirement.aquatic(),
            new Vec2f(0.0f, 1.0f),
            IdleStaminaBias.LOW,
            false,
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
            false,
            WaterCruiseGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.SCENT_TRAIL_FOLLOW,
            Category.WANDER,
            20,
            secondsToTicks(4),
            secondsToTicks(10),
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
                MobCapabilities.CapabilityRequirement.fromToken("prefers_land")
            )),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.NONE,
            false,
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
            false,
            PurposefulPatrolGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.OWNER_ORBIT,
            Category.WANDER,
            20,
            0,
            0,
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
                // Land-orbiters hand off to OrbitSwimGoal when a species is fully aquatic.
                MobCapabilities.CapabilityRequirement.not(MobCapabilities.CapabilityRequirement.aquatic())
            )),
            new Vec2f(0.0f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            false,
            OwnerOrbitGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.TOY_POUNCE,
            Category.PLAY,
            18,
            secondsToTicks(9),
            secondsToTicks(18),
            MobCapabilities.CapabilityRequirement.fromToken("can_jump"),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            true,
            ToyPounceGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PARKOUR_CHALLENGE,
            Category.PLAY,
            18,
            secondsToTicks(10),
            secondsToTicks(20),
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_jump"),
                MobCapabilities.CapabilityRequirement.fromToken("can_wander")
            )),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            true,
            ParkourChallengeGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.FETCH_ITEM,
            Category.PLAY,
            18,
            secondsToTicks(20),
            secondsToTicks(40),
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("has_owner")
            )),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            true,
            SimpleFetchItemGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.DIVE_PLAY,
            Category.PLAY,
            18,
            secondsToTicks(8),
            secondsToTicks(16),
            MobCapabilities.CapabilityRequirement.fromToken("can_swim"),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            true,
            DivePlayGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.AERIAL_ACROBATICS,
            Category.PLAY,
            18,
            secondsToTicks(7),
            secondsToTicks(14),
            MobCapabilities.CapabilityRequirement.fromToken("can_fly"),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            true,
            AerialAcrobaticsGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.WATER_SPLASH,
            Category.PLAY,
            18,
            secondsToTicks(10),
            secondsToTicks(18),
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_swim"),
                MobCapabilities.CapabilityRequirement.fromToken("can_wander")
            )),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            false,
            WaterSplashGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.CROUCH_CUDDLE,
            Category.SOCIAL,
            12,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.0f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            false,
            CrouchCuddleGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PET_SNUGGLE,
            Category.SOCIAL,
            14,
            secondsToTicks(90),
            secondsToTicks(150),
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.2f, 0.8f),
            IdleStaminaBias.LOW,
            true,
            false,
            PetSnuggleGoal::new
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
            true,
            ParallelPlayGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.NURSE_BABY,
            Category.SOCIAL,
            15,
            secondsToTicks(25),
            secondsToTicks(45),
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
                MobCapabilities.CapabilityRequirement.fromToken("can_wander")
            )),
            new Vec2f(0.15f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            true,
            NurseBabyGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.SHOW_OFF_TRICK,
            Category.SOCIAL,
            15,
            secondsToTicks(12),
            secondsToTicks(24),
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            true,
            ShowOffTrickGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.BEDTIME_COMPANION,
            Category.SOCIAL,
            16,
            secondsToTicks(30),
            secondsToTicks(60),
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.0f, 0.85f),
            IdleStaminaBias.NONE,
            false,
            false,
            BedtimeCompanionGoal::new
        ));

        // Removed unused registration: GiftBringing

        registerBuiltIn(new GoalDefinition(
            GoalIds.PERCH_ON_SHOULDER,
            Category.SOCIAL,
            15,
            secondsToTicks(12),
            secondsToTicks(24),
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
                MobCapabilities.CapabilityRequirement.fromToken("can_fly"),
                MobCapabilities.CapabilityRequirement.fromToken("is_small_size")
            )),
            new Vec2f(0.0f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            true,
            PerchOnShoulderGoal::new
        ));

        // Eye Contact goal retired; datapack definition and factory stub removed to prevent loader errors

        registerBuiltIn(new GoalDefinition(
            GoalIds.ORBIT_SWIM,
            Category.SOCIAL,
            15,
            secondsToTicks(8),
            secondsToTicks(18),
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
                MobCapabilities.CapabilityRequirement.fromToken("can_swim")
            )),
            new Vec2f(0.3f, 0.8f),
            IdleStaminaBias.NONE,
            false,
            true,
            OrbitSwimGoal::new
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
            false,
            CrouchApproachResponseGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.HIDE_AND_SEEK,
            Category.SPECIAL,
            16,
            secondsToTicks(18),
            secondsToTicks(40),
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
                MobCapabilities.CapabilityRequirement.fromToken("can_wander")
            )),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            true,
            HideAndSeekGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.INVESTIGATE_BLOCK,
            Category.SPECIAL,
            16,
            secondsToTicks(8),
            secondsToTicks(16),
            MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            false,
            InvestigateBlockGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.STARGAZING,
            Category.SPECIAL,
            16,
            secondsToTicks(30),
            secondsToTicks(60),
            MobCapabilities.CapabilityRequirement.fromToken("can_sit"),
            new Vec2f(0.0f, 0.5f),
            IdleStaminaBias.NONE,
            false,
            false,
            StargazingGoal::new
        ));
        registerBuiltIn(new GoalDefinition(
            GoalIds.SKY_SURVEY,
            Category.SPECIAL,
            21,
            secondsToTicks(40),
            secondsToTicks(90),
            MobCapabilities.CapabilityRequirement.allOf(List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_fly"),
                MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
                MobCapabilities.CapabilityRequirement.fromToken("prefers_air")
            )),
            new Vec2f(0.45f, 0.95f),
            IdleStaminaBias.CENTERED,
            false,
            true,
            SkySurveyGoal::new
        ));
        // Subtle behavior: P0 - Sunbeam sprawl (world interaction)
        registerBuiltIn(new GoalDefinition(
            GoalIds.SUNBEAM_SPRAWL,
            Category.SPECIAL,
            16,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
            new Vec2f(0.0f, 0.6f),
            IdleStaminaBias.LOW,
            false,
            true,
            SunbeamSprawlGoal::new
        ));
        // Subtle behavior: P0 - Scent trail sniff (world interaction)
        registerBuiltIn(new GoalDefinition(
            GoalIds.SCENT_TRAIL_SNIFF,
            Category.SPECIAL,
            16,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.CENTERED,
            false,
            false,
            ScentTrailSniffGoal::new
        ));
        // Subtle behavior: P0 - Pack groom (social micro)
        registerBuiltIn(new GoalDefinition(
            GoalIds.PACK_GROOM,
            Category.SOCIAL,
            15,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.0f, 0.8f),
            IdleStaminaBias.NONE,
            false,
            false,
            PackGroomGoal::new
        ));
        // PACK_SENTINEL_WATCH removed as part of AI simplification
        // Subtle behavior: P0 - Leaf chase wind (environmental playful dart)
        registerBuiltIn(new GoalDefinition(
            GoalIds.LEAF_CHASE_WIND,
            Category.PLAY,
            18,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
            new Vec2f(0.6f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            false,
            LeafChaseWindGoal::new
        ));
        // Subtle behavior: P1 - Puddle paw (world interaction)
        registerBuiltIn(new GoalDefinition(
            GoalIds.PUDDLE_PAW,
            Category.SPECIAL,
            16,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
            new Vec2f(0.0f, 0.6f),
            IdleStaminaBias.LOW,
            false,
            false,
            PuddlePawGoal::new
        ));
        // Subtle behavior: P1 - Burrow peek (world interaction)
        registerBuiltIn(new GoalDefinition(
            GoalIds.BURROW_PEEK,
            Category.SPECIAL,
            16,
            0,
            0,
            MobCapabilities.CapabilityRequirement.allOf(java.util.List.of(
                MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
                MobCapabilities.CapabilityRequirement.fromToken("prefers_land")
            )),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.CENTERED,
            false,
            false,
            BurrowPeekGoal::new
        ));
        // Subtle behavior: P1 - Hearth settle (world interaction)
        registerBuiltIn(new GoalDefinition(
            GoalIds.HEARTH_SETTLE,
            Category.SPECIAL,
            16,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
            new Vec2f(0.0f, 0.6f),
            IdleStaminaBias.LOW,
            false,
            true,
            HearthSettleGoal::new
        ));
        // LEAD_FOLLOW_NUDGE removed as part of AI simplification
        // Subtle behavior: P1 - Mirrored yawn (social)
        registerBuiltIn(new GoalDefinition(
            GoalIds.MIRRORED_YAWN,
            Category.SOCIAL,
            15,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.0f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            false,
            MirroredYawnGoal::new
        ));
        // Subtle behavior: P1 - Gift bringing (social)
        registerBuiltIn(new GoalDefinition(
            GoalIds.GIFT_BRINGING,
            Category.SOCIAL,
            15,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.25f, 0.75f),
            IdleStaminaBias.NONE,
            false,
            true,
            GiftBringingGoal::new
        ));
        // Subtle behavior: P1 - Show and drop (social)
        registerBuiltIn(new GoalDefinition(
            GoalIds.SHOW_AND_DROP,
            Category.SOCIAL,
            15,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            true,
            ShowAndDropGoal::new
        ));
        // Subtle behavior: P1 - Fish watching (environmental)
        registerBuiltIn(new GoalDefinition(
            GoalIds.FISH_WATCHING,
            Category.SPECIAL,
            16,
            0,
            0,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.CENTERED,
            false,
            true,
            FishWatchingGoal::new
        ));
        // Subtle behavior: P1 - Night sky listen (environmental)
        registerBuiltIn(new GoalDefinition(
            GoalIds.NIGHT_SKY_LISTEN,
            Category.SPECIAL,
            16,
            0,
            0,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.0f, 0.5f),
            IdleStaminaBias.LOW,
            false,
            true,
            NightSkyListenGoal::new
        ));
        
        registerBuiltIn(new GoalDefinition(
            GoalIds.SHAKE_DRY,
            Category.IDLE_QUIRK,
            25,
            300,
            900,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.0f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            false,
            ShakeDryGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.GO_TO_DRYING_BLOCK,
            Category.SPECIAL,
            17,
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
            new Vec2f(0.3f, 0.7f),
            IdleStaminaBias.NONE,
            false,
            false,
            GoToDryingBlockGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.WET_SHAKE,
            Category.IDLE_QUIRK,
            29,
            0,
            0,
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.0f, 0.2f),
            IdleStaminaBias.LOW,
            false,
            false,
            WetShakeGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.SELF_PRESERVATION,
            Category.SPECIAL,
            12, // High priority for combat response
            0,
            0,
            MobCapabilities.CapabilityRequirement.fromToken("has_owner"),
            new Vec2f(0.3f, 1.0f),
            IdleStaminaBias.NONE,
            false,
            true,
            SelfPreservationGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.CURIOSITY,
            Category.SPECIAL,
            16, // Lower priority than self-preservation
            secondsToTicks(10),
            secondsToTicks(30),
            MobCapabilities.CapabilityRequirement.fromToken("can_wander"),
            new Vec2f(0.4f, 0.9f),
            IdleStaminaBias.CENTERED,
            false,
            true,
            CuriosityGoal::new
        ));

        registerBuiltIn(new GoalDefinition(
            GoalIds.PERFORM_AMBIENT_ANIMATION,
            Category.IDLE_QUIRK,
            30, // Low priority for ambient animations
            secondsToTicks(15),
            secondsToTicks(60),
            MobCapabilities.CapabilityRequirement.any(),
            new Vec2f(0.0f, 0.8f),
            IdleStaminaBias.LOW,
            false,
            false,
            PerformAmbientAnimationGoal::new
        ));

        initializeMovementConfigs();
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

    private static void initializeMovementConfigs() {
        Set<Identifier> remaining = new HashSet<>(DEFINITIONS.keySet());
        BUILT_IN_MOVEMENT_CONFIGS.clear();

        assignNone(remaining,
            GoalIds.BUBBLE_PLAY,
            GoalIds.FLOAT_IDLE,
            GoalIds.MIRRORED_YAWN,
            GoalIds.NIGHT_SKY_LISTEN,
            GoalIds.PERFORM_AMBIENT_ANIMATION,
            GoalIds.PERK_EARS_SCAN,
            GoalIds.PET_SNUGGLE,
            GoalIds.PREEN_FEATHERS,
            GoalIds.SHAKE_DRY,
            GoalIds.SIT_SPHINX_POSE,
            GoalIds.SNIFF_GROUND,
            GoalIds.STRETCH_AND_YAW,
            GoalIds.WET_SHAKE,
            GoalIds.WING_FLUTTER
        );

        assignInfluencer(remaining, 0.65f, GoalIds.MAINTAIN_PACK_SPACING);
        assignInfluencer(remaining, 1.0f, GoalIds.HESITATE_IN_COMBAT);

        Iterator<Identifier> iterator = remaining.iterator();
        while (iterator.hasNext()) {
            Identifier id = iterator.next();
            GoalDefinition definition = DEFINITIONS.get(id);
            if (definition == null) {
                iterator.remove();
                continue;
            }
            configureMovement(id, GoalMovementConfig.actor(definition.priority()));
            iterator.remove();
        }

        if (!remaining.isEmpty()) {
            throw new IllegalStateException("Missing movement role assignments for: " + remaining);
        }
    }

    private static void configureMovement(Identifier id, GoalMovementConfig config) {
        GoalMovementConfig normalized = config == null ? GoalMovementConfig.none() : config;
        MOVEMENT_CONFIGS.put(id, normalized);
        if (!BUILT_IN_MOVEMENT_CONFIGS.containsKey(id) && BUILT_IN_DEFAULTS.containsKey(id)) {
            BUILT_IN_MOVEMENT_CONFIGS.put(id, normalized);
        }
    }

    private static void assignNone(Set<Identifier> remaining, Identifier... ids) {
        for (Identifier id : ids) {
            GoalDefinition definition = DEFINITIONS.get(id);
            if (definition == null) {
                continue;
            }
            configureMovement(id, GoalMovementConfig.none());
            remaining.remove(id);
        }
    }

    private static void assignInfluencer(Set<Identifier> remaining, float weight, Identifier... ids) {
        for (Identifier id : ids) {
            GoalDefinition definition = DEFINITIONS.get(id);
            if (definition == null) {
                continue;
            }
            configureMovement(id, GoalMovementConfig.influencer(weight));
            remaining.remove(id);
        }
    }

    public static GoalDefinition register(GoalDefinition definition) {
        return register(definition, null);
    }

    public static GoalDefinition register(GoalDefinition definition, @Nullable GoalMovementConfig movementOverride) {
        Identifier id = definition.id();
        if (DEFINITIONS.containsKey(id)) {
            throw new IllegalStateException("Duplicate goal id registered: " + id);
        }
        DEFINITIONS.put(id, definition);
        BUILT_IN_DEFAULTS.putIfAbsent(id, definition);
        DATA_DRIVEN.remove(id);
        assignMovementForDefinition(id, definition, movementOverride);
        return definition;
    }

    public static GoalDefinition registerDataDriven(GoalDefinition definition) {
        return registerDataDriven(definition, null);
    }

    public static GoalDefinition registerDataDriven(GoalDefinition definition, @Nullable GoalMovementConfig movementOverride) {
        Identifier id = definition.id();
        DEFINITIONS.put(id, definition);
        DATA_DRIVEN.add(id);
        assignMovementForDefinition(id, definition, movementOverride);
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
                GoalMovementConfig builtInConfig = BUILT_IN_MOVEMENT_CONFIGS.get(id);
                if (builtInConfig != null) {
                    configureMovement(id, builtInConfig);
                } else {
                    MOVEMENT_CONFIGS.remove(id);
                }
            } else {
                DEFINITIONS.remove(id);
                MOVEMENT_CONFIGS.remove(id);
            }
        }
        DATA_DRIVEN.clear();
    }

    private static void assignMovementForDefinition(Identifier id, GoalDefinition definition, @Nullable GoalMovementConfig movementOverride) {
        GoalMovementConfig config = movementOverride;
        if (config == null) {
            GoalMovementConfig builtInConfig = BUILT_IN_MOVEMENT_CONFIGS.get(id);
            if (builtInConfig != null) {
                config = builtInConfig;
            } else {
                config = GoalMovementConfig.actor(definition.priority());
            }
        }
        configureMovement(id, config);
    }

    public static Collection<GoalDefinition> all() {
        return Collections.unmodifiableCollection(DEFINITIONS.values());
    }

    public static Optional<GoalDefinition> get(Identifier id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static GoalMovementConfig movementConfig(Identifier id) {
        return MOVEMENT_CONFIGS.getOrDefault(id, GoalMovementConfig.none());
    }

    public static GoalDefinition require(Identifier id) {
        GoalDefinition definition = DEFINITIONS.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown goal id: " + id);
        }
        return definition;
    }
}
