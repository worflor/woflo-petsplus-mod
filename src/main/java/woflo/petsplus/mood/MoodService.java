package woflo.petsplus.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.mood.EmotionProvider;
import woflo.petsplus.api.mood.MoodAPI;
import woflo.petsplus.api.mood.MoodListener;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Internal service implementing the public MoodAPI. */
public final class MoodService implements MoodAPI {
    private static final MoodService INSTANCE = new MoodService();
    public static MoodService getInstance() { return INSTANCE; }

    private final List<EmotionProvider> providers = new ArrayList<>();
    private final List<MoodListener> listeners = new ArrayList<>();

    // Per-pet rate-limit trackers for providers (pet -> (providerIdx -> lastTick))
    private final Map<MobEntity, long[]> petProviderLastTick = new WeakHashMap<>();
    // Track last mood state per pet to emit change events
    private final Map<MobEntity, int[]> petMoodSnapshot = new WeakHashMap<>(); // [moodOrdinal, level]

    private MoodService() {}

    @Override
    public void pushEmotion(MobEntity pet, PetComponent.Emotion emotion, float amount) {
    if (!(pet.getWorld() instanceof ServerWorld)) return;
        PetComponent comp = PetComponent.getOrCreate(pet);
        comp.pushEmotion(emotion, amount);
        comp.updateMood();
        snapshotAndNotify(pet, comp);
    }

    @Override
    public PetComponent.Mood getCurrentMood(MobEntity pet) {
        PetComponent comp = PetComponent.getOrCreate(pet);
        return comp.getCurrentMood();
    }

    @Override
    public int getMoodLevel(MobEntity pet) {
        PetComponent comp = PetComponent.getOrCreate(pet);
        return comp.getMoodLevel();
    }

    @Override
    public float getMoodStrength(MobEntity pet, PetComponent.Mood mood) {
        PetComponent comp = PetComponent.getOrCreate(pet);
        return comp.getMoodStrength(mood);
    }

    @Override
    public java.util.Map<PetComponent.Mood, Float> getMoodBlend(MobEntity pet) {
        PetComponent comp = PetComponent.getOrCreate(pet);
        return comp.getMoodBlend();
    }

    @Override
    public boolean hasMoodAbove(MobEntity pet, PetComponent.Mood mood, float threshold) {
        PetComponent comp = PetComponent.getOrCreate(pet);
        return comp.hasMoodAbove(mood, threshold);
    }

    @Override
    public void registerProvider(EmotionProvider provider) {
        if (provider == null) return;
        providers.add(provider);
        // Expand rate-limit arrays for existing pets lazily on next process
    }

    @Override
    public void unregisterProvider(EmotionProvider provider) {
        providers.remove(provider);
        // We won't shrink arrays; they'll be rebuilt implicitly on next process
    }

    @Override
    public void registerListener(MoodListener listener) {
        if (listener != null) listeners.add(listener);
    }

    @Override
    public void unregisterListener(MoodListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void processPet(ServerWorld world, MobEntity pet, PetComponent comp, long time) {
        if (providers.isEmpty() || pet == null || !pet.isAlive()) return;
        if (comp == null) comp = PetComponent.getOrCreate(pet);

        long[] lastTicks = petProviderLastTick.computeIfAbsent(pet, p -> new long[Math.max(1, providers.size())]);
        if (lastTicks.length < providers.size()) {
            long[] expanded = new long[providers.size()];
            System.arraycopy(lastTicks, 0, expanded, 0, lastTicks.length);
            lastTicks = expanded;
            petProviderLastTick.put(pet, lastTicks);
        }

        for (int i = 0; i < providers.size(); i++) {
            EmotionProvider provider = providers.get(i);
            int period = Math.max(1, provider.periodHintTicks());
            long last = lastTicks[i];
            if (time - last >= period) {
                provider.contribute(world, pet, comp, time, this);
                lastTicks[i] = time;
            }
        }

        // After contributions, update mood and notify if changed
        comp.updateMood();
        snapshotAndNotify(pet, comp);
    }

    private void snapshotAndNotify(MobEntity pet, PetComponent comp) {
        int moodOrd = comp.getCurrentMood().ordinal();
        int level = comp.getMoodLevel();
        int[] prev = petMoodSnapshot.get(pet);
        if (prev == null || prev[0] != moodOrd || prev[1] != level) {
            if (prev != null) {
                PetComponent.Mood oldMood = PetComponent.Mood.values()[prev[0]];
                int oldLevel = prev[1];
                for (MoodListener l : listeners) {
                    try { l.onMoodChanged(pet, oldMood, oldLevel, comp.getCurrentMood(), level); } catch (Exception ignored) {}
                }
            }
            petMoodSnapshot.put(pet, new int[]{moodOrd, level});
        }
    }
}
