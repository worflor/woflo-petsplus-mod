package woflo.petsplus.ai.suggester.signal;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DesirabilitySignalRegistry {
    private static final Map<Identifier, DesirabilitySignal> SIGNALS = new LinkedHashMap<>();
    private static volatile Collection<DesirabilitySignal> cachedView = List.of();

    private DesirabilitySignalRegistry() {
    }

    public static synchronized void register(DesirabilitySignal signal) {
        SIGNALS.put(signal.id(), signal);
        refreshView();
    }

    public static Collection<DesirabilitySignal> all() {
        return cachedView;
    }

    public static boolean isEmpty() {
        return cachedView.isEmpty();
    }

    public static synchronized void clear() {
        SIGNALS.clear();
        refreshView();
    }

    private static void refreshView() {
        if (SIGNALS.isEmpty()) {
            cachedView = List.of();
            return;
        }
        cachedView = Collections.unmodifiableList(List.copyOf(SIGNALS.values()));
    }
}
