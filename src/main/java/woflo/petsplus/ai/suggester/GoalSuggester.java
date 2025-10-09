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
     * Base: 1.0, modified by personality, mood, memory, variety, environment, energy, etc.
     */
    private float calculateDesirability(GoalType goalType, PetContext ctx) {
        float desirability = 1.0f;
        
        // Nature modifier (if PetsPlus pet)
        if (ctx.hasPetsPlusComponent() && ctx.natureProfile() != null) {
            desirability *= getNatureModifier(goalType, ctx);
        }
        
        // Mood blend modifier (if PetsPlus pet) - NOW DEEPLY INTEGRATED WITH ALL EMOTIONS
        if (ctx.hasPetsPlusComponent()) {
            desirability *= getMoodBlendModifier(goalType, ctx);
        }
        
        // Environmental context modifier (weather, time, biome)
        desirability *= getEnvironmentalModifier(goalType, ctx);
        
        // Energy-based gating (uses behavioral momentum)
        desirability *= getEnergyModifier(goalType, ctx);
        
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
     * Now integrates ALL 40+ emotions for deep behavioral immersion.
     */
    private float getMoodBlendModifier(GoalType goalType, PetContext ctx) {
        float modifier = 1.0f;
        
        // === MOOD-LEVEL MODIFIERS (Primary Behavioral States) ===
        
        // PLAYFUL - Energizes play behaviors
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.4f)) {
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 1.5f;
            }
            if (goalType == GoalType.TAIL_CHASE || goalType == GoalType.POUNCE_PRACTICE) {
                modifier *= 1.8f;
            }
        }
        
        // CALM - Reduces energetic behaviors, promotes stillness
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.CALM, 0.5f)) {
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.4f;
            }
            if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.FLOAT_IDLE) {
                modifier *= 1.8f;
            }
            if (goalType == GoalType.STARGAZING) {
                modifier *= 2.0f; // Perfect for contemplation
            }
        }
        
        // BONDED - Amplifies social/owner-centric behaviors
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.BONDED, 0.4f)) {
            if (goalType.getCategory() == GoalType.Category.SOCIAL) {
                modifier *= 1.6f;
            }
            if (goalType == GoalType.OWNER_ORBIT || goalType == GoalType.LEAN_AGAINST_OWNER) {
                modifier *= 2.0f;
            }
        }
        
        // CURIOUS - Drives investigation and exploration
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.CURIOUS, 0.4f)) {
            if (goalType == GoalType.INVESTIGATE_BLOCK || goalType == GoalType.SNIFF_GROUND) {
                modifier *= 1.7f;
            }
            if (goalType == GoalType.SCENT_TRAIL_FOLLOW) {
                modifier *= 1.5f;
            }
        }
        
        // RESTLESS - Needs movement and activity
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.RESTLESS, 0.4f)) {
            if (goalType.getCategory() == GoalType.Category.WANDER) {
                modifier *= 1.4f;
            }
            if (goalType == GoalType.CASUAL_WANDER || goalType == GoalType.PURPOSEFUL_PATROL) {
                modifier *= 1.6f;
            }
            // Severely reduces idle behaviors
            if (goalType.getCategory() == GoalType.Category.IDLE_QUIRK) {
                if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.FLOAT_IDLE) {
                    modifier *= 0.2f; // Can't sit still!
                }
            }
        }
        
        // PROTECTIVE - Guardian behaviors, owner-focused
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.PROTECTIVE, 0.4f)) {
            if (goalType == GoalType.OWNER_ORBIT || goalType == GoalType.PURPOSEFUL_PATROL) {
                modifier *= 1.8f;
            }
            if (goalType == GoalType.PERCH_HOP) {
                modifier *= 1.5f; // High ground for scouting
            }
            // Less playful when protective
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.6f;
            }
        }
        
        // YUGEN - Mystical, contemplative appreciation
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.YUGEN, 0.4f)) {
            if (goalType == GoalType.STARGAZING) {
                modifier *= 2.5f; // Perfect for mystery appreciation
            }
            if (goalType == GoalType.FLOAT_IDLE || goalType == GoalType.SIT_SPHINX_POSE) {
                modifier *= 1.6f;
            }
            // Reduces energetic behaviors
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.5f;
            }
        }
        
        // SAUDADE - Grief/longing reduces all behaviors
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.SAUDADE, 0.4f)) {
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.3f; // Not in the mood
            }
            if (goalType == GoalType.SIT_SPHINX_POSE) {
                modifier *= 1.8f; // Sitting quietly in grief
            }
            if (goalType.getCategory() == GoalType.Category.SOCIAL) {
                modifier *= 0.7f; // Withdrawn
            }
        }
        
        // FOCUSED - Task-oriented, reduces distractions
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.FOCUSED, 0.4f)) {
            if (goalType == GoalType.SCENT_TRAIL_FOLLOW || goalType == GoalType.INVESTIGATE_BLOCK) {
                modifier *= 1.8f;
            }
            if (goalType == GoalType.PURPOSEFUL_PATROL) {
                modifier *= 1.6f;
            }
            // Reduces idle quirks and play
            if (goalType.getCategory() == GoalType.Category.IDLE_QUIRK) {
                modifier *= 0.5f;
            }
        }
        
        // PASSIONATE - Intense, driven behaviors
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.PASSIONATE, 0.4f)) {
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 1.4f;
            }
            if (goalType == GoalType.PARKOUR_CHALLENGE || goalType == GoalType.AERIAL_ACROBATICS) {
                modifier *= 1.7f;
            }
        }
        
        // SISU - Determined perseverance
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.SISU, 0.4f)) {
            if (goalType == GoalType.PURPOSEFUL_PATROL || goalType == GoalType.PARKOUR_CHALLENGE) {
                modifier *= 1.6f;
            }
        }
        
        // AFRAID - Seeking safety, close to owner
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.AFRAID, 0.4f)) {
            if (goalType == GoalType.LEAN_AGAINST_OWNER || goalType == GoalType.OWNER_ORBIT) {
                modifier *= 2.2f; // Desperately seeking comfort
            }
            if (goalType.getCategory() == GoalType.Category.WANDER) {
                modifier *= 0.3f; // Don't want to wander far
            }
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.2f; // Too scared to play
            }
        }
        
        // ANGRY - Reduces social behaviors, increases intensity
        if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.ANGRY, 0.4f)) {
            if (goalType.getCategory() == GoalType.Category.SOCIAL) {
                modifier *= 0.5f;
            }
            if (goalType == GoalType.POUNCE_PRACTICE) {
                modifier *= 1.5f; // Aggressive energy
            }
        }
        
        // === EMOTION-LEVEL MODIFIERS (Granular Feelings) ===
        
        // KEFI - Vibrant vitality, zest for life
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.3f)) {
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 1.6f;
            }
            if (goalType == GoalType.AERIAL_ACROBATICS || goalType == GoalType.WATER_SPLASH) {
                modifier *= 1.8f; // Perfect for exuberant energy
            }
            if (goalType == GoalType.PARKOUR_CHALLENGE) {
                modifier *= 1.7f;
            }
        }
        
        // LAGOM - Perfect balance, contentment
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.3f)) {
            if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.FLOAT_IDLE) {
                modifier *= 1.9f; // Ideal for balanced rest
            }
            if (goalType == GoalType.STARGAZING) {
                modifier *= 1.6f;
            }
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.7f; // Content, not overly playful
            }
        }
        
        // ENNUI - Tedious boredom, needs change
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.ENNUI, 0.3f)) {
            // Reduces all idle quirks - sick of doing nothing
            if (goalType.getCategory() == GoalType.Category.IDLE_QUIRK) {
                modifier *= 0.3f;
            }
            // Increases play to break monotony
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 1.5f;
            }
            if (goalType == GoalType.HIDE_AND_SEEK || goalType == GoalType.INVESTIGATE_BLOCK) {
                modifier *= 1.7f; // Needs novelty
            }
        }
        
        // FOREBODING - Dread, anticipatory fear
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.FOREBODING, 0.3f)) {
            if (goalType == GoalType.LEAN_AGAINST_OWNER || goalType == GoalType.OWNER_ORBIT) {
                modifier *= 2.0f; // Seeks safety near owner
            }
            if (goalType == GoalType.PERCH_ON_SHOULDER) {
                modifier *= 1.8f; // Wants to hide on owner
            }
            if (goalType.getCategory() == GoalType.Category.WANDER) {
                modifier *= 0.4f; // Too worried to explore
            }
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.3f;
            }
        }
        
        // YUGEN (Emotion) - Mysterious, profound beauty appreciation
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.YUGEN, 0.3f)) {
            if (goalType == GoalType.STARGAZING) {
                modifier *= 2.8f; // Peak contemplation
            }
            if (goalType == GoalType.FLOAT_IDLE) {
                modifier *= 1.6f; // Drifting in wonder
            }
            if (goalType == GoalType.INVESTIGATE_BLOCK) {
                modifier *= 1.4f; // Curious about mysteries
            }
        }
        
        // SOBREMESA - Togetherness, bonding contentment
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.3f)) {
            if (goalType.getCategory() == GoalType.Category.SOCIAL) {
                modifier *= 1.8f; // Desires social connection
            }
            if (goalType == GoalType.LEAN_AGAINST_OWNER || goalType == GoalType.PARALLEL_PLAY) {
                modifier *= 2.0f; // Perfect for bonding
            }
            if (goalType == GoalType.PERCH_ON_SHOULDER) {
                modifier *= 1.9f;
            }
        }
        
        // HIRAETH - Longing for home/belonging
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.HIRAETH, 0.3f)) {
            if (goalType == GoalType.OWNER_ORBIT) {
                modifier *= 2.2f; // Wants to be near "home" (owner)
            }
            if (goalType == GoalType.LEAN_AGAINST_OWNER) {
                modifier *= 1.9f;
            }
            if (goalType.getCategory() == GoalType.Category.WANDER) {
                modifier *= 0.5f; // Doesn't want to stray
            }
        }
        
        // GUARDIAN_VIGIL (Emotion) - Guarding instinct
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.GUARDIAN_VIGIL, 0.3f)) {
            if (goalType == GoalType.OWNER_ORBIT || goalType == GoalType.PURPOSEFUL_PATROL) {
                modifier *= 1.9f;
            }
            if (goalType == GoalType.PERK_EARS_SCAN) {
                modifier *= 1.6f; // Alert scanning
            }
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.6f; // On duty, not playing
            }
        }
        
        // VIGILANT - Watchful alertness
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.VIGILANT, 0.3f)) {
            if (goalType == GoalType.PURPOSEFUL_PATROL || goalType == GoalType.PERK_EARS_SCAN) {
                modifier *= 1.8f;
            }
            if (goalType == GoalType.PERCH_HOP) {
                modifier *= 1.7f; // High ground for watching
            }
            if (goalType.getCategory() == GoalType.Category.IDLE_QUIRK) {
                if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.FLOAT_IDLE) {
                    modifier *= 0.4f; // Too alert to rest
                }
            }
        }
        
        // WABI_SABI - Appreciation of imperfection
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.WABI_SABI, 0.3f)) {
            if (goalType == GoalType.INVESTIGATE_BLOCK || goalType == GoalType.SNIFF_GROUND) {
                modifier *= 1.7f; // Curious about details
            }
            if (goalType == GoalType.CIRCLE_SPOT) {
                modifier *= 1.4f; // Quirky behavior fits
            }
        }
        
        // NOSTALGIA - Remembering the past
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.NOSTALGIA, 0.3f)) {
            if (goalType == GoalType.SCENT_TRAIL_FOLLOW) {
                modifier *= 1.8f; // Following old trails
            }
            if (goalType == GoalType.SIT_SPHINX_POSE) {
                modifier *= 1.5f; // Quiet remembering
            }
        }
        
        // PLAYFULNESS (Emotion) - Direct play drive
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.3f)) {
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 1.7f;
            }
            if (goalType == GoalType.TAIL_CHASE || goalType == GoalType.TOY_POUNCE) {
                modifier *= 1.9f;
            }
        }
        
        // CURIOUS (Emotion) - Investigative drive
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.CURIOUS, 0.3f)) {
            if (goalType == GoalType.INVESTIGATE_BLOCK || goalType == GoalType.SNIFF_GROUND) {
                modifier *= 1.8f;
            }
            if (goalType == GoalType.SCENT_TRAIL_FOLLOW) {
                modifier *= 1.6f;
            }
            if (goalType == GoalType.HIDE_AND_SEEK) {
                modifier *= 1.5f;
            }
        }
        
        // CHEERFUL - Bursting joy
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.CHEERFUL, 0.3f)) {
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 1.6f;
            }
            if (goalType == GoalType.TAIL_CHASE || goalType == GoalType.BUBBLE_PLAY) {
                modifier *= 1.8f;
            }
        }
        
        // RELIEF - Post-stress relaxation
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.RELIEF, 0.3f)) {
            if (goalType == GoalType.STRETCH_AND_YAW || goalType == GoalType.SIT_SPHINX_POSE) {
                modifier *= 1.7f; // Relaxing after stress
            }
            if (goalType == GoalType.LEAN_AGAINST_OWNER) {
                modifier *= 1.8f; // Seeking comfort
            }
        }
        
        // PRIDE - Accomplishment, showing off
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.PRIDE, 0.3f)) {
            if (goalType == GoalType.SHOW_OFF_TRICK) {
                modifier *= 2.0f; // Perfect for displaying pride
            }
            if (goalType == GoalType.PARKOUR_CHALLENGE || goalType == GoalType.AERIAL_ACROBATICS) {
                modifier *= 1.6f; // Showing off skills
            }
        }
        
        // LOYALTY - Devotion to owner
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.3f)) {
            if (goalType == GoalType.OWNER_ORBIT || goalType == GoalType.FETCH_ITEM) {
                modifier *= 1.9f;
            }
            if (goalType == GoalType.GIFT_BRINGING) {
                modifier *= 2.0f; // Bringing gifts out of loyalty
            }
        }
        
        // UBUNTU - Pack unity, communal spirit
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.3f)) {
            if (goalType == GoalType.PARALLEL_PLAY) {
                modifier *= 2.0f; // Playing together
            }
            if (goalType.getCategory() == GoalType.Category.SOCIAL) {
                modifier *= 1.6f;
            }
        }
        
        // STOIC - Enduring, unmoved
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.STOIC, 0.3f)) {
            if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.PURPOSEFUL_PATROL) {
                modifier *= 1.6f;
            }
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.5f; // Too stoic for frivolity
            }
        }
        
        // RESTLESS (Emotion) - Can't sit still
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.RESTLESS, 0.3f)) {
            if (goalType.getCategory() == GoalType.Category.WANDER) {
                modifier *= 1.7f;
            }
            if (goalType == GoalType.TAIL_CHASE || goalType == GoalType.CIRCLE_SPOT) {
                modifier *= 1.5f; // Fidgety behaviors
            }
            if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.FLOAT_IDLE) {
                modifier *= 0.2f; // Cannot be still!
            }
        }
        
        // FERNWEH - Wanderlust, desire to travel
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.FERNWEH, 0.3f)) {
            if (goalType.getCategory() == GoalType.Category.WANDER) {
                modifier *= 1.9f;
            }
            if (goalType == GoalType.AERIAL_PATROL || goalType == GoalType.WATER_CRUISE) {
                modifier *= 2.0f; // Long journeys
            }
        }
        
        // HOPEFUL - Optimistic anticipation
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.HOPEFUL, 0.3f)) {
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 1.4f;
            }
            if (goalType == GoalType.INVESTIGATE_BLOCK || goalType == GoalType.FETCH_ITEM) {
                modifier *= 1.5f;
            }
        }
        
        // FOCUSED (Emotion) - Concentrated attention
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.FOCUSED, 0.3f)) {
            if (goalType == GoalType.SCENT_TRAIL_FOLLOW || goalType == GoalType.INVESTIGATE_BLOCK) {
                modifier *= 1.8f;
            }
            if (goalType == GoalType.SNIFF_GROUND || goalType == GoalType.PERK_EARS_SCAN) {
                modifier *= 1.6f;
            }
            if (goalType.getCategory() == GoalType.Category.IDLE_QUIRK) {
                if (goalType != GoalType.PERK_EARS_SCAN && goalType != GoalType.SNIFF_GROUND) {
                    modifier *= 0.6f; // Reduced distraction quirks
                }
            }
        }
        
        // STARTLE - Jumpiness
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.STARTLE, 0.3f)) {
            if (goalType == GoalType.PERCH_HOP || goalType == GoalType.PERK_EARS_SCAN) {
                modifier *= 1.5f; // Alert behaviors
            }
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.5f; // Too jumpy to play
            }
        }
        
        // MONO_NO_AWARE - Bittersweet transience appreciation
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.MONO_NO_AWARE, 0.3f)) {
            if (goalType == GoalType.STARGAZING) {
                modifier *= 2.0f; // Contemplating impermanence
            }
            if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.FLOAT_IDLE) {
                modifier *= 1.6f;
            }
        }
        
        // CONTENT - Satisfied peacefulness
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.3f)) {
            if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.LEAN_AGAINST_OWNER) {
                modifier *= 1.7f;
            }
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.8f; // Content, not overly energetic
            }
        }
        
        // FRUSTRATED - Irritation, blocked goals
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.FRUSTRATION, 0.3f)) {
            if (goalType == GoalType.POUNCE_PRACTICE) {
                modifier *= 1.5f; // Working out frustration
            }
            if (goalType.getCategory() == GoalType.Category.SOCIAL) {
                modifier *= 0.6f; // Not in social mood
            }
        }
        
        // REGRET - Remorse, wishing to undo
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.REGRET, 0.3f)) {
            if (goalType == GoalType.LEAN_AGAINST_OWNER) {
                modifier *= 1.8f; // Seeking forgiveness
            }
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.5f; // Not playful when regretful
            }
        }
        
        // QUERENCIA - Sacred safe space feeling
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.QUERECIA, 0.3f)) {
            if (goalType == GoalType.CIRCLE_SPOT || goalType == GoalType.SIT_SPHINX_POSE) {
                modifier *= 1.9f; // Settling into safe spot
            }
            if (goalType.getCategory() == GoalType.Category.WANDER) {
                modifier *= 0.6f; // Doesn't want to leave safe space
            }
        }
        
        // SISU (Emotion) - Determined grit
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.SISU, 0.3f)) {
            if (goalType == GoalType.PURPOSEFUL_PATROL || goalType == GoalType.PARKOUR_CHALLENGE) {
                modifier *= 1.7f;
            }
        }
        
        // GAMAN - Enduring with patience
        if (ctx.hasEmotionAbove(woflo.petsplus.state.PetComponent.Emotion.GAMAN, 0.3f)) {
            if (goalType == GoalType.SIT_SPHINX_POSE) {
                modifier *= 1.8f; // Patient waiting
            }
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.6f; // Patiently restraining play urges
            }
        }
        
        return modifier;
    }
    
    /**
     * Environmental context influences behavior (weather, time of day, biome).
     * Creates immersive behavioral shifts based on world state.
     */
    private float getEnvironmentalModifier(GoalType goalType, PetContext ctx) {
        float modifier = 1.0f;
        
        // === TIME OF DAY MODIFIERS ===
        
        boolean isNight = !ctx.isDaytime();
        
        // STARGAZING - Only at night, massive boost
        if (goalType == GoalType.STARGAZING) {
            if (isNight) {
                modifier *= 3.0f; // Stargazing is a night activity!
            } else {
                return 0.0f; // Can't stargaze during day
            }
        }
        
        // Night increases contemplative/restful behaviors
        if (isNight) {
            if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.FLOAT_IDLE) {
                modifier *= 1.3f; // More restful at night
            }
            if (goalType == GoalType.PERCH_HOP) {
                modifier *= 1.4f; // Birds perch at night
            }
            // Reduces energetic play behaviors at night
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.7f; // Less playful at night
            }
            // Increases owner-proximity behaviors at night (seeking safety)
            if (goalType == GoalType.OWNER_ORBIT || goalType == GoalType.LEAN_AGAINST_OWNER) {
                modifier *= 1.2f;
            }
        }
        
        // Day increases energetic behaviors
        if (ctx.isDaytime()) {
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 1.2f; // More playful during day
            }
            if (goalType.getCategory() == GoalType.Category.WANDER) {
                modifier *= 1.15f; // More exploring during day
            }
        }
        
        // === WEATHER MODIFIERS ===
        // Check if world is raining or thundering
        boolean isRaining = ctx.mob().getEntityWorld().isRaining();
        boolean isThundering = ctx.mob().getEntityWorld().isThundering();
        
        if (isRaining) {
            // Rain reduces outdoor wandering
            if (goalType.getCategory() == GoalType.Category.WANDER) {
                modifier *= 0.6f; // Don't want to wander far in rain
            }
            // Rain increases owner-proximity (seeking shelter)
            if (goalType == GoalType.LEAN_AGAINST_OWNER) {
                modifier *= 1.5f;
            }
            // Rain reduces play
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.7f;
            }
            // But water-based goals get boost in rain!
            if (goalType == GoalType.WATER_SPLASH || goalType == GoalType.BUBBLE_PLAY) {
                modifier *= 1.3f; // Splash in the rain!
            }
            // Indoor idle behaviors increased
            if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.CIRCLE_SPOT) {
                modifier *= 1.4f; // Cozy indoors
            }
        }
        
        if (isThundering) {
            // Thunder creates fear - massive boost to owner-proximity
            if (goalType == GoalType.LEAN_AGAINST_OWNER || goalType == GoalType.OWNER_ORBIT) {
                modifier *= 2.0f; // Hide near owner during storm!
            }
            if (goalType == GoalType.PERCH_ON_SHOULDER) {
                modifier *= 2.2f; // Small pets hide on owner
            }
            // Thunder severely reduces wandering and play
            if (goalType.getCategory() == GoalType.Category.WANDER) {
                modifier *= 0.3f;
            }
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.4f;
            }
        }
        
        // === BIOME-BASED MODIFIERS (using mob's current biome) ===
        
        try {
            var biome = ctx.mob().getEntityWorld().getBiome(ctx.currentPos());
            
            // Check biome keys
            var biomeKey = biome.getKey();
            if (biomeKey.isPresent()) {
                var key = biomeKey.get();
                
                // SNOWY/TAIGA BIOMES - Restlessness during day (as designed!)
                if (key.getValue().toString().contains("taiga") || 
                    key.getValue().toString().contains("snow") ||
                    key.getValue().toString().contains("ice") ||
                    key.getValue().toString().contains("frozen")) {
                    
                    // In cold biomes during the day, pets get restless
                    if (ctx.isDaytime()) {
                        if (goalType.getCategory() == GoalType.Category.WANDER) {
                            modifier *= 1.3f; // Need to move to stay warm
                        }
                        if (goalType == GoalType.CASUAL_WANDER || goalType == GoalType.PURPOSEFUL_PATROL) {
                            modifier *= 1.4f;
                        }
                    } else {
                        // At night in cold biomes, huddle behavior
                        if (goalType == GoalType.LEAN_AGAINST_OWNER || goalType == GoalType.CIRCLE_SPOT) {
                            modifier *= 1.5f; // Huddle for warmth
                        }
                    }
                }
                
                // OCEAN/WATER BIOMES - Boost aquatic behaviors
                if (key.getValue().toString().contains("ocean") ||
                    key.getValue().toString().contains("river") ||
                    key.getValue().toString().contains("beach")) {
                    
                    if (goalType == GoalType.WATER_CRUISE || goalType == GoalType.DIVE_PLAY || 
                        goalType == GoalType.WATER_SPLASH || goalType == GoalType.ORBIT_SWIM) {
                        modifier *= 1.5f; // Perfect water environment
                    }
                }
                
                // DESERT BIOMES - Reduce energy expenditure
                if (key.getValue().toString().contains("desert") ||
                    key.getValue().toString().contains("savanna") ||
                    key.getValue().toString().contains("badlands")) {
                    
                    if (ctx.isDaytime()) {
                        // Hot days = conserve energy
                        if (goalType.getCategory() == GoalType.Category.PLAY) {
                            modifier *= 0.6f; // Too hot to play
                        }
                        if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.FLOAT_IDLE) {
                            modifier *= 1.6f; // Resting in shade
                        }
                    }
                }
                
                // FOREST/JUNGLE BIOMES - Exploration and investigation
                if (key.getValue().toString().contains("forest") ||
                    key.getValue().toString().contains("jungle") ||
                    key.getValue().toString().contains("grove")) {
                    
                    if (goalType == GoalType.INVESTIGATE_BLOCK || goalType == GoalType.SNIFF_GROUND ||
                        goalType == GoalType.SCENT_TRAIL_FOLLOW) {
                        modifier *= 1.4f; // Rich environment to explore
                    }
                    if (goalType == GoalType.PERCH_HOP) {
                        modifier *= 1.3f; // Trees to perch on
                    }
                }
                
                // MOUNTAINS/PEAKS - High-ground behaviors
                if (key.getValue().toString().contains("mountain") ||
                    key.getValue().toString().contains("peak") ||
                    key.getValue().toString().contains("hill")) {
                    
                    if (goalType == GoalType.PERCH_HOP || goalType == GoalType.AERIAL_PATROL) {
                        modifier *= 1.5f; // High vantage points
                    }
                    if (goalType == GoalType.PURPOSEFUL_PATROL) {
                        modifier *= 1.3f; // Patrolling high ground
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail if biome lookup fails
        }
        
        return modifier;
    }
    
    /**
     * Energy-based modifiers using behavioral momentum.
     * High-energy pets prefer active behaviors, low-energy pets prefer rest.
     */
    private float getEnergyModifier(GoalType goalType, PetContext ctx) {
        float momentum = ctx.behavioralMomentum();
        
        // Use goal type's energy bias as base
        float energyBias = goalType.getEnergyBias(momentum);
        
        // Additional contextual modifiers based on energy level
        float modifier = energyBias;
        
        // HIGH ENERGY (momentum > 0.7) - Craves activity
        if (momentum > 0.7f) {
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 1.3f; // Extra boost to play
            }
            if (goalType == GoalType.AERIAL_ACROBATICS || goalType == GoalType.PARKOUR_CHALLENGE) {
                modifier *= 1.5f; // Perfect for high energy
            }
            // Penalize rest heavily when hyperactive
            if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.FLOAT_IDLE) {
                modifier *= 0.3f; // Can't sit still!
            }
        }
        
        // LOW ENERGY (momentum < 0.3) - Needs rest
        if (momentum < 0.3f) {
            if (goalType.getCategory() == GoalType.Category.IDLE_QUIRK) {
                if (goalType == GoalType.SIT_SPHINX_POSE || goalType == GoalType.FLOAT_IDLE || 
                    goalType == GoalType.STRETCH_AND_YAW) {
                    modifier *= 1.6f; // Perfect for tired pets
                }
            }
            // Penalize energetic behaviors when tired
            if (goalType.getCategory() == GoalType.Category.PLAY) {
                modifier *= 0.4f; // Too tired to play
            }
            if (goalType == GoalType.PARKOUR_CHALLENGE || goalType == GoalType.AERIAL_ACROBATICS) {
                modifier *= 0.2f; // Way too tired
            }
        }
        
        // MEDIUM ENERGY (0.3 - 0.7) - Balanced, prefers wander
        if (momentum >= 0.3f && momentum <= 0.7f) {
            if (goalType.getCategory() == GoalType.Category.WANDER) {
                modifier *= 1.2f; // Moderate activity ideal
            }
            if (goalType.getCategory() == GoalType.Category.SOCIAL) {
                modifier *= 1.15f; // Good energy for socializing
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

