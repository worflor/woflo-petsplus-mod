package woflo.petsplus.mood;

import woflo.petsplus.state.PetComponent;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks per-pet mood baselines so emotion batches across threads share the latest blend.
 */
public final class EmotionBaselineTracker {

    private static final Map<PetComponent, EnumMap<PetComponent.Mood, Float>> BASELINES =
        Collections.synchronizedMap(new WeakHashMap<>());

    private EmotionBaselineTracker() {}

    public static void resetForTest() {
        BASELINES.clear();
    }

    public static void ensureBaseline(PetComponent component) {
        if (component == null) {
            return;
        }
        synchronized (BASELINES) {
            BASELINES.computeIfAbsent(component, EmotionBaselineTracker::snapshotBlend);
        }
    }

    public static EnumMap<PetComponent.Mood, Float> copyBaseline(PetComponent component) {
        EnumMap<PetComponent.Mood, Float> copy = new EnumMap<>(PetComponent.Mood.class);
        if (component == null) {
            return copy;
        }
        synchronized (BASELINES) {
            EnumMap<PetComponent.Mood, Float> baseline = BASELINES.get(component);
            if (baseline == null) {
                baseline = snapshotBlend(component);
                BASELINES.put(component, baseline);
            }
            copy.putAll(baseline);
        }
        return copy;
    }

    public static void updateBaseline(PetComponent component, Map<PetComponent.Mood, Float> snapshot) {
        if (component == null) {
            return;
        }
        synchronized (BASELINES) {
            if (snapshot == null) {
                BASELINES.remove(component);
            } else {
                EnumMap<PetComponent.Mood, Float> updated = new EnumMap<>(PetComponent.Mood.class);
                updated.putAll(snapshot);
                BASELINES.put(component, updated);
            }
        }
    }

    public static void recordDirectChange(PetComponent component) {
        if (component == null) {
            return;
        }
        updateBaseline(component, snapshotBlend(component));
    }

    public static EnumMap<PetComponent.Mood, Float> snapshotBlend(PetComponent component) {
        EnumMap<PetComponent.Mood, Float> snapshot = new EnumMap<>(PetComponent.Mood.class);
        if (component != null) {
            snapshot.putAll(component.getMoodBlend());
        }
        return snapshot;
    }
}
