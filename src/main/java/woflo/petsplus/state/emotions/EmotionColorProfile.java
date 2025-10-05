package woflo.petsplus.state.emotions;

import net.minecraft.text.TextColor;
import net.minecraft.util.math.MathHelper;

/**
 * Structured color metadata for authored emotions.
 */
public record EmotionColorProfile(TextColor baseColor, TextColor accentColor) {
    private static final float DEFAULT_ACCENT_LIGHTEN = 0.18f;

    public EmotionColorProfile(int baseRgb) {
        this(TextColor.fromRgb(baseRgb), lighten(baseRgb, DEFAULT_ACCENT_LIGHTEN));
    }

    public EmotionColorProfile(int baseRgb, int accentRgb) {
        this(TextColor.fromRgb(baseRgb), TextColor.fromRgb(accentRgb));
    }

    private static TextColor lighten(int rgb, float factor) {
        factor = MathHelper.clamp(factor, 0f, 1f);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * factor));
        g = Math.min(255, Math.round(g + (255 - g) * factor));
        b = Math.min(255, Math.round(b + (255 - b) * factor));
        return TextColor.fromRgb((r << 16) | (g << 8) | b);
    }
}
