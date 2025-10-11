package woflo.petsplus.ai.suggester.signal;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FeasibilitySignalRegistry {
    private static final Map<Identifier, FeasibilitySignal> SIGNALS = new LinkedHashMap<>();

    private FeasibilitySignalRegistry() {
    }

    public static synchronized void register(FeasibilitySignal signal) {
        SIGNALS.put(signal.id(), signal);
    }

    public static synchronized Collection<FeasibilitySignal> all() {
        return Collections.unmodifiableCollection(SIGNALS.values());
    }

    public static synchronized boolean isEmpty() {
        return SIGNALS.isEmpty();
    }

    public static synchronized void clear() {
        SIGNALS.clear();
    }
}
