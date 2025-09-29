package net.minecraft.entity.mob;

import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Minimal mob entity stub for tests. */
public class MobEntity extends LivingEntity {
    private Vec3d velocity = Vec3d.ZERO;
    public float bodyYaw;
    public float headYaw;

    public MobEntity(World world) {
        super(world);
    }

    public Vec3d getVelocity() {
        return velocity;
    }

    public void setVelocity(Vec3d velocity) {
        this.velocity = velocity;
    }

    @Override
    public Text getDisplayName() {
        return super.getDisplayName();
    }
}
