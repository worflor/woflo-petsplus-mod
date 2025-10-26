package woflo.petsplus.effects;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.processing.OwnerSpatialResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves aura targets by reusing the pet swarm index and cached owner proximity data
 * so repeated aura pulses avoid issuing duplicate world scans.
 */
public final class AuraTargetResolver {

    private final PetSwarmIndex swarmIndex;
    private final PlayerProximityIndex playerIndex = new PlayerProximityIndex();
    private final AuraNeighborhoodIndex neighborhoodIndex;

    public AuraTargetResolver(PetSwarmIndex swarmIndex) {
        this.swarmIndex = swarmIndex;
        this.neighborhoodIndex = new AuraNeighborhoodIndex(swarmIndex);
    }

    public void handleOwnerTick(ServerPlayerEntity owner) {
        playerIndex.update(owner);
        if (owner != null && owner.getEntityWorld() instanceof ServerWorld world) {
            neighborhoodIndex.snapshot(owner, world.getTime(), this::queryPlayers);
        }
    }

    public void handleOwnerRemoval(ServerPlayerEntity owner) {
        playerIndex.remove(owner);
        if (owner != null) {
            neighborhoodIndex.remove(owner.getUuid());
        }
    }

    public void handleOwnerRemoval(UUID ownerId) {
        playerIndex.remove(ownerId);
        neighborhoodIndex.remove(ownerId);
    }

    public List<LivingEntity> resolveTargets(ServerWorld world, MobEntity pet, PetComponent component,
                                             ServerPlayerEntity owner, double radius, PetRoleType.AuraTarget target) {
        return resolveTargets(world, pet, component, owner, radius, target, null, null);
    }

    public List<LivingEntity> resolveTargets(ServerWorld world, MobEntity pet, PetComponent component,
                                             ServerPlayerEntity owner, double radius, PetRoleType.AuraTarget target,
                                             @Nullable List<PetSwarmIndex.SwarmEntry> swarmSnapshot) {
        return resolveTargets(world, pet, component, owner, radius, target, swarmSnapshot, null);
    }

    public List<LivingEntity> resolveTargets(ServerWorld world, MobEntity pet, PetComponent component,
                                             ServerPlayerEntity owner, double radius, PetRoleType.AuraTarget target,
                                             @Nullable List<PetSwarmIndex.SwarmEntry> swarmSnapshot,
                                             @Nullable OwnerSpatialResult spatialResult) {
        if (target == null) {
            return List.of();
        }

        double squaredRadius = radius <= 0 ? 0 : radius * radius;
        Vec3d center = pet.getEntityPos();
        Set<LivingEntity> resolved = new ObjectLinkedOpenHashSet<>();

        switch (target) {
            case PET -> resolved.add(pet);
            case OWNER -> addOwnerIfInRange(resolved, owner, center, squaredRadius);
            case OWNER_AND_PET -> {
                addOwnerIfInRange(resolved, owner, center, squaredRadius);
                resolved.add(pet);
            }
            case OWNER_AND_ALLIES -> {
                addOwnerIfInRange(resolved, owner, center, squaredRadius);
                resolved.addAll(collectNearbyAllies(world, pet, component, owner, center, radius, squaredRadius, swarmSnapshot, spatialResult));
            }
            case NEARBY_PLAYERS -> resolved.addAll(collectNearbyPlayers(owner, center, radius, squaredRadius));
            case NEARBY_ALLIES -> resolved.addAll(collectNearbyAllies(world, pet, component, owner, center, radius, squaredRadius, swarmSnapshot, spatialResult));
        }

        resolved.remove(pet);
        if (target == PetRoleType.AuraTarget.PET || target == PetRoleType.AuraTarget.OWNER_AND_PET) {
            resolved.add(pet);
        }

        return new ArrayList<>(resolved);
    }

