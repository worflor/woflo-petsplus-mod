package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Aquatic-specific idle quirk - mob plays with bubbles.
 */
public class BubblePlayGoal extends AdaptiveGoal {
    private int playTicks = 0;
    private static final int PLAY_DURATION = 50;
    
    public BubblePlayGoal(MobEntity mob) {
        super(mob, GoalType.BUBBLE_PLAY, EnumSet.noneOf(Control.class));
    }
    
    @Override
    protected boolean canStartGoal() {
        return mob.isTouchingWater() && mob.getNavigation().isIdle();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return mob.isTouchingWater() && playTicks < PLAY_DURATION;
    }
    
    @Override
    protected void onStartGoal() {
        playTicks = 0;
    }
    
    @Override
    protected void onStopGoal() {
        // Nothing to clean up
    }
    
    @Override
    protected void onTickGoal() {
        playTicks++;
        
        // Spin and create bubbles
        mob.setYaw(mob.getYaw() + 10);
        
        // Spawn bubble particles
        if (playTicks % 5 == 0 && mob.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                ParticleTypes.BUBBLE,
                mob.getX(), mob.getY() + 0.5, mob.getZ(),
                3,
                0.3, 0.3, 0.3,
                0.1
            );
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.10f,
            woflo.petsplus.state.PetComponent.Emotion.GLEE, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        return 0.6f; // Fun bubble play
    }
}
