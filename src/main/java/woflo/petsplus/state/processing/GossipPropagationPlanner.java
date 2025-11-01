package woflo.petsplus.state.processing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import woflo.petsplus.state.gossip.RumorEntry;
import woflo.petsplus.state.coordination.PetWorkScheduler;

/**
 * Prepares gossip sharing plans for owner batches so the heavy neighbour
 * computations can run on background threads.
 */
public final class GossipPropagationPlanner {
    private static final double DEFAULT_RADIUS = 12.0D;
    private static final double DEFAULT_RADIUS_SQ = DEFAULT_RADIUS * DEFAULT_RADIUS;
    private static final int MAX_NEIGHBORS = 4;

    private GossipPropagationPlanner() {
    }

    public static GossipPropagationPlan plan(OwnerBatchSnapshot snapshot) {
        if (snapshot == null) {
            return GossipPropagationPlan.empty();
        }

        Map<UUID, OwnerBatchSnapshot.TaskSnapshot> gossipTasks = tasksFor(snapshot, PetWorkScheduler.TaskType.GOSSIP_DECAY);
        if (gossipTasks.isEmpty()) {
            return GossipPropagationPlan.empty();
        }

        List<OwnerBatchSnapshot.PetSummary> pets = snapshot.pets();
        if (pets.isEmpty()) {
            return GossipPropagationPlan.empty();
        }

        Map<UUID, OwnerBatchSnapshot.PetSummary> summariesById = new HashMap<>(pets.size());
        List<PetNode> nodes = new ArrayList<>(pets.size());
        Map<UUID, PetNode> nodesById = new HashMap<>(pets.size());
        for (OwnerBatchSnapshot.PetSummary pet : pets) {
            if (pet == null) {
                continue;
            }
            UUID petId = pet.petUuid();
            if (petId == null) {
                continue;
            }
            summariesById.put(petId, pet);
            if (pet.gossipOptedOut()) {
                continue;
            }
            double x = pet.x();
            double y = pet.y();
            double z = pet.z();
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
                continue;
            }
            PetNode node = new PetNode(petId, x, y, z, nodes.size());
            nodes.add(node);
            nodesById.put(petId, node);
        }

        if (summariesById.isEmpty()) {
            return GossipPropagationPlan.empty();
        }

        List<StoryCandidate> storytellers = new ArrayList<>(gossipTasks.size());
        long snapshotTick = snapshot.snapshotTick();
        List<PetNode> storytellerNodes = new ArrayList<>(gossipTasks.size());
        for (OwnerBatchSnapshot.TaskSnapshot task : gossipTasks.values()) {
            UUID storytellerId = task.petUuid();
            OwnerBatchSnapshot.PetSummary storyteller = summariesById.get(storytellerId);
            if (storyteller == null || storyteller.gossipOptedOut()) {
                continue;
            }

            List<RumorEntry> shareableRumors = selectShareableRumors(storyteller, snapshotTick);
            if (shareableRumors.isEmpty()) {
                continue;
            }

            PetNode node = nodesById.get(storytellerId);
            if (node == null) {
                continue;
            }
            node.ensureBuffer(MAX_NEIGHBORS);
            if (node.neighbors != null) {
                storytellerNodes.add(node);
            }
            storytellers.add(new StoryCandidate(node, shareableRumors));
        }

        if (storytellers.isEmpty()) {
            return GossipPropagationPlan.empty();
        }

