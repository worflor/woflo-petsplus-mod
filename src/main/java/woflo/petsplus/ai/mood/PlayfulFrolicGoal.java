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
        if (distance > 16.0 && !mob.getNavigation().isFollowingPath()) {
            mob.getNavigation().startMovingTo(toyCenter.x, toyCenter.y, toyCenter.z, 1.05);
        }

        if (orbitTarget == null || mob.squaredDistanceTo(orbitTarget) < 1.0 || frolicTicks % 25 == 0) {
            chooseNextOrbitTarget();
        }

        if (orbitTarget != null) {
            mob.getNavigation().startMovingTo(orbitTarget.x, orbitTarget.y, orbitTarget.z, 1.1);
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
        frolicTicks = 0;
        mob.getNavigation().stop();
    }

    private BlockPos findToySpot() {
        World world = mob.getWorld();
        BlockPos origin = mob.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterateOutwards(origin, 8, 3, 8)) {
            if (!origin.isWithinDistance(pos, 8)) {
                continue;
            }

            if (!isToyValid(pos)) {
                continue;
            }

            double distance = pos.getSquaredDistance(origin);
            if (distance < bestDistance) {
                best = pos.toImmutable();
                bestDistance = distance;
            }
        }

        return best;
    }

    private boolean isToyValid(BlockPos pos) {
        World world = mob.getWorld();
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }

        return MoodEnvironmentAffinities.isPlayfulToy(world.getBlockState(pos));
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
        double y = toyPos.getY() + 0.2;
        orbitTarget = new Vec3d(x, y, z);
    }
}
