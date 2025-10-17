package woflo.petsplus.ai.behaviors;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Factory for creating goal instances from behavior data.
 * Bridges the new behavior system with the existing goal classes.
 */
public class BehaviorGoalFactory {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<String, Class<? extends AdaptiveGoal>> GOAL_CLASS_CACHE = new HashMap<>();
    
    /**
     * Create a goal instance from behavior data.
     * 
     * @param behaviorId The behavior identifier
     * @param mob The mob entity
     * @return The created goal, or empty if creation failed
     */
    public static Optional<AdaptiveGoal> createGoal(Identifier behaviorId, MobEntity mob) {
        BehaviorManager behaviorManager = BehaviorManager.getInstance();
        Optional<BehaviorData> behaviorOpt = behaviorManager.getBehavior(behaviorId);
        
        if (behaviorOpt.isEmpty()) {
            LOGGER.warn("No behavior found for ID: {}", behaviorId);
            return Optional.empty();
        }
        
        BehaviorData behavior = behaviorOpt.get();
        return createGoalFromBehavior(behavior, mob);
    }
    
    /**
     * Create a goal instance from behavior data.
     * 
     * @param behavior The behavior data
     * @param mob The mob entity
     * @return The created goal, or empty if creation failed
     */
    public static Optional<AdaptiveGoal> createGoalFromBehavior(BehaviorData behavior, MobEntity mob) {
        try {
            // Try to find the goal class
            Class<? extends AdaptiveGoal> goalClass = findGoalClass(behavior.goalClass());
            if (goalClass == null) {
                LOGGER.error("Unknown goal class: {}", behavior.goalClass());
                return Optional.empty();
            }
            
            // Try to create an instance with behavior data
            AdaptiveGoal goal = createGoalInstance(goalClass, mob, behavior);
            if (goal != null) {
                return Optional.of(goal);
            }
            
            LOGGER.error("Failed to create goal instance for: {}", behavior.goalClass());
            return Optional.empty();
            
        } catch (Exception e) {
            LOGGER.error("Error creating goal from behavior: {}", behavior.goalClass(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Find a goal class by name.
     */
    private static Class<? extends AdaptiveGoal> findGoalClass(String className) {
        // Check cache first
        Class<? extends AdaptiveGoal> cached = GOAL_CLASS_CACHE.get(className);
        if (cached != null) {
            return cached;
        }
        
        try {
            // Try to load the class
            @SuppressWarnings("unchecked")
            Class<? extends AdaptiveGoal> clazz = 
                (Class<? extends AdaptiveGoal>) Class.forName(className);
            
            // Cache it for future use
            GOAL_CLASS_CACHE.put(className, clazz);
            return clazz;
            
        } catch (ClassNotFoundException e) {
            LOGGER.error("Goal class not found: {}", className);
            return null;
        } catch (ClassCastException e) {
            LOGGER.error("Goal class is not an AdaptiveGoal: {}", className);
            return null;
        }
    }
    
    /**
     * Create an instance of a goal class.
     */
    private static AdaptiveGoal createGoalInstance(Class<? extends AdaptiveGoal> goalClass, 
                                                   MobEntity mob, BehaviorData behavior) {
        try {
            // First try constructor with behavior data
            try {
                Constructor<? extends AdaptiveGoal> constructor = 
                    goalClass.getConstructor(MobEntity.class, BehaviorData.class);
                return constructor.newInstance(mob, behavior);
                
            } catch (NoSuchMethodException e) {
                // Fall back to constructor with just mob
                Constructor<? extends AdaptiveGoal> constructor = 
                    goalClass.getConstructor(MobEntity.class);
                AdaptiveGoal goal = constructor.newInstance(mob);
                
                // Set behavior data if it implements IAdaptableGoal
                if (goal instanceof IAdaptableGoal adaptableGoal) {
                    adaptableGoal.setBehaviorData(behavior);
                }
                
                return goal;
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to instantiate goal: {}", goalClass.getName(), e);
            return null;
        }
    }
    
    /**
     * Get the goal definition for a behavior.
     * This is a simplified implementation - in a full system we would
     * have a more explicit mapping between behavior IDs and goal definitions.
     */
    public static Optional<GoalDefinition> getGoalDefinition(Identifier behaviorId) {
        // For now, we'll just try to find any goal definition that matches
        // the behavior's goal class by name
        BehaviorManager behaviorManager = BehaviorManager.getInstance();
        Optional<BehaviorData> behaviorOpt = behaviorManager.getBehavior(behaviorId);
        
        if (behaviorOpt.isEmpty()) {
            return Optional.empty();
        }
        
        String behaviorClass = behaviorOpt.get().goalClass();
        
        // Simple name-based matching
        for (GoalDefinition definition : GoalRegistry.all()) {
            if (definition.id().getPath().equals(behaviorId.getPath())) {
                return Optional.of(definition);
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Clear the goal class cache (for testing purposes).
     */
    public static void clearCache() {
        GOAL_CLASS_CACHE.clear();
    }
}