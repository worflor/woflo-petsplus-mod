package woflo.petsplus.ui;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.events.TributeHandler;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages orbital particle effects for pets waiting for tribute.
 * Creates electron-shell-like orbital patterns that scale with tribute levels.
 */
public class TributeOrbitalEffects {

    private static final Map<String, OrbitalConfiguration> ORBITAL_CONFIGS = new HashMap<>();
    private static final long ORBITAL_UPDATE_INTERVAL = 80; // 4 seconds, aligned with ambient particles

    // Performance optimization: Cache orbital positions for smooth interpolation
    private static final int CACHE_SIZE = 100; // Number of pre-computed positions per ring
    private static final int MAX_CACHE_ENTRIES = 50; // Maximum number of distinct orbital paths to cache
    // Using LinkedHashMap with insertion-order for proper FIFO eviction (oldest entries removed first)
    private static final Map<String, List<Vec3d>> ORBITAL_PATH_CACHE = new java.util.LinkedHashMap<>(
        MAX_CACHE_ENTRIES + 1, 0.75f, false);

    static {
        initializeOrbitalConfigurations();
    }

    /**
     * Configuration for orbital effects at different tribute levels
     */
    public static class OrbitalConfiguration {
        public final List<OrbitalRing> rings;
        public final double intensityMultiplier;
        public final boolean hasTrails;
        public final ParticleEffect primaryParticle;
        public final ParticleEffect secondaryParticle;
        public final Vector3f primaryColor;
        public final Vector3f secondaryColor;

        public OrbitalConfiguration(List<OrbitalRing> rings, double intensityMultiplier, boolean hasTrails,
                                  ParticleEffect primaryParticle, ParticleEffect secondaryParticle,
                                  Vector3f primaryColor, Vector3f secondaryColor) {
            this.rings = rings;
            this.intensityMultiplier = intensityMultiplier;
            this.hasTrails = hasTrails;
            this.primaryParticle = primaryParticle;
            this.secondaryParticle = secondaryParticle;
            this.primaryColor = primaryColor;
            this.secondaryColor = secondaryColor;
        }
    }

    /**
     * Represents a single orbital ring with its physics parameters
     */
    public static class OrbitalRing {
        public final double radius;
        public final double period; // seconds for one complete orbit
        public final double inclination; // degrees
        public final double phaseOffset; // radians
        public final int particleCount;
        public final double verticalOscillation; // amplitude of Y oscillation

        public OrbitalRing(double radius, double period, double inclination, double phaseOffset,
                          int particleCount, double verticalOscillation) {
            this.radius = radius;
            this.period = period;
            this.inclination = inclination;
            this.phaseOffset = phaseOffset;
            this.particleCount = particleCount;
            this.verticalOscillation = verticalOscillation;
        }
    }

    private static void initializeOrbitalConfigurations() {
        // Level 10 - Single equatorial orbital (Gold theme)
        ORBITAL_CONFIGS.put("tribute_orbital_10", new OrbitalConfiguration(
            List.of(new OrbitalRing(1.5, 6.0, 0.0, 0.0, 6, 0.1)),
            1.0,
            false,
            ParticleTypes.CRIT, // Gold-like particles
            ParticleTypes.WAX_ON,
            new Vector3f(1.0f, 0.84f, 0.0f), // Gold
            new Vector3f(1.0f, 1.0f, 0.5f)  // Light yellow
        ));

        // Level 20 - Dual orbital system (Diamond theme)
        ORBITAL_CONFIGS.put("tribute_orbital_20", new OrbitalConfiguration(
            List.of(
                new OrbitalRing(1.5, 5.0, 0.0, 0.0, 8, 0.1),
                new OrbitalRing(2.0, 7.0, 45.0, Math.PI, 6, 0.15)
            ),
            1.2,
            true,
            ParticleTypes.END_ROD, // Cyan-like particles
            ParticleTypes.ENCHANT,
            new Vector3f(0.0f, 0.75f, 1.0f), // Cyan
            new Vector3f(0.5f, 0.9f, 1.0f)  // Light blue
        ));

        // Level 30 - Triple orbital shell (Netherite theme)
        ORBITAL_CONFIGS.put("tribute_orbital_30", new OrbitalConfiguration(
            List.of(
                new OrbitalRing(1.5, 4.0, 0.0, 0.0, 12, 0.1),
                new OrbitalRing(2.0, 5.5, 45.0, Math.PI / 2, 10, 0.15),
                new OrbitalRing(2.5, 7.0, 90.0, Math.PI, 8, 0.2)
            ),
            1.5,
            true,
            ParticleTypes.SOUL_FIRE_FLAME, // Purple-like particles
            ParticleTypes.PORTAL,
            new Vector3f(0.55f, 0.0f, 0.55f), // Purple
            new Vector3f(0.55f, 0.0f, 0.0f)  // Dark red
        ));
    }

