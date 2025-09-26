package woflo.petsplus.api.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Bridge abstraction that exposes the minimal tameable contract Pets+ relies on.
 *
 * Vanilla {@link net.minecraft.entity.passive.TameableEntity} already satisfies these
 * methods, and mixins can provide lightweight state for non-vanilla tameables like
 * frogs and rabbits without needing to inherit the vanilla base class.
 */
public interface PetsplusTameable {
    boolean petsplus$isTamed();

    void petsplus$setTamed(boolean tamed);

    @Nullable
    UUID petsplus$getOwnerUuid();

    void petsplus$setOwnerUuid(@Nullable UUID ownerUuid);

    @Nullable
    LivingEntity petsplus$getOwner();

    void petsplus$setOwner(@Nullable LivingEntity owner);

    boolean petsplus$isSitting();

    void petsplus$setSitting(boolean sitting);

    default boolean petsplus$isOwnedBy(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        LivingEntity owner = petsplus$getOwner();
        return owner != null && owner.getUuid().equals(player.getUuid());
    }
}
