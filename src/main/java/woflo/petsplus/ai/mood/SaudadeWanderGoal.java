package woflo.petsplus.ai.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * When pets feel SAUDADE, they wistfully circle sentimental anchors such as owners or familiar homes.
 */
public class SaudadeWanderGoal extends MoodBasedGoal {
    private static final float REQUIRED_STRENGTH = 0.6f;
    private static final float CONTINUE_STRENGTH = 0.45f;
    private static final int MAX_WANDER_TICKS = 120;

    private BlockPos sentimentalAnchor;
    private Vec3d orbitTarget;
    private int wanderTicks;
    private double orbitAngleOffset;

    public SaudadeWanderGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.SAUDADE);
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        if (petComponent.getMoodStrength(requiredMood) < REQUIRED_STRENGTH) {
            return false;
        }

        BlockPos anchor = findSentimentalAnchor();
        if (anchor != null) {
            sentimentalAnchor = anchor;
            return true;
        }
        return false;
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        return sentimentalAnchor != null
            && wanderTicks < MAX_WANDER_TICKS
            && petComponent.getMoodStrength(requiredMood) >= CONTINUE_STRENGTH;
    }

    @Override
    public void start() {
        wanderTicks = 0;
        orbitAngleOffset = mob.getRandom().nextDouble() * Math.PI * 2;
        chooseNextOrbitTarget();
    }

    @Override
    public void tick() {
        wanderTicks++;

        if (sentimentalAnchor == null) {
            stop();
            return;
        }

        if (wanderTicks % 30 == 0) {
            BlockPos refreshed = findSentimentalAnchor();
            if (refreshed != null) {
                sentimentalAnchor = refreshed;
            }
        }

        if (orbitTarget == null || mob.squaredDistanceTo(orbitTarget) < 1.2 || wanderTicks % 35 == 0) {
            chooseNextOrbitTarget();
        }

        if (orbitTarget != null) {
            mob.getNavigation().startMovingTo(orbitTarget.x, orbitTarget.y, orbitTarget.z, 0.75);
            mob.getLookControl().lookAt(sentimentalAnchor.getX() + 0.5, sentimentalAnchor.getY() + 0.5, sentimentalAnchor.getZ() + 0.5);
        }
    }

    @Override
    public void stop() {
        sentimentalAnchor = null;
        orbitTarget = null;
        wanderTicks = 0;
        mob.getNavigation().stop();
    }

    private void chooseNextOrbitTarget() {
        if (sentimentalAnchor == null) {
            orbitTarget = null;
            return;
        }

        double angle = orbitAngleOffset + (wanderTicks / 20.0) * Math.PI / 2.0;
        double radius = 3.0 + Math.sin(wanderTicks / 15.0) * 1.5;
        double x = sentimentalAnchor.getX() + 0.5 + Math.cos(angle) * radius;
        double z = sentimentalAnchor.getZ() + 0.5 + Math.sin(angle) * radius;

        World world = mob.getWorld();
        BlockPos groundReference = BlockPos.ofFloored(x, sentimentalAnchor.getY(), z);
        if (!world.getWorldBorder().contains(groundReference) || !isChunkLoaded(world, groundReference)) {
            orbitTarget = Vec3d.ofCenter(sentimentalAnchor);
            return;
        }

        BlockPos ground = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, groundReference);
        if (!world.getBlockState(ground).isAir()) {
            ground = ground.up();
        }

        orbitTarget = new Vec3d(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
    }

    private BlockPos findSentimentalAnchor() {
        List<BlockPos> anchors = new ArrayList<>();
        var owner = petComponent.getOwner();
        if (owner != null && owner.getWorld() == mob.getWorld()) {
            anchors.add(owner.getBlockPos());
        }

        if (mob.getWorld() instanceof ServerWorld serverWorld) {
            anchors.add(serverWorld.getSpawnPos());
        }

        anchors.add(mob.getBlockPos());

        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;
        Vec3d petPos = mob.getPos();
        World world = mob.getWorld();
        for (BlockPos candidate : anchors) {
            if (candidate == null) {
                continue;
            }
            if (!world.getWorldBorder().contains(candidate)) {
                continue;
            }
            if (!isChunkLoaded(world, candidate)) {
                continue;
            }
            double distance = candidate.getSquaredDistance(petPos);
            if (distance < closestDistance) {
                closest = candidate;
                closestDistance = distance;
            }
        }

        return closest != null ? closest : mob.getBlockPos();
    }

    private boolean isChunkLoaded(World world, BlockPos pos) {
        if (world instanceof ServerWorld serverWorld) {
            return serverWorld.isChunkLoaded(pos);
        }
        return true;
    }
}
