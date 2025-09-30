package woflo.petsplus.ai.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * When pets are CURIOUS, they explore interesting nearby objects and areas.
 */
public class CuriousExploreGoal extends MoodBasedGoal {
    private BlockPos exploreTarget;
    private int exploreTicks;
    private static final int MAX_EXPLORE_TICKS = 120; // 6 seconds per target

    public CuriousExploreGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.CURIOUS);
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        // Look for interesting things to investigate
        BlockPos interestingTarget = findInterestingTarget();
        if (interestingTarget != null) {
            this.exploreTarget = interestingTarget;
            return true;
        }
        return false;
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        return exploreTicks < MAX_EXPLORE_TICKS && exploreTarget != null;
    }

    @Override
    public void start() {
        super.start();
        exploreTicks = 0;
        if (exploreTarget != null) {
            mob.getNavigation().startMovingTo(exploreTarget.getX() + 0.5, exploreTarget.getY(), exploreTarget.getZ() + 0.5, 0.9);
        }
    }

    @Override
    public void tick() {
        exploreTicks++;

        if (exploreTarget == null) {
            stop();
            return;
        }

        // If we've reached the target, investigate it
        if (mob.getBlockPos().isWithinDistance(exploreTarget, 2.0)) {
            // "Sniff around" behavior - look at target, move around it slightly
            Vec3d targetVec = Vec3d.ofCenter(exploreTarget);
            mob.getLookControl().lookAt(targetVec.x, targetVec.y, targetVec.z);

            // Circle around the interesting object
            if (exploreTicks % 20 == 0) { // Every second
                double angle = Math.random() * Math.PI * 2;
                double x = exploreTarget.getX() + 0.5 + Math.cos(angle) * 1.5;
                double z = exploreTarget.getZ() + 0.5 + Math.sin(angle) * 1.5;
                mob.getNavigation().startMovingTo(x, exploreTarget.getY(), z, 0.7);
            }
        } else if (!mob.getNavigation().isFollowingPath()) {
            // Not at target yet, keep moving toward it
            mob.getNavigation().startMovingTo(exploreTarget.getX() + 0.5, exploreTarget.getY(), exploreTarget.getZ() + 0.5, 0.9);
        }

        // Find a new target occasionally
        if (exploreTicks % 80 == 0) { // Every 4 seconds
            BlockPos newTarget = findInterestingTarget();
            if (newTarget != null && !newTarget.equals(exploreTarget)) {
                exploreTarget = newTarget;
                exploreTicks = Math.max(0, exploreTicks - 40); // Extend exploration time
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        exploreTarget = null;
        exploreTicks = 0;
        mob.getNavigation().stop();
    }

    private BlockPos findInterestingTarget() {
        BlockPos petPos = mob.getBlockPos();

        // Look for interesting blocks within 12 blocks
        for (int attempts = 0; attempts < 15; attempts++) {
            int x = petPos.getX() + mob.getRandom().nextInt(24) - 12;
            int y = petPos.getY() + mob.getRandom().nextInt(6) - 3;
            int z = petPos.getZ() + mob.getRandom().nextInt(24) - 12;

            BlockPos candidate = new BlockPos(x, y, z);
            if (isInteresting(candidate) && isAccessible(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private boolean isInteresting(BlockPos pos) {
        String blockName = mob.getWorld().getBlockState(pos).getBlock().toString().toLowerCase();

        // Interesting blocks that curious pets might investigate
        return blockName.contains("chest") ||
               blockName.contains("barrel") ||
               blockName.contains("flower") ||
               blockName.contains("mushroom") ||
               blockName.contains("torch") ||
               blockName.contains("lantern") ||
               blockName.contains("campfire") ||
               blockName.contains("bell") ||
               blockName.contains("lectern") ||
               blockName.contains("bookshelf") ||
               blockName.contains("enchanting") ||
               blockName.contains("brewing") ||
               blockName.contains("cauldron") ||
               blockName.contains("furnace") ||
               blockName.contains("anvil") ||
               blockName.contains("beacon") ||
               blockName.contains("jukebox") ||
               blockName.contains("note_block");
    }

    private boolean isAccessible(BlockPos pos) {
        // Check if pet can reasonably reach this position
        if (mob.squaredDistanceTo(Vec3d.ofCenter(pos)) > 144) return false; // Max 12 blocks

        // Check if there's a clear path (basic check)
        BlockPos above = pos.up();
        return mob.getWorld().getBlockState(above).isAir() ||
               mob.getWorld().getBlockState(above).getCollisionShape(mob.getWorld(), above).isEmpty();
    }
}