package woflo.petsplus.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.taming.ComponentBackedTameableBridge;

import java.util.UUID;

/**
 * Shares the Pets+ tameable bridge logic with turtles so they align with other companions.
 */
@Mixin(TurtleEntity.class)
public abstract class TurtleEntityMixin extends AnimalEntity implements PetsplusTameable {

    @Unique
    private final ComponentBackedTameableBridge petsplus$bridge =
        new ComponentBackedTameableBridge((MobEntity) (Object) this);

    protected TurtleEntityMixin(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public boolean petsplus$isTamed() {
        return this.petsplus$bridge.isTamed();
    }

    @Override
    public void petsplus$setTamed(boolean tamed) {
        this.petsplus$bridge.setTamed(tamed);
        if (!tamed) {
            this.setAiDisabled(false);
        }
    }

    @Override
    public @Nullable UUID petsplus$getOwnerUuid() {
        return this.petsplus$bridge.getOwnerUuid();
    }

    @Override
    public void petsplus$setOwnerUuid(@Nullable UUID ownerUuid) {
        this.petsplus$bridge.setOwnerUuid(ownerUuid);
    }

    @Override
    public @Nullable LivingEntity petsplus$getOwner() {
        return this.petsplus$bridge.getOwner();
    }

    @Override
    public void petsplus$setOwner(@Nullable LivingEntity owner) {
        this.petsplus$bridge.setOwner(owner);
    }

    @Override
    public boolean petsplus$isSitting() {
        return this.petsplus$bridge.isSitting();
    }

    @Override
    public void petsplus$setSitting(boolean sitting) {
        this.petsplus$bridge.setSitting(sitting);
        this.setAiDisabled(sitting);
        if (sitting) {
            this.getNavigation().stop();
            this.setVelocity(Vec3d.ZERO);
        }
    }
}
