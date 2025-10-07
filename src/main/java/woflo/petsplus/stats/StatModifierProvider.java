package woflo.petsplus.stats;

/**
 * Core interface for stat modifier systems in the pet progression pipeline.
 * 
 * <p>All stat modifiers (Nature, Imprint, Role, Level) implement this interface to provide
 * multiplicative bonuses that compound together. Each modifier returns a value where:
 * <ul>
 *   <li>1.0 = no change (100%)</li>
 *   <li>1.1 = +10% bonus (110%)</li>
 *   <li>0.9 = -10% penalty (90%)</li>
 * </ul>
 * 
 * <h2>Modifier Pipeline</h2>
 * The pet stat system applies modifiers in this order:
 * <pre>
 * BASE STATS (vanilla mob stats)
 *     ↓
 * × NATURE MULTIPLIER (themed personality bonuses)
 *     ↓
 * × IMPRINT MULTIPLIER (unique per-pet variance)
 *     ↓
 * × ROLE MULTIPLIER (chosen specialization focus)
 *     ↓
 * × LEVEL MULTIPLIER (progression over time)
 *     ↓
 * = FINAL STATS
 * </pre>
 * 
 * <h2>Design Philosophy</h2>
 * <ul>
 *   <li><b>Nature:</b> Small themed bonuses (1.03x-1.08x) that define archetype</li>
 *   <li><b>Imprint:</b> Random variance (0.88x-1.12x) ensuring every pet is unique</li>
 *   <li><b>Role:</b> Fixed specialization bonuses (1.05x-1.10x) based on player choice</li>
 *   <li><b>Level:</b> Progressive scaling (1.0x at level 1, ~2.3x at level 30)</li>
 * </ul>
 * 
 * <p>All modifiers are multiplicative, so they compound:
 * <br>Example: 1.06 (nature) × 1.08 (imprint) × 1.10 (role) × 2.1 (level) = 2.64x final health
 * 
 * @see woflo.petsplus.stats.PetAttributeManager
 * @see woflo.petsplus.stats.nature.NatureModifierSampler
 */
public interface StatModifierProvider {
    
    /**
     * Returns the multiplicative health bonus.
     * Default is 1.0 (no change).
     * 
     * @return multiplier for health (e.g., 1.06 = +6% health)
     */
    default float getHealthMultiplier() {
        return 1.0f;
    }
    
    /**
     * Returns the multiplicative speed bonus.
     * Default is 1.0 (no change).
     * 
     * @return multiplier for speed (e.g., 0.94 = -6% speed)
     */
    default float getSpeedMultiplier() {
        return 1.0f;
    }
    
    /**
     * Returns the multiplicative attack damage bonus.
     * Default is 1.0 (no change).
     * 
     * @return multiplier for attack (e.g., 1.12 = +12% attack)
     */
    default float getAttackMultiplier() {
        return 1.0f;
    }
    
    /**
     * Returns the multiplicative defense (armor) bonus.
     * Default is 1.0 (no change).
     * 
     * @return multiplier for defense (e.g., 1.05 = +5% armor)
     */
    default float getDefenseMultiplier() {
        return 1.0f;
    }
    
    /**
     * Returns the multiplicative agility bonus.
     * Affects knockback resistance and mobility.
     * Default is 1.0 (no change).
     * 
     * @return multiplier for agility (e.g., 1.03 = +3% agility)
     */
    default float getAgilityMultiplier() {
        return 1.0f;
    }
    
    /**
     * Returns the multiplicative vitality bonus.
     * Affects regeneration and status resistance.
     * Default is 1.0 (no change).
     * 
     * @return multiplier for vitality (e.g., 0.97 = -3% vitality)
     */
    default float getVitalityMultiplier() {
        return 1.0f;
    }
    
    /**
     * Returns the multiplicative swim speed bonus.
     * Default is 1.0 (no change).
     * 
     * @return multiplier for swim speed (e.g., 1.08 = +8% swim speed)
     */
    default float getSwimSpeedMultiplier() {
        return 1.0f;
    }
    
    /**
     * Returns the multiplicative focus bonus.
     * Affects follow range and awareness.
     * Default is 1.0 (no change).
     * 
     * @return multiplier for focus (e.g., 1.04 = +4% follow range)
     */
    default float getFocusMultiplier() {
        return 1.0f;
    }
    
    /**
     * Returns the multiplicative loyalty bonus.
     * Affects bond-related behaviors.
     * Default is 1.0 (no change).
     * 
     * @return multiplier for loyalty (e.g., 1.02 = +2% loyalty)
     */
    default float getLoyaltyMultiplier() {
        return 1.0f;
    }
    
    /**
     * Checks if this provider has any non-default multipliers.
     * Used to optimize stat application by skipping empty providers.
     * 
     * @return true if all multipliers are 1.0 (default), false otherwise
     */
    default boolean isEmpty() {
        return getHealthMultiplier() == 1.0f
            && getSpeedMultiplier() == 1.0f
            && getAttackMultiplier() == 1.0f
            && getDefenseMultiplier() == 1.0f
            && getAgilityMultiplier() == 1.0f
            && getVitalityMultiplier() == 1.0f
            && getSwimSpeedMultiplier() == 1.0f
            && getFocusMultiplier() == 1.0f
            && getLoyaltyMultiplier() == 1.0f;
    }
    
    /**
     * Empty modifier provider that returns 1.0 for all stats (no change).
     */
    StatModifierProvider EMPTY = new StatModifierProvider() {
        @Override
        public boolean isEmpty() {
            return true;
        }
    };
}
