package woflo.petsplus.ui;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for particle and audio feedback effects throughout the mod.
 * Maps event names to specific visual/audio feedback patterns.
 */
public class FeedbackConfig {

    public static class FeedbackEffect {
        public final List<ParticleConfig> particles;
        public final AudioConfig audio;
        public final int delayTicks;
        public final boolean serverSide;

        public FeedbackEffect(List<ParticleConfig> particles, AudioConfig audio, int delayTicks, boolean serverSide) {
            this.particles = particles;
            this.audio = audio;
            this.delayTicks = delayTicks;
            this.serverSide = serverSide;
        }
    }

    public static class ParticleConfig {
        public final ParticleEffect type;
        public final int count;
        public final double offsetX, offsetY, offsetZ;
        public final double speed;
        public final String pattern; // "circle", "line", "burst", "area", "spiral"
        public final double radius;
        public final boolean adaptToEntitySize;

        public ParticleConfig(ParticleEffect type, int count, double offsetX, double offsetY, double offsetZ,
                            double speed, String pattern, double radius, boolean adaptToEntitySize) {
            this.type = type;
            this.count = count;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.speed = speed;
            this.pattern = pattern;
            this.radius = radius;
            this.adaptToEntitySize = adaptToEntitySize;
        }
    }

    public static class AudioConfig {
        public final SoundEvent sound;
        public final float volume;
        public final float pitch;
        public final double radius;

