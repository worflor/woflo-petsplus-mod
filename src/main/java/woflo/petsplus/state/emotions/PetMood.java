package woflo.petsplus.state.emotions;

import net.minecraft.util.Formatting;

/**
 * Canonical list of pet moods with associated display formatting.
 * PetComponent.Mood wraps these values for backward compatibility.
 */
public enum PetMood {
    HAPPY(Formatting.GOLD, Formatting.YELLOW),             // Joyful brightness
    PLAYFUL(Formatting.YELLOW, Formatting.GREEN),          // Energetic fun
    CURIOUS(Formatting.AQUA, Formatting.WHITE),            // Wonder, exploration - bright, questioning
    BONDED(Formatting.DARK_AQUA, Formatting.AQUA),         // Deep connection - trust colors
    CALM(Formatting.GREEN, Formatting.DARK_GREEN),         // Peaceful, grounded greens
    PASSIONATE(Formatting.RED, Formatting.GOLD),           // Enthusiastic warmth
    YUGEN(Formatting.DARK_PURPLE, Formatting.LIGHT_PURPLE), // Subtle awareness of beauty
    FOCUSED(Formatting.BLUE, Formatting.DARK_BLUE),        // Concentrated clarity
    SISU(Formatting.WHITE, Formatting.GOLD),               // Resilient resolve
    SAUDADE(Formatting.DARK_BLUE, Formatting.BLUE),        // Nostalgic longing - deep blues
    PROTECTIVE(Formatting.BLUE, Formatting.GRAY),          // Steady guardian - reliable blues
    RESTLESS(Formatting.YELLOW, Formatting.RED),           // Agitated energy - hot, moving colors
    AFRAID(Formatting.DARK_RED, Formatting.RED),           // Scared - urgent reds
    ANGRY(Formatting.RED, Formatting.DARK_RED),            // Mad - intense red
    
    // Ultra-rare moods
    ECHOED_RESONANCE(Formatting.DARK_AQUA, Formatting.DARK_PURPLE), // Whispers from the deep
    ARCANE_OVERFLOW(Formatting.LIGHT_PURPLE, Formatting.AQUA),      // Drunk on enchantment
    PACK_SPIRIT(Formatting.GOLD, Formatting.RED);                   // United pack power

    public final Formatting primaryFormatting;
    public final Formatting secondaryFormatting;

    PetMood(Formatting primaryFormatting, Formatting secondaryFormatting) {
        this.primaryFormatting = primaryFormatting;
        this.secondaryFormatting = secondaryFormatting;
    }
}
