package woflo.petsplus.ai.goals.wander;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Casual meandering wander - slow, curved paths with pauses.
 */
public class CasualWanderGoal extends AdaptiveGoal {
    private BlockPos target;
    private int pauseTicks = 0;
    private boolean isPaused = false;
    
    public CasualWanderGoal(MobEntity mob) {
        super(mob, GoalType.CASUAL_WANDER, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.getNavigation().isIdle() && mob.isOnGround();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return true; // Let duration handle it
    }
    
    @Override
    protected void onStartGoal() {
        pickNewTarget();
        isPaused = false;
        pauseTicks = 0;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        if (isPaused) {
            pauseTicks++;
            if (pauseTicks > 40) { // 2 second pause
                isPaused = false;
                pauseTicks = 0;
                pickNewTarget();
            }
        } else {
            if (target == null || mob.getBlockPos().isWithinDistance(target, 2.0)) {
                // Reached target, maybe pause
                if (mob.getRandom().nextFloat() < 0.4f) {
                    isPaused = true;
                    mob.getNavigation().stop();
                } else {
                    pickNewTarget();
                }
            } else {
                mob.getNavigation().startMovingTo(target.getX(), target.getY(), target.getZ(), 0.7); // Slow pace
            }
        }
    }
    
    private void pickNewTarget() {
        // Short distance, gentle wandering
        int dx = mob.getRandom().nextInt(7) - 3;
        int dz = mob.getRandom().nextInt(7) - 3;
        target = mob.getBlockPos().add(dx, 0, dz);
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        PetContext ctx = getContext();
        
        // Base exploration emotions - gentle contentment and ambient appreciation
        var builder = new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.12f)    // Pleasant routine
            .add(woflo.petsplus.state.PetComponent.Emotion.YUGEN, 0.08f);     // Subtle wonder
        
        // Context-aware modulation based on distance to owner
        if (ctx.distanceToOwner() > 20.0) {
            // Far from owner: territorial species feel longing
            builder.add(woflo.petsplus.state.PetComponent.Emotion.HIRAETH, 0.10f);
        } else if (ctx.ownerNearby()) {
            // Near owner: comfortable proximity
            builder.add(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.10f);
            builder.add(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.05f);
        }
        
        return builder.build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.5f;
        
        // More engaging if calm
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.CALM, 0.4f)) {
            engagement += 0.2f;
        }
        
        return engagement;
    }
}
