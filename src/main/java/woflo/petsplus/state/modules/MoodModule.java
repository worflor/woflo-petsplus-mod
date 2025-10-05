package woflo.petsplus.state.modules;

import java.util.Map;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

public interface MoodModule extends DataBackedModule<MoodModule.Data> {
    PetComponent.Mood getCurrentMood(long now);
    int getMoodLevel(long now);
    void applyStimulus(PetComponent.Emotion emotion, float amount, long now);
    void addContagionShare(PetComponent.Emotion emotion, float strength);
    Map<PetComponent.Mood, Float> getMoodBlend();
    @Nullable PetComponent.Emotion getDominantEmotion();
    Map<PetComponent.Emotion, Float> getEmotionPalette();

    record Data(
        Map<PetComponent.Mood, Float> moodBlend,
        Map<PetComponent.Emotion, Float> emotionStrengths,
        long lastUpdateTick
    ) {}
}
