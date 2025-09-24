package woflo.petsplus.state;

import net.minecraft.util.Formatting;

/**
 * Canonical list of pet moods with associated display formatting.
 * PetComponent.Mood wraps these values for backward compatibility.
 */
public enum PetMood {
    JOYFUL(Formatting.GOLD, Formatting.YELLOW),            // Happy - bright gold
    PLAYFUL(Formatting.YELLOW, Formatting.GREEN),          // Fun energy - bright, lively
    CURIOUS(Formatting.AQUA, Formatting.WHITE),            // Wonder, exploration - bright, questioning
    BONDED(Formatting.DARK_AQUA, Formatting.AQUA),         // Deep connection - trust colors
    ZEN(Formatting.GREEN, Formatting.DARK_GREEN),          // Calm - natural greens
    ZEALOUS(Formatting.RED, Formatting.YELLOW),            // Passionate energy - fire colors
    YUGEN(Formatting.DARK_PURPLE, Formatting.LIGHT_PURPLE), // Mysterious beauty - purples
    TARAB(Formatting.LIGHT_PURPLE, Formatting.GOLD),       // Musical ecstasy - purple to gold
    KINTSUGI(Formatting.WHITE, Formatting.GOLD),           // Beautiful repair - precious metals
    SAUDADE(Formatting.DARK_BLUE, Formatting.BLUE),        // Melancholic longing - deep blues
    PROTECTIVE(Formatting.BLUE, Formatting.GRAY),          // Steady guardian - reliable blues
    RESTLESS(Formatting.YELLOW, Formatting.RED),           // Agitated energy - hot, moving colors
    FEARFUL(Formatting.DARK_RED, Formatting.RED),          // Scared - dark to bright red
    WRATHFUL(Formatting.RED, Formatting.DARK_RED);         // Angry - intense red

    public final Formatting primaryFormatting;
    public final Formatting secondaryFormatting;

    PetMood(Formatting primaryFormatting, Formatting secondaryFormatting) {
        this.primaryFormatting = primaryFormatting;
        this.secondaryFormatting = secondaryFormatting;
    }
}
