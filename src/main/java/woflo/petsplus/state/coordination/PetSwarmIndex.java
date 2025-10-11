package woflo.petsplus.state.coordination;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

/**
 * Maintains an owner-scoped spatial index of tracked pets so that group
 * interactions can query nearby allies without scanning the entire world.
 */
public final class PetSwarmIndex {

    private final Map<UUID, OwnerSwarm> swarmsByOwner = new HashMap<>();
    private final Map<MobEntity, OwnerSwarm> swarmByPet = new IdentityHashMap<>();
    private final List<SwarmListener> listeners = new ArrayList<>();

    public void trackPet(MobEntity pet, PetComponent component) {
        updatePet(pet, component);
    }

    public void updatePet(MobEntity pet, PetComponent component) {
        if (!(pet.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld)) {
            return;
        }

        UUID ownerId = component.getOwnerUuid();
        if (ownerId == null) {
            untrackPet(pet);
            return;
        }

        OwnerSwarm swarm = swarmsByOwner.computeIfAbsent(ownerId, OwnerSwarm::new);
        OwnerSwarm current = swarmByPet.get(pet);
        if (current != null && current != swarm) {
            current.remove(pet);
            if (current.isEmpty()) {
                swarmsByOwner.remove(current.ownerId(), current);
            }
            notifyOwnerCleared(current.ownerId());
        }

        swarmByPet.put(pet, swarm);
        swarm.updateEntry(pet, component);
        notifyOwnerUpdated(ownerId, swarm.snapshot());
    }

    public void untrackPet(MobEntity pet) {
        OwnerSwarm swarm = swarmByPet.remove(pet);
        if (swarm != null) {
            swarm.remove(pet);
            if (swarm.isEmpty()) {
                swarmsByOwner.remove(swarm.ownerId(), swarm);
            }
            notifyOwnerUpdated(swarm.ownerId(), swarm.snapshot());
        }
    }

    public void removeOwner(UUID ownerId) {
        OwnerSwarm swarm = swarmsByOwner.remove(ownerId);
        if (swarm != null) {
            swarm.clear();
        }
        notifyOwnerCleared(ownerId);
    }

    public void clear() {
        for (OwnerSwarm swarm : swarmsByOwner.values()) {
            swarm.clear();
        }
        swarmsByOwner.clear();
        swarmByPet.clear();
        synchronized (listeners) {
            if (!listeners.isEmpty()) {
                for (SwarmListener listener : listeners) {
                    listener.onSwarmUpdated(null, List.of());
                }
            }
        }
    }

