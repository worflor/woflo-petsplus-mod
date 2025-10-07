package woflo.petsplus.state.relationships;

import net.minecraft.util.math.MathHelper;

/**
 * Types of interactions that build or damage relationships between pets and entities.
 * Each interaction affects multiple relationship dimensions: trust, affection, respect.
 * Base values can be scaled by context (species traits, mood, nature, etc).
 */
public enum InteractionType {
    // === POSITIVE INTERACTIONS ===
    
    /** Entity fed the pet food */
    FEEDING(0.08f, 0.10f, 0.02f),
    
    /** Entity petted the pet (player right-click) */
    PETTING(0.06f, 0.12f, 0.01f),
    
    /** Entity approached slowly while crouching */
    GENTLE_APPROACH(0.03f, 0.02f, 0.0f),
    
    /** Entity and pet fought same target together */
    COMBAT_ALLY(0.15f, 0.05f, 0.20f),
    
    /** Passive proximity over time (per minute) */
    PROXIMITY(0.001f, 0.001f, 0.0f),
    
    /** Entity played with pet (threw item, toy interaction) */
    PLAY(0.03f, 0.15f, -0.02f),
    
    /** Entity gave pet a valuable item/gift */
    GIFT(0.08f, 0.12f, 0.04f),
    
    /** Entity rescued pet from danger */
    RESCUE(0.25f, 0.15f, 0.15f),
    
    /** Entity healed the pet */
    HEALING(0.10f, 0.08f, 0.05f),
    
    /** Entity defended pet from attacker */
    DEFENDED(0.20f, 0.08f, 0.12f),
    
    // === NEGATIVE INTERACTIONS ===
    
    /** Entity attacked the pet directly */
    ATTACK(-0.20f, -0.10f, 0.08f),
    
    /** Entity threatened the pet (rapid approach, weapon drawn) */
    THREAT(-0.08f, -0.05f, 0.03f),
    
    /** Entity made loud noise near pet */
    LOUD_NOISE(-0.03f, -0.02f, 0.0f),
    
    /** Entity stole from pet or took item aggressively */
    THEFT(-0.15f, -0.12f, 0.02f),
    
    /** Entity attacked pet's owner */
    OWNER_ATTACK(-0.30f, -0.15f, 0.10f),
    
    // === NEUTRAL INTERACTIONS ===
    
    /** Generic observation without negative/positive context */
    OBSERVATION(0.0f, 0.0f, 0.0f);
    
    private final float trustDelta;
    private final float affectionDelta;
    private final float respectDelta;
    
    InteractionType(float trustDelta, float affectionDelta, float respectDelta) {
        this.trustDelta = trustDelta;
        this.affectionDelta = affectionDelta;
        this.respectDelta = respectDelta;
    }
    
    /**
     * Get base trust delta for this interaction.
     */
    public float getTrustDelta() {
        return trustDelta;
    }
    
    /**
     * Get base affection delta for this interaction.
     */
    public float getAffectionDelta() {
        return affectionDelta;
    }
    
    /**
     * Get base respect delta for this interaction.
     */
    public float getRespectDelta() {
        return respectDelta;
    }
    
    /**
     * Check if this is a positive interaction that builds relationships.
     */
    public boolean isPositive() {
        return trustDelta > 0.0f || affectionDelta > 0.0f;
    }
    
    /**
     * Check if this is a negative interaction that damages relationships.
     */
    public boolean isNegative() {
        return trustDelta < 0.0f || affectionDelta < 0.0f;
    }
    
    /**
     * Check if this interaction significantly impacts any dimension (threshold: 0.08).
     */
    public boolean isSignificant() {
        return Math.abs(trustDelta) >= 0.08f || 
               Math.abs(affectionDelta) >= 0.08f || 
               Math.abs(respectDelta) >= 0.08f;
    }
    
    /**
     * Apply contextual scaling to interaction values.
     * 
     * @param trustMultiplier scales trust change (nature, relationship history, etc)
     * @param affectionMultiplier scales affection change (species, mood, playfulness)
     * @param respectMultiplier scales respect change (demonstrated competence, power)
     * @return scaled dimensional result
     */
    public DimensionalResult scaled(
        float trustMultiplier,
        float affectionMultiplier,
        float respectMultiplier
    ) {
        float scaledTrust = MathHelper.clamp(
            trustDelta * trustMultiplier,
            -1.0f, 1.0f
        );
        
        float scaledAffection = MathHelper.clamp(
            affectionDelta * affectionMultiplier,
            -0.5f, 0.5f
        );
        
        float scaledRespect = MathHelper.clamp(
            respectDelta * respectMultiplier,
            -0.5f, 0.5f
        );
        
        return new DimensionalResult(scaledTrust, scaledAffection, scaledRespect);
    }
    
    /**
     * Result of an interaction with dimensional changes.
     */
    public record DimensionalResult(
        float trustDelta,
        float affectionDelta,
        float respectDelta
    ) {
        
        public boolean isEmpty() {
            return Math.abs(trustDelta) < 0.001f && 
                   Math.abs(affectionDelta) < 0.001f && 
                   Math.abs(respectDelta) < 0.001f;
        }
    }
}
