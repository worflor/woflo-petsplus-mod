package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Social behavior - pet leans against owner's leg for affection.
 */
public class LeanAgainstOwnerGoal extends AdaptiveGoal {
    private int leanTicks = 0;
    private static final int LEAN_DURATION = 60;
    
    public LeanAgainstOwnerGoal(MobEntity mob) {
        super(mob, GoalType.LEAN_AGAINST_OWNER, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        return ctx.owner() != null && ctx.distanceToOwner() < 3.0f;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        PetContext ctx = getContext();
        return ctx.owner() != null && ctx.distanceToOwner() < 4.0f && leanTicks < LEAN_DURATION;
    }
    
    @Override
    protected void onStartGoal() {
        leanTicks = 0;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        leanTicks++;
        
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        if (owner == null) {
            return;
        }
        
        // Move close to owner
        if (mob.distanceTo(owner) > 1.0) {
            mob.getNavigation().startMovingTo(owner, 0.8);
        } else {
            mob.getNavigation().stop();
            
            // Face same direction as owner
            mob.setYaw(owner.getYaw());
            mob.headYaw = owner.headYaw;
            
            // Slight pushing motion (lean)
            double dx = owner.getX() - mob.getX();
            double dz = owner.getZ() - mob.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 0.5 && distance < 1.5) {
                mob.setVelocity(dx / distance * 0.01, 0, dz / distance * 0.01);
            }
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.20f)
            .add(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.15f)
            .add(woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.12f)
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.018f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);
        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);

        float socialBlend = MathHelper.clamp((socialCharge - 0.35f) / 0.3f, -1.0f, 1.0f);
        float engagement = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.55f, 0.92f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.45f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.88f, 1.06f);
        engagement *= staminaScale;

        if (ctx.bondStrength() > 0.7f) {
            engagement = Math.max(engagement, 0.96f);
        }

        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.BONDED, 0.4f)) {
            engagement = Math.max(engagement, 1.0f);
        }

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}
