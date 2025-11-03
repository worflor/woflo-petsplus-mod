package woflo.petsplus.ai.goals.play;


import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;

import java.util.EnumSet;

/**
 * Athletic parkour - jumps between interesting blocks for fun.
 * Creates playful, energetic behavior.
 */
public class ParkourChallengeGoal extends AdaptiveGoal {
    private static final int BASE_MAX_JUMPS = 12;

    private BlockPos targetBlock;
    private int jumpAttempts = 0;
    private Vec3d lastIssuedNavigationTarget;

    private int getMaxJumps() {
        PetContext ctx = getContext();
        float playfulness = ctx.hasPetsPlusComponent() ? ctx.getMoodStrength(woflo.petsplus.state.PetComponent.Mood.PLAYFUL) : 0.0f;
        float multiplier = MathHelper.clamp(1.0f + playfulness, 0.8f, 1.8f);
        return (int) (BASE_MAX_JUMPS * multiplier);
    }
    
    public ParkourChallengeGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.PARKOUR_CHALLENGE), EnumSet.of(Control.MOVE, Control.JUMP));
    }
    
    @Override
    protected boolean canStartGoal() {
        if (!mob.isOnGround()) return false;
        
        targetBlock = findParkourBlock();
        return targetBlock != null;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return jumpAttempts < getMaxJumps() && targetBlock != null;
    }
    
    @Override
    protected void onStartGoal() {
        jumpAttempts = 0;
        lastIssuedNavigationTarget = null;
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        mob.setPitch(0.0f);
        targetBlock = null;
        jumpAttempts = 0;
        lastIssuedNavigationTarget = null;
    }

    @Override
    protected void onTickGoal() {
        if (targetBlock == null) return;

        // Navigate toward block
        issueNavigationCommand(1.2);

        // Attempt jumps when close
        if (mob.getBlockPos().isWithinDistance(targetBlock, 3.0)) {
            if (mob.isOnGround() && mob.getRandom().nextFloat() < 0.3f) {
                mob.getJumpControl().setActive();
                jumpAttempts++;
            }
        }
        
        // Find new target after reaching
        if (mob.getBlockPos().isWithinDistance(targetBlock, 1.5)) {
            BlockPos newTarget = findParkourBlock();
            if (newTarget != null) {
                targetBlock = newTarget;
                lastIssuedNavigationTarget = null;
            }
        }
        
        // Playful head bob
        mob.setPitch((float) Math.sin(jumpAttempts * 0.5) * 10);
    }
    
    /**
     * Finds an interesting block to jump to/onto.
     */
    private BlockPos findParkourBlock() {
        BlockPos start = mob.getBlockPos();
        
        for (int attempts = 0; attempts < 10; attempts++) {
            int dx = mob.getRandom().nextInt(7) - 3;
            int dz = mob.getRandom().nextInt(7) - 3;
            int dy = mob.getRandom().nextInt(3) - 1;
            
            BlockPos candidate = start.add(dx, dy, dz);
            BlockState state = mob.getEntityWorld().getBlockState(candidate);
            
            // Interesting blocks for parkour
            if (isInterestingParkourBlock(state)) {
                Path path = mob.getNavigation().findPathTo(candidate, 0);
                if (path != null && path.reachesTarget()) {
                    return candidate;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a block is interesting for parkour.
     */
    private boolean isInterestingParkourBlock(BlockState state) {
        return state.isIn(woflo.petsplus.tags.PetsplusBlockTags.PARKOUR_BLOCKS);
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.triple(
            woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.18f,
            woflo.petsplus.state.PetComponent.Emotion.PRIDE, 0.12f,
            woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.10f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.8f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.55f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.6f, 1.18f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.55f) / 0.3f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.68f, 1.14f);
        engagement *= momentumScale;

        // Very engaging for playful pets
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.5f)) {
            engagement = 1.0f;
        }
        
        // Engaging for young/energetic pets
        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            engagement += 0.1f;
        }
        
        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }

    private void issueNavigationCommand(double speed) {
        if (targetBlock == null) {
            return;
        }
        Vec3d target = new Vec3d(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5);
        if (!mob.getNavigation().isIdle() && lastIssuedNavigationTarget != null
            && target.squaredDistanceTo(lastIssuedNavigationTarget) <= 0.04) {
            return;
        }
        mob.getNavigation().startMovingTo(
            targetBlock.getX(),
            targetBlock.getY(),
            targetBlock.getZ(),
            speed
        );
        lastIssuedNavigationTarget = target;
    }
}
