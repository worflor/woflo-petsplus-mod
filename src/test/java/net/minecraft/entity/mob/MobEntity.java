package net.minecraft.entity.mob;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Minimal mob entity stub for tests. */
public class MobEntity extends LivingEntity {
    private Vec3d velocity = Vec3d.ZERO;
    public float bodyYaw;
    public float headYaw;
    private float yaw;
    private final Random random = Random.create();

    public MobEntity(World world) {
        super(world);
    }

    public Vec3d getVelocity() {
        return velocity;
    }

    public void setVelocity(Vec3d velocity) {
        this.velocity = velocity;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public Random getRandom() {
        return random;
    }

    public void setPathfindingPenalty(PathNodeType type, float penalty) {
        // no-op for tests
    }

    @Override
    public Text getDisplayName() {
        return super.getDisplayName();
    }
}