        public AudioConfig(SoundEvent sound, float volume, float pitch, double radius) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            this.radius = radius;
        }
    }

    private static final Map<String, FeedbackEffect> FEEDBACK_REGISTRY = new HashMap<>();

    static {
        initializeDefaultEffects();
    }

    private static void initializeDefaultEffects() {
        // Role identification particles (passive)
        registerRoleParticles();

        // Ability feedback effects (active)
        registerAbilityFeedback();

        // Combat feedback effects
        registerCombatFeedback();

        // Utility/QoL feedback
        registerUtilityFeedback();

        // Tribute orbital effects
        registerTributeOrbitalFeedback();

        // Petting feedback effects
        registerPettingFeedback();
    }

    private static void registerRoleParticles() {
        // Guardian: Protective blue shimmer
        register("guardian_ambient", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 4, 0.05, 0.05, 0.05, 0.01, "circle", 1.0, true)
        ), null, 0, true);

        // Striker: Sharp red sparks
        register("striker_ambient", List.of(
            new ParticleConfig(ParticleTypes.CRIT, 6, 0.1, 0.1, 0.1, 0.05, "burst", 0.5, true)
        ), null, 0, true);

        // Support: Green plus with hearts
        register("support_ambient", List.of(
            new ParticleConfig(ParticleTypes.HAPPY_VILLAGER, 4, 0.02, 0.02, 0.02, 0.01, "plus", 0.8, true),
            new ParticleConfig(ParticleTypes.HEART, 1, 0.1, 0.1, 0.1, 0.01, "burst", 0.1, false)
        ), null, 0, true);

        // Scout: Swirling golden trail
        register("scout_ambient", List.of(
            new ParticleConfig(ParticleTypes.WAX_ON, 3, 0.02, 0.02, 0.02, 0.01, "spiral", 0.4, true)
        ), null, 0, true);

        // Skyrider: Upward wisps
        register("skyrider_ambient", List.of(
            new ParticleConfig(ParticleTypes.CLOUD, 3, 0.05, 0.0, 0.05, 0.02, "upward", 0.3, true),
            new ParticleConfig(ParticleTypes.FIREWORK, 1, 0.1, 0.1, 0.1, 0.01, "burst", 0.1, false)
        ), null, 0, true);

        // Enchantment Bound: Purple mystical
        register("enchantment_bound_ambient", List.of(
            new ParticleConfig(ParticleTypes.ENCHANT, 4, 0.02, 0.02, 0.02, 0.02, "random", 0.6, true)
        ), null, 0, true);

        // Cursed One: Dark smoke with embers
        register("cursed_one_ambient", List.of(
            new ParticleConfig(ParticleTypes.LARGE_SMOKE, 2, 0.03, 0.02, 0.03, 0.01, "upward", 0.4, true),
            new ParticleConfig(ParticleTypes.LAVA, 1, 0.1, 0.1, 0.1, 0.01, "burst", 0.1, false)
        ), null, 0, true);

        // Eepy Eeper: Sleepy Z pattern
        register("eepy_eeper_ambient", List.of(
            new ParticleConfig(ParticleTypes.NOTE, 3, 0.01, 0.01, 0.01, 0.01, "z_pattern", 0.6, true),
            new ParticleConfig(ParticleTypes.POOF, 2, 0.1, 0.05, 0.1, 0.01, "burst", 0.1, false)
        ), null, 0, true);

        // Eclipsed: Void with reality tears
        register("eclipsed_ambient", List.of(
            new ParticleConfig(ParticleTypes.PORTAL, 3, 0.02, 0.02, 0.02, 0.01, "random", 0.6, true),
            new ParticleConfig(ParticleTypes.REVERSE_PORTAL, 2, 0.15, 0.1, 0.15, 0.02, "burst", 0.1, false)
        ), null, 0, true);
    }

    private static void registerAbilityFeedback() {
        // Guardian: Damage absorbed
        register("guardian_damage_absorbed", List.of(
            new ParticleConfig(ParticleTypes.ENCHANTED_HIT, 3, 0.2, 0.1, 0.2, 0.02, "burst", 0.5, false)
        ), new AudioConfig(SoundEvents.ITEM_SHIELD_BLOCK.value(), 0.3f, 1.2f, 8.0), 0, true);

        // Guardian: Shield bash rider triggered
        register("guardian_shield_bash", List.of(
            new ParticleConfig(ParticleTypes.SWEEP_ATTACK, 1, 0.0, 0.0, 0.0, 0.0, "burst", 0.0, false),
            new ParticleConfig(ParticleTypes.CRIT, 5, 0.3, 0.2, 0.3, 0.05, "burst", 0.8, false)
        ), new AudioConfig(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 0.4f, 0.9f, 10.0), 0, true);

        // Striker: Execution bonus triggered
        register("striker_execution", List.of(
            new ParticleConfig(ParticleTypes.DAMAGE_INDICATOR, 3, 0.2, 0.3, 0.2, 0.1, "burst", 0.5, false)
        ), new AudioConfig(SoundEvents.ENTITY_CAT_HISS, 0.4f, 1.1f, 8.0), 0, true);

        // Striker: Finisher mark applied
        register("striker_finisher_mark", List.of(
            new ParticleConfig(ParticleTypes.ANGRY_VILLAGER, 1, 0.0, 0.5, 0.0, 0.0, "burst", 0.0, false),
            new ParticleConfig(ParticleTypes.CRIT, 2, 0.1, 0.2, 0.1, 0.03, "circle", 0.3, false)
        ), new AudioConfig(SoundEvents.ENTITY_ARROW_HIT, 0.3f, 0.7f, 6.0), 0, true);

        // Support: Sitting regen area active - visualize the actual 6-block radius
        register("support_sitting_regen", List.of(
            new ParticleConfig(ParticleTypes.HEART, 8, 0.1, 0.05, 0.1, 0.01, "aura_radius_ground", 6.0, false),
            new ParticleConfig(ParticleTypes.HAPPY_VILLAGER, 4, 0.2, 0.02, 0.2, 0.01, "aura_radius_edge", 6.0, false)
        ), new AudioConfig(SoundEvents.ENTITY_BEE_LOOP, 0.1f, 1.2f, 8.0), 0, true);

        // Support: Potion aura pulse - visualize the actual potion radius
        register("support_potion_pulse", List.of(
            new ParticleConfig(ParticleTypes.EFFECT, 6, 0.2, 0.1, 0.2, 0.02, "aura_radius_edge", 6.0, false),
            new ParticleConfig(ParticleTypes.ENCHANT, 2, 0.1, 0.05, 0.1, 0.01, "area", 2.0, false)
        ), null, 0, true);

        // Scout: Loot magnetism activated - show the 12-block collection radius
        register("scout_loot_wisp", List.of(
            new ParticleConfig(ParticleTypes.WAX_ON, 12, 0.3, 0.1, 0.3, 0.02, "aura_radius_edge", 12.0, false),
            new ParticleConfig(ParticleTypes.ENCHANT, 6, 0.2, 0.05, 0.2, 0.01, "aura_radius_ground", 8.0, false),
            new ParticleConfig(ParticleTypes.END_ROD, 2, 0.1, 0.3, 0.1, 0.02, "spiral", 1.0, true)
        ), new AudioConfig(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 0.8f, 12.0), 0, true);

        // Skyrider: Wind assistance
        register("skyrider_windlash", List.of(
            new ParticleConfig(ParticleTypes.CLOUD, 8, 0.5, 0.2, 0.5, 0.05, "burst", 1.0, false),
            new ParticleConfig(ParticleTypes.FIREWORK, 2, 0.3, 0.4, 0.3, 0.02, "upward", 0.5, false)
        ), new AudioConfig(SoundEvents.ITEM_ELYTRA_FLYING, 0.2f, 1.4f, 8.0), 0, true);

        // Enchantment Bound: Echo triggered
        register("enchantment_bound_echo", List.of(
            new ParticleConfig(ParticleTypes.ENCHANT, 8, 0.4, 0.3, 0.4, 0.03, "spiral", 1.5, true),
            new ParticleConfig(ParticleTypes.FIREWORK, 1, 0.0, 0.2, 0.0, 0.0, "burst", 0.0, false)
        ), new AudioConfig(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 0.3f, 1.1f, 8.0), 0, true);

        // Cursed One: Doom echo - show the 5-block weakness area
        register("cursed_one_doom_echo", List.of(
            new ParticleConfig(ParticleTypes.LARGE_SMOKE, 8, 0.3, 0.1, 0.3, 0.02, "aura_radius_ground", 5.0, false),
            new ParticleConfig(ParticleTypes.LAVA, 6, 0.2, 0.2, 0.2, 0.03, "aura_radius_edge", 5.0, false),
            new ParticleConfig(ParticleTypes.SOUL, 4, 0.1, 0.3, 0.1, 0.01, "spiral", 2.0, false)
        ), new AudioConfig(SoundEvents.ENTITY_PHANTOM_AMBIENT, 0.2f, 0.6f, 10.0), 0, true);

        // Cursed One: Auto-resurrect
        register("cursed_one_resurrect", List.of(
            new ParticleConfig(ParticleTypes.TOTEM_OF_UNDYING, 15, 0.8, 0.5, 0.8, 0.1, "burst", 2.0, false),
            new ParticleConfig(ParticleTypes.LAVA, 5, 0.4, 0.3, 0.4, 0.05, "circle", 1.5, false)
        ), new AudioConfig(SoundEvents.ITEM_TOTEM_USE, 0.5f, 0.8f, 16.0), 0, true);

        // Cursed One: Death bond - pet dies when owner dies
        register("cursed_one_death_bond", List.of(
            new ParticleConfig(ParticleTypes.SOUL, 12, 0.5, 0.8, 0.5, 0.03, "spiral", 2.0, true),
            new ParticleConfig(ParticleTypes.LARGE_SMOKE, 8, 0.3, 0.2, 0.3, 0.02, "burst", 1.0, false),
            new ParticleConfig(ParticleTypes.CRIMSON_SPORE, 6, 0.4, 0.4, 0.4, 0.02, "upward", 0.8, false)
        ), null, 0, true);

        // Cursed One: Self-resurrect - pet refuses to die (immortality)
        register("cursed_one_self_resurrect", List.of(
            new ParticleConfig(ParticleTypes.SOUL_FIRE_FLAME, 20, 0.6, 0.4, 0.6, 0.08, "upward", 1.5, true),
            new ParticleConfig(ParticleTypes.LARGE_SMOKE, 10, 0.4, 0.3, 0.4, 0.04, "circle", 2.0, false),
            new ParticleConfig(ParticleTypes.CRIMSON_SPORE, 15, 0.5, 0.5, 0.5, 0.03, "burst", 1.2, false)
        ), new AudioConfig(SoundEvents.ENTITY_WITHER_SPAWN, 0.7f, 1.5f, 14.0), 0, true);

        // Eclipsed: Void brand applied
        register("eclipsed_voidbrand", List.of(
            new ParticleConfig(ParticleTypes.PORTAL, 4, 0.2, 0.3, 0.2, 0.02, "circle", 0.5, false),
            new ParticleConfig(ParticleTypes.REVERSE_PORTAL, 1, 0.0, 0.2, 0.0, 0.0, "burst", 0.0, false)
        ), new AudioConfig(SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, 0.2f, 1.8f, 8.0), 0, true);

        // Eclipsed: Phase partner triggered
        register("eclipsed_phase_partner", List.of(
            new ParticleConfig(ParticleTypes.REVERSE_PORTAL, 8, 0.6, 0.4, 0.6, 0.04, "spiral", 1.2, true),
            new ParticleConfig(ParticleTypes.END_ROD, 3, 0.3, 0.2, 0.3, 0.02, "burst", 0.6, false)
        ), new AudioConfig(SoundEvents.ENTITY_SHULKER_TELEPORT, 0.3f, 1.3f, 10.0), 0, true);

        // Eclipsed: Event horizon zone - visualize the 6-block void zone
        register("eclipsed_event_horizon", List.of(
            new ParticleConfig(ParticleTypes.PORTAL, 15, 0.3, 0.1, 0.3, 0.02, "aura_radius_ground", 6.0, false),
            new ParticleConfig(ParticleTypes.REVERSE_PORTAL, 8, 0.2, 0.2, 0.2, 0.03, "aura_radius_edge", 6.0, false),
            new ParticleConfig(ParticleTypes.END_ROD, 4, 0.1, 0.3, 0.1, 0.01, "circle", 6.0, false)
        ), new AudioConfig(SoundEvents.BLOCK_PORTAL_TRIGGER, 0.3f, 0.7f, 12.0), 0, true);

        // Eepy Eeper: Nap regen - show the enhanced 4+1 block regen radius
        register("eepy_eeper_nap_regen", List.of(
            new ParticleConfig(ParticleTypes.HEART, 6, 0.2, 0.05, 0.2, 0.01, "aura_radius_ground", 5.0, false),
            new ParticleConfig(ParticleTypes.NOTE, 3, 0.1, 0.1, 0.1, 0.01, "aura_radius_edge", 4.0, false),
            new ParticleConfig(ParticleTypes.POOF, 1, 0.1, 0.05, 0.1, 0.01, "burst", 0.5, true)
        ), new AudioConfig(SoundEvents.ENTITY_CAT_PURR, 0.15f, 1.2f, 6.0), 0, true);
    }

    private static void registerCombatFeedback() {
        // Generic damage redirect
        register("damage_redirected", List.of(
            new ParticleConfig(ParticleTypes.ENCHANTED_HIT, 2, 0.15, 0.1, 0.15, 0.02, "line", 1.0, false)
        ), new AudioConfig(SoundEvents.ITEM_SHIELD_BLOCK.value(), 0.25f, 1.1f, 6.0), 0, true);

        // Owner attack enhanced by pet
        register("owner_attack_enhanced", List.of(
            new ParticleConfig(ParticleTypes.CRIT, 3, 0.2, 0.2, 0.2, 0.04, "burst", 0.5, false)
        ), new AudioConfig(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.2f, 1.2f, 6.0), 0, true);

        // Pet ability cooldown ready
        register("ability_ready", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 1, 0.0, 0.3, 0.0, 0.0, "burst", 0.0, false)
        ), new AudioConfig(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.2f, 1.3f, 4.0), 0, true);
    }

    private static void registerUtilityFeedback() {
        // Pet level up
        register("pet_level_up", List.of(
            new ParticleConfig(ParticleTypes.TOTEM_OF_UNDYING, 8, 0.5, 0.8, 0.5, 0.05, "burst", 1.0, true),
            new ParticleConfig(ParticleTypes.FIREWORK, 3, 0.3, 0.5, 0.3, 0.03, "upward", 0.8, true)
        ), new AudioConfig(SoundEvents.ENTITY_VILLAGER_YES, 0.4f, 1.1f, 12.0), 0, true);

        // Pet bond strengthened
        register("bond_strengthened", List.of(
            new ParticleConfig(ParticleTypes.HEART, 3, 0.3, 0.4, 0.3, 0.02, "burst", 0.6, false)
        ), new AudioConfig(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.4f, 8.0), 0, true);

        // Configuration changed
        register("config_changed", List.of(
            new ParticleConfig(ParticleTypes.ENCHANT, 2, 0.1, 0.2, 0.1, 0.01, "circle", 0.3, false)
        ), new AudioConfig(SoundEvents.UI_BUTTON_CLICK.value(), 0.2f, 1.0f, 4.0), 0, true);
    }

    private static void registerTributeOrbitalFeedback() {
        // Level 10 - Single orbital (Gold theme)
        register("tribute_orbital_10", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 6, 0.02, 0.02, 0.02, 0.01, "orbital_single", 1.5, true)
        ), null, 0, true);

        // Level 20 - Dual orbital (Diamond theme)
        register("tribute_orbital_20", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 8, 0.02, 0.02, 0.02, 0.01, "orbital_dual", 1.5, true),
            new ParticleConfig(ParticleTypes.ENCHANT, 6, 0.01, 0.01, 0.01, 0.005, "orbital_dual", 2.0, true)
        ), null, 0, true);

        // Level 30 - Triple orbital (Netherite theme)
        register("tribute_orbital_30", List.of(
            new ParticleConfig(ParticleTypes.SOUL_FIRE_FLAME, 12, 0.02, 0.02, 0.02, 0.01, "orbital_triple", 1.5, true),
            new ParticleConfig(ParticleTypes.FLAME, 10, 0.01, 0.01, 0.01, 0.005, "orbital_triple", 2.0, true),
            new ParticleConfig(ParticleTypes.LAVA, 8, 0.01, 0.01, 0.01, 0.005, "orbital_triple", 2.5, true)
        ), null, 0, true);

        // Tribute completion effects
        register("tribute_complete_10", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 15, 0.4, 0.4, 0.4, 0.08, "burst", 1.0, true),
            new ParticleConfig(ParticleTypes.CRIT, 8, 0.3, 0.3, 0.3, 0.05, "circle", 1.2, true)
        ), new AudioConfig(SoundEvents.ENTITY_VILLAGER_YES, 0.8f, 1.4f, 12.0), 0, true);

        register("tribute_complete_20", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 20, 0.5, 0.5, 0.5, 0.1, "burst", 1.2, true),
            new ParticleConfig(ParticleTypes.ENCHANT, 12, 0.4, 0.4, 0.4, 0.06, "spiral", 1.5, true)
        ), new AudioConfig(SoundEvents.ENTITY_VILLAGER_YES, 0.9f, 1.5f, 14.0), 0, true);

        register("tribute_complete_30", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 25, 0.6, 0.6, 0.6, 0.12, "burst", 1.5, true),
            new ParticleConfig(ParticleTypes.SOUL_FIRE_FLAME, 15, 0.5, 0.5, 0.5, 0.08, "spiral", 2.0, true),
            new ParticleConfig(ParticleTypes.EXPLOSION, 1, 0.0, 0.0, 0.0, 0.0, "burst", 0.0, false)
        ), new AudioConfig(SoundEvents.ENTITY_VILLAGER_YES, 1.0f, 1.6f, 16.0), 0, true);
    }

    private static void registerPettingFeedback() {
        // Base petting effects
        register("pet_hearts", List.of(
            new ParticleConfig(ParticleTypes.HEART, 5, 0.4, 0.3, 0.4, 0.02, "burst", 0.8, true)
        ), null, 0, true);

        // Pet sounds (these will be overridden by specific pet types)
        register("pet_wolf_happy", List.of(), 
            new AudioConfig(SoundEvents.ENTITY_WOLF_STEP, 0.3f, 1.3f, 8.0), 0, true);
        register("pet_cat_purr", List.of(), 
            new AudioConfig(SoundEvents.ENTITY_CAT_PURR, 0.4f, 1.1f, 8.0), 0, true);
        register("pet_parrot_chirp", List.of(), 
            new AudioConfig(SoundEvents.ENTITY_PARROT_IMITATE_BLAZE, 0.3f, 1.4f, 8.0), 0, true);
        register("pet_horse_snort", List.of(), 
            new AudioConfig(SoundEvents.ENTITY_HORSE_ANGRY, 0.4f, 1.2f, 8.0), 0, true);
        register("pet_llama_spit", List.of(), 
            new AudioConfig(SoundEvents.ENTITY_LLAMA_CHEST, 0.3f, 1.5f, 8.0), 0, true);
        register("pet_camel_grumble", List.of(), 
            new AudioConfig(SoundEvents.ENTITY_CAMEL_STEP, 0.4f, 1.1f, 8.0), 0, true);
        register("pet_generic_happy", List.of(), 
            new AudioConfig(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.6f, 8.0), 0, true);

        // Role-specific petting effects
        register("guardian_protection_stance", List.of(
            new ParticleConfig(ParticleTypes.ENCHANTED_HIT, 3, 0.2, 0.1, 0.2, 0.01, "circle", 1.0, true)
        ), new AudioConfig(SoundEvents.ITEM_SHIELD_BLOCK.value(), 0.2f, 1.3f, 6.0), 0, true);

        register("striker_eagerness", List.of(
            new ParticleConfig(ParticleTypes.CRIT, 4, 0.3, 0.2, 0.3, 0.03, "burst", 0.6, true)
        ), new AudioConfig(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.25f, 1.4f, 6.0), 0, true);

        register("support_gentle_aura", List.of(
            new ParticleConfig(ParticleTypes.COMPOSTER, 6, 0.4, 0.3, 0.4, 0.01, "aura_radius_edge", 1.2, true)
        ), new AudioConfig(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 0.2f, 1.6f, 6.0), 0, true);

        register("scout_alertness", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 2, 0.1, 0.4, 0.1, 0.02, "upward", 0.8, true)
        ), new AudioConfig(SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, 0.3f, 1.2f, 6.0), 0, true);

        register("skyrider_wind_dance", List.of(
            new ParticleConfig(ParticleTypes.CLOUD, 8, 0.5, 0.2, 0.5, 0.05, "spiral", 1.5, true)
        ), new AudioConfig(SoundEvents.ITEM_ELYTRA_FLYING, 0.15f, 1.8f, 6.0), 0, true);

        register("enchantment_sparkle", List.of(
            new ParticleConfig(ParticleTypes.ENCHANT, 8, 0.4, 0.4, 0.4, 0.02, "circle", 1.0, true),
            new ParticleConfig(ParticleTypes.PORTAL, 3, 0.2, 0.3, 0.2, 0.01, "burst", 0.5, true)
        ), new AudioConfig(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 0.3f, 1.4f, 6.0), 0, true);

        register("cursed_dark_affection", List.of(
            new ParticleConfig(ParticleTypes.SOUL, 5, 0.3, 0.2, 0.3, 0.02, "circle", 0.8, true),
            new ParticleConfig(ParticleTypes.SMOKE, 2, 0.1, 0.1, 0.1, 0.01, "burst", 0.3, true)
        ), new AudioConfig(SoundEvents.BLOCK_SOUL_SAND_STEP, 0.25f, 1.1f, 6.0), 0, true);

        register("eclipsed_void_pulse", List.of(
            new ParticleConfig(ParticleTypes.REVERSE_PORTAL, 6, 0.3, 0.3, 0.3, 0.01, "spiral", 1.0, true),
            new ParticleConfig(ParticleTypes.ASH, 4, 0.2, 0.2, 0.2, 0.02, "burst", 0.5, true)
        ), new AudioConfig(SoundEvents.BLOCK_PORTAL_AMBIENT, 0.2f, 1.5f, 6.0), 0, true);

        register("eepy_sleepy_contentment", List.of(
            new ParticleConfig(ParticleTypes.NOTE, 3, 0.2, 0.3, 0.2, 0.01, "gentle_float", 0.8, true),
            new ParticleConfig(ParticleTypes.HAPPY_VILLAGER, 2, 0.1, 0.2, 0.1, 0.01, "burst", 0.4, true)
        ), new AudioConfig(SoundEvents.ENTITY_CAT_PURR, 0.3f, 0.8f, 6.0), 0, true);
    }

    private static void register(String eventName, List<ParticleConfig> particles, AudioConfig audio, int delayTicks, boolean serverSide) {
        FEEDBACK_REGISTRY.put(eventName, new FeedbackEffect(particles, audio, delayTicks, serverSide));
    }

    public static FeedbackEffect getFeedback(String eventName) {
        return FEEDBACK_REGISTRY.get(eventName);
    }

    public static boolean hasFeedback(String eventName) {
        return FEEDBACK_REGISTRY.containsKey(eventName);
    }

    public static void registerCustomFeedback(String eventName, FeedbackEffect effect) {
        FEEDBACK_REGISTRY.put(eventName, effect);
    }
}