package woflo.petsplus.api;

import net.minecraft.util.Identifier;

/**
 * Base interface for all ability effects in the PetsPlus system.
 * Effects are the actual actions that occur when an ability is triggered.
 */
public interface Effect {
    /**
     * Gets the unique identifier for this effect type.
     * @return The effect identifier
     */
    Identifier getId();
    
    /**
     * Executes this effect with the given context.
     * @param context The effect context containing execution data
     * @return true if the effect was successfully executed
     */
    boolean execute(EffectContext context);
    
    /**
     * Gets the duration this effect should last in ticks.
     * @return Duration in ticks, or 0 for instant effects
     */
    default int getDurationTicks() {
        return 0;
    }
    
    /**
     * Whether this effect respects boss safety rules.
     * @return true if boss-safe
     */
    default boolean isBossSafe() {
        return true;
    }
}
