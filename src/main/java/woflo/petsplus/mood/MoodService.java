package woflo.petsplus.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.mood.EmotionProvider;
import woflo.petsplus.api.mood.MoodAPI;
import woflo.petsplus.api.mood.MoodListener;
import woflo.petsplus.api.mood.ReactiveEmotionProvider;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.policy.AIPerfPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Internal service implementing the public MoodAPI. */
public final class MoodService implements MoodAPI {
    private static final MoodService INSTANCE = new MoodService();
    public static MoodService getInstance() { return INSTANCE; }

    private final List<EmotionProvider> legacyProviders = new ArrayList<>();
    private final List<ReactiveEmotionProvider> reactiveProviders = new ArrayList<>();
    private final List<MoodListener> listeners = new ArrayList<>();

    private final Map<MobEntity, int[]> petMoodSnapshot = new WeakHashMap<>(); // [moodOrdinal, level]
    private final EmotionStimulusBus stimulusBus = new EmotionStimulusBus(this);
    private final ThreadLocal<Boolean> inStimulusDispatch = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MoodService() {}

    @Override
    public void pushEmotion(MobEntity pet, PetComponent.Emotion emotion, float amount) {
        if (!(pet.getEntityWorld() instanceof ServerWorld)) return;
        PetComponent comp = PetComponent.getOrCreate(pet);
        comp.pushEmotion(emotion, amount);
        if (!isInStimulusDispatch()) {
            comp.updateMood();
            snapshotAndNotify(pet, comp);
        }
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
        if (provider instanceof ReactiveEmotionProvider reactive) {
            if (!reactiveProviders.contains(reactive)) {
                reactiveProviders.add(reactive);
                reactive.register(stimulusBus);
            }
        } else {
            legacyProviders.add(provider);
        }
    }

    @Override
    public void unregisterProvider(EmotionProvider provider) {
        if (provider == null) return;
        if (provider instanceof ReactiveEmotionProvider reactive) {
            if (reactiveProviders.remove(reactive)) {
                reactive.unregister(stimulusBus);
            }
        } else {
            legacyProviders.remove(provider);
        }
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
        // Legacy API is now routed through the stimulus bus; schedule an immediate flush.
        if (pet == null) {
            return;
        }
        if (comp == null) {
            comp = PetComponent.getOrCreate(pet);
        }
        PetComponent finalComp = comp;
        stimulusBus.queueStimulus(pet, component -> {
            for (EmotionProvider provider : legacyProviders) {
                provider.contribute(world, pet, finalComp, time, this);
            }
        });
    }

    public EmotionStimulusBus getStimulusBus() {
        return stimulusBus;
    }

    void commitStimuli(MobEntity pet, PetComponent comp, long time) {
        comp.updateMood();
        snapshotAndNotify(pet, comp);
    }

    void ensureFresh(MobEntity pet, PetComponent comp, long time) {
        comp.updateMood();
        snapshotAndNotify(pet, comp);
    }

    public boolean isInStimulusDispatch() {
        return Boolean.TRUE.equals(inStimulusDispatch.get());
    }

    void beginStimulusDispatch() {
        inStimulusDispatch.set(Boolean.TRUE);
    }

    void endStimulusDispatch() {
        inStimulusDispatch.remove();
    }

    public void trackPet(ServerWorld world, MobEntity pet) {
        // Pet component creation is handled by StateManager to avoid circular dependency
        // This method is called by StateManager after the component is created
    }

    public void untrackPet(MobEntity pet) {
        petMoodSnapshot.remove(pet);
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

    /** Base provider cadence for the "near" LOD in ticks. */
    public static int providerBaseTicksNear() {
        return AIPerfPolicy.PROVIDER_BASE_TICKS_NEAR;
    }

    /** Base provider cadence for the "mid" LOD in ticks. */
    public static int providerBaseTicksMid() {
        return AIPerfPolicy.PROVIDER_BASE_TICKS_MID;
    }

    /** Base provider cadence for the "far" LOD in ticks. */
    public static int providerBaseTicksFar() {
        return AIPerfPolicy.PROVIDER_BASE_TICKS_FAR;
    }

    /**
     * Computes the tick modulation divisor for a given distance based on policy buckets.
     * - NEAR (<= {@link woflo.petsplus.policy.AIPerfPolicy#NEAR_DIST}): {@code 1}
     * - MID (<= {@link woflo.petsplus.policy.AIPerfPolicy#MID_DIST}): {@link woflo.petsplus.policy.AIPerfPolicy#MID_TICK_MOD}
     * - FAR (> {@link woflo.petsplus.policy.AIPerfPolicy#MID_DIST}): {@link woflo.petsplus.policy.AIPerfPolicy#FAR_TICK_MOD}
     *
     * @param distance distance in blocks
     * @return tick modulation divisor for the corresponding LOD
     */
    public static int lodTickModForDistance(double distance) {
        if (distance <= AIPerfPolicy.NEAR_DIST) {
            return 1;
        } else if (distance <= AIPerfPolicy.MID_DIST) {
            return AIPerfPolicy.MID_TICK_MOD;
        } else {
            return AIPerfPolicy.FAR_TICK_MOD;
        }
    }
}
