package woflo.petsplus.ai.behaviors;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.EmotionFeedback;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.state.PetComponent;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Map;

/**
 * Base implementation of IAdaptableGoal that bridges the new urgency system
 * with the existing AdaptiveGoal system.
 */
public abstract class AdaptableGoalBase extends AdaptiveGoal implements IAdaptableGoal {
    
    protected BehaviorData behaviorData;
    private String[] cachedMutexFlags;
    private EnumSet<Goal.Control> controls;
    
    public AdaptableGoalBase(MobEntity mob, GoalDefinition goalDefinition) {
        super(mob, goalDefinition, EnumSet.of(Goal.Control.MOVE)); // Default controls
    }
    
    @Override
    public float calculateUrgency(MobEntity pet, PetComponent petComponent, Map<String, Float> contextValues) {
        // If we have behavior data with urgency calculation, use it
        if (behaviorData != null && behaviorData.urgencyCalculation() != null) {
            return behaviorData.urgencyCalculation().calculateUrgency(petComponent, contextValues);
        }
        
        // Fallback to default urgency calculation based on canStart()
        return canStart() ? 0.5f : 0.0f;
    }
    
    @Override
    public boolean meetsBasicRequirements(MobEntity pet, PetComponent petComponent) {
        // Check behavior requirements if available
        if (behaviorData != null && behaviorData.requirements() != null) {
            return behaviorData.requirements().meetsRequirements(petComponent);
        }
        
        // Fallback to basic checks
        return pet.isAlive() && !pet.isRemoved() && petComponent != null;
    }
    
    @Override
    public BehaviorData getBehaviorData() {
        return behaviorData;
    }
    
    @Override
    public void setBehaviorData(BehaviorData behaviorData) {
        this.behaviorData = behaviorData;
        this.cachedMutexFlags = null; // Reset cached flags
    }
    
    @Override
    public String[] getMutexFlags() {
        if (cachedMutexFlags == null) {
            if (behaviorData != null && behaviorData.mutexFlags() != null) {
                cachedMutexFlags = behaviorData.mutexFlags().toArray(new String[0]);
            } else {
                // Default mutex flags based on controls
                EnumSet<Goal.Control> controls = getControls();
                if (controls.contains(Goal.Control.MOVE)) {
                    cachedMutexFlags = new String[]{"MOVEMENT"};
                } else if (controls.contains(Goal.Control.LOOK)) {
                    cachedMutexFlags = new String[]{"INTERACTION"};
                } else {
                    cachedMutexFlags = new String[0];
                }
            }
        }
        return cachedMutexFlags;
    }
    
    @Override
    protected boolean canStartGoal() {
        // If we have behavior data, check requirements first
        if (behaviorData != null && behaviorData.requirements() != null) {
            PetComponent pc = PetComponent.get(mob);
            if (pc != null && !behaviorData.requirements().meetsRequirements(pc)) {
                return false;
            }
        }
        
        // Return true by default - subclasses should override this
        return true;
    }
    
    @Override
    protected void onStopGoal() {
        // Apply behavior feedback if available
        if (behaviorData != null && behaviorData.feedback() != null) {
            PetComponent pc = PetComponent.get(mob);
            if (pc != null) {
                // Apply energy cost
                int energyCost = behaviorData.feedback().energyCost();
                if (energyCost > 0) {
                    // Apply energy cost through the pet's energy system
                    // This would need to be implemented based on the existing energy system
                }
                
                // Apply emotion rewards
                var emotionRewards = behaviorData.feedback().emotionRewards();
                if (!emotionRewards.isEmpty()) {
                    // Apply emotion rewards through the mood system
                    // This would integrate with the existing emotion system
                }
            }
        }
    }
    
    /**
     * Helper method to access the mob field from AdaptiveGoal using reflection
     * since it's protected.
     */
    protected static MobEntity getMobFromAdaptiveGoal(AdaptiveGoal goal) {
        try {
            Field mobField = AdaptiveGoal.class.getDeclaredField("mob");
            mobField.setAccessible(true);
            return (MobEntity) mobField.get(goal);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access mob field from AdaptiveGoal", e);
        }
    }
    
    /**
     * Helper method to access the goalDefinition field from AdaptiveGoal using reflection
     * since it's protected.
     */
    protected static GoalDefinition getGoalDefinitionFromAdaptiveGoal(AdaptiveGoal goal) {
        try {
            Field goalDefinitionField = AdaptiveGoal.class.getDeclaredField("goalDefinition");
            goalDefinitionField.setAccessible(true);
            return (GoalDefinition) goalDefinitionField.get(goal);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access goalDefinition field from AdaptiveGoal", e);
        }
    }
}