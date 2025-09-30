package woflo.petsplus.ai.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * When pets are BONDED, they stay very close to their owner and follow more closely than usual.
 */
public class BondedStayCloseGoal extends MoodBasedGoal {
    private static final double CLOSE_DISTANCE = 3.0; // Stay within 3 blocks
    private static final double TOO_FAR_DISTANCE = 5.0; // Start moving if further than this

    public BondedStayCloseGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.BONDED);
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        PlayerEntity owner = petComponent.getOwner();
        if (owner == null) return false;

        // Only activate if we're too far from owner
        return mob.squaredDistanceTo(owner) > TOO_FAR_DISTANCE * TOO_FAR_DISTANCE;
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        PlayerEntity owner = petComponent.getOwner();
        if (owner == null) return false;

        // Continue until we're close enough
        return mob.squaredDistanceTo(owner) > CLOSE_DISTANCE * CLOSE_DISTANCE;
    }

    @Override
    public void start() {
        super.start();
        // Start moving toward owner immediately
        PlayerEntity owner = petComponent.getOwner();
        if (owner != null) {
            mob.getNavigation().startMovingTo(owner, 1.2); // Faster movement when bonded
        }
    }

    @Override
    public void tick() {
        PlayerEntity owner = petComponent.getOwner();
        if (owner == null) {
            stop();
            return;
        }

        // Continuously update path to stay close
        double distance = mob.squaredDistanceTo(owner);

        if (distance > CLOSE_DISTANCE * CLOSE_DISTANCE) {
            // Too far - move closer
            mob.getNavigation().startMovingTo(owner, 1.2);
        } else {
            // Close enough - stop moving and face owner occasionally
            if (!mob.getNavigation().isIdle()) {
                mob.getNavigation().stop();
            }

            // Look at owner occasionally to show affection
            if (mob.getRandom().nextFloat() < 0.1f) { // 10% chance per tick
                mob.getLookControl().lookAt(owner);
            }
        }

        // If owner teleports or moves very far, immediately start following
        if (distance > 16 * 16) { // 16 blocks
            mob.getNavigation().startMovingTo(owner, 1.5); // Even faster for long distances
        }
    }

    @Override
    public void stop() {
        super.stop();
        mob.getNavigation().stop();
    }
}