package woflo.petsplus.ai.goals.play;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Water splash - plays in shallow water, creating splashes.
 * Creates playful aquatic behavior.
 */
public class WaterSplashGoal extends AdaptiveGoal {
    private int splashTicks = 0;
    private static final int MAX_SPLASH_TICKS = 100; // 5 seconds
    
    public WaterSplashGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.WATER_SPLASH), EnumSet.of(Control.MOVE, Control.JUMP));
    }
    
    @Override
    protected boolean canStartGoal() {
        if (!mob.isOnGround()) return false;
        
        // Must be in shallow water (water block above solid ground)
        BlockPos below = mob.getBlockPos().down();
        BlockState belowState = mob.getEntityWorld().getBlockState(below);
        BlockState currentState = mob.getEntityWorld().getBlockState(mob.getBlockPos());
        
        return currentState.isOf(Blocks.WATER) && belowState.isSolidBlock(mob.getEntityWorld(), below);
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return splashTicks < MAX_SPLASH_TICKS;
    }
    
    @Override
    protected void onStartGoal() {
        splashTicks = 0;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        mob.setPitch(0.0f);
        splashTicks = 0;
    }
    
    @Override
    protected void onTickGoal() {
        splashTicks++;
        
        // Random jumping and spinning
        if (splashTicks % 15 == 0) {
            mob.getJumpControl().setActive();
        }
        
        // Random movement in water
        if (splashTicks % 20 == 0) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            mob.setVelocity(
                Math.cos(angle) * 0.15,
                mob.getVelocity().y,
                Math.sin(angle) * 0.15
            );
        }
        
        // Spin around
        mob.bodyYaw += 5;
        
        // Create splash particles
        if (splashTicks % 10 == 0 && mob.getEntityWorld() instanceof ServerWorld world) {
            world.spawnParticles(
                ParticleTypes.SPLASH,
                mob.getX(), mob.getY() + 0.2, mob.getZ(),
                8,
                0.5, 0.2, 0.5,
                0.2
            );
            
            // Occasional water droplets
            if (mob.getRandom().nextFloat() < 0.3f) {
                world.spawnParticles(
                    ParticleTypes.FALLING_WATER,
                    mob.getX(), mob.getY() + 0.5, mob.getZ(),
                    3,
                    0.3, 0.3, 0.3,
                    0.0
                );
            }
        }
        
        // Playful head movements
        mob.setPitch((float) Math.sin(splashTicks * 0.3) * 20);
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.triple(
            woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.12f,
            woflo.petsplus.state.PetComponent.Emotion.CHEERFUL, 0.10f,
            woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.08f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.85f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.5f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.68f, 1.14f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.55f) / 0.3f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.72f, 1.12f);
        engagement *= momentumScale;

        // Very engaging for playful pets
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.5f)) {
            engagement = 1.0f;
        }
        
        // Engaging for young pets
        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            engagement += 0.1f;
        }
        
        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}

