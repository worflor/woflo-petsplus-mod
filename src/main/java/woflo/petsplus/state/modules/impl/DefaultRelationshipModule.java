package woflo.petsplus.state.modules.impl;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.context.perception.ContextSlice;
import woflo.petsplus.ai.context.perception.PerceptionBus;
import woflo.petsplus.ai.context.perception.PerceptionStimulus;
import woflo.petsplus.ai.context.perception.PerceptionStimulusType;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.modules.RelationshipModule;
import woflo.petsplus.state.relationships.InteractionType;
import woflo.petsplus.state.relationships.RelationshipProfile;
import woflo.petsplus.state.relationships.RelationshipType;
import woflo.petsplus.state.relationships.SpeciesMemory;

import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.util.math.MathHelper.clamp;

/**
 * Default implementation of RelationshipModule.
 * Manages a map of entity UUID to relationship profiles.
 */
public class DefaultRelationshipModule implements RelationshipModule {
    
    private PetComponent parent;
    private final Map<UUID, RelationshipProfile> relationships = new HashMap<>();
    private final SpeciesMemory speciesMemory = new SpeciesMemory();
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
            if (parent != null) {
                parent.markContextDirty(ContextSlice.SOCIAL, ContextSlice.AGGREGATES);
                PerceptionBus bus = parent.getPerceptionBus();
                if (bus != null) {
                    long tick = 0L;
                    if (parent.getPetEntity() != null && parent.getPetEntity().getEntityWorld() != null) {
                        tick = parent.getPetEntity().getEntityWorld().getTime();
                    }
                    bus.publish(new PerceptionStimulus(
                        PerceptionStimulusType.SOCIAL_GRAPH,
                        tick,
                        EnumSet.of(ContextSlice.SOCIAL),
                        updated
                    ));
                }
            }
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
        EntityType<?> species = resolveSpecies(speciesId);
        if (species == null) {
            return;
        }

        SpeciesMemory.InteractionContext interaction = resolveInteractionContext(context, intensity);
        if (interaction == null) {
            return;
        }

        if (currentTick > 0L) {
            speciesMemory.applyDecay(currentTick, 1.0f);
        }

        speciesMemory.recordInteraction(species, interaction);
        notifySpeciesMemoryUpdated();
    }

    @Override
    public float getSpeciesFear(String speciesId) {
        EntityType<?> species = resolveSpecies(speciesId);
        return species != null ? speciesMemory.getFear(species) : 0.0f;
    }

    @Override
    public float getSpeciesHuntingPreference(String speciesId) {
        EntityType<?> species = resolveSpecies(speciesId);
        return species != null ? speciesMemory.getHuntingPreference(species) : 0.0f;
    }

    @Override
    public float getSpeciesCaution(String speciesId) {
        EntityType<?> species = resolveSpecies(speciesId);
        return species != null ? speciesMemory.getCaution(species) : 0.0f;
    }

    @Override
    public boolean hasSignificantSpeciesMemory(String speciesId) {
        EntityType<?> species = resolveSpecies(speciesId);
        return species != null && speciesMemory.hasMemoryOf(species);
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
        notifySpeciesMemoryUpdated();
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

    @Nullable
    private EntityType<?> resolveSpecies(String speciesId) {
        if (speciesId == null || speciesId.isEmpty()) {
            return null;
        }
        Identifier identifier = Identifier.tryParse(speciesId);
        if (identifier == null) {
            return null;
        }
        return Registries.ENTITY_TYPE.get(identifier);
    }

    @Nullable
    private SpeciesMemory.InteractionContext resolveInteractionContext(String context, float intensity) {
        if (context == null || context.isEmpty()) {
            return SpeciesMemory.InteractionContext.observedOwnerFeed();
        }

        String normalized = context.toLowerCase(Locale.ROOT);
        float normalizedStrength = clampPositive(intensity, 1.0f);

        return switch (normalized) {
            case "pet_killed_wild", "pet_killed", "hunt_success", "successful_hunt" ->
                SpeciesMemory.InteractionContext.petKilledWild(normalizedStrength);
            case "pet_attacked_by_wild", "pet_attacked", "attacked" -> {
                float damage = clampPositive(Math.abs(intensity), 1.0f);
                yield SpeciesMemory.InteractionContext.petAttackedByWild(damage, normalizedStrength);
            }
            case "pet_killed_by_wild", "pet_killed_by", "death" ->
                SpeciesMemory.InteractionContext.petKilledByWild(normalizedStrength);
            case "owner_hunt", "observed_owner_hunt", "owner_killed" ->
                SpeciesMemory.InteractionContext.observedOwnerHunt(normalizedStrength);
            case "owner_feed", "observed_owner_feed", "owner_pamper" ->
                SpeciesMemory.InteractionContext.observedOwnerFeed();
            default -> buildCustomInteraction(intensity);
        };
    }

    private SpeciesMemory.InteractionContext buildCustomInteraction(float intensity) {
        float clamped = MathHelper.clamp(intensity, -1.0f, 1.0f);
        float fearDelta = clamped >= 0.0f ? clamped * 0.3f : clamped * 0.15f;
        float huntingDelta = clamped >= 0.0f ? -clamped * 0.1f : Math.abs(clamped) * 0.25f;
        float cautionDelta = clamped >= 0.0f ? clamped * 0.2f : 0.0f;
        return new SpeciesMemory.InteractionContext(fearDelta, huntingDelta, cautionDelta);
    }

    private float clampPositive(float value, float fallback) {
        float magnitude = Math.abs(value);
        if (magnitude < 1.0E-3f) {
            magnitude = fallback;
        }
        return clamp(magnitude, 0.1f, 3.0f);
    }

    private void notifySpeciesMemoryUpdated() {
        if (parent == null) {
            return;
        }
        parent.markContextDirty(ContextSlice.SOCIAL, ContextSlice.AGGREGATES);
    }
}
