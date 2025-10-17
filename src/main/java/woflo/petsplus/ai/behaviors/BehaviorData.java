package woflo.petsplus.ai.behaviors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import woflo.petsplus.state.PetComponent;

import java.util.List;
import java.util.Map;

/**
 * Unified behavior data model representing a complete behavior definition.
 * Each behavior contains all the information needed to configure and run a goal.
 */
public class BehaviorData {
    public static final Codec<BehaviorData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("goal_class").forGetter(BehaviorData::goalClass),
            Codec.STRING.listOf().optionalFieldOf("mutex_flags", List.of()).forGetter(BehaviorData::mutexFlags),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("parameters", Map.of()).forGetter(BehaviorData::parameters),
            Requirements.CODEC.fieldOf("requirements").forGetter(BehaviorData::requirements),
            Triggers.CODEC.fieldOf("triggers").forGetter(BehaviorData::triggers),
            Feedback.CODEC.fieldOf("feedback").forGetter(BehaviorData::feedback),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("associated_data", Map.of()).forGetter(BehaviorData::associatedData),
            UrgencyCalculation.CODEC.fieldOf("urgency_calculation").forGetter(BehaviorData::urgencyCalculation)
        ).apply(instance, BehaviorData::new)
    );

    private final String goalClass;
    private final List<String> mutexFlags;
    private final Map<String, String> parameters;
    private final Requirements requirements;
    private final Triggers triggers;
    private final Feedback feedback;
    private final Map<String, String> associatedData;
    private final UrgencyCalculation urgencyCalculation;

    public BehaviorData(String goalClass, List<String> mutexFlags, Map<String, String> parameters,
                       Requirements requirements, Triggers triggers, Feedback feedback,
                       Map<String, String> associatedData, UrgencyCalculation urgencyCalculation) {
        this.goalClass = goalClass;
        this.mutexFlags = mutexFlags;
        this.parameters = parameters;
        this.requirements = requirements;
        this.triggers = triggers;
        this.feedback = feedback;
        this.associatedData = associatedData;
        this.urgencyCalculation = urgencyCalculation;
    }

    public String goalClass() { return goalClass; }
    public List<String> mutexFlags() { return mutexFlags; }
    public Map<String, String> parameters() { return parameters; }
    public Requirements requirements() { return requirements; }
    public Triggers triggers() { return triggers; }
    public Feedback feedback() { return feedback; }
    public Map<String, String> associatedData() { return associatedData; }
    public UrgencyCalculation urgencyCalculation() { return urgencyCalculation; }

    /**
     * Requirements that must be met for this behavior to be considered.
     */
    public static class Requirements {
        public static final Codec<Requirements> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.STRING.listOf().optionalFieldOf("allowed_moods", List.of()).forGetter(Requirements::allowedMoods),
                Codec.STRING.optionalFieldOf("min_energy_level", "LOW").forGetter(Requirements::minEnergyLevel),
                Codec.STRING.listOf().optionalFieldOf("capability_requirements", List.of()).forGetter(Requirements::capabilityRequirements),
                Codec.FLOAT.optionalFieldOf("min_bond_level", 0.0f).forGetter(Requirements::minBondLevel)
            ).apply(instance, Requirements::new)
        );

        private final List<String> allowedMoods;
        private final String minEnergyLevel;
        private final List<String> capabilityRequirements;
        private final float minBondLevel;

        public Requirements(List<String> allowedMoods, String minEnergyLevel, 
                           List<String> capabilityRequirements, float minBondLevel) {
            this.allowedMoods = allowedMoods;
            this.minEnergyLevel = minEnergyLevel;
            this.capabilityRequirements = capabilityRequirements;
            this.minBondLevel = minBondLevel;
        }

        public List<String> allowedMoods() { return allowedMoods; }
        public String minEnergyLevel() { return minEnergyLevel; }
        public List<String> capabilityRequirements() { return capabilityRequirements; }
        public float minBondLevel() { return minBondLevel; }

        public boolean meetsRequirements(PetComponent petComponent) {
            // Check mood requirements
            if (!allowedMoods.isEmpty()) {
                boolean hasAllowedMood = false;
                for (String mood : allowedMoods) {
                    try {
                        PetComponent.Mood moodEnum = PetComponent.Mood.valueOf(mood.toUpperCase());
                        if (petComponent.hasMoodAbove(moodEnum, 0.1f)) {
                            hasAllowedMood = true;
                            break;
                        }
                    } catch (IllegalArgumentException e) {
                        // Skip invalid mood names
                    }
                }
                if (!hasAllowedMood) return false;
            }

            // Check energy requirements
            try {
                PetComponent.EnergyLevel requiredLevel = PetComponent.EnergyLevel.valueOf(minEnergyLevel.toUpperCase());
                if (petComponent.getEnergyLevel().ordinal() < requiredLevel.ordinal()) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                // Default to LOW if invalid level
            }

            // Check bond level
            if (petComponent.getBondStrength() < minBondLevel) {
                return false;
            }

            return true;
        }
    }

    /**
     * Triggers that can activate this behavior.
     */
    public static class Triggers {
        public static final Codec<Triggers> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("on_stimulus", "").forGetter(Triggers::onStimulus),
                Codec.BOOL.optionalFieldOf("on_block_interest", false).forGetter(Triggers::onBlockInterest),
                Codec.BOOL.optionalFieldOf("on_entity_nearby", false).forGetter(Triggers::onEntityNearby),
                Codec.BOOL.optionalFieldOf("on_idle", false).forGetter(Triggers::onIdle),
                Codec.BOOL.optionalFieldOf("on_owner_distant", false).forGetter(Triggers::onOwnerDistant),
                Codec.BOOL.optionalFieldOf("on_owner_idle", false).forGetter(Triggers::onOwnerIdle),
                Codec.BOOL.optionalFieldOf("on_owner_nearby", false).forGetter(Triggers::onOwnerNearby),
                Codec.BOOL.optionalFieldOf("on_playful_mood", false).forGetter(Triggers::onPlayfulMood)
            ).apply(instance, Triggers::new)
        );

        private final String onStimulus;
        private final boolean onBlockInterest;
        private final boolean onEntityNearby;
        private final boolean onIdle;
        private final boolean onOwnerDistant;
        private final boolean onOwnerIdle;
        private final boolean onOwnerNearby;
        private final boolean onPlayfulMood;

        public Triggers(String onStimulus, boolean onBlockInterest, boolean onEntityNearby,
                       boolean onIdle, boolean onOwnerDistant, boolean onOwnerIdle,
                       boolean onOwnerNearby, boolean onPlayfulMood) {
            this.onStimulus = onStimulus;
            this.onBlockInterest = onBlockInterest;
            this.onEntityNearby = onEntityNearby;
            this.onIdle = onIdle;
            this.onOwnerDistant = onOwnerDistant;
            this.onOwnerIdle = onOwnerIdle;
            this.onOwnerNearby = onOwnerNearby;
            this.onPlayfulMood = onPlayfulMood;
        }

        public String onStimulus() { return onStimulus; }
        public boolean onBlockInterest() { return onBlockInterest; }
        public boolean onEntityNearby() { return onEntityNearby; }
        public boolean onIdle() { return onIdle; }
        public boolean onOwnerDistant() { return onOwnerDistant; }
        public boolean onOwnerIdle() { return onOwnerIdle; }
        public boolean onOwnerNearby() { return onOwnerNearby; }
        public boolean onPlayfulMood() { return onPlayfulMood; }
    }

    /**
     * Feedback to provide when this behavior completes.
     */
    public static class Feedback {
        public static final Codec<Feedback> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("on_success", "").forGetter(Feedback::onSuccess),
                Codec.INT.optionalFieldOf("energy_cost", 0).forGetter(Feedback::energyCost),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("emotion_rewards", Map.of()).forGetter(Feedback::emotionRewards),
                Codec.STRING.optionalFieldOf("contagion_emotion", "").forGetter(Feedback::contagionEmotion),
                Codec.FLOAT.optionalFieldOf("contagion_strength", 0.0f).forGetter(Feedback::contagionStrength)
            ).apply(instance, Feedback::new)
        );

        private final String onSuccess;
        private final int energyCost;
        private final Map<String, Float> emotionRewards;
        private final String contagionEmotion;
        private final float contagionStrength;

        public Feedback(String onSuccess, int energyCost, Map<String, Float> emotionRewards,
                       String contagionEmotion, float contagionStrength) {
            this.onSuccess = onSuccess;
            this.energyCost = energyCost;
            this.emotionRewards = emotionRewards;
            this.contagionEmotion = contagionEmotion;
            this.contagionStrength = contagionStrength;
        }

        public String onSuccess() { return onSuccess; }
        public int energyCost() { return energyCost; }
        public Map<String, Float> emotionRewards() { return emotionRewards; }
        public String contagionEmotion() { return contagionEmotion; }
        public float contagionStrength() { return contagionStrength; }
    }

    /**
     * Urgency calculation parameters for dynamic goal selection.
     */
    public static class UrgencyCalculation {
        public static final Codec<UrgencyCalculation> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.FLOAT.optionalFieldOf("base_urgency", 0.5f).forGetter(UrgencyCalculation::baseUrgency),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("mood_multipliers", Map.of()).forGetter(UrgencyCalculation::moodMultipliers),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("energy_modifier", Map.of()).forGetter(UrgencyCalculation::energyModifier),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("context_modifiers", Map.of()).forGetter(UrgencyCalculation::contextModifiers)
            ).apply(instance, UrgencyCalculation::new)
        );

        private final float baseUrgency;
        private final Map<String, Float> moodMultipliers;
        private final Map<String, Float> energyModifier;
        private final Map<String, Float> contextModifiers;

        public UrgencyCalculation(float baseUrgency, Map<String, Float> moodMultipliers,
                                 Map<String, Float> energyModifier, Map<String, Float> contextModifiers) {
            this.baseUrgency = baseUrgency;
            this.moodMultipliers = moodMultipliers;
            this.energyModifier = energyModifier;
            this.contextModifiers = contextModifiers;
        }

        public float baseUrgency() { return baseUrgency; }
        public Map<String, Float> moodMultipliers() { return moodMultipliers; }
        public Map<String, Float> energyModifier() { return energyModifier; }
        public Map<String, Float> contextModifiers() { return contextModifiers; }

        /**
         * Calculate the urgency score for this behavior based on pet state.
         */
        public float calculateUrgency(PetComponent petComponent, Map<String, Float> contextValues) {
            float urgency = baseUrgency;

            // Apply mood multipliers
            for (Map.Entry<String, Float> entry : moodMultipliers.entrySet()) {
                try {
                    PetComponent.Mood mood = PetComponent.Mood.valueOf(entry.getKey().toUpperCase());
                    if (petComponent.hasMoodAbove(mood, 0.1f)) {
                        urgency *= entry.getValue();
                    }
                } catch (IllegalArgumentException e) {
                    // Skip invalid mood names
                }
            }

            // Apply energy modifier
            String energyLevel = petComponent.getEnergyLevel().name();
            Float energyMod = energyModifier.get(energyLevel);
            if (energyMod != null) {
                urgency *= energyMod;
            }

            // Apply context modifiers
            for (Map.Entry<String, Float> entry : contextModifiers.entrySet()) {
                Float contextValue = contextValues.get(entry.getKey());
                if (contextValue != null) {
                    urgency *= (1.0f + (contextValue * entry.getValue()));
                }
            }

            return Math.max(0.0f, Math.min(1.0f, urgency));
        }
    }
}