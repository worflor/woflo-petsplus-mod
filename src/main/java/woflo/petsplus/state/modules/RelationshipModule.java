package woflo.petsplus.state.modules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.relationships.InteractionType;
import woflo.petsplus.state.relationships.RelationshipProfile;
import woflo.petsplus.state.relationships.RelationshipType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module for managing relationships between a pet and any living entity.
 * Entity-agnostic, mod-agnostic design - works with players, mobs, modded entities.
 */
public interface RelationshipModule extends DataBackedModule<RelationshipModule.Data> {
    
    /**
     * Get relationship profile with a specific entity.
     * Returns null if no relationship exists.
     */
    @Nullable
    RelationshipProfile getRelationship(UUID entityId);
    
    /**
     * Record an interaction with an entity.
     * Creates new relationship if none exists.
     * 
     * @param entityId UUID of the interacting entity
     * @param interactionType type of interaction
     * @param currentTick current world time
     * @param trustMultiplier contextual scaling for trust change
     * @param affectionMultiplier contextual scaling for affection change
     * @param respectMultiplier contextual scaling for respect change
     */
    void recordInteraction(
        UUID entityId,
        InteractionType interactionType,
        long currentTick,
        float trustMultiplier,
        float affectionMultiplier,
        float respectMultiplier
    );
    
    /**
     * Get trust level with an entity (-1.0 to 1.0).
     * Returns 0.0 if no relationship exists.
     */
    float getTrust(UUID entityId);
    
    /**
     * Get familiarity level with an entity (0.0 to 1.0).
     * Returns 0.0 if no relationship exists.
     */
    float getFamiliarity(UUID entityId);
    
    /**
     * Get computed relationship type with an entity.
     * Returns STRANGER if no relationship exists.
     */
    RelationshipType getRelationshipType(UUID entityId);
    
    /**
     * Apply decay to all relationships based on time passed.
     * 
     * @param currentTick current world time
     * @param decayRate decay rate per tick
     */
    void applyDecay(long currentTick, float decayRate);
    
    /**
     * Remove stale relationships that have been inactive too long.
     * 
     * @param currentTick current world time
     * @param maxIdleTicks maximum idle time before cleanup
     * @return number of relationships removed
     */
    int cleanupStaleRelationships(long currentTick, long maxIdleTicks);
    
    /**
     * Get all relationship profiles, sorted by trust level descending.
     */
    List<RelationshipProfile> getAllRelationships();
    
    /**
     * Get all relationships matching a specific type.
     */
    List<RelationshipProfile> getRelationshipsByType(RelationshipType type);
    
    /**
     * Get relationships with trust above threshold.
     */
    List<RelationshipProfile> getTrustedEntities(float minTrust);
    
    /**
     * Get total count of relationships being tracked.
     */
    int getRelationshipCount();
    
    /**
     * Check if entity has any relationship (even minimal).
     */
    boolean hasRelationship(UUID entityId);

    /**
     * Remove a specific relationship.
     */
    void removeRelationship(UUID entityId);
    
    /**
     * Clear all relationships.
     */
    void clearAllRelationships();

    /**
     * Get last decay check time.
     */
    long getLastDecayTick();

    /**
     * Provide current harmony compatibility information for downstream systems.
     */
    default void applyHarmonyCompatibility(Map<UUID, woflo.petsplus.state.PetComponent.HarmonyCompatibility> compatibilities, long tick) {
    }

    /**
     * Snapshot of known harmony compatibilities keyed by entity id.
     */
    default Map<UUID, woflo.petsplus.state.PetComponent.HarmonyCompatibility> getHarmonyCompatibilities() {
        return Map.of();
    }

    /**
     * Lookup harmony compatibility for a specific entity.
     */
    default woflo.petsplus.state.PetComponent.HarmonyCompatibility getHarmonyCompatibility(UUID entityId) {
        return null;
    }
    
    // ========== Species Memory Methods ==========
    
