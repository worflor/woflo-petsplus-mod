package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Subtle eye contact behavior - pet looks at owner's eyes when owner focuses on them.
 * Probabilistic and personality-driven - pets retain autonomy and don't always respond.
 * 
 * LOW PRIORITY - ambient behavior that happens when pet is idle/not busy.
 * HIGH PRIORITY can still override this (maintains free will).
 */
public class EyeContactGoal extends AdaptiveGoal {
    private int contactTicks = 0;
    private int maxContactDuration = 60; // 3 seconds base
    private boolean ownerWasLookingLastTick = false;
    private int ownerLookingTickCount = 0;
    private long lastCheckTime = 0;
    
    private static final int MIN_DURATION = 20;  // 1 second minimum
    private static final int MAX_DURATION = 100; // 5 seconds maximum
    private static final int RESPONSE_DELAY_MIN = 5;  // 0.25s min delay
    private static final int RESPONSE_DELAY_MAX = 40; // 2s max delay
    
    public EyeContactGoal(MobEntity mob) {
        super(mob, GoalType.EYE_CONTACT, EnumSet.of(Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        if (owner == null || !ctx.ownerNearby()) return false;
        if (ctx.distanceToOwner() > 10.0) return false;
        
        // Check if owner is looking
        boolean ownerLooking = isOwnerLookingAtPet(owner);
        
        if (ownerLooking) {
            ownerLookingTickCount++;
        } else {
            ownerLookingTickCount = 0;
            return false;
        }
        
        // PROBABILISTIC RESPONSE - not guaranteed!
        // Chance increases with:
        // - Bond strength (bonded pets more responsive)
        // - How long owner has been looking (attention-seeking)
        // - Mood (calm/bonded more likely to respond)
        
        float responseChance = calculateResponseChance(ctx);
        
        // Require minimum attention duration before checking
        int requiredDelay = RESPONSE_DELAY_MIN + (int)((1f - ctx.bondStrength()) * RESPONSE_DELAY_MAX);
        if (ownerLookingTickCount < requiredDelay) return false;
        
        // Roll for response (checked every few ticks to avoid spam)
        long now = mob.getEntityWorld().getTime();
        if (now - lastCheckTime < 10) return false; // Check every 0.5s
        lastCheckTime = now;
        
        return mob.getRandom().nextFloat() < responseChance;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        if (owner == null || !ctx.ownerNearby()) return false;
        if (contactTicks >= maxContactDuration) return false;
        
        // Brief persistence even if owner looks away (natural lag)
        if (!isOwnerLookingAtPet(owner)) {
            return contactTicks < 10; // Grace period
        }
        
        return true;
    }
    
    @Override
    protected void onStartGoal() {
        contactTicks = 0;
        ownerWasLookingLastTick = true;
        ownerLookingTickCount = 0;
        
        // Variable duration based on mood/personality
        PetContext ctx = getContext();
        maxContactDuration = MIN_DURATION;
        
        // Bonded pets hold eye contact longer
        if (ctx.bondStrength() > 0.7f) {
            maxContactDuration += 30;
        }
        
        // Calm/affectionate moods = longer gaze
        if (ctx.hasPetsPlusComponent()) {
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.BONDED, 0.4f)) {
                maxContactDuration += 20;
            }
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.CALM, 0.5f)) {
                maxContactDuration += 15;
            }
        }
        
        maxContactDuration = Math.min(maxContactDuration, MAX_DURATION);
    }
    
    @Override
    protected void onStopGoal() {
        contactTicks = 0;
    }
    
    @Override
    protected void onTickGoal() {
        contactTicks++;
        
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        if (owner == null) return;
        
        // Look directly at owner's eyes (head position)
        mob.getLookControl().lookAt(
            owner.getX(),
            owner.getEyeY(), // Eye level for authentic eye contact
            owner.getZ(),
            30.0f, // Max yaw change per tick
            30.0f  // Max pitch change per tick
        );
        
        // Subtle head tilt occasionally (canids/felids do this)
        if (contactTicks % 30 == 0 && mob.getRandom().nextFloat() < 0.3f) {
            float tilt = mob.getRandom().nextFloat() * 10 - 5;
            mob.setPitch(mob.getPitch() + tilt);
        }
        
        // Tail wag for canids during eye contact (sign of happiness)
        if (contactTicks % 8 == 0 && ctx.bondStrength() > 0.5f) {
            // Subtle body sway (tail wag simulation)
            mob.bodyYaw += mob.getRandom().nextFloat() * 4 - 2;
        }
    }
    
    /**
     * Calculate probability of pet responding to owner's gaze.
     * Respects pet autonomy - they don't always look back!
     */
    private float calculateResponseChance(PetContext ctx) {
        float baseChance = 0.15f; // 15% base chance per check (happens every 0.5s)
        
        // Bond strength heavily influences (0.5-1.0 pets are more responsive)
        baseChance += ctx.bondStrength() * 0.35f;
        
        // Mood modulation
        if (ctx.hasPetsPlusComponent()) {
            // Bonded mood = very responsive
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.BONDED, 0.4f)) {
                baseChance += 0.25f;
            }
            // Calm = attentive
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.CALM, 0.5f)) {
                baseChance += 0.15f;
            }
            // Playful = distracted
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.5f)) {
                baseChance -= 0.10f;
            }
            // Restless/stressed = avoids eye contact
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.RESTLESS, 0.3f)) {
                baseChance -= 0.20f;
            }
            // Focused mood (cats) = less responsive
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.FOCUSED, 0.4f)) {
                baseChance *= 0.5f;
            }
        }   
        
        // Young pets are more distractible
        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            baseChance *= 0.7f;
        }
        
        // Attention duration bonus (owner staring persistently)
        if (ownerLookingTickCount > 40) { // >2 seconds
            baseChance += 0.20f;
        }
        
        return Math.max(0.05f, Math.min(0.70f, baseChance)); // Clamp 5-70%
    }
    
    /**
     * Check if owner is looking at pet (uses same logic as UI focus detection).
     */
    private boolean isOwnerLookingAtPet(PlayerEntity owner) {
        double dx = mob.getX() - owner.getX();
        double dy = mob.getEyeY() - owner.getEyeY();
        double dz = mob.getZ() - owner.getZ();
        
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > 10.0) return false;
        
        // Get owner's look vector
        float yaw = owner.getYaw();
        float pitch = owner.getPitch();
        
        double lookX = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        double lookY = -Math.sin(Math.toRadians(pitch));
        double lookZ = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        
        // Normalize direction to pet
        dx /= distance;
        dy /= distance;
        dz /= distance;
        
        // Dot product to check angle
        double dot = lookX * dx + lookY * dy + lookZ * dz;
        
        // Within ~30 degree cone (cos(30°) ≈ 0.866)
        return dot > 0.85;
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Eye contact is a bonding moment - subtle but meaningful emotions
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.18f)      // Connection
            .add(woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.15f)     // Devotion
            .add(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.12f)   // Comfortable intimacy
            .add(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.10f)       // Perfect moment
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.015f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.8f;
        
        // Very engaging for bonded pets
        if (ctx.bondStrength() > 0.7f) {
            engagement = 1.0f;
        }
        
        // Peak engagement in first few seconds (novelty)
        if (contactTicks < 20) {
            engagement = Math.min(1.0f, engagement + 0.2f);
        }
        
        return engagement;
    }
}

