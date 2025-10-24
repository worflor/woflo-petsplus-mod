package woflo.petsplus.ai.suggester.signal;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FeasibilitySignalRegistry {
    private static final Map<Identifier, FeasibilitySignal> SIGNALS = new LinkedHashMap<>();
    private static volatile Collection<FeasibilitySignal> cachedView = List.of();

    private FeasibilitySignalRegistry() {
    }

    public static synchronized void register(FeasibilitySignal signal) {
        SIGNALS.put(signal.id(), signal);
        refreshView();
    }

    public static Collection<FeasibilitySignal> all() {
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
