package woflo.petsplus.effects;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.state.coordination.PetSwarmIndex;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class AuraNeighborhoodIndex {

    private static final double MAX_RADIUS = 48.0D;
    private static final double MAX_RADIUS_SQ = MAX_RADIUS * MAX_RADIUS;
    private static final int REFRESH_INTERVAL = 10;

    interface PlayerSupplier {
        List<ServerPlayerEntity> query(ServerPlayerEntity owner, double radius, double squaredRadius);
    }

    private static final NeighborhoodSnapshot EMPTY = new NeighborhoodSnapshot(List.of(), List.of(), 0L);

    private final PetSwarmIndex swarmIndex;
    private final Map<UUID, CachedNeighborhood> cache = new ConcurrentHashMap<>();

    AuraNeighborhoodIndex(PetSwarmIndex swarmIndex) {
        this.swarmIndex = swarmIndex;
    }

    NeighborhoodSnapshot snapshot(ServerPlayerEntity owner, long worldTime, PlayerSupplier players) {
        if (owner == null || owner.isRemoved() || !(owner.getEntityWorld() instanceof ServerWorld world)) {
            return EMPTY;
        }
        UUID ownerId = owner.getUuid();
        if (ownerId == null) {
            return EMPTY;
        }
        CachedNeighborhood cached = cache.computeIfAbsent(ownerId, ignored -> new CachedNeighborhood());
        if (needsRefresh(cached, world, worldTime)) {
            cached.world = world;
            cached.lastTick = worldTime;
            cached.pets = snapshotPets(ownerId);
            cached.players = snapshotPlayers(owner, players);
            cached.snapshot = cached.pets.isEmpty() && cached.players.isEmpty()
                ? EMPTY
                : new NeighborhoodSnapshot(cached.pets, cached.players, cached.lastTick);
        }
        return cached.snapshot;
    }

    void remove(UUID ownerId) {
        if (ownerId != null) {
            cache.remove(ownerId);
        }
    }

    private boolean needsRefresh(CachedNeighborhood cached, ServerWorld world, long worldTime) {
        if (cached.world != world) {
            return true;
        }
        return worldTime - cached.lastTick >= REFRESH_INTERVAL;
    }

    private List<PetSwarmIndex.SwarmEntry> snapshotPets(UUID ownerId) {
        List<PetSwarmIndex.SwarmEntry> entries = swarmIndex.snapshotOwner(ownerId);
        if (entries.isEmpty()) {
            return List.of();
        }
        return entries;
    }

    private List<ServerPlayerEntity> snapshotPlayers(ServerPlayerEntity owner, PlayerSupplier players) {
        List<ServerPlayerEntity> nearby = players.query(owner, MAX_RADIUS, MAX_RADIUS_SQ);
        if (nearby.isEmpty()) {
            return List.of();
        }
        return List.copyOf(nearby);
    }

    static final class NeighborhoodSnapshot {
        private final List<PetSwarmIndex.SwarmEntry> pets;
        private final List<ServerPlayerEntity> players;
        private final long tick;

        NeighborhoodSnapshot(List<PetSwarmIndex.SwarmEntry> pets, List<ServerPlayerEntity> players, long tick) {
            this.pets = pets;
            this.players = players;
            this.tick = tick;
        }

        public List<PetSwarmIndex.SwarmEntry> pets() {
            return pets;
        }

        public List<ServerPlayerEntity> players() {
            return players;
        }

        public long tick() {
            return tick;
        }

        public boolean isEmpty() {
            return pets.isEmpty() && players.isEmpty();
        }
    }

    private static final class CachedNeighborhood {
        private ServerWorld world;
        private long lastTick;
        private List<PetSwarmIndex.SwarmEntry> pets = List.of();
        private List<ServerPlayerEntity> players = List.of();
        private NeighborhoodSnapshot snapshot = EMPTY;
    }
}
