package net.minecraft.text;

/** Minimal Text interface with factory helpers. */
public interface Text {
    String asString();

    static MutableText literal(String literal) {
        return new MutableText(literal);
    }

    static MutableText translatable(String key, Object... args) {
        return new MutableText(key);
    }

    class SimpleText implements Text {
        private final String value;

        SimpleText(String value) {
            this.value = value;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
