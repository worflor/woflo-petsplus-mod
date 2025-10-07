package woflo.petsplus.state.relationships;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tracks pet's aggregated memories and feelings toward wild animal species.
 * Lightweight, species-level tracking (not per-entity) for emergent hunting preferences and fears.
 * 
 * Design principles:
 * - Only tracks significant interactions
 * - Natural decay over time (no permanent trauma)
 * - Species cap to prevent bloat
 * - Enables emergent behavior without grinding
 */
public class SpeciesMemory {
    
    private static final int MAX_SPECIES_TRACKED = 20;
    private static final long DECAY_INTERVAL_TICKS = 24000L; // 1 Minecraft day
    private static final float DECAY_RATE = 0.05f; // 5% decay per day
    private static final float MIN_SIGNIFICANCE = 0.05f; // Prune below this
    
    private final Map<EntityType<?>, SpeciesRelationship> memories = new HashMap<>();
    private long lastDecayTick = 0;
    
    /**
     * Record an interaction with a wild animal species.
     */
    public void recordInteraction(EntityType<?> species, InteractionContext context) {
        if (species == null) {
            return;
        }
        
        // Get or create species relationship
        SpeciesRelationship relationship = memories.computeIfAbsent(species, k -> new SpeciesRelationship());
        
        // Apply interaction
        relationship.recordInteraction(context);
        
        // Enforce species cap by pruning least significant
        if (memories.size() > MAX_SPECIES_TRACKED) {
            pruneWeakestMemory();
        }
    }
    
    /**
     * Get fear level toward a species (0.0 = no fear, 1.0 = terrified).
     */
    public float getFear(EntityType<?> species) {
        SpeciesRelationship rel = memories.get(species);
        return rel != null ? rel.fear : 0.0f;
    }
    
    /**
     * Get hunting enjoyment toward a species (0.0 = neutral, 1.0 = loves hunting).
     */
    public float getHuntingPreference(EntityType<?> species) {
        SpeciesRelationship rel = memories.get(species);
        return rel != null ? rel.huntingPreference : 0.0f;
    }
    
    /**
     * Get respect/caution toward a species (0.0 = dismissive, 1.0 = highly cautious).
     */
    public float getCaution(EntityType<?> species) {
        SpeciesRelationship rel = memories.get(species);
        return rel != null ? rel.caution : 0.0f;
    }
    
    /**
     * Check if pet has significant memory of this species.
     */
    public boolean hasMemoryOf(EntityType<?> species) {
        SpeciesRelationship rel = memories.get(species);
        return rel != null && rel.getSignificance() > MIN_SIGNIFICANCE;
    }
    