    /**
     * Record an interaction with a wild animal species.
     * 
     * @param speciesId identifier for the species (e.g., EntityType key)
     * @param context context of the interaction
     * @param intensity intensity of the interaction (-1.0 to 1.0)
     * @param currentTick current world time
     */
    void recordSpeciesInteraction(String speciesId, String context, float intensity, long currentTick);
    
    /**
     * Get fear level toward a species.
     * 
     * @param speciesId identifier for the species
     * @return fear level (0.0 to 1.0)
     */
    float getSpeciesFear(String speciesId);
    
    /**
     * Get hunting preference toward a species.
     * 
     * @param speciesId identifier for the species
     * @return hunting preference level (0.0 to 1.0)
     */
    float getSpeciesHuntingPreference(String speciesId);
    
    /**
     * Get caution level toward a species.
     * 
     * @param speciesId identifier for the species
     * @return caution level (0.0 to 1.0)
     */
    float getSpeciesCaution(String speciesId);
    
    /**
     * Check if the pet has significant memory of a species.
     * 
     * @param speciesId identifier for the species
     * @return true if memory is significant enough to influence behavior
     */
    boolean hasSignificantSpeciesMemory(String speciesId);
    
    /**
     * Apply decay to all species memories.
     * 
     * @param currentTick current world time
     * @param decayRate multiplier applied to the base species-memory decay rate
     */
    void applySpeciesMemoryDecay(long currentTick, float decayRate);
    
    /**
     * Data record for serialization.
     */
    record Data(
        List<SerializedRelationship> relationships,
        long lastDecayTick,
        woflo.petsplus.state.relationships.SpeciesMemory.Data speciesMemory
    ) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                SerializedRelationship.CODEC.listOf().fieldOf("relationships").forGetter(Data::relationships),
                Codec.LONG.optionalFieldOf("lastDecayTick", 0L).forGetter(Data::lastDecayTick),
                woflo.petsplus.state.relationships.SpeciesMemory.Data.CODEC.optionalFieldOf("speciesMemory", 
                    new woflo.petsplus.state.relationships.SpeciesMemory.Data(java.util.List.of(), 0L)).forGetter(Data::speciesMemory)
            ).apply(instance, Data::new)
        );
    }
    
    /**
     * Serialized relationship entry for NBT storage.
     */
    record SerializedRelationship(
        UUID entityId,
        boolean familiarityEstablished,
        float trust,
        float affection,
        float respect,
        long lastInteractionTick,
        long firstInteractionTick
    ) {
        public static final Codec<SerializedRelationship> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Uuids.CODEC.fieldOf("entityId").forGetter(SerializedRelationship::entityId),
                Codec.BOOL.optionalFieldOf("familiarityEstablished", false).forGetter(SerializedRelationship::familiarityEstablished),
                Codec.FLOAT.fieldOf("trust").forGetter(SerializedRelationship::trust),
                Codec.FLOAT.optionalFieldOf("affection", 0.0f).forGetter(SerializedRelationship::affection),
                Codec.FLOAT.optionalFieldOf("respect", 0.0f).forGetter(SerializedRelationship::respect),
                Codec.LONG.fieldOf("lastInteractionTick").forGetter(SerializedRelationship::lastInteractionTick),
                Codec.LONG.fieldOf("firstInteractionTick").forGetter(SerializedRelationship::firstInteractionTick)
            ).apply(instance, SerializedRelationship::new)
        );
        
        /**
         * Convert to RelationshipProfile.
         * Note: Interaction history is not persisted, will be empty on load.
         */
        public RelationshipProfile toProfile() {
            return new RelationshipProfile(
                entityId,
                familiarityEstablished,
                trust,
                affection,
                respect,
                new java.util.ArrayList<>(), // Empty interaction history
                lastInteractionTick,
                firstInteractionTick
            );
        }
        
        /**
         * Create from RelationshipProfile.
         */
        public static SerializedRelationship fromProfile(RelationshipProfile profile) {
            return new SerializedRelationship(
                profile.entityId(),
                profile.familiarityEstablished(),
                profile.trust(),
                profile.affection(),
                profile.respect(),
                profile.lastInteractionTick(),
                profile.firstInteractionTick()
            );
        }
    }
}
