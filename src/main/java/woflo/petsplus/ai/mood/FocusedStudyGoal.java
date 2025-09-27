package woflo.petsplus.ai.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
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

        BlockPos target = findStudyTarget();
        if (target != null) {
            this.studyTarget = target;
            return true;
        }
        return false;
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        return studyTarget != null
            && studyTicks < MAX_STUDY_TICKS
            && petComponent.getMoodStrength(requiredMood) >= CONTINUE_STRENGTH
            && isStudyTargetValid(studyTarget);
    }

    @Override
    public void start() {
        studyTicks = 0;
        if (studyTarget != null) {
            mob.getNavigation().startMovingTo(studyTarget.getX() + 0.5, studyTarget.getY(), studyTarget.getZ() + 0.5, 0.9);
        }
    }

    @Override
    public void tick() {
        studyTicks++;

        if (studyTarget == null) {
            stop();
            return;
        }

        if (mob.getBlockPos().isWithinDistance(studyTarget, 2.0)) {
            mob.getNavigation().stop();
            Vec3d look = Vec3d.ofCenter(studyTarget).add(0, 0.25, 0);
            mob.getLookControl().lookAt(look.x, look.y, look.z);

            if (studyTicks % 25 == 0) {
                // Slowly circle the target to imply careful inspection
                double angle = mob.getRandom().nextDouble() * Math.PI * 2;
                double radius = 1.8 + mob.getRandom().nextDouble() * 0.7;
                double x = studyTarget.getX() + 0.5 + Math.cos(angle) * radius;
                double z = studyTarget.getZ() + 0.5 + Math.sin(angle) * radius;
                mob.getNavigation().startMovingTo(x, studyTarget.getY(), z, 0.75);
            }
        } else if (!mob.getNavigation().isFollowingPath()) {
            mob.getNavigation().startMovingTo(studyTarget.getX() + 0.5, studyTarget.getY(), studyTarget.getZ() + 0.5, 0.9);
        }
    }

    @Override
    public void stop() {
        studyTarget = null;
        studyTicks = 0;
        mob.getNavigation().stop();
    }

    private BlockPos findStudyTarget() {
        BlockPos origin = mob.getBlockPos();

        for (int attempts = 0; attempts < 12; attempts++) {
            int dx = mob.getRandom().nextInt(17) - 8;
            int dy = mob.getRandom().nextInt(5) - 2;
            int dz = mob.getRandom().nextInt(17) - 8;

            BlockPos candidate = origin.add(dx, dy, dz);
            if (!isStudyTargetValid(candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private boolean isStudyTargetValid(BlockPos pos) {
        var world = mob.getWorld();
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }

        BlockState state = world.getBlockState(pos);
        if (!MoodEnvironmentAffinities.isFocusedStudyBlock(state)) {
            return false;
        }

        BlockPos above = pos.up();
        return world.getBlockState(above).isAir() || world.getBlockState(above).getCollisionShape(world, above).isEmpty();
    }
}
