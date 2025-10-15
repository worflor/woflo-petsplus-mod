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

    public boolean setTamed(boolean tamed) {
        if (this.tamed == tamed) {
            return false;
        }
        this.tamed = tamed;
        if (tamed) {
            this.mob.setPersistent();
        } else {
            this.sitting = false;
            this.ownerUuid = null;
            PetComponent.getOrCreate(this.mob).setOwner(null);
        }
        this.sync();
        return true;
    }

    public boolean isSitting() {
        return this.sitting;
    }

    public boolean setSitting(boolean sitting) {
        if (this.sitting == sitting) {
            return false;
        }
        this.sitting = sitting;
        this.sync();
        return true;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public boolean setOwnerUuid(@Nullable UUID ownerUuid) {
        boolean changed = ownerUuid == null ? this.ownerUuid != null : !ownerUuid.equals(this.ownerUuid);
        this.ownerUuid = ownerUuid;
        PetComponent component = PetComponent.getOrCreate(this.mob);
        component.setOwnerUuid(ownerUuid);
        if (ownerUuid != null && this.mob.getEntityWorld() instanceof ServerWorld serverWorld) {
            PlayerEntity player = resolveOwner(serverWorld, ownerUuid);
            if (player != null) {
                component.setOwner(player);
            }
        }
        this.sync();
        return changed;
    }

    @Nullable
    public LivingEntity getOwner() {
        if (this.ownerUuid == null) {
            return null;
        }
        if (this.mob.getEntityWorld() instanceof ServerWorld serverWorld) {
            return resolveOwner(serverWorld, this.ownerUuid);
        }
        return null;
    }

    public boolean setOwner(@Nullable LivingEntity owner) {
        if (owner == null) {
            return this.setOwnerUuid(null);
        }

        boolean changed = this.ownerUuid == null || !this.ownerUuid.equals(owner.getUuid());
        this.ownerUuid = owner.getUuid();
        PetComponent component = PetComponent.getOrCreate(this.mob);
        if (owner instanceof PlayerEntity player) {
            component.setOwner(player);
            component.setOwnerUuid(player.getUuid());
        }
        if (!(owner instanceof PlayerEntity)) {
            component.setOwner(null);
            component.setOwnerUuid(null);
        }
        this.sync();
        return changed;
    }

    private void sync() {
        PetComponent component = PetComponent.getOrCreate(this.mob);
        component.setStateData("petsplus:tamed", this.tamed);
        component.setStateData("petsplus:sitting", this.sitting);
        component.setStateData("petsplus:owner_uuid", this.ownerUuid != null ? this.ownerUuid.toString() : "");
    }

    @Nullable
    private PlayerEntity resolveOwner(ServerWorld serverWorld, UUID ownerUuid) {
        PlayerEntity player = serverWorld.getPlayerByUuid(ownerUuid);
        if (player == null && serverWorld.getServer() != null) {
            player = serverWorld.getServer().getPlayerManager().getPlayer(ownerUuid);
        }
        if (player != null && (!player.isAlive() || player.isRemoved())) {
            return null;
        }
        return player;
    }
}


