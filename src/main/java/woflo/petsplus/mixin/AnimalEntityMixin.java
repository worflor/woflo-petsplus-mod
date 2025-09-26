package woflo.petsplus.mixin;

import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import woflo.petsplus.events.PetBreedingHandler;

import java.util.Optional;

/**
 * Captures vanilla breeding completions so Pets+ can enrich the newborn pet.
 */
@Mixin(AnimalEntity.class)
public abstract class AnimalEntityMixin {

    @Inject(method = "breed", at = @At("RETURN"))
    private void petsplus$onBreed(ServerWorld world, AnimalEntity mate,
                                  CallbackInfoReturnable<Optional<AnimalEntity>> cir) {
        Optional<AnimalEntity> child = cir.getReturnValue();
        child.ifPresent(passiveEntity ->
            PetBreedingHandler.onPetBred((AnimalEntity) (Object) this, mate, passiveEntity)
        );
    }
}
