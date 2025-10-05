package woflo.petsplus.state.modules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;

public interface OwnerModule extends DataBackedModule<OwnerModule.Data> {
    @Nullable PlayerEntity getOwner(ServerWorld world);
    @Nullable UUID getOwnerUuid();
    void setOwner(@Nullable PlayerEntity owner);
    void setOwnerUuid(@Nullable UUID ownerUuid);
    boolean isOwnedBy(PlayerEntity player);
    void recordCrouchCuddle(UUID ownerUuid, long expiryTick);
    boolean hasActiveCrouchCuddle(long currentTick, @Nullable UUID ownerUuid);
    @Nullable UUID getCrouchCuddleOwnerId();
    void clearCrouchCuddle(UUID ownerId);

    record Data(@Nullable UUID ownerUuid, @Nullable UUID crouchCuddleOwnerId, long crouchCuddleExpiryTick) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Uuids.CODEC.optionalFieldOf("ownerUuid").forGetter(d -> java.util.Optional.ofNullable(d.ownerUuid())),
                Uuids.CODEC.optionalFieldOf("crouchCuddleOwnerId").forGetter(d -> java.util.Optional.ofNullable(d.crouchCuddleOwnerId())),
                Codec.LONG.optionalFieldOf("crouchCuddleExpiryTick", 0L).forGetter(Data::crouchCuddleExpiryTick)
            ).apply(instance, (owner, cuddle, expiry) -> new Data(owner.orElse(null), cuddle.orElse(null), expiry))
        );
    }
}
