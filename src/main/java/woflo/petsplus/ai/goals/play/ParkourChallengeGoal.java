package woflo.petsplus.ai.goals.play;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Athletic parkour - jumps between interesting blocks for fun.
 * Creates playful, energetic behavior.
 */
public class ParkourChallengeGoal extends AdaptiveGoal {
    private BlockPos targetBlock;
    private int jumpAttempts = 0;
    private static final int MAX_JUMPS = 6;
    
    public ParkourChallengeGoal(MobEntity mob) {
        super(mob, GoalType.PARKOUR_CHALLENGE, EnumSet.of(Control.MOVE, Control.JUMP));
    }
    
    @Override
    protected boolean canStartGoal() {
        if (!mob.isOnGround()) return false;
        
        targetBlock = findParkourBlock();
        return targetBlock != null;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return jumpAttempts < MAX_JUMPS && targetBlock != null;
    }
    
    @Override
    protected void onStartGoal() {
        jumpAttempts = 0;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        targetBlock = null;
    }
    
    @Override
    protected void onTickGoal() {
        if (targetBlock == null) return;
        
        // Navigate toward block
        mob.getNavigation().startMovingTo(
            targetBlock.getX(), 
            targetBlock.getY(), 
            targetBlock.getZ(), 
            1.2
        );
        
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
            BlockState state = mob.getWorld().getBlockState(candidate);
            
            // Interesting blocks for parkour
            if (isInterestingParkourBlock(state.getBlock())) {
                return candidate;
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a block is interesting for parkour.
     */
    private boolean isInterestingParkourBlock(Block block) {
        return block == Blocks.OAK_FENCE || 
               block == Blocks.SPRUCE_FENCE ||
               block == Blocks.BIRCH_FENCE ||
               block == Blocks.JUNGLE_FENCE ||
               block == Blocks.ACACIA_FENCE ||
               block == Blocks.DARK_OAK_FENCE ||
               block == Blocks.MANGROVE_FENCE ||
               block == Blocks.CHERRY_FENCE ||
               block == Blocks.OAK_LOG ||
               block == Blocks.HAY_BLOCK ||
               block == Blocks.SLIME_BLOCK ||
               block == Blocks.SCAFFOLDING;
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.8f;
        
        // Very engaging for playful pets
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.5f)) {
            engagement = 1.0f;
        }
        
        // Engaging for young/energetic pets
        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            engagement += 0.1f;
        }
        
        return Math.min(1.0f, engagement);
    }
}
