package woflo.petsplus.api.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

/**
 * Public API for PetsPlus emotion and mood system.
 * External systems can push emotions, query moods, and register providers/listeners.
 */
public interface MoodAPI {
    static MoodAPI get() { return woflo.petsplus.mood.MoodService.getInstance(); }

    // Emotions
    void pushEmotion(MobEntity pet, PetComponent.Emotion emotion, float amount);

    // Query mood state
    PetComponent.Mood getCurrentMood(MobEntity pet);
    int getMoodLevel(MobEntity pet);

    // New blend-based queries
    float getMoodStrength(MobEntity pet, PetComponent.Mood mood);
    java.util.Map<PetComponent.Mood, Float> getMoodBlend(MobEntity pet);
    boolean hasMoodAbove(MobEntity pet, PetComponent.Mood mood, float threshold);

    // Optional helpers
    default void pushAffiliative(MobEntity pet, float amount) {
        pushEmotion(pet, PetComponent.Emotion.GEZELLIG, amount * 0.5f);
        pushEmotion(pet, PetComponent.Emotion.UBUNTU, amount * 0.5f);
    }

    // Provider and listener registration
    void registerProvider(EmotionProvider provider);
    void unregisterProvider(EmotionProvider provider);

    void registerListener(MoodListener listener);
    void unregisterListener(MoodListener listener);

    /** Process one pet for provider contributions (rate-limited by caller). */
    void processPet(ServerWorld world, MobEntity pet, @Nullable PetComponent comp, long time);
}
