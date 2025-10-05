package woflo.petsplus.state.emotions;

import net.minecraft.util.Formatting;

/**
 * Pet mood states derived from interactions and environment.
 */
public enum PetMoodEnum {
    HAPPY(PetMood.HAPPY),
    PLAYFUL(PetMood.PLAYFUL),
    CURIOUS(PetMood.CURIOUS),
    BONDED(PetMood.BONDED),
    CALM(PetMood.CALM),
    PASSIONATE(PetMood.PASSIONATE),
    YUGEN(PetMood.YUGEN),
    FOCUSED(PetMood.FOCUSED),
    SISU(PetMood.SISU),
    SAUDADE(PetMood.SAUDADE),
    PROTECTIVE(PetMood.PROTECTIVE),
    RESTLESS(PetMood.RESTLESS),
    AFRAID(PetMood.AFRAID),
    ANGRY(PetMood.ANGRY),
    
    // Ultra-rare moods
    ECHOED_RESONANCE(PetMood.ECHOED_RESONANCE),
    ARCANE_OVERFLOW(PetMood.ARCANE_OVERFLOW),
    PACK_SPIRIT(PetMood.PACK_SPIRIT);

    public final Formatting primaryFormatting;
    public final Formatting secondaryFormatting;
    public final PetMood delegate;
    
    PetMoodEnum(PetMood d) {
        this.delegate = d;
        this.primaryFormatting = d.primaryFormatting;
        this.secondaryFormatting = d.secondaryFormatting;
    }
}
