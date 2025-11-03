package woflo.petsplus.ai.util;

import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

/**
 * Utility helpers for evaluating whether movement targets are safe for ground-based navigation.
 */
public final class MovementSafetyUtil {
    private MovementSafetyUtil() {
    }

    /**
     * Returns true when the given target would cause the mob to step off a ledge or into a
     * hazardous drop, taking into account flight/aquatic capabilities.
     */
    public static boolean isUnsafeLedge(MobEntity mob,
                                        WorldView world,
                                        Vec3d target,
                                        double dropThreshold,
                                        int maxDepth,
                                        boolean canFly,
                                        boolean isAquatic) {
        if (canFly || world == null) {
            return false;
        }
        double dropMagnitude = mob.getY() - target.y;
        if (dropMagnitude <= dropThreshold) {
            return false;
        }

        BlockPos.Mutable mutable = new BlockPos.Mutable(
            MathHelper.floor(target.x),
            MathHelper.floor(target.y - 0.5d),
            MathHelper.floor(target.z));
        int depth = 0;
        while (mutable.getY() > world.getBottomY() && depth < maxDepth) {
            BlockState state = world.getBlockState(mutable);
            if (!state.getCollisionShape(world, mutable).isEmpty()) {
                if (state.getFluidState().isIn(FluidTags.WATER)) {
                    return !isAquatic;
                }
                return false;
            }
            FluidState fluid = world.getFluidState(mutable);
            if (fluid.isIn(FluidTags.WATER)) {
                return !isAquatic;
            }
            mutable.move(Direction.DOWN);
            depth++;
        }
        return true;
    }
}
