package woflo.petsplus.ai;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.director.AdaptiveDirector;
import woflo.petsplus.ai.director.DirectorDecision;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.planner.DeterministicPlanner;
import woflo.petsplus.ai.suggester.GoalSuggester;
import woflo.petsplus.mixin.MobEntityAccessor;
import woflo.petsplus.state.PetComponent;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class AdaptiveAIManager {
    
    private static final GoalSuggester SUGGESTER = new GoalSuggester();
    private static final DeterministicPlanner PLANNER = new DeterministicPlanner();
    private static final Map<MobEntity, AdaptiveDirector> DIRECTORS = new WeakHashMap<>();
    
    public static void initializeAdaptiveAI(MobEntity mob) {
        MobCapabilities.CapabilityProfile capabilities = MobCapabilities.analyze(mob);

        addCompatibleGoals(mob, capabilities);
    }
    
    /**
     * Remove all adaptive AI goals from a mob.
     */
    public static void clearAdaptiveAI(MobEntity mob) {
        try {
            MobEntityAccessor accessor = (MobEntityAccessor) mob;
            var goalSelector = accessor.getGoalSelector();
            
            // Remove all AdaptiveGoal instances
            goalSelector.getGoals().removeIf(goal -> goal.getGoal() instanceof AdaptiveGoal);
            
        } catch (Exception e) {
            // Silently fail if mob doesn't support goal removal
        }
    }
    
    /**
     * Reinitialize AI (useful after capability changes).
     */
    public static void reinitializeAdaptiveAI(MobEntity mob) {
        clearAdaptiveAI(mob);
        initializeAdaptiveAI(mob);
    }
    
    /**
     * Add all goals that are compatible with the mob's capabilities.
     */
    private static void addCompatibleGoals(MobEntity mob, MobCapabilities.CapabilityProfile capabilities) {
        try {
            MobEntityAccessor accessor = (MobEntityAccessor) mob;
            var goalSelector = accessor.getGoalSelector();

            for (GoalDefinition definition : GoalRegistry.all()) {
                if (!definition.isCompatible(capabilities)) {
                    continue;
                }

                AdaptiveGoal goal = definition.createGoal(mob);
                if (goal == null) {
                    continue;
                }

                goalSelector.add(definition.priority(), goal);
            }

        } catch (Exception e) {
            Petsplus.LOGGER.error(
                "Failed to initialize adaptive AI for {}", mob.getType().getName().getString(), e
            );
        }
    }

    /**
     * Get suggested goals for a mob based on current context.
     * This is the AI "brain" - use for debugging or advanced control.
     */
    public static List<GoalSuggester.Suggestion> getSuggestions(MobEntity mob) {
        PetComponent pc = PetComponent.get(mob);
        PetContext ctx = pc != null ? PetContext.capture(mob, pc) : PetContext.captureVanilla(mob);
        return SUGGESTER.suggest(ctx);
    }

    private static AdaptiveDirector directorFor(MobEntity mob) {
        return DIRECTORS.computeIfAbsent(mob, ignored -> new AdaptiveDirector(SUGGESTER, PLANNER));
    }

    public static DirectorDecision tickDirector(MobEntity mob) {
        PetComponent component = PetComponent.get(mob);
        PetContext context = component != null ? PetContext.capture(mob, component) : PetContext.captureVanilla(mob);
        return directorFor(mob).decide(mob, context);
    }

    /**
     * Check if a mob has the new adaptive AI system.
     */
    public static boolean hasAdaptiveAI(MobEntity mob) {
        try {
            MobEntityAccessor accessor = (MobEntityAccessor) mob;
            return accessor.getGoalSelector().getGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof AdaptiveGoal);
        } catch (Exception e) {
            return false;
        }
    }
}
