package woflo.petsplus.ai.goals.wander;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;

import java.util.EnumSet;

/**
 * Water cruising for aquatic mobs - smooth swimming patterns.
 */
public class WaterCruiseGoal extends AdaptiveGoal {
    private Vec3d cruiseTarget;
    private int cruiseTicks = 0;
    
    public WaterCruiseGoal(MobEntity mob) {
        super(mob, GoalRegistry.WATER_CRUISE, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.isTouchingWater();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return mob.isTouchingWater();
    }
    
    @Override
    protected void onStartGoal() {
        cruiseTicks = 0;
        pickNewCruiseTarget();
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        cruiseTicks++;
        
        if (cruiseTarget == null || mob.getEntityPos().distanceTo(cruiseTarget) < 2.0 || cruiseTicks % 80 == 0) {
            pickNewCruiseTarget();
        }
        
        if (cruiseTarget != null) {
            mob.getNavigation().startMovingTo(cruiseTarget.x, cruiseTarget.y, cruiseTarget.z, 0.9);
        }
    }
    
    private void pickNewCruiseTarget() {
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        double distance = 5 + mob.getRandom().nextDouble() * 5;
        double depthVariation = mob.getRandom().nextDouble() * 3 - 1.5;
        
        cruiseTarget = new Vec3d(
            mob.getX() + Math.cos(angle) * distance,
            mob.getY() + depthVariation,
            mob.getZ() + Math.sin(angle) * distance
        );
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.18f)
            .add(woflo.petsplus.state.PetComponent.Emotion.YUGEN, 0.15f)
            .add(woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.12f)
            .add(woflo.petsplus.state.PetComponent.Emotion.CURIOUS, 0.10f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.6f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.45f) / 0.3f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.64f, 1.08f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.5f) / 0.3f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.7f, 1.12f);
        engagement *= momentumScale;

        float socialBlend = MathHelper.clamp((socialCharge - 0.4f) / 0.3f, -1.0f, 1.0f);
        float socialScale = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.72f, 1.08f);
        engagement *= socialScale;

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}

