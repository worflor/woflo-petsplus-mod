package woflo.petsplus.ui;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.PetRole;

/**
 * Manages subtle particle effects for different pet roles.
 * Each role has a unique particle pattern that helps identify the pet's role.
 */
public class ParticleEffectManager {

    private static final int PARTICLE_INTERVAL_TICKS = 80; // Every 4 seconds
    private static final double BASE_HEIGHT_OFFSET = 0.3; // Above pet center

    /**
     * Emit role-specific particle effects for a pet if enough time has passed.
     * Now delegates to the new FeedbackManager system for consistency.
     */
    public static void emitRoleParticles(MobEntity pet, ServerWorld world, long currentTick) {
        // Delegate to the new feedback system
        FeedbackManager.emitRoleAmbientParticles(pet, world, currentTick);
    }

    /**
     * Calculate height offset based on pet size and type.
     */
    private static double calculateHeightOffset(MobEntity pet) {
        double petHeight = pet.getHeight();

        // For very small pets (like parrots), place particles above
        if (petHeight < 0.8) {
            return petHeight + 0.2;
        }
        // For medium pets, place at center-top
        else if (petHeight < 1.5) {
            return petHeight * 0.7;
        }
        // For large pets, place at mid-height
        else {
            return petHeight * 0.5;
        }
    }

    /**
     * Guardian: Gentle blue shimmer in a protective circle
     */
    private static void emitGuardianParticles(ServerWorld world, Vec3d pos, MobEntity pet) {
        double radius = Math.min(pet.getWidth() * 0.8, 1.0);
        for (int i = 0; i < 4; i++) {
            double angle = (i / 4.0) * 2 * Math.PI;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;

            world.spawnParticles(ParticleTypes.END_ROD,
                x, pos.y, z,
                1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /**
     * Striker: Sharp red sparks in quick bursts
     */
    private static void emitStrikerParticles(ServerWorld world, Vec3d pos, MobEntity pet) {
        // Quick burst of sharp particles
        for (int i = 0; i < 3; i++) {
            double offsetX = (world.getRandom().nextDouble() - 0.5) * 0.5;
            double offsetZ = (world.getRandom().nextDouble() - 0.5) * 0.5;

            world.spawnParticles(ParticleTypes.CRIT,
                pos.x + offsetX, pos.y, pos.z + offsetZ,
                2, 0.1, 0.1, 0.1, 0.05);
        }
    }

    /**
     * Support: Gentle green plus pattern with heart accents
     */
    private static void emitSupportParticles(ServerWorld world, Vec3d pos, MobEntity pet) {
        // Plus pattern
        double size = Math.min(pet.getWidth() * 0.6, 0.8);

        // Horizontal line
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
            pos.x - size/2, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.01);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
            pos.x + size/2, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.01);

        // Vertical line
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
            pos.x, pos.y, pos.z - size/2, 1, 0.02, 0.02, 0.02, 0.01);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
            pos.x, pos.y, pos.z + size/2, 1, 0.02, 0.02, 0.02, 0.01);

