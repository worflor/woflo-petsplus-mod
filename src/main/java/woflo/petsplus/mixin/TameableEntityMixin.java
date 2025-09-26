package woflo.petsplus.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.events.PetDetectionHandler;
import woflo.petsplus.state.PetComponent;

import java.util.UUID;

/**
 * Mixin to detect when entities are tamed and trigger pet registration.
 */
@Mixin(TameableEntity.class)
public abstract class TameableEntityMixin implements PetsplusTameable {

    private TameableEntity petsplus$self() {
        return (TameableEntity) (Object) this;
    }

    @Unique
    private PetComponent petsplus$getComponent() {
        return PetComponent.getOrCreate(petsplus$self());
    }
    
    @Inject(method = "setOwner", at = @At("TAIL"))
    private void onSetOwner(LivingEntity owner, CallbackInfo ci) {
        TameableEntity tameable = (TameableEntity) (Object) this;

        // Only trigger if the entity is now tamed and has a player owner
        if (tameable.isTamed() && owner instanceof PlayerEntity player) {
            PetDetectionHandler.onEntityTamed(tameable, player);
        }
    }

    @Override
    public boolean petsplus$isTamed() {
        return petsplus$self().isTamed();
    }

    @Override
    public void petsplus$setTamed(boolean tamed) {
        petsplus$self().setTamed(tamed, true);
        PetComponent component = petsplus$getComponent();
        component.setStateData("petsplus:tamed", tamed);
        if (!tamed) {
            component.setOwner(null);
            component.setStateData("petsplus:sitting", false);
            component.setStateData("petsplus:owner_uuid", "");
        }
    }

    @Override
    public @Nullable UUID petsplus$getOwnerUuid() {
        LivingEntity owner = petsplus$self().getOwner();
        return owner != null ? owner.getUuid() : null;
    }

    @Override
    public void petsplus$setOwnerUuid(@Nullable UUID ownerUuid) {
        if (ownerUuid == null) {
            petsplus$self().setOwner((LivingEntity) null);
            PetComponent component = petsplus$getComponent();
            component.setOwner(null);
            component.setOwnerUuid(null);
            component.setStateData("petsplus:owner_uuid", "");
            return;
        }
        PetComponent component = petsplus$getComponent();
        component.setStateData("petsplus:owner_uuid", ownerUuid.toString());
        component.setOwnerUuid(ownerUuid);
        if (petsplus$self().getWorld() instanceof ServerWorld serverWorld) {
            PlayerEntity player = serverWorld.getPlayerByUuid(ownerUuid);
            if (player != null) {
                petsplus$self().setOwner(player);
                component.setOwner(player);
            }
        }
    }

    @Override
    public @Nullable LivingEntity petsplus$getOwner() {
        return petsplus$self().getOwner();
    }

    @Override
    public void petsplus$setOwner(@Nullable LivingEntity owner) {
        petsplus$self().setOwner(owner);
        PetComponent component = petsplus$getComponent();
        if (owner instanceof PlayerEntity player) {
            component.setOwner(player);
            component.setOwnerUuid(player.getUuid());
            component.setStateData("petsplus:owner_uuid", player.getUuidAsString());
        } else {
            component.setOwner(null);
            component.setOwnerUuid(null);
            component.setStateData("petsplus:owner_uuid", "");
        }
    }

    @Override
    public boolean petsplus$isSitting() {
        return petsplus$self().isSitting();
    }

    @Override
    public void petsplus$setSitting(boolean sitting) {
        petsplus$self().setSitting(sitting);
        petsplus$getComponent().setStateData("petsplus:sitting", sitting);
    }
}