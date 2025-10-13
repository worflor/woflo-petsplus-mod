package woflo.petsplus.ai.goals.special;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Stargazing - looks up at the night sky contemplatively.
 * Creates peaceful, philosophical behavior.
 */
public class StargazingGoal extends AdaptiveGoal {
    private int gazeTicks = 0;
    private static final int MAX_GAZE_TICKS = 200; // 10 seconds
    private BlockPos gazeSpot;
    private float startingPitch = 0.0f;
    
    public StargazingGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.STARGAZING), EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        
        // Only at night, clear sky, peaceful environment
        if (ctx.isDaytime()) return false;
        if (mob.getEntityWorld().isRaining()) return false;
        if (!mob.isOnGround()) return false;
        
        // Check if sky is visible (not underground/covered)
        BlockPos above = mob.getBlockPos().up(10);
        if (!mob.getEntityWorld().isSkyVisible(above)) return false;
        
        gazeSpot = findGoodGazingSpot();
        return gazeSpot != null;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        PetContext ctx = getContext();
        return gazeSpot != null &&
               gazeTicks < MAX_GAZE_TICKS &&
               !ctx.isDaytime() &&
               !mob.getEntityWorld().isRaining() &&
               isGazeSpotValid(gazeSpot);
    }
    
    @Override
    protected void onStartGoal() {
        gazeTicks = 0;
        startingPitch = mob.getPitch();
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        mob.setPitch(startingPitch);
        mob.bodyYaw = MathHelper.wrapDegrees(mob.bodyYaw);
        gazeSpot = null;
    }

    @Override
    protected void onTickGoal() {
        gazeTicks++;

        if (gazeSpot == null || !isGazeSpotValid(gazeSpot)) {
            requestStop();
            return;
        }

        double distance = mob.getBlockPos().getSquaredDistance(gazeSpot);
        
        if (distance > 2.0) {
            // Move to gazing spot
            mob.getNavigation().startMovingTo(
                gazeSpot.getX(), 
                gazeSpot.getY(), 
                gazeSpot.getZ(), 
                0.6
            );
        } else {
            // Sit still and look up at stars
            mob.getNavigation().stop();
            
            // Look upward at sky
            mob.setPitch(-60); // Look up
            
            // Slow head movements - tracking stars/moon
            if (gazeTicks % 60 == 0) {
                mob.bodyYaw = MathHelper.wrapDegrees(mob.bodyYaw + mob.getRandom().nextFloat() * 20 - 10);
            }

            // Occasional contemplative movements
            if (gazeTicks % 80 == 0) {
                // Small head tilt
                mob.setPitch(-60 + (float) (Math.sin(gazeTicks * 0.05) * 10));
            }
        }
    }
    
    /**
     * Finds a good spot for stargazing - open area with sky view.
     */
    private BlockPos findGoodGazingSpot() {
        BlockPos start = mob.getBlockPos();
        
        for (int attempts = 0; attempts < 8; attempts++) {
            int dx = mob.getRandom().nextInt(9) - 4;
            int dz = mob.getRandom().nextInt(9) - 4;

            BlockPos candidate = start.add(dx, 0, dz);

            if (!mob.getEntityWorld().isChunkLoaded(candidate)) {
                continue;
            }

            // Must have clear sky view
            if (isGazeSpotValid(candidate)) {
                return candidate;
            }
        }

        // Fallback to current position if sky visible
        if (isGazeSpotValid(start)) {
            return start;
        }

        return null;
    }

    private boolean isGazeSpotValid(BlockPos spot) {
        return spot != null
            && mob.getEntityWorld().isChunkLoaded(spot)
            && mob.getEntityWorld().isSkyVisible(spot.up(5));
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.triple(
            woflo.petsplus.state.PetComponent.Emotion.YUGEN, 0.35f,
            woflo.petsplus.state.PetComponent.Emotion.MONO_NO_AWARE, 0.28f,
            woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.18f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.85f;

        float mentalFocus = MathHelper.clamp(ctx.mentalFocus(), 0.0f, 1.0f);
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);

        float focusBlend = MathHelper.clamp((mentalFocus - 0.55f) / 0.3f, -1.0f, 1.0f);
        float focusScale = MathHelper.lerp((focusBlend + 1.0f) * 0.5f, 0.6f, 1.15f);
        engagement *= focusScale;

        float socialBlend = MathHelper.clamp((socialCharge - 0.4f) / 0.3f, -1.0f, 1.0f);
        float socialScale = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.72f, 1.08f);
        engagement *= socialScale;

        // Very engaging for yugen (profound, mysterious beauty) mood
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.YUGEN, 0.3f)) {
            engagement = 1.0f;
        }
        
        // Engaging for calm, contemplative pets
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.CALM, 0.5f)) {
            engagement = 0.95f;
        }
        
        // More engaging for mature pets
        if (ctx.getAgeCategory() == PetContext.AgeCategory.MATURE) {
            engagement += 0.1f;
        }
        
        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}

