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

    public static Identifier ofVanilla(String path) {
        return new Identifier("minecraft", path);
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
