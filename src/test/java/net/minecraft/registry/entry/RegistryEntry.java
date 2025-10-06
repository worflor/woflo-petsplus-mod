package net.minecraft.registry.entry;

/** Minimal registry entry stub used for tests. */
public interface RegistryEntry<T> {
    T value();

    static <T> RegistryEntry<T> of(T value) {
        return new Simple<>(value);
    }

    final class Simple<T> implements RegistryEntry<T> {
        private final T value;

        private Simple(T value) {
            this.value = value;
        }

        @Override
        public T value() {
            return value;
        }
    }
}
