package woflo.petsplus.effects;

import org.junit.jupiter.api.Test;
import woflo.petsplus.api.EffectContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
class HealOwnerFlatPctEffectTest {

    @Test
    void executeReturnsFalseWhenOwnerMissing() {
        EffectContext context = mock(EffectContext.class);
        when(context.getOwner()).thenReturn(null);

        HealOwnerFlatPctEffect effect = new HealOwnerFlatPctEffect(0.0, 0.25);

        boolean result = effect.execute(context);

        assertFalse(result, "Effect should not execute when owner is missing");
        verify(context).getOwner();
    }

    @Test
    void calculateHealAmountCombinesFlatAndPercent() {
        HealOwnerFlatPctEffect effect = new HealOwnerFlatPctEffect(3.0, 0.1);

        assertEquals(5.0f, effect.calculateHealAmount(20.0f), 1.0e-4f,
            "Heal amount should include flat and percent components");
    }
}
