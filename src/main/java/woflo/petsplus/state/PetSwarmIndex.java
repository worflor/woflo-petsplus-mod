package woflo.petsplus.state;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Maintains an owner-scoped spatial index of tracked pets so that group
 * interactions can query nearby allies without scanning the entire world.
 */
public final class PetSwarmIndex {

    private final Map<UUID, OwnerSwarm> swarmsByOwner = new HashMap<>();
    private final Map<MobEntity, OwnerSwarm> swarmByPet = new IdentityHashMap<>();

    public void trackPet(MobEntity pet, PetComponent component) {
        updatePet(pet, component);
    }

    public void updatePet(MobEntity pet, PetComponent component) {
        if (!(pet.getWorld() instanceof net.minecraft.server.world.ServerWorld)) {
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
        }

        swarmByPet.put(pet, swarm);
        swarm.updateEntry(pet, component);
    }

    public void untrackPet(MobEntity pet) {
        OwnerSwarm swarm = swarmByPet.remove(pet);
        if (swarm != null) {
            swarm.remove(pet);
            if (swarm.isEmpty()) {
                swarmsByOwner.remove(swarm.ownerId(), swarm);
            }
        }
    }

    public void removeOwner(UUID ownerId) {
        OwnerSwarm swarm = swarmsByOwner.remove(ownerId);
        if (swarm != null) {
            swarm.clear();
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

    public void forEachNeighbor(MobEntity pet, PetComponent component, double radius,
                                NeighborVisitor visitor) {
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
        swarm.forEachNeighbor(pet, component, radius, visitor);
    }

    public interface NeighborVisitor {
        void accept(SwarmEntry entry, double squaredDistance);
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
        private final UUID ownerId;
        private final Map<MobEntity, TrackedEntry> entries = new IdentityHashMap<>();
        private final Map<Long, OwnerCell> cells = new HashMap<>();

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
        }

        void updateEntry(MobEntity pet, PetComponent component) {
            if (pet.isRemoved()) {
                remove(pet);
                return;
            }

            TrackedEntry entry = entries.get(pet);
            if (entry == null) {
                entry = createEntry(pet, component);
                entries.put(pet, entry);
                addToCell(entry, entry.cellKey);
            } else {
                entry.component = component;
            }

            double x = pet.getX();
            double y = pet.getY();
            double z = pet.getZ();

            long newKey = cellKey(x, y, z);
            if (entry.cellKey != newKey) {
                moveEntry(entry, newKey);
            }

            entry.x = x;
            entry.y = y;
            entry.z = z;
        }

        void remove(MobEntity pet) {
            TrackedEntry entry = entries.remove(pet);
            if (entry != null) {
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
                }
            }
            swarmByPet.remove(entry.pet());
        }

        void forEachInRange(Vec3d center, double radius, Consumer<SwarmEntry> consumer) {
            if (entries.isEmpty()) {
                return;
            }

            double radiusSq = radius * radius;
            int sectionRadius = MathHelper.ceil(radius / 16.0);
            int baseX = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.x));
            int baseY = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.y));
            int baseZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.z));

            for (int sx = baseX - sectionRadius; sx <= baseX + sectionRadius; sx++) {
                for (int sy = baseY - sectionRadius; sy <= baseY + sectionRadius; sy++) {
                    for (int sz = baseZ - sectionRadius; sz <= baseZ + sectionRadius; sz++) {
                        OwnerCell cell = cells.get(ChunkSectionPos.asLong(sx, sy, sz));
                        if (cell == null) continue;
                        cell.forEach(center.x, center.y, center.z, radiusSq, consumer);
                    }
                }
            }
        }

        void forEachNeighbor(MobEntity pet, PetComponent component, double radius,
                             NeighborVisitor visitor) {
            TrackedEntry base = entries.get(pet);
            if (base == null) {
                updateEntry(pet, component);
                base = entries.get(pet);
                if (base == null) {
                    return;
                }
            }

            double radiusSq = radius * radius;
            int sectionRadius = MathHelper.ceil(radius / 16.0);
            int baseX = ChunkSectionPos.getSectionCoord(MathHelper.floor(base.x));
            int baseY = ChunkSectionPos.getSectionCoord(MathHelper.floor(base.y));
            int baseZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(base.z));

            for (int sx = baseX - sectionRadius; sx <= baseX + sectionRadius; sx++) {
                for (int sy = baseY - sectionRadius; sy <= baseY + sectionRadius; sy++) {
                    for (int sz = baseZ - sectionRadius; sz <= baseZ + sectionRadius; sz++) {
                        OwnerCell cell = cells.get(ChunkSectionPos.asLong(sx, sy, sz));
                        if (cell == null) continue;
                        cell.forEachNeighbor(base, radiusSq, visitor);
                    }
                }
            }
        }
    }

    private static final class OwnerCell {
        private final long key;
        private final List<TrackedEntry> members = new ArrayList<>();

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

        void forEachNeighbor(TrackedEntry base, double radiusSq, NeighborVisitor visitor) {
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
                    visitor.accept(entry, distSq);
                }
                i++;
            }
        }
    }

    private static final class TrackedEntry extends SwarmEntry {
        private long cellKey;
        private OwnerCell cell;

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
    }

    private static long cellKey(double x, double y, double z) {
        int secX = ChunkSectionPos.getSectionCoord(MathHelper.floor(x));
        int secY = ChunkSectionPos.getSectionCoord(MathHelper.floor(y));
        int secZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(z));
        return ChunkSectionPos.asLong(secX, secY, secZ);
    }
}