    public void addListener(SwarmListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(SwarmListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void forEachPetInRange(ServerPlayerEntity owner, Vec3d center, double radius,
                                  Consumer<SwarmEntry> consumer) {
        OwnerSwarm swarm = swarmsByOwner.get(owner.getUuid());
        if (swarm == null) {
            return;
        }
        swarm.forEachInRange(center, radius, consumer);
    }

    public void forEachPetInRange(Vec3d center, double radius, Consumer<SwarmEntry> consumer) {
        if (center == null || consumer == null) {
            return;
        }
        double clampedRadius = Math.max(0.0D, radius);
        if (swarmsByOwner.isEmpty()) {
            return;
        }
        for (OwnerSwarm swarm : swarmsByOwner.values()) {
            swarm.forEachInRange(center, clampedRadius, consumer);
        }
    }

    public void forEachNeighbor(MobEntity pet, PetComponent component, double radius,
                                NeighborVisitor visitor) {
        forEachNeighbor(pet, component, radius, 0, visitor);
    }

    public void forEachNeighbor(MobEntity pet, PetComponent component, double radius,
                                int maxSamples, NeighborVisitor visitor) {
        OwnerSwarm swarm = swarmByPet.get(pet);
        if (swarm == null) {
            UUID ownerId = component.getOwnerUuid();
            if (ownerId == null) {
                return;
            }
            swarm = swarmsByOwner.get(ownerId);
            if (swarm == null) {
                return;
            }
        }
        swarm.forEachNeighbor(pet, component, radius, maxSamples, visitor);
    }

    public List<SwarmEntry> snapshotOwner(UUID ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        OwnerSwarm swarm = swarmsByOwner.get(ownerId);
        if (swarm == null) {
            return List.of();
        }
        return swarm.snapshot();
    }

    @Nullable
    public SwarmEntry findEntry(UUID ownerId, UUID petId) {
        if (ownerId == null || petId == null) {
            return null;
        }
        OwnerSwarm swarm = swarmsByOwner.get(ownerId);
        if (swarm == null) {
            return null;
        }
        TrackedEntry entry = swarm.findByUuid(petId);
        if (entry == null) {
            return null;
        }
        return entry.snapshot();
    }

    public interface NeighborVisitor {
        void accept(SwarmEntry entry, double squaredDistance);
    }

    public interface SwarmListener {
        void onSwarmUpdated(@Nullable UUID ownerId, List<SwarmEntry> entries);
    }

    private void notifyOwnerUpdated(@Nullable UUID ownerId, List<SwarmEntry> snapshot) {
        List<SwarmListener> copy;
        synchronized (listeners) {
            if (listeners.isEmpty()) {
                return;
            }
            copy = new ArrayList<>(listeners);
        }
        for (SwarmListener listener : copy) {
            listener.onSwarmUpdated(ownerId, snapshot);
        }
    }

    private void notifyOwnerCleared(@Nullable UUID ownerId) {
        notifyOwnerUpdated(ownerId, List.of());
    }

    public static class SwarmEntry {
        private final MobEntity pet;
        PetComponent component;
        double x;
        double y;
        double z;

        private SwarmEntry(MobEntity pet, PetComponent component, double x, double y, double z) {
            this.pet = pet;
            this.component = component;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public MobEntity pet() {
            return pet;
        }

        public PetComponent component() {
            return component;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }
    }

    private final class OwnerSwarm {
        private static final int CLUSTER_SHIFT = 2;
        private static final double SECTION_SIZE = 16.0;
        private static final double CLUSTER_SIZE = SECTION_SIZE * (1 << CLUSTER_SHIFT);
        private final UUID ownerId;
        private final Map<MobEntity, TrackedEntry> entries = new IdentityHashMap<>();
        private final Map<UUID, TrackedEntry> entriesByUuid = new HashMap<>();
        private final Map<Long, OwnerCell> cells = new HashMap<>();
        private final Map<Long, OwnerCluster> clusters = new HashMap<>();

        private List<SwarmEntry> snapshotView = List.of();
        private boolean snapshotDirty = true;
        private int structureVersion = 0;

        private OwnerSwarm(UUID ownerId) {
            this.ownerId = ownerId;
        }

        UUID ownerId() {
            return ownerId;
        }

        boolean isEmpty() {
            return entries.isEmpty();
        }

        void clear() {
            for (TrackedEntry entry : new ArrayList<>(entries.values())) {
                removeEntry(entry);
            }
            entries.clear();
            cells.clear();
            snapshotView = List.of();
            markDirty();
        }

        void updateEntry(MobEntity pet, PetComponent component) {
            if (pet.isRemoved()) {
                remove(pet);
                return;
            }

            TrackedEntry entry = entries.get(pet);
            boolean changed = false;
            if (entry == null) {
                entry = createEntry(pet, component);
                entries.put(pet, entry);
                entriesByUuid.put(pet.getUuid(), entry);
                addToCell(entry, entry.cellKey);
                changed = true;
            } else {
                if (entry.component != component) {
                    entry.component = component;
                    changed = true;
                }
            }

            double x = pet.getX();
            double y = pet.getY();
            double z = pet.getZ();

            long newKey = cellKey(x, y, z);
            if (entry.cellKey != newKey) {
                moveEntry(entry, newKey);
                changed = true;
            }

            if (entry.x != x || entry.y != y || entry.z != z) {
                entry.x = x;
                entry.y = y;
                entry.z = z;
                changed = true;
            }

            if (changed) {
                markDirty();
            }
        }

        void remove(MobEntity pet) {
            TrackedEntry entry = entries.remove(pet);
            if (entry != null) {
                entriesByUuid.remove(pet.getUuid());
                removeEntry(entry);
            }
        }

        private TrackedEntry createEntry(MobEntity pet, PetComponent component) {
            double x = pet.getX();
            double y = pet.getY();
            double z = pet.getZ();
            long key = cellKey(x, y, z);
            return new TrackedEntry(pet, component, x, y, z, key);
        }

        private void addToCell(TrackedEntry entry, long key) {
            OwnerCell cell = cells.computeIfAbsent(key, OwnerCell::new);
            if (cell.cluster == null) {
                attachCellToCluster(cell);
            }
            cell.add(entry);
            entry.cell = cell;
            entry.cellKey = key;
        }

        private void moveEntry(TrackedEntry entry, long newKey) {
            OwnerCell currentCell = entry.cell;
            if (currentCell != null) {
                currentCell.remove(entry);
                if (currentCell.isEmpty()) {
                    cells.remove(currentCell.key);
                    detachCellFromCluster(currentCell);
                }
            }
            addToCell(entry, newKey);
        }

        private void removeEntry(TrackedEntry entry) {
            OwnerCell cell = entry.cell;
            if (cell != null) {
                cell.remove(entry);
                if (cell.isEmpty()) {
                    cells.remove(cell.key);
                    detachCellFromCluster(cell);
                }
            }
            swarmByPet.remove(entry.pet());
            entriesByUuid.remove(entry.pet().getUuid());
            entry.snapshot = null;
            entry.invalidateCache();
            markDirty();
        }

        @Nullable
        TrackedEntry findByUuid(UUID petId) {
            if (petId == null) {
                return null;
            }
            return entriesByUuid.get(petId);
        }

        void forEachInRange(Vec3d center, double radius, Consumer<SwarmEntry> consumer) {
            if (entries.isEmpty()) {
                return;
            }

            final double radiusSq = radius * radius;
            forEachClusterIntersectingSphere(center.x, center.y, center.z, radius, new ClusterConsumer() {
                @Override
                public boolean accept(OwnerCluster cluster) {
                    cluster.forEach(center.x, center.y, center.z, radiusSq, consumer);
                    return true;
                }
            });
        }

        void forEachNeighbor(MobEntity pet, PetComponent component, double radius,
                             NeighborVisitor visitor) {
            forEachNeighbor(pet, component, radius, 0, visitor);
        }

        void forEachNeighbor(MobEntity pet, PetComponent component, double radius,
                             int maxSamples, NeighborVisitor visitor) {
            TrackedEntry base = entries.get(pet);
            if (base == null) {
                updateEntry(pet, component);
                base = entries.get(pet);
                if (base == null) {
                    return;
                }
            }

            final double radiusSq = radius * radius;
            final int limit = maxSamples > 0 ? maxSamples : Integer.MAX_VALUE;

            NeighborCache cache = base.ensureCache();
            if (cache.isValid(structureVersion) && cache.supports(radiusSq, limit)) {
                cache.forEach(radiusSq, limit, visitor);
                return;
            }

            cache.begin(structureVersion, radiusSq, limit);
            final NeighborCache activeCache = cache;
            final TrackedEntry searchBase = base;
            final NeighborAcceptor collector = new NeighborAcceptor() {
                @Override
                public boolean accept(TrackedEntry entry, double distanceSq) {
                    activeCache.add(entry, distanceSq);
                    return true;
                }
            };

            final int[] visited = new int[]{0};
            forEachClusterIntersectingSphere(base.x, base.y, base.z, radius, new ClusterConsumer() {
                @Override
                public boolean accept(OwnerCluster cluster) {
                    if (visited[0] >= limit) {
                        return false;
                    }
                    visited[0] = cluster.forEachNeighbor(searchBase, radiusSq, collector, visited[0], limit);
                    return visited[0] < limit;
                }
            });

            cache.finish();
            cache.forEach(radiusSq, limit, visitor);
        }

        List<SwarmEntry> snapshot() {
            if (entries.isEmpty()) {
                snapshotView = List.of();
                snapshotDirty = false;
                return List.of();
            }
            if (!snapshotDirty) {
                return snapshotView;
            }

            List<SwarmEntry> copy = new ArrayList<>(entries.size());
            var iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<MobEntity, TrackedEntry> mapEntry = iterator.next();
                TrackedEntry tracked = mapEntry.getValue();
                if (!tracked.isValid()) {
                    iterator.remove();
                    removeEntry(tracked);
                    continue;
                }
                copy.add(tracked.snapshot());
            }

            if (copy.isEmpty()) {
                snapshotView = List.of();
            } else {
                snapshotView = Collections.unmodifiableList(copy);
            }
            snapshotDirty = false;
            return snapshotView;
        }

        private void markDirty() {
            snapshotDirty = true;
            bumpStructureVersion();
        }

        private void bumpStructureVersion() {
            if (structureVersion == Integer.MAX_VALUE) {
                structureVersion = 0;
                invalidateAllCaches();
            } else {
                structureVersion++;
            }
        }

        private void invalidateAllCaches() {
            for (TrackedEntry entry : entries.values()) {
                entry.invalidateCache();
            }
        }

        private void attachCellToCluster(OwnerCell cell) {
            long clusterKey = clusterKeyForCell(cell.key);
            OwnerCluster cluster = clusters.computeIfAbsent(clusterKey, OwnerCluster::new);
            cluster.addCell(cell);
        }

        private void detachCellFromCluster(OwnerCell cell) {
            OwnerCluster cluster = cell.cluster;
            if (cluster == null) {
                return;
            }
            cluster.removeCell(cell);
            if (cluster.isEmpty()) {
                clusters.remove(cluster.key);
            }
            cell.cluster = null;
        }

        private void forEachClusterIntersectingSphere(double centerX, double centerY, double centerZ,
                                                      double radius, ClusterConsumer consumer) {
            if (clusters.isEmpty()) {
                return;
            }

            double minX = centerX - radius;
            double maxX = centerX + radius;
            double minY = centerY - radius;
            double maxY = centerY + radius;
            double minZ = centerZ - radius;
            double maxZ = centerZ + radius;

            int minSectionX = ChunkSectionPos.getSectionCoord(MathHelper.floor(minX));
            int maxSectionX = ChunkSectionPos.getSectionCoord(MathHelper.floor(maxX));
            int minSectionY = ChunkSectionPos.getSectionCoord(MathHelper.floor(minY));
            int maxSectionY = ChunkSectionPos.getSectionCoord(MathHelper.floor(maxY));
            int minSectionZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(minZ));
            int maxSectionZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(maxZ));

            int minClusterX = minSectionX >> CLUSTER_SHIFT;
            int maxClusterX = maxSectionX >> CLUSTER_SHIFT;
            int minClusterY = minSectionY >> CLUSTER_SHIFT;
            int maxClusterY = maxSectionY >> CLUSTER_SHIFT;
            int minClusterZ = minSectionZ >> CLUSTER_SHIFT;
            int maxClusterZ = maxSectionZ >> CLUSTER_SHIFT;

            double radiusSq = radius * radius;

            for (int clusterX = minClusterX; clusterX <= maxClusterX; clusterX++) {
                for (int clusterY = minClusterY; clusterY <= maxClusterY; clusterY++) {
                    for (int clusterZ = minClusterZ; clusterZ <= maxClusterZ; clusterZ++) {
                        OwnerCluster cluster = clusters.get(ChunkSectionPos.asLong(clusterX, clusterY, clusterZ));
                        if (cluster == null) {
                            continue;
                        }
                        if (!cluster.intersectsSphere(centerX, centerY, centerZ, radiusSq)) {
                            continue;
                        }
                        if (!consumer.accept(cluster)) {
                            return;
                        }
                    }
                }
            }
        }

        @FunctionalInterface
        private interface ClusterConsumer {
            boolean accept(OwnerCluster cluster);
        }

        private long clusterKeyForCell(long cellKey) {
            int secX = ChunkSectionPos.unpackX(cellKey);
            int secY = ChunkSectionPos.unpackY(cellKey);
            int secZ = ChunkSectionPos.unpackZ(cellKey);
            return ChunkSectionPos.asLong(secX >> CLUSTER_SHIFT, secY >> CLUSTER_SHIFT, secZ >> CLUSTER_SHIFT);
        }

        private double clusterMin(int clusterCoord) {
            int section = clusterCoord << CLUSTER_SHIFT;
            return ChunkSectionPos.getBlockCoord(section);
        }

        private double clusterMax(int clusterCoord) {
            return clusterMin(clusterCoord) + CLUSTER_SIZE;
        }

        private double clamp(double value, double min, double max) {
            if (value < min) return min;
            if (value > max) return max;
            return value;
        }

        private final class OwnerCluster {
            private final long key;
            private final List<OwnerCell> members = new ArrayList<>();

            private OwnerCluster(long key) {
                this.key = key;
            }

            void addCell(OwnerCell cell) {
                members.add(cell);
                cell.cluster = this;
            }

            void removeCell(OwnerCell cell) {
                for (int i = 0; i < members.size(); i++) {
                    if (members.get(i) == cell) {
                        int last = members.size() - 1;
                        if (i != last) {
                            members.set(i, members.get(last));
                        }
                        members.remove(last);
                        break;
                    }
                }
            }

            boolean isEmpty() {
                return members.isEmpty();
            }

            boolean intersectsSphere(double centerX, double centerY, double centerZ, double radiusSq) {
                int clusterX = ChunkSectionPos.unpackX(key);
                int clusterY = ChunkSectionPos.unpackY(key);
                int clusterZ = ChunkSectionPos.unpackZ(key);

                double minX = clusterMin(clusterX);
                double minY = clusterMin(clusterY);
                double minZ = clusterMin(clusterZ);
                double maxX = clusterMax(clusterX);
                double maxY = clusterMax(clusterY);
                double maxZ = clusterMax(clusterZ);

                double nearestX = clamp(centerX, minX, maxX);
                double nearestY = clamp(centerY, minY, maxY);
                double nearestZ = clamp(centerZ, minZ, maxZ);

                double dx = centerX - nearestX;
                double dy = centerY - nearestY;
                double dz = centerZ - nearestZ;
                return (dx * dx) + (dy * dy) + (dz * dz) <= radiusSq;
            }

            void forEach(double centerX, double centerY, double centerZ, double radiusSq,
                         Consumer<SwarmEntry> consumer) {
                for (OwnerCell cell : members) {
                    cell.forEach(centerX, centerY, centerZ, radiusSq, consumer);
                }
            }

            int forEachNeighbor(TrackedEntry base, double radiusSq, NeighborAcceptor acceptor,
                                 int visited, int limit) {
                for (OwnerCell cell : members) {
                    visited = cell.forEachNeighbor(base, radiusSq, acceptor, visited, limit);
                    if (visited >= limit) {
                        return visited;
                    }
                }
                return visited;
            }
        }
    }

