package woflo.petsplus.mood.providers;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackContagionProviderTest {

    @Test
    void mirrorsDominantEmotionFromNearbyPackMate() {
        PackContagionProvider provider = new PackContagionProvider();
        PetComponent comp = mock(PetComponent.class);
        PetComponent allyComp = mock(PetComponent.class);

        when(allyComp.computeBondResilience(0L)).thenReturn(0.75f);
        when(allyComp.getDominantEmotion()).thenReturn(PetComponent.Emotion.UBUNTU);

        provider.applyAllyMirror(comp, allyComp, 9.0, 0.8f, 0L, true);

        ArgumentCaptor<Float> share = ArgumentCaptor.forClass(Float.class);
        verify(comp).addContagionShare(eq(PetComponent.Emotion.UBUNTU), share.capture());
        assertTrue(share.getValue() > 0f, "Pack contagion should add a positive share");
    }

    @Test
    void ignoresStrangersWithWeakBond() {
        PackContagionProvider provider = new PackContagionProvider();
        PetComponent comp = mock(PetComponent.class);
        PetComponent strangerComp = mock(PetComponent.class);

        when(strangerComp.computeBondResilience(0L)).thenReturn(0.3f);
        when(strangerComp.getDominantEmotion()).thenReturn(PetComponent.Emotion.ANGST);

        provider.applyAllyMirror(comp, strangerComp, 4.0, 0.3f, 0L, false);

        verify(comp, never()).addContagionShare(eq(PetComponent.Emotion.ANGST), anyFloat());
    }

    @Test
    void ownerCombatSpreadsProtectiveness() {
        PackContagionProvider provider = new PackContagionProvider();
        PetComponent comp = mock(PetComponent.class);
        OwnerCombatState state = mock(OwnerCombatState.class);

        when(state.isInCombat()).thenReturn(true);
        when(state.recentlyDamaged(200L, 80)).thenReturn(true);

        provider.applyOwnerMirror(comp, state, 0.9f, 200L);

        verify(comp).addContagionShare(eq(PetComponent.Emotion.GUARDIAN_VIGIL), anyFloat());
    }
}