        // Occasional heart
        if (world.getRandom().nextFloat() < 0.3) {
            world.spawnParticles(ParticleTypes.HEART,
                pos.x, pos.y + 0.2, pos.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /**
     * Scout: Swirling golden trail suggesting movement
     */
    private static void emitScoutParticles(ServerWorld world, Vec3d pos, MobEntity pet) {
        // Spiral trail
        double time = world.getTime() * 0.1;
        for (int i = 0; i < 3; i++) {
            double angle = time + (i * Math.PI * 2 / 3);
            double radius = 0.4 + Math.sin(time) * 0.1;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;

            world.spawnParticles(ParticleTypes.WAX_ON,
                x, pos.y + Math.sin(angle) * 0.1, z,
                1, 0.02, 0.02, 0.02, 0.01);
        }
    }

    /**
     * Skyrider: Upward-flowing white wisps
     */
    private static void emitSkyriderParticles(ServerWorld world, Vec3d pos, MobEntity pet) {
        // Upward flowing particles
        for (int i = 0; i < 3; i++) {
            double offsetX = (world.getRandom().nextDouble() - 0.5) * 0.3;
            double offsetZ = (world.getRandom().nextDouble() - 0.5) * 0.3;

            world.spawnParticles(ParticleTypes.CLOUD,
                pos.x + offsetX, pos.y - 0.2, pos.z + offsetZ,
                1, 0.05, 0.0, 0.05, 0.02);
        }

        // Occasional white sparkle higher up
        if (world.getRandom().nextFloat() < 0.4) {
            world.spawnParticles(ParticleTypes.FIREWORK,
                pos.x, pos.y + 0.5, pos.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /**
     * Enchantment Bound: Purple enchantment sparkles in mystical patterns
     */
    private static void emitEnchantmentBoundParticles(ServerWorld world, Vec3d pos, MobEntity pet) {
        // Enchantment table-like particles
        for (int i = 0; i < 4; i++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double radius = 0.3 + world.getRandom().nextDouble() * 0.3;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;

            world.spawnParticles(ParticleTypes.ENCHANT,
                x, pos.y + world.getRandom().nextDouble() * 0.3, z,
                1, 0.02, 0.02, 0.02, 0.02);
        }
    }

    /**
     * Cursed One: Dark smoke with occasional red embers
     */
    private static void emitCursedOneParticles(ServerWorld world, Vec3d pos, MobEntity pet) {
        // Dark smoke
        for (int i = 0; i < 2; i++) {
            double offsetX = (world.getRandom().nextDouble() - 0.5) * 0.4;
            double offsetZ = (world.getRandom().nextDouble() - 0.5) * 0.4;

            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                pos.x + offsetX, pos.y - 0.1, pos.z + offsetZ,
                1, 0.03, 0.02, 0.03, 0.01);
        }

        // Occasional red ember
        if (world.getRandom().nextFloat() < 0.3) {
            world.spawnParticles(ParticleTypes.LAVA,
                pos.x, pos.y, pos.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /**
     * Eepy Eeper: Soft z-shaped pattern with sleepy particles
     */
    private static void emitEepyEeperParticles(ServerWorld world, Vec3d pos, MobEntity pet) {
        // Z-pattern for sleepiness
        double time = world.getTime() * 0.05;
        for (int i = 0; i < 3; i++) {
            double progress = (i / 3.0 + time) % 1.0;
            double x = pos.x + (progress - 0.5) * 0.6;
            double y = pos.y + Math.sin(progress * Math.PI) * 0.2;
            double z = pos.z + Math.cos(progress * Math.PI * 2) * 0.1;

            world.spawnParticles(ParticleTypes.NOTE,
                x, y, z, 1, 0.01, 0.01, 0.01, 0.01);
        }

        // Soft puff occasionally
        if (world.getRandom().nextFloat() < 0.2) {
            world.spawnParticles(ParticleTypes.POOF,
                pos.x, pos.y + 0.1, pos.z, 2, 0.1, 0.05, 0.1, 0.01);
        }
    }

    /**
     * Eclipsed: Dark void particles with purple edge effects
     */
    private static void emitEclipsedParticles(ServerWorld world, Vec3d pos, MobEntity pet) {
        // Void-like dark particles
        for (int i = 0; i < 3; i++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double radius = 0.2 + world.getRandom().nextDouble() * 0.4;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;

            world.spawnParticles(ParticleTypes.PORTAL,
                x, pos.y, z, 1, 0.02, 0.02, 0.02, 0.01);
        }

        // Occasional reality tear effect
        if (world.getRandom().nextFloat() < 0.25) {
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                pos.x, pos.y + 0.1, pos.z, 2, 0.15, 0.1, 0.15, 0.02);
        }
    }

    /**
     * Check if pet should emit particles (only when visible and not in combat for subtlety).
     */
    public static boolean shouldEmitParticles(MobEntity pet, ServerWorld world) {
        // Don't emit particles if pet is in active combat (to reduce visual noise)
        var component = woflo.petsplus.state.PetComponent.get(pet);
        if (component != null) {
            long lastAttack = component.getLastAttackTick();
            if (world.getTime() - lastAttack < 60) { // 3 seconds after combat
                return false;
            }
        }

        // Only emit if pet is alive and in a loaded chunk
        return pet.isAlive() && !pet.isRemoved();
    }
}