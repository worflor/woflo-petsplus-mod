package woflo.petsplus.mixin;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.taming.SittingOffsetTracker;

import java.util.UUID;

/**
 * Mixin to persist PetComponent data when entities are saved/loaded.
 * This ensures pet ownership data survives world saves, loads, and server restarts.
 */
@Mixin(MobEntity.class)
public class MobEntityDataMixin {
    
    @Inject(method = "writeCustomData", at = @At("HEAD"))
    private void savePetsPlusData(WriteView view, CallbackInfo ci) {
        MobEntity entity = (MobEntity) (Object) this;
        PetComponent component = PetComponent.getOrCreateForPersistence(entity);

        WriteView petsPlusData = view.get("PetsPlusData");
        if (petsPlusData != null) {
            Identifier roleId = component.getRoleId();
            if (roleId != null) {
                petsPlusData.putString("role", roleId.toString());
            } else {
                petsPlusData.remove("role");
            }
            petsPlusData.putString("petUuid", entity.getUuidAsString());
        }

        component.saveToEntity();
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void loadPetsPlusData(ReadView view, CallbackInfo ci) {
        MobEntity entity = (MobEntity) (Object) this;

        PetComponent component = PetComponent.getOrCreateForPersistence(entity);
        component.loadFromEntity();

        view.getOptionalReadView("PetsPlusData").ifPresent(data -> {
            data.getOptionalString("role").ifPresent(roleString -> {
                Identifier parsed = Identifier.tryParse(roleString);
                if (parsed != null) {
                    component.setRoleId(parsed);
                }
            });
        });

        // If the component did not carry an assigned role (e.g., due to shoulder
        // perch serialization path), restore the last remembered role for this UUID.
        if (component.getAssignedRoleId() == null) {
            var remembered = woflo.petsplus.state.PetRoleMemory.recall(entity.getUuid());
            if (remembered != null) {
                component.setRoleId(remembered);
            }
        }

        boolean sittingOffsetApplied = component.getStateData("petsplus:sitting_offset", Boolean.class, false);
        if (entity instanceof SittingOffsetTracker tracker) {
            tracker.petsplus$setSittingOffsetApplied(sittingOffsetApplied);
        }

        if (entity instanceof PetsplusTameable tameable && !(entity instanceof TameableEntity)) {
            boolean tamed = component.getStateData("petsplus:tamed", Boolean.class, false);
            tameable.petsplus$setTamed(tamed);

            boolean sitting = component.getStateData("petsplus:sitting", Boolean.class, false);
            tameable.petsplus$setSitting(sitting);

            String ownerId = component.getStateData("petsplus:owner_uuid", String.class, "");
            if (ownerId != null && !ownerId.isEmpty()) {
                try {
                    tameable.petsplus$setOwnerUuid(UUID.fromString(ownerId));
                } catch (IllegalArgumentException ignored) {
                    tameable.petsplus$setOwnerUuid(null);
                }
            } else {
                tameable.petsplus$setOwnerUuid(null);
            }
        }
        
        // For vanilla TameableEntity, sync owner from vanilla to PetComponent
        // This ensures owner data is consistent after vanilla loads its NBT
        if (entity instanceof TameableEntity tameableEntity) {
            net.minecraft.entity.LivingEntity vanillaOwner = tameableEntity.getOwner();
            if (vanillaOwner instanceof net.minecraft.entity.player.PlayerEntity player) {
                // Sync vanilla owner to PetComponent if they differ
                java.util.UUID petComponentOwner = component.getOwnerUuid();
                if (petComponentOwner == null || !petComponentOwner.equals(player.getUuid())) {
                    component.setOwner(player);
                    component.setOwnerUuid(player.getUuid());
                }
            }
        }

        // Ensure the component is properly registered after load
        PetComponent.set(entity, component);
    }
}
