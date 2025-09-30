package net.minecraft.entity.player;

import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Minimal player entity stub. */
public class PlayerEntity extends LivingEntity {
    private Vec3d look = new Vec3d(0.0, 0.0, 1.0);
    private boolean sneaking;

    public PlayerEntity(World world) {
        super(world);
    }

    public Vec3d getRotationVec(float tickDelta) {
        return look;
    }

    public void setRotationVec(Vec3d look) {
        this.look = look;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public float getAttackCooldownProgress(float partialTicks) {
        return 1.0f;
    }

    public Text getName() {
        return Text.literal("Player");
    }
}
