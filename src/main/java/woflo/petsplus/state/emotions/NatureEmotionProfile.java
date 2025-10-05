package woflo.petsplus.state.emotions;

import org.jetbrains.annotations.Nullable;

public record NatureEmotionProfile(
    @Nullable PetEmotion majorEmotion, float majorStrength,
    @Nullable PetEmotion minorEmotion, float minorStrength,
    @Nullable PetEmotion quirkEmotion, float quirkStrength
) {
    public static final NatureEmotionProfile EMPTY = new NatureEmotionProfile(null, 0f, null, 0f, null, 0f);

    public boolean isEmpty() {
        return (majorEmotion == null || majorStrength <= 0f)
            && (minorEmotion == null || minorStrength <= 0f)
            && (quirkEmotion == null || quirkStrength <= 0f);
    }
}
