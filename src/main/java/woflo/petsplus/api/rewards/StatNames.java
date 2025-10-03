package woflo.petsplus.api.rewards;

import java.util.Set;

/**
 * Central definition of valid stat names for level rewards.
 */
public final class StatNames {
    public static final Set<String> VALID_STATS = Set.of(
        "health",
        "defense", 
        "speed",
        "attack",
        "learning"
    );
    
    private StatNames() {
        // Utility class
    }
    
    /**
     * Check if a stat name is valid (case-insensitive).
     */
    public static boolean isValid(String stat) {
        return VALID_STATS.contains(stat.toLowerCase());
    }
}
