package woflo.petsplus.ai.goals.wander;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Purposeful patrol - visits important landmarks in a circuit.
 * Creates territorial, guardian-like behavior.
 */
public class PurposefulPatrolGoal extends AdaptiveGoal {
    private List<BlockPos> patrolPoints = new ArrayList<>();
    private int currentPointIndex = 0;
    private int patrolTicks = 0;
    private static final int MAX_PATROL_TICKS = 400; // 20 seconds
    
    public PurposefulPatrolGoal(MobEntity mob) {
        super(mob, GoalType.PURPOSEFUL_PATROL, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        setupPatrolPoints();
        return !patrolPoints.isEmpty() && mob.isOnGround();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return patrolTicks < MAX_PATROL_TICKS && !patrolPoints.isEmpty();
    }
    
    @Override
    protected void onStartGoal() {
        patrolTicks = 0;
        currentPointIndex = 0;
        setupPatrolPoints();
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        patrolPoints.clear();
    }
    
    @Override
    protected void onTickGoal() {
        patrolTicks++;
        
        if (patrolPoints.isEmpty()) {
            return;
        }
        
        BlockPos currentTarget = patrolPoints.get(currentPointIndex);
        
        if (mob.getBlockPos().isWithinDistance(currentTarget, 2.0)) {
            // Reached patrol point - pause briefly, then move to next
            if (patrolTicks % 30 == 0) {
                currentPointIndex = (currentPointIndex + 1) % patrolPoints.size();
            }
        } else {
            mob.getNavigation().startMovingTo(
                currentTarget.getX(), 
                currentTarget.getY(), 
                currentTarget.getZ(), 
                0.8
            );
        }
        
        // Look alert
        if (patrolTicks % 40 < 20) {
            mob.setPitch(-5); // Slight upward tilt
        }
    }
    
    /**
     * Setup patrol points based on important locations.
     */
    private void setupPatrolPoints() {
        patrolPoints.clear();
        PetContext ctx = getContext();
        
        // Owner's last known position
        PlayerEntity owner = ctx.owner();
        if (owner != null) {
            patrolPoints.add(owner.getBlockPos());
        }
        
        // Pet's spawn/home position (current pos as fallback)
        patrolPoints.add(mob.getBlockPos());
        
        // Create a square patrol pattern around home
        BlockPos home = mob.getBlockPos();
        int radius = 8;
        
        patrolPoints.add(home.add(radius, 0, radius));
        patrolPoints.add(home.add(radius, 0, -radius));
        patrolPoints.add(home.add(-radius, 0, -radius));
        patrolPoints.add(home.add(-radius, 0, radius));
        
        // Shuffle slightly for variety
        if (mob.getRandom().nextBoolean() && patrolPoints.size() > 2) {
            BlockPos temp = patrolPoints.get(1);
            patrolPoints.set(1, patrolPoints.get(2));
            patrolPoints.set(2, temp);
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.FOCUSED, 0.18f)
            .add(woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.15f)
            .add(woflo.petsplus.state.PetComponent.Emotion.CURIOUS, 0.12f)
            .add(woflo.petsplus.state.PetComponent.Emotion.VIGILANT, 0.10f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.7f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.5f) / 0.3f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.62f, 1.08f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.5f) / 0.3f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.7f, 1.12f);
        engagement *= momentumScale;

        // Very engaging if protective
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.PROTECTIVE, 0.4f)) {
            engagement = 0.9f;
        }
        
        // Engaging for mature pets
        if (ctx.getAgeCategory() == PetContext.AgeCategory.MATURE) {
            engagement += 0.1f;
        }
        
        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}
