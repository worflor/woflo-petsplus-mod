package woflo.petsplus.ai.suggester;

import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.GoalType;

import java.util.*;

/**
 * Evaluates pet state and suggests behavioral goals with confidence weights.
 * Does NOT execute goals - just provides weighted recommendations.
 * 
 * This is the brain of the AI enhancement system.
 */
public class GoalSuggester {
    
    /**
     * A suggested goal with contextual weight.
     */
    public record Suggestion(
        GoalType type,
        float desirability,    // 0.0 = no interest, 1.0+ = strong desire
        float feasibility,     // 0.0 = impossible, 1.0 = fully feasible
        String reason,         // Debug/explanation text
        Map<String, Object> context
    ) {
        public float score() {
            return desirability * feasibility;
        }
    }
    
    /**
     * Suggest goals for the given pet context.
     * Returns a list of viable suggestions, sorted by score.
     */
    public List<Suggestion> suggest(PetContext ctx) {
        List<Suggestion> pool = new ArrayList<>();
        
        // Get mob capabilities
        MobCapabilities.CapabilityProfile capabilities = MobCapabilities.analyze(ctx.mob());
        
        // Generate suggestions for all compatible goals
        for (GoalType goalType : GoalType.values()) {
            // Filter by capability
            if (!goalType.isCompatible(capabilities)) {
                continue;
            }
            
            // Calculate desirability
            float desirability = calculateDesirability(goalType, ctx);
            if (desirability <= 0.0f) {
                continue; // Not interested
            }
            
            // Calculate feasibility
            float feasibility = calculateFeasibility(goalType, ctx);
            if (feasibility <= 0.0f) {
                continue; // Not possible right now
            }
            
            // Create suggestion
            String reason = explainSuggestion(goalType, ctx, desirability, feasibility);
            Map<String, Object> context = new HashMap<>();
            
            pool.add(new Suggestion(goalType, desirability, feasibility, reason, context));
        }
        
        // Sort by score (descending)
        pool.sort((a, b) -> Float.compare(b.score(), a.score()));
        
        return pool;
    }
    
    /**
     * Calculate how much the pet wants to do this goal.
     * Base: 1.0, modified by personality, mood, memory, variety, etc.
     */
    private float calculateDesirability(GoalType goalType, PetContext ctx) {
        float desirability = 1.0f;
        
        // Nature modifier (if PetsPlus pet)
        if (ctx.hasPetsPlusComponent() && ctx.natureProfile() != null) {
            desirability *= getNatureModifier(goalType, ctx);
        }
        
        // Mood blend modifier (if PetsPlus pet)
        if (ctx.hasPetsPlusComponent()) {
            desirability *= getMoodBlendModifier(goalType, ctx);
        }
        
        // Age modifier
        desirability *= getAgeModifier(goalType, ctx);
        
        // Bond modifier
        desirability *= getBondModifier(goalType, ctx);
        
        // Variety penalty (don't repeat same goal too often)
        desirability *= getVarietyMultiplier(goalType, ctx);
        
        // Memory bias (did we enjoy this before?)
        if (ctx.hasPetsPlusComponent()) {
            desirability *= getMemoryBias(goalType, ctx);
        }
        
        return desirability;
    }
    
    /**
     * Calculate how feasible this goal is right now.
     * 1.0 = can do it, 0.0 = can't do it
     */
    private float calculateFeasibility(GoalType goalType, PetContext ctx) {
        float feasibility = 1.0f;
        
        // Owner proximity check for social goals
        if (goalType.getCategory() == GoalType.Category.SOCIAL) {
            if (!ctx.ownerNearby()) {
                return 0.0f;
            }
            // Closer owner = more feasible
            feasibility *= Math.max(0.2f, 1.0f - (ctx.distanceToOwner() / 16.0f));
        }
        
        // Check if mob is stuck or in a bad position
        if (!ctx.mob().isOnGround() && !ctx.mob().isTouchingWater() && !ctx.mob().hasVehicle()) {
            feasibility *= 0.3f; // Harder to start new goals while airborne
        }
        
        // Check if mob is in combat
        if (ctx.mob().getAttacker() != null || ctx.mob().getAttacking() != null) {
            return 0.0f; // Never suggest goals during combat
        }
        
        return feasibility;
    }
    
    /**
     * Nature influences goal preferences (PetsPlus only).
     */
    private float getNatureModifier(GoalType goalType, PetContext ctx) {
        // TODO: Implement nature-specific modifiers
        // For now, return neutral
        return 1.0f;
    }
    
