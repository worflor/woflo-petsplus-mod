package woflo.petsplus.mixin;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
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
    
    private static final String PETSPLUS_DATA_KEY = "PetsPlusData";
    
    @Inject(method = "writeCustomData", at = @At("TAIL"))
    private void savePetsPlusData(WriteView view, CallbackInfo ci) {
        MobEntity entity = (MobEntity) (Object) this;
        PetComponent component = PetComponent.get(entity);
        
        if (component != null) {
            // Convert PetComponent to NBT and store it in the view
            NbtCompound petData = new NbtCompound();
            component.writeToNbt(petData);
            view.put(PETSPLUS_DATA_KEY, NbtCompound.CODEC, petData);
        }
    }
    
    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void loadPetsPlusData(ReadView view, CallbackInfo ci) {
        MobEntity entity = (MobEntity) (Object) this;
        
        view.read(PETSPLUS_DATA_KEY, NbtCompound.CODEC).ifPresent(petData -> {
            // Create or get existing component
            PetComponent component = PetComponent.getOrCreate(entity);
            component.readFromNbt(petData);

            // Ensure the component is properly registered
            PetComponent.set(entity, component);

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
        });
    }
}