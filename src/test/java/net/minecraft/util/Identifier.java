package net.minecraft.util;

/** Simplified Identifier stub for unit tests. */
public final class Identifier {
    private final String namespace;
    private final String path;

    private Identifier(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static Identifier of(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    public static Identifier of(String value) {
        if (value == null || value.isBlank()) {
            return new Identifier("minecraft", "generated");
        }
        int colonIndex = value.indexOf(':');
        if (colonIndex < 0) {
            return new Identifier("minecraft", value);
        }
        String namespace = value.substring(0, colonIndex);
        String path = value.substring(colonIndex + 1);
        return new Identifier(namespace, path);
    }

    public static Identifier ofVanilla(String path) {
        return new Identifier("minecraft", path);
    }

    public static Identifier tryParse(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int colonIndex = trimmed.indexOf(':');
        if (colonIndex < 0) {
            return new Identifier("minecraft", trimmed);
        }
        if (colonIndex == 0 || colonIndex == trimmed.length() - 1) {
            return null;
        }
        String namespace = trimmed.substring(0, colonIndex);
        String path = trimmed.substring(colonIndex + 1);
        if (namespace.isEmpty() || path.isEmpty()) {
            return null;
        }
        return new Identifier(namespace, path);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Identifier other)) {
            return false;
        }
        return namespace.equals(other.namespace) && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return 31 * namespace.hashCode() + path.hashCode();
    }
}
