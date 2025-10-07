package woflo.petsplus.state.relationships;

import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stores multi-dimensional relationship data between a pet and a specific entity.
 * Relationships have depth: trust (safety), affection (emotional bond), respect (authority).
 * Immutable - use builder or recordInteraction to create modified copies.
 */
public record RelationshipProfile(
    UUID entityId,
    boolean familiarityEstablished,
    float trust,           // -1.0 to 1.0: Do I feel safe around you?
    float affection,       // 0.0 to 1.0: Do I enjoy your presence?
    float respect,         // 0.0 to 1.0: Do I see you as significant?
    List<InteractionMemory> recentInteractions,
    long lastInteractionTick,
    long firstInteractionTick
) {
    
    private static final int MAX_INTERACTION_MEMORY = 30; // Track last 30 interactions
    private static final float FAMILIARITY_THRESHOLD = 0.15f; // When familiarity becomes permanent
    
    /**
     * Empty relationship profile for new entities.
     */
    public static RelationshipProfile createNew(UUID entityId, long currentTick) {
        return new RelationshipProfile(
            entityId,
            false,
            0.0f,
            0.0f,
            0.0f,
            new ArrayList<>(),
            currentTick,
            currentTick
        );
    }
    
    /**
     * Compute current comfort level (derived from familiarity + trust).
     * High comfort = pet acts natural and relaxed.
     */
    public float getComfort() {
        float familiarityFactor = familiarityEstablished ? 0.4f : 0.0f;
        float trustFactor = MathHelper.clamp((trust + 1.0f) / 2.0f, 0.0f, 1.0f) * 0.6f;
        return familiarityFactor + trustFactor;
    }
    
    /**
     * Get computed familiarity value for threshold checks.
     */
    public float getFamiliarity() {
        return familiarityEstablished ? 1.0f : 0.0f;
    }
    
    /**
     * Compute the current relationship type from dimensions.
     */
    public RelationshipType getType() {
        return RelationshipType.compute(getFamiliarity(), trust, affection, respect, getComfort());
    }
    
    /**
     * Record an interaction and return updated profile.
     */
    public RelationshipProfile recordInteraction(
        InteractionType interactionType,
        InteractionType.DimensionalResult result,
        long currentTick
    ) {
        if (result.isEmpty()) {
            return this;
        }
        
        // Update dimensions
        float newTrust = MathHelper.clamp(
            trust + result.trustDelta(),
            -1.0f,
            1.0f
        );
        
        float newAffection = MathHelper.clamp(
            affection + result.affectionDelta(),
            0.0f,
            1.0f
        );
        
        float newRespect = MathHelper.clamp(
            respect + result.respectDelta(),
            0.0f,
            1.0f
        );
        
        // Update interaction history
        List<InteractionMemory> updatedHistory = new ArrayList<>(recentInteractions);
        updatedHistory.add(new InteractionMemory(
            interactionType,
            currentTick,
            result.trustDelta(),
            result.affectionDelta(),
            result.respectDelta()
        ));
        
        // Prune old interactions (keep last 30)
        if (updatedHistory.size() > MAX_INTERACTION_MEMORY) {
            updatedHistory = updatedHistory.subList(
                updatedHistory.size() - MAX_INTERACTION_MEMORY,
                updatedHistory.size()
            );
        }
        
        // Check if familiarity should be established
        boolean newFamiliarity = familiarityEstablished || shouldEstablishFamiliarity(updatedHistory);
        
        return new RelationshipProfile(
            entityId,
            newFamiliarity,
            newTrust,
            newAffection,
            newRespect,
            updatedHistory,
            currentTick,
            firstInteractionTick
        );
    }
    
    /**
     * Recalculate dimensions based on recency-weighted interaction history.
     * No time-based decay - dimensions reflect weighted recent interactions.
     * 
     * @param currentTick current world time
     * @return profile with recalculated dimensions
     */
    public RelationshipProfile recalculate(long currentTick) {
        if (recentInteractions.isEmpty()) {
            return this;
        }
        
        float weightedTrust = 0.0f;
        float weightedAffection = 0.0f;
        float weightedRespect = 0.0f;
        float totalWeight = 0.0f;
        
        // Weight interactions by recency (exponential decay on weight, not value)
        for (InteractionMemory memory : recentInteractions) {
            long age = currentTick - memory.tick();
            // Newer interactions weighted more heavily
            float weight = (float) Math.exp(-age / 144000.0); // Half-life ~2 hours
            
            weightedTrust += memory.trustDelta() * weight;
            weightedAffection += memory.affectionDelta() * weight;
            weightedRespect += memory.respectDelta() * weight;
            totalWeight += weight;
        }
        
        if (totalWeight > 0.0f) {
            float newTrust = MathHelper.clamp(weightedTrust / totalWeight, -1.0f, 1.0f);
            float newAffection = MathHelper.clamp(weightedAffection / totalWeight, 0.0f, 1.0f);
            float newRespect = MathHelper.clamp(weightedRespect / totalWeight, 0.0f, 1.0f);
            
            return new RelationshipProfile(
                entityId,
                familiarityEstablished,
                newTrust,
                newAffection,
                newRespect,
                recentInteractions,
                lastInteractionTick,
                firstInteractionTick
            );
        }
        
        return this;
    }
    
    /**
     * Check if this relationship should be garbage collected.
     * Only removes truly dead/meaningless relationships.
     * 
     * @return true if relationship should be removed
     */
    public boolean shouldPrune() {
        // Never remove if familiarity established
        if (familiarityEstablished) {
            return false;
        }
        
        // Remove if no meaningful interactions
        if (recentInteractions.isEmpty()) {
            return true;
        }
        
        // Remove if interactions too weak to establish familiarity
        return recentInteractions.size() < 2 && 
               Math.abs(trust) < 0.1f && 
               affection < 0.1f && 
               respect < 0.1f;
    }
    
    /**
     * Get total interaction count.
     */
    public int getTotalInteractionCount() {
        return recentInteractions.size();
    }
    
    /**
     * Get relationship duration in ticks.
     */
    public long getRelationshipDuration(long currentTick) {
        return currentTick - firstInteractionTick;
    }
    
    /**
     * Check if familiarity should be established based on interaction history.
     */
    private boolean shouldEstablishFamiliarity(List<InteractionMemory> interactions) {
        if (interactions.size() < 2) {
            return false; // Need multiple interactions
        }
        
        // Check for significant interactions or accumulated weak ones
        int significantCount = 0;
        float totalImpact = 0.0f;
        
        for (InteractionMemory memory : interactions) {
            float impact = Math.abs(memory.trustDelta()) + 
                          memory.affectionDelta() + 
                          memory.respectDelta();
            totalImpact += impact;
            
            if (impact > 0.1f) {
                significantCount++;
            }
        }
        
        // Establish if: 2+ significant interactions OR cumulative impact exceeds threshold
        return significantCount >= 2 || totalImpact > 0.3f;
    }
    
    /**
     * Memory of a specific interaction with dimension changes.
     */
    public record InteractionMemory(
        InteractionType type,
        long tick,
        float trustDelta,
        float affectionDelta,
        float respectDelta
    ) {}
}
