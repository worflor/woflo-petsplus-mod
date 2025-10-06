package woflo.petsplus.state.modules.impl;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.modules.OwnerModule;

import java.util.UUID;

/**
 * Default implementation of OwnerModule that manages pet ownership state
 * including owner references, UUIDs, and crouch cuddle interactions.
 */
public class DefaultOwnerModule implements OwnerModule {
    private PetComponent parent;
    private PlayerEntity ownerCache;
    private UUID ownerUuid;
    private UUID crouchCuddleOwnerId;
    private long crouchCuddleExpiryTick;

    @Override
    public void onAttach(PetComponent parent) {
        this.parent = parent;
    }

    @Override
    @Nullable
    public PlayerEntity getOwner(ServerWorld world) {
        // Clear cache if owner was removed
        if (ownerCache != null && ownerCache.isRemoved()) {
            ownerCache = null;
        }
        
        // Try to resolve owner from UUID if cache is empty
        if (ownerCache == null && ownerUuid != null) {
            PlayerEntity resolved = world.getPlayerByUuid(ownerUuid);
            if (resolved != null) {
                ownerCache = resolved;
            }
        }
        
        return ownerCache;
    }

    @Override
    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    public void setOwner(@Nullable PlayerEntity owner) {
        UUID newOwnerUuid = owner != null ? owner.getUuid() : null;
        setOwnerUuid(newOwnerUuid);
        this.ownerCache = owner;
    }

    @Override
    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        UUID previousOwnerUuid = this.ownerUuid;
        this.ownerUuid = ownerUuid;
        
        // Clear cache if owner changed
        if (previousOwnerUuid != ownerUuid) {
            this.ownerCache = null;
        }
        
        // Notify parent about owner change (for scheduling invalidation, etc.)
        if (parent != null && previousOwnerUuid != ownerUuid) {
            parent.onOwnerChanged(previousOwnerUuid, ownerUuid);
        }
    }

    @Override
    public boolean isOwnedBy(PlayerEntity player) {
        if (player == null || ownerUuid == null) {
            return false;
        }
        return ownerUuid.equals(player.getUuid());
    }

    @Override
    public void recordCrouchCuddle(UUID ownerUuid, long expiryTick) {
        this.crouchCuddleOwnerId = ownerUuid;
        this.crouchCuddleExpiryTick = expiryTick;
    }

    @Override
    public boolean hasActiveCrouchCuddle(long currentTick, @Nullable UUID ownerUuid) {
        if (crouchCuddleOwnerId == null || currentTick >= crouchCuddleExpiryTick) {
            return false;
        }
        if (ownerUuid != null && !crouchCuddleOwnerId.equals(ownerUuid)) {
            return false;
        }
        return true;
    }

    @Nullable
    public UUID getCrouchCuddleOwnerId() {
        return crouchCuddleOwnerId;
    }

    @Override
    public void clearCrouchCuddle(UUID ownerId) {
        if (crouchCuddleOwnerId != null && crouchCuddleOwnerId.equals(ownerId)) {
            this.crouchCuddleOwnerId = null;
            this.crouchCuddleExpiryTick = 0;
        }
    }

    @Override
    public void clearCrouchCuddle() {
        this.crouchCuddleOwnerId = null;
        this.crouchCuddleExpiryTick = 0;
    }

    @Override
    public Data toData() {
        return new Data(
            ownerUuid,
            crouchCuddleOwnerId,
            crouchCuddleExpiryTick
        );
    }

    @Override
    public void fromData(Data data) {
        this.ownerUuid = data.ownerUuid();
        this.ownerCache = null; // Will be resolved on first getOwner() call
        this.crouchCuddleOwnerId = data.crouchCuddleOwnerId();
        this.crouchCuddleExpiryTick = data.crouchCuddleExpiryTick();
    }
}
