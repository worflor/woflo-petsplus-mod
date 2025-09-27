package woflo.petsplus.stats.nature;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NatureFlavorHandlerTest {

    @Test
    void radiantOwnerSleepRespectsCooldown() {
        PetComponent component = mock(PetComponent.class);
        when(component.getNatureId()).thenReturn(Identifier.of("petsplus", "radiant"));
        PetComponent.NatureEmotionProfile profile = new PetComponent.NatureEmotionProfile(
            PetComponent.Emotion.CHEERFUL, 0.34f,
            PetComponent.Emotion.HOPEFUL, 0.22f,
            PetComponent.Emotion.GLEE, 0.18f);
        when(component.getNatureEmotionProfile()).thenReturn(profile);

        Map<String, Object> state = new HashMap<>();
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

        List<PetComponent.Emotion> emotions = new ArrayList<>();
        doAnswer(invocation -> {
            emotions.add(invocation.getArgument(0));
            return null;
        }).when(component).pushEmotion(any(), anyFloat());

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
}
