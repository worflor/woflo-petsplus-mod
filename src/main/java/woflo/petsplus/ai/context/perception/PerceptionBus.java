package woflo.petsplus.ai.context.perception;

import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lightweight perception bus that routes stimuli to registered listeners.
 * The bus keeps ordering deterministic by dispatching listeners in the order
 * they were registered without allocating per publish.
 */
public final class PerceptionBus {
    private static final PerceptionListener[] EMPTY_LISTENERS = new PerceptionListener[0];

    private final ConcurrentMap<Identifier, ListenerBucket> keyedListeners = new ConcurrentHashMap<>();
    private volatile PerceptionListener[] broadcastListeners = EMPTY_LISTENERS;

    public void subscribe(Identifier type, PerceptionListener listener) {
        if (type == null || listener == null) {
            return;
        }
        keyedListeners.compute(type, (identifier, bucket) -> {
            ListenerBucket target = bucket;
            if (target == null) {
                target = new ListenerBucket();
            }
            target.add(listener);
            return target;
        });
    }

    public void subscribeAll(PerceptionListener listener) {
        if (listener == null) {
            return;
        }
        synchronized (this) {
            PerceptionListener[] current = broadcastListeners;
            PerceptionListener[] next = new PerceptionListener[current.length + 1];
            System.arraycopy(current, 0, next, 0, current.length);
            next[current.length] = listener;
            broadcastListeners = next;
        }
    }

    public void publish(PerceptionStimulus stimulus) {
        if (stimulus == null) {
            return;
        }
        PerceptionListener[] broadcastSnapshot = broadcastListeners;
        for (PerceptionListener listener : broadcastSnapshot) {
            listener.onStimulus(stimulus);
        }
        ListenerBucket bucket = keyedListeners.get(stimulus.type());
        if (bucket != null) {
            bucket.publish(stimulus);
        }
    }

    public void clear() {
        broadcastListeners = EMPTY_LISTENERS;
        keyedListeners.clear();
    }

    private static final class ListenerBucket {
        private volatile PerceptionListener[] listeners = EMPTY_LISTENERS;

        void add(PerceptionListener listener) {
            Objects.requireNonNull(listener, "listener");
            synchronized (this) {
                PerceptionListener[] current = listeners;
                PerceptionListener[] next = new PerceptionListener[current.length + 1];
                System.arraycopy(current, 0, next, 0, current.length);
                next[current.length] = listener;
                listeners = next;
            }
        }

        void publish(PerceptionStimulus stimulus) {
            PerceptionListener[] snapshot = listeners;
            for (PerceptionListener listener : snapshot) {
                listener.onStimulus(stimulus);
            }
        }
    }
}
