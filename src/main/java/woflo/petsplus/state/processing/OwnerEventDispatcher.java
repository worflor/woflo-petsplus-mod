package woflo.petsplus.state.processing;

import woflo.petsplus.Petsplus;

/**
 * Simple dispatcher for owner-scoped event listeners. Keeps listener registration
 * lightweight so gameplay systems can opt into owner-centric batches instead of
 * per-pet polling.
 */
public final class OwnerEventDispatcher {
    private final java.util.EnumMap<OwnerEventType, ListenerBucket> listeners =
        new java.util.EnumMap<>(OwnerEventType.class);
    private final java.util.concurrent.CopyOnWriteArrayList<PresenceListener> presenceListeners =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    public OwnerEventDispatcher() {
        for (OwnerEventType type : OwnerEventType.values()) {
            listeners.put(type, new ListenerBucket());
        }
    }

    public void register(OwnerEventType type, OwnerEventListener listener) {
        if (type == null || listener == null) {
            return;
        }
        boolean becameActive = false;
        synchronized (listeners) {
            ListenerBucket bucket = listeners.computeIfAbsent(type, ignored -> new ListenerBucket());
            becameActive = bucket.add(listener);
        }
        if (becameActive) {
            notifyPresenceChanged(type, true);
        }
    }

    public void unregister(OwnerEventType type, OwnerEventListener listener) {
        if (type == null || listener == null) {
            return;
        }
        boolean becameInactive = false;
        synchronized (listeners) {
            ListenerBucket bucket = listeners.get(type);
            if (bucket != null) {
                becameInactive = bucket.remove(listener);
            }
        }
        if (becameInactive) {
            notifyPresenceChanged(type, false);
        }
    }

    public void dispatch(OwnerEventFrame frame) {
        OwnerEventListener[] snapshot;
        synchronized (listeners) {
            ListenerBucket bucket = listeners.get(frame.eventType());
            if (bucket == null || bucket.isEmpty()) {
                return;
            }
            snapshot = bucket.snapshot();
        }
        for (OwnerEventListener listener : snapshot) {
            try {
                listener.onOwnerEvent(frame);
            } catch (Exception ex) {
                Petsplus.LOGGER.error("Owner event listener failed for event {}", frame.eventType(), ex);
            }
        }
    }

    public boolean hasListeners(OwnerEventType type) {
        if (type == null) {
            return false;
        }
        synchronized (listeners) {
            ListenerBucket bucket = listeners.get(type);
            return bucket != null && !bucket.isEmpty();
        }
    }

    public void addPresenceListener(PresenceListener listener) {
        if (listener == null) {
            return;
        }
        java.util.EnumMap<OwnerEventType, Boolean> snapshot = new java.util.EnumMap<>(OwnerEventType.class);
        synchronized (listeners) {
            for (OwnerEventType type : OwnerEventType.values()) {
                ListenerBucket bucket = listeners.get(type);
                snapshot.put(type, bucket != null && !bucket.isEmpty());
            }
        }
        presenceListeners.add(listener);
        for (java.util.Map.Entry<OwnerEventType, Boolean> entry : snapshot.entrySet()) {
            listener.onPresenceChanged(entry.getKey(), entry.getValue());
        }
    }

    public void removePresenceListener(PresenceListener listener) {
        if (listener == null) {
            return;
        }
        presenceListeners.remove(listener);
    }

    public void clear() {
        synchronized (listeners) {
            for (ListenerBucket bucket : listeners.values()) {
                bucket.clear();
            }
        }
        presenceListeners.clear();
    }

    private void notifyPresenceChanged(OwnerEventType type, boolean hasListeners) {
        if (presenceListeners.isEmpty()) {
            return;
        }
        for (PresenceListener listener : presenceListeners) {
            try {
                listener.onPresenceChanged(type, hasListeners);
            } catch (Exception ex) {
                Petsplus.LOGGER.error("Owner event presence listener failed for {}", type, ex);
            }
        }
    }

    @FunctionalInterface
    public interface PresenceListener {
        void onPresenceChanged(OwnerEventType type, boolean hasListeners);
    }

    private static final class ListenerBucket {
        private final java.util.ArrayList<OwnerEventListener> listeners = new java.util.ArrayList<>();
        private OwnerEventListener[] snapshot = new OwnerEventListener[0];
        private boolean snapshotDirty = false;

        boolean add(OwnerEventListener listener) {
            if (listeners.contains(listener)) {
                return false;
            }
            boolean wasEmpty = listeners.isEmpty();
            listeners.add(listener);
            snapshotDirty = true;
            return wasEmpty;
        }

        boolean remove(OwnerEventListener listener) {
            boolean removed = listeners.remove(listener);
            if (removed) {
                snapshotDirty = true;
            }
            return removed && listeners.isEmpty();
        }

        boolean isEmpty() {
            return listeners.isEmpty();
        }

        OwnerEventListener[] snapshot() {
            if (snapshotDirty) {
                snapshot = listeners.toArray(OwnerEventListener[]::new);
                snapshotDirty = false;
            }
            return snapshot;
        }

        void clear() {
            listeners.clear();
            snapshot = new OwnerEventListener[0];
            snapshotDirty = false;
        }
    }
}
