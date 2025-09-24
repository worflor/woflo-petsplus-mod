package woflo.petsplus.ai.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * When pets are JOYFUL, they exhibit playful behavior - bouncing, circling owner, random movements.
 */
public class JoyfulPlayGoal extends MoodBasedGoal {
    private int playTicks;
    private Vec3d playTarget;
    private PlayBehavior currentBehavior;
    private static final int MAX_PLAY_TICKS = 160; // 8 seconds

    private enum PlayBehavior {
        CIRCLE_OWNER,
        BOUNCE_AROUND,
        CHASE_NOTHING
    }

    public JoyfulPlayGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.JOYFUL);
        this.setControls(EnumSet.of(Control.MOVE, Control.JUMP));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        // Only play when safe (no nearby threats) and not already moving somewhere important
        boolean hasNearbyThreats = !mob.getWorld().getEntitiesByClass(
            HostileEntity.class,
            mob.getBoundingBox().expand(12),
            entity -> true
        ).isEmpty();

        return !hasNearbyThreats && !mob.getNavigation().isFollowingPath();
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        return playTicks < MAX_PLAY_TICKS;
    }

    @Override
    public void start() {
        this.playTicks = 0;
        this.currentBehavior = PlayBehavior.values()[mob.getRandom().nextInt(PlayBehavior.values().length)];
        selectNewPlayTarget();
    }

    @Override
    public void tick() {
        playTicks++;

        switch (currentBehavior) {
            case CIRCLE_OWNER -> tickCircleOwner();
            case BOUNCE_AROUND -> tickBounceAround();
            case CHASE_NOTHING -> tickChaseNothing();
        }

        // Switch behaviors occasionally
        if (playTicks % 40 == 0) { // Every 2 seconds
            currentBehavior = PlayBehavior.values()[mob.getRandom().nextInt(PlayBehavior.values().length)];
            selectNewPlayTarget();
        }
    }

    @Override
    public void stop() {
        playTicks = 0;
        playTarget = null;
        mob.getNavigation().stop();
    }

    private void tickCircleOwner() {
        PlayerEntity owner = petComponent.getOwner();
        if (owner == null || mob.squaredDistanceTo(owner) > 256) return;

        // Circle around owner
        double angle = (playTicks * 0.1) + mob.getId(); // Unique offset per pet
        double radius = 3.0 + Math.sin(playTicks * 0.05); // Varying radius

        Vec3d ownerPos = owner.getPos();
        double x = ownerPos.x + Math.cos(angle) * radius;
        double z = ownerPos.z + Math.sin(angle) * radius;

        mob.getNavigation().startMovingTo(x, ownerPos.y, z, 1.0);

        // Random jumps while circling
        if (mob.getRandom().nextFloat() < 0.1f && mob.isOnGround()) {
            mob.jump();
        }
    }

    private void tickBounceAround() {
        // Random bouncy movement
        if (playTarget == null || mob.getPos().distanceTo(playTarget) < 2.0) {
            selectNewPlayTarget();
        }

        if (playTarget != null) {
            mob.getNavigation().startMovingTo(playTarget.x, playTarget.y, playTarget.z, 1.1);
        }

        // Frequent jumping
        if (mob.getRandom().nextFloat() < 0.2f && mob.isOnGround()) {
            mob.jump();
        }
    }

    private void tickChaseNothing() {
        // Erratic movement like chasing invisible prey
        if (playTicks % 10 == 0) { // Change direction frequently
            selectNewPlayTarget();
        }

        if (playTarget != null) {
            mob.getNavigation().startMovingTo(playTarget.x, playTarget.y, playTarget.z, 1.2);
        }

        // Occasional pouncing (jumping)
        if (mob.getRandom().nextFloat() < 0.15f && mob.isOnGround()) {
            mob.jump();
        }
    }

    private void selectNewPlayTarget() {
        Vec3d currentPos = mob.getPos();
        double range = 5.0;

        double x = currentPos.x + (mob.getRandom().nextDouble() - 0.5) * range * 2;
        double z = currentPos.z + (mob.getRandom().nextDouble() - 0.5) * range * 2;
        double y = currentPos.y;

        // Try to find a valid ground position
        for (int dy = -2; dy <= 2; dy++) {
            if (mob.getWorld().getBlockState(new net.minecraft.util.math.BlockPos((int)x, (int)(y + dy), (int)z)).isAir()) {
                this.playTarget = new Vec3d(x, y + dy, z);
                return;
            }
        }

        this.playTarget = new Vec3d(x, y, z);
    }
}