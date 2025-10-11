package woflo.petsplus.ai.goals.play;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Aerial acrobatics - performs tricks while flying.
 * Creates playful, showy flight behavior.
 */
public class AerialAcrobaticsGoal extends AdaptiveGoal {
    private int acrobaticTicks = 0;
    private int trickType = 0; // 0 = loop, 1 = barrel roll, 2 = dive bomb
    private static final int MAX_ACROBATIC_TICKS = 80; // 4 seconds
    
    public AerialAcrobaticsGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.AERIAL_ACROBATICS), EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        return !mob.isOnGround() && 
               mob.getY() > mob.getEntityWorld().getSeaLevel() + 5;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return acrobaticTicks < MAX_ACROBATIC_TICKS && !mob.isOnGround();
    }
    
    @Override
    protected void onStartGoal() {
        acrobaticTicks = 0;
        trickType = mob.getRandom().nextInt(3);
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        acrobaticTicks++;
        
        switch (trickType) {
            case 0: // Loop
                performLoop();
                break;
            case 1: // Barrel roll
                performBarrelRoll();
                break;
            case 2: // Dive bomb
                performDiveBomb();
                break;
        }
    }
    
    /**
     * Performs a vertical loop.
     */
    private void performLoop() {
        double progress = acrobaticTicks / (double) MAX_ACROBATIC_TICKS;
        double angle = progress * Math.PI * 2; // Full circle
        
        // Circular motion
        Vec3d velocity = new Vec3d(
            Math.sin(angle) * 0.1,
            Math.cos(angle) * 0.2,
            mob.getVelocity().z
        );
        
        mob.setVelocity(velocity);
        mob.setPitch((float) (Math.cos(angle) * 60)); // Pitch changes through loop
    }
    
    /**
     * Performs a barrel roll (spinning).
     */
    private void performBarrelRoll() {
        double progress = acrobaticTicks / (double) MAX_ACROBATIC_TICKS;
        double spin = progress * Math.PI * 4; // Two full rotations
        
        // Maintain altitude, spin body
        mob.setVelocity(
            mob.getVelocity().x,
            0.05, // Slight upward drift
            mob.getVelocity().z
        );
        
        mob.bodyYaw += 9; // Fast spin
        mob.setPitch((float) (Math.sin(spin) * 30)); // Wobble pitch
    }
    
    /**
     * Performs a dive bomb and recovery.
     */
    private void performDiveBomb() {
        if (acrobaticTicks < MAX_ACROBATIC_TICKS / 2) {
            // Dive phase
            mob.setVelocity(
                mob.getVelocity().x,
                -0.3, // Fast descent
                mob.getVelocity().z
            );
            mob.setPitch(70); // Steep dive
        } else {
            // Recovery phase
            mob.setVelocity(
                mob.getVelocity().x,
                0.25, // Pull up
                mob.getVelocity().z
            );
            mob.setPitch(-20); // Level out
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.triple(
            woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.15f,
            woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.12f,
            woflo.petsplus.state.PetComponent.Emotion.PRIDE, 0.10f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.9f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.55f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.6f, 1.2f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.55f) / 0.3f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.7f, 1.16f);
        engagement *= momentumScale;

        // Very engaging for playful pets
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.5f)) {
            engagement = 1.0f;
        }
        
        // Engaging for young pets
        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            engagement = 1.0f;
        }
        
        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}

