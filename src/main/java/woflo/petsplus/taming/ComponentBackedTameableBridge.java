package woflo.petsplus.taming;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

import java.util.UUID;

/**
 * Lightweight helper that stores tameable state in {@link PetComponent} so
 * non-vanilla companions can share the same code path.
 */
public final class ComponentBackedTameableBridge {

    private final MobEntity mob;
    private boolean tamed;
    private boolean sitting;
    @Nullable
    private UUID ownerUuid;

    public ComponentBackedTameableBridge(MobEntity mob) {
        this.mob = mob;
    }

    public boolean isTamed() {
        return this.tamed;
    }

    public void setTamed(boolean tamed) {
        this.tamed = tamed;
        if (tamed) {
            this.mob.setPersistent();
        } else {
            this.sitting = false;
            this.ownerUuid = null;
            PetComponent.getOrCreate(this.mob).setOwner(null);
        }
        this.sync();
    }

    public boolean isSitting() {
        return this.sitting;
    }

    public void setSitting(boolean sitting) {
        this.sitting = sitting;
        this.sync();
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        PetComponent component = PetComponent.getOrCreate(this.mob);
        if (ownerUuid == null) {
            component.setOwner(null);
        } else if (this.mob.getWorld() instanceof ServerWorld serverWorld) {
            PlayerEntity player = serverWorld.getPlayerByUuid(ownerUuid);
            if (player != null) {
                component.setOwner(player);
            }
        }
        this.sync();
    }

    @Nullable
    public LivingEntity getOwner() {
        if (this.ownerUuid == null) {
            return null;
        }
        if (this.mob.getWorld() instanceof ServerWorld serverWorld) {
            return serverWorld.getPlayerByUuid(this.ownerUuid);
        }
        return null;
    }

    public void setOwner(@Nullable LivingEntity owner) {
        if (owner == null) {
            this.setOwnerUuid(null);
            return;
        }

        this.ownerUuid = owner.getUuid();
        PetComponent component = PetComponent.getOrCreate(this.mob);
        if (owner instanceof PlayerEntity player) {
            component.setOwner(player);
        }
        this.sync();
    }

    private void sync() {
        PetComponent component = PetComponent.getOrCreate(this.mob);
        component.setStateData("petsplus:tamed", this.tamed);
        component.setStateData("petsplus:sitting", this.sitting);
        component.setStateData("petsplus:owner_uuid", this.ownerUuid != null ? this.ownerUuid.toString() : "");
    }
}

