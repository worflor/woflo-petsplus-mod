package woflo.petsplus.interaction;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Tracks owner movement patterns toward pets.
 * 
 * <p>Uses a lightweight 3-tick rolling window to calculate average velocity
 * and detect intentional approach vectors. Integrates with OwnerAbilitySignalTracker
 * for composite crouch+approach+eye-contact signals.
 * 
 * <p><b>Performance:</b> O(1) per player tick, automatic cleanup via WeakHashMap.
 * 
 * <p><b>Thresholds:</b>
 * <ul>
 *   <li>Min speed: 0.05 blocks/tick (filters standing still)</li>
 *   <li>Alignment: 0.7 dot product (~45° cone)</li>
 *   <li>History size: 3 ticks for smoothing</li>
 * </ul>
 */
public final class OwnerApproachDetector {
    
    private static final Map<UUID, MovementHistory> MOVEMENT_CACHE = new WeakHashMap<>();
    private static final int HISTORY_SIZE = 3;
    private static final double APPROACH_ANGLE_THRESHOLD = 0.7; // cos(45°) ≈ 0.707
    private static final double MIN_APPROACH_SPEED = 0.05; // Minimum speed to count as approaching
    
    private OwnerApproachDetector() {
        // Utility class
    }
    
    /**
     * Track player movement for approach detection.
     * Call this every tick for crouching players.
     */
    public static void trackPlayerMovement(ServerPlayerEntity player, long tick) {
        if (player == null || player.isSpectator() || !player.isAlive()) {
            return;
        }
        
        UUID playerId = player.getUuid();
        MovementHistory history = MOVEMENT_CACHE.computeIfAbsent(playerId, id -> new MovementHistory());
        
        Vec3d currentPos = player.getEntityPos();
        history.addPosition(currentPos, tick);
    }
    
    /**
     * Check if player is actively approaching a pet.
     * 
     * @param player The player
     * @param pet The pet to check
     * @return true if player is moving toward the pet
     */
    public static boolean isApproachingPet(ServerPlayerEntity player, MobEntity pet) {
        if (player == null || pet == null) {
            return false;
        }
        
        MovementHistory history = MOVEMENT_CACHE.get(player.getUuid());
        if (history == null || !history.hasValidHistory()) {
            return false;
        }
        
        // Get player's current velocity
        Vec3d velocity = history.getAverageVelocity();
        double speed = velocity.length();

        // Must be moving (not standing still)
        if (speed < MIN_APPROACH_SPEED) {
            return false;
        }

        // Calculate direction from player to pet
        Vec3d playerPos = player.getEntityPos();
        Vec3d petPos = pet.getEntityPos();
        Vec3d toPet = petPos.subtract(playerPos);
        double distanceSq = toPet.lengthSquared();
        if (distanceSq < 1.0e-6) {
            return false;
        }

        double dot = velocity.dotProduct(toPet);
        if (dot <= 0.0) {
            return false;
        }

        double alignmentThresholdSq = APPROACH_ANGLE_THRESHOLD * APPROACH_ANGLE_THRESHOLD;
        double speedSq = speed * speed;
        return dot * dot >= alignmentThresholdSq * speedSq * distanceSq;
    }
    
    /**
     * Get distance between player and pet.
     * Used for distance-based response tuning.
     */
    public static double getDistanceToPet(ServerPlayerEntity player, MobEntity pet) {
        if (player == null || pet == null) {
            return Double.MAX_VALUE;
        }
        return player.distanceTo(pet);
    }
    
    /**
     * Get the player's current approach speed.
     * 
     * @param player The player
     * @return Speed in blocks/tick, or 0.0 if not moving
     */
    public static double getApproachSpeed(ServerPlayerEntity player) {
        if (player == null) {
            return 0.0;
        }
        
        MovementHistory history = MOVEMENT_CACHE.get(player.getUuid());
        if (history == null || !history.hasValidHistory()) {
            return 0.0;
        }
        
        return history.getAverageVelocity().length();
    }
    
    /**
     * Get the normalized approach direction vector.
     * 
     * @param player The player
     * @param pet The pet
     * @return Normalized direction vector from player to pet
     */
    public static Vec3d getApproachDirection(ServerPlayerEntity player, MobEntity pet) {
        if (player == null || pet == null) {
            return Vec3d.ZERO;
        }
        
        Vec3d playerPos = player.getEntityPos();
        Vec3d petPos = pet.getEntityPos();
        
        Vec3d direction = petPos.subtract(playerPos);
        double lengthSq = direction.lengthSquared();

        if (lengthSq < 0.001 * 0.001) {
            return Vec3d.ZERO;
        }

        double invLength = 1.0 / Math.sqrt(lengthSq);
        return direction.multiply(invLength);
    }
    
    /**
     * Clear tracking data for a player (call on disconnect).
     */
    public static void clearPlayer(UUID playerId) {
        MOVEMENT_CACHE.remove(playerId);
    }
    
    /**
     * Tracks recent movement positions for velocity calculation.
     */
    private static class MovementHistory {
        private final Vec3d[] positions = new Vec3d[HISTORY_SIZE];
        private final long[] ticks = new long[HISTORY_SIZE];
        private int index = 0;
        private int count = 0;
        
        public void addPosition(Vec3d pos, long tick) {
            positions[index] = pos;
            ticks[index] = tick;
            index = (index + 1) % HISTORY_SIZE;
            count = Math.min(count + 1, HISTORY_SIZE);
        }
        
        public boolean hasValidHistory() {
            return count >= 2; // Need at least 2 points for velocity
        }
        
        public Vec3d getAverageVelocity() {
            if (!hasValidHistory()) {
                return Vec3d.ZERO;
            }
            
            // Calculate average velocity across all history points
            Vec3d totalVelocity = Vec3d.ZERO;
            int validSamples = 0;
            
            for (int i = 1; i < count; i++) {
                int prevIdx = (index - i - 1 + HISTORY_SIZE) % HISTORY_SIZE;
                int currIdx = (index - i + HISTORY_SIZE) % HISTORY_SIZE;
                
                Vec3d prev = positions[prevIdx];
                Vec3d curr = positions[currIdx];
                long timeDelta = ticks[currIdx] - ticks[prevIdx];
                
                if (prev != null && curr != null && timeDelta > 0) {
                    Vec3d velocity = curr.subtract(prev).multiply(1.0 / timeDelta);
                    totalVelocity = totalVelocity.add(velocity);
                    validSamples++;
                }
            }
            
            if (validSamples == 0) {
                return Vec3d.ZERO;
            }
            
            return totalVelocity.multiply(1.0 / validSamples);
        }
    }
}

