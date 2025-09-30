package net.minecraft.entity;

import java.util.UUID;

import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Minimal base entity stub for tests. */
public class Entity {
    private final UUID uuid = UUID.randomUUID();
    protected World world;
    protected double x;
    protected double y;
    protected double z;
    private boolean removed;
    public double fallDistance;
    private Text displayName = Text.literal("Entity");

    public Entity(World world) {
        this.world = world;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUuidAsString() {
        return uuid.toString();
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d getPos() {
        return new Vec3d(x, y, z);
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    public boolean isAlive() {
        return !removed;
    }

    public Box getBoundingBox() {
        return Box.of(getPos(), 1.0, 1.0, 1.0);
    }

    public double squaredDistanceTo(Entity other) {
        Vec3d diff = getPos().subtract(other.getPos());
        return diff.x() * diff.x() + diff.y() * diff.y() + diff.z() * diff.z();
    }

    public Text getDisplayName() {
        return displayName;
    }

    public void setDisplayName(Text displayName) {
        this.displayName = displayName;
    }
}
