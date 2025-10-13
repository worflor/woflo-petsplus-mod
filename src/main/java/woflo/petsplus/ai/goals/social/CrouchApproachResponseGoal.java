package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.behavior.variants.*;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.EmotionFeedback;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;
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
 *     <li>Behavioural energy stack (momentum/physical stamina/social charge)</li>
 *     <li>Species type (wolves 1.2x, cats 0.8x, horses 0.5x, etc.)</li>
 *     <li>Age (young pets 1.25x more responsive)</li>
 *     <li>Distance (close approach 1.3x, distant 0.7x)</li>
 *   </ul>
 *   <li>If roll succeeds, select and execute personality-driven behavior variant</li>
 *   <li>Emit bonding emotions (CHEERFUL, UBUNTU, LOYALTY) on completion</li>
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
    private static final String CROUCH_APPROACH_SIGNAL = "crouch_approach_signal_tick";
    private long lastSignalTick = -1;
    private static final long SIGNAL_TIMEOUT = 10; // Signal expires after 10 ticks

    private static final float PLAYFUL_MOOD_THRESHOLD = 0.3f;
    private static final float BONDED_MOOD_THRESHOLD = 0.4f;
    private static final float CALM_MOOD_THRESHOLD = 0.5f;
    private static final float HAPPY_MOOD_THRESHOLD = 0.4f;
    private static final float CURIOUS_MOOD_THRESHOLD = 0.4f;
    private static final float FOCUSED_MOOD_THRESHOLD = 0.4f;
    private static final float AFRAID_MOOD_THRESHOLD = 0.3f;
    private static final float ANGRY_MOOD_THRESHOLD = 0.3f;
    private static final float RESTLESS_MOOD_THRESHOLD = 0.4f;

    private static final float PLAYFUL_CHANCE = 0.70f;
    private static final float BONDED_CHANCE = 0.60f;
    private static final float CALM_CHANCE = 0.40f;
    private static final float HAPPY_CHANCE = 0.55f;
    private static final float CURIOUS_CHANCE = 0.50f;

    private static final float FOCUSED_PENALTY = 0.80f;
    private static final float AFRAID_PENALTY = 0.90f;
    private static final float ANGRY_PENALTY = 0.70f;
    private static final float RESTLESS_PENALTY = 0.40f;

    private static final float BOND_STRENGTH_MULTIPLIER = 0.5f;
    private static final float YOUNG_PET_MULTIPLIER = 1.25f;
    private static final float MATURE_PET_MULTIPLIER = 0.9f;

    private static final float CLOSE_APPROACH_MULTIPLIER = 1.3f;
    private static final float DISTANT_APPROACH_MULTIPLIER = 0.7f;
    
    public CrouchApproachResponseGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.CROUCH_APPROACH_RESPONSE), EnumSet.of(Control.MOVE, Control.LOOK));
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
        if (ctx.hasMoodInBlend(PetComponent.Mood.PLAYFUL, PLAYFUL_MOOD_THRESHOLD)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.PLAYFUL, 0.0f);
            baseChance += PLAYFUL_CHANCE * strength;
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.BONDED, BONDED_MOOD_THRESHOLD)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.BONDED, 0.0f);
            baseChance += BONDED_CHANCE * strength;
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.CALM, CALM_MOOD_THRESHOLD)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.CALM, 0.0f);
            baseChance += CALM_CHANCE * strength;
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.HAPPY, HAPPY_MOOD_THRESHOLD)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.HAPPY, 0.0f);
            baseChance += HAPPY_CHANCE * strength;
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.CURIOUS, CURIOUS_MOOD_THRESHOLD)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.CURIOUS, 0.0f);
            baseChance += CURIOUS_CHANCE * strength;
        }
        
        // significantly reduce response
        if (ctx.hasMoodInBlend(PetComponent.Mood.FOCUSED, FOCUSED_MOOD_THRESHOLD)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.FOCUSED, 0.0f);
            baseChance *= (1.0f - (FOCUSED_PENALTY * strength)); // Reduce by up to 80%
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.AFRAID, AFRAID_MOOD_THRESHOLD)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.AFRAID, 0.0f);
            baseChance *= (1.0f - (AFRAID_PENALTY * strength)); // Very reduced when scared
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.ANGRY, ANGRY_MOOD_THRESHOLD)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.ANGRY, 0.0f);
            baseChance *= (1.0f - (ANGRY_PENALTY * strength)); // Reduced when angry
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.RESTLESS, RESTLESS_MOOD_THRESHOLD)) {
            float strength = ctx.moodBlend().getOrDefault(PetComponent.Mood.RESTLESS, 0.0f);
            baseChance *= (1.0f - (RESTLESS_PENALTY * strength)); // Somewhat distracted
        }
        
        // Bond strength multiplier (×1.5 at max bond)
        baseChance *= (1.0f + ctx.bondStrength() * BOND_STRENGTH_MULTIPLIER);
        
        // Age consideration - young pets are more playful and responsive
        PetContext.AgeCategory age = ctx.getAgeCategory();
        if (age == PetContext.AgeCategory.YOUNG) {
            baseChance *= YOUNG_PET_MULTIPLIER; // Young pets 25% more responsive
        } else if (age == PetContext.AgeCategory.MATURE) {
            baseChance *= MATURE_PET_MULTIPLIER; // Mature pets slightly less impulsive
        }
        
        float behavioralMomentum = MathHelper.clamp(ctx.behavioralMomentum(), 0f, 1f);
        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0f, 1f);
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0f, 1f);

        float energyBoost = MathHelper.clamp((Math.max(behavioralMomentum, physicalStamina) - 0.55f) / 0.35f, 0f, 1f);
        float fatiguePenalty = MathHelper.clamp((0.5f - Math.min(behavioralMomentum, physicalStamina)) / 0.3f, 0f, 1f);
        if (energyBoost > 0f) {
            baseChance *= 1.0f + (energyBoost * 0.35f);
        }
        if (fatiguePenalty > 0f) {
            baseChance *= 1.0f - (fatiguePenalty * 0.45f);
        }

        float socialDelta = socialCharge - 0.45f;
        if (socialDelta > 0f) {
            float socialBoost = MathHelper.clamp(socialDelta / 0.35f, 0f, 1f);
            baseChance *= 1.0f + (socialBoost * 0.4f);
        } else if (socialDelta < 0f) {
            float socialDamp = MathHelper.clamp(-socialDelta / 0.3f, 0f, 1f);
            baseChance *= 1.0f - (socialDamp * 0.35f);
        }
        
        // Distance consideration - closer approach = higher response
        // Sweet spot is 3-6 blocks (natural interaction distance)
        if (ctx.distanceToOwner() <= 4.0f) {
            baseChance *= CLOSE_APPROACH_MULTIPLIER; // Bonus for very close approach
        } else if (ctx.distanceToOwner() > 10.0f) {
            baseChance *= DISTANT_APPROACH_MULTIPLIER; // Penalty for distant approach
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
        
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0f, 1f);
        float stamina = MathHelper.clamp(ctx.physicalStamina(), 0f, 1f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0f, 1f);

        if (ctx.bondStrength() > 0.7f) {
            float excitement = Math.max(momentum, stamina);
            if (excitement >= 0.65f || socialCharge >= 0.6f) {
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
        
        Long signalTick = pc.getStateData(CROUCH_APPROACH_SIGNAL, Long.class, -1L);
        long worldTime = mob.getEntityWorld().getTime();
        
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
            .add(PetComponent.Emotion.CHEERFUL, 0.25f)           // Playful joy
            .add(PetComponent.Emotion.UBUNTU, 0.18f)         // Connection
            .add(PetComponent.Emotion.LOYALTY, 0.15f)        // Devotion
            .add(PetComponent.Emotion.PLAYFULNESS, 0.12f)    // Engagement
            .add(PetComponent.Emotion.HANYAUKU, 0.10f)       // Joyful enthusiasm
            .withContagion(PetComponent.Emotion.CHEERFUL, 0.02f) // Spread joy to nearby pets
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

