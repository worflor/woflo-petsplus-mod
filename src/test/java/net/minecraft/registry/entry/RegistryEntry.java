package net.minecraft.registry.entry;

/** Minimal registry entry stub used for tests. */
public class RegistryEntry<T> {
    private final T value;

    private RegistryEntry(T value) {
        this.value = value;
    }

    public static <T> RegistryEntry<T> of(T value) {
        return new RegistryEntry<>(value);
    }

    public T value() {
        return value;
    }
}