    /**
     * Calculate orbital position for a particle at given time
     */
    public static Vec3d calculateOrbitalPosition(MobEntity pet, OrbitalRing ring, double time) {
        Vec3d petPos = pet.getLerpedPos(1.0f);
        double entityRadius = Math.max(pet.getWidth(), pet.getHeight()) * 0.5;
        double effectiveRadius = ring.radius * (1.0 + entityRadius * 0.2); // Scale with pet size

        // Calculate the angle based on time and period
        double angle = (time * 2 * Math.PI) / ring.period + ring.phaseOffset;

        // Base circular motion in XZ plane
        double x = effectiveRadius * Math.cos(angle);
        double z = effectiveRadius * Math.sin(angle);

        // Apply inclination tilt for 3D orbital path
        double inclinationRad = Math.toRadians(ring.inclination);
        double y = effectiveRadius * ring.verticalOscillation * Math.sin(angle * 1.5) * Math.sin(inclinationRad);

        // Add pet's position offset and center the orbital around pet's center
        double centerY = petPos.y + pet.getHeight() * 0.5;

        return new Vec3d(
            petPos.x + x,
            centerY + y,
            petPos.z + z
        );
    }

    /**
     * Emit orbital effects for a pet waiting for tribute
     */
    public static void emitTributeOrbital(MobEntity pet, ServerWorld world, long currentTick) {
        if (currentTick % ORBITAL_UPDATE_INTERVAL != 0) return;

        PetComponent petComp = PetComponent.get(pet);
        if (petComp == null || !TributeHandler.isPetWaitingForTribute(pet)) return;

        // Get tribute level
        int tributeLevel = getTributeLevel(petComp);
        if (tributeLevel == 0) return;

        String configKey = "tribute_orbital_" + tributeLevel;
        OrbitalConfiguration config = ORBITAL_CONFIGS.get(configKey);
        if (config == null) return;

        // Check if orbitals are enabled in config
        if (!shouldEmitOrbitalEffects(pet, world, petComp)) return;

        PetsPlusConfig configInstance = PetsPlusConfig.getInstance();
        double timeScale = configInstance.getTributeOrbitalTimeScale();
        double time = world.getTime() * timeScale;

        // Calculate intensity based on config
        double configIntensity = configInstance.getTributeOrbitalIntensityMultiplier();
        double intensityMultiplier = config.intensityMultiplier * configIntensity;

        // Emit particles for each orbital ring
        for (OrbitalRing ring : config.rings) {
            emitOrbitalRing(pet, world, ring, config, time, intensityMultiplier);
        }

        // Add occasional burst effects for level 30
        if (tributeLevel == 30 && world.getRandom().nextFloat() < 0.1) {
            emitNetheriteBurstEffect(pet, world, config);
        }
    }

    private static void emitOrbitalRing(MobEntity pet, ServerWorld world, OrbitalRing ring,
                                      OrbitalConfiguration config, double time, double intensityMultiplier) {
        int effectiveCount = (int) Math.max(1, ring.particleCount * intensityMultiplier);

        // Batch particle spawning for better performance
        for (int i = 0; i < effectiveCount; i++) {
            double particleTime = time + (i * ring.period / effectiveCount);
            Vec3d pos = getInterpolatedOrbitalPosition(pet, ring, particleTime);

            // Primary particle with slight randomization for organic feel
            double offsetX = (world.getRandom().nextDouble() - 0.5) * 0.02;
            double offsetY = (world.getRandom().nextDouble() - 0.5) * 0.02;
            double offsetZ = (world.getRandom().nextDouble() - 0.5) * 0.02;

            world.spawnParticles(config.primaryParticle,
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                1, 0.01, 0.01, 0.01, 0.005);

            // Trail particles with fade effect (for higher levels)
            if (config.hasTrails && i % 2 == 0) {
                Vec3d trailPos = getInterpolatedOrbitalPosition(pet, ring, particleTime - 0.15);
                world.spawnParticles(config.secondaryParticle,
                    trailPos.x, trailPos.y, trailPos.z,
                    1, 0.005, 0.005, 0.005, 0.002);
            }
        }

        // Add pulse effect for long waiting times
        if (world.getTime() % 200 == 0) { // Pulse every 10 seconds
            Vec3d petPos = pet.getLerpedPos(1.0f);
            double centerY = petPos.y + pet.getHeight() * 0.5;

            // Subtle pulse effect to indicate waiting state
            world.spawnParticles(config.primaryParticle,
                petPos.x, centerY, petPos.z,
                2, 0.08, 0.08, 0.08, 0.005);
        }
    }

