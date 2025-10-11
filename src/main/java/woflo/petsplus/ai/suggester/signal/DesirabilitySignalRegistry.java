package woflo.petsplus.ai.suggester.signal;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DesirabilitySignalRegistry {
    private static final Map<Identifier, DesirabilitySignal> SIGNALS = new LinkedHashMap<>();

    private DesirabilitySignalRegistry() {
    }

    public static synchronized void register(DesirabilitySignal signal) {
        SIGNALS.put(signal.id(), signal);
    }

    public static synchronized Collection<DesirabilitySignal> all() {
        return Collections.unmodifiableCollection(SIGNALS.values());
    }

    public static synchronized boolean isEmpty() {
        return SIGNALS.isEmpty();
    }

    public static synchronized void clear() {
        SIGNALS.clear();
    }
}
