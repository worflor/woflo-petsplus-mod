package woflo.petsplus.ai;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.*;
import woflo.petsplus.ai.goals.idle.*;
import woflo.petsplus.ai.goals.play.*;
import woflo.petsplus.ai.goals.social.*;
import woflo.petsplus.ai.goals.special.*;
import woflo.petsplus.ai.goals.wander.*;
import woflo.petsplus.ai.suggester.GoalSuggester;
import woflo.petsplus.mixin.MobEntityAccessor;
import woflo.petsplus.state.PetComponent;

import java.util.*;

/**
 * NEW AI Manager using the capability-based, pet-agnostic goal suggestion system.
 * 
 * Completely replaces the old mood-based system with:
 * - Capability detection (works with ANY mob)
 * - Weighted goal suggestions
 * - Personality-driven behavior
 * - Memory and learning
 * 
 * This is the ONLY AI manager you need now.
 */
public class AdaptiveAIManager {
    
    private static final GoalSuggester SUGGESTER = new GoalSuggester();
    
    /**
     * Initialize the new adaptive AI system for a mob.
     * Works with vanilla mobs, PetsPlus pets, and modded mobs.
     */
    public static void initializeAdaptiveAI(MobEntity mob) {
        // Analyze what this mob can do
        MobCapabilities.CapabilityProfile capabilities = MobCapabilities.analyze(mob);
        
        // Add all compatible goals
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
            
            // === IDLE QUIRKS (Priority 28) ===
            // Universal quirks
            if (GoalType.TAIL_CHASE.isCompatible(capabilities)) {
                goalSelector.add(28, new TailChaseGoal(mob));
            }
            if (GoalType.CIRCLE_SPOT.isCompatible(capabilities)) {
                goalSelector.add(28, new CircleSpotGoal(mob));
            }
            if (GoalType.STRETCH_AND_YAW.isCompatible(capabilities)) {
                goalSelector.add(28, new StretchAndYawnGoal(mob));
            }
            
            // Land-specific quirks
            if (GoalType.SNIFF_GROUND.isCompatible(capabilities)) {
                goalSelector.add(28, new SniffGroundGoal(mob));
            }
            if (GoalType.POUNCE_PRACTICE.isCompatible(capabilities)) {
                goalSelector.add(28, new PouncePracticeGoal(mob));
            }
            if (GoalType.PERK_EARS_SCAN.isCompatible(capabilities)) {
                goalSelector.add(28, new PerkEarsScanGoal(mob));
            }
            if (GoalType.SIT_SPHINX_POSE.isCompatible(capabilities)) {
                goalSelector.add(28, new SitSphinxPoseGoal(mob));
            }
            
            // Flying-specific quirks
            if (GoalType.PREEN_FEATHERS.isCompatible(capabilities)) {
                goalSelector.add(28, new PreenFeathersGoal(mob));
            }
            if (GoalType.WING_FLUTTER.isCompatible(capabilities)) {
                goalSelector.add(28, new WingFlutterGoal(mob));
            }
            if (GoalType.PERCH_HOP.isCompatible(capabilities)) {
                goalSelector.add(27, new PerchHopGoal(mob));
            }
            
            // Aquatic-specific quirks
            if (GoalType.FLOAT_IDLE.isCompatible(capabilities)) {
                goalSelector.add(28, new FloatIdleGoal(mob));
            }
            if (GoalType.BUBBLE_PLAY.isCompatible(capabilities)) {
                goalSelector.add(28, new BubblePlayGoal(mob));
            }
            if (GoalType.SURFACE_BREATH.isCompatible(capabilities)) {
                goalSelector.add(27, new SurfaceBreathGoal(mob));
            }
            
            // === WANDER VARIANTS (Priority 20) ===
            if (GoalType.CASUAL_WANDER.isCompatible(capabilities)) {
                goalSelector.add(20, new CasualWanderGoal(mob));
            }
            if (GoalType.AERIAL_PATROL.isCompatible(capabilities)) {
                goalSelector.add(20, new AerialPatrolGoal(mob));
            }
            if (GoalType.WATER_CRUISE.isCompatible(capabilities)) {
                goalSelector.add(20, new WaterCruiseGoal(mob));
            }
            if (GoalType.OWNER_ORBIT.isCompatible(capabilities)) {
                goalSelector.add(20, new OwnerOrbitGoal(mob));
            }
            if (GoalType.SCENT_TRAIL_FOLLOW.isCompatible(capabilities)) {
                goalSelector.add(20, new ScentTrailFollowGoal(mob));
            }
            if (GoalType.PURPOSEFUL_PATROL.isCompatible(capabilities)) {
                goalSelector.add(20, new PurposefulPatrolGoal(mob));
            }
            
            // === PLAY BEHAVIORS (Priority 18) ===
            if (GoalType.TOY_POUNCE.isCompatible(capabilities)) {
                goalSelector.add(18, new ToyPounceGoal(mob));
            }
            if (GoalType.PARKOUR_CHALLENGE.isCompatible(capabilities)) {
                goalSelector.add(18, new ParkourChallengeGoal(mob));
            }
            if (GoalType.FETCH_ITEM.isCompatible(capabilities)) {
                goalSelector.add(18, new FetchItemGoal(mob));
            }
            if (GoalType.DIVE_PLAY.isCompatible(capabilities)) {
                goalSelector.add(18, new DivePlayGoal(mob));
            }
            if (GoalType.AERIAL_ACROBATICS.isCompatible(capabilities)) {
                goalSelector.add(18, new AerialAcrobaticsGoal(mob));
            }
            if (GoalType.WATER_SPLASH.isCompatible(capabilities)) {
                goalSelector.add(18, new WaterSplashGoal(mob));
            }
            
            // === SOCIAL BEHAVIORS (Priority 15) ===
            if (GoalType.LEAN_AGAINST_OWNER.isCompatible(capabilities)) {
                goalSelector.add(15, new LeanAgainstOwnerGoal(mob));
            }
            if (GoalType.SHOW_OFF_TRICK.isCompatible(capabilities)) {
                goalSelector.add(15, new ShowOffTrickGoal(mob));
            }
            if (GoalType.PARALLEL_PLAY.isCompatible(capabilities)) {
                goalSelector.add(15, new ParallelPlayGoal(mob));
            }
            if (GoalType.GIFT_BRINGING.isCompatible(capabilities)) {
                goalSelector.add(15, new GiftBringingGoal(mob));
            }
            if (GoalType.PERCH_ON_SHOULDER.isCompatible(capabilities)) {
                goalSelector.add(15, new PerchOnShoulderGoal(mob));
            }
            if (GoalType.ORBIT_SWIM.isCompatible(capabilities)) {
                goalSelector.add(15, new OrbitSwimGoal(mob));
            }
            if (GoalType.EYE_CONTACT.isCompatible(capabilities)) {
                goalSelector.add(GoalType.EYE_CONTACT.getPriority(), new EyeContactGoal(mob));
            }
            if (GoalType.CROUCH_APPROACH_RESPONSE.isCompatible(capabilities)) {
                goalSelector.add(12, new CrouchApproachResponseGoal(mob));
            }
            
            // === SPECIAL BEHAVIORS (Priority 16) ===
            if (GoalType.HIDE_AND_SEEK.isCompatible(capabilities)) {
                goalSelector.add(16, new HideAndSeekGoal(mob));
            }
            if (GoalType.INVESTIGATE_BLOCK.isCompatible(capabilities)) {
                goalSelector.add(16, new InvestigateBlockGoal(mob));
            }
            if (GoalType.STARGAZING.isCompatible(capabilities)) {
                goalSelector.add(16, new StargazingGoal(mob));
            }
            
        } catch (Exception e) {
            System.err.println("Failed to initialize adaptive AI for " + mob.getType().getName().getString() + ": " + e.getMessage());
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
