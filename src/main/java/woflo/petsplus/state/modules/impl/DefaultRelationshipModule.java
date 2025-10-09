package woflo.petsplus.state.modules.impl;

import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.modules.RelationshipModule;
import woflo.petsplus.state.relationships.InteractionType;
import woflo.petsplus.state.relationships.RelationshipProfile;
import woflo.petsplus.state.relationships.RelationshipType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of RelationshipModule.
 * Manages a map of entity UUID to relationship profiles.
 */
public class DefaultRelationshipModule implements RelationshipModule {
    
    private PetComponent parent;
    private final Map<UUID, RelationshipProfile> relationships = new HashMap<>();
    private final woflo.petsplus.state.relationships.SpeciesMemory speciesMemory = new woflo.petsplus.state.relationships.SpeciesMemory();
    private long lastDecayTick = 0;
    
    @Override
    public void onAttach(PetComponent parent) {
        this.parent = parent;
    }
    
    @Override
    @Nullable
    public RelationshipProfile getRelationship(UUID entityId) {
        return relationships.get(entityId);
    }
    
    @Override
    public void recordInteraction(
        UUID entityId,
        InteractionType interactionType,
        long currentTick,
        float trustMultiplier,
        float affectionMultiplier,
        float respectMultiplier
    ) {
        if (entityId == null || interactionType == null) {
            return;
        }
        
        // Get or create relationship profile
        RelationshipProfile profile = relationships.get(entityId);
        if (profile == null) {
            profile = RelationshipProfile.createNew(entityId, currentTick);
        }
        
        // Apply interaction with contextual scaling
        InteractionType.DimensionalResult result = interactionType.scaled(
            trustMultiplier,
            affectionMultiplier,
            respectMultiplier
        );
        
        if (!result.isEmpty()) {
            RelationshipProfile updated = profile.recordInteraction(interactionType, result, currentTick);
            relationships.put(entityId, updated);
        }
    }
    
    @Override
    public float getTrust(UUID entityId) {
        RelationshipProfile profile = relationships.get(entityId);
        return profile != null ? profile.trust() : 0.0f;
    }
    
    @Override
    public float getFamiliarity(UUID entityId) {
        RelationshipProfile profile = relationships.get(entityId);
        return profile != null ? profile.getFamiliarity() : 0.0f;
    }
    
    @Override
    public RelationshipType getRelationshipType(UUID entityId) {
        RelationshipProfile profile = relationships.get(entityId);
        return profile != null ? profile.getType() : RelationshipType.STRANGER;
    }
    
    @Override
    public void applyDecay(long currentTick, float decayRate) {
        if (relationships.isEmpty()) {
            lastDecayTick = currentTick;
            return;
        }
        
        // Apply recalculation (which includes decay weighting) to all relationships
        List<UUID> toUpdate = new ArrayList<>(relationships.keySet());
        for (UUID entityId : toUpdate) {
            RelationshipProfile profile = relationships.get(entityId);
            if (profile != null) {
                RelationshipProfile recalculated = profile.recalculate(currentTick);
                relationships.put(entityId, recalculated);
            }
        }
        
        lastDecayTick = currentTick;
    }
    
    @Override
    public int cleanupStaleRelationships(long currentTick, long maxIdleTicks) {
        int removed = 0;
        Iterator<Map.Entry<UUID, RelationshipProfile>> iterator = relationships.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<UUID, RelationshipProfile> entry = iterator.next();
            RelationshipProfile profile = entry.getValue();
            
            // Check if relationship should be pruned (stale/insignificant)
            if (profile.shouldPrune() || (currentTick - profile.lastInteractionTick() > maxIdleTicks)) {
                iterator.remove();
                removed++;
            }
        }
        
