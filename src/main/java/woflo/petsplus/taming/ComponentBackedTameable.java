package woflo.petsplus.taming;

import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.entity.PetsplusTameable;

/**
 * Default implementation of {@link PetsplusTameable} backed by
 * {@link ComponentBackedTameableBridge}. Mixins that expose tameable
 * behaviour for non-vanilla entities can implement this interface and only
 * provide the bridge instance plus any custom side-effects they want to run
 * when tame or sitting state changes.
 */
public interface ComponentBackedTameable extends PetsplusTameable {

    ComponentBackedTameableBridge petsplus$getBridge();

    default void petsplus$afterTameChange(boolean tamed) {
    }

    default void petsplus$afterSittingChange(boolean sitting) {
    }

    @Override
    default boolean petsplus$isTamed() {
        return petsplus$getBridge().isTamed();
    }

    @Override
    default void petsplus$setTamed(boolean tamed) {
        if (petsplus$getBridge().setTamed(tamed)) {
            petsplus$afterTameChange(tamed);
        }
    }

    @Override
    default @Nullable java.util.UUID petsplus$getOwnerUuid() {
        return petsplus$getBridge().getOwnerUuid();
    }

    @Override
    default void petsplus$setOwnerUuid(@Nullable java.util.UUID ownerUuid) {
        petsplus$getBridge().setOwnerUuid(ownerUuid);
    }

    @Override
    default @Nullable LivingEntity petsplus$getOwner() {
        return petsplus$getBridge().getOwner();
    }

    @Override
    default void petsplus$setOwner(@Nullable LivingEntity owner) {
        petsplus$getBridge().setOwner(owner);
    }

    @Override
    default boolean petsplus$isSitting() {
        return petsplus$getBridge().isSitting();
    }

    @Override
    default void petsplus$setSitting(boolean sitting) {
        if (petsplus$getBridge().setSitting(sitting)) {
            petsplus$afterSittingChange(sitting);
        }
    }
}
