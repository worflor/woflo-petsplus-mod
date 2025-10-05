package woflo.petsplus.effects;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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

    public AuraTargetResolver(PetSwarmIndex swarmIndex) {
        this.swarmIndex = swarmIndex;
    }

    public void handleOwnerTick(ServerPlayerEntity owner) {
        playerIndex.update(owner);
    }

    public void handleOwnerRemoval(ServerPlayerEntity owner) {
        playerIndex.remove(owner);
    }

    public void handleOwnerRemoval(UUID ownerId) {
        playerIndex.remove(ownerId);
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
        Vec3d center = pet.getPos();
        Set<LivingEntity> resolved = new LinkedHashSet<>();

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

    private void addOwnerIfInRange(Set<LivingEntity> resolved, ServerPlayerEntity owner, Vec3d center, double squaredRadius) {
        if (owner == null || owner.isRemoved() || !owner.isAlive()) {
            return;
        }
        if (squaredRadius == 0 || owner.getPos().squaredDistanceTo(center) <= squaredRadius) {
            resolved.add(owner);
        }
    }

    private List<LivingEntity> collectNearbyAllies(ServerWorld world, MobEntity pet, PetComponent component,
                                                   ServerPlayerEntity owner, Vec3d center, double radius, double squaredRadius,
                                                   @Nullable List<PetSwarmIndex.SwarmEntry> swarmSnapshot,
                                                   @Nullable OwnerSpatialResult spatialResult) {
        Set<LivingEntity> allies = new LinkedHashSet<>();
        if (owner != null && (squaredRadius == 0 || owner.getPos().squaredDistanceTo(center) <= squaredRadius)) {
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

        for (LivingEntity player : collectNearbyPlayers(owner, center, radius, squaredRadius)) {
            allies.add(player);
        }
        return new ArrayList<>(allies);
    }

    private List<LivingEntity> collectNearbyPlayers(ServerPlayerEntity owner, Vec3d center, double radius, double squaredRadius) {
        if (owner == null) {
            return List.of();
        }
        List<ServerPlayerEntity> players = playerIndex.playersInRange(owner, center, radius, squaredRadius);
        if (players.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(players);
    }

    private static final class PlayerProximityIndex {
        private static final double MAX_RADIUS = 64.0;
        private static final int CELL_SIZE = 16;

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
            if (!(player.getWorld() instanceof ServerWorld world)) {
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
            if (!(owner.getWorld() instanceof ServerWorld world)) {
                return List.of();
            }

            WorldIndex index = worldIndexes.get(world);
            if (index == null) {
                return List.of(owner);
            }

            double effectiveRadius = radius <= 0 ? MAX_RADIUS : Math.min(radius, MAX_RADIUS);
            double radiusSq = squaredRadius == 0 ? effectiveRadius * effectiveRadius : Math.min(squaredRadius, effectiveRadius * effectiveRadius);

            List<ServerPlayerEntity> players = index.query(center, effectiveRadius, radiusSq);
            if ((radiusSq == 0 || owner.getPos().squaredDistanceTo(center) <= radiusSq) && !players.contains(owner)) {
                players.add(owner);
            }
            return players;
        }

        private static long toCellKey(double x, double z) {
            int cellX = MathHelper.floor(x / CELL_SIZE);
            int cellZ = MathHelper.floor(z / CELL_SIZE);
            return (((long) cellX) << 32) ^ (cellZ & 0xffffffffL);
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
            private final Map<Long, List<TrackedPlayer>> buckets = new HashMap<>();

            private void add(TrackedPlayer tracked) {
                buckets.computeIfAbsent(tracked.cellKey, key -> new ArrayList<>()).add(tracked);
            }

            private void move(TrackedPlayer tracked, long newKey) {
                remove(tracked);
                tracked.cellKey = newKey;
                add(tracked);
            }

            private void remove(TrackedPlayer tracked) {
                List<TrackedPlayer> bucket = buckets.get(tracked.cellKey);
                if (bucket == null) {
                    return;
                }
                bucket.remove(tracked);
                if (bucket.isEmpty()) {
                    buckets.remove(tracked.cellKey);
                }
            }

            private List<ServerPlayerEntity> query(Vec3d center, double radius, double radiusSq) {
                int minCellX = MathHelper.floor((center.x - radius) / CELL_SIZE);
                int maxCellX = MathHelper.floor((center.x + radius) / CELL_SIZE);
                int minCellZ = MathHelper.floor((center.z - radius) / CELL_SIZE);
                int maxCellZ = MathHelper.floor((center.z + radius) / CELL_SIZE);

                List<ServerPlayerEntity> result = new ArrayList<>();
                for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                    for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                        List<TrackedPlayer> bucket = buckets.get(toCellKey(cellX, cellZ));
                        if (bucket == null) {
                            continue;
                        }
                        for (TrackedPlayer tracked : bucket) {
                            ServerPlayerEntity candidate = tracked.player;
                            if (candidate == null || candidate.isRemoved() || !candidate.isAlive()) {
                                continue;
                            }
                            double dx = tracked.x - center.x;
                            double dy = tracked.y - center.y;
                            double dz = tracked.z - center.z;
                            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                            if (radiusSq == 0 || distSq <= radiusSq) {
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
