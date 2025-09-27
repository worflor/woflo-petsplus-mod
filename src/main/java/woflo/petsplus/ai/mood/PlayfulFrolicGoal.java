package woflo.petsplus.ai.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * PLAYFUL pets look for toy-like blocks and hop around them for a short burst.
 */
public class PlayfulFrolicGoal extends MoodBasedGoal {
    private static final float REQUIRED_STRENGTH = 0.65f;
    private static final float CONTINUE_STRENGTH = 0.45f;
    private static final int MAX_FROLIC_TICKS = 100;

    private BlockPos toyPos;
    private Vec3d orbitTarget;
    private Vec3d lastNavigationTarget;
    private int frolicTicks;
    private boolean clockwise;

    public PlayfulFrolicGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.PLAYFUL);
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        if (petComponent.getMoodStrength(requiredMood) < REQUIRED_STRENGTH) {
            return false;
        }

        toyPos = findToySpot();
        return toyPos != null;
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        return toyPos != null
            && frolicTicks < MAX_FROLIC_TICKS
            && petComponent.getMoodStrength(requiredMood) >= CONTINUE_STRENGTH
            && isToyValid(toyPos);
    }

    @Override
    public void start() {
        frolicTicks = 0;
        clockwise = mob.getRandom().nextBoolean();
        lastNavigationTarget = null;
        chooseNextOrbitTarget();
    }

    @Override
    public void tick() {
        frolicTicks++;
        if (toyPos == null) {
            stop();
            return;
        }

        if (!isToyValid(toyPos) && frolicTicks % 20 == 0) {
            BlockPos refreshed = findToySpot();
            if (refreshed != null) {
                toyPos = refreshed;
                chooseNextOrbitTarget();
            }
        }

        if (toyPos == null) {
            stop();
            return;
        }

        Vec3d toyCenter = Vec3d.ofCenter(toyPos);
        double distance = mob.squaredDistanceTo(toyCenter);
        if (distance > 16.0 && shouldIssuePath(toyCenter)) {
            mob.getNavigation().startMovingTo(toyCenter.x, toyCenter.y, toyCenter.z, 1.05);
            lastNavigationTarget = toyCenter;
        }

        if (orbitTarget == null || mob.squaredDistanceTo(orbitTarget) < 1.0 || frolicTicks % 25 == 0) {
            chooseNextOrbitTarget();
        }

        if (orbitTarget != null) {
            if (shouldIssuePath(orbitTarget)) {
                mob.getNavigation().startMovingTo(orbitTarget.x, orbitTarget.y, orbitTarget.z, 1.1);
                lastNavigationTarget = orbitTarget;
            }
        }

        mob.getLookControl().lookAt(toyCenter.x, toyCenter.y, toyCenter.z);

        if (mob.isOnGround() && frolicTicks % 18 == 0) {
            mob.jump();
        }
    }

    @Override
    public void stop() {
        toyPos = null;
        orbitTarget = null;
        lastNavigationTarget = null;
        frolicTicks = 0;
        mob.getNavigation().stop();
    }

    private BlockPos findToySpot() {
        return MoodWorldUtil.findClosestMatch(mob, 8, 3, 240, (world, pos, state) -> {
            if (!MoodEnvironmentAffinities.isPlayfulToy(state)) {
                return false;
            }

            BlockPos above = pos.up();
            if (!MoodWorldUtil.isChunkLoaded(world, above)) {
                return false;
            }

            return world.getBlockState(above).getCollisionShape(world, above).isEmpty();
        });
    }

    private boolean isToyValid(BlockPos pos) {
        World world = mob.getWorld();
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }

        if (!MoodWorldUtil.isChunkLoaded(world, pos)) {
            return false;
        }

        BlockPos above = pos.up();
        if (!MoodWorldUtil.isChunkLoaded(world, above)) {
            return false;
        }

        return MoodEnvironmentAffinities.isPlayfulToy(world.getBlockState(pos))
            && world.getBlockState(above).getCollisionShape(world, above).isEmpty();
    }

    private void chooseNextOrbitTarget() {
        if (toyPos == null) {
            orbitTarget = null;
            return;
        }

        double baseAngle = (clockwise ? 1 : -1) * (frolicTicks / 14.0);
        double randomOffset = (mob.getRandom().nextDouble() - 0.5) * 0.8;
        double angle = baseAngle + randomOffset;
        double radius = 1.8 + mob.getRandom().nextDouble();
        double x = toyPos.getX() + 0.5 + Math.cos(angle) * radius;
        double z = toyPos.getZ() + 0.5 + Math.sin(angle) * radius;

        World world = mob.getWorld();
        BlockPos base = BlockPos.ofFloored(x, toyPos.getY(), z);
        BlockPos stand = MoodWorldUtil.findStandablePosition(world, base, 3);
        if (stand != null) {
            orbitTarget = Vec3d.ofCenter(stand);
        } else {
            orbitTarget = Vec3d.ofCenter(toyPos);
        }
        lastNavigationTarget = null;
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

        return lastNavigationTarget.squaredDistanceTo(target) > 0.36;
    }
}
