package net.minecraft.registry;

import net.minecraft.util.Identifier;

/** Minimal registry key stub for unit tests. */
public final class RegistryKey<T> {
    private final Identifier value;

    private RegistryKey(Identifier value) {
        this.value = value;
    }

    public static <T> RegistryKey<T> of(Identifier id) {
        return new RegistryKey<>(id);
    }

    public static <T> RegistryKey<T> ofRegistry(Identifier id) {
        return new RegistryKey<>(id);
    }

    public Identifier getValue() {
        return value;
    }
}
