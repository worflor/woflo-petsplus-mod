package woflo.petsplus.state.morality;

/**
 * Type-safe deed classification for behavioral influence system.
 * 
 * Maps owner "dark deeds" (combat events, betrayals, cruelty) to structured
 * persona adjustments instead of loose string tag matching. Each deed defines
 * which behavioral axes shift and by how much.
 * 
 * Deed classification happens via deriveDeedKind() which inspects the actual
 * tags present in the context (not assumptions about what tags might exist).
 * This prevents tag mismatch bugs and makes deed effects transparent.
 * 
 * Some deeds are reserved for future implementation (KILL_SPREE, PROTECT_ALLY, GUARD_DUTY).
 */
public enum DeedKind {
    // === CRUELTY / VICIOUSNESS ===
    /** Kill innocent passive mob (sheep, cow, etc.) */
    KILL_PASSIVE_MOB("cruelty", "aggression: +0.15, empathy: -0.25"),
    
    /** Kill baby animal */
    KILL_BABY_MOB("severe_cruelty", "aggression: +0.25, empathy: -0.35"),
    
    /** Kill villager/NPC (betrayal of trust) */
    KILL_VILLAGER("betrayal", "empathy: -0.30"),
    
    /** Kill owner's own pet (extreme betrayal) */
    KILL_OWN_PET("extreme_betrayal", "aggression: +0.20, empathy: -0.40"),
    
    // === TRAUMA / COWARDICE ===
    /** Take damage from owner (friendly fire) */
    FRIENDLY_FIRE("betrayal_trauma", "courage: -0.20"),
    
    /** Betray owner's team */
    TEAM_BETRAYAL("shame", "courage: -0.25, empathy: -0.15"),
    
    // === RUTHLESSNESS ===
    /** Kill low-health target (dishonorable finish) */
    CLUTCH_FINISH("opportunism", "aggression: +0.20"),
    
    /** Kill spree (multiple kills in succession) */
    KILL_SPREE("bloodlust", "aggression: +0.10"),
    
    // === VIRTUE / HEROISM ===
    /** Protect ally from damage */
    PROTECT_ALLY("heroism", "courage: +0.15, social: +0.15, empathy: +0.10"),
    
    /** Guard duty / protective stance */
    GUARD_DUTY("vigilance", "courage: +0.10, social: +0.10"),
    
    /** Kill hostile threat (justified defense) */
    KILL_HOSTILE("justified_defense", "courage: +0.10"),
    
    /** Kill boss-level threat */
    KILL_BOSS("valor", "courage: +0.15, aggression: +0.05");
    
    private final String category;
    private final String description;
    
    DeedKind(String category, String description) {
        this.category = category;
        this.description = description;
    }
    
    public String category() {
        return category;
    }
    
    public String description() {
        return description;
    }
    
    public boolean isCruelty() {
        return category.contains("cruelty") || category.contains("betrayal");
    }
    
    public boolean isTrauma() {
        return category.contains("trauma") || category.contains("shame");
    }
    
    public boolean isRuthless() {
        return category.contains("opportunism") || category.contains("bloodlust");
    }
    
    public boolean isVirtuous() {
        return category.contains("heroism") || category.contains("vigilance") || category.contains("valor");
    }
}
