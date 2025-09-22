package woflo.petsplus.ui;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Centralizes UI styling rules: primary/secondary text, accents, and lightweight emphasis.
 */
public final class UIStyle {
    private UIStyle() {}

    public static MutableText primary(String s) {
        return Text.literal(s).formatted(Formatting.GRAY);
    }

    public static MutableText secondary(String s) {
        return Text.literal(s).formatted(Formatting.DARK_GRAY);
    }

    public static MutableText value(String s, Formatting color) {
        return Text.literal(s).formatted(color);
    }

    public static MutableText bold(Text base) {
        return base.copy().formatted(Formatting.BOLD);
    }

    public static MutableText italic(Text base) {
        return base.copy().formatted(Formatting.ITALIC);
    }

    public static MutableText sepDot() {
        return secondary(" • ");
    }

    public static MutableText spacer() {
        return secondary("  ");
    }
}
