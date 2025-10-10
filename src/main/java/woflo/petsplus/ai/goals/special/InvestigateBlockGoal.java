package woflo.petsplus.ai.goals.special;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Investigate block - examines interesting blocks curiously.
 * Creates inquisitive, intelligent behavior.
 */
public class InvestigateBlockGoal extends AdaptiveGoal {
    private BlockPos targetBlock;
    private int investigateTicks = 0;
    private static final int MAX_INVESTIGATE_TICKS = 100; // 5 seconds
    
    public InvestigateBlockGoal(MobEntity mob) {
        super(mob, GoalType.INVESTIGATE_BLOCK, EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        targetBlock = findInterestingBlock();
        return targetBlock != null;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return investigateTicks < MAX_INVESTIGATE_TICKS && targetBlock != null;
    }
    
    @Override
    protected void onStartGoal() {
        investigateTicks = 0;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        targetBlock = null;
    }
    
    @Override
    protected void onTickGoal() {
        investigateTicks++;
        
        if (targetBlock == null) return;
        
        double distance = mob.getBlockPos().getSquaredDistance(targetBlock);
        
        if (distance > 4.0) {
            // Approach block
            mob.getNavigation().startMovingTo(
                targetBlock.getX(), 
                targetBlock.getY(), 
                targetBlock.getZ(), 
                0.8
            );
        } else {
            // Investigate up close
            mob.getNavigation().stop();
            
            // Look at block
            mob.getLookControl().lookAt(
                targetBlock.getX() + 0.5, 
                targetBlock.getY() + 0.5, 
                targetBlock.getZ() + 0.5
            );
            
            // Circle around it
            if (investigateTicks % 20 == 0) {
                double angle = mob.getRandom().nextDouble() * Math.PI * 2;
                double offsetX = Math.cos(angle) * 1.5;
                double offsetZ = Math.sin(angle) * 1.5;
                
                mob.getNavigation().startMovingTo(
                    targetBlock.getX() + offsetX,
                    targetBlock.getY(),
                    targetBlock.getZ() + offsetZ,
                    0.6
                );
            }
            
            // Curious head tilts
            if (investigateTicks % 15 < 8) {
                mob.setPitch(20); // Tilt down
            } else {
                mob.setPitch(-10); // Tilt up
            }
        }
    }
    
    /**
     * Finds an interesting block nearby to investigate.
     */
    private BlockPos findInterestingBlock() {
        BlockPos start = mob.getBlockPos();
        
        for (int attempts = 0; attempts < 15; attempts++) {
            int dx = mob.getRandom().nextInt(13) - 6;
            int dz = mob.getRandom().nextInt(13) - 6;
            int dy = mob.getRandom().nextInt(5) - 2;
            
            BlockPos candidate = start.add(dx, dy, dz);
            BlockState state = mob.getEntityWorld().getBlockState(candidate);
            
            if (isInterestingBlock(state.getBlock())) {
                return candidate;
            }
        }
        
        return null;
    }
    
    /**
     * Determines if a block is interesting enough to investigate.
     */
    private boolean isInterestingBlock(Block block) {
        return block == Blocks.CHEST ||
               block == Blocks.BARREL ||
               block == Blocks.CRAFTING_TABLE ||
               block == Blocks.FURNACE ||
               block == Blocks.BLAST_FURNACE ||
               block == Blocks.SMOKER ||
               block == Blocks.ENCHANTING_TABLE ||
               block == Blocks.BREWING_STAND ||
               block == Blocks.FLOWER_POT ||
               block == Blocks.CAKE ||
               block == Blocks.JUKEBOX ||
               block == Blocks.NOTE_BLOCK ||
               block == Blocks.BELL ||
               block == Blocks.CAMPFIRE ||
               block == Blocks.SOUL_CAMPFIRE ||
               block == Blocks.LECTERN ||
               block == Blocks.COMPOSTER ||
               block == Blocks.BEEHIVE ||
               block == Blocks.BEE_NEST ||
               block == Blocks.RESPAWN_ANCHOR ||
               block == Blocks.LODESTONE;
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.CURIOUS, 0.28f)
            .add(woflo.petsplus.state.PetComponent.Emotion.FOCUSED, 0.22f)
            .add(woflo.petsplus.state.PetComponent.Emotion.CHEERFUL, 0.18f)
            .add(woflo.petsplus.state.PetComponent.Emotion.HOPEFUL, 0.15f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.8f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float mentalFocus = MathHelper.clamp(ctx.mentalFocus(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.45f) / 0.3f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.65f, 1.08f);
        engagement *= staminaScale;

        float focusBlend = MathHelper.clamp((mentalFocus - 0.5f) / 0.3f, -1.0f, 1.0f);
        float focusScale = MathHelper.lerp((focusBlend + 1.0f) * 0.5f, 0.7f, 1.14f);
        engagement *= focusScale;

        // Peak engagement for curious pets
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.CURIOUS, 0.4f)) {
            engagement = 1.0f;
        }
        
        // Higher when close to block
        if (targetBlock != null && mob.getBlockPos().getSquaredDistance(targetBlock) < 4.0) {
            engagement += 0.15f;
        }
        
        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}

