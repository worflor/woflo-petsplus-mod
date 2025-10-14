package woflo.petsplus.ai.goals;

import net.minecraft.util.Identifier;

/**
 * Stable identifier constants for every adaptive goal shipped with Pets+.
 *
 * <p>The registry no longer exposes the {@link GoalDefinition} singletons as
 * public fields so datapacks can replace the backing definitions. These
 * identifiers provide a consistent way for runtime systems to look up the
 * active definition via {@link GoalRegistry#require(Identifier)}.
 */
public final class GoalIds {

    private GoalIds() {
    }

    private static Identifier id(String path) {
        return Identifier.of("petsplus", path);
    }

    public static final Identifier STRETCH_AND_YAW = id("stretch_and_yawn");
    public static final Identifier CIRCLE_SPOT = id("circle_spot");
    public static final Identifier TAIL_CHASE = id("tail_chase");
    public static final Identifier SNIFF_GROUND = id("sniff_ground");
    public static final Identifier POUNCE_PRACTICE = id("pounce_practice");
    public static final Identifier PERK_EARS_SCAN = id("perk_ears_scan");
    public static final Identifier SIT_SPHINX_POSE = id("sit_sphinx_pose");
    public static final Identifier PREEN_FEATHERS = id("preen_feathers");
    public static final Identifier WING_FLUTTER = id("wing_flutter");
    public static final Identifier PERCH_HOP = id("perch_hop");
    public static final Identifier FLOAT_IDLE = id("float_idle");
    public static final Identifier BUBBLE_PLAY = id("bubble_play");
    public static final Identifier SURFACE_BREATH = id("surface_breath");
    public static final Identifier CASUAL_WANDER = id("casual_wander");
    public static final Identifier AERIAL_PATROL = id("aerial_patrol");
    public static final Identifier WATER_CRUISE = id("water_cruise");
    public static final Identifier SCENT_TRAIL_FOLLOW = id("scent_trail_follow");
    public static final Identifier PURPOSEFUL_PATROL = id("purposeful_patrol");
    public static final Identifier OWNER_ORBIT = id("owner_orbit");
    public static final Identifier TOY_POUNCE = id("toy_pounce");
    public static final Identifier PARKOUR_CHALLENGE = id("parkour_challenge");
    public static final Identifier FETCH_ITEM = id("fetch_item");
    public static final Identifier DIVE_PLAY = id("dive_play");
    public static final Identifier AERIAL_ACROBATICS = id("aerial_acrobatics");
    public static final Identifier WATER_SPLASH = id("water_splash");
    public static final Identifier LEAN_AGAINST_OWNER = id("lean_against_owner");
    public static final Identifier PARALLEL_PLAY = id("parallel_play");
    public static final Identifier SHOW_OFF_TRICK = id("show_off_trick");
    public static final Identifier PERCH_ON_SHOULDER = id("perch_on_shoulder");
    public static final Identifier ORBIT_SWIM = id("orbit_swim");
    public static final Identifier CROUCH_APPROACH_RESPONSE = id("crouch_approach_response");
    public static final Identifier HIDE_AND_SEEK = id("hide_and_seek");
    public static final Identifier INVESTIGATE_BLOCK = id("investigate_block");
    public static final Identifier STARGAZING = id("stargazing");
    // Subtle behavior: P0
    public static final Identifier SUNBEAM_SPRAWL = id("sunbeam_sprawl");
    public static final Identifier SCENT_TRAIL_SNIFF = id("scent_trail_sniff");
    public static final Identifier PACK_GROOM = id("pack_groom");
    public static final Identifier LEAF_CHASE_WIND = id("leaf_chase_wind");
    // Subtle behavior: P1
    // Species tags: multi-tag gating
    public static final Identifier PUDDLE_PAW = id("puddle_paw");
    public static final Identifier BURROW_PEEK = id("burrow_peek");
    public static final Identifier HEARTH_SETTLE = id("hearth_settle");
    public static final Identifier LEAD_FOLLOW_NUDGE = id("lead_follow_nudge");
    public static final Identifier MIRRORED_YAWN = id("mirrored_yawn");
    public static final Identifier SHOW_AND_DROP = id("show_and_drop");
    public static final Identifier FISH_WATCHING = id("fish_watching");
    public static final Identifier NIGHT_SKY_LISTEN = id("night_sky_listen");
    public static final Identifier SHAKE_DRY = id("shake_dry");
    public static final Identifier GO_TO_DRYING_BLOCK = id("go_to_drying_block");
    public static final Identifier WET_SHAKE = id("wet_shake");
}
