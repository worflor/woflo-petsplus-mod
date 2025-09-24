package woflo.petsplus.ai.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * When pets are RESTLESS, they can't stay still and wander around frequently.
 */
public class RestlessWanderGoal extends MoodBasedGoal {
    private Vec3d wanderTarget;
    private int wanderTicks;
    private int stationaryTicks;
    private static final int MAX_STATIONARY_TICKS = 40; // Max 2 seconds still

    public RestlessWanderGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.RESTLESS);
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        // Always want to wander when restless, unless already moving somewhere important
        return !mob.getNavigation().isFollowingPath();
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        // Continue indefinitely while restless - this goal manages its own movement
        return true;
    }

    @Override
    public void start() {
        wanderTicks = 0;
        stationaryTicks = 0;
        selectNewWanderTarget();
    }

    @Override
    public void tick() {
        wanderTicks++;

        // Track if we're being stationary too long
        if (mob.getVelocity().length() < 0.01) {
            stationaryTicks++;
        } else {
            stationaryTicks = 0;
        }

        // If we've been still too long or reached target, pick new destination
        if (stationaryTicks > MAX_STATIONARY_TICKS ||
            (wanderTarget != null && mob.getPos().distanceTo(wanderTarget) < 1.5)) {
            selectNewWanderTarget();
            stationaryTicks = 0;
        }

        // Move toward current target
        if (wanderTarget != null) {
            mob.getNavigation().startMovingTo(wanderTarget.x, wanderTarget.y, wanderTarget.z, 1.1); // Slightly fast pace
        }

        // Change direction frequently (every 3-6 seconds)
        if (wanderTicks % (60 + mob.getRandom().nextInt(60)) == 0) {
            selectNewWanderTarget();
        }
    }

    @Override
    public void stop() {
        wanderTarget = null;
        wanderTicks = 0;
        stationaryTicks = 0;
    }

    private void selectNewWanderTarget() {
        Vec3d currentPos = mob.getPos();

        // Pick a random direction within 8 blocks
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        double distance = 4 + mob.getRandom().nextDouble() * 4; // 4-8 blocks

        double x = currentPos.x + Math.cos(angle) * distance;
        double z = currentPos.z + Math.sin(angle) * distance;
        double y = currentPos.y;

        // Try to find valid ground level
        for (int dy = -2; dy <= 2; dy++) {
            net.minecraft.util.math.BlockPos testPos = new net.minecraft.util.math.BlockPos((int)x, (int)(y + dy), (int)z);

            if (mob.getWorld().getBlockState(testPos).isAir() &&
                mob.getWorld().getBlockState(testPos.down()).isSolidBlock(mob.getWorld(), testPos.down())) {
                this.wanderTarget = new Vec3d(x, y + dy, z);
                return;
            }
        }

        // Fallback: just pick a nearby spot
        this.wanderTarget = new Vec3d(x, y, z);
    }
}