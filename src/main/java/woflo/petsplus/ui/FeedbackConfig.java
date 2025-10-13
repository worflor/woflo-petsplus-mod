package woflo.petsplus.ui;

import net.minecraft.particle.EffectParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

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
        public final boolean performanceScaled; // New field for adaptive particle counts

        public ParticleConfig(ParticleEffect type, int count, double offsetX, double offsetY, double offsetZ,
                            double speed, String pattern, double radius, boolean adaptToEntitySize) {
            this(type, count, offsetX, offsetY, offsetZ, speed, pattern, radius, adaptToEntitySize, true);
        }
        
        public ParticleConfig(ParticleEffect type, int count, double offsetX, double offsetY, double offsetZ,
                            double speed, String pattern, double radius, boolean adaptToEntitySize, boolean performanceScaled) {
            this.type = type;
            this.count = count;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.speed = speed;
            this.pattern = pattern;
            this.radius = radius;
            this.adaptToEntitySize = adaptToEntitySize;
            this.performanceScaled = performanceScaled;
        }
        
        /**
         * Gets the performance-adjusted particle count based on system performance
         */
        public int getAdjustedCount() {
            if (!performanceScaled) return count;
            
            // Simple performance scaling - could be enhanced with actual performance metrics
            float performanceFactor = getPerformanceFactor();
            return Math.max(1, Math.round(count * performanceFactor));
        }
        
        private float getPerformanceFactor() {
            // Default implementation - could be enhanced to monitor actual performance
            // Returns a value between 0.5f and 1.0f based on system load
            return 0.8f; // Conservative default
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

    // Performance and memory optimization fields
    private static volatile boolean initialized = false;
    private static volatile boolean initializing = false;
    private static final ReentrantLock initLock = new ReentrantLock();
    private static final Map<String, FeedbackEffect> FEEDBACK_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<ParticleConfig>> PATTERN_CACHE = new ConcurrentHashMap<>();
    
    // Performance scaling configuration
    private static volatile float globalPerformanceFactor = 1.0f;
    private static volatile boolean performanceModeEnabled = true;

    /**
     * Ensures the configuration is initialized using double-checked locking pattern
     */
    private static void ensureInitialized() {
        if (!initialized) {
            initLock.lock();
            try {
                if (!initialized && !initializing) {
                    initializing = true;
                    try {
                        initializeDefaultEffects();
                        initialized = true;
                    } finally {
                        initializing = false;
                    }
                }
            } finally {
                initLock.unlock();
            }
        }
    }
    
    /**
     * Cleanup method to clear all registries and caches
     */
    public static void cleanup() {
        initLock.lock();
        try {
            FEEDBACK_REGISTRY.clear();
            PATTERN_CACHE.clear();
            initialized = false;
        } finally {
            initLock.unlock();
        }
    }
    
    /**
     * Reloads the configuration by cleaning up and reinitializing
     */
    public static void reloadConfiguration() {
        cleanup();
        ensureInitialized();
    }
    
    /**
     * Sets the global performance factor for particle scaling
     */
    public static void setGlobalPerformanceFactor(float factor) {
        globalPerformanceFactor = Math.max(0.1f, Math.min(1.0f, factor));
    }
    
    /**
     * Gets the current global performance factor
     */
    public static float getGlobalPerformanceFactor() {
        return globalPerformanceFactor;
    }
    
    /**
     * Enables or disables performance mode
     */
    public static void setPerformanceModeEnabled(boolean enabled) {
        performanceModeEnabled = enabled;
    }
    
    /**
     * Gets the cached particle pattern or creates a new one
     */
    private static List<ParticleConfig> getCachedPattern(String patternKey) {
        return PATTERN_CACHE.computeIfAbsent(patternKey, k -> {
            // Pattern creation logic would go here
            // For now, return an empty list as placeholder
            return List.of();
        });
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

        // Contagion feedback effects
        registerContagionFeedback();
    }

    private static void registerRoleParticles() {
        // Guardian: Protective blue shimmer
        register("guardian_ambient", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 2, 0.04, 0.04, 0.04, 0.01, "circle", 1.0, true)
        ), null, 0, true);

        // Striker: Sharp red sparks
        register("striker_ambient", List.of(
            new ParticleConfig(ParticleTypes.CRIT, 3, 0.08, 0.08, 0.08, 0.04, "burst", 0.5, true)
        ), null, 0, true);

        // Support: Green plus with hearts
        register("support_ambient", List.of(
            new ParticleConfig(ParticleTypes.HAPPY_VILLAGER, 2, 0.02, 0.02, 0.02, 0.01, "plus", 0.6, true),
            new ParticleConfig(ParticleTypes.HEART, 1, 0.06, 0.06, 0.06, 0.01, "burst", 0.1, false)
        ), null, 0, true);

        // Scout: Swirling golden trail
        register("scout_ambient", List.of(
            new ParticleConfig(ParticleTypes.WAX_ON, 2, 0.02, 0.02, 0.02, 0.01, "spiral", 0.4, true)
        ), null, 0, true);

        // Skyrider: Upward wisps
        register("skyrider_ambient", List.of(
            new ParticleConfig(ParticleTypes.CLOUD, 2, 0.05, 0.0, 0.05, 0.02, "upward", 0.3, true),
            new ParticleConfig(ParticleTypes.FIREWORK, 1, 0.08, 0.08, 0.08, 0.01, "burst", 0.1, false)
        ), null, 0, true);

        // Enchantment Bound: Purple mystical
        register("enchantment_bound_ambient", List.of(
            new ParticleConfig(ParticleTypes.ENCHANT, 3, 0.02, 0.02, 0.02, 0.02, "random", 0.5, true)
        ), null, 0, true);

        // Cursed One: Dark smoke with embers
        register("cursed_one_ambient", List.of(
            new ParticleConfig(ParticleTypes.LARGE_SMOKE, 2, 0.03, 0.02, 0.03, 0.01, "upward", 0.4, true),
            new ParticleConfig(ParticleTypes.LAVA, 1, 0.08, 0.08, 0.08, 0.01, "burst", 0.1, false)
        ), null, 0, true);

        // Eepy Eeper: Sleepy Z pattern
        register("eepy_eeper_ambient", List.of(
            new ParticleConfig(ParticleTypes.NOTE, 2, 0.01, 0.01, 0.01, 0.01, "z_pattern", 0.6, true),
            new ParticleConfig(ParticleTypes.POOF, 1, 0.08, 0.04, 0.08, 0.01, "burst", 0.1, false)
        ), null, 0, true);

        // Eclipsed: Void with reality tears
        register("eclipsed_ambient", List.of(
            new ParticleConfig(ParticleTypes.PORTAL, 2, 0.02, 0.02, 0.02, 0.01, "random", 0.6, true),
            new ParticleConfig(ParticleTypes.REVERSE_PORTAL, 1, 0.1, 0.08, 0.1, 0.02, "burst", 0.1, false)
        ), null, 0, true);
    }

    private static void registerAbilityFeedback() {
        // Guardian: Damage absorbed
        register("guardian_damage_absorbed", List.of(
            new ParticleConfig(ParticleTypes.ENCHANTED_HIT, 2, 0.15, 0.08, 0.15, 0.02, "burst", 0.5, false)
        ), new AudioConfig(SoundEvents.ITEM_SHIELD_BLOCK.value(), 0.3f, 1.2f, 8.0), 0, true);

        // Guardian: Shield bash rider triggered
        register("guardian_shield_bash", List.of(
            new ParticleConfig(ParticleTypes.SWEEP_ATTACK, 1, 0.0, 0.0, 0.0, 0.0, "burst", 0.0, false),
            new ParticleConfig(ParticleTypes.CRIT, 4, 0.25, 0.15, 0.25, 0.05, "burst", 0.7, false)
        ), new AudioConfig(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 0.4f, 0.9f, 10.0), 0, true);

        // Striker: Execution bonus triggered
        register("striker_execution", List.of(
            new ParticleConfig(ParticleTypes.DAMAGE_INDICATOR, 3, 0.18, 0.25, 0.18, 0.1, "burst", 0.5, false)
        ), new AudioConfig(SoundEvents.ENTITY_CAT_HISS, 0.4f, 1.1f, 8.0), 0, true);

        // Striker: Finisher mark applied
        register("striker_finisher_mark", List.of(
            new ParticleConfig(ParticleTypes.ANGRY_VILLAGER, 1, 0.0, 0.4, 0.0, 0.0, "burst", 0.0, false),
            new ParticleConfig(ParticleTypes.CRIT, 1, 0.08, 0.15, 0.08, 0.03, "circle", 0.25, false)
        ), new AudioConfig(SoundEvents.ENTITY_ARROW_HIT, 0.3f, 0.7f, 6.0), 0, true);

        // Support: Sitting regen area active - visualize the actual 6-block radius
        register("support_sitting_regen", List.of(
            new ParticleConfig(ParticleTypes.HEART, 4, 0.08, 0.04, 0.08, 0.01, "aura_radius_ground", 6.0, false),
            new ParticleConfig(ParticleTypes.HAPPY_VILLAGER, 2, 0.15, 0.02, 0.15, 0.01, "aura_radius_edge", 6.0, false)
        ), new AudioConfig(SoundEvents.ENTITY_BEE_LOOP, 0.1f, 1.2f, 8.0), 0, true);

        // Support: Potion aura pulse - visualize the actual potion radius
        register("support_potion_pulse", List.of(
            new ParticleConfig(EffectParticleEffect.of(ParticleTypes.EFFECT, 1.0F, 1.0F, 1.0F, 1.0F), 4, 0.15, 0.08, 0.15, 0.02, "aura_radius_edge", 6.0, false),
            new ParticleConfig(ParticleTypes.ENCHANT, 1, 0.08, 0.04, 0.08, 0.01, "area", 2.0, false)
        ), null, 0, true);

        // Scout: Loot magnetism activated - show the 12-block collection radius
        register("scout_loot_wisp", List.of(
            new ParticleConfig(ParticleTypes.WAX_ON, 4, 0.25, 0.08, 0.25, 0.02, "aura_radius_edge", 12.0, false),
            new ParticleConfig(ParticleTypes.ENCHANT, 2, 0.15, 0.04, 0.15, 0.01, "aura_radius_ground", 8.0, false),
            new ParticleConfig(ParticleTypes.END_ROD, 1, 0.08, 0.2, 0.08, 0.02, "spiral", 1.0, true)
        ), new AudioConfig(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 0.8f, 12.0), 0, true);

        // Eepy Eeper: Drowsy mist control burst
        register("eepy_eeper_drowsy_mist", List.of(
            new ParticleConfig(ParticleTypes.CLOUD, 6, 0.35, 0.06, 0.35, 0.01, "aura_radius_ground", 6.0, false),
            new ParticleConfig(ParticleTypes.ENCHANT, 2, 0.22, 0.25, 0.22, 0.0, "aura_radius_edge", 6.0, false),
            new ParticleConfig(ParticleTypes.FALLING_SPORE_BLOSSOM, 1, 0.2, 0.2, 0.2, 0.0, "area", 4.0, false)
        ), new AudioConfig(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 0.35f, 0.85f, 10.0), 0, true);

        // Skyrider: Wind assistance
        register("skyrider_windlash", List.of(
            new ParticleConfig(ParticleTypes.CLOUD, 6, 0.4, 0.15, 0.4, 0.05, "burst", 1.0, false),
            new ParticleConfig(ParticleTypes.FIREWORK, 1, 0.2, 0.3, 0.2, 0.02, "upward", 0.5, false)
        ), new AudioConfig(SoundEvents.ITEM_ELYTRA_FLYING, 0.2f, 1.4f, 8.0), 0, true);

        // Skyrider: Gust upwards escape
        register("skyrider_gust", List.of(
            new ParticleConfig(ParticleTypes.CLOUD, 6, 0.3, 0.2, 0.3, 0.03, "spiral", 1.0, true),
            new ParticleConfig(ParticleTypes.END_ROD, 2, 0.15, 0.25, 0.15, 0.015, "upward", 0.6, false)
        ), new AudioConfig(SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), 0.35f, 1.3f, 10.0), 0, true);

        // Enchantment Bound: Echo triggered
        register("enchantment_bound_echo", List.of(
            new ParticleConfig(ParticleTypes.ENCHANT, 4, 0.3, 0.2, 0.3, 0.03, "spiral", 1.2, true),
            new ParticleConfig(ParticleTypes.FIREWORK, 1, 0.0, 0.15, 0.0, 0.0, "burst", 0.0, false)
        ), new AudioConfig(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 0.3f, 1.1f, 8.0), 0, true);

        // Cursed One: Doom echo - show the 5-block weakness area
        register("cursed_one_doom_echo", List.of(
            new ParticleConfig(ParticleTypes.LARGE_SMOKE, 4, 0.25, 0.08, 0.25, 0.02, "aura_radius_ground", 5.0, false),
            new ParticleConfig(ParticleTypes.LAVA, 2, 0.15, 0.15, 0.15, 0.03, "aura_radius_edge", 5.0, false),
            new ParticleConfig(ParticleTypes.SOUL, 1, 0.08, 0.2, 0.08, 0.01, "spiral", 1.5, false)
        ), new AudioConfig(SoundEvents.ENTITY_PHANTOM_AMBIENT, 0.2f, 0.6f, 10.0), 0, true);

        // Cursed One: Auto-resurrect
        register("cursed_one_resurrect", List.of(
            new ParticleConfig(ParticleTypes.TOTEM_OF_UNDYING, 6, 0.6, 0.4, 0.6, 0.08, "burst", 1.2, false),
            new ParticleConfig(ParticleTypes.LAVA, 2, 0.25, 0.2, 0.25, 0.05, "circle", 1.0, false)
        ), new AudioConfig(SoundEvents.ITEM_TOTEM_USE, 0.5f, 0.8f, 16.0), 0, true);

        // Cursed One: Death bond - pet dies when owner dies
        register("cursed_one_death_bond", List.of(
            new ParticleConfig(ParticleTypes.SOUL, 6, 0.4, 0.6, 0.4, 0.03, "spiral", 1.2, true),
            new ParticleConfig(ParticleTypes.LARGE_SMOKE, 2, 0.25, 0.15, 0.25, 0.02, "burst", 0.8, false),
            new ParticleConfig(ParticleTypes.CRIMSON_SPORE, 1, 0.3, 0.3, 0.3, 0.02, "upward", 0.6, false)
        ), null, 0, true);

        // Cursed One: Self-resurrect - pet refuses to die (immortality)
        register("cursed_one_self_resurrect", List.of(
            new ParticleConfig(ParticleTypes.SOUL_FIRE_FLAME, 6, 0.5, 0.35, 0.5, 0.06, "upward", 1.0, true),
            new ParticleConfig(ParticleTypes.LARGE_SMOKE, 2, 0.25, 0.2, 0.25, 0.04, "circle", 1.2, false),
            new ParticleConfig(ParticleTypes.CRIMSON_SPORE, 0, 0.0, 0.0, 0.0, 0.0, "burst", 0.0, false)
        ), new AudioConfig(SoundEvents.ENTITY_WITHER_SPAWN, 0.7f, 1.5f, 14.0), 0, true);

        // Eclipsed: Void brand applied
        register("eclipsed_voidbrand", List.of(
            new ParticleConfig(ParticleTypes.PORTAL, 2, 0.15, 0.2, 0.15, 0.02, "circle", 0.4, false),
            new ParticleConfig(ParticleTypes.REVERSE_PORTAL, 1, 0.0, 0.15, 0.0, 0.0, "burst", 0.0, false)
        ), new AudioConfig(SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, 0.2f, 1.8f, 8.0), 0, true);

        // Eclipsed: Phase partner triggered
        register("eclipsed_phase_partner", List.of(
            new ParticleConfig(ParticleTypes.REVERSE_PORTAL, 4, 0.45, 0.3, 0.45, 0.04, "spiral", 1.0, true),
            new ParticleConfig(ParticleTypes.END_ROD, 1, 0.2, 0.15, 0.2, 0.02, "burst", 0.5, false)
        ), new AudioConfig(SoundEvents.ENTITY_SHULKER_TELEPORT, 0.3f, 1.3f, 10.0), 0, true);

        // Eclipsed: Event horizon zone - visualize the 6-block void zone
        register("eclipsed_event_horizon", List.of(
            new ParticleConfig(ParticleTypes.PORTAL, 6, 0.25, 0.08, 0.25, 0.02, "aura_radius_ground", 6.0, false),
            new ParticleConfig(ParticleTypes.REVERSE_PORTAL, 2, 0.15, 0.15, 0.15, 0.03, "aura_radius_edge", 6.0, false),
            new ParticleConfig(ParticleTypes.END_ROD, 1, 0.08, 0.2, 0.08, 0.01, "circle", 6.0, false)
        ), new AudioConfig(SoundEvents.BLOCK_PORTAL_TRIGGER, 0.3f, 0.7f, 12.0), 0, true);

        // Eppy Eeper: Nap regen - show the enhanced 4+1 block regen radius
        register("eepy_eeper_nap_regen", List.of(
            new ParticleConfig(ParticleTypes.HEART, 4, 0.15, 0.04, 0.15, 0.01, "aura_radius_ground", 5.0, false),
            new ParticleConfig(ParticleTypes.NOTE, 1, 0.08, 0.08, 0.08, 0.01, "aura_radius_edge", 4.0, false),
            new ParticleConfig(ParticleTypes.POOF, 0, 0.0, 0.0, 0.0, 0.0, "burst", 0.0, true)
        ), new AudioConfig(SoundEvents.ENTITY_CAT_PURR, 0.15f, 1.2f, 6.0), 0, true);

        // Striker: Hunt focus marker (called from StrikerHuntManager)
        if (!FEEDBACK_REGISTRY.containsKey("striker_mark_focus")) {
            register("striker_mark_focus", List.of(
                new ParticleConfig(ParticleTypes.CRIT, 2, 0.1, 0.15, 0.1, 0.02, "circle", 0.3, false)
            ), new AudioConfig(SoundEvents.UI_BUTTON_CLICK.value(), 0.15f, 1.3f, 6.0), 0, true);
        }

        // Centralized keys added for remaining direct spawns (budget-compliant)
        // Eepy: sleep link at pet
        if (!FEEDBACK_REGISTRY.containsKey("eepy_sleep_link")) {
            register("eepy_sleep_link", List.of(
                new ParticleConfig(ParticleTypes.SPORE_BLOSSOM_AIR, 2, 0.12, 0.0, 0.12, 0.008, "burst", 0.25, true)
            ), null, 0, true);
        }

        // Eepy: empowered player hint
        if (!FEEDBACK_REGISTRY.containsKey("eepy_player_empowered")) {
            register("eppy_player_empowered", List.of(
                new ParticleConfig(ParticleTypes.HAPPY_VILLAGER, 2, 0.15, 0.3, 0.15, 0.01, "burst", 0.25, true)
            ), null, 0, true);
        }

        // Eepy: pet recovery cue (includes sound)
        if (!FEEDBACK_REGISTRY.containsKey("eepy_pet_recovery")) {
            register("eepy_pet_recovery", List.of(
                new ParticleConfig(ParticleTypes.HAPPY_VILLAGER, 2, 0.18, 0.15, 0.18, 0.02, "burst", 0.25, true)
            ), new AudioConfig(SoundEvents.ENTITY_CAT_PURR, 0.6f, 1.2f, 6.0), 0, true);
        }

        // Cursed One: soul sacrifice activation
        if (!FEEDBACK_REGISTRY.containsKey("cursed_one_soul_sacrifice")) {
            register("cursed_one_soul_sacrifice", List.of(
                new ParticleConfig(ParticleTypes.SOUL_FIRE_FLAME, 5, 0.3, 0.25, 0.3, 0.02, "burst", 0.45, true),
                new ParticleConfig(ParticleTypes.SMOKE, 3, 0.22, 0.18, 0.22, 0.03, "burst", 0.35, false)
            ), new AudioConfig(SoundEvents.ITEM_TOTEM_USE, 0.6f, 0.6f, 12.0), 0, true);
        }
        // Layer an additional charge tone at similar location (balanced pitch)
        if (!FEEDBACK_REGISTRY.containsKey("cursed_one_soul_sacrifice_aux")) {
            registerCustomFeedback("cursed_one_soul_sacrifice_aux",
                new FeedbackEffect(List.of(), new AudioConfig(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 1.25f, 12.0), 0, true));
        }

        // Cooldown refresh subtle spiral + center pop, optional sparkles
        if (!FEEDBACK_REGISTRY.containsKey("cooldown_refresh")) {
            register("cooldown_refresh", List.of(
                new ParticleConfig(ParticleTypes.SOUL, 2, 0.0, 0.0, 0.0, 0.01, "spiral", 0.22, true),
                new ParticleConfig(ParticleTypes.END_ROD, 1, 0.0, 0.0, 0.0, 0.01, "burst", 0.0, false),
                new ParticleConfig(ParticleTypes.ENCHANT, 1, 0.0, 0.0, 0.0, 0.005, "burst", 0.0, false)
            ), null, 0, true);
        }

        // Dev crown ambient emission key (sustained; emitted at ambient cadence externally)
        if (!FEEDBACK_REGISTRY.containsKey("dev_crown_ambient")) {
            register("dev_crown_ambient", List.of(
                new ParticleConfig(ParticleTypes.END_ROD, 4, 0.01, 0.0, 0.01, 0.002, "circle", 0.4, true),
                new ParticleConfig(ParticleTypes.ENCHANT, 1, 0.01, 0.05, 0.01, 0.008, "burst", 0.0, false),
                new ParticleConfig(ParticleTypes.WAX_ON, 1, 0.04, 0.15, 0.04, 0.01, "burst", 0.0, false)
            ), null, 0, true);
        }

        // Afterimage finish burst: primary shards <=8, optional dust <=4, one-shot
        if (!FEEDBACK_REGISTRY.containsKey("afterimage_finish")) {
            register("afterimage_finish", List.of(
                // Use ENCHANTED_HIT to visually read as shards without requiring BlockStateParticleEffect here
                new ParticleConfig(ParticleTypes.ENCHANTED_HIT, 8, 0.35, 0.25, 0.35, 0.08, "burst", 0.6, false),
                // Subtle ash as secondary dust shimmer within cap
                new ParticleConfig(ParticleTypes.WHITE_ASH, 4, 0.25, 0.2, 0.25, 0.04, "burst", 0.45, false)
            ), null, 0, true);
        }
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
            new ParticleConfig(ParticleTypes.TOTEM_OF_UNDYING, 6, 0.4, 0.6, 0.4, 0.05, "burst", 0.8, true),
            new ParticleConfig(ParticleTypes.FIREWORK, 1, 0.2, 0.35, 0.2, 0.03, "upward", 0.6, true)
        ), new AudioConfig(SoundEvents.ENTITY_VILLAGER_YES, 0.4f, 1.1f, 12.0), 0, true);

        // Pet bond strengthened
        register("bond_strengthened", List.of(
            new ParticleConfig(ParticleTypes.HEART, 2, 0.2, 0.3, 0.2, 0.02, "burst", 0.5, false)
        ), new AudioConfig(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.4f, 8.0), 0, true);

        // Configuration changed
        register("config_changed", List.of(
            new ParticleConfig(ParticleTypes.ENCHANT, 1, 0.08, 0.15, 0.08, 0.01, "circle", 0.25, false)
        ), new AudioConfig(SoundEvents.UI_BUTTON_CLICK.value(), 0.2f, 1.0f, 4.0), 0, true);
    }

    private static void registerTributeOrbitalFeedback() {
        // Level 10 - Single orbital (Gold theme)
        register("tribute_orbital_10", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 2, 0.02, 0.02, 0.02, 0.01, "orbital_single", 1.5, true)
        ), null, 0, true);

        // Level 20 - Dual orbital (Diamond theme)
        register("tribute_orbital_20", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 2, 0.02, 0.02, 0.02, 0.01, "orbital_dual", 1.5, true),
            new ParticleConfig(ParticleTypes.ENCHANT, 1, 0.01, 0.01, 0.01, 0.005, "orbital_dual", 2.0, true)
        ), null, 0, true);

        // Level 30 - Triple orbital (Netherite theme)
        register("tribute_orbital_30", List.of(
            new ParticleConfig(ParticleTypes.SOUL_FIRE_FLAME, 2, 0.02, 0.02, 0.02, 0.01, "orbital_triple", 1.5, true)
        ), null, 0, true);

        // Tribute completion effects
        register("tribute_complete_10", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 4, 0.25, 0.25, 0.25, 0.06, "burst", 0.8, true),
            new ParticleConfig(ParticleTypes.CRIT, 2, 0.2, 0.2, 0.2, 0.04, "circle", 0.8, true)
        ), new AudioConfig(SoundEvents.ENTITY_VILLAGER_YES, 0.8f, 1.4f, 12.0), 0, true);

        register("tribute_complete_20", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 5, 0.3, 0.3, 0.3, 0.08, "burst", 1.0, true),
            new ParticleConfig(ParticleTypes.ENCHANT, 3, 0.25, 0.25, 0.25, 0.05, "spiral", 1.2, true)
        ), new AudioConfig(SoundEvents.ENTITY_VILLAGER_YES, 0.9f, 1.5f, 14.0), 0, true);

        register("tribute_complete_30", List.of(
            new ParticleConfig(ParticleTypes.END_ROD, 6, 0.35, 0.35, 0.35, 0.1, "burst", 1.2, true),
            new ParticleConfig(ParticleTypes.SOUL_FIRE_FLAME, 2, 0.3, 0.3, 0.3, 0.06, "spiral", 1.2, true),
            new ParticleConfig(ParticleTypes.EXPLOSION, 0, 0.0, 0.0, 0.0, 0.0, "burst", 0.0, false)
        ), new AudioConfig(SoundEvents.ENTITY_VILLAGER_YES, 1.0f, 1.6f, 16.0), 0, true);
    }

    private static void registerPettingFeedback() {
        // Base petting effects
        if (!FEEDBACK_REGISTRY.containsKey("pet_hearts")) {
            register("pet_hearts", List.of(
                new ParticleConfig(ParticleTypes.HEART, 4, 0.25, 0.3, 0.25, 0.02, "burst", 0.5, true)
            ), null, 0, true);
        }

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

    private static void registerContagionFeedback() {
        // Positive contagion - Green sparkles between pets (joy, relief, contentment)
        register("contagion_positive", List.of(
            new ParticleConfig(ParticleTypes.HAPPY_VILLAGER, 3, 0.2, 0.1, 0.2, 0.01, "circle", 0.8, true),
            new ParticleConfig(ParticleTypes.END_ROD, 2, 0.1, 0.05, 0.1, 0.005, "burst", 0.3, false)
        ), null, 0, true);

        // Negative contagion - Red particles for fear/distress (foreboding, angst, startle)
        register("contagion_negative", List.of(
            new ParticleConfig(ParticleTypes.ANGRY_VILLAGER, 2, 0.15, 0.1, 0.15, 0.01, "circle", 0.6, true),
            new ParticleConfig(ParticleTypes.SMOKE, 1, 0.1, 0.05, 0.1, 0.008, "burst", 0.2, false)
        ), null, 0, true);

        // Neutral contagion - White particles for curiosity/contentment (curious, vigilant, stoic)
        register("contagion_neutral", List.of(
            new ParticleConfig(ParticleTypes.WHITE_ASH, 3, 0.2, 0.1, 0.2, 0.01, "circle", 0.7, true),
            new ParticleConfig(ParticleTypes.ENCHANT, 1, 0.1, 0.05, 0.1, 0.005, "burst", 0.2, false)
        ), null, 0, true);

        // Combat contagion - Orange/red for combat emotions (triumph, Guardian Vigil)
        register("contagion_combat", List.of(
            new ParticleConfig(ParticleTypes.CRIT, 2, 0.2, 0.1, 0.2, 0.015, "circle", 0.8, true),
            new ParticleConfig(ParticleTypes.FLAME, 1, 0.1, 0.05, 0.1, 0.008, "burst", 0.3, false)
        ), null, 0, true);

        // Discovery contagion - Blue/purple for exploration emotions (curiosity, wonder)
        register("contagion_discovery", List.of(
            new ParticleConfig(ParticleTypes.ENCHANT, 3, 0.2, 0.1, 0.2, 0.01, "circle", 0.8, true),
            new ParticleConfig(ParticleTypes.PORTAL, 1, 0.1, 0.05, 0.1, 0.005, "burst", 0.2, false)
        ), null, 0, true);

        // Social contagion - Pink/hearts for social emotions (bonding, affection)
        register("contagion_social", List.of(
            new ParticleConfig(ParticleTypes.HEART, 2, 0.2, 0.1, 0.2, 0.01, "circle", 0.6, true),
            new ParticleConfig(ParticleTypes.HAPPY_VILLAGER, 2, 0.1, 0.05, 0.1, 0.005, "burst", 0.3, false)
        ), null, 0, true);
    }

    private static void register(String eventName, List<ParticleConfig> particles, AudioConfig audio, int delayTicks, boolean serverSide) {
        if (eventName == null || eventName.trim().isEmpty()) {
            throw new IllegalArgumentException("Event name cannot be null or empty");
        }
        if (delayTicks < 0) {
            throw new IllegalArgumentException("Delay ticks cannot be negative");
        }
        // Particles and audio can be null for effects that only have one or the other
        FEEDBACK_REGISTRY.put(eventName, new FeedbackEffect(particles, audio, delayTicks, serverSide));
    }

    public static FeedbackEffect getFeedback(String eventName) {
        if (!initialized) {
            if (!initializing) {
                ensureInitialized();
            } else if (eventName == null || eventName.trim().isEmpty()) {
                return null;
            }
        }
        if (eventName == null || eventName.trim().isEmpty()) {
            return null;
        }
        return FEEDBACK_REGISTRY.get(eventName);
    }

    public static boolean hasFeedback(String eventName) {
        if (!initialized && !initializing) {
            ensureInitialized();
        }
        if (eventName == null || eventName.trim().isEmpty()) {
            return false;
        }
        return FEEDBACK_REGISTRY.containsKey(eventName);
    }

    public static void registerCustomFeedback(String eventName, FeedbackEffect effect) {
        if (!initialized && !initializing) {
            ensureInitialized();
        }
        if (eventName == null || eventName.trim().isEmpty()) {
            throw new IllegalArgumentException("Event name cannot be null or empty");
        }
        if (effect == null) {
            throw new IllegalArgumentException("Feedback effect cannot be null");
        }
        FEEDBACK_REGISTRY.put(eventName, effect);
    }
}
