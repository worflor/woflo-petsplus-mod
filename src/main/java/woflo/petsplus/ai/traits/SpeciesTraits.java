package woflo.petsplus.ai.traits;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.ai.behavior.variants.*;
import woflo.petsplus.state.PetComponent;

import java.util.HashMap;
import java.util.Map;

/**
 * Species-specific behavior trait configuration.
 * 
 * <p>Maps entity types to behavior profiles that modify interaction responses
 * and preferred behavior variants. This creates species-authentic interactions:
 * 
 * <p><b>Canids (Wolf, Fox):</b> Highly responsive, energetic, social. Prefer
 * bouncing and circling behaviors. 1.1-1.2x response rate.
 * 
 * <p><b>Felines (Cat, Ocelot):</b> Moderately responsive, more selective.
 * Independent mood bias increases. Prefer gentle nuzzling. 0.7-0.8x response rate.
 * 
 * <p><b>Equines (Horse, Donkey, Mule):</b> Reserved, calm responders.
 * Only basic approach behavior. 0.5-0.6x response rate.
 * 
 * <p><b>Avians (Parrot):</b> Flighty, rare responders. Quick movements.
 * 0.4x response rate.
 * 
 * <p>Energy multipliers further differentiate movement speeds and stamina
 * consumption during responses.
 */
public final class SpeciesTraits {
    
    private static final Map<EntityType<?>, SpeciesProfile> PROFILES = new HashMap<>();
    
    private static final SpeciesProfile DEFAULT_PROFILE = SpeciesProfile.builder()
        .approachResponseModifier(0.9f)
        .preferredVariants(BasicApproachVariant.class)
        .energyMultiplier(1.0f)
        .build();
    
    static {
        // Canids - highly responsive, energetic
        PROFILES.put(EntityType.WOLF, SpeciesProfile.builder()
            .approachResponseModifier(1.2f)
            .preferredVariants(PlayfulBounceVariant.class, ExcitedCircleVariant.class, AffectionateNuzzleVariant.class)
            .energyMultiplier(1.3f)
            .build());
        
        PROFILES.put(EntityType.FOX, SpeciesProfile.builder()
            .approachResponseModifier(1.1f)
            .preferredVariants(PlayfulBounceVariant.class, AffectionateNuzzleVariant.class)
            .energyMultiplier(1.2f)
            .build());
        
        // Felines - moderate response, more independent/calm
        PROFILES.put(EntityType.CAT, SpeciesProfile.builder()
            .approachResponseModifier(0.8f)
            .preferredVariants(AffectionateNuzzleVariant.class, BasicApproachVariant.class)
            .energyMultiplier(0.9f)
            .moodBias(PetComponent.Mood.CALM, 1.2f)
            .build());
        
        PROFILES.put(EntityType.OCELOT, SpeciesProfile.builder()
            .approachResponseModifier(0.7f)
            .preferredVariants(AffectionateNuzzleVariant.class, BasicApproachVariant.class)
            .energyMultiplier(0.85f)
            .moodBias(PetComponent.Mood.CALM, 1.3f)
            .build());
        
        // Equines - lower response, calm
        PROFILES.put(EntityType.HORSE, SpeciesProfile.builder()
            .approachResponseModifier(0.5f)
            .preferredVariants(BasicApproachVariant.class)
            .energyMultiplier(0.7f)
            .build());
        
        PROFILES.put(EntityType.DONKEY, SpeciesProfile.builder()
            .approachResponseModifier(0.6f)
            .preferredVariants(BasicApproachVariant.class)
            .energyMultiplier(0.75f)
            .build());
        
        PROFILES.put(EntityType.MULE, SpeciesProfile.builder()
            .approachResponseModifier(0.55f)
            .preferredVariants(BasicApproachVariant.class)
            .energyMultiplier(0.72f)
            .build());
        
        // Avians - lowest response, flighty
        PROFILES.put(EntityType.PARROT, SpeciesProfile.builder()
            .approachResponseModifier(0.4f)
            .preferredVariants(PlayfulBounceVariant.class)
            .energyMultiplier(0.8f)
            .build());
    }
    
    private SpeciesTraits() {
        // Utility class
    }
    
    /**
     * Get the approach response modifier for a mob species.
     * 
     * @param mob The mob entity
     * @return Response modifier (0.0-2.0, default 1.0)
     */
    public static float getApproachResponseModifier(MobEntity mob) {
        return getProfile(mob).approachResponseModifier();
    }
    
    /**
     * Get the energy multiplier for a mob species.
     * 
     * @param mob The mob entity
     * @return Energy multiplier (0.0-2.0, default 1.0)
     */
    public static float getEnergyMultiplier(MobEntity mob) {
        return getProfile(mob).energyMultiplier();
    }
    
    /**
     * Get the complete species profile for a mob.
     * 
     * @param mob The mob entity
     * @return Species profile
     */
    public static SpeciesProfile getProfile(MobEntity mob) {
        return PROFILES.getOrDefault(mob.getType(), DEFAULT_PROFILE);
    }
    
    /**
     * Get mood bias for a specific mood and species.
     * 
     * @param mob The mob entity
     * @param mood The mood to check
     * @return Mood bias multiplier (default 1.0)
     */
    public static float getMoodBias(MobEntity mob, PetComponent.Mood mood) {
        SpeciesProfile profile = getProfile(mob);
        return profile.moodBiases().getOrDefault(mood, 1.0f);
    }
    
    /**
     * Get the preferred behavior variant for current context.
     * 
     * @param mob The mob entity
     * @param variantIndex Priority index (0 = most preferred)
     * @return Variant class, or null if no preference
     */
    public static Class<? extends BehaviorVariant> getPreferredVariant(MobEntity mob, int variantIndex) {
        SpeciesProfile profile = getProfile(mob);
        if (variantIndex < 0 || variantIndex >= profile.preferredVariants().size()) {
            return null;
        }
        return profile.preferredVariants().get(variantIndex);
    }
}