    /**
     * Apply natural memory decay over time.
     */
    public void applyDecay(long currentTick) {
        if (currentTick - lastDecayTick < DECAY_INTERVAL_TICKS) {
            return;
        }
        
        long daysPassed = (currentTick - lastDecayTick) / DECAY_INTERVAL_TICKS;
        if (daysPassed <= 0) {
            return;
        }
        
        lastDecayTick = currentTick;
        
        // Decay all memories
        Iterator<Map.Entry<EntityType<?>, SpeciesRelationship>> iterator = memories.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<EntityType<?>, SpeciesRelationship> entry = iterator.next();
            SpeciesRelationship rel = entry.getValue();
            
            rel.decay(DECAY_RATE * daysPassed);
            
            // Prune insignificant memories
            if (rel.getSignificance() < MIN_SIGNIFICANCE) {
                iterator.remove();
            }
        }
    }
    
    /**
     * Remove the weakest (least significant) memory to maintain cap.
     */
    private void pruneWeakestMemory() {
        EntityType<?> weakest = null;
        float lowestSignificance = Float.MAX_VALUE;
        
        for (Map.Entry<EntityType<?>, SpeciesRelationship> entry : memories.entrySet()) {
            float sig = entry.getValue().getSignificance();
            if (sig < lowestSignificance) {
                lowestSignificance = sig;
                weakest = entry.getKey();
            }
        }
        
        if (weakest != null) {
            memories.remove(weakest);
        }
    }
    
    /**
     * Convert to data for serialization.
     */
    public Data toData() {
        List<SerializedSpeciesMemory> serialized = memories.entrySet().stream()
            .map(entry -> {
                Identifier id = Registries.ENTITY_TYPE.getId(entry.getKey());
                if (id == null) return null;
                SpeciesRelationship rel = entry.getValue();
                return new SerializedSpeciesMemory(
                    id.toString(),
                    rel.fear,
                    rel.huntingPreference,
                    rel.caution,
                    rel.interactionCount
                );
            })
            .filter(s -> s != null)
            .collect(Collectors.toList());
        
        return new Data(serialized, lastDecayTick);
    }
    
    /**
     * Load from data.
     */
    public void fromData(Data data) {
        memories.clear();
        
        if (data.memories() != null) {
            for (SerializedSpeciesMemory serialized : data.memories()) {
                Identifier id = Identifier.tryParse(serialized.speciesId());
                if (id == null) continue;
                
                EntityType<?> type = Registries.ENTITY_TYPE.get(id);
                if (type == null) continue;
                
                SpeciesRelationship rel = new SpeciesRelationship();
                rel.fear = serialized.fear();
                rel.huntingPreference = serialized.huntingPreference();
                rel.caution = serialized.caution();
                rel.interactionCount = serialized.interactionCount();
                memories.put(type, rel);
            }
        }
        
        lastDecayTick = data.lastDecayTick();
    }
    
    /**
     * Represents aggregated feelings toward a specific species.
     */
    public static class SpeciesRelationship {
        private float fear = 0.0f;              // 0.0 to 1.0
        private float huntingPreference = 0.0f; // 0.0 to 1.0
        private float caution = 0.0f;           // 0.0 to 1.0
        private int interactionCount = 0;
        
        void recordInteraction(InteractionContext context) {
            interactionCount++;
            
            // Apply changes with diminishing returns (prevents instant max values)
            float learningRate = 1.0f / (float) Math.sqrt(interactionCount + 1);
            
            fear = MathHelper.clamp(fear + context.fearDelta * learningRate, 0.0f, 1.0f);
            huntingPreference = MathHelper.clamp(huntingPreference + context.huntingDelta * learningRate, 0.0f, 1.0f);
            caution = MathHelper.clamp(caution + context.cautionDelta * learningRate, 0.0f, 1.0f);
        }
        
        void decay(float amount) {
            // Negative emotions decay faster (pets forget trauma)
            fear = Math.max(0.0f, fear - amount * 1.5f);
            // Preferences decay slower (learned behavior persists)
            huntingPreference = Math.max(0.0f, huntingPreference - amount * 0.5f);
            caution = Math.max(0.0f, caution - amount);
        }
        
        float getSignificance() {
            // Total emotional weight
            return fear + huntingPreference + caution;
        }
    }
    
    /**
     * Data record for Codec serialization.
     */
    public record Data(
        List<SerializedSpeciesMemory> memories,
        long lastDecayTick
    ) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                SerializedSpeciesMemory.CODEC.listOf().fieldOf("memories").forGetter(Data::memories),
                Codec.LONG.optionalFieldOf("lastDecayTick", 0L).forGetter(Data::lastDecayTick)
            ).apply(instance, Data::new)
        );
    }
    
    /**
     * Serialized species memory entry.
     */
    public record SerializedSpeciesMemory(
        String speciesId,
        float fear,
        float huntingPreference,
        float caution,
        int interactionCount
    ) {
        public static final Codec<SerializedSpeciesMemory> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.STRING.fieldOf("speciesId").forGetter(SerializedSpeciesMemory::speciesId),
                Codec.FLOAT.fieldOf("fear").forGetter(SerializedSpeciesMemory::fear),
                Codec.FLOAT.fieldOf("huntingPreference").forGetter(SerializedSpeciesMemory::huntingPreference),
                Codec.FLOAT.fieldOf("caution").forGetter(SerializedSpeciesMemory::caution),
                Codec.INT.fieldOf("interactionCount").forGetter(SerializedSpeciesMemory::interactionCount)
            ).apply(instance, SerializedSpeciesMemory::new)
        );
    }
    
    /**
     * Context for a species interaction to determine how it affects memory.
     */
    public static class InteractionContext {
        public final float fearDelta;
        public final float huntingDelta;
        public final float cautionDelta;
        
        public InteractionContext(float fearDelta, float huntingDelta, float cautionDelta) {
            this.fearDelta = fearDelta;
            this.huntingDelta = huntingDelta;
            this.cautionDelta = cautionDelta;
        }
        
        // Preset contexts for common scenarios
        public static InteractionContext petKilledWild(float targetStrength) {
            // Successfully hunting builds preference, reduces fear
            return new InteractionContext(
                -0.1f * targetStrength,           // Reduces fear
                0.3f,                              // Builds hunting preference
                Math.max(0.0f, 0.1f * (targetStrength - 0.5f)) // Caution if strong
            );
        }
        
        public static InteractionContext petAttackedByWild(float damageIntensity, float targetStrength) {
            // Being attacked builds fear and caution
            return new InteractionContext(
                0.2f * damageIntensity * targetStrength, // Fear based on damage and strength
                -0.05f,                                   // Slightly reduces hunting interest
                0.3f * targetStrength                     // Caution based on threat level
            );
        }
        
        public static InteractionContext petKilledByWild(float targetStrength) {
            // Death creates strong fear and caution
            return new InteractionContext(
                0.5f * targetStrength,  // Major fear
                -0.2f,                   // Avoids hunting this species
                0.4f * targetStrength    // High caution
            );
        }
        
        public static InteractionContext observedOwnerHunt(float targetStrength) {
            // Learning from owner: safe to hunt
            return new InteractionContext(
                -0.05f,     // Slight fear reduction
                0.15f,      // Moderate hunting interest
                -0.05f      // Slight caution reduction
            );
        }
        
        public static InteractionContext observedOwnerFeed() {
            // Learning from owner: this species is friendly
            return new InteractionContext(
                -0.1f,  // Fear reduction
                -0.2f,  // Hunting discouraged
                -0.1f   // Caution reduction
            );
        }
    }
}
