package woflo.petsplus.ai.context.social;

import woflo.petsplus.state.relationships.RelationshipProfile;
import woflo.petsplus.state.relationships.RelationshipType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot of the social edges known to a pet at capture time. The
 * snapshot is derived from the relationship module so it reflects the same
 * decay and familiarity logic used elsewhere in the personality stack.
 */
public final class SocialSnapshot {

    private static final SocialSnapshot EMPTY = new SocialSnapshot(Map.of());

    private final Map<UUID, Edge> edges;

    public SocialSnapshot(Map<UUID, Edge> edges) {
        if (edges == null || edges.isEmpty()) {
            this.edges = Map.of();
            return;
        }
        this.edges = Collections.unmodifiableMap(new LinkedHashMap<>(edges));
    }

    public static SocialSnapshot empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return edges.isEmpty();
    }

    public Map<UUID, Edge> edges() {
        return edges;
    }

    public List<UUID> trusted(float minimumTrust) {
        if (edges.isEmpty()) {
            return List.of();
        }
        List<UUID> trusted = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Edge> entry : edges.entrySet()) {
            if (entry.getValue().trust() >= minimumTrust) {
                trusted.add(entry.getKey());
            }
        }
        return List.copyOf(trusted);
    }

    public static SocialSnapshot fromRelationships(List<RelationshipProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return empty();
        }
        Map<UUID, Edge> edges = new LinkedHashMap<>();
        for (RelationshipProfile profile : profiles) {
            if (profile == null || profile.entityId() == null) {
                continue;
            }
            edges.put(profile.entityId(), new Edge(
                profile.trust(),
                profile.affection(),
                profile.respect(),
                profile.getComfort(),
                profile.getType()
            ));
        }
        return new SocialSnapshot(edges);
    }

    public record Edge(
        float trust,
        float affection,
        float respect,
        float comfort,
        RelationshipType type
    ) {
        public Edge {
            if (type == null) {
                type = RelationshipType.STRANGER;
            }
        }
    }
}

