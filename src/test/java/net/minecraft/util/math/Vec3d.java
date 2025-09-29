package net.minecraft.util.math;

/** Minimal 3D vector stub. */
public class Vec3d {
    public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);

    private final double x;
    private final double y;
    private final double z;

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }

    public Vec3d subtract(Vec3d other) {
        return new Vec3d(x - other.x, y - other.y, z - other.z);
    }

    public Vec3d add(Vec3d other) {
        return new Vec3d(x + other.x, y + other.y, z + other.z);
    }

    public Vec3d multiply(double scalar) {
        return new Vec3d(x * scalar, y * scalar, z * scalar);
    }

    public double dotProduct(Vec3d other) {
        return (x * other.x) + (y * other.y) + (z * other.z);
    }

    public double length() {
        return Math.sqrt((x * x) + (y * y) + (z * z));
    }

    public Vec3d normalize() {
        double length = length();
        if (length == 0.0) {
            return ZERO;
        }
        return new Vec3d(x / length, y / length, z / length);
    }
}
