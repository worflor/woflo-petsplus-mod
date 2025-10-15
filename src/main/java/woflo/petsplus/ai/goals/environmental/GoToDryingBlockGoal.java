package woflo.petsplus.ai.goals.environmental;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.tags.PetsplusEntityTypeTags;

import java.util.EnumSet;

public class GoToDryingBlockGoal extends AdaptiveGoal {

    private static final int SEARCH_RADIUS = 10;
    private static final int DRY_TIME_TICKS = 100; // 5 seconds minimum hold near shelter

    private BlockPos targetDryingBlock;
    private int dryingTicks;

    public GoToDryingBlockGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.GO_TO_DRYING_BLOCK), EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartGoal() {
        if (!isEntityWet(mob)) {
            return false;
        }
        if (isSheltered(mob)) {
            return false;
        }
        if (mob.getType().isIn(PetsplusEntityTypeTags.AQUATIC_LIKE)) {
            return false;
        }
        return findDryingBlock();
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (targetDryingBlock == null) {
            return false;
        }
        boolean atSpot = mob.getBlockPos().getSquaredDistance(targetDryingBlock) < 4.0;
        // Continue pathing until we reach the spot while wet
        if (!atSpot) {
            return isEntityWet(mob);
        }
        // Once at the spot, hold for a minimum drying time regardless of current wet flag
        if (dryingTicks < DRY_TIME_TICKS) {
            return true;
        }
        return false;
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
        if (targetDryingBlock != null) {
            if (mob.getBlockPos().getSquaredDistance(targetDryingBlock) < 4.0) {
                // Only increment while spot remains sheltered; if it becomes exposed, stop holding.
                if (!mob.getEntityWorld().isSkyVisible(targetDryingBlock)) {
                    dryingTicks++;
                } else {
                    // Lost shelter (block changed), abandon.
                    requestStop();
                }
            } else if (isEntityWet(mob) && (this.mob.getNavigation().isIdle())) {
                // Still wet and not at spot; kick navigation if it stalled
                this.mob.getNavigation().startMovingTo(targetDryingBlock.getX(), targetDryingBlock.getY(), targetDryingBlock.getZ(), 1.0);
            }
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
        Random random = mob.getRandom();
        World world = mob.getEntityWorld();
        for (int i = 0; i < 10; i++) {
            int xOffset = random.nextBetween(-SEARCH_RADIUS, SEARCH_RADIUS);
            int zOffset = random.nextBetween(-SEARCH_RADIUS, SEARCH_RADIUS);
            BlockPos samplePos = mobPos.add(xOffset, 0, zOffset);
            BlockPos groundPos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, samplePos);
            BlockPos standPos = groundPos.up();

            if (!world.isSkyVisible(standPos)
                && world.isAir(standPos)
                && world.getFluidState(standPos).isEmpty()
                && world.getBlockState(groundPos).isSideSolidFullSquare(world, groundPos, Direction.UP)) {
                var path = mob.getNavigation().findPathTo(standPos, 0);
                if (path != null) {
                    this.targetDryingBlock = standPos;
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isEntityWet(MobEntity entity) {
        return entity.isSubmergedInWater()
            || entity.isTouchingWater()
            || entity.isTouchingWaterOrRain();
    }

    @Override
    protected float calculateEngagement() {
        return 0.6f; // Slightly higher engagement as it's a comfort-seeking behavior
    }

    @Override
    protected woflo.petsplus.state.emotions.PetMoodEngine.ActivityType getActivityType() {
        return woflo.petsplus.state.emotions.PetMoodEngine.ActivityType.REST;
    }

    @Override
    protected float getActivityIntensity() {
        return 0.1f;
    }

    private static boolean isSheltered(MobEntity entity) {
        World world = entity.getEntityWorld();
        return world != null && !world.isSkyVisible(entity.getBlockPos());
    }
}
