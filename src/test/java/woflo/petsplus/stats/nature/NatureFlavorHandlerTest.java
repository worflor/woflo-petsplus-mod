package woflo.petsplus.stats.nature;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NatureFlavorHandlerTest {

    @Test
    void radiantOwnerSleepRespectsCooldown() {
        PetComponent.NatureEmotionProfile profile = new PetComponent.NatureEmotionProfile(
            PetComponent.Emotion.CHEERFUL, 0.34f,
            PetComponent.Emotion.HOPEFUL, 0.22f,
            PetComponent.Emotion.CONTENT, 0.18f);
        Map<String, Object> state = new HashMap<>();
        List<PetComponent.Emotion> emotions = new ArrayList<>();
        List<Float> amounts = new ArrayList<>();
        PetComponent component = prepareComponent(Identifier.of("petsplus", "radiant"), profile, state, emotions, amounts);

        NatureFlavorHandler.triggerForPet(null, component, null, null,
            NatureFlavorHandler.Trigger.OWNER_SLEEP, 1000L);

        assertEquals(1, emotions.size());
        assertEquals(PetComponent.Emotion.CHEERFUL, emotions.get(0));

        NatureFlavorHandler.triggerForPet(null, component, null, null,
            NatureFlavorHandler.Trigger.OWNER_SLEEP, 1005L);

        assertEquals(1, emotions.size(), "cooldown should suppress subsequent pulses");

        NatureFlavorHandler.triggerForPet(null, component, null, null,
            NatureFlavorHandler.Trigger.OWNER_SLEEP, 1300L);

        assertEquals(2, emotions.size(), "cooldown expiry should allow another pulse");
        assertEquals(PetComponent.Emotion.CHEERFUL, emotions.get(1));
    }

    @Test
    void archaeologyBrushPulsesMajorEmotion() {
        PetComponent.NatureEmotionProfile profile = new PetComponent.NatureEmotionProfile(
            PetComponent.Emotion.NOSTALGIA, 0.4f,
            PetComponent.Emotion.MONO_NO_AWARE, 0.2f,
            PetComponent.Emotion.CHEERFUL, 0.1f);
        Map<String, Object> state = new HashMap<>();
        List<PetComponent.Emotion> emotions = new ArrayList<>();
        List<Float> amounts = new ArrayList<>();
        PetComponent component = prepareComponent(Identifier.of("petsplus", "relic"), profile, state, emotions, amounts);

        NatureFlavorHandler.triggerForPet(null, component, null, null,
            NatureFlavorHandler.Trigger.ARCHAEOLOGY_BRUSH, 2000L);

        assertEquals(1, emotions.size());
        assertEquals(PetComponent.Emotion.NOSTALGIA, emotions.get(0));
        assertEquals(1, amounts.size());
        assertTrue(amounts.get(0) > 0f, "archaeology brush pulse should apply positive emotion");
    }

    @Test
    void trialKeyTriggerUsesMinorEmotion() {
        PetComponent.NatureEmotionProfile profile = new PetComponent.NatureEmotionProfile(
            PetComponent.Emotion.FOREBODING, 0.25f,
            PetComponent.Emotion.CURIOUS, 0.35f,
            PetComponent.Emotion.DISGUST, 0.05f);
        Map<String, Object> state = new HashMap<>();
        List<PetComponent.Emotion> emotions = new ArrayList<>();
        List<Float> amounts = new ArrayList<>();
        PetComponent component = prepareComponent(Identifier.of("petsplus", "unnatural"), profile, state, emotions, amounts);

        NatureFlavorHandler.triggerForPet(null, component, null, null,
            NatureFlavorHandler.Trigger.USE_TRIAL_KEY, 3600L);

        assertEquals(1, emotions.size());
        assertEquals(PetComponent.Emotion.CURIOUS, emotions.get(0));
        assertEquals(1, amounts.size());
        assertTrue(amounts.get(0) > 0f, "trial key pulse should apply positive emotion");
    }

    @Test
    void decoratedPotPlacementPulsesCeramicMinor() {
        PetComponent.NatureEmotionProfile profile = new PetComponent.NatureEmotionProfile(
            PetComponent.Emotion.NOSTALGIA, 0.35f,
            PetComponent.Emotion.CURIOUS, 0.25f,
            PetComponent.Emotion.MONO_NO_AWARE, 0.12f);
        Map<String, Object> state = new HashMap<>();
        List<PetComponent.Emotion> emotions = new ArrayList<>();
        List<Float> amounts = new ArrayList<>();
        PetComponent component = prepareComponent(Identifier.of("petsplus", "ceramic"), profile, state, emotions, amounts);

        NatureFlavorHandler.triggerForPet(null, component, null, null,
            NatureFlavorHandler.Trigger.DECORATED_POT_PLACED, 4200L);

        assertEquals(1, emotions.size());
        assertEquals(PetComponent.Emotion.CURIOUS, emotions.get(0));
        assertTrue(amounts.get(0) > 0f, "decorated pot placement should excite ceramic pets");
    }

    @Test
    void cherryPetalHarvestSupportsBlossomNature() {
        PetComponent.NatureEmotionProfile profile = new PetComponent.NatureEmotionProfile(
            PetComponent.Emotion.CHEERFUL, 0.38f,
            PetComponent.Emotion.HOPEFUL, 0.27f,
            PetComponent.Emotion.QUERECIA, 0.18f);
        Map<String, Object> state = new HashMap<>();
        List<PetComponent.Emotion> emotions = new ArrayList<>();
        List<Float> amounts = new ArrayList<>();
        PetComponent component = prepareComponent(Identifier.of("petsplus", "blossom"), profile, state, emotions, amounts);

        NatureFlavorHandler.triggerForPet(null, component, null, null,
            NatureFlavorHandler.Trigger.CHERRY_PETAL_HARVEST, 5200L);

        assertEquals(1, emotions.size());
        assertEquals(PetComponent.Emotion.HOPEFUL, emotions.get(0));
        assertTrue(amounts.get(0) > 0f, "petal harvest should nudge blossom pets");
    }

    @Test
    void redstonePulseDrivesClockworkMajorEmotion() {
        PetComponent.NatureEmotionProfile profile = new PetComponent.NatureEmotionProfile(
            PetComponent.Emotion.FOCUSED, 0.4f,
            PetComponent.Emotion.CURIOUS, 0.22f,
            PetComponent.Emotion.YUGEN, 0.14f);
        Map<String, Object> state = new HashMap<>();
        List<PetComponent.Emotion> emotions = new ArrayList<>();
        List<Float> amounts = new ArrayList<>();
        PetComponent component = prepareComponent(Identifier.of("petsplus", "clockwork"), profile, state, emotions, amounts);

        NatureFlavorHandler.triggerForPet(null, component, null, null,
            NatureFlavorHandler.Trigger.REDSTONE_PULSE, 6400L);

        assertEquals(1, emotions.size());
        assertEquals(PetComponent.Emotion.FOCUSED, emotions.get(0));
        assertTrue(amounts.get(0) > 0f, "redstone pulses should energize clockwork pets");
    }

    @Test
    void cherryBlossomBloomPulsesVerdantMinor() {
        PetComponent.NatureEmotionProfile profile = new PetComponent.NatureEmotionProfile(
            PetComponent.Emotion.CHEERFUL, 0.3f,
            PetComponent.Emotion.CONTENT, 0.4f,
            PetComponent.Emotion.LAGOM, 0.2f);
        Map<String, Object> state = new HashMap<>();
        List<PetComponent.Emotion> emotions = new ArrayList<>();
        List<Float> amounts = new ArrayList<>();
        PetComponent component = prepareComponent(Identifier.of("petsplus", "verdant"), profile, state, emotions, amounts);

        NatureFlavorHandler.triggerForPet(null, component, null, null,
            NatureFlavorHandler.Trigger.CHERRY_BLOSSOM_BLOOM, 5400L);

        assertEquals(1, emotions.size());
        assertEquals(PetComponent.Emotion.CONTENT, emotions.get(0));
        assertEquals(1, amounts.size());
        assertTrue(amounts.get(0) > 0f, "cherry blossom bloom pulse should be positive");
    }

    private static PetComponent prepareComponent(Identifier natureId,
                                                 PetComponent.NatureEmotionProfile profile,
                                                 Map<String, Object> state,
                                                 List<PetComponent.Emotion> emotions,
                                                 List<Float> amounts) {
        PetComponent component = mock(PetComponent.class);
        when(component.getNatureId()).thenReturn(natureId);
        when(component.getNatureEmotionProfile()).thenReturn(profile);

        when(component.getStateData(anyString(), Mockito.eq(Long.class), Mockito.anyLong()))
            .thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                Long fallback = invocation.getArgument(2);
                return (Long) state.getOrDefault(key, fallback);
            });
        when(component.getStateData(anyString(), Mockito.eq(Long.class)))
            .thenAnswer(invocation -> (Long) state.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            state.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(component).setStateData(anyString(), any());

        doAnswer(invocation -> {
            emotions.add(invocation.getArgument(0));
            amounts.add(invocation.getArgument(1));
            return null;
        }).when(component).pushEmotion(any(), anyFloat());

        return component;
    }
}
