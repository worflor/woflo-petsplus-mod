package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.ai.behavior.MomentumState;
import woflo.petsplus.ai.behavior.variants.*;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.EmotionFeedback;
import woflo.petsplus.ai.goals.GoalType;
import woflo.petsplus.ai.traits.SpeciesTraits;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * Response goal triggered by owner crouching + approaching + eye contact.
 * 
 * <p><b>How it works:</b>
 * <ol>
 *   <li>Owner crouches while looking at pet</li>
 *   <li>Owner walks toward pet (detected via movement tracking)</li>
 *   <li>Signal fires and is marked in PetComponent</li>
 *   <li>Goal evaluates response probability based on:</li>
 *   <ul>
 *     <li>Mood (Playful 70%, Bonded 60%, Calm 40%, Independent -80%)</li>
 *     <li>Bond strength (×1.5 multiplier at max)</li>
 *     <li>Energy level (high = 1.3x, low = 0.7x)</li>
 *     <li>Species type (wolves 1.2x, cats 0.8x, horses 0.5x, etc.)</li>
 *     <li>Age (young pets 1.25x more responsive)</li>
 *     <li>Distance (close approach 1.3x, distant 0.7x)</li>
 *   </ul>
 *   <li>If roll succeeds, select and execute personality-driven behavior variant</li>
 *   <li>Emit bonding emotions (GLEE, UBUNTU, LOYALTY) on completion</li>
 * </ol>
 * 
 * <p><b>Behavior Variants:</b>
 * <ul>
 *   <li>PlayfulBounce: Jumps excitedly toward owner</li>
 *   <li>AffectionateNuzzle: Slow gentle approach with head tilt</li>
 *   <li>ExcitedCircle: Runs circles around owner</li>
 *   <li>BasicApproach: Simple walk with tail wag</li>
 * </ul>
 * 
 * <p><b>Safety Features:</b>
 * <ul>
 *   <li>Won't trigger during combat or danger</li>
 *   <li>Won't trigger when in hazardous environments (fire, lava)</li>
 *   <li>Stops if owner moves away or danger appears</li>
 *   <li>Signal timeout prevents stale triggers</li>
 * </ul>
 * 
 * <p>Designed for immersive, lively pet interactions that feel natural and rewarding.
 */
public class CrouchApproachResponseGoal extends AdaptiveGoal {
    
    private BehaviorVariant currentVariant = null;
    private int variantTicks = 0;
    private long lastSignalTick = -1;
    private static final long SIGNAL_TIMEOUT = 10; // Signal expires after 10 ticks
    