    private static final class OwnerCell {
        private final long key;
        private final List<TrackedEntry> members = new ArrayList<>();
        private OwnerSwarm.OwnerCluster cluster;

        private OwnerCell(long key) {
            this.key = key;
        }

        void add(TrackedEntry entry) {
            members.add(entry);
        }

        void remove(TrackedEntry entry) {
            for (int i = 0; i < members.size(); i++) {
                if (members.get(i) == entry) {
                    int last = members.size() - 1;
                    if (i != last) {
                        members.set(i, members.get(last));
                    }
                    members.remove(last);
                    break;
                }
            }
        }

        boolean isEmpty() {
            return members.isEmpty();
        }

        void forEach(double centerX, double centerY, double centerZ, double radiusSq,
                     Consumer<SwarmEntry> consumer) {
            for (int i = 0; i < members.size();) {
                TrackedEntry entry = members.get(i);
                if (!entry.isValid()) {
                    remove(entry);
                    continue;
                }

                double dx = entry.x - centerX;
                double dy = entry.y - centerY;
                double dz = entry.z - centerZ;
                if ((dx * dx) + (dy * dy) + (dz * dz) <= radiusSq) {
                    consumer.accept(entry);
                }
                i++;
            }
        }

        int forEachNeighbor(TrackedEntry base, double radiusSq, NeighborAcceptor acceptor,
                             int visited, int limit) {
            for (int i = 0; i < members.size();) {
                TrackedEntry entry = members.get(i);
                if (!entry.isValid()) {
                    remove(entry);
                    continue;
                }
                if (entry == base) {
                    i++;
                    continue;
                }

                double dx = entry.x - base.x;
                double dy = entry.y - base.y;
                double dz = entry.z - base.z;
                double distSq = (dx * dx) + (dy * dy) + (dz * dz);
                if (distSq <= radiusSq) {
                    if (acceptor.accept(entry, distSq)) {
                        visited++;
                    }
                    if (visited >= limit) {
                        return visited;
                    }
                }
                i++;
            }
            return visited;
        }
    }