    public List<LivingEntity> resolveUsingNeighborhood(ServerWorld world, MobEntity pet, PetComponent component,
                                                       ServerPlayerEntity owner, double radius,
                                                       PetRoleType.AuraTarget target) {
        if (world == null || pet == null || owner == null || target == null) {
            return List.of();
        }

        AuraNeighborhoodIndex.NeighborhoodSnapshot snapshot =
            neighborhoodIndex.snapshot(owner, world.getTime(), this::queryPlayers);
        if (snapshot.isEmpty()) {
            return List.of();
        }

        double r = Math.max(0.0D, radius);
        double squaredRadius = r <= 0 ? 0 : r * r;
        Vec3d center = pet.getEntityPos();
        Set<LivingEntity> resolved = new ObjectLinkedOpenHashSet<>();

        switch (target) {
            case PET -> resolved.add(pet);
            case OWNER -> addOwnerIfInRange(resolved, owner, center, squaredRadius);
            case OWNER_AND_PET -> {
                addOwnerIfInRange(resolved, owner, center, squaredRadius);
                resolved.add(pet);
            }
            case OWNER_AND_ALLIES -> {
                addOwnerIfInRange(resolved, owner, center, squaredRadius);
                addAlliesFromSnapshot(resolved, snapshot, pet, center, squaredRadius, owner);
            }
            case NEARBY_PLAYERS -> {
                addOwnerIfInRange(resolved, owner, center, squaredRadius);
                addPlayersFromSnapshot(resolved, snapshot.players(), center, squaredRadius, owner);
            }
            case NEARBY_ALLIES -> addAlliesFromSnapshot(resolved, snapshot, pet, center, squaredRadius, owner);
        }

        resolved.remove(pet);
        if (target == PetRoleType.AuraTarget.PET || target == PetRoleType.AuraTarget.OWNER_AND_PET) {
            resolved.add(pet);
        }

        if (resolved.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(resolved);
    }

    private void addOwnerIfInRange(Set<LivingEntity> resolved, ServerPlayerEntity owner, Vec3d center, double squaredRadius) {
        if (owner == null || owner.isRemoved() || !owner.isAlive()) {
            return;
        }
        if (squaredRadius == 0 || owner.getEntityPos().squaredDistanceTo(center) <= squaredRadius) {
            resolved.add(owner);
        }
    }

    private void addAlliesFromSnapshot(Set<LivingEntity> resolved,
                                       AuraNeighborhoodIndex.NeighborhoodSnapshot snapshot,
                                       MobEntity pet,
                                       Vec3d center,
                                       double squaredRadius,
                                       ServerPlayerEntity owner) {
        if (snapshot == null) {
            return;
        }
        for (PetSwarmIndex.SwarmEntry entry : snapshot.pets()) {
            if (entry == null) {
                continue;
            }
            MobEntity other = entry.pet();
            if (other == null || other == pet || !other.isAlive()) {
                continue;
            }
            double dx = entry.x() - center.x;
            double dy = entry.y() - center.y;
            double dz = entry.z() - center.z;
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (squaredRadius == 0 || distSq <= squaredRadius) {
                resolved.add(other);
            }
        }
        addPlayersFromSnapshot(resolved, snapshot.players(), center, squaredRadius, owner);
    }

    private void addPlayersFromSnapshot(Set<LivingEntity> resolved,
                                        List<ServerPlayerEntity> players,
                                        Vec3d center,
                                        double squaredRadius,
                                        @Nullable ServerPlayerEntity owner) {
        if (players == null || players.isEmpty()) {
            return;
        }
        for (ServerPlayerEntity player : players) {
            if (player == null || player.isRemoved() || !player.isAlive()) {
                continue;
            }
            if (owner != null && player.getUuid().equals(owner.getUuid())) {
                continue;
            }
            if (squaredRadius == 0 || player.getEntityPos().squaredDistanceTo(center) <= squaredRadius) {
                resolved.add(player);
            }
        }
    }

    private List<ServerPlayerEntity> queryPlayers(ServerPlayerEntity owner, double radius, double squaredRadius) {
        if (owner == null) {
            return List.of();
        }
        Vec3d center = owner.getEntityPos();
        return playerIndex.playersInRange(owner, center, radius, squaredRadius);
    }

    private List<LivingEntity> collectNearbyAllies(ServerWorld world, MobEntity pet, PetComponent component,
                                                   ServerPlayerEntity owner, Vec3d center, double radius, double squaredRadius,
                                                   @Nullable List<PetSwarmIndex.SwarmEntry> swarmSnapshot,
                                                   @Nullable OwnerSpatialResult spatialResult) {
        Set<LivingEntity> allies = new ObjectLinkedOpenHashSet<>();
        if (owner != null && (squaredRadius == 0 || owner.getEntityPos().squaredDistanceTo(center) <= squaredRadius)) {
            allies.add(owner);
        }

        boolean populatedFromSpatial = false;
        if (spatialResult != null && component != null) {
            UUID ownerId = component.getOwnerUuid();
            MobEntity basePet = pet;
            UUID baseId = basePet != null ? basePet.getUuid() : null;
            if (ownerId != null && baseId != null) {
                List<UUID> neighborIds = spatialResult.neighborsWithin(baseId, radius <= 0.0D ? Double.MAX_VALUE : radius, Integer.MAX_VALUE);
                if (!neighborIds.isEmpty()) {
                    populatedFromSpatial = true;
                    for (UUID id : neighborIds) {
                        PetSwarmIndex.SwarmEntry entry = swarmIndex.findEntry(ownerId, id);
                        if (entry == null) {
                            continue;
                        }
                        MobEntity other = entry.pet();
                        if (other == null || other == pet || !other.isAlive()) {
                            continue;
                        }
                        double dx = entry.x() - center.x;
                        double dy = entry.y() - center.y;
                        double dz = entry.z() - center.z;
                        double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                        if (squaredRadius == 0 || distSq <= squaredRadius) {
                            allies.add(other);
                        }
                    }
                }
            }
        }

        if (!populatedFromSpatial) {
            if (swarmSnapshot != null && !swarmSnapshot.isEmpty()) {
                for (PetSwarmIndex.SwarmEntry entry : swarmSnapshot) {
                    MobEntity other = entry.pet();
                    if (other == null || other == pet || !other.isAlive()) {
                        continue;
                    }
                    double dx = entry.x() - center.x;
                    double dy = entry.y() - center.y;
                    double dz = entry.z() - center.z;
                    double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                    if (squaredRadius == 0 || distSq <= squaredRadius) {
                        allies.add(other);
                    }
                }
            } else {
                swarmIndex.forEachNeighbor(pet, component, radius, (entry, distSq) -> {
                    MobEntity other = entry.pet();
                    if (other != pet && other.isAlive() && (squaredRadius == 0 || distSq <= squaredRadius)) {
                        allies.add(other);
                    }
                });
            }
        }

        List<ServerPlayerEntity> nearbyPlayers = collectNearbyPlayers(owner, center, radius, squaredRadius);
        for (ServerPlayerEntity player : nearbyPlayers) {
            if (player != null) {
                allies.add(player);
            }
        }
        return new ArrayList<>(allies);
    }

    private List<ServerPlayerEntity> collectNearbyPlayers(ServerPlayerEntity owner, Vec3d center, double radius, double squaredRadius) {
        if (owner == null) {
            return List.of();
        }
        List<ServerPlayerEntity> players = playerIndex.playersInRange(owner, center, radius, squaredRadius);
        if (players.isEmpty()) {
            return List.of();
        }
        return players;
    }

    private static final class PlayerProximityIndex {
        private static final double MAX_RADIUS = 64.0;
        private static final int CELL_SIZE = 16;
        private static final double INV_CELL_SIZE = 1.0D / CELL_SIZE;

        private final Map<UUID, TrackedPlayer> trackedPlayers = new ConcurrentHashMap<>();
        private final Map<ServerWorld, WorldIndex> worldIndexes = new ConcurrentHashMap<>();

        public void update(ServerPlayerEntity player) {
            if (player == null) {
                return;
            }
            if (player.isRemoved()) {
                remove(player);
                return;
            }
            if (!(player.getEntityWorld() instanceof ServerWorld world)) {
                remove(player);
                return;
            }

            WorldIndex index = worldIndexes.computeIfAbsent(world, w -> new WorldIndex());
            trackedPlayers.compute(player.getUuid(), (uuid, existing) -> {
                if (existing == null) {
                    return new TrackedPlayer(player, world, index);
                }
                existing.refresh(player, world, index);
                return existing;
            });
        }

        public void remove(ServerPlayerEntity player) {
            if (player == null) {
                return;
            }
            remove(player.getUuid());
        }

        public void remove(UUID playerId) {
            if (playerId == null) {
                return;
            }
            TrackedPlayer tracked = trackedPlayers.remove(playerId);
            if (tracked != null) {
                tracked.detach();
            }
        }

        public List<ServerPlayerEntity> playersInRange(ServerPlayerEntity owner, Vec3d center, double radius, double squaredRadius) {
            if (owner == null) {
                return List.of();
            }
            if (!(owner.getEntityWorld() instanceof ServerWorld world)) {
                return List.of();
            }

            WorldIndex index = worldIndexes.get(world);
            if (index == null) {
                return List.of(owner);
            }

            double effectiveRadius = radius <= 0 ? MAX_RADIUS : Math.min(radius, MAX_RADIUS);
            double effectiveRadiusSq = effectiveRadius * effectiveRadius;
            boolean unlimited = squaredRadius <= 0;
            double queryRadiusSq = unlimited ? effectiveRadiusSq : Math.min(squaredRadius, effectiveRadiusSq);

            List<ServerPlayerEntity> players = index.query(center, effectiveRadius, queryRadiusSq);
            boolean ownerIncluded = false;
            for (int i = 0; i < players.size(); i++) {
                if (players.get(i) == owner) {
                    ownerIncluded = true;
                    break;
                }
            }
            if (!ownerIncluded) {
                double ownerDistanceSq = owner.squaredDistanceTo(center);
                if (unlimited || ownerDistanceSq <= queryRadiusSq) {
                    players.add(owner);
                }
            }
            return players;
        }

        private static long toCellKey(double x, double z) {
            int cellX = MathHelper.floor(x * INV_CELL_SIZE);
            int cellZ = MathHelper.floor(z * INV_CELL_SIZE);
            return toCellKey(cellX, cellZ);
        }

        private static long toCellKey(int cellX, int cellZ) {
            return (((long) cellX) << 32) ^ (cellZ & 0xffffffffL);
        }

        private final class TrackedPlayer {
            private ServerPlayerEntity player;
            private ServerWorld world;
            private WorldIndex index;
            private long cellKey;
            private double x;
            private double y;
            private double z;

            private TrackedPlayer(ServerPlayerEntity player, ServerWorld world, WorldIndex index) {
                this.player = player;
                this.world = world;
                this.index = index;
                this.x = player.getX();
                this.y = player.getY();
                this.z = player.getZ();
                this.cellKey = toCellKey(x, z);
                index.add(this);
            }

            private void refresh(ServerPlayerEntity player, ServerWorld world, WorldIndex targetIndex) {
                this.player = player;
                if (this.world != world) {
                    detach();
                    this.world = world;
                    this.index = targetIndex;
                    this.cellKey = toCellKey(player.getX(), player.getZ());
                    this.index.add(this);
                }
                updatePosition(player.getX(), player.getY(), player.getZ());
            }

            private void updatePosition(double x, double y, double z) {
                this.x = x;
                this.y = y;
                this.z = z;
                long newKey = toCellKey(x, z);
                if (newKey != cellKey && index != null) {
                    index.move(this, newKey);
                    this.cellKey = newKey;
                }
            }

            private void detach() {
                if (index != null) {
                    index.remove(this);
                }
                this.index = null;
                this.world = null;
            }
        }

        private final class WorldIndex {
            private final Long2ReferenceOpenHashMap<ObjectArrayList<TrackedPlayer>> buckets = new Long2ReferenceOpenHashMap<>();
            private final ArrayDeque<ObjectArrayList<TrackedPlayer>> listPool = new ArrayDeque<>();

            private void add(TrackedPlayer tracked) {
                ObjectArrayList<TrackedPlayer> bucket = buckets.get(tracked.cellKey);
                if (bucket == null) {
                    bucket = borrow();
                    buckets.put(tracked.cellKey, bucket);
                }
                bucket.add(tracked);
            }

            private void move(TrackedPlayer tracked, long newKey) {
                remove(tracked);
                tracked.cellKey = newKey;
                add(tracked);
            }

            private ObjectArrayList<TrackedPlayer> borrow() {
                ObjectArrayList<TrackedPlayer> list = listPool.pollFirst();
                return list != null ? list : new ObjectArrayList<>();
            }

            private void recycle(ObjectArrayList<TrackedPlayer> list) {
                if (list == null) {
                    return;
                }
                list.clear();
                listPool.offerLast(list);
            }

            private void remove(TrackedPlayer tracked) {
                ObjectArrayList<TrackedPlayer> bucket = buckets.get(tracked.cellKey);
                if (bucket == null) {
                    return;
                }
                for (int i = 0; i < bucket.size(); i++) {
                    if (bucket.get(i) == tracked) {
                        int last = bucket.size() - 1;
                        if (i != last) {
                            bucket.set(i, bucket.get(last));
                        }
                        bucket.remove(last);
                        break;
                    }
                }
                if (bucket.isEmpty()) {
                    buckets.remove(tracked.cellKey);
                    recycle(bucket);
                }
            }

            private List<ServerPlayerEntity> query(Vec3d center, double radius, double radiusSq) {
                double cx = center.x;
                double cy = center.y;
                double cz = center.z;
                double minX = cx - radius;
                double maxX = cx + radius;
                double minZ = cz - radius;
                double maxZ = cz + radius;
                int minCellX = MathHelper.floor(minX * INV_CELL_SIZE);
                int maxCellX = MathHelper.floor(maxX * INV_CELL_SIZE);
                int minCellZ = MathHelper.floor(minZ * INV_CELL_SIZE);
                int maxCellZ = MathHelper.floor(maxZ * INV_CELL_SIZE);

                ObjectArrayList<ServerPlayerEntity> result = new ObjectArrayList<>();
                final double limitSq = radiusSq <= 0 ? Double.POSITIVE_INFINITY : radiusSq;
                for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                    long baseKey = ((long) cellX) << 32;
                    for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                        ObjectArrayList<TrackedPlayer> bucket = buckets.get(baseKey ^ (cellZ & 0xffffffffL));
                        if (bucket == null) {
                            continue;
                        }
                        for (int i = 0; i < bucket.size(); i++) {
                            TrackedPlayer tracked = bucket.get(i);
                            ServerPlayerEntity candidate = tracked.player;
                            if (candidate == null || candidate.isRemoved() || !candidate.isAlive()) {
                                continue;
                            }
                            double dx = tracked.x - cx;
                            double dy = tracked.y - cy;
                            double dz = tracked.z - cz;
                            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                            if (distSq <= limitSq) {
                                result.add(candidate);
                            }
                        }
                    }
                }
                return result;
            }
        }
    }
}


