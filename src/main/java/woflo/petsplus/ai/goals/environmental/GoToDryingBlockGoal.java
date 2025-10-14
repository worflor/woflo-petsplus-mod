package woflo.petsplus.ai.goals.environmental;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.tags.ModBlockTags;
import woflo.petsplus.tags.PetsplusEntityTypeTags;

import java.util.EnumSet;
import java.util.Random;

public class GoToDryingBlockGoal extends AdaptiveGoal {

    private static final int SEARCH_RADIUS = 10;
    private static final int DRY_TIME_TICKS = 60; // 3 seconds

    private BlockPos targetDryingBlock;
    private int dryingTicks;

    public GoToDryingBlockGoal(MobEntity mob, GoalDefinition goalDefinition) {
        super(mob, goalDefinition, EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartGoal() {
        if (!isEntityWet(mob)) {
            return false;
        }
        if (mob.getType().isIn(PetsplusEntityTypeTags.AQUATIC_LIKE)) {
            return false;
        }
        return findDryingBlock();
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (dryingTicks >= DRY_TIME_TICKS) {
            return false;
        }
        if (targetDryingBlock == null || !isEntityWet(mob)) {
            return false;
        }
        return mob.getBlockPos().getSquaredDistance(targetDryingBlock) < 4.0; // Continue if close to the block
    }

    @Override
    protected void onStartGoal() {
        if (targetDryingBlock != null) {
            this.mob.getNavigation().startMovingTo(targetDryingBlock.getX(), targetDryingBlock.getY(), targetDryingBlock.getZ(), 1.0);
            this.dryingTicks = 0;
        }
    }

    @Override
    protected void onTickGoal() {
        if (targetDryingBlock != null && mob.getBlockPos().getSquaredDistance(targetDryingBlock) < 4.0) {
            dryingTicks++;
        }
    }

    @Override
    protected void onStopGoal() {
        if (dryingTicks >= DRY_TIME_TICKS) {
            // This is a bit of a hack, as we can't directly make the entity "not wet".
            // The wetness is determined by vanilla logic (rain, water).
            // By moving them to a drying block, we assume it provides some shelter.
            // The main effect is that they will stop trying to find a drying block.
            // The existing ShakeDryGoal will trigger once they are no longer exposed to rain.
        }
        this.mob.getNavigation().stop();
        this.targetDryingBlock = null;
        this.dryingTicks = 0;
    }

    private boolean findDryingBlock() {
        BlockPos mobPos = mob.getBlockPos();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            BlockPos potentialPos = mobPos.add(random.nextInt(SEARCH_RADIUS * 2) - SEARCH_RADIUS, random.nextInt(6) - 3, random.nextInt(SEARCH_RADIUS * 2) - SEARCH_RADIUS);
            if (mob.getEntityWorld().getBlockState(potentialPos).isIn(ModBlockTags.DRYING_BLOCKS)) {
                this.targetDryingBlock = potentialPos;
                return true;
            }
        }
        return false;
    }

    private static boolean isEntityWet(MobEntity entity) {
        return entity.isWet();
    }

    @Override
    protected float calculateEngagement() {
        return 0.6f; // Slightly higher engagement as it's a comfort-seeking behavior
    }

    @Override
    protected woflo.petsplus.state.emotions.PetMoodEngine.ActivityType getActivityType() {
        return woflo.petsplus.state.emotions.PetMoodEngine.ActivityType.NEUTRAL;
    }

    @Override
    protected float getActivityIntensity() {
        return 0.1f;
    }
}
