package woflo.petsplus.social.gossip.handler;

import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.context.perception.PerceptionStimulus;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates emergent gossip topic identifiers from context without hardcoding.
 * <p>
 * Topics are created by combining:
 * - Stimulus type (emotion, combat, owner_activity, etc.)
 * - StateKey paths (combat_engaged, arcane_energy, etc.)
 * - Payload data (emotion type, mob type, activity type)
 * <p>
 * This allows infinite topic variations driven by actual game state rather than
 * predefined constants. Topics like "emotion:frightened:combat_active" or
 * "combat:zombie:arcane_high" emerge naturally.
 * <p>
 * Narrative templates are populated with context-aware substitutions:
 * - {pet} → pet name
 * - {owner} → owner name
 * - {emotion} → emotion type from payload
 * - {mob_type} → mob type from combat payload
 * - {intensity} → calculated intensity value
 * - {location} → biome/dimension from context
 */
public final class GossipTopicBuilder {
    
    private final List<String> pathSegments = new ArrayList<>();
    private String namespace = Petsplus.MOD_ID;
    
    private GossipTopicBuilder() {}
    
    /**
     * Creates a new topic builder starting with the stimulus type.
     */
    public static GossipTopicBuilder fromStimulus(PerceptionStimulus stimulus) {
        GossipTopicBuilder builder = new GossipTopicBuilder();
        builder.pathSegments.add(normalizeSegment(stimulus.type().toString()));
        return builder;
    }
    
    /**
     * Creates a new topic builder from a base path.
     */
    public static GossipTopicBuilder fromPath(String basePath) {
        GossipTopicBuilder builder = new GossipTopicBuilder();
        builder.pathSegments.add(normalizeSegment(basePath));
        return builder;
    }
    
    /**
     * Appends a context segment to the topic path.
     * Null or empty segments are ignored.
     */
    public GossipTopicBuilder append(String segment) {
        if (segment != null && !segment.isEmpty()) {
            pathSegments.add(normalizeSegment(segment));
        }
        return this;
    }
    
    /**
     * Appends a StateKey context if the state is present.
     * Example: appendState("combat_engaged", true) → adds "combat" if true
     */
    public GossipTopicBuilder appendState(String stateKey, boolean active) {
        if (active) {
            pathSegments.add(normalizeSegment(stateKey));
        }
        return this;
    }
    
    /**
     * Appends an intensity qualifier based on numeric value.
     * low/medium/high/extreme for 0-0.3, 0.3-0.6, 0.6-0.85, 0.85+
     */
    public GossipTopicBuilder appendIntensity(double intensity) {
        String qualifier;
        if (intensity < 0.3) {
            qualifier = "low";
        } else if (intensity < 0.6) {
            qualifier = "medium";
        } else if (intensity < 0.85) {
            qualifier = "high";
        } else {
            qualifier = "extreme";
        }
        pathSegments.add(qualifier);
        return this;
    }
    
    /**
     * Sets a custom namespace for the topic (default: petsplus)
     */
    public GossipTopicBuilder withNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }
    
    /**
     * Builds the final topic identifier from the accumulated path.
     * Example: emotion/frightened/combat_active → petsplus:emotion.frightened.combat_active
     */
    public Identifier buildIdentifier() {
        if (pathSegments.isEmpty()) {
            return Identifier.of(namespace, "gossip.unknown");
        }
        
        // Join segments with dots for hierarchical topic IDs
        String path = String.join(".", pathSegments);
        return Identifier.of(namespace, "gossip." + path);
    }
    
    /**
     * Builds the final topic ID as a long for RumorEntry storage.
     * Uses UUID-based hashing like GossipTopics.concrete()
     */
    public long build() {
        Identifier id = buildIdentifier();
        String key = id.getNamespace() + ":" + id.getPath();
        
        // Same hashing algorithm as GossipTopics.computeId()
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(
            key.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return most ^ least;
    }
    
    /**
     * Builds a narrative template identifier for translation lookup.
     * Example: emotion.frightened.combat_active → petsplus.gossip.emotion.frightened.combat_active.narrative
     */
    public String buildNarrativeKey() {
        Identifier topicId = buildIdentifier();
        return topicId.getNamespace() + "." + topicId.getPath() + ".narrative";
    }
    
    /**
     * Normalizes a segment to lowercase, replacing spaces/underscores with safe characters.
     */
    private static String normalizeSegment(String segment) {
        return segment.toLowerCase(Locale.ROOT)
            .replace(' ', '_')
            .replace('-', '_')
            .replaceAll("[^a-z0-9_]", "");
    }
    
    /**
     * Utility: Creates a context-aware substitution map for narrative templates.
     * 
     * @param pet The pet generating gossip
     * @param stimulus The perception stimulus
     * @return Map of placeholder → value for template substitution
     */
    public static java.util.Map<String, String> buildSubstitutions(
        PetComponent pet, 
        PerceptionStimulus stimulus
    ) {
        java.util.Map<String, String> subs = new java.util.HashMap<>();
        
        // Always available
        subs.put("pet", pet.getPet() != null ? pet.getPet().getName().getString() : "Pet");
        subs.put("owner", pet.getOwner() != null ? pet.getOwner().getName().getString() : "Owner");
        subs.put("stimulus_type", stimulus.type().toString());
        
        // Extract from payload if available
        Object payload = stimulus.payload();
        if (payload != null) {
            // Emotion payloads
            if (payload instanceof woflo.petsplus.state.emotions.PetMoodEngine.EmotionSnapshot emotion) {
                subs.put("emotion", emotion.emotion().name().toLowerCase());
                subs.put("intensity", String.format("%.2f", emotion.intensity()));
            }
            
            // Add more payload extraction as needed
            // Combat, owner activity, crowd summaries, etc.
        }
        
        return subs;
    }
    
    /**
     * Applies substitutions to a narrative template.
     * Example: "{pet} saw {owner} defeat {mob_type}" → "Fluffy saw Steve defeat zombie"
     */
    public static String applySubstitutions(String template, java.util.Map<String, String> substitutions) {
        String result = template;
        for (var entry : substitutions.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
