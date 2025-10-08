package woflo.petsplus.util;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.StateManager;

import java.util.List;

/**
 * Unified pet targeting utility for commands and interactions.
 * Uses intelligent raycast targeting with proximity fallback.
 * Integrates with existing swarm index for performance.
 */
public final class PetTargetingUtil {
    
    // Configuration constants
    private static final double COMMAND_RAYCAST_DISTANCE = 16.0;
    private static final double COMMAND_RAYCAST_THRESHOLD = 0.85; // Dot product threshold for alignment
    private static final double PROXIMITY_FALLBACK_DISTANCE = 10.0;
    
    private PetTargetingUtil() {
        // Utility class
    }
    
    /**
     * Find a pet using intelligent targeting:
     * 1. Raycast to pet player is looking at (primary)
     * 2. Proximity fallback to nearest pet (secondary)
     * 
     * @param player The player targeting a pet
     * @return The targeted pet, or null if none found
     */
    @Nullable
    public static MobEntity findTargetPet(ServerPlayerEntity player) {
        return findTargetPet(player, true);
    }
    
    /**
     * Find a pet with optional raycast targeting.
     * 
     * @param player The player targeting a pet
     * @param useRaycast Whether to use raycast targeting (true) or only proximity (false)
     * @return The targeted pet, or null if none found
     */
    @Nullable
    public static MobEntity findTargetPet(ServerPlayerEntity player, boolean useRaycast) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            return null;
        }
        
        // Primary: Try raycast targeting if enabled
        if (useRaycast) {
            MobEntity lookedAt = findLookedAtPet(player, world);
            if (lookedAt != null) {
                return lookedAt;
            }
        }
        
        // Secondary: Fall back to proximity
        return findNearestPet(player, world);
    }
    
    /**
     * Find the pet the player is looking at using raycast.
     * Reuses proven logic from OwnerAbilitySignalTracker.
     * Uses swarm index for performance.
     * 
     * @param player The player
     * @param world The world
     * @return The pet being looked at, or null
     */
    @Nullable
    private static MobEntity findLookedAtPet(ServerPlayerEntity player, ServerWorld world) {
        StateManager stateManager = StateManager.forWorld(world);
        List<PetSwarmIndex.SwarmEntry> swarm = stateManager.getSwarmIndex().snapshotOwner(player.getUuid());
        
        if (swarm.isEmpty()) {
            return null;
        }
        
        Vec3d eyePos = player.getCameraPosVec(1.0f);
        Vec3d lookVec = player.getRotationVec(1.0f);
        double maxDistanceSq = COMMAND_RAYCAST_DISTANCE * COMMAND_RAYCAST_DISTANCE;
        
        MobEntity closest = null;
        double closestDistanceSq = maxDistanceSq;
        
        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            MobEntity mob = entry.pet();
            if (mob == null || !mob.isAlive() || mob.isRemoved() || mob.getEntityWorld() != world) {
                continue;
            }
            
            double distanceSq = player.squaredDistanceTo(mob);
            if (distanceSq > maxDistanceSq) {
                continue;
            }
            
            // Calculate alignment using dot product
            Vec3d toEntity = mob.getBoundingBox().getCenter().subtract(eyePos);
            double lengthSq = toEntity.lengthSquared();
            if (lengthSq < 1.0e-4) {
                continue; // Too close to calculate direction
            }
            
            double alignment = toEntity.normalize().dotProduct(lookVec);
            if (alignment < COMMAND_RAYCAST_THRESHOLD) {
                continue; // Not looking at this pet
            }
            
            // Check line of sight
            if (!player.canSee(mob)) {
                continue;
            }
            
            // Track closest pet meeting criteria
            if (distanceSq < closestDistanceSq) {
                closest = mob;
                closestDistanceSq = distanceSq;
            }
        }
        
        return closest;
    }
    
    /**
     * Find the nearest pet to the player within proximity range.
     * Fallback method when raycast doesn't find a target.
     * 
     * @param player The player
     * @param world The world
     * @return The nearest pet, or null
     */
    @Nullable
    private static MobEntity findNearestPet(ServerPlayerEntity player, ServerWorld world) {
        return world.getEntitiesByClass(MobEntity.class,
            player.getBoundingBox().expand(PROXIMITY_FALLBACK_DISTANCE),
            entity -> {
                PetComponent petComp = PetComponent.get(entity);
                return petComp != null && petComp.isOwnedBy(player) && entity.isAlive();
            }).stream()
            .min((a, b) -> Double.compare(
                player.squaredDistanceTo(a),
                player.squaredDistanceTo(b)))
            .orElse(null);
    }
    
    /**
     * Get raycast distance for targeting.
     * @return The maximum raycast distance
     */
    public static double getRaycastDistance() {
        return COMMAND_RAYCAST_DISTANCE;
    }
    
    /**
     * Get proximity fallback distance.
     * @return The maximum proximity distance
     */
    public static double getProximityDistance() {
        return PROXIMITY_FALLBACK_DISTANCE;
    }
}