    public CrouchApproachResponseGoal(MobEntity mob) {
        super(mob, GoalType.CROUCH_APPROACH_RESPONSE, EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        
        // Check prerequisites
        if (ctx.owner() == null || !ctx.ownerNearby()) {
            return false;
        }
        
        // Safety: Don't respond during combat or danger
        if (mob.getAttacker() != null || mob.getAttacking() != null) {
            return false;
        }
        
        // Safety: Don't respond when in water (unless aquatic)
        if (mob.isTouchingWater() && !woflo.petsplus.ai.capability.MobCapabilities.prefersWater(mob)) {
            return false;
        }
        
        // Safety: Don't respond when on fire or in lava
        if (mob.isOnFire() || mob.isInLava()) {
            return false;
        }
        
        // Safety: Don't respond when riding or being ridden
        if (mob.hasVehicle() || mob.hasPassengers()) {
            return false;
        }
        
        // Check for recent signal
        if (!hasCrouchApproachSignal()) {
            return false;
        }
        
        // Calculate response chance
        float chance = calculateResponseChance(ctx);
        
        // Roll for response
        return mob.getRandom().nextFloat() < chance;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        if (currentVariant == null) {
            return false;
        }
        
        // Stop if owner is no longer nearby
        PetContext ctx = getContext();
        if (ctx.owner() == null || !ctx.ownerNearby()) {
            return false;
        }
        
        // Stop if danger appears
        if (mob.getAttacker() != null || mob.getAttacking() != null) {
            return false;
        }
        
        return currentVariant.shouldContinue(mob, variantTicks);
    }
    
    @Override
    protected void onStartGoal() {
        PetContext ctx = getContext();
        currentVariant = selectVariant(ctx);
        variantTicks = 0;
        
        if (currentVariant != null) {
            currentVariant.initialize(mob, ctx);
            
            // Track gentle approach for relationship system
            if (ctx.owner() != null) {
                woflo.petsplus.events.RelationshipEventHandler.onGentleApproach(mob, ctx.owner());
            }
            
            // Play happy sound on response (immersion)
            playResponseSound();
        }
    }
    
    @Override
    protected void onStopGoal() {
        if (currentVariant != null) {
            currentVariant.stop(mob);
            currentVariant = null;
        }
        variantTicks = 0;
    }
    
    @Override
    protected void onTickGoal() {
        if (currentVariant != null) {
            currentVariant.tick(mob, variantTicks);
            variantTicks++;
        }
    }
    
    /**
     * Calculate the probability of responding to crouch-approach signal.
     * Considers mood, bond, energy, species, age, and negative moods.
     */
    private float calculateResponseChance(PetContext ctx) {
        float baseChance = 0.0f;
        
        // Mood modifiers (primary factor) - weighted by blend strength
        if (ctx.hasMoodInBlend(PetComponent.Mood.PLAYFUL, 0.3f)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.PLAYFUL, 0.0f);
            baseChance += 0.70f * strength;
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.BONDED, 0.4f)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.BONDED, 0.0f);
            baseChance += 0.60f * strength;
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.CALM, 0.5f)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.CALM, 0.0f);
            baseChance += 0.40f * strength;
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.HAPPY, 0.4f)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.HAPPY, 0.0f);
            baseChance += 0.55f * strength;
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.CURIOUS, 0.4f)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.CURIOUS, 0.0f);
            baseChance += 0.50f * strength;
        }
        
        // significantly reduce response
        if (ctx.hasMoodInBlend(PetComponent.Mood.FOCUSED, 0.4f)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.FOCUSED, 0.0f);
            baseChance *= (1.0f - (0.80f * strength)); // Reduce by up to 80%
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.AFRAID, 0.3f)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.AFRAID, 0.0f);
            baseChance *= (1.0f - (0.90f * strength)); // Very reduced when scared
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.ANGRY, 0.3f)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.ANGRY, 0.0f);
            baseChance *= (1.0f - (0.70f * strength)); // Reduced when angry
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.RESTLESS, 0.4f)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.RESTLESS, 0.0f);
            baseChance *= (1.0f - (0.40f * strength)); // Somewhat distracted
        }
        
        // Bond strength multiplier (×1.5 at max bond)
        baseChance *= (1.0f + ctx.bondStrength() * 0.5f);
        
        // Age consideration - young pets are more playful and responsive
        PetContext.AgeCategory age = ctx.getAgeCategory();
        if (age == PetContext.AgeCategory.YOUNG) {
            baseChance *= 1.25f; // Young pets 25% more responsive
        } else if (age == PetContext.AgeCategory.MATURE) {
            baseChance *= 0.9f; // Mature pets slightly less impulsive
        }
        
        // Energy level multiplier
        MomentumState momentum = MomentumState.capture(mob);
        if (momentum.level() == MomentumState.EnergyLevel.ENERGETIC || 
            momentum.level() == MomentumState.EnergyLevel.HYPERACTIVE) {
            baseChance *= 1.3f;
        } else if (momentum.level() == MomentumState.EnergyLevel.EXHAUSTED ||
                   momentum.level() == MomentumState.EnergyLevel.TIRED) {
            baseChance *= 0.7f;
        }
        
        // Distance consideration - closer approach = higher response
        // Sweet spot is 3-6 blocks (natural interaction distance)
        if (ctx.distanceToOwner() <= 4.0f) {
            baseChance *= 1.3f; // Bonus for very close approach
        } else if (ctx.distanceToOwner() > 10.0f) {
            baseChance *= 0.7f; // Penalty for distant approach
        }
        
        // Species modifier
        float speciesModifier = SpeciesTraits.getApproachResponseModifier(mob);
        baseChance *= speciesModifier;
        
        // Apply species mood biases
        for (PetComponent.Mood mood : PetComponent.Mood.values()) {
            if (ctx.hasMoodInBlend(mood, 0.3f)) {
                float bias = SpeciesTraits.getMoodBias(mob, mood);
                if (bias != 1.0f) {
                    baseChance *= bias;
                }
            }
        }
        
        // Clamp to reasonable range (5%-95%)
        return Math.min(0.95f, Math.max(0.05f, baseChance));
    }
    
    /**
     * Select behavior variant based on personality and species.
     * Uses priority order with fallbacks for robustness.
     */
    private BehaviorVariant selectVariant(PetContext ctx) {
        BehaviorVariant selected = null;
        
        // Personality-driven selection with priority order
        if (ctx.hasMoodInBlend(PetComponent.Mood.PLAYFUL, 0.6f)) {
            selected = tryInstantiatePreferredOrDefault(PlayfulBounceVariant.class, 0);
            if (selected != null) return selected;
        }
        
        if (ctx.hasMoodInBlend(PetComponent.Mood.BONDED, 0.5f)) {
            selected = tryInstantiatePreferredOrDefault(AffectionateNuzzleVariant.class, 1);
            if (selected != null) return selected;
        }
        
        if (ctx.bondStrength() > 0.7f) {
            MomentumState momentum = MomentumState.capture(mob);
            if (momentum.level() == MomentumState.EnergyLevel.ENERGETIC ||
                momentum.level() == MomentumState.EnergyLevel.HYPERACTIVE) {
                selected = tryInstantiatePreferredOrDefault(ExcitedCircleVariant.class, 0);
                if (selected != null) return selected;
            }
        }
        
        // Fallback: basic approach (always succeeds)
        selected = tryInstantiatePreferredOrDefault(BasicApproachVariant.class, 0);
        
        // Ultimate fallback if all else fails
        return selected != null ? selected : new BasicApproachVariant();
    }
    
    /**
     * Try to instantiate species-preferred variant, or fall back to default.
     */
    private BehaviorVariant tryInstantiatePreferredOrDefault(
        Class<? extends BehaviorVariant> defaultVariant,
        int preferenceIndex
    ) {
        // Try species preference first
        Class<? extends BehaviorVariant> preferred = SpeciesTraits.getPreferredVariant(mob, preferenceIndex);
        if (preferred != null) {
            try {
                return preferred.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                // Fall through to default
            }
        }
        
        // Fall back to default
        try {
            return defaultVariant.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // Ultimate fallback
            return new BasicApproachVariant();
        }
    }
    
    /**
     * Check if a recent crouch-approach signal exists.
     */
    private boolean hasCrouchApproachSignal() {
        PetComponent pc = PetComponent.get(mob);
        if (pc == null) {
            return false;
        }
        
        Long signalTick = pc.getStateData("crouch_approach_signal_tick", Long.class, -1L);
        long worldTime = mob.getWorld().getTime();
        
        if (signalTick < 0 || worldTime - signalTick > SIGNAL_TIMEOUT) {
            return false;
        }
        
        // Consume signal (one-time use)
        if (signalTick != lastSignalTick) {
            lastSignalTick = signalTick;
            return true;
        }
        
        return false;
    }
    
    /**
     * Play species-appropriate happy sound when responding.
     */
    private void playResponseSound() {
        if (mob.isSilent()) {
            return;
        }
        
        // 60% chance to vocalize on response
        if (mob.getRandom().nextFloat() < 0.6f) {
            try {
                mob.playAmbientSound();
            } catch (Exception e) {
                // Silently fail if ambient sound not available
            }
        }
    }
    
    @Override
    protected EmotionFeedback defineEmotionFeedback() {
        PetContext ctx = getContext();
        EmotionFeedback.Builder builder = new EmotionFeedback.Builder()
            .add(PetComponent.Emotion.GLEE, 0.25f)           // Playful joy
            .add(PetComponent.Emotion.UBUNTU, 0.18f)         // Connection
            .add(PetComponent.Emotion.LOYALTY, 0.15f)        // Devotion
            .add(PetComponent.Emotion.PLAYFULNESS, 0.12f)    // Engagement
            .add(PetComponent.Emotion.HANYAUKU, 0.10f)       // Joyful enthusiasm
            .withContagion(PetComponent.Emotion.GLEE, 0.02f) // Spread joy to nearby pets
            .withContagion(PetComponent.Emotion.UBUNTU, 0.015f); // Spread connection
        
        // Extra bond emotions for highly bonded pets
        if (ctx.bondStrength() > 0.7f) {
            builder.add(PetComponent.Emotion.SOBREMESA, 0.12f); // Comfortable togetherness
        }
        
        return builder.build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        
        // Base engagement
        float engagement = 0.85f;
        
        // Higher engagement if owner is still nearby and watching
        if (ctx.owner() != null && ctx.ownerNearby()) {
            engagement = 0.95f;
        }
        
        // Personality modulation
        if (ctx.hasMoodInBlend(PetComponent.Mood.PLAYFUL, 0.5f)) {
            engagement = 1.0f;
        }
        
        return engagement;
    }
}
