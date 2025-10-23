package woflo.petsplus.ai.goals;

import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

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

    public static final Identifier STRETCH_AND_YAW = Petsplus.id("stretch_and_yawn");
    public static final Identifier CIRCLE_SPOT = Petsplus.id("circle_spot");
    public static final Identifier TAIL_CHASE = Petsplus.id("tail_chase");
    public static final Identifier SNIFF_GROUND = Petsplus.id("sniff_ground");
    public static final Identifier POUNCE_PRACTICE = Petsplus.id("pounce_practice");
    public static final Identifier PERK_EARS_SCAN = Petsplus.id("perk_ears_scan");
    public static final Identifier SIT_SPHINX_POSE = Petsplus.id("sit_sphinx_pose");
    public static final Identifier PREEN_FEATHERS = Petsplus.id("preen_feathers");
    public static final Identifier WING_FLUTTER = Petsplus.id("wing_flutter");
    public static final Identifier PERCH_HOP = Petsplus.id("perch_hop");
    public static final Identifier FLOAT_IDLE = Petsplus.id("float_idle");
    public static final Identifier BUBBLE_PLAY = Petsplus.id("bubble_play");
    public static final Identifier SURFACE_BREATH = Petsplus.id("surface_breath");
    public static final Identifier CASUAL_WANDER = Petsplus.id("casual_wander");
    public static final Identifier AERIAL_PATROL = Petsplus.id("aerial_patrol");
    public static final Identifier WATER_CRUISE = Petsplus.id("water_cruise");
    public static final Identifier SCENT_TRAIL_FOLLOW = Petsplus.id("scent_trail_follow");
    public static final Identifier PURPOSEFUL_PATROL = Petsplus.id("purposeful_patrol");
    public static final Identifier OWNER_ORBIT = Petsplus.id("owner_orbit");
    public static final Identifier FOLLOW_OWNER = Petsplus.id("follow_owner");
    public static final Identifier MAINTAIN_PACK_SPACING = Petsplus.id("maintain_pack_spacing");
    public static final Identifier HESITATE_IN_COMBAT = Petsplus.id("hesitate_in_combat");
    public static final Identifier SHELTER_FROM_RAIN = Petsplus.id("shelter_from_rain");
    public static final Identifier OWNER_ASSIST_ATTACK = Petsplus.id("owner_assist_attack");
    public static final Identifier CROUCH_CUDDLE = Petsplus.id("crouch_cuddle");
    public static final Identifier PET_SNUGGLE = Petsplus.id("pet_snuggle");
    public static final Identifier TOY_POUNCE = Petsplus.id("toy_pounce");
    public static final Identifier PARKOUR_CHALLENGE = Petsplus.id("parkour_challenge");
    public static final Identifier FETCH_ITEM = Petsplus.id("fetch_item");
    public static final Identifier SIMPLE_FETCH_ITEM = Petsplus.id("simple_fetch_item");
    public static final Identifier DIVE_PLAY = Petsplus.id("dive_play");
    public static final Identifier AERIAL_ACROBATICS = Petsplus.id("aerial_acrobatics");
    public static final Identifier WATER_SPLASH = Petsplus.id("water_splash");
    public static final Identifier LEAN_AGAINST_OWNER = Petsplus.id("lean_against_owner");
    public static final Identifier PARALLEL_PLAY = Petsplus.id("parallel_play");
    public static final Identifier TRUSTED_ALLY_CHECK_IN = Petsplus.id("trusted_ally_check_in");
    public static final Identifier NURSE_BABY = Petsplus.id("nurse_baby");
    public static final Identifier SHOW_OFF_TRICK = Petsplus.id("show_off_trick");
    public static final Identifier BEDTIME_COMPANION = Petsplus.id("bedtime_companion");
    public static final Identifier PERCH_ON_SHOULDER = Petsplus.id("perch_on_shoulder");
    public static final Identifier ORBIT_SWIM = Petsplus.id("orbit_swim");
    public static final Identifier CROUCH_APPROACH_RESPONSE = Petsplus.id("crouch_approach_response");
    public static final Identifier HIDE_AND_SEEK = Petsplus.id("hide_and_seek");
    public static final Identifier INVESTIGATE_BLOCK = Petsplus.id("investigate_block");
    public static final Identifier STARGAZING = Petsplus.id("stargazing");
    public static final Identifier SKY_SURVEY = Petsplus.id("sky_survey");
    // Subtle behavior: P0
    public static final Identifier SUNBEAM_SPRAWL = Petsplus.id("sunbeam_sprawl");
    public static final Identifier SCENT_TRAIL_SNIFF = Petsplus.id("scent_trail_sniff");
    public static final Identifier PACK_GROOM = Petsplus.id("pack_groom");
    // PACK_SENTINEL_WATCH removed as part of AI simplification
    public static final Identifier LEAF_CHASE_WIND = Petsplus.id("leaf_chase_wind");
    // Subtle behavior: P1
    // Species tags: multi-tag gating
    public static final Identifier PUDDLE_PAW = Petsplus.id("puddle_paw");
    public static final Identifier BURROW_PEEK = Petsplus.id("burrow_peek");
    public static final Identifier HEARTH_SETTLE = Petsplus.id("hearth_settle");
    // LEAD_FOLLOW_NUDGE removed as part of AI simplification
    public static final Identifier MIRRORED_YAWN = Petsplus.id("mirrored_yawn");
    public static final Identifier SHOW_AND_DROP = Petsplus.id("show_and_drop");
    public static final Identifier GIFT_BRINGING = Petsplus.id("gift_bringing");
    public static final Identifier FISH_WATCHING = Petsplus.id("fish_watching");
    public static final Identifier NIGHT_SKY_LISTEN = Petsplus.id("night_sky_listen");
    public static final Identifier SHAKE_DRY = Petsplus.id("shake_dry");
    public static final Identifier GO_TO_DRYING_BLOCK = Petsplus.id("go_to_drying_block");
    public static final Identifier WET_SHAKE = Petsplus.id("wet_shake");
    public static final Identifier SELF_PRESERVATION = Petsplus.id("self_preservation");
    public static final Identifier CURIOSITY = Petsplus.id("curiosity");
    public static final Identifier PERFORM_AMBIENT_ANIMATION = Petsplus.id("perform_ambient_animation");
}
