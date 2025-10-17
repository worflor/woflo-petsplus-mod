package woflo.petsplus.ai.goals.special;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.EmotionFeedback;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.tags.PetsplusBlockTags;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Curiosity goal that drives pets to interact with interesting blocks in the environment.
 * Only activates when pet has a positive mood (Happy, Playful, Curious).
 * Creates a feedback loop by triggering positive emotions upon successful interactions.
 */
public class CuriosityGoal extends AdaptiveGoal {
    private static final int MAX_DURATION_TICKS = 300; // 15 seconds
    private static final double SCAN_RADIUS = 8.0; // Blocks to scan for interest points
    private static final double INTERACTION_RADIUS = 2.0; // Distance to consider "at" the block
    private static final int INTERACTION_DURATION = 60; // 3 seconds to interact with block
    
    // Positive moods that enable this goal
    private static final PetComponent.Mood[] POSITIVE_MOODS = {
        PetComponent.Mood.HAPPY,
        PetComponent.Mood.PLAYFUL,
        PetComponent.Mood.CURIOUS,
        PetComponent.Mood.BONDED
    };
    
    private BlockPos targetBlock;
    private int interactionTicks;
    private boolean hasInteracted;
    private Vec3d interactionPosition;

    public CuriosityGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.CURIOSITY), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        // Check if pet has a positive mood
        if (!hasPositiveMood()) {
            return false;
        }
        
        // Find an interesting block nearby
        targetBlock = findInterestingBlock();
        if (targetBlock == null) {
            return false;
        }
        
        // Calculate interaction position (slightly offset from block center)
        interactionPosition = Vec3d.ofCenter(targetBlock).add(
            (mob.getRandom().nextDouble() - 0.5) * 1.5,
            0,
            (mob.getRandom().nextDouble() - 0.5) * 1.5
        );
        
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (getActiveTicks() >= MAX_DURATION_TICKS) {
            return false;
        }
        
        // Stop if mood is no longer positive
        if (!hasPositiveMood()) {
            return false;
        }
        
        // Stop if block is no longer valid
        if (targetBlock == null || !isValidInterestBlock(targetBlock)) {
            return false;
        }
        
        // Continue until interaction is complete
        return !hasInteracted || interactionTicks < INTERACTION_DURATION;
    }

    @Override
    protected void onStartGoal() {
        interactionTicks = 0;
        hasInteracted = false;
        Petsplus.LOGGER.debug("[CuriosityGoal] Pet {} investigating block at {}", 
            mob.getDisplayName().getString(), targetBlock);
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        targetBlock = null;
        interactionTicks = 0;
        hasInteracted = false;
        interactionPosition = null;
    }

    @Override
    protected void onTickGoal() {
        // Check if we've reached the interaction position
        double distanceToInteraction = mob.squaredDistanceTo(interactionPosition);
        
        if (distanceToInteraction <= INTERACTION_RADIUS * INTERACTION_RADIUS) {
            // We're at the block, start interaction
            if (!hasInteracted) {
                hasInteracted = true;
                Petsplus.LOGGER.debug("[CuriosityGoal] Pet {} interacting with block at {}", 
                    mob.getDisplayName().getString(), targetBlock);
                
                // Trigger environmental interaction stimulus
                triggerInteractionStimulus();
            }
            
            interactionTicks++;
            
            // Look at the block
            mob.getLookControl().lookAt(
                Vec3d.ofCenter(targetBlock).add(0, 0.5, 0),
                30.0f, 30.0f
            );
            
            // Perform interaction animation
            performInteractionAnimation();
        } else {
            // Move toward the block
            mob.getNavigation().startMovingTo(
                interactionPosition.x, 
                interactionPosition.y, 
                interactionPosition.z, 
                1.0
            );
        }
    }
    
    private boolean hasPositiveMood() {
        PetComponent pc = PetComponent.get(mob);
        if (pc == null || pc.getMoodEngine() == null) {
            return false;
        }
        
        // Check if any positive mood is dominant
        PetComponent.Mood dominantMood = pc.getDominantMood();
        if (dominantMood == null) {
            return false;
        }
        
        for (PetComponent.Mood positiveMood : POSITIVE_MOODS) {
            if (dominantMood == positiveMood) {
                return true;
            }
        }
        
        // Also check mood blend - if any positive mood has significant strength
        var moodBlend = pc.getMoodBlend();
        for (PetComponent.Mood positiveMood : POSITIVE_MOODS) {
            if (moodBlend.getOrDefault(positiveMood, 0f) > 0.4f) {
                return true;
            }
        }
        
        return false;
    }
    
    private BlockPos findInterestingBlock() {
        World world = mob.getWorld();
        BlockPos centerPos = mob.getBlockPos();
        
        // Find all blocks of interest within scan radius
        List<BlockPos> candidateBlocks = BlockPos.stream(
            centerPos.add(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
            centerPos.add(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS)
        )
        .filter(pos -> world.isChunkLoaded(pos))
        .filter(this::isValidInterestBlock)
        .filter(pos -> mob.canSee(Vec3d.ofCenter(pos)))
        .collect(Collectors.toList());
        
        if (candidateBlocks.isEmpty()) {
            return null;
        }
        
        // Prefer blocks that are closer and more visible
        BlockPos closestBlock = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (BlockPos pos : candidateBlocks) {
            double distance = mob.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (distance < closestDistance) {
                closestDistance = distance;
                closestBlock = pos;
            }
        }
        
        return closestBlock;
    }
    
    private boolean isValidInterestBlock(BlockPos pos) {
        World world = mob.getWorld();
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        
        // Check if block is in our interest points tag
        if (state.isIn(PetsplusBlockTags.PET_INTEREST_POINTS)) {
            return true;
        }
        
        // Additional logic could be added here for dynamic interest detection
        // For example, blocks with special properties, recently placed blocks, etc.
        
        return false;
    }
    
    private void triggerInteractionStimulus() {
        PetComponent pc = PetComponent.get(mob);
        if (pc == null) {
            return;
        }
        
        // Trigger positive emotion stimulus for environmental interaction
        // This creates the feedback loop mentioned in the requirements
        pc.pushEmotion(PetComponent.Emotion.CURIOUS, 0.15f);
        pc.pushEmotion(PetComponent.Emotion.CHEERFUL, 0.10f);
        
        // If owner is nearby, also trigger a social connection emotion
        PlayerEntity owner = pc.getOwner();
        if (owner != null && mob.squaredDistanceTo(owner) < 100) { // 10 blocks
            pc.pushEmotion(PetComponent.Emotion.LOYALTY, 0.05f);
        }
    }
    
    private void performInteractionAnimation() {
        // Simple animation - could be expanded based on block type
        int animationPhase = interactionTicks % 20;
        
        if (animationPhase < 10) {
            // Look down at block
            mob.setPitch(MathHelper.lerp(animationPhase / 10.0f, 0.0f, 30.0f));
        } else {
            // Look back up
            mob.setPitch(MathHelper.lerp((animationPhase - 10) / 10.0f, 30.0f, 0.0f));
        }
        
        // Small head movement
        if (interactionTicks % 40 == 20) {
            mob.headYaw = mob.bodyYaw + mob.getRandom().nextFloat() * 10 - 5;
        }
    }

    @Override
    protected float calculateEngagement() {
        float baseEngagement = 0.7f;
        
        // Higher engagement for curious pets
        PetComponent pc = PetComponent.get(mob);
        if (pc != null && pc.getMoodEngine() != null) {
            float curiosityLevel = pc.getMoodEngine().getEmotionWeight(PetComponent.Emotion.CURIOUS);
            baseEngagement += curiosityLevel * 0.3f;
            
            // Higher engagement when closer to completing interaction
            if (hasInteracted) {
                float progress = Math.min(1.0f, interactionTicks / (float) INTERACTION_DURATION);
                baseEngagement += progress * 0.2f;
            }
        }
        
        return Math.min(baseEngagement, 1.0f);
    }

    @Override
    protected EmotionFeedback defineEmotionFeedback() {
        return new EmotionFeedback.Builder()
            .add(PetComponent.Emotion.CURIOUS, 0.20f)      // Curiosity reinforces itself
            .add(PetComponent.Emotion.CHEERFUL, 0.15f)     // Joy of discovery
            .add(PetComponent.Emotion.CONTENT, 0.10f)       // Satisfaction
            .withContagion(PetComponent.Emotion.CURIOUS, 0.020f)  // Spread curiosity
            .build();
    }
}