package woflo.petsplus.state.processing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable spatial analysis of an owner batch captured for asynchronous
 * processing. Results only contain primitive data so they can be safely shared
 * between threads and replayed on the main thread without touching live world
 * state.
 */
public final class OwnerSpatialResult {
    private final UUID ownerId;
    private final long snapshotTick;
    private final Map<UUID, List<Neighbor>> neighborsByPet;

    private OwnerSpatialResult(UUID ownerId,
                               long snapshotTick,
                               Map<UUID, List<Neighbor>> neighborsByPet) {
        this.ownerId = ownerId;
        this.snapshotTick = snapshotTick;
        this.neighborsByPet = neighborsByPet;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public long snapshotTick() {
        return snapshotTick;
    }

    public boolean isEmpty() {
        return neighborsByPet.isEmpty();
    }

    /**
     * Returns a list of neighbor pet UUIDs ordered by increasing distance. The
     * list is capped by {@code limit} and filtered by {@code radius}.
     */
    public List<UUID> neighborsWithin(UUID petUuid, double radius, int limit) {
        if (petUuid == null || radius <= 0.0D || limit <= 0) {
            return List.of();
        }
        List<Neighbor> neighbors = neighborsByPet.get(petUuid);
        if (neighbors == null || neighbors.isEmpty()) {
            return List.of();
        }
        double radiusSq = radius * radius;
        List<UUID> matches = new ArrayList<>(Math.min(limit, neighbors.size()));
        for (Neighbor neighbor : neighbors) {
            if (neighbor.squaredDistance() > radiusSq) {
                break;
            }
            matches.add(neighbor.petUuid());
            if (matches.size() >= limit) {
                break;
            }
        }
        return matches;
    }

    public static OwnerSpatialResult analyze(OwnerBatchSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<OwnerBatchSnapshot.PetSummary> pets = snapshot.pets();
        if (pets.isEmpty()) {
            return new OwnerSpatialResult(snapshot.ownerId(), snapshot.snapshotTick(), Map.of());
        }

        Map<UUID, List<Neighbor>> neighbors = new HashMap<>();
        List<OwnerBatchSnapshot.PetSummary> filtered = new ArrayList<>(pets.size());
        for (OwnerBatchSnapshot.PetSummary pet : pets) {
            if (pet.petUuid() == null) {
                continue;
            }
            if (Double.isNaN(pet.x()) || Double.isNaN(pet.y()) || Double.isNaN(pet.z())) {
                continue;
            }
            filtered.add(pet);
            neighbors.put(pet.petUuid(), new ArrayList<>());
        }

        int size = filtered.size();
        for (int i = 0; i < size; i++) {
            OwnerBatchSnapshot.PetSummary left = filtered.get(i);
            for (int j = i + 1; j < size; j++) {
                OwnerBatchSnapshot.PetSummary right = filtered.get(j);
                double dx = left.x() - right.x();
                double dy = left.y() - right.y();
                double dz = left.z() - right.z();
                double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);
                neighbors.get(left.petUuid()).add(new Neighbor(right.petUuid(), distanceSq));
                neighbors.get(right.petUuid()).add(new Neighbor(left.petUuid(), distanceSq));
            }
        }

        Map<UUID, List<Neighbor>> immutable = new HashMap<>(neighbors.size());
        for (Map.Entry<UUID, List<Neighbor>> entry : neighbors.entrySet()) {
            List<Neighbor> list = entry.getValue();
            list.sort((a, b) -> Double.compare(a.squaredDistance(), b.squaredDistance()));
            immutable.put(entry.getKey(), Collections.unmodifiableList(list));
        }

        return new OwnerSpatialResult(
            snapshot.ownerId(),
            snapshot.snapshotTick(),
            Collections.unmodifiableMap(immutable)
        );
    }

    public record Neighbor(UUID petUuid, double squaredDistance) {
    }
}
