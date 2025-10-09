package woflo.petsplus.events;

import org.junit.jupiter.api.Test;
import woflo.petsplus.state.PetComponent;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EmotionContradictionResolverTest {

    @Test
    void guardianVigilAndProtectiveCanCoexist() {
        Map<PetComponent.Emotion, Float> emotions = new EnumMap<>(PetComponent.Emotion.class);
        emotions.put(PetComponent.Emotion.GUARDIAN_VIGIL, 0.8f);
        emotions.put(PetComponent.Emotion.PROTECTIVE, 0.6f);

        Map<PetComponent.Emotion, Float> resolved = EmotionContradictionResolver.resolveContradictions(emotions);

        assertTrue(resolved.containsKey(PetComponent.Emotion.GUARDIAN_VIGIL),
            "Guardian Vigil should persist through resolution");
        assertTrue(resolved.containsKey(PetComponent.Emotion.PROTECTIVE),
            "Protective should coexist with Guardian Vigil after rebalance");
    }

    @Test
    void guardianVigilFiltersRestlessButKeepsProtective() {
        Map<PetComponent.Emotion, Float> emotions = new EnumMap<>(PetComponent.Emotion.class);
        emotions.put(PetComponent.Emotion.GUARDIAN_VIGIL, 0.7f);
        emotions.put(PetComponent.Emotion.PROTECTIVE, 0.6f);
        emotions.put(PetComponent.Emotion.RESTLESS, 0.5f);

        Map<PetComponent.Emotion, Float> resolved = EmotionContradictionResolver.resolveContradictions(emotions);

        assertTrue(resolved.containsKey(PetComponent.Emotion.GUARDIAN_VIGIL),
            "Guardian Vigil should have the highest priority in threat conflicts");
        assertTrue(resolved.containsKey(PetComponent.Emotion.PROTECTIVE),
            "Protective should remain when Guardian Vigil is dominant");
        assertEquals(2, resolved.size(), "Guardian Vigil should suppress restlessness while keeping protective drive");
    }

    @Test
    void protectiveFiltersStartleInGuardianPresence() {
        Map<PetComponent.Emotion, Float> emotions = new EnumMap<>(PetComponent.Emotion.class);
        emotions.put(PetComponent.Emotion.GUARDIAN_VIGIL, 0.7f);
        emotions.put(PetComponent.Emotion.PROTECTIVE, 0.6f);
        emotions.put(PetComponent.Emotion.STARTLE, 0.5f);

        Map<PetComponent.Emotion, Float> resolved = EmotionContradictionResolver.resolveContradictions(emotions);

        assertTrue(resolved.containsKey(PetComponent.Emotion.GUARDIAN_VIGIL),
            "Guardian Vigil should persist through resolution");
        assertTrue(resolved.containsKey(PetComponent.Emotion.PROTECTIVE),
            "Protective should remain when Guardian Vigil is dominant");
        assertEquals(2, resolved.size(), "Protective should absorb the STARTLE contradiction while Guardian Vigil leads");
    }
}
