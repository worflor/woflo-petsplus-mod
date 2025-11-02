package woflo.petsplus.stats.nature;

/**
 * Nature-specific morality and behavioral identity defining baseline personality
 * and resistance to experiential drift.
 * 
 * <p>Each nature has an inherent morality core that acts as an "anchor" for the
 * pet's behavioral axes. When a pet experiences deeds, its behavior drifts from this
 * baseline. Over time, the pet's personality decays back toward its Nature's baseline,
 * representing the pet's innate behavioral predisposition reasserting itself.
 * 
 * <p><strong>Baselines:</strong> Where each behavioral axis decays toward when experiences
 * fade (range 0.0-1.0). For example, an Infernal pet decays toward high aggression (0.65)
 * and low empathy (0.35), reflecting its inherently destructive nature.
 * 
 * <p><strong>Retention Multiplier:</strong> How quickly behavioral traits return to baseline.
 *   - 1.0 = normal decay speed (neutral)
 *   - >1.0 = slower decay, more persistent (sticky traits, Nature identity strongly maintained)
 *   - <1.0 = faster decay (volatile Nature rapidly snaps back to baseline)
 * 
 * <p>Retention is derived from Nature's volatility multiplier:
 *   - Low volatility (0.44-0.75) → retention 1.4-1.5x (contemplative, enduring identity)
 *   - Mid volatility (0.80-1.20) → retention 1.0-1.2x (balanced)
 *   - High volatility (1.25-1.55) → retention 0.65-0.85x (chaotic, snappy return to baseline)
 */
public record NatureMoralityProfile(
    float aggressionBaseline,
    float empathyBaseline,
    float courageBaseline,
    float socialBaseline,
    float resourceBaseline,
    float retentionMultiplier
) {
    /**
     * Neutral profile: all axes at 0.5 (true neutral), normal decay speed.
     * Used as fallback when no Nature is assigned or unknown Nature encountered.
     */
    public static final NatureMoralityProfile NEUTRAL = new NatureMoralityProfile(
        0.5f, 0.5f, 0.5f, 0.5f, 0.5f,  // all neutral baselines
        1.0f                             // normal drift
    );
    
    /**
     * Get baseline value for a specific behavioral axis.
     * 
     * @param axisName one of: "aggression", "empathy", "courage", "social", "resource"
     * @return baseline value (0.0-1.0), or 0.5f if axis name unrecognized
     */
    public float baselineFor(String axisName) {
        return switch (axisName) {
            case "aggression" -> aggressionBaseline;
            case "empathy" -> empathyBaseline;
            case "courage" -> courageBaseline;
            case "social" -> socialBaseline;
            case "resource" -> resourceBaseline;
            default -> 0.5f;
        };
    }
    
    /**
     * Validate that all baselines are clamped to [0.0, 1.0].
     * Called during profile creation to catch configuration errors early.
     */
    public boolean isValid() {
        return aggressionBaseline >= 0.0f && aggressionBaseline <= 1.0f &&
               empathyBaseline >= 0.0f && empathyBaseline <= 1.0f &&
               courageBaseline >= 0.0f && courageBaseline <= 1.0f &&
               socialBaseline >= 0.0f && socialBaseline <= 1.0f &&
               resourceBaseline >= 0.0f && resourceBaseline <= 1.0f &&
               retentionMultiplier > 0.0f;
    }
}
