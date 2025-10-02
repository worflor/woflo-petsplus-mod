package woflo.petsplus.advancement;

import com.mojang.serialization.Codec;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import woflo.petsplus.state.PetComponent;

/**
 * Stores which pet earned the "Bestest Friends Forevererer" advancement for each player.
 * Used so that the matching "Or Not" advancement can trigger specifically when that pet dies.
 */
public class BestFriendTracker extends PersistentState {
    private static final String STORAGE_KEY = "petsplus_best_friend_tracker";

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<BestFriendTracker> CODEC = Codec.unboundedMap(UUID_CODEC, UUID_CODEC)
        .xmap(BestFriendTracker::fromMap, BestFriendTracker::toMap);
    private static final PersistentStateType<BestFriendTracker> TYPE = new PersistentStateType<>(
        STORAGE_KEY,
        BestFriendTracker::new,
        CODEC,
        null
    );

    private final Map<UUID, UUID> trackedBestFriends = new HashMap<>();

    private BestFriendTracker() {
    }

    public static BestFriendTracker get(ServerWorld world) {
        if (world == null) {
            throw new IllegalArgumentException("ServerWorld cannot be null when accessing BestFriendTracker");
        }

        MinecraftServer server = world.getServer();
        if (server == null) {
            throw new IllegalStateException("Cannot access BestFriendTracker before the server is ready");
        }

        ServerWorld storageWorld = server.getOverworld();
        PersistentStateManager manager = storageWorld.getPersistentStateManager();
        return manager.getOrCreate(TYPE);
    }

    private static BestFriendTracker fromMap(Map<UUID, UUID> map) {
        BestFriendTracker tracker = new BestFriendTracker();
        tracker.trackedBestFriends.putAll(map);
        return tracker;
    }

    private Map<UUID, UUID> toMap() {
        return new HashMap<>(trackedBestFriends);
    }

    /**
     * Registers the pet that just reached level 30 for this owner if none has been stored yet.
     *
     * @return {@code true} if the pet was stored as the new best friend.
     */
    public boolean registerBestFriend(ServerWorld world, UUID ownerUuid, UUID petUuid) {
        if (world == null || ownerUuid == null || petUuid == null) {
            return false;
        }

        UUID trackedPet = trackedBestFriends.get(ownerUuid);
        if (trackedPet != null) {
            if (trackedPet.equals(petUuid)) {
                return false;
            }

            if (isTrackedPetStillOwnedByPlayer(world, ownerUuid, trackedPet)) {
                return false;
            }
        }

        trackedBestFriends.put(ownerUuid, petUuid);
        markDirty();
        return true;
    }

    private boolean isTrackedPetStillOwnedByPlayer(ServerWorld world, UUID ownerUuid, UUID petUuid) {
        MinecraftServer server = world.getServer();
        if (server == null) {
            return true;
        }

        for (ServerWorld serverWorld : server.getWorlds()) {
            Entity entity = serverWorld.getEntity(petUuid);
            if (!(entity instanceof MobEntity mob)) {
                continue;
            }

            if (!mob.isAlive()) {
                return false;
            }

            PetComponent component = PetComponent.get(mob);
            if (component == null) {
                return false;
            }

            UUID currentOwner = component.getOwnerUuid();
            return ownerUuid.equals(currentOwner);
        }

        return false;
    }

    /**
     * Checks if the provided pet UUID matches the stored best friend for the owner.
     */
    public boolean isBestFriend(UUID ownerUuid, UUID petUuid) {
        if (ownerUuid == null || petUuid == null) {
            return false;
        }
        UUID stored = trackedBestFriends.get(ownerUuid);
        return stored != null && stored.equals(petUuid);
    }

    /**
     * Clears the stored best friend entry if it matches the provided pet UUID.
     * This allows a future pet to become the tracked best friend again.
     */
    public boolean clearIfBestFriend(UUID ownerUuid, UUID petUuid) {
        if (!isBestFriend(ownerUuid, petUuid)) {
            return false;
        }
        trackedBestFriends.remove(ownerUuid);
        markDirty();
        return true;
    }
}
