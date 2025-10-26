package woflo.petsplus.state.relationships;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import woflo.petsplus.state.PetComponent;

import java.util.UUID;

/**
 * Policy for determining when relationships should be tracked and how they should be gated.
 * Uses emergent quality-based limits instead of hard caps.
 * Relationships naturally prune based on significance and engagement.
 */
public class RelationshipPolicy {
    
    // Quality thresholds for tracking (not hard caps)
    private static final float MIN_INTERACTION_QUALITY = 0.08f; // Meaningful interaction threshold
    private static final float MIN_TRUST_TO_PERSIST = -0.05f; // Below this needs fresh interactions
    private static final float MIN_FAMILIARITY_TO_PERSIST = 0.08f; // Below this is forgettable
    
    // Interaction requirements
    private static final int MIN_INTERACTIONS_FOR_WEAK_RELATIONSHIP = 3; // Need multiple weak interactions
    private static final int MIN_INTERACTIONS_FOR_TRACKING = 1; // Single strong interaction OK
    private static final int MIN_INTERACTIONS_TO_TRACK = 1; // Minimum interactions before tracking
    
    // Natural decay timing (relationships fade without engagement)
    private static final long STALE_THRESHOLD_TICKS = 288000L; // ~4 hours
    private static final long HOSTILE_MEMORY_TICKS = 144000L; // ~2 hours for resolved threats
    private static final long WEAK_RELATIONSHIP_TIMEOUT = 72000L; // ~1 hour for minimal relationships
    
    /**
     * Check if a relationship should be created for an entity.
     * Quality-based gating: only track meaningful interactions.
     * No hard caps - relationships naturally prune based on engagement.
     */
    public static boolean shouldTrackRelationship(
        LivingEntity entity,
        InteractionType interactionType,
        PetComponent petComponent
    ) {
        // Don't track passive proximity/observation - needs active interaction
        if (interactionType == InteractionType.PROXIMITY || 
            interactionType == InteractionType.OBSERVATION) {
            return false;
        }
        
        // Owner and players: always track (core relationships)
        if (entity instanceof PlayerEntity) {
            return true;
        }
        
        // Pets from same owner: track if owner pack exists
        UUID entityOwnerId = getOwnerUuid(entity);
        if (entityOwnerId != null && entityOwnerId.equals(petComponent.getOwnerUuid())) {
            return true; // Pack members
        }
        
        // For other entities: must be significant interaction
        // This prevents tracking random mobs just passing by
        return interactionType.isSignificant();
    }
    
    /**
     * Check if an existing relationship should be maintained or naturally pruned.
     * Quality-based: weak/inactive relationships fade, strong ones persist.
     */
    public static boolean shouldMaintainRelationship(
        RelationshipProfile profile,
        LivingEntity entity,
        long currentTick,
        PetComponent petComponent
    ) {
        long timeSinceInteraction = currentTick - profile.lastInteractionTick();
        
        // Never prune owner relationship
        UUID ownerUuid = petComponent.getOwnerUuid();
        if (ownerUuid != null && ownerUuid.equals(profile.entityId())) {
            return true;
        }
        
        // Never prune player relationships (high value)
        if (entity instanceof PlayerEntity) {
            return true;
        }
        
        // Keep pack members (same owner pets)
        UUID entityOwnerId = getOwnerUuid(entity);
        if (entityOwnerId != null && entityOwnerId.equals(ownerUuid)) {
            return timeSinceInteraction < STALE_THRESHOLD_TICKS;
        }
        
        // Strong relationships persist longer
        if (profile.trust() > 0.5f || profile.getFamiliarity() > 0.6f) {
            return timeSinceInteraction < STALE_THRESHOLD_TICKS;
        }
        
        // Resolved threats fade faster
        if (entity instanceof HostileEntity && profile.trust() < -0.3f) {
            return timeSinceInteraction < HOSTILE_MEMORY_TICKS;
        }
        
        // Weak relationships need recent engagement
        if (profile.getFamiliarity() < MIN_FAMILIARITY_TO_PERSIST && 
            profile.trust() < MIN_TRUST_TO_PERSIST) {
            if (profile.getTotalInteractionCount() < MIN_INTERACTIONS_FOR_WEAK_RELATIONSHIP) {
                return timeSinceInteraction < WEAK_RELATIONSHIP_TIMEOUT;
            }
        }
        
        // Default: relationships naturally fade over time
        return timeSinceInteraction < STALE_THRESHOLD_TICKS;
    }
    
    /**
     * Determine if interaction should update an existing relationship.
     * Filters out noise and insignificant interactions.
     */
    public static boolean shouldUpdateRelationship(
        RelationshipProfile existingProfile,
        InteractionType interactionType,
        InteractionType.DimensionalResult result
    ) {
        // Always process significant interactions
        if (interactionType.isSignificant()) {
            return true;
        }
        
        // Filter out tiny changes from passive proximity
        if (result.isEmpty() || 
            Math.abs(result.trustDelta()) < MIN_INTERACTION_QUALITY) {
            return false;
        }
        
        // Allow updates for established relationships
        if (existingProfile.getFamiliarity() > 0.15f) {
            return true;
        }
        
        // Require multiple interactions before tracking weak relationships
        return existingProfile.getTotalInteractionCount() >= MIN_INTERACTIONS_TO_TRACK;
    }
    
    /**
     * Calculate relationship memory pressure.
     * Higher pressure = more aggressive natural pruning.
     * Returns 0.0 (no pressure) to 1.0 (high pressure).
     */
    public static float calculateMemoryPressure(
        int totalRelationships,
        int ownerPackSize
    ) {
        // Soft limit based on owner's pack size
        // More pets = more relationships expected
        int softLimit = 50 + (ownerPackSize * 25); // 50 base + 25 per pack member
        
        if (totalRelationships < softLimit) {
            return 0.0f; // No pressure
        }
        
        // Gradual pressure increase beyond soft limit
        float excess = totalRelationships - softLimit;
        return Math.min(1.0f, excess / softLimit);
    }
    
    /**
     * Helper to get owner UUID from entity (works with PetsPlus and vanilla tameables).
     */
    private static UUID getOwnerUuid(LivingEntity entity) {
        // Try PetsPlus component first (only works with MobEntity)
        if (entity instanceof net.minecraft.entity.mob.MobEntity mobEntity) {
            PetComponent pc = PetComponent.get(mobEntity);
            if (pc != null) {
                return pc.getOwnerUuid();
            }
        }
        
        // Try vanilla tameable
        if (entity instanceof net.minecraft.entity.passive.TameableEntity tameable) {
            var owner = tameable.getOwner();
            return owner != null ? owner.getUuid() : null;
        }
        
        return null;
    }
}
