package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Social behavior - pet does a trick to show off to owner.
 */
public class ShowOffTrickGoal extends AdaptiveGoal {
    private int trickType; // 0=spin, 1=jump, 2=bow
    private int trickTicks = 0;
    private static final int TRICK_DURATION = 40;
    
    public ShowOffTrickGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.SHOW_OFF_TRICK), EnumSet.of(Control.MOVE, Control.JUMP, Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        if (ctx.owner() == null || !ctx.ownerNearby()) {
            return false;
        }
        
        // Only if owner is looking at pet
        PlayerEntity owner = ctx.owner();
        return isOwnerLookingAtPet(owner);
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return trickTicks < TRICK_DURATION;
    }
    
    @Override
    protected void onStartGoal() {
        trickType = mob.getRandom().nextInt(3);
        trickTicks = 0;
    }
    
    @Override
    protected void onStopGoal() {
        mob.setSneaking(false);
    }
    
    @Override
    protected void onTickGoal() {
        trickTicks++;
        
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        if (owner != null) {
            mob.getLookControl().lookAt(owner, 30, 30);
        }
        
        switch (trickType) {
            case 0: // Spin
                mob.setYaw(mob.getYaw() + 18); // Full rotation in 20 ticks
                break;
                
            case 1: // Jump
                if (trickTicks % 20 == 0 && mob.isOnGround()) {
                    mob.setVelocity(0, 0.4, 0);
                    mob.velocityModified = true;
                }
                break;
                
            case 2: // Bow
                if (trickTicks < 20) {
                    mob.setSneaking(true);
                    mob.setPitch(30);
                } else {
                    mob.setSneaking(false);
                    mob.setPitch(0);
                }
                break;
        }
    }
    
    private boolean isOwnerLookingAtPet(PlayerEntity owner) {
        // Simple check - if owner is facing pet's general direction
        double dx = mob.getX() - owner.getX();
        double dz = mob.getZ() - owner.getZ();
        double angle = Math.atan2(dz, dx) * 180 / Math.PI - 90;
        double yawDiff = Math.abs(((angle - owner.getYaw() + 180) % 360) - 180);
        
        return yawDiff < 45; // Within 45 degrees
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.PRIDE, 0.32f)
            .add(woflo.petsplus.state.PetComponent.Emotion.HANYAUKU, 0.25f)
            .add(woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.20f)
            .add(woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.18f)
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.PRIDE, 0.022f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);
        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float mentalFocus = MathHelper.clamp(ctx.mentalFocus(), 0.0f, 1.0f);

        float socialBlend = MathHelper.clamp((socialCharge - 0.35f) / 0.3f, -1.0f, 1.0f);
        float engagement = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.42f, 0.9f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.5f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.7f, 1.12f);
        engagement *= staminaScale;

        float focusBlend = MathHelper.clamp((mentalFocus - 0.45f) / 0.35f, -1.0f, 1.0f);
        float focusScale = MathHelper.lerp((focusBlend + 1.0f) * 0.5f, 0.78f, 1.1f);
        engagement *= focusScale;

        if (ctx.owner() != null && isOwnerLookingAtPet(ctx.owner())) {
            engagement = Math.max(engagement, 1.0f);
        }

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}
