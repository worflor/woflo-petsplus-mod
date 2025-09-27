package woflo.petsplus.ai.mood;

import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Lightweight world helpers for mood AI goals so they can stay chunk-safe and efficient.
 */
public final class MoodWorldUtil {
    private MoodWorldUtil() {}

    /**
     * Predicate interface that exposes both the position and the state for affinity scans.
     */
    @FunctionalInterface
    public interface BlockSearchPredicate {
        boolean test(World world, BlockPos pos, BlockState state);
    }

    public static boolean isChunkLoaded(World world, BlockPos pos) {
        if (world instanceof ServerWorld serverWorld) {
            return serverWorld.isChunkLoaded(pos);
        }
        return true;
    }

    public static boolean isStandable(World world, BlockPos pos) {
        if (!isChunkLoaded(world, pos) || !isChunkLoaded(world, pos.down())) {
            return false;
        }

        BlockState state = world.getBlockState(pos);
        if (!state.getCollisionShape(world, pos).isEmpty() || !state.getFluidState().isEmpty()) {
            return false;
        }

        BlockState floor = world.getBlockState(pos.down());
        return !floor.getCollisionShape(world, pos.down()).isEmpty();
    }

    public static BlockPos findStandablePosition(World world, BlockPos base, int verticalRange) {
        if (!isChunkLoaded(world, base)) {
            return null;
        }

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int x = base.getX();
        int y = base.getY();
        int z = base.getZ();

        mutable.set(x, y, z);
        if (isStandable(world, mutable)) {
            return mutable.toImmutable();
        }

        for (int offset = 1; offset <= verticalRange; offset++) {
            mutable.set(x, y + offset, z);
            if (isStandable(world, mutable)) {
                return mutable.toImmutable();
            }

            mutable.set(x, y - offset, z);
            if (isStandable(world, mutable)) {
                return mutable.toImmutable();
            }
        }
        return null;
    }

    public static BlockPos findClosestMatch(MobEntity mob, int horizontalRadius, int verticalRadius, int maxChecks,
                                            BlockSearchPredicate predicate) {
        World world = mob.getWorld();
        BlockPos origin = mob.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;
        int evaluated = 0;

        for (int radius = 0; radius <= horizontalRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius != 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                        if (radius == 0 && dy != 0) {
                            continue;
                        }

                        mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        if (!world.getWorldBorder().contains(mutable)) {
                            continue;
                        }
                        if (!isChunkLoaded(world, mutable)) {
                            continue;
                        }

                        evaluated++;
                        if (maxChecks > 0 && evaluated > maxChecks && closest != null) {
                            return closest;
                        }

                        BlockState state = world.getBlockState(mutable);
                        if (!predicate.test(world, mutable, state)) {
                            continue;
                        }

                        double distance = mutable.getSquaredDistance(origin);
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closest = mutable.toImmutable();
                            if (distance <= 4.0) {
                                return closest;
                            }
                        }
                    }
                }
            }
        }
        return closest;
    }
}
