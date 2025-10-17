package woflo.petsplus.ai.behaviors;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.state.PetComponent;

import java.util.Map;

/**
 * Interface for goals that can be dynamically selected based on urgency scoring.
 * Replaces the simple boolean canStart() with a more sophisticated urgency calculation.
 */
public interface IAdaptableGoal {
    
    /**
     * Calculate the urgency score for this goal (0.0 to 1.0).
     * Higher scores indicate higher priority for execution.
     * 
     * @param pet The pet entity
     * @param petComponent The pet's component data
     * @param contextValues Additional context values that might affect urgency
     * @return Urgency score from 0.0 (no urgency) to 1.0 (maximum urgency)
     */
    float calculateUrgency(MobEntity pet, PetComponent petComponent, Map<String, Float> contextValues);
    
    /**
     * Check if this goal can currently execute based on basic requirements.
     * This is a simpler check than canStart() and should only verify fundamental constraints.
     * 
     * @param pet The pet entity
     * @param petComponent The pet's component data
     * @return true if the goal meets basic requirements
     */
    boolean meetsBasicRequirements(MobEntity pet, PetComponent petComponent);
    
    /**
     * Get the behavior data that configured this goal.
     * 
     * @return The behavior data, or null if not configured via behavior system
     */
    BehaviorData getBehaviorData();
    
    /**
     * Set the behavior data that configures this goal.
     * 
     * @param behaviorData The behavior data to apply
     */
    void setBehaviorData(BehaviorData behaviorData);
    
    /**
     * Get the mutex flags for this goal.
     * Mutex flags determine which other goals cannot run simultaneously.
     * 
     * @return Array of mutex flag strings
     */
    String[] getMutexFlags();
    
    /**
     * Check if this goal is mutually exclusive with another goal.
     * 
     * @param otherGoal The other goal to check against
     * @return true if the goals cannot run simultaneously
     */
    default boolean isMutuallyExclusiveWith(IAdaptableGoal otherGoal) {
        if (otherGoal == null) return false;
        
        String[] myFlags = getMutexFlags();
        String[] theirFlags = otherGoal.getMutexFlags();
        
        for (String myFlag : myFlags) {
            for (String theirFlag : theirFlags) {
                if (myFlag.equals(theirFlag)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Get the energy cost of executing this goal.
     * 
     * @return Energy cost value
     */
    default float getEnergyCost() {
        BehaviorData data = getBehaviorData();
        if (data != null) {
            return data.feedback().energyCost();
        }
        return 5.0f; // Default cost
    }
    
    /**
     * Check if this goal should be considered for execution.
     * Combines basic requirements with urgency threshold.
     * 
     * @param pet The pet entity
     * @param petComponent The pet's component data
     * @param contextValues Additional context values
     * @param minimumUrgency The minimum urgency required to be considered
     * @return true if the goal should be considered
     */
    default boolean shouldBeConsidered(MobEntity pet, PetComponent petComponent, 
                                      Map<String, Float> contextValues, float minimumUrgency) {
        return meetsBasicRequirements(pet, petComponent) && 
               calculateUrgency(pet, petComponent, contextValues) >= minimumUrgency;
    }
}