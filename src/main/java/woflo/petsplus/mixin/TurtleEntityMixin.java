package woflo.petsplus.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import woflo.petsplus.taming.ComponentBackedTameable;
import woflo.petsplus.taming.ComponentBackedTameableBridge;

/**
 * Shares the Pets+ tameable bridge logic with turtles so they align with other companions.
 */
@Mixin(TurtleEntity.class)
public abstract class TurtleEntityMixin extends AnimalEntity implements ComponentBackedTameable {

    @Unique
    private final ComponentBackedTameableBridge petsplus$bridge =
        new ComponentBackedTameableBridge((MobEntity) (Object) this);

    protected TurtleEntityMixin(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public ComponentBackedTameableBridge petsplus$getBridge() {
        return this.petsplus$bridge;
    }

    @Override
    public void petsplus$afterTameChange(boolean tamed) {
        if (!tamed) {
            this.setAiDisabled(false);
        }
    }

    @Override
    public void petsplus$afterSittingChange(boolean sitting) {
        this.setAiDisabled(sitting);
        if (sitting) {
            this.getNavigation().stop();
            this.setVelocity(Vec3d.ZERO);
        }
    }
}
