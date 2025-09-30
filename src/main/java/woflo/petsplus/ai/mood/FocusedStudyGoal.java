package woflo.petsplus.ai.mood;

import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * When pets are FOCUSED, they pause to study nearby scholarly or crafted blocks.
 */
public class FocusedStudyGoal extends MoodBasedGoal {
    private static final float REQUIRED_STRENGTH = 0.7f;
    private static final float CONTINUE_STRENGTH = 0.45f;
    private static final int MAX_STUDY_TICKS = 80;
    private BlockPos studyTarget;
    private BlockPos studyApproachPos;
    private Vec3d orbitTarget;
    private Vec3d lastNavigationTarget;
    private int studyTicks;

    public FocusedStudyGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.FOCUSED);
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        if (petComponent.getMoodStrength(requiredMood) < REQUIRED_STRENGTH) {
            return false;
        }

        return assignStudyTarget();
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        return studyTarget != null
            && studyApproachPos != null
            && studyTicks < MAX_STUDY_TICKS
            && petComponent.getMoodStrength(requiredMood) >= CONTINUE_STRENGTH
            && isStudyTargetValid();
    }

    @Override
    public void start() {
        super.start();
        studyTicks = 0;
        orbitTarget = null;
        lastNavigationTarget = null;

        if (studyApproachPos != null) {
            Vec3d approach = Vec3d.ofCenter(studyApproachPos);
            mob.getNavigation().startMovingTo(approach.x, approach.y, approach.z, 0.9);
        }
    }

    @Override
    public void tick() {
        studyTicks++;

        if (studyTarget == null || studyApproachPos == null) {
            stop();
            return;
        }

        if (studyTicks % 40 == 0 && !MoodWorldUtil.isStandable(mob.getWorld(), studyApproachPos)) {
            BlockPos refreshed = MoodWorldUtil.findStandableNear(mob.getWorld(), studyTarget, 3, 3);
            if (refreshed != null) {
                studyApproachPos = refreshed;
                orbitTarget = null;
                lastNavigationTarget = null;
            }
        }

        Vec3d approach = Vec3d.ofCenter(studyApproachPos);
        Vec3d focus = Vec3d.ofCenter(studyTarget).add(0, 0.25, 0);

        double distanceToApproach = mob.squaredDistanceTo(approach);
        if (distanceToApproach > 2.25) {
            orbitTarget = null;
            if (shouldIssuePath(approach)) {
                mob.getNavigation().startMovingTo(approach.x, approach.y, approach.z, 0.9);
                lastNavigationTarget = approach;
            }
        } else {
            mob.getNavigation().stop();
            mob.getLookControl().lookAt(focus.x, focus.y, focus.z);

            if (orbitTarget == null || mob.squaredDistanceTo(orbitTarget) < 1.0 || studyTicks % 25 == 0) {
                orbitTarget = pickOrbitTarget();
                lastNavigationTarget = null;
            }

            if (orbitTarget != null && shouldIssuePath(orbitTarget)) {
                mob.getNavigation().startMovingTo(orbitTarget.x, orbitTarget.y, orbitTarget.z, 0.75);
                lastNavigationTarget = orbitTarget;
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        studyTarget = null;
        studyApproachPos = null;
        orbitTarget = null;
        lastNavigationTarget = null;
        studyTicks = 0;
        mob.getNavigation().stop();
    }

    private boolean assignStudyTarget() {
        MoodWorldUtil.StandableMatch match = MoodWorldUtil.findClosestStandableMatch(
            mob,
            8,
            4,
            220,
            3,
            3,
            (world, pos, state) -> {
                if (!MoodEnvironmentAffinities.isFocusedStudyBlock(state)) {
                    return false;
                }

                BlockPos above = pos.up();
                if (!MoodWorldUtil.isChunkLoaded(world, above)) {
                    return false;
                }

                return world.getBlockState(above).getCollisionShape(world, above).isEmpty();
            }
        );

        if (match == null) {
            return false;
        }

        studyTarget = match.target();
        studyApproachPos = match.approach();
        return true;
    }

    private boolean isStudyTargetValid() {
        var world = mob.getWorld();
        if (studyTarget == null || studyApproachPos == null) {
            return false;
        }

        if (!world.getWorldBorder().contains(studyTarget) || !world.getWorldBorder().contains(studyApproachPos)) {
            return false;
        }

        if (!MoodWorldUtil.isChunkLoaded(world, studyTarget) || !MoodWorldUtil.isChunkLoaded(world, studyApproachPos)) {
            return false;
        }

        BlockState state = world.getBlockState(studyTarget);
        if (!MoodEnvironmentAffinities.isFocusedStudyBlock(state)) {
            return false;
        }

        BlockPos above = studyTarget.up();
        if (!MoodWorldUtil.isChunkLoaded(world, above)) {
            return false;
        }

        BlockState aboveState = world.getBlockState(above);
        if (!aboveState.isAir() && !aboveState.getCollisionShape(world, above).isEmpty()) {
            return false;
        }

        return MoodWorldUtil.isStandable(world, studyApproachPos);
    }

    private Vec3d pickOrbitTarget() {
        if (studyTarget == null) {
            return null;
        }

        World world = mob.getWorld();
        for (int attempts = 0; attempts < 5; attempts++) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double radius = 1.8 + mob.getRandom().nextDouble() * 0.7;
            double x = studyTarget.getX() + 0.5 + Math.cos(angle) * radius;
            double z = studyTarget.getZ() + 0.5 + Math.sin(angle) * radius;

            BlockPos base = BlockPos.ofFloored(x, studyTarget.getY(), z);
            BlockPos stand = MoodWorldUtil.findStandablePosition(world, base, 3);
            if (stand != null) {
                return Vec3d.ofCenter(stand);
            }
        }

        return studyApproachPos != null ? Vec3d.ofCenter(studyApproachPos) : null;
    }

    private boolean shouldIssuePath(Vec3d target) {
        if (target == null) {
            return false;
        }

        if (mob.getNavigation().isIdle()) {
            return true;
        }

        if (lastNavigationTarget == null) {
            return true;
        }

        return lastNavigationTarget.squaredDistanceTo(target) > 0.25;
    }
}
