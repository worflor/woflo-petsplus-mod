package woflo.petsplus.ai.goals.play;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Dive play - quick dives and resurfaces for fun.
 * Creates energetic aquatic play behavior.
 */
public class DivePlayGoal extends AdaptiveGoal {
    private static final int BASE_DIVE_TICKS = 80;

    private int diveTicks = 0;
    private boolean diving = false;
    private int targetDuration = BASE_DIVE_TICKS;

    private int computeDiveDuration() {
        PetContext ctx = getContext();
        float playfulness = ctx.hasPetsPlusComponent() ? ctx.getMoodStrength(woflo.petsplus.state.PetComponent.Mood.PLAYFUL) : 0.0f;
        float multiplier = MathHelper.clamp(1.0f + playfulness, 0.8f, 1.8f);
        return (int) (BASE_DIVE_TICKS * multiplier);
    }
    
    public DivePlayGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.DIVE_PLAY), EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        var profile = woflo.petsplus.ai.traits.SpeciesTraits.getProfile(mob);
        if (!profile.aquatic()) {
            return false;
        }
        return mob.isTouchingWater() && 
               mob.getEntityWorld() instanceof ServerWorld;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return diveTicks < targetDuration && mob.isTouchingWater();
    }
    
    @Override
    protected void onStartGoal() {
        diveTicks = 0;
        diving = mob.getRandom().nextBoolean();
        targetDuration = Math.max(40, computeDiveDuration());
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        mob.setPitch(0.0f);
        diveTicks = 0;
        diving = false;
    }
    
    @Override
    protected void onTickGoal() {
        diveTicks++;
        
        // Alternate between diving and surfacing
        if (diveTicks % 25 == 0) {
            diving = !diving;
        }
        
        if (diving) {
            // Try to catch fish
            java.util.List<net.minecraft.entity.passive.FishEntity> fish = mob.getEntityWorld().getNonSpectatingEntities(net.minecraft.entity.passive.FishEntity.class, mob.getBoundingBox().expand(5));
            if (!fish.isEmpty()) {
                mob.getNavigation().startMovingTo(fish.get(0), 1.2);
            } else {
                // Dive down
                mob.setVelocity(
                    mob.getVelocity().x * 0.9,
                    -0.1,
                    mob.getVelocity().z * 0.9
                );
            }
            mob.setPitch(60); // Look down
            
            // Bubble particles
            if (diveTicks % 5 == 0 && mob.getEntityWorld() instanceof ServerWorld world) {
                world.spawnParticles(
                    ParticleTypes.BUBBLE,
                    mob.getX(), mob.getY() + 0.5, mob.getZ(),
                    3,
                    0.2, 0.2, 0.2,
                    0.02
                );
            }
        } else {
            // Resurface
            mob.setVelocity(
                mob.getVelocity().x * 0.9,
                0.15,
                mob.getVelocity().z * 0.9
            );
            mob.setPitch(-30); // Look up
            
            // Splash particles when breaking surface
            if (mob.getY() > mob.getEntityWorld().getSeaLevel() - 1 && diveTicks % 3 == 0) {
                if (mob.getEntityWorld() instanceof ServerWorld world) {
                    world.spawnParticles(
                        ParticleTypes.SPLASH,
                        mob.getX(), mob.getY(), mob.getZ(),
                        5,
                        0.3, 0.1, 0.3,
                        0.1
                    );
                }
            }
        }
        
        // Random horizontal movement
        if (diveTicks % 10 == 0) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            mob.setVelocity(
                Math.cos(angle) * 0.2,
                mob.getVelocity().y,
                Math.sin(angle) * 0.2
            );
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.12f,
            woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.10f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.85f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.5f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.68f, 1.16f);
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

