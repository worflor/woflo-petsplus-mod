package woflo.petsplus.tags;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

/**
 * Shared entity type tags used by PetsPlus at runtime.
 * Species tags: multi-tag gating
 */
public final class PetsplusEntityTypeTags {
    public static final TagKey<EntityType<?>> FLYERS = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "flyers")
    );

    public static final TagKey<EntityType<?>> CC_RESISTANT = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "cc_resistant")
    );

    // Species family tags - multi-tag gating
    public static final TagKey<EntityType<?>> FELINE_LIKE = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "feline_like")
    );

    public static final TagKey<EntityType<?>> CANINE_LIKE = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "canine_like")
    );

    public static final TagKey<EntityType<?>> AVIAN = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "avian")
    );

    public static final TagKey<EntityType<?>> AQUATIC = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "aquatic")
    );

    public static final TagKey<EntityType<?>> REPTILE_SHELLY = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "reptile_shelly")
    );

    // Behavioral trait tags - multi-tag gating
    public static final TagKey<EntityType<?>> PLAYFUL_SPECIES = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "playful_species")
    );

    public static final TagKey<EntityType<?>> GROOMING_SPECIES = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "grooming_species")
    );

    public static final TagKey<EntityType<?>> FLIER_PERCHER = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "flier_percher")
    );

    public static final TagKey<EntityType<?>> SCENT_DRIVEN = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "scent_driven")
    );

    public static final TagKey<EntityType<?>> CURIOUS_SCAVENGER = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "curious_scavenger")
    );

    public static final TagKey<EntityType<?>> SOCIAL_AFFILIATIVE = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "social_affiliative")
    );

    public static final TagKey<EntityType<?>> SUN_BASKER = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "sun_basker")
    );

    public static final TagKey<EntityType<?>> NOCTURNAL_LEANING = TagKey.of(
        RegistryKeys.ENTITY_TYPE,
        Identifier.of(Petsplus.MOD_ID, "nocturnal_leaning")
    );

    private PetsplusEntityTypeTags() {
    }
}
