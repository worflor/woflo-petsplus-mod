package woflo.petsplus.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.interaction.OwnerAbilitySignalTracker;

/**
 * Ensures proximity cuddle channels are torn down when a tracked pet despawns.
 */
@Mixin(Entity.class)
public abstract class MobEntityRemoveMixin {

    @Inject(method = "remove(Lnet/minecraft/entity/Entity$RemovalReason;)V", at = @At("TAIL"))
    private void petsplus$onRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        if (!((Object) this instanceof MobEntity mob)) {
            return;
        }

        // Tear down any owner ability channels
        OwnerAbilitySignalTracker.handlePetRemoved(mob);

        // Remember the role for owned pets being removed. This covers
        // shoulder‑perch despawns and any other temporary removal paths
        // where component role may not survive round‑trip.
        var component = woflo.petsplus.state.PetComponent.get(mob);
        if (component != null && component.getOwnerUuid() != null && component.getAssignedRoleId() != null) {
            woflo.petsplus.state.PetRoleMemory.remember(mob.getUuid(), component.getRoleId());
        }
    }
}