        return removed;
    }
    
    @Override
    public List<RelationshipProfile> getAllRelationships() {
        return relationships.values().stream()
            .sorted(Comparator.comparing(RelationshipProfile::trust).reversed())
            .collect(Collectors.toList());
    }
    
    @Override
    public List<RelationshipProfile> getRelationshipsByType(RelationshipType type) {
        return relationships.values().stream()
            .filter(profile -> profile.getType() == type)
            .sorted(Comparator.comparing(RelationshipProfile::trust).reversed())
            .collect(Collectors.toList());
    }
    
    @Override
    public List<RelationshipProfile> getTrustedEntities(float minTrust) {
        return relationships.values().stream()
            .filter(profile -> profile.trust() >= minTrust)
            .sorted(Comparator.comparing(RelationshipProfile::trust).reversed())
            .collect(Collectors.toList());
    }
    
    @Override
    public int getRelationshipCount() {
        return relationships.size();
    }
    
    @Override
    public boolean hasRelationship(UUID entityId) {
        return relationships.containsKey(entityId);
    }
    
    @Override
    public void removeRelationship(UUID entityId) {
        relationships.remove(entityId);
    }
    
    @Override
    public void clearAllRelationships() {
        relationships.clear();
    }
    
    @Override
    public long getLastDecayTick() {
        return lastDecayTick;
    }
    
    @Override
    public Data toData() {
        List<SerializedRelationship> serialized = relationships.values().stream()
            .map(SerializedRelationship::fromProfile)
            .collect(Collectors.toList());
        
        return new Data(serialized, lastDecayTick, speciesMemory.toData());
    }
    
    @Override
    public void fromData(Data data) {
        relationships.clear();
        
        if (data.relationships() != null) {
            for (SerializedRelationship serialized : data.relationships()) {
                RelationshipProfile profile = serialized.toProfile();
                relationships.put(profile.entityId(), profile);
            }
        }
        
        lastDecayTick = data.lastDecayTick();
        
        // Load species memory
        if (data.speciesMemory() != null) {
            speciesMemory.fromData(data.speciesMemory());
        }
    }
    
    // ========== Species Memory Methods ==========
    
    @Override
    public void recordSpeciesInteraction(String speciesId, String context, float intensity, long currentTick) {
        // Convert String speciesId to EntityType if needed
        // For now, using a simplified approach
        // TODO: Implement proper conversion if needed
    }
    
    @Override
    public float getSpeciesFear(String speciesId) {
        // Convert String speciesId to EntityType if needed
        return 0.0f; // TODO: Implement proper conversion
    }
    
    @Override
    public float getSpeciesHuntingPreference(String speciesId) {
        return 0.0f; // TODO: Implement proper conversion
    }
    
    @Override
    public float getSpeciesCaution(String speciesId) {
        return 0.0f; // TODO: Implement proper conversion
    }
    
    @Override
    public boolean hasSignificantSpeciesMemory(String speciesId) {
        return false; // TODO: Implement proper conversion
    }
    
    @Override
    public void applySpeciesMemoryDecay(long currentTick, float decayRate) {
        speciesMemory.applyDecay(currentTick, decayRate);
    }
    
    // Direct access methods for internal use with EntityType
    public void recordSpeciesInteractionDirect(
        net.minecraft.entity.EntityType<?> species,
        woflo.petsplus.state.relationships.SpeciesMemory.InteractionContext context
    ) {
        speciesMemory.recordInteraction(species, context);
    }
    
    public float getSpeciesFearDirect(net.minecraft.entity.EntityType<?> species) {
        return speciesMemory.getFear(species);
    }
    
    public float getSpeciesHuntingPreferenceDirect(net.minecraft.entity.EntityType<?> species) {
        return speciesMemory.getHuntingPreference(species);
    }
    
    public float getSpeciesCautionDirect(net.minecraft.entity.EntityType<?> species) {
        return speciesMemory.getCaution(species);
    }
    
    public boolean hasMemoryOfSpeciesDirect(net.minecraft.entity.EntityType<?> species) {
        return speciesMemory.hasMemoryOf(species);
    }
    
    public woflo.petsplus.state.relationships.SpeciesMemory getSpeciesMemory() {
        return speciesMemory;
    }
}
