package woflo.petsplus.mixin;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.PetBreedingHandler;
import woflo.petsplus.events.RelationshipEventHandler;
import woflo.petsplus.state.PetComponent;

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
    
    @Inject(method = "lovePlayer(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"))
    private void petsplus$onFed(PlayerEntity player, CallbackInfo ci) {
        AnimalEntity animal = (AnimalEntity) (Object) this;
        if (animal instanceof MobEntity mob && player != null && !animal.getEntityWorld().isClient()) {
            PetComponent petComponent = PetComponent.get(mob);
            if (petComponent != null) {
                // Track feeding interaction for relationship system
                RelationshipEventHandler.onPetFed(mob, player);
            } else {
                // Wild animal being fed - notify nearby pets to learn
                if (mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                    java.util.List<MobEntity> nearbyPets = serverWorld.getEntitiesByClass(
                        MobEntity.class,
                        mob.getBoundingBox().expand(16.0),
                        nearbyMob -> {
                            woflo.petsplus.state.PetComponent pc = woflo.petsplus.state.PetComponent.get(nearbyMob);
                            if (pc == null) return false;
                            return pc.isOwnedBy(player) && nearbyMob.isAlive();
                        }
                    );
                    for (MobEntity pet : nearbyPets) {
                        woflo.petsplus.events.RelationshipEventHandler.onPetObservedOwnerFeed(pet, mob);
                    }
                }
            }
        }
    }
}


