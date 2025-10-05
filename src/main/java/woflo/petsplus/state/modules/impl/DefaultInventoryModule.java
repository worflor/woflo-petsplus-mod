package woflo.petsplus.state.modules.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import woflo.petsplus.state.modules.InventoryModule;

public final class DefaultInventoryModule implements InventoryModule {
    private final Map<String, DefaultedList<ItemStack>> inventories = new HashMap<>();

    @Override
    public void onAttach(woflo.petsplus.state.PetComponent parent) {
        // No-op
    }

    @Override
    public void onDetach() {
        inventories.clear();
    }

    @Override
    public DefaultedList<ItemStack> getInventory(String category, int size) {
        DefaultedList<ItemStack> inventory = inventories.get(category);
        if (inventory == null) {
            inventory = DefaultedList.ofSize(size, ItemStack.EMPTY);
            inventories.put(category, inventory);
            return inventory;
        }
        if (inventory.size() != size) {
            DefaultedList<ItemStack> resized = DefaultedList.ofSize(size, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(size, inventory.size()); i++) {
                resized.set(i, inventory.get(i));
            }
            inventories.put(category, resized);
            return resized;
        }
        return inventory;
    }

    @Override
    public DefaultedList<ItemStack> getInventoryIfPresent(String category) {
        return inventories.get(category);
    }

    @Override
    public void setInventory(String category, DefaultedList<ItemStack> stacks) {
        inventories.put(category, stacks == null ? DefaultedList.ofSize(0, ItemStack.EMPTY) : stacks);
    }

    @Override
    public Set<String> getCategories() {
        return Collections.unmodifiableSet(inventories.keySet());
    }

    @Override
    public Data toData() {
        Map<String, List<ItemStack>> serialized = new HashMap<>();
        inventories.forEach((key, list) -> serialized.put(key, List.copyOf(list)));
        return new Data(serialized);
    }

    @Override
    public void fromData(Data data) {
        inventories.clear();
        if (data == null || data.inventories() == null) {
            return;
        }
        data.inventories().forEach((key, list) -> {
            DefaultedList<ItemStack> converted = DefaultedList.ofSize(list.size(), ItemStack.EMPTY);
            for (int i = 0; i < list.size(); i++) {
                converted.set(i, list.get(i));
            }
            inventories.put(key, converted);
        });
    }
}

