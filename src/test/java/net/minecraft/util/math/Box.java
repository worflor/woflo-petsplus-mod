package net.minecraft.util.math;

/** Minimal axis-aligned box stub used in tests. */
public class Box {
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public static Box of(Vec3d center, double width, double height, double depth) {
        double halfX = width / 2.0;
        double halfY = height / 2.0;
        double halfZ = depth / 2.0;
        return new Box(center.x() - halfX, center.y() - halfY, center.z() - halfZ,
            center.x() + halfX, center.y() + halfY, center.z() + halfZ);
    }

    public Box expand(double amount) {
        return new Box(minX - amount, minY - amount, minZ - amount,
            maxX + amount, maxY + amount, maxZ + amount);
    }
}
