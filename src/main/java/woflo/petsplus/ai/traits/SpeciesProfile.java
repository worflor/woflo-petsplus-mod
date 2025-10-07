package woflo.petsplus.ai.traits;

import woflo.petsplus.ai.behavior.variants.BehaviorVariant;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration profile for species-specific behavior traits.
 */
public record SpeciesProfile(
    float approachResponseModifier,
    List<Class<? extends BehaviorVariant>> preferredVariants,
    float energyMultiplier,
    Map<PetComponent.Mood, Float> moodBiases
) {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private float approachResponseModifier = 1.0f;
        private final List<Class<? extends BehaviorVariant>> preferredVariants = new ArrayList<>();
        private float energyMultiplier = 1.0f;
        private final Map<PetComponent.Mood, Float> moodBiases = new HashMap<>();
        
        public Builder approachResponseModifier(float modifier) {
            this.approachResponseModifier = modifier;
            return this;
        }
        
        @SafeVarargs
        public final Builder preferredVariants(Class<? extends BehaviorVariant>... variants) {
            for (Class<? extends BehaviorVariant> variant : variants) {
                this.preferredVariants.add(variant);
            }
            return this;
        }
        
        public Builder energyMultiplier(float multiplier) {
            this.energyMultiplier = multiplier;
            return this;
        }
        
        public Builder moodBias(PetComponent.Mood mood, float bias) {
            this.moodBiases.put(mood, bias);
            return this;
        }
        
        public SpeciesProfile build() {
            return new SpeciesProfile(
                approachResponseModifier,
                List.copyOf(preferredVariants),
                energyMultiplier,
                Map.copyOf(moodBiases)
            );
        }
    }
}
