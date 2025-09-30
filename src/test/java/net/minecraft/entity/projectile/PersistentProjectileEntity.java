package net.minecraft.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/** Minimal persistent projectile stub for tests. */
public class PersistentProjectileEntity extends Entity {
    private boolean critical;

    public PersistentProjectileEntity(World world) {
        super(world);
    }

    public net.minecraft.entity.EntityType<?> getType() {
        return null;
    }

    public boolean isCritical() {
        return critical;
    }

    public void setCritical(boolean critical) {
        this.critical = critical;
    }
}
