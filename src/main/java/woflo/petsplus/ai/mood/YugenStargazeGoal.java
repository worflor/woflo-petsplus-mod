package woflo.petsplus.ai.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * When pets feel YUGEN, they seek a quiet spot to admire the night sky.
 */
public class YugenStargazeGoal extends MoodBasedGoal {
    private static final float REQUIRED_STRENGTH = 0.68f;
    private static final float CONTINUE_STRENGTH = 0.45f;
    private static final int MAX_STARGAZE_TICKS = 100;

    private BlockPos stargazeSpot;
    private int stargazeTicks;

    public YugenStargazeGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.YUGEN);
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        if (petComponent.getMoodStrength(requiredMood) < REQUIRED_STRENGTH) {
            return false;
        }

        World world = mob.getWorld();
        if (!world.getDimension().hasSkyLight()) {
            return false;
        }
        if (!world.isNight() || world.isRaining() || world.isThundering()) {
            return false;
        }

        BlockPos spot = findStargazeSpot();
        if (spot != null) {
            stargazeSpot = spot;
            return true;
        }
        return false;
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        World world = mob.getWorld();
        return stargazeSpot != null
            && stargazeTicks < MAX_STARGAZE_TICKS
            && world.isNight()
            && !world.isRaining()
            && !world.isThundering()
            && petComponent.getMoodStrength(requiredMood) >= CONTINUE_STRENGTH
            && world.isSkyVisible(stargazeSpot.up());
    }

    @Override
    public void start() {
        stargazeTicks = 0;
        if (stargazeSpot != null) {
            mob.getNavigation().startMovingTo(stargazeSpot.getX() + 0.5, stargazeSpot.getY(), stargazeSpot.getZ() + 0.5, 0.75);
        }
    }

    @Override
    public void tick() {
        stargazeTicks++;
        if (stargazeSpot == null) {
            stop();
            return;
        }

        if (mob.getBlockPos().isWithinDistance(stargazeSpot, 2.0)) {
            mob.getNavigation().stop();
            Vec3d look = new Vec3d(mob.getX(), mob.getEyeY() + 4.0, mob.getZ());
            mob.getLookControl().lookAt(look.x, look.y, look.z);

            if (stargazeTicks % 40 == 0) {
                // Slightly adjust gaze to simulate following constellations
                double offset = (mob.getRandom().nextDouble() - 0.5) * 1.5;
                mob.getLookControl().lookAt(mob.getX() + offset, mob.getEyeY() + 5.0, mob.getZ() + offset);
            }
        } else if (!mob.getNavigation().isFollowingPath()) {
            mob.getNavigation().startMovingTo(stargazeSpot.getX() + 0.5, stargazeSpot.getY(), stargazeSpot.getZ() + 0.5, 0.75);
        }
    }

    @Override
    public void stop() {
        stargazeSpot = null;
        stargazeTicks = 0;
        mob.getNavigation().stop();
    }

    private BlockPos findStargazeSpot() {
        BlockPos origin = mob.getBlockPos();
        World world = mob.getWorld();

        for (int attempts = 0; attempts < 10; attempts++) {
            int dx = mob.getRandom().nextInt(11) - 5;
            int dz = mob.getRandom().nextInt(11) - 5;
            BlockPos candidate = origin.add(dx, 0, dz);

            // adjust to top-most solid ground within a small vertical range
            candidate = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, candidate);
            if (!world.getBlockState(candidate).isAir()) {
                candidate = candidate.up();
            }

            if (!world.isSkyVisible(candidate)) {
                continue;
            }
            if (!world.getBlockState(candidate.down()).isSolidBlock(world, candidate.down())) {
                continue;
            }
            return candidate;
        }
        return null;
    }
}
