package woflo.petsplus.ai.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * When pets are CALM, they seek peaceful spots and rest peacefully.
 */
public class CalmRestGoal extends MoodBasedGoal {
    private BlockPos restSpot;
    private int restTicks;
    private static final int MAX_REST_TICKS = 400; // 20 seconds maximum

    public CalmRestGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.CALM);
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        // Only rest when not already resting and not moving somewhere important
        return restSpot == null && !mob.getNavigation().isFollowingPath();
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        return restTicks < MAX_REST_TICKS && restSpot != null;
    }

    @Override
    public void start() {
        restTicks = 0;
        restSpot = findPeacefulSpot();

        if (restSpot != null) {
            mob.getNavigation().startMovingTo(restSpot.getX() + 0.5, restSpot.getY(), restSpot.getZ() + 0.5, 0.8);
        }
    }

    @Override
    public void tick() {
        restTicks++;

        if (restSpot == null) {
            stop();
            return;
        }

        // Once we reach the rest spot, stay there
        if (mob.getBlockPos().isWithinDistance(restSpot, 2.0)) {
            // Stop moving and rest peacefully
            mob.getNavigation().stop();

            // Occasionally sit if the mob supports it (like wolves/cats)
            if (restTicks % 60 == 0 && mob instanceof PetsplusTameable tameable) {
                if (!tameable.petsplus$isSitting() && mob.getRandom().nextFloat() < 0.7f) {
                    tameable.petsplus$setSitting(true);
                }
            }
        } else if (!mob.getNavigation().isFollowingPath()) {
            // If we're not moving toward rest spot, try again
            mob.getNavigation().startMovingTo(restSpot.getX() + 0.5, restSpot.getY(), restSpot.getZ() + 0.5, 0.8);
        }

        // Find a new rest spot occasionally
        if (restTicks % 200 == 0) { // Every 10 seconds
            BlockPos newSpot = findPeacefulSpot();
            if (newSpot != null && !newSpot.equals(restSpot)) {
                restSpot = newSpot;
                if (mob instanceof PetsplusTameable tameable) {
                    tameable.petsplus$setSitting(false);
                }
                mob.getNavigation().startMovingTo(restSpot.getX() + 0.5, restSpot.getY(), restSpot.getZ() + 0.5, 0.8);
            }
        }
    }

    @Override
    public void stop() {
        restSpot = null;
        restTicks = 0;

        // Stand up if sitting
        if (mob instanceof PetsplusTameable tameable && tameable.petsplus$isSitting()) {
            tameable.petsplus$setSitting(false);
        }
    }

    private BlockPos findPeacefulSpot() {
        BlockPos petPos = mob.getBlockPos();

        // Look for a nice spot within 8 blocks
        for (int attempts = 0; attempts < 10; attempts++) {
            int x = petPos.getX() + mob.getRandom().nextInt(16) - 8;
            int z = petPos.getZ() + mob.getRandom().nextInt(16) - 8;

            // Find the ground level
            BlockPos candidate = new BlockPos(x, petPos.getY(), z);
            for (int y = petPos.getY() + 3; y >= petPos.getY() - 3; y--) {
                BlockPos checkPos = new BlockPos(x, y, z);
                if (!mob.getWorld().getBlockState(checkPos).isAir() &&
                    mob.getWorld().getBlockState(checkPos.up()).isAir()) {
                    candidate = checkPos.up();
                    break;
                }
            }

            // Check if it's a good peaceful spot
            if (isPeacefulSpot(candidate)) {
                return candidate;
            }
        }

        // Fallback to current position
        return petPos;
    }

    private boolean isPeacefulSpot(BlockPos pos) {
        // Check if position is safe and peaceful
        if (!mob.getWorld().getBlockState(pos).isAir()) return false;
        if (!mob.getWorld().getBlockState(pos.down()).isSolidBlock(mob.getWorld(), pos.down())) return false;

        // Avoid areas with monsters nearby
        boolean hasNearbyThreats = !mob.getWorld().getEntitiesByClass(
            net.minecraft.entity.LivingEntity.class,
            net.minecraft.util.math.Box.of(net.minecraft.util.math.Vec3d.ofCenter(pos), 8, 8, 8),
            entity -> entity instanceof net.minecraft.entity.mob.HostileEntity
        ).isEmpty();

        if (hasNearbyThreats) return false;

        // Prefer spots with natural elements (grass, flowers, water nearby)
        int natureScore = 0;
        for (BlockPos nearby : BlockPos.iterate(pos.add(-2, -1, -2), pos.add(2, 1, 2))) {
            String blockName = mob.getWorld().getBlockState(nearby).getBlock().toString().toLowerCase();
            if (blockName.contains("grass") || blockName.contains("flower") || blockName.contains("fern")) {
                natureScore++;
            }
        }

        return natureScore > 0; // Prefer natural spots
    }
}