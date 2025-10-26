package woflo.petsplus.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentType;

/**
 * Shared helpers for working with Mojang serialization codecs.
 */
public final class CodecUtils {

    private static final Codec<Identifier> IDENTIFIER_CODEC = resolveIdentifierCodec();
    private static final AtomicReference<Codec<List<ItemStack>>> ITEM_STACK_LIST_CODEC = new AtomicReference<>();

    private CodecUtils() {}

    public static Codec<Identifier> identifierCodec() {
        return IDENTIFIER_CODEC;
    }

    public static Codec<List<ItemStack>> itemStackListCodec() {
        Codec<List<ItemStack>> codec = ITEM_STACK_LIST_CODEC.get();
        if (codec == null) {
            codec = resolveItemStackListCodec();
            ITEM_STACK_LIST_CODEC.compareAndSet(null, codec);
        }
        return ITEM_STACK_LIST_CODEC.get();
    }

    private static Codec<Identifier> resolveIdentifierCodec() {
        try {
            return Identifier.CODEC;
        } catch (Throwable ignored) {
            return Codec.STRING.comapFlatMap(
                value -> {
                    Identifier parsed = Identifier.tryParse(value);
                    if (parsed == null) {
                        return DataResult.error(() -> "Invalid identifier: " + value);
                    }
                    return DataResult.success(parsed);
                },
                Identifier::toString
            );
        }
    }

    private static Codec<List<ItemStack>> resolveItemStackListCodec() {
        try {
            return ItemStack.CODEC.listOf();
        } catch (Throwable ignored) {
            return createFallbackItemStackListCodec();
        }
    }

    private static Codec<List<ItemStack>> createFallbackItemStackListCodec() {
        Encoder<List<ItemStack>> encoder = new Encoder<>() {
            @Override
            public <T> DataResult<T> encode(List<ItemStack> input, DynamicOps<T> ops, T prefix) {
                List<T> encoded = new ArrayList<>(input.size());
                for (int index = 0; index < input.size(); index++) {
                    ItemStack stack = input.get(index);
                    DataResult<NbtCompound> encodedStack = encodeStack(stack, index);
                    Optional<NbtCompound> maybeStack = encodedStack.result();
                    if (maybeStack.isEmpty()) {
                        String detail = encodedStack.error().map(Object::toString).orElse("unknown error");
                        final String errorMessage = "Failed to encode item stack at slot " + index + ": " + detail;
                        return DataResult.error(() -> errorMessage);
                    }
                    encoded.add(NbtOps.INSTANCE.convertTo(ops, maybeStack.get()));
                }
                return ops.mergeToList(prefix, encoded);
            }
        };

        Decoder<List<ItemStack>> decoder = new Decoder<>() {
            @Override
            public <T> DataResult<Pair<List<ItemStack>, T>> decode(DynamicOps<T> ops, T input) {
                NbtElement element = ops.convertTo(NbtOps.INSTANCE, input);
                if (!(element instanceof NbtList nbtList)) {
                    return DataResult.error(() -> "Expected NbtList when decoding item stacks but found " + element);
                }

                List<ItemStack> decoded = new ArrayList<>(nbtList.size());
                for (int i = 0; i < nbtList.size(); i++) {
                    NbtElement entry = nbtList.get(i);
                    DataResult<ItemStack> decodedStack = decodeStack(entry, i);
                    Optional<ItemStack> maybeStack = decodedStack.result();
                    if (maybeStack.isEmpty()) {
                        String detail = decodedStack.error().map(Object::toString).orElse("unknown error");
                        final String errorMessage = "Failed to decode item stack at slot " + i + ": " + detail;
                        return DataResult.error(() -> errorMessage);
                    }
                    decoded.add(maybeStack.get());
                }
                return DataResult.success(Pair.of(decoded, ops.empty()));
            }
        };

        return Codec.of(encoder, decoder);
    }

    private static DataResult<NbtCompound> encodeStack(ItemStack stack, int slot) {
        try {
            NbtCompound nbt = new NbtCompound();
            if (stack.isEmpty()) {
                nbt.putBoolean("empty", true);
                return DataResult.success(nbt);
            }

            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            if (itemId == null) {
                return DataResult.error(() -> "Unregistered item in slot " + slot);
            }

            nbt.putString("id", itemId.toString());
            nbt.putInt("count", stack.getCount());

            ComponentMap components = stack.getComponents();
            if (!components.isEmpty()) {
                NbtCompound componentsNbt = new NbtCompound();
                for (Component component : components) {
                    ComponentType<?> type = component.type();
                    Identifier componentId = Registries.DATA_COMPONENT_TYPE.getId(type);
                    if (componentId == null) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    var codec = (Codec<Object>) type.getCodec();
                    Object value = component.value();
                    DataResult<NbtElement> encoded = codec.encodeStart(NbtOps.INSTANCE, value);
                    Optional<NbtElement> result = encoded.result();
                    if (result.isEmpty()) {
                        return DataResult.error(() -> "Failed to encode component '" + componentId + "' at slot " + slot);
                    }
                    componentsNbt.put(componentId.toString(), result.get());
                }

                if (!componentsNbt.getKeys().isEmpty()) {
                    nbt.put("components", componentsNbt);
                }
            }

            return DataResult.success(nbt);
        } catch (Exception e) {
            return DataResult.error(() -> "Failed to encode item stack at slot " + slot + ": " + e.getMessage());
        }
    }

    private static DataResult<ItemStack> decodeStack(NbtElement element, int slot) {
        if (!(element instanceof NbtCompound compound)) {
            return DataResult.error(() -> "Expected NbtCompound but found " + element);
        }

        try {
            if (compound.getBoolean("empty").orElse(false)) {
                return DataResult.success(ItemStack.EMPTY);
            }

            String idString = compound.getString("id").orElse("");
            if (idString.isEmpty()) {
                return DataResult.success(ItemStack.EMPTY);
            }

            Identifier itemId = Identifier.tryParse(idString);
            if (itemId == null) {
                return DataResult.error(() -> "Invalid item identifier '" + idString + "' at slot " + slot);
            }

            Item item = Registries.ITEM.get(itemId);
            if (!itemId.equals(Registries.ITEM.getId(item))) {
                return DataResult.error(() -> "Unknown item '" + itemId + "' at slot " + slot);
            }

            int count = compound.getInt("count").orElse(1);
            ItemStack stack = new ItemStack(item, Math.max(1, count));
            stack.setCount(Math.max(0, count));

            if (compound.contains("components")) {
                compound.getCompound("components").ifPresent(componentsNbt -> {
                    for (String key : componentsNbt.getKeys()) {
                        Identifier componentId = Identifier.tryParse(key);
                        if (componentId == null || !componentsNbt.contains(key)) {
                            continue;
                        }

                        NbtElement elementNbt = componentsNbt.get(key);
                        if (elementNbt == null) {
                            continue;
                        }

                        ComponentType<?> type = Registries.DATA_COMPONENT_TYPE.get(componentId);
                        if (type == null || type.shouldSkipSerialization()) {
                            continue;
                        }

                        DataResult<?> decoded = type.getCodec().parse(NbtOps.INSTANCE, elementNbt);
                        decoded.result().ifPresent(value -> {
                            @SuppressWarnings("unchecked")
                            ComponentType<Object> castType = (ComponentType<Object>) type;
                            stack.set(castType, value);
                        });
                    }
                });
            }

            return DataResult.success(stack);
        } catch (Exception e) {
            return DataResult.error(() -> "Failed to decode item stack at slot " + slot + ": " + e.getMessage());
        }
    }

    public static final class TestHooks {
        private TestHooks() {}

        public static Codec<List<ItemStack>> fallbackItemStackListCodec() {
            return createFallbackItemStackListCodec();
        }

        public static Codec<List<ItemStack>> currentItemStackListCodec() {
            return CodecUtils.itemStackListCodec();
        }

        public static void forceItemStackListCodec(Codec<List<ItemStack>> codec) {
            ITEM_STACK_LIST_CODEC.set(Objects.requireNonNull(codec));
        }
    }
}
