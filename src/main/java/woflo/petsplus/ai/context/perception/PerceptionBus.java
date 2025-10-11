package woflo.petsplus.ai.context.perception;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight perception bus that routes stimuli to registered listeners.
 * The bus keeps ordering deterministic by dispatching listeners in the order
 * they were registered.
 */
public final class PerceptionBus {
    private final Map<Identifier, List<PerceptionListener>> keyedListeners = new HashMap<>();
    private final List<PerceptionListener> broadcastListeners = new ArrayList<>();

    public synchronized void subscribe(Identifier type, PerceptionListener listener) {
        keyedListeners.computeIfAbsent(type, ignored -> new ArrayList<>()).add(listener);
    }

    public synchronized void subscribeAll(PerceptionListener listener) {
        broadcastListeners.add(listener);
    }

    public void publish(PerceptionStimulus stimulus) {
        List<PerceptionListener> listeners = new ArrayList<>();
        synchronized (this) {
            listeners.addAll(broadcastListeners);
            List<PerceptionListener> keyed = keyedListeners.get(stimulus.type());
            if (keyed != null) {
                listeners.addAll(keyed);
            }
        }
        for (PerceptionListener listener : listeners) {
            listener.onStimulus(stimulus);
        }
    }

    public synchronized void clear() {
        keyedListeners.clear();
        broadcastListeners.clear();
    }
}
