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

        sparkPos = findClosest(MoodEnvironmentAffinities::isPassionateSpark);
        resonancePos = findClosest(MoodEnvironmentAffinities::isPassionateResonator);
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
        chargeTicks = 0;
        headingToResonance = mob.getRandom().nextBoolean();
        pickNextTarget();
    }

    @Override
    public void tick() {
        chargeTicks++;

        if (chargeTicks % 20 == 0) {
            // Refresh affinities occasionally to react to new blocks
            if (sparkPos == null) {
                sparkPos = findClosest(MoodEnvironmentAffinities::isPassionateSpark);
            }
            if (resonancePos == null) {
                resonancePos = findClosest(MoodEnvironmentAffinities::isPassionateResonator);
            }
        }

        if (currentTarget == null || mob.squaredDistanceTo(currentTarget) < 1.5 || chargeTicks % 18 == 0) {
            pickNextTarget();
        }

        if (currentTarget != null) {
            mob.getNavigation().startMovingTo(currentTarget.x, currentTarget.y, currentTarget.z, 1.2);
            mob.getLookControl().lookAt(currentTarget.x, currentTarget.y + 0.5, currentTarget.z);
        }
    }

    @Override
    public void stop() {
        sparkPos = null;
        resonancePos = null;
        currentTarget = null;
        mob.getNavigation().stop();
    }

    private void pickNextTarget() {
        if (sparkPos == null && resonancePos == null) {
            currentTarget = null;
            return;
        }

        if (sparkPos != null && resonancePos != null) {
            headingToResonance = !headingToResonance;
            BlockPos destination = headingToResonance ? resonancePos : sparkPos;
            currentTarget = Vec3d.ofCenter(destination);
            return;
        }

        BlockPos destination = sparkPos != null ? sparkPos : resonancePos;
        if (destination != null) {
            Vec3d center = Vec3d.ofCenter(destination);
            double offsetAngle = mob.getRandom().nextDouble() * Math.PI * 2;
            double radius = 1.4 + mob.getRandom().nextDouble();
            currentTarget = center.add(Math.cos(offsetAngle) * radius, 0.1, Math.sin(offsetAngle) * radius);
        }
    }

    private BlockPos findClosest(Predicate<net.minecraft.block.BlockState> predicate) {
        World world = mob.getWorld();
        BlockPos origin = mob.getBlockPos();
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterateOutwards(origin, 10, 3, 10)) {
            if (!origin.isWithinDistance(pos, 10)) {
                continue;
            }

            if (!world.getWorldBorder().contains(pos)) {
                continue;
            }

            if (!predicate.test(world.getBlockState(pos))) {
                continue;
            }

            double distance = pos.getSquaredDistance(origin);
            if (distance < closestDistance) {
                closest = pos.toImmutable();
                closestDistance = distance;
            }
        }

        return closest;
    }

    private boolean isAffinityValid(BlockPos pos, Predicate<net.minecraft.block.BlockState> predicate) {
        World world = mob.getWorld();
        return world.getWorldBorder().contains(pos) && predicate.test(world.getBlockState(pos));
    }
}
