package woflo.petsplus.state.processing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import woflo.petsplus.state.gossip.RumorEntry;
import woflo.petsplus.state.PetWorkScheduler;

/**
 * Prepares gossip sharing plans for owner batches so the heavy neighbour
 * computations can run on background threads.
 */
public final class GossipPropagationPlanner {
    private static final double DEFAULT_RADIUS = 12.0D;
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

        Map<UUID, OwnerBatchSnapshot.PetSummary> summariesById = new HashMap<>();
        for (OwnerBatchSnapshot.PetSummary pet : pets) {
            UUID petId = pet.petUuid();
            if (petId != null) {
                summariesById.put(petId, pet);
            }
        }

        if (summariesById.isEmpty()) {
            return GossipPropagationPlan.empty();
        }

        Map<UUID, List<Share>> transmissions = new HashMap<>();

        for (OwnerBatchSnapshot.TaskSnapshot task : gossipTasks.values()) {
            UUID storytellerId = task.petUuid();
            OwnerBatchSnapshot.PetSummary storyteller = summariesById.get(storytellerId);
            if (storyteller == null || storyteller.gossipOptedOut()) {
                continue;
            }

            List<RumorEntry> shareableRumors = selectShareableRumors(storyteller, snapshot.snapshotTick());
            if (shareableRumors.isEmpty()) {
                continue;
            }

            List<UUID> neighbors = collectNeighbors(storyteller, summariesById);
            if (neighbors.isEmpty()) {
                continue;
            }

            List<Share> shares = buildShares(neighbors, shareableRumors);
            if (!shares.isEmpty()) {
                transmissions.put(storytellerId, shares);
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

    private static List<UUID> collectNeighbors(OwnerBatchSnapshot.PetSummary storyteller,
                                               Map<UUID, OwnerBatchSnapshot.PetSummary> summariesById) {
        double originX = storyteller.x();
        double originY = storyteller.y();
        double originZ = storyteller.z();
        if (Double.isNaN(originX) || Double.isNaN(originY) || Double.isNaN(originZ)) {
            return List.of();
        }

        List<UUID> neighbors = new ArrayList<>();
        double radiusSq = DEFAULT_RADIUS * DEFAULT_RADIUS;

        for (OwnerBatchSnapshot.PetSummary candidate : summariesById.values()) {
            if (candidate == storyteller || candidate.gossipOptedOut()) {
                continue;
            }
            double x = candidate.x();
            double y = candidate.y();
            double z = candidate.z();
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
                continue;
            }
            double dx = x - originX;
            double dy = y - originY;
            double dz = z - originZ;
            double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (distanceSq > radiusSq) {
                continue;
            }
            neighbors.add(candidate.petUuid());
        }

        if (neighbors.isEmpty()) {
            return neighbors;
        }

        neighbors.sort((a, b) -> compareDistance(a, b, summariesById, originX, originY, originZ));
        return neighbors.size() > MAX_NEIGHBORS ? neighbors.subList(0, MAX_NEIGHBORS) : neighbors;
    }

    private static int compareDistance(UUID leftId, UUID rightId,
                                       Map<UUID, OwnerBatchSnapshot.PetSummary> summaries,
                                       double originX, double originY, double originZ) {
        OwnerBatchSnapshot.PetSummary left = summaries.get(leftId);
        OwnerBatchSnapshot.PetSummary right = summaries.get(rightId);
        double leftDistance = left == null ? Double.MAX_VALUE : distanceSquared(left, originX, originY, originZ);
        double rightDistance = right == null ? Double.MAX_VALUE : distanceSquared(right, originX, originY, originZ);
        return Double.compare(leftDistance, rightDistance);
    }

    private static double distanceSquared(OwnerBatchSnapshot.PetSummary entry,
                                           double originX,
                                           double originY,
                                           double originZ) {
        double dx = entry.x() - originX;
        double dy = entry.y() - originY;
        double dz = entry.z() - originZ;
        return (dx * dx) + (dy * dy) + (dz * dz);
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
            this.transmissions = transmissions == null || transmissions.isEmpty()
                ? Map.of()
                : Map.copyOf(transmissions);
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
