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
    Map<PetComponent.Mood, Float> moodBiases,
    // Species tags: multi-tag gating - cached boolean flags
    boolean felineLike,
    boolean canineLike,
    boolean avian,
    boolean aquatic,
    boolean reptileShelly,
    boolean playfulSpecies,
    boolean groomingSpecies,
    boolean flierPercher,
    boolean scentDriven,
    boolean curiousScavenger,
    boolean socialAffiliative,
    boolean sunBasker,
    boolean nocturnalLeaning
) {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private float approachResponseModifier = 1.0f;
        private final List<Class<? extends BehaviorVariant>> preferredVariants = new ArrayList<>();
        private float energyMultiplier = 1.0f;
        private final Map<PetComponent.Mood, Float> moodBiases = new HashMap<>();
        
        // Species tags: multi-tag gating - cached boolean flags with defaults
        private boolean felineLike = false;
        private boolean canineLike = false;
        private boolean avian = false;
        private boolean aquatic = false;
        private boolean reptileShelly = false;
        private boolean playfulSpecies = false;
        private boolean groomingSpecies = false;
        private boolean flierPercher = false;
        private boolean scentDriven = false;
        private boolean curiousScavenger = false;
        private boolean socialAffiliative = false;
        private boolean sunBasker = false;
        private boolean nocturnalLeaning = false;
        
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
        
        // Species tags: multi-tag gating - boolean flag setters
        public Builder felineLike(boolean value) {
            this.felineLike = value;
            return this;
        }
        
        public Builder canineLike(boolean value) {
            this.canineLike = value;
            return this;
        }
        
        public Builder avian(boolean value) {
            this.avian = value;
            return this;
        }
        
        public Builder aquatic(boolean value) {
            this.aquatic = value;
            return this;
        }
        
        public Builder reptileShelly(boolean value) {
            this.reptileShelly = value;
            return this;
        }
        
        public Builder playfulSpecies(boolean value) {
            this.playfulSpecies = value;
            return this;
        }
        
        public Builder groomingSpecies(boolean value) {
            this.groomingSpecies = value;
            return this;
        }
        
        public Builder flierPercher(boolean value) {
            this.flierPercher = value;
            return this;
        }
        
        public Builder scentDriven(boolean value) {
            this.scentDriven = value;
            return this;
        }
        
        public Builder curiousScavenger(boolean value) {
            this.curiousScavenger = value;
            return this;
        }
        
        public Builder socialAffiliative(boolean value) {
            this.socialAffiliative = value;
            return this;
        }
        
        public Builder sunBasker(boolean value) {
            this.sunBasker = value;
            return this;
        }
        
        public Builder nocturnalLeaning(boolean value) {
            this.nocturnalLeaning = value;
            return this;
        }
        
        public SpeciesProfile build() {
            return new SpeciesProfile(
                approachResponseModifier,
                List.copyOf(preferredVariants),
                energyMultiplier,
                Map.copyOf(moodBiases),
                felineLike,
                canineLike,
                avian,
                aquatic,
                reptileShelly,
                playfulSpecies,
                groomingSpecies,
                flierPercher,
                scentDriven,
                curiousScavenger,
                socialAffiliative,
                sunBasker,
                nocturnalLeaning
            );
        }
    }
}
