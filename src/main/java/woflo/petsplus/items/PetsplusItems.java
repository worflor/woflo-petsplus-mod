package woflo.petsplus.items;

import net.minecraft.item.Item;
import java.util.function.Function;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import woflo.petsplus.Petsplus;

/**
 * Registry for custom Pets+ items.
 */
public class PetsplusItems {
    
    /**
     * Pet Compendium - A special book that displays detailed pet information.
     */
    public static final Item PET_COMPENDIUM = register(
        "pet_compendium",
        settings -> new PetCompendium(settings
            .maxCount(1)
            .rarity(Rarity.UNCOMMON)
            .translationKey("item." + Petsplus.MOD_ID + ".pet_compendium"))
    );
    
    private static Item register(String id, Function<Item.Settings, Item> factory) {
        Identifier identifier = Identifier.of(Petsplus.MOD_ID, id);
        Item.Settings settings = new Item.Settings()
            .registryKey(RegistryKey.of(Registries.ITEM.getKey(), identifier));
        Item item = factory.apply(settings);
        return Registry.register(Registries.ITEM, identifier, item);
    }
    
    /**
     * Initialize items. Call this during mod initialization.
     */
    public static void register() {
        Petsplus.LOGGER.info("Registering Pets+ items");
    }
}
