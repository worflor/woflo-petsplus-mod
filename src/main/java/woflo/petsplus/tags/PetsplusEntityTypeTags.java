package woflo.petsplus.tags;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

/**
 * Shared entity type tags used by PetsPlus at runtime.
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

    private PetsplusEntityTypeTags() {
    }
}
