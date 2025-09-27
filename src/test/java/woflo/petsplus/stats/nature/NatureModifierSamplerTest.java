package woflo.petsplus.stats.nature;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.nature.NatureModifierSampler.NatureAdjustment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NatureModifierSamplerTest {

    @Test
    void seededSamplingIsDeterministic() {
        Identifier natureId = Identifier.of("petsplus", "radiant");
        long seed = 12345L;

        NatureAdjustment first = NatureModifierSampler.sample(natureId, seed);
        NatureAdjustment second = NatureModifierSampler.sample(natureId, seed);

        assertEquals(first.majorValue(), second.majorValue(), 1.0e-6f);
        assertEquals(first.minorValue(), second.minorValue(), 1.0e-6f);
        assertEquals(first.volatilityMultiplier(), second.volatilityMultiplier(), 1.0e-6f);
        assertEquals(first.resilienceMultiplier(), second.resilienceMultiplier(), 1.0e-6f);
        assertEquals(first.contagionModifier(), second.contagionModifier(), 1.0e-6f);
        assertEquals(first.guardModifier(), second.guardModifier(), 1.0e-6f);
        assertEquals(first.emotionProfile(), second.emotionProfile());
    }

    @Test
    void differentSeedsShiftNatureRolls() {
        Identifier natureId = Identifier.of("petsplus", "radiant");

        NatureAdjustment first = NatureModifierSampler.sample(natureId, 1L);
        NatureAdjustment second = NatureModifierSampler.sample(natureId, 2L);

        boolean statsDiffer = Math.abs(first.majorValue() - second.majorValue()) > 1.0e-6
            || Math.abs(first.minorValue() - second.minorValue()) > 1.0e-6;
        boolean tuningDiffers = Math.abs(first.volatilityMultiplier() - second.volatilityMultiplier()) > 1.0e-6
            || Math.abs(first.resilienceMultiplier() - second.resilienceMultiplier()) > 1.0e-6
            || Math.abs(first.contagionModifier() - second.contagionModifier()) > 1.0e-6
            || Math.abs(first.guardModifier() - second.guardModifier()) > 1.0e-6;
        boolean emotionDiffers = !first.emotionProfile().equals(second.emotionProfile());

        assertTrue(statsDiffer || tuningDiffers || emotionDiffers,
            "Different seeds should produce unique nature rolls");
    }

    @Test
    void emotionProfileStaysWithinBounds() {
        Identifier natureId = Identifier.of("petsplus", "nocturne");
        NatureAdjustment adjustment = NatureModifierSampler.sample(natureId, 99L);
        PetComponent.NatureEmotionProfile profile = adjustment.emotionProfile();

        assertNotNull(profile);
        if (profile.majorEmotion() != null) {
            assertTrue(profile.majorStrength() >= 0.05f && profile.majorStrength() <= 1.0f);
        }
        if (profile.minorEmotion() != null) {
            assertTrue(profile.minorStrength() >= 0.05f && profile.minorStrength() <= 1.0f);
        }
        if (profile.quirkEmotion() != null) {
            assertTrue(profile.quirkStrength() >= 0.05f && profile.quirkStrength() <= 1.0f);
        }
    }
}