        if (nodes.size() >= 2 && !storytellerNodes.isEmpty()) {
            storytellerNodes.sort((a, b) -> Integer.compare(a.index, b.index));
            for (PetNode storytellerNode : storytellerNodes) {
                NeighborBuffer bufferA = storytellerNode.neighbors;
                if (bufferA == null) {
                    continue;
                }
                for (int j = 0; j < nodes.size(); j++) {
                    PetNode other = nodes.get(j);
                    if (other == storytellerNode) {
                        continue;
                    }
                    if (other.neighbors != null && other.index < storytellerNode.index) {
                        continue;
                    }
                    double dx = other.x - storytellerNode.x;
                    double dy = other.y - storytellerNode.y;
                    double dz = other.z - storytellerNode.z;
                    double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);
                    if (distanceSq > DEFAULT_RADIUS_SQ) {
                        continue;
                    }
                    bufferA.insert(other.id, distanceSq);
                    NeighborBuffer bufferB = other.neighbors;
                    if (bufferB != null && other.index > storytellerNode.index) {
                        bufferB.insert(storytellerNode.id, distanceSq);
                    }
                }
            }
        }

        Map<UUID, List<Share>> transmissions = new HashMap<>(storytellers.size());
        for (StoryCandidate candidate : storytellers) {
            List<UUID> neighbors = candidate.node.neighborIds();
            if (neighbors.isEmpty()) {
                continue;
            }
            List<Share> shares = buildShares(neighbors, candidate.rumors);
            if (!shares.isEmpty()) {
                transmissions.put(candidate.node.id, shares);
            }
        }

        if (transmissions.isEmpty()) {
            return GossipPropagationPlan.empty();
        }

        return new GossipPropagationPlan(transmissions);
    }

    private static Map<UUID, OwnerBatchSnapshot.TaskSnapshot> tasksFor(OwnerBatchSnapshot snapshot,
                                                                       PetWorkScheduler.TaskType type) {
        Map<UUID, OwnerBatchSnapshot.TaskSnapshot> tasksByPet = new HashMap<>();
        Map<PetWorkScheduler.TaskType, List<OwnerBatchSnapshot.TaskSnapshot>> buckets = snapshot.taskBuckets();
        List<OwnerBatchSnapshot.TaskSnapshot> tasks = buckets.get(type);
        if (tasks == null || tasks.isEmpty()) {
            return tasksByPet;
        }
        for (OwnerBatchSnapshot.TaskSnapshot task : tasks) {
            UUID petId = task.petUuid();
            if (petId != null) {
                tasksByPet.put(petId, task);
            }
        }
        return tasksByPet;
    }

    private static List<RumorEntry> selectShareableRumors(OwnerBatchSnapshot.PetSummary storyteller,
                                                          long snapshotTick) {
        List<RumorEntry> fresh = storyteller.freshRumors();
        List<RumorEntry> abstracts = storyteller.abstractRumors();
        if ((fresh == null || fresh.isEmpty()) && (abstracts == null || abstracts.isEmpty())) {
            return List.of();
        }
        List<RumorEntry> combined = new ArrayList<>(MAX_NEIGHBORS);
        if (fresh != null) {
            for (RumorEntry entry : fresh) {
                if (entry != null) {
                    combined.add(entry.copy());
                    if (combined.size() >= MAX_NEIGHBORS) {
                        return combined;
                    }
                }
            }
        }
        if (abstracts != null) {
            for (RumorEntry entry : abstracts) {
                if (entry != null) {
                    combined.add(entry.copy());
                    if (combined.size() >= MAX_NEIGHBORS) {
                        break;
                    }
                }
            }
        }
        return combined;
    }

    private record StoryCandidate(PetNode node, List<RumorEntry> rumors) { }

    private static final class PetNode {
        private final UUID id;
        private final double x;
        private final double y;
        private final double z;
        private final int index;
        private NeighborBuffer neighbors;

        private PetNode(UUID id, double x, double y, double z, int index) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.index = index;
        }

        private void ensureBuffer(int capacity) {
            if (capacity <= 0 || neighbors != null) {
                return;
            }
            neighbors = new NeighborBuffer(capacity);
        }

        private List<UUID> neighborIds() {
            return neighbors == null ? List.of() : neighbors.toList();
        }
    }

    private static final class NeighborBuffer {
        private final UUID[] ids;
        private final double[] distances;
        private int size;

        NeighborBuffer(int capacity) {
            if (capacity <= 0) {
                this.ids = new UUID[0];
                this.distances = new double[0];
                return;
            }
            this.ids = new UUID[capacity];
            this.distances = new double[capacity];
        }

        void insert(UUID id, double distanceSq) {
            if (ids.length == 0) {
                return;
            }

            int insertAt = size;
            while (insertAt > 0 && distanceSq < distances[insertAt - 1]) {
                insertAt--;
            }

            if (size < ids.length) {
                if (insertAt < size) {
                    System.arraycopy(ids, insertAt, ids, insertAt + 1, size - insertAt);
                    System.arraycopy(distances, insertAt, distances, insertAt + 1, size - insertAt);
                }
                ids[insertAt] = id;
                distances[insertAt] = distanceSq;
                size++;
                return;
            }

            if (insertAt >= size) {
                return;
            }

            if (insertAt < ids.length - 1) {
                System.arraycopy(ids, insertAt, ids, insertAt + 1, (ids.length - insertAt - 1));
                System.arraycopy(distances, insertAt, distances, insertAt + 1, (distances.length - insertAt - 1));
            }
            ids[insertAt] = id;
            distances[insertAt] = distanceSq;
        }

        List<UUID> toList() {
            if (size == 0) {
                return List.of();
            }
            List<UUID> ordered = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                UUID id = ids[i];
                if (id != null) {
                    ordered.add(id);
                }
            }
            return ordered.isEmpty() ? List.of() : List.copyOf(ordered);
        }
    }

    private static List<Share> buildShares(List<UUID> neighbors,
                                           List<RumorEntry> rumors) {
        if (neighbors.isEmpty() || rumors.isEmpty()) {
            return List.of();
        }
        List<Share> shares = new ArrayList<>(Math.min(neighbors.size(), rumors.size()));
        int rumorIndex = 0;
        for (UUID neighborId : neighbors) {
            if (neighborId == null) {
                continue;
            }
            if (rumorIndex >= rumors.size()) {
                break;
            }
            RumorEntry rumor = rumors.get(rumorIndex++);
            if (rumor == null) {
                continue;
            }
            shares.add(new Share(neighborId, rumor));
        }
        return shares;
    }

    /**
     * Immutable mapping between storytellers and their queued gossip shares.
     */
    public static final class GossipPropagationPlan {
        private static final GossipPropagationPlan EMPTY = new GossipPropagationPlan(Map.of());

        private final Map<UUID, List<Share>> transmissions;

        GossipPropagationPlan(Map<UUID, List<Share>> transmissions) {
            if (transmissions == null || transmissions.isEmpty()) {
                this.transmissions = Map.of();
                return;
            }

            Map<UUID, List<Share>> immutableTransmissions = new HashMap<>(transmissions.size());
            for (Map.Entry<UUID, List<Share>> entry : transmissions.entrySet()) {
                UUID storytellerId = entry.getKey();
                if (storytellerId == null) {
                    continue;
                }
                List<Share> shares = entry.getValue();
                if (shares == null || shares.isEmpty()) {
                    continue;
                }
                immutableTransmissions.put(storytellerId, List.copyOf(shares));
            }

            this.transmissions = immutableTransmissions.isEmpty()
                ? Map.of()
                : Map.copyOf(immutableTransmissions);
        }

        public static GossipPropagationPlan empty() {
            return EMPTY;
        }

        public boolean isEmpty() {
            return transmissions.isEmpty();
        }

        public Set<UUID> storytellers() {
            return transmissions.keySet();
        }

        public List<Share> sharesFor(UUID storytellerId) {
            List<Share> shares = transmissions.get(storytellerId);
            return shares == null ? List.of() : shares;
        }
    }

    public record Share(UUID listenerId, RumorEntry rumor) {
        public Share {
            Objects.requireNonNull(listenerId, "listenerId");
            Objects.requireNonNull(rumor, "rumor");
        }
    }
}
