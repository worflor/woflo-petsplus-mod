package woflo.petsplus.api;

import net.minecraft.entity.player.PlayerEntity;

public interface HungerManagerAccess {
    void petsplus$setOwner(PlayerEntity player);

    PlayerEntity petsplus$getOwner();
}
