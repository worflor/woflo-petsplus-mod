package woflo.petsplus.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.AbilityType;
import woflo.petsplus.api.registry.PetsPlusRegistries;

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

    /**
     * Token item that grants specific abilities to pets.
     */
    public static final Item ABILITY_TOKEN = register(
        "ability_token",
        settings -> new AbilityTokenItem(settings
            .maxCount(16)
            .rarity(Rarity.RARE)
            .translationKey("item." + Petsplus.MOD_ID + ".ability_token"))
    );

    private static final List<ItemStack> ABILITY_TOKEN_STACKS = new ArrayList<>();
    
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
        refreshAbilityTokenStacks();
    }

    /**
     * Rebuild the cached list of ability token stacks after data reload.
     */
    public static synchronized void refreshAbilityTokenStacks() {
        ABILITY_TOKEN_STACKS.clear();
        List<AbilityType> abilityTypes = new ArrayList<>();
        PetsPlusRegistries.abilityTypeRegistry().forEach(abilityTypes::add);
        abilityTypes.sort(Comparator.comparing(type -> type.id().toString()));
        for (AbilityType type : abilityTypes) {
            ABILITY_TOKEN_STACKS.add(AbilityTokenItem.create(type));
        }
    }

    public static List<ItemStack> abilityTokenStacks() {
        if (ABILITY_TOKEN_STACKS.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(ABILITY_TOKEN_STACKS);
    }
}
