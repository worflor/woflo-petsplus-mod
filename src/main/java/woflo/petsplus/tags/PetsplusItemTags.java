package woflo.petsplus.tags;

import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class PetsplusItemTags {
    public static final TagKey<Item> FETCH_BLACKLIST = TagKey.of(RegistryKeys.ITEM, Identifier.of("petsplus", "fetch_blacklist"));

    private PetsplusItemTags() {
    }
}
