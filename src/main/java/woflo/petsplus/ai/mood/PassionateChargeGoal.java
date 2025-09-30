package woflo.petsplus.ai.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * PASSIONATE pets dart between fiery sparks and resonant blocks, as if hyped by the ambience.
 */
public class PassionateChargeGoal extends MoodBasedGoal {
    private static final float REQUIRED_STRENGTH = 0.62f;
    private static final float CONTINUE_STRENGTH = 0.45f;
    private static final int MAX_CHARGE_TICKS = 90;

    private BlockPos sparkPos;
    private BlockPos resonancePos;
    private Vec3d currentTarget;
    private Vec3d lastNavigationTarget;
    private int chargeTicks;
    private boolean headingToResonance;

    public PassionateChargeGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.PASSIONATE);
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        if (petComponent.getMoodStrength(requiredMood) < REQUIRED_STRENGTH) {
            return false;
        }

        sparkPos = MoodWorldUtil.findClosestMatch(mob, 10, 3, 320,
            (world, pos, state) -> MoodEnvironmentAffinities.isPassionateSpark(state));
        resonancePos = MoodWorldUtil.findClosestMatch(mob, 10, 3, 320,
            (world, pos, state) -> MoodEnvironmentAffinities.isPassionateResonator(state));
        return sparkPos != null || resonancePos != null;
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        if (chargeTicks >= MAX_CHARGE_TICKS) {
            return false;
        }

        if (petComponent.getMoodStrength(requiredMood) < CONTINUE_STRENGTH) {
            return false;
        }

        boolean sparkValid = sparkPos != null && isAffinityValid(sparkPos, MoodEnvironmentAffinities::isPassionateSpark);
        boolean resonanceValid = resonancePos != null && isAffinityValid(resonancePos, MoodEnvironmentAffinities::isPassionateResonator);

        if (!sparkValid) {
            sparkPos = null;
        }
        if (!resonanceValid) {
            resonancePos = null;
        }

        if (!sparkValid && !resonanceValid) {
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        super.start();
        chargeTicks = 0;
        headingToResonance = mob.getRandom().nextBoolean();
        lastNavigationTarget = null;
        pickNextTarget();
    }

    @Override
    public void tick() {
        chargeTicks++;

        if (chargeTicks % 20 == 0) {
            // Refresh affinities occasionally to react to new blocks
            if (sparkPos == null) {
                sparkPos = MoodWorldUtil.findClosestMatch(mob, 10, 3, 160,
                    (world, pos, state) -> MoodEnvironmentAffinities.isPassionateSpark(state));
            }
            if (resonancePos == null) {
                resonancePos = MoodWorldUtil.findClosestMatch(mob, 10, 3, 160,
                    (world, pos, state) -> MoodEnvironmentAffinities.isPassionateResonator(state));
            }
        }

        if (currentTarget == null || mob.squaredDistanceTo(currentTarget) < 1.5 || chargeTicks % 18 == 0) {
            pickNextTarget();
        }

        if (currentTarget != null) {
            if (shouldIssuePath(currentTarget)) {
                mob.getNavigation().startMovingTo(currentTarget.x, currentTarget.y, currentTarget.z, 1.2);
                lastNavigationTarget = currentTarget;
            }
            mob.getLookControl().lookAt(currentTarget.x, currentTarget.y + 0.5, currentTarget.z);
        }
    }

    @Override
    public void stop() {
        super.stop();
        sparkPos = null;
        resonancePos = null;
        currentTarget = null;
        lastNavigationTarget = null;
        mob.getNavigation().stop();
    }

    private void pickNextTarget() {
        if (sparkPos == null && resonancePos == null) {
            currentTarget = null;
            lastNavigationTarget = null;
            return;
        }

        if (sparkPos != null && resonancePos != null) {
            headingToResonance = !headingToResonance;
            BlockPos destination = headingToResonance ? resonancePos : sparkPos;
            currentTarget = createApproachTarget(destination, 1.2, 2.4);
            lastNavigationTarget = null;
            return;
        }

        BlockPos destination = sparkPos != null ? sparkPos : resonancePos;
        if (destination != null) {
            currentTarget = createApproachTarget(destination, 1.0, 2.0);
            lastNavigationTarget = null;
        }
    }

    private boolean isAffinityValid(BlockPos pos, Predicate<net.minecraft.block.BlockState> predicate) {
        World world = mob.getWorld();
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }

        if (!MoodWorldUtil.isChunkLoaded(world, pos)) {
            return false;
        }

        return predicate.test(world.getBlockState(pos));
    }

    private boolean shouldIssuePath(Vec3d target) {
        if (mob.getNavigation().isIdle()) {
            return true;
        }

        if (lastNavigationTarget == null) {
            return true;
        }

        return lastNavigationTarget.squaredDistanceTo(target) > 0.36;
    }

    private Vec3d createApproachTarget(BlockPos destination, double minRadius, double maxRadius) {
        if (destination == null) {
            return null;
        }

        World world = mob.getWorld();
        Vec3d center = Vec3d.ofCenter(destination);

        for (int attempts = 0; attempts < 5; attempts++) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double radius = minRadius + mob.getRandom().nextDouble() * (maxRadius - minRadius);
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            BlockPos base = BlockPos.ofFloored(x, destination.getY(), z);
            BlockPos stand = MoodWorldUtil.findStandablePosition(world, base, 3);
            if (stand != null) {
                return Vec3d.ofCenter(stand);
            }
        }

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int ring = 1; ring <= 2; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    mutable.set(destination.getX() + dx, destination.getY(), destination.getZ() + dz);
                    BlockPos stand = MoodWorldUtil.findStandablePosition(world, mutable, 3);
                    if (stand != null) {
                        return Vec3d.ofCenter(stand);
                    }
                }
            }
        }

        return mob.getPos();
    }
}
