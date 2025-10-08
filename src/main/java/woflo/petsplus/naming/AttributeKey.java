package woflo.petsplus.naming;

/**
 * Represents a parsed attribute from a pet's name.
 *
 * @param type     The attribute type (e.g., "brave", "swift", "fierce")
 * @param value    The attribute value (can be empty for boolean attributes)
 * @param priority The priority of this attribute for conflict resolution (higher = more important)
 */
public record AttributeKey(String type, String value, int priority) {

    public AttributeKey(String type, String value) {
        this(type, value, 0);
    }

    public AttributeKey(String type) {
        this(type, "", 0);
    }

    /**
     * Returns true if this attribute has an associated value.
     */
    public boolean hasValue() {
        return value != null && !value.isEmpty();
    }

    /**
     * Returns a normalized version of the type for comparison.
     */
    public String normalizedType() {
        return type != null ? type.toLowerCase().trim() : "";
    }

    /**
     * Returns a normalized version of the value for comparison.
     */
    public String normalizedValue() {
        return value != null ? value.toLowerCase().trim() : "";
    }

    @Override
    public String toString() {
        if (hasValue()) {
            return type + ":" + value + (priority != 0 ? " (priority: " + priority + ")" : "");
        }
        return type + (priority != 0 ? " (priority: " + priority + ")" : "");
    }
}
