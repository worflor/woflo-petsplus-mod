package woflo.petsplus.ai.mood;

import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.EnumSet;

/**
 * When pets are FEARFUL, they flee from threats and seek safety near their owner.
 */
public class FearfulFleeGoal extends MoodBasedGoal {
    private Vec3d fleeDirection;
    private int fleeTicks;
    private static final int MAX_FLEE_TICKS = 100; // 5 seconds

    public FearfulFleeGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.FEARFUL);
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        // Look for nearby threats (monsters, hostile entities, or low health)
        boolean hasNearbyThreats = !mob.getWorld().getEntitiesByClass(
            LivingEntity.class,
            mob.getBoundingBox().expand(8),
            entity -> entity instanceof HostileEntity
        ).isEmpty();

        boolean isLowHealth = mob.getHealth() / mob.getMaxHealth() < 0.5f;

        return hasNearbyThreats || isLowHealth;
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        return fleeTicks < MAX_FLEE_TICKS && fleeDirection != null;
    }

    @Override
    public void start() {
        // Calculate flee direction - away from threats, toward owner if possible
        Vec3d escapeDirection = calculateFleeDirection();
        if (escapeDirection != null) {
            this.fleeDirection = escapeDirection;
            this.fleeTicks = 0;
        }
    }

    @Override
    public void tick() {
        if (fleeDirection == null) return;

        fleeTicks++;

        // Move in flee direction
        Vec3d targetPos = mob.getPos().add(fleeDirection.multiply(2.0));
        mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 1.2); // Faster movement when scared

        // Occasionally recalculate direction
        if (fleeTicks % 20 == 0) {
            Vec3d newDirection = calculateFleeDirection();
            if (newDirection != null) {
                fleeDirection = newDirection;
            }
        }
    }

    @Override
    public void stop() {
        fleeDirection = null;
        fleeTicks = 0;
        mob.getNavigation().stop();
    }

    private Vec3d calculateFleeDirection() {
        PlayerEntity owner = petComponent.getOwner();
        Vec3d petPos = mob.getPos();

        // Find nearest threat
        Vec3d threatDirection = null;
        double closestThreatDistance = Double.MAX_VALUE;

        for (LivingEntity threat : mob.getWorld().getEntitiesByClass(
            LivingEntity.class,
            mob.getBoundingBox().expand(12),
            entity -> entity instanceof HostileEntity)) {

            double distance = mob.squaredDistanceTo(threat);
            if (distance < closestThreatDistance) {
                closestThreatDistance = distance;
                threatDirection = threat.getPos().subtract(petPos).normalize();
            }
        }

        // If owner is nearby, flee toward them
        if (owner != null && mob.squaredDistanceTo(owner) < 256) { // 16 blocks
            Vec3d toOwner = owner.getPos().subtract(petPos).normalize();
            if (threatDirection != null) {
                // Flee away from threat, but bias toward owner
                return threatDirection.negate().multiply(0.7).add(toOwner.multiply(0.3)).normalize();
            } else {
                return toOwner;
            }
        }

        // Otherwise, just flee away from threat
        return threatDirection != null ? threatDirection.negate() : null;
    }
}