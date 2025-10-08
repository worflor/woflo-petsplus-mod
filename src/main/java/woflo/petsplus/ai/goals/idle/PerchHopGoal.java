package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Flying-specific idle quirk - bird hops between nearby perch spots.
 */
public class PerchHopGoal extends AdaptiveGoal {
    private BlockPos targetPerch;
    private int hops = 0;
    private static final int MAX_HOPS = 3;
    
    public PerchHopGoal(MobEntity mob) {
        super(mob, GoalType.PERCH_HOP, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.isOnGround() || findNearbyPerch() != null;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return hops < MAX_HOPS;
    }
    
    @Override
    protected void onStartGoal() {
        hops = 0;
        targetPerch = findNearbyPerch();
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        if (targetPerch == null || mob.getBlockPos().isWithinDistance(targetPerch, 1.0)) {
            hops++;
            targetPerch = findNearbyPerch();
        }
        
        if (targetPerch != null) {
            mob.getNavigation().startMovingTo(targetPerch.getX(), targetPerch.getY(), targetPerch.getZ(), 1.0);
        }
    }
    
    private BlockPos findNearbyPerch() {
        // Find a nearby block at similar height
        for (int i = 0; i < 5; i++) {
            int dx = mob.getRandom().nextInt(5) - 2;
            int dz = mob.getRandom().nextInt(5) - 2;
            int dy = mob.getRandom().nextInt(3) - 1;
            
            BlockPos candidate = mob.getBlockPos().add(dx, dy, dz);
            if (!mob.getEntityWorld().getBlockState(candidate).isAir() &&
                mob.getEntityWorld().getBlockState(candidate.up()).isAir()) {
                return candidate.up();
            }
        }
        return null;
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.VIGILANT, 0.08f,
            woflo.petsplus.state.PetComponent.Emotion.CURIOUS, 0.06f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        return 0.5f;
    }
}