    private static void emitNetheriteBurstEffect(MobEntity pet, ServerWorld world, OrbitalConfiguration config) {
        Vec3d petPos = pet.getLerpedPos(1.0f);
        double centerY = petPos.y + pet.getHeight() * 0.5;

        // Explosive particle burst
        world.spawnParticles(ParticleTypes.EXPLOSION,
            petPos.x, centerY, petPos.z,
            1, 0.0, 0.0, 0.0, 0.0);

        // Ring of fire particles
        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0) * 2 * Math.PI;
            double radius = 1.0;
            double x = petPos.x + Math.cos(angle) * radius;
            double z = petPos.z + Math.sin(angle) * radius;

            world.spawnParticles(ParticleTypes.FLAME,
                x, centerY, z,
                2, 0.1, 0.1, 0.1, 0.02);
        }
    }


    private static boolean shouldEmitOrbitalEffects(MobEntity pet, ServerWorld world, PetComponent petComp) {
        PetsPlusConfig config = PetsPlusConfig.getInstance();

        // Check if orbital effects are enabled
        if (!config.isTributeOrbitalEnabled()) return false;

        // Respect combat pause configuration
        if (config.isTributeOrbitalCombatPauseEnabled() &&
            world.getTime() - petComp.getLastAttackTick() < 60) return false;

        // Check if pet is alive and loaded
        if (!pet.isAlive() || pet.isRemoved()) return false;

        // Check visibility (same rules as ambient particles)
        return FeedbackManager.shouldEmitAmbientParticles(pet, world);
    }

    private static int getTributeLevel(PetComponent petComp) {
        int level = petComp.getLevel();
        PetRoleType roleType = petComp.getRoleType();
        if (roleType == null) return 0;

        // Get tribute milestones from role configuration
        var milestones = roleType.xpCurve().tributeMilestones();
        if (!milestones.contains(level)) return 0;

        // Check if milestone is not yet unlocked
        if (petComp.isMilestoneUnlocked(level)) return 0;

        // Return the milestone level for orbital configuration lookup
        return level;
    }

    /**
     * Get orbital configuration for a specific tribute level
     */
    public static OrbitalConfiguration getOrbitalConfig(int tributeLevel) {
        return ORBITAL_CONFIGS.get("tribute_orbital_" + tributeLevel);
    }

    /**
     * Register custom orbital configuration
     */
    public static void registerOrbitalConfig(String key, OrbitalConfiguration config) {
        ORBITAL_CONFIGS.put(key, config);
    }

    /**
     * Check if orbital effects are available for a tribute level
     */
    public static boolean hasOrbitalConfig(int tributeLevel) {
        return ORBITAL_CONFIGS.containsKey("tribute_orbital_" + tributeLevel);
    }

    /**
     * Pre-compute orbital path for performance optimization
     */
    private static List<Vec3d> getOrCreateOrbitalPath(OrbitalRing ring, String ringKey) {
        return ORBITAL_PATH_CACHE.computeIfAbsent(ringKey, key -> {
            // Prevent unbounded cache growth by removing oldest entry (FIFO eviction via LinkedHashMap)
            if (ORBITAL_PATH_CACHE.size() >= MAX_CACHE_ENTRIES) {
                // Remove first entry - LinkedHashMap guarantees insertion-order iteration
                String firstKey = ORBITAL_PATH_CACHE.keySet().iterator().next();
                ORBITAL_PATH_CACHE.remove(firstKey);
            }
            
            List<Vec3d> path = new ArrayList<>(CACHE_SIZE);
            for (int i = 0; i < CACHE_SIZE; i++) {
                double time = (i / (double) CACHE_SIZE) * ring.period;
                double angle = (time * 2 * Math.PI) / ring.period + ring.phaseOffset;

                double x = ring.radius * Math.cos(angle);
                double z = ring.radius * Math.sin(angle);

                double inclinationRad = Math.toRadians(ring.inclination);
                double y = ring.radius * ring.verticalOscillation * Math.sin(angle * 1.5) * Math.sin(inclinationRad);

                path.add(new Vec3d(x, y, z));
            }
            return path;
        });
    }

    /**
     * Get interpolated position from cached orbital path
     */
    private static Vec3d getInterpolatedOrbitalPosition(MobEntity pet, OrbitalRing ring, double time) {
        String ringKey = String.format("%.1f_%.1f_%.1f", ring.radius, ring.period, ring.inclination);
        List<Vec3d> path = getOrCreateOrbitalPath(ring, ringKey);

        if (path.isEmpty()) {
            return calculateOrbitalPosition(pet, ring, time); // Fallback
        }

        Vec3d petPos = pet.getLerpedPos(1.0f);
        double entityRadius = Math.max(pet.getWidth(), pet.getHeight()) * 0.5;

        // Get position from cached path
        double normalizedTime = (time % ring.period) / ring.period;
        int index = (int) (normalizedTime * (path.size() - 1));
        Vec3d pathPos = path.get(Math.max(0, Math.min(index, path.size() - 1)));

        // Scale and transform to pet position
        double scale = 1.0 + entityRadius * 0.2;
        double centerY = petPos.y + pet.getHeight() * 0.5;

        return new Vec3d(
            petPos.x + pathPos.x * scale,
            centerY + pathPos.y * scale,
            petPos.z + pathPos.z * scale
        );
    }

    /**
     * Create fade-out effect when tribute is paid
     */
    public static void emitTributeCompleteEffect(MobEntity pet, ServerWorld world, int tributeLevel) {
        OrbitalConfiguration config = getOrbitalConfig(tributeLevel);
        if (config == null) return;

        Vec3d petPos = pet.getLerpedPos(1.0f);
        double centerY = petPos.y + pet.getHeight() * 0.5;

        // Smooth fade-in effect with alpha blending simulation
        for (OrbitalRing ring : config.rings) {
            int fadeSteps = 20;
            for (int step = 0; step < fadeSteps; step++) {
                double alpha = 1.0 - (step / (double) fadeSteps);
                int particleCount = (int) Math.max(1, ring.particleCount * alpha);

                for (int i = 0; i < particleCount; i++) {
                    double angle = (i / (double) particleCount) * 2 * Math.PI;
                    double radius = ring.radius * alpha * 0.8; // Converge to center

                    double x = petPos.x + Math.cos(angle) * radius;
                    double z = petPos.z + Math.sin(angle) * radius;
                    double y = centerY + (world.getRandom().nextDouble() - 0.5) * 0.2;

                    // Use different particles based on alpha for fade effect
                    ParticleEffect particle = alpha > 0.5 ? config.primaryParticle : config.secondaryParticle;
                    world.spawnParticles(particle,
                        x, y, z,
                        1, 0.02, 0.02, 0.02, 0.01);
                }
            }
        }

        // Enhanced central burst effect with audio
        world.spawnParticles(ParticleTypes.END_ROD,
            petPos.x, centerY, petPos.z,
            25, 0.6, 0.6, 0.6, 0.12);

        // Additional celebratory effects
        world.spawnParticles(ParticleTypes.FIREWORK,
            petPos.x, centerY + 1.0, petPos.z,
            8, 0.3, 0.3, 0.3, 0.05);

        // Sound effect
        world.playSound(null, petPos.x, centerY, petPos.z,
            SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
            SoundCategory.NEUTRAL, 0.5f, 1.2f);
    }

    /**
     * Clear cached orbital paths (for memory management)
     */
    public static void clearOrbitalCache() {
        ORBITAL_PATH_CACHE.clear();
    }

    /**
     * Get cache statistics for debugging
     */
    public static String getCacheStats() {
        return String.format("Orbital cache: %d paths, ~%d positions",
            ORBITAL_PATH_CACHE.size(),
            ORBITAL_PATH_CACHE.values().stream().mapToInt(List::size).sum());
    }
}