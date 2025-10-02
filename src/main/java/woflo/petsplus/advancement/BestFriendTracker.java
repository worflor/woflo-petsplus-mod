package woflo.petsplus.advancement;

import com.mojang.serialization.Codec;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
        ServerWorld storageWorld = world.getServer().getOverworld();
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
    public boolean registerBestFriend(UUID ownerUuid, UUID petUuid) {
        if (ownerUuid == null || petUuid == null) {
            return false;
        }
        if (trackedBestFriends.containsKey(ownerUuid)) {
            return false;
        }
        trackedBestFriends.put(ownerUuid, petUuid);
        markDirty();
        return true;
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
