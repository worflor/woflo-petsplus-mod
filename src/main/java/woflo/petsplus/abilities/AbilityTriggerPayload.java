package woflo.petsplus.abilities;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Payload describing an owner-scoped ability trigger request. Carries the
 * trigger identifier and any supplemental event data that should be forwarded
 * to individual pet trigger contexts.
 */
public final class AbilityTriggerPayload {
    private final String eventType;
    private final Map<String, Object> eventData;

    private AbilityTriggerPayload(String eventType, Map<String, Object> eventData) {
        this.eventType = eventType;
        this.eventData = eventData;
    }

    /**
     * Creates a payload instance for the given trigger and optional data map.
     */
    public static AbilityTriggerPayload of(String eventType, Map<String, Object> data) {
        String sanitized = Objects.requireNonNull(eventType, "eventType").trim();
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Ability trigger event type cannot be blank");
        }
        Map<String, Object> copied;
        if (data == null || data.isEmpty()) {
            copied = Collections.emptyMap();
        } else {
            copied = Collections.unmodifiableMap(new HashMap<>(data));
        }
        return new AbilityTriggerPayload(sanitized, copied);
    }

    public String eventType() {
        return eventType;
    }

    public Map<String, Object> eventData() {
        return eventData;
    }

    public boolean hasData() {
        return !eventData.isEmpty();
    }
}
