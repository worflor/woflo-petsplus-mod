package woflo.petsplus.ai.behavior.variants;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.ai.context.PetContext;

/**
 * Interface for polymorphic pet behavior variants.
 * Allows personality-driven behavior selection with species-specific implementations.
 */
public interface BehaviorVariant {
    
    /**
     * Initialize the variant when selected.
     * 
     * @param mob The pet mob
     * @param context Current pet context
     */
    void initialize(MobEntity mob, PetContext context);
    
    /**
     * Execute one tick of the behavior.
     * 
     * @param mob The pet mob
     * @param ticks Number of ticks this variant has been active
     */
    void tick(MobEntity mob, int ticks);
    
    /**
     * Check if this variant should continue executing.
     * 
     * @param mob The pet mob
     * @param ticks Number of ticks this variant has been active
     * @return true if variant should continue
     */
    boolean shouldContinue(MobEntity mob, int ticks);
    
    /**
     * Clean up when the variant stops.
     * 
     * @param mob The pet mob
     */
    void stop(MobEntity mob);
    
    /**
     * Get the default duration for this variant in ticks.
     * 
     * @return Duration in ticks
     */
    int getDefaultDuration();
}
