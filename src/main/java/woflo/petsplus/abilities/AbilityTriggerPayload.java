package woflo.petsplus.abilities;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;


/**
 * Payload describing an owner-scoped ability trigger request. Carries the
 * trigger identifier and any supplemental event data that should be forwarded
 * to individual pet trigger contexts.
 */
public final class AbilityTriggerPayload {
    private final String eventType;
    private final Map<String, Object> eventData;
    private final CompletableFuture<AbilityTriggerResult> resultFuture;

    private AbilityTriggerPayload(String eventType,
                                  Map<String, Object> eventData,
                                  @Nullable CompletableFuture<AbilityTriggerResult> resultFuture) {
        this.eventType = eventType;
        this.eventData = eventData;
        this.resultFuture = resultFuture;
    }

    /**
     * Creates a payload instance for the given trigger and optional data map.
     */
    public static AbilityTriggerPayload of(String eventType, Map<String, Object> data) {
        return of(eventType, data, null);
    }

    public static AbilityTriggerPayload of(String eventType,
                                           Map<String, Object> data,
                                           @Nullable CompletableFuture<AbilityTriggerResult> resultFuture) {
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
        return new AbilityTriggerPayload(sanitized, copied, resultFuture);
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

    @Nullable
    public CompletableFuture<AbilityTriggerResult> resultFuture() {
        return resultFuture;
    }

    public boolean requiresSynchronousResult() {
        return resultFuture != null;
    }

    public void completeResult(@Nullable AbilityTriggerResult result) {
        if (resultFuture != null) {
            AbilityTriggerResult value = result == null ? AbilityTriggerResult.empty() : result;
            resultFuture.complete(value);
        }
    }

    public void completeExceptionally(@Nullable Throwable error) {
        if (resultFuture != null && error != null) {
            resultFuture.completeExceptionally(error);
        }
    }
}
