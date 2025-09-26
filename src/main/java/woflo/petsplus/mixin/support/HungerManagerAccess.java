package woflo.petsplus.mixin.support;

import net.minecraft.entity.player.PlayerEntity;

public interface HungerManagerAccess {
    void petsplus$setOwner(PlayerEntity player);

    PlayerEntity petsplus$getOwner();
}
