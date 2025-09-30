package woflo.petsplus.ai.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * When pets are HAPPY, they exhibit playful behavior - bouncing, circling owner, random movements.
 */
public class HappyPlayGoal extends MoodBasedGoal {
    private int playTicks;
    private Vec3d playTarget;
    private PlayBehavior currentBehavior;
    private static final int MAX_PLAY_TICKS = 160; // 8 seconds
    private static final String ACTION_JUMP = "happy_jump";
    private static final String ACTION_BEHAVIOR = "happy_behavior";
    private static final String ACTION_TARGET = "happy_target";

    private enum PlayBehavior {
        CIRCLE_OWNER,
        BOUNCE_AROUND,
        CHASE_NOTHING
    }

    public HappyPlayGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.HAPPY);
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
        super.start();
        this.playTicks = 0;
        long now = currentWorldTick();
        chooseNextBehavior(now, true);
        scheduleActionCooldown(ACTION_JUMP, now, 26, 44);
    }

    @Override
    public void tick() {
        playTicks++;
        long now = currentWorldTick();

        if (currentBehavior == null || isActionReady(ACTION_BEHAVIOR, now)) {
            chooseNextBehavior(now, false);
        }

        float jumpChance = 0.0f;
        switch (currentBehavior) {
            case CIRCLE_OWNER -> jumpChance = tickCircleOwner(now);
            case BOUNCE_AROUND -> jumpChance = tickBounceAround(now);
            case CHASE_NOTHING -> jumpChance = tickChaseNothing(now);
        }

        if (mob.isOnGround() && jumpChance > 0.0f && isActionReady(ACTION_JUMP, now)) {
            if (mob.getRandom().nextFloat() < jumpChance) {
                mob.jump();
            }
            scheduleActionCooldown(ACTION_JUMP, now, 30, 55);
        }
    }

    @Override
    public void stop() {
        super.stop();
        playTicks = 0;
        playTarget = null;
        mob.getNavigation().stop();
    }

    private float tickCircleOwner(long now) {
        PlayerEntity owner = petComponent.getOwner();
        if (owner == null || mob.squaredDistanceTo(owner) > 256) {
            return 0.0f;
        }

        // Circle around owner
        double angle = (playTicks * 0.1) + mob.getId(); // Unique offset per pet
        double radius = 3.0 + Math.sin(playTicks * 0.05); // Varying radius

        Vec3d ownerPos = owner.getPos();
        double x = ownerPos.x + Math.cos(angle) * radius;
        double z = ownerPos.z + Math.sin(angle) * radius;

        mob.getNavigation().startMovingTo(x, ownerPos.y, z, 1.0);

        // Gentle hop cadence while circling
        return 0.22f;
    }

    private float tickBounceAround(long now) {
        // Random bouncy movement
        if (playTarget == null || mob.getPos().distanceTo(playTarget) < 1.6 || isActionReady(ACTION_TARGET, now)) {
            selectNewPlayTarget();
            scheduleActionCooldown(ACTION_TARGET, now, 55, 85);
        }

        if (playTarget != null) {
            mob.getNavigation().startMovingTo(playTarget.x, playTarget.y, playTarget.z, 1.1);
        }

        return 0.38f;
    }

    private float tickChaseNothing(long now) {
        // Erratic movement like chasing invisible prey
        if (playTarget == null || isActionReady(ACTION_TARGET, now)) {
            selectNewPlayTarget();
            scheduleActionCooldown(ACTION_TARGET, now, 20, 35);
        }

        if (playTarget != null) {
            mob.getNavigation().startMovingTo(playTarget.x, playTarget.y, playTarget.z, 1.2);
        }

        return 0.32f;
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

    private void chooseNextBehavior(long now, boolean force) {
        PlayBehavior previous = currentBehavior;
        if (force || currentBehavior == null) {
            currentBehavior = PlayBehavior.values()[mob.getRandom().nextInt(PlayBehavior.values().length)];
        } else {
            PlayBehavior[] behaviors = PlayBehavior.values();
            PlayBehavior next = previous;
            int attempts = 0;
            while (next == previous && attempts < 4) {
                next = behaviors[mob.getRandom().nextInt(behaviors.length)];
                attempts++;
            }
            currentBehavior = next;
        }

        selectNewPlayTarget();
        scheduleActionCooldown(ACTION_BEHAVIOR, now, 80, 120);
        clearActionCooldown(ACTION_TARGET);
    }
}