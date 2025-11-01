package woflo.petsplus.items;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

/**
 * Custom creative item groups for Pets+ content.
 */
public final class PetsplusItemGroups {
    public static final RegistryKey<ItemGroup> ABILITY_TOKENS = RegistryKey.of(
        RegistryKeys.ITEM_GROUP,
        Identifier.of(Petsplus.MOD_ID, "ability_tokens")
    );

    private PetsplusItemGroups() {
    }

    public static void register() {
        ItemGroup group = FabricItemGroup.builder()
            .icon(() -> new ItemStack(PetsplusItems.ABILITY_TOKEN))
            .displayName(Text.translatable("itemGroup." + Petsplus.MOD_ID + ".ability_tokens"))
            .entries((displayContext, entries) -> {
                for (ItemStack stack : PetsplusItems.abilityTokenStacks()) {
                    entries.add(stack.copy());
                }
            })
            .build();
        Registry.register(Registries.ITEM_GROUP, ABILITY_TOKENS, group);
    }
}
