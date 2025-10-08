package woflo.petsplus.ui;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages subtle particle effects for cooldown refreshes on pets
 */
public class CooldownParticleManager {
    
    // Track last cooldown refresh time per pet to avoid spam
    private static final Map<UUID, Long> lastRefreshTime = new HashMap<>();
    private static final long MINIMUM_REFRESH_INTERVAL = 5; // 0.25 seconds minimum between effects
    private static long nextCleanupTick;
    
    /**
     * Trigger a subtle cooldown refresh particle effect around a pet
     */
    public static void triggerCooldownRefresh(ServerWorld world, MobEntity pet) {
        if (pet == null || world == null) return;
        
        UUID petId = pet.getUuid();
        long currentTime = world.getTime();
        
        // Prevent particle spam - only trigger if enough time has passed
        if (lastRefreshTime.containsKey(petId)) {
            long timeSinceLastEffect = currentTime - lastRefreshTime.get(petId);
            if (timeSinceLastEffect < MINIMUM_REFRESH_INTERVAL) {
                return;
            }
        }
        
        lastRefreshTime.put(petId, currentTime);
        
        // Create the effect
        spawnCooldownParticles(world, pet);
    }
    
    /**
     * Creates a unique, subtle particle pattern for cooldown refresh
     */
    private static void spawnCooldownParticles(ServerWorld world, MobEntity pet) {
        Vec3d petPos = pet.getEntityPos();
        Random random = world.getRandom();
        
        // Create a subtle "refresh" effect - small spiral of cyan particles
        double height = pet.getHeight() * 0.7; // Position at pet's chest level
        Vec3d centerPos = petPos.add(0, height, 0);
        
        // Spawn a small spiral of 6 particles with gentle cyan glow
        for (int i = 0; i < 6; i++) {
            double angle = (i / 6.0) * Math.PI * 2;
            double radius = 0.25 + (i * 0.03); // Expanding spiral
            double xOffset = Math.cos(angle) * radius;
            double zOffset = Math.sin(angle) * radius;
            double yOffset = (i * 0.015) - 0.06; // Slight upward movement
            
            Vec3d particlePos = centerPos.add(xOffset, yOffset, zOffset);
            
            // Use soul particles for mystical cyan-like glow
            world.spawnParticles(ParticleTypes.SOUL,
                particlePos.x, particlePos.y, particlePos.z,
                1, 0, 0, 0, 0.01);
        }
        
        // Add a subtle "pop" at center for emphasis
        world.spawnParticles(ParticleTypes.END_ROD,
            centerPos.x, centerPos.y, centerPos.z,
            1, 0, 0, 0, 0.01);
        
        // Optional: Very subtle enchantment sparkle
        if (random.nextFloat() < 0.6f) {
            world.spawnParticles(ParticleTypes.ENCHANT,
                centerPos.x + (random.nextGaussian() * 0.15), 
                centerPos.y + (random.nextGaussian() * 0.08),
                centerPos.z + (random.nextGaussian() * 0.15),
                1, 0, 0, 0, 0.005);
        }
    }
    
    /**
     * Cleanup old entries to prevent memory leaks
     */
    public static void cleanup(long currentTime) {
        lastRefreshTime.entrySet().removeIf(entry ->
            currentTime - entry.getValue() > 6000); // Remove entries older than 5 minutes
    }

    public static void maybeCleanup(long currentTime) {
        if (currentTime < nextCleanupTick) {
            return;
        }
        cleanup(currentTime);
        nextCleanupTick = currentTime + 6000;
    }

    /**
     * Clean up all entries during server shutdown.
     */
    public static void shutdown() {
        lastRefreshTime.clear();
    }
}
