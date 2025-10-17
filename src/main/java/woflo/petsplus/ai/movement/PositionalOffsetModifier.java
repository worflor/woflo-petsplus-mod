package woflo.petsplus.ai.movement;

import net.minecraft.util.math.Vec3d;

/**
 * Requests that the movement director shift the follow anchor by a weighted offset.
 */
public record PositionalOffsetModifier(double offsetX, double offsetY, double offsetZ, double weight) {
    public PositionalOffsetModifier {
        weight = Math.max(0.0, weight);
    }

    public Vec3d asVector() {
        return new Vec3d(offsetX, offsetY, offsetZ);
    }
}
