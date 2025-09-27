package woflo.petsplus.mixin;

import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.PetBreedingHandler;

/**
 * Captures vanilla breeding completions so Pets+ can enrich the newborn pet.
 */
@Mixin(AnimalEntity.class)
public abstract class AnimalEntityMixin {

    @Inject(method = "breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;Lnet/minecraft/entity/passive/PassiveEntity;)V", at = @At("RETURN"))
    private void petsplus$onBreed(ServerWorld world, AnimalEntity mate,
                                  PassiveEntity baby, CallbackInfo ci) {
        if (baby instanceof AnimalEntity) {
            PetBreedingHandler.onPetBred((AnimalEntity) (Object) this, mate, (AnimalEntity) baby);
        }
    }
}
