package woflo.petsplus.state.relationships;

/**
 * Categories of relationships pets can have with any living entity.
 * Computed dynamically from multiple relationship dimensions.
 * Types emerge from combinations of trust, affection, respect, and comfort.
 */
public enum RelationshipType {
    /**
     * Unknown entity, no familiarity established.
     */
    STRANGER,
    
    /**
     * Pet trusts someone but doesn't particularly like them.
     * High trust + low affection (e.g., vet, strict trainer)
     */
    TRUSTED_AUTHORITY,
    
    /**
     * Pet likes someone but doesn't fully trust them.
     * Low trust + high affection (e.g., fun but unpredictable friend)
     */
    FUN_ACQUAINTANCE,
    
    /**
     * Pet respects power/competence but fears or dislikes.
     * Low trust + high respect (e.g., powerful threat, alpha rival)
     */
    RIVAL,
    
    /**
     * Standard friendly relationship.
     * Moderate trust + affection + respect
     */
    FRIEND,
    
    /**
     * Deep emotional bond, owner-like relationship.
     * High trust + high affection + high comfort
     */
    COMPANION,
    
    /**
     * Pet is cautious, wary, distrustful.
     * Negative trust, low affection
     */
    WARY,
    
    /**
     * Active threat, hostile entity.
     * Very negative trust, pet will flee or fight
     */
    HOSTILE,
    
    /**
     * Neutral acquaintance, no strong feelings.
     * Low affection, neutral trust, low respect
     */
    NEUTRAL;
    
    /**
     * Compute relationship type from multiple dimensions.
     * Types emerge naturally from dimension combinations.
     * 
     * @param familiarity binary: does pet know this entity?
     * @param trust -1.0 to 1.0: does pet feel safe?
     * @param affection 0.0 to 1.0: does pet enjoy their presence?
     * @param respect 0.0 to 1.0: does pet see them as significant?
     * @param comfort 0.0 to 1.0: does pet feel at ease?
     * @return emergent relationship type
     */
    public static RelationshipType compute(
        float familiarity,
        float trust,
        float affection,
        float respect,
        float comfort
    ) {
        // Stranger: no familiarity
        if (familiarity < 0.5f) {
            return STRANGER;
        }
        
        // Hostile: very negative trust
        if (trust < -0.5f) {
            return HOSTILE;
        }
        
        // Wary: negative trust, low affection
        if (trust < -0.2f && affection < 0.3f) {
            return WARY;
        }
        
        // Companion: deep bond (high trust + affection + comfort)
        if (trust > 0.7f && affection > 0.7f && comfort > 0.8f) {
            return COMPANION;
        }
        
        // Rival: respect without trust/affection
        if (respect > 0.6f && trust < 0.3f && affection < 0.4f) {
            return RIVAL;
        }
        
        // Trusted Authority: trust without much affection
        if (trust > 0.5f && affection < 0.4f && respect > 0.4f) {
            return TRUSTED_AUTHORITY;
        }
        
        // Fun Acquaintance: affection without trust
        if (affection > 0.5f && trust < 0.4f && trust > -0.1f) {
            return FUN_ACQUAINTANCE;
        }
        
        // Friend: balanced positive relationship
        if (trust > 0.4f && affection > 0.4f && comfort > 0.5f) {
            return FRIEND;
        }
        
        // Neutral: knows them, but no strong feelings
        if (comfort > 0.3f) {
            return NEUTRAL;
        }
        
        // Default wary
        return WARY;
    }
    
    /**
     * Check if this relationship level allows positive social interactions.
     */
    public boolean allowsPositiveInteraction() {
        return this == FRIEND || this == COMPANION || this == FUN_ACQUAINTANCE;
    }
    
    /**
     * Check if this relationship level triggers defensive behavior.
     */
    public boolean triggersDefensiveBehavior() {
        return this == WARY || this == HOSTILE;
    }
    
    /**
     * Check if pet will accept petting/close interaction from this entity.
     */
    public boolean acceptsIntimateInteraction() {
        return this == FRIEND || this == COMPANION;
    }
}
