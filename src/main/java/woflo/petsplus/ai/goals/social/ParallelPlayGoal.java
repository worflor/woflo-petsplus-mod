package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Parallel play - stays near owner doing independent activities.
 * Creates companionable, comfortable togetherness.
 */
public class ParallelPlayGoal extends AdaptiveGoal {
    private int parallelTicks = 0;
    private static final int MAX_PARALLEL_TICKS = 400; // 20 seconds
    private static final double COMFORTABLE_DISTANCE = 5.0;
    private int lastPlayTick = 0;
    private static final int PLAY_TRACKING_INTERVAL = 100; // Track every 5 seconds
    
    public ParallelPlayGoal(MobEntity mob) {
        super(mob, GoalType.PARALLEL_PLAY, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        return owner != null && 
               ctx.ownerNearby() &&
               ctx.distanceToOwner() < 12.0;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        PetContext ctx = getContext();
        return parallelTicks < MAX_PARALLEL_TICKS && 
               ctx.ownerNearby() &&
               ctx.distanceToOwner() < 15.0;
    }
    
    @Override
    protected void onStartGoal() {
        parallelTicks = 0;
        lastPlayTick = 0;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        parallelTicks++;
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        if (owner == null) return;
        
        double distance = ctx.distanceToOwner();
        
        if (distance > COMFORTABLE_DISTANCE + 3.0) {
            // Too far - move closer casually
            mob.getNavigation().startMovingTo(owner, 0.7);
        } else if (distance < COMFORTABLE_DISTANCE - 2.0) {
            // Too close - give owner space
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double offsetX = Math.cos(angle) * COMFORTABLE_DISTANCE;
            double offsetZ = Math.sin(angle) * COMFORTABLE_DISTANCE;
            
            mob.getNavigation().startMovingTo(
                owner.getX() + offsetX,
                owner.getY(),
                owner.getZ() + offsetZ,
                0.6
            );
        } else {
            // Perfect distance - do own thing
            mob.getNavigation().stop();
            
            // Track play interaction periodically for relationship system
            if (parallelTicks - lastPlayTick >= PLAY_TRACKING_INTERVAL) {
                woflo.petsplus.events.RelationshipEventHandler.onPlayInteraction(mob, owner);
                lastPlayTick = parallelTicks;
            }
            
            // Occasionally glance at owner
            if (parallelTicks % 60 == 0) {
                mob.getLookControl().lookAt(owner);
            } else {
                // Look around casually
                mob.setPitch((float) (Math.sin(parallelTicks * 0.05) * 5));
                if (parallelTicks % 40 == 0) {
                    mob.bodyYaw += mob.getRandom().nextFloat() * 30 - 15;
                }
            }
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.22f)
            .add(woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.20f)
            .add(woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.18f)
            .add(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.15f)
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.025f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);
        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);

        float socialBlend = MathHelper.clamp((socialCharge - 0.35f) / 0.3f, -1.0f, 1.0f);
        float engagement = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.5f, 0.88f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.4f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.82f, 1.08f);
        engagement *= staminaScale;

        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.CALM, 0.5f)) {
            engagement += 0.12f;
        }

        engagement += ctx.bondStrength() * 0.15f;

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}
