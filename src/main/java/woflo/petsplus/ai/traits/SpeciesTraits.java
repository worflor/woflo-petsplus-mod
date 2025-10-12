package woflo.petsplus.ai.traits;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.ai.behavior.variants.*;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.tags.PetsplusEntityTypeTags;

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
    
    /**
     * Helper method to set boolean flags from tags for any entity type.
     * Species tags: multi-tag gating - O(1) lookups at runtime
     */
    private static SpeciesProfile.Builder applyTagFlags(EntityType<?> type, SpeciesProfile.Builder builder) {
        // Species family tags
        if (type.isIn(PetsplusEntityTypeTags.FELINE_LIKE)) {
            builder.felineLike(true);
        }
        if (type.isIn(PetsplusEntityTypeTags.CANINE_LIKE)) {
            builder.canineLike(true);
        }
        if (type.isIn(PetsplusEntityTypeTags.AVIAN)) {
            builder.avian(true);
        }
        if (type.isIn(PetsplusEntityTypeTags.AQUATIC)) {
            builder.aquatic(true);
        }
        if (type.isIn(PetsplusEntityTypeTags.REPTILE_SHELLY)) {
            builder.reptileShelly(true);
        }
        
        // Behavioral trait tags
        if (type.isIn(PetsplusEntityTypeTags.PLAYFUL_SPECIES)) {
            builder.playfulSpecies(true);
        }
        if (type.isIn(PetsplusEntityTypeTags.GROOMING_SPECIES)) {
            builder.groomingSpecies(true);
        }
        if (type.isIn(PetsplusEntityTypeTags.FLIER_PERCHER)) {
            builder.flierPercher(true);
        }
        if (type.isIn(PetsplusEntityTypeTags.SCENT_DRIVEN)) {
            builder.scentDriven(true);
        }
        if (type.isIn(PetsplusEntityTypeTags.CURIOUS_SCAVENGER)) {
            builder.curiousScavenger(true);
        }
        if (type.isIn(PetsplusEntityTypeTags.SOCIAL_AFFILIATIVE)) {
            builder.socialAffiliative(true);
        }
        if (type.isIn(PetsplusEntityTypeTags.SUN_BASKER)) {
            builder.sunBasker(true);
        }
        if (type.isIn(PetsplusEntityTypeTags.NOCTURNAL_LEANING)) {
            builder.nocturnalLeaning(true);
        }
        
        return builder;
    }
    
    static {
        // Canids - highly responsive, energetic
        // Species tags: multi-tag gating
        PROFILES.put(EntityType.WOLF, SpeciesProfile.builder()
            .approachResponseModifier(1.2f)
            .preferredVariants(PlayfulBounceVariant.class, ExcitedCircleVariant.class, AffectionateNuzzleVariant.class)
            .energyMultiplier(1.3f)
            .canineLike(true)
            .scentDriven(true)
            .socialAffiliative(true)
            .build());
        
        PROFILES.put(EntityType.FOX, SpeciesProfile.builder()
            .approachResponseModifier(1.1f)
            .preferredVariants(PlayfulBounceVariant.class, AffectionateNuzzleVariant.class)
            .energyMultiplier(1.2f)
            .canineLike(true)
            .playfulSpecies(true)
            .curiousScavenger(true)
            .scentDriven(true)
            .nocturnalLeaning(true)
            .build());
        
        // Felines - moderate response, more independent/calm
        // Species tags: multi-tag gating
        PROFILES.put(EntityType.CAT, SpeciesProfile.builder()
            .approachResponseModifier(0.8f)
            .preferredVariants(AffectionateNuzzleVariant.class, BasicApproachVariant.class)
            .energyMultiplier(0.9f)
            .moodBias(PetComponent.Mood.CALM, 1.2f)
            .felineLike(true)
            .playfulSpecies(true)
            .groomingSpecies(true)
            .curiousScavenger(true)
            .sunBasker(true)
            .nocturnalLeaning(true)
            .build());
        
        PROFILES.put(EntityType.OCELOT, SpeciesProfile.builder()
            .approachResponseModifier(0.7f)
            .preferredVariants(AffectionateNuzzleVariant.class, BasicApproachVariant.class)
            .energyMultiplier(0.85f)
            .moodBias(PetComponent.Mood.CALM, 1.3f)
            .felineLike(true)
            .curiousScavenger(true)
            .nocturnalLeaning(true)
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
        // Species tags: multi-tag gating
        PROFILES.put(EntityType.PARROT, SpeciesProfile.builder()
            .approachResponseModifier(0.4f)
            .preferredVariants(PlayfulBounceVariant.class)
            .energyMultiplier(0.8f)
            .avian(true)
            .playfulSpecies(true)
            .groomingSpecies(true)
            .flierPercher(true)
            .socialAffiliative(true)
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
        EntityType<?> type = mob.getType();
        SpeciesProfile profile = PROFILES.get(type);
        
        // If no explicit profile exists, create one with tag flags
        if (profile == null) {
            profile = applyTagFlags(type, SpeciesProfile.builder()).build();
        }
        
        return profile;
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