    /**
     * Mood blend influences current tendencies (PetsPlus only).
     */
    private float getMoodBlendModifier(GoalType goalType, PetContext ctx) {
        float modifier = 1.0f;
        
        // Playful mood increases play behaviors
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.4f)) {
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 1.5f;
            }
            if (goalType == GoalType.TAIL_CHASE || goalType == GoalType.POUNCE_PRACTICE) {
                modifier *= 1.8f;
            }
        }
        
        // Calm mood decreases energetic behaviors
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.CALM, 0.5f)) {
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.4f;
            }
            if (goalType == GoalType.SIT_SPHINX_POSE) {
                modifier *= 1.8f;
            }
        }
        
        // Bonded mood increases social behaviors
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.BONDED, 0.4f)) {
            if (goalType.getCategory() == GoalType.Category.SOCIAL) {
                modifier *= 1.6f;
            }
            if (goalType == GoalType.OWNER_ORBIT) {
                modifier *= 2.0f;
            }
        }
        
        // Curious mood increases investigation
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.CURIOUS, 0.4f)) {
            if (goalType == GoalType.INVESTIGATE_BLOCK || goalType == GoalType.SNIFF_GROUND) {
                modifier *= 1.7f;
            }
        }
        
        // Restless mood increases wander behaviors
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.RESTLESS, 0.4f)) {
            if (goalType.getCategory() == GoalType.Category.WANDER) {
                modifier *= 1.4f;
            }
        }
        
        return modifier;
    }
    
    /**
     * Age influences behavioral preferences.
     */
    private float getAgeModifier(GoalType goalType, PetContext ctx) {
        PetContext.AgeCategory age = ctx.getAgeCategory();
        
        if (age == PetContext.AgeCategory.YOUNG) {
            // Young pets prefer playful behaviors
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                return 1.5f;
            }
            if (goalType == GoalType.TAIL_CHASE) {
                return 2.0f;
            }
            if (goalType == GoalType.SIT_SPHINX_POSE) {
                return 0.3f;
            }
        } else if (age == PetContext.AgeCategory.MATURE) {
            // Mature pets prefer calm behaviors
            if (goalType == GoalType.SIT_SPHINX_POSE) {
                return 1.5f;
            }
            if (goalType == GoalType.TAIL_CHASE) {
                return 0.6f;
            }
        }
        
        return 1.0f;
    }
    
    /**
     * Bond strength influences social behaviors.
     */
    private float getBondModifier(GoalType goalType, PetContext ctx) {
        if (goalType.getCategory() == GoalType.Category.SOCIAL) {
            // Higher bond = more social behaviors
            return 1.0f + (ctx.bondStrength() * 0.8f);
        }
        return 1.0f;
    }
    
    /**
     * Variety multiplier - penalize recently executed goals.
     */
    private float getVarietyMultiplier(GoalType goalType, PetContext ctx) {
        long ticksSinceLastExecution = ctx.ticksSince(goalType);
        
        // Recently executed? Big penalty
        if (ticksSinceLastExecution < 100) {
            return 0.3f;
        } else if (ticksSinceLastExecution < 300) {
            return 0.7f;
        } else if (ticksSinceLastExecution < 600) {
            return 0.9f;
        }
        
        // Check if in recent goals queue
        if (ctx.recentGoals().contains(goalType)) {
            int position = 0;
            for (GoalType recent : ctx.recentGoals()) {
                if (recent == goalType) {
                    break;
                }
                position++;
            }
            
            // More recent = bigger penalty
            if (position == 0) {
                return 0.3f; // Just did this!
            } else if (position == 1) {
                return 0.6f;
            } else if (position == 2) {
                return 0.8f;
            }
        }
        
        return 1.0f;
    }
    
    /**
     * Memory bias - did we enjoy this goal before?
     */
    private float getMemoryBias(GoalType goalType, PetContext ctx) {
        // TODO: Implement with GoalMemory system in Phase 5
        return 1.0f;
    }
    
    /**
     * Explain why this suggestion was made (for debugging).
     */
    private String explainSuggestion(GoalType goalType, PetContext ctx, float desirability, float feasibility) {
        StringBuilder reason = new StringBuilder();
        reason.append(goalType.name()).append(" (");
        
        if (ctx.hasPetsPlusComponent()) {
            if (ctx.currentMood() != null) {
                reason.append("Mood: ").append(ctx.currentMood().name()).append(", ");
            }
        }
        
        reason.append("Age: ").append(ctx.getAgeCategory()).append(", ");
        reason.append("Desire: ").append(String.format("%.2f", desirability)).append(", ");
        reason.append("Feasible: ").append(String.format("%.2f", feasibility));
        reason.append(")");
        
        return reason.toString();
    }
}
