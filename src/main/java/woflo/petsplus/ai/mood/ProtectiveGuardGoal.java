package woflo.petsplus.ai.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * When pets are PROTECTIVE, they position themselves between owner and potential threats,
 * maintaining a defensive stance.
 */
public class ProtectiveGuardGoal extends MoodBasedGoal {
    private Vec3d guardPosition;
    private int guardTicks;
    private static final int MAX_GUARD_TICKS = 200; // 10 seconds

    public ProtectiveGuardGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.PROTECTIVE);
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        PlayerEntity owner = petComponent.getOwner();
        if (owner == null || mob.squaredDistanceTo(owner) > 256) return false; // Owner too far

        // Look for nearby threats to guard against
        boolean hasThreats = !mob.getWorld().getEntitiesByClass(
            net.minecraft.entity.LivingEntity.class,
            owner.getBoundingBox().expand(16),
            entity -> entity instanceof net.minecraft.entity.mob.HostileEntity
        ).isEmpty();

        // Also guard if owner is hurt
        boolean ownerHurt = owner.getHealth() / owner.getMaxHealth() < 0.7f;

        return hasThreats || ownerHurt;
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        PlayerEntity owner = petComponent.getOwner();
        return guardTicks < MAX_GUARD_TICKS &&
               owner != null &&
               mob.squaredDistanceTo(owner) <= 256;
    }

    @Override
    public void start() {
        guardTicks = 0;
        updateGuardPosition();
    }

    @Override
    public void tick() {
        guardTicks++;

        PlayerEntity owner = petComponent.getOwner();
        if (owner == null) {
            stop();
            return;
        }

        // Update guard position every 2 seconds or if owner moves significantly
        if (guardTicks % 40 == 0 ||
            guardPosition == null ||
            owner.getPos().distanceTo(guardPosition) > 4.0) {
            updateGuardPosition();
        }

        // Move to guard position
        if (guardPosition != null && mob.getPos().distanceTo(guardPosition) > 1.5) {
            mob.getNavigation().startMovingTo(guardPosition.x, guardPosition.y, guardPosition.z, 1.0);
        } else {
            // At guard position - face toward potential threats
            lookTowardThreats(owner);
        }

        // Alert behavior - occasional looking around
        if (guardTicks % 20 == 0 && mob.getRandom().nextFloat() < 0.3f) {
            lookTowardThreats(owner);
        }
    }

    @Override
    public void stop() {
        guardPosition = null;
        guardTicks = 0;
        mob.getNavigation().stop();
    }

    private void updateGuardPosition() {
        PlayerEntity owner = petComponent.getOwner();
        if (owner == null) return;

        Vec3d ownerPos = owner.getPos();

        // Find the nearest threat to position between
        var nearbyThreats = mob.getWorld().getEntitiesByClass(
            net.minecraft.entity.LivingEntity.class,
            owner.getBoundingBox().expand(16),
            entity -> entity instanceof net.minecraft.entity.mob.HostileEntity
        );

        if (!nearbyThreats.isEmpty()) {
            // Position between owner and nearest threat
            var nearestThreat = nearbyThreats.get(0);
            double minDistance = Double.MAX_VALUE;
            for (var threat : nearbyThreats) {
                double distance = owner.squaredDistanceTo(threat);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestThreat = threat;
                }
            }

            Vec3d threatPos = nearestThreat.getPos();
            Vec3d direction = threatPos.subtract(ownerPos).normalize();

            // Position 2-3 blocks from owner, toward the threat
            guardPosition = ownerPos.add(direction.multiply(2.5));
        } else {
            // No specific threat - patrol around owner
            double angle = guardTicks * 0.05 + mob.getId(); // Slow rotation unique to each pet
            double radius = 3.0;

            guardPosition = ownerPos.add(
                Math.cos(angle) * radius,
                0,
                Math.sin(angle) * radius
            );
        }

        // Ensure guard position is on solid ground
        guardPosition = findSafeGroundNear(guardPosition);
    }

    private void lookTowardThreats(PlayerEntity owner) {
        // Look toward the nearest threat, or toward owner if no threats
        var nearbyThreats = mob.getWorld().getEntitiesByClass(
            net.minecraft.entity.LivingEntity.class,
            owner.getBoundingBox().expand(12),
            entity -> entity instanceof net.minecraft.entity.mob.HostileEntity
        );

        if (!nearbyThreats.isEmpty()) {
            var nearest = nearbyThreats.get(0);
            double minDistance = Double.MAX_VALUE;
            for (var threat : nearbyThreats) {
                double distance = mob.squaredDistanceTo(threat);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = threat;
                }
            }

            mob.getLookControl().lookAt(nearest);
        } else {
            // No immediate threats - occasionally look at owner
            if (mob.getRandom().nextFloat() < 0.5f) {
                mob.getLookControl().lookAt(owner);
            }
        }
    }

    private Vec3d findSafeGroundNear(Vec3d target) {
        // Simple ground-finding logic
        for (int dy = -2; dy <= 2; dy++) {
            Vec3d testPos = target.add(0, dy, 0);
            net.minecraft.util.math.BlockPos blockPos = new net.minecraft.util.math.BlockPos((int)testPos.x, (int)testPos.y, (int)testPos.z);

            if (mob.getWorld().getBlockState(blockPos.down()).isSolidBlock(mob.getWorld(), blockPos.down()) &&
                mob.getWorld().getBlockState(blockPos).isAir()) {
                return testPos;
            }
        }

        return target; // Fallback to original position
    }
}