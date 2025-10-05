package woflo.petsplus.state.modules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

public interface InventoryModule extends DataBackedModule<InventoryModule.Data> {
    DefaultedList<ItemStack> getInventory(String category, int size);
    @Nullable DefaultedList<ItemStack> getInventoryIfPresent(String category);
    void setInventory(String category, DefaultedList<ItemStack> stacks);
    Set<String> getCategories();

    record Data(Map<String, List<ItemStack>> inventories) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.unboundedMap(Codec.STRING, ItemStack.CODEC.listOf()).optionalFieldOf("inventories", new HashMap<>()).forGetter(Data::inventories)
            ).apply(instance, Data::new)
        );
    }
}