    private static final class TrackedEntry extends SwarmEntry {
        private long cellKey;
        private OwnerCell cell;
        private SwarmEntry snapshot;
        private NeighborCache neighborCache;

        private TrackedEntry(MobEntity pet, PetComponent component, double x, double y, double z, long cellKey) {
            super(pet, component, x, y, z);
            this.cellKey = cellKey;
        }

        boolean isValid() {
            PetComponent component = component();
            if (pet().isRemoved()) {
                return false;
            }
            UUID ownerId = component != null ? component.getOwnerUuid() : null;
            return ownerId != null;
        }

        SwarmEntry snapshot() {
            SwarmEntry view = snapshot;
            if (view == null) {
                view = new SwarmEntry(pet(), component(), x, y, z);
                snapshot = view;
            } else {
                view.component = component();
                view.x = x;
                view.y = y;
                view.z = z;
            }
            return view;
        }

        NeighborCache ensureCache() {
            NeighborCache cache = neighborCache;
            if (cache == null) {
                cache = new NeighborCache();
                neighborCache = cache;
            }
            return cache;
        }

        void invalidateCache() {
            if (neighborCache != null) {
                neighborCache.invalidate();
            }
        }
    }

    private static long cellKey(double x, double y, double z) {
        int secX = ChunkSectionPos.getSectionCoord(MathHelper.floor(x));
        int secY = ChunkSectionPos.getSectionCoord(MathHelper.floor(y));
        int secZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(z));
        return ChunkSectionPos.asLong(secX, secY, secZ);
    }

    private interface NeighborAcceptor {
        boolean accept(TrackedEntry entry, double distanceSq);
    }

    private static final class NeighborCache {
        private static final Comparator<NeighborRecord> SORT_BY_DISTANCE = Comparator.comparingDouble(record -> record.distanceSq);

        private int version = -1;
        private double computedRadiusSq = 0.0D;
        private int cachedLimit = 0;
        private final List<NeighborRecord> records = new ArrayList<>();
        private int size = 0;

        boolean isValid(int currentVersion) {
            return version == currentVersion;
        }

        boolean supports(double radiusSq, int limit) {
            return radiusSq <= computedRadiusSq && limit <= cachedLimit;
        }

        void begin(int currentVersion, double radiusSq, int limit) {
            this.version = currentVersion;
            this.computedRadiusSq = radiusSq;
            this.cachedLimit = limit;
            this.size = 0;
        }

        void add(TrackedEntry entry, double distanceSq) {
            NeighborRecord record;
            if (size < records.size()) {
                record = records.get(size);
            } else {
                record = new NeighborRecord();
                records.add(record);
            }
            record.entry = entry;
            record.distanceSq = distanceSq;
            size++;
        }

        void finish() {
            if (size > 1) {
                records.subList(0, size).sort(SORT_BY_DISTANCE);
            }
        }

        void forEach(double radiusSq, int limit, NeighborVisitor visitor) {
            int remaining = limit;
            double threshold = Math.min(radiusSq, computedRadiusSq);
            for (int i = 0; i < size && remaining > 0; i++) {
                NeighborRecord record = records.get(i);
                if (record.distanceSq > threshold) {
                    break;
                }
                visitor.accept(record.entry, record.distanceSq);
                remaining--;
            }
        }

        void invalidate() {
            version = -1;
            computedRadiusSq = 0.0D;
            cachedLimit = 0;
            size = 0;
        }

        private static final class NeighborRecord {
            private TrackedEntry entry;
            private double distanceSq;
        }
    }
}

