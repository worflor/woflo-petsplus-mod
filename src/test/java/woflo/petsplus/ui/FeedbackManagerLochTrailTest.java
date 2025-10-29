package woflo.petsplus.ui;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import woflo.petsplus.state.PetComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackManagerLochTrailTest {

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    private static final FeedbackManager.GroundTrailDefinition TRAIL_DEFINITION =
        new FeedbackManager.GroundTrailDefinition("loch_n_load_tag", "friend_loch_trail", "trail_loch_next_tick", 6L, 40L);

    @Test
    void prepareLochTrailEmissionSkipsWithoutTag() {
        PetComponent component = mock(PetComponent.class);
        when(component.getStateData(eq(TRAIL_DEFINITION.stateKey()), eq(Boolean.class), eq(Boolean.FALSE)))
            .thenReturn(Boolean.FALSE);

        boolean shouldEmit = FeedbackManager.prepareTrailEmission(component, TRAIL_DEFINITION, 40L);

        assertFalse(shouldEmit);
        verify(component).clearStateData(TRAIL_DEFINITION.nextTickStateKey());
        verify(component, never()).setStateData(eq(TRAIL_DEFINITION.nextTickStateKey()), any());
    }

    @Test
    void prepareLochTrailEmissionSchedulesWhenReady() {
        PetComponent component = mock(PetComponent.class);
        AtomicLong nextAllowed = new AtomicLong(0L);

        when(component.getStateData(eq(TRAIL_DEFINITION.stateKey()), eq(Boolean.class), eq(Boolean.FALSE)))
            .thenReturn(Boolean.TRUE);
        when(component.getLastAttackTick()).thenReturn(0L);
        when(component.getStateData(eq(TRAIL_DEFINITION.nextTickStateKey()), eq(Long.class), eq(0L)))
            .thenAnswer(invocation -> nextAllowed.get());
        doAnswer(invocation -> {
            Object value = invocation.getArgument(1);
            if (value instanceof Number number) {
                nextAllowed.set(number.longValue());
            }
            return null;
        }).when(component).setStateData(eq(TRAIL_DEFINITION.nextTickStateKey()), any());

        boolean first = FeedbackManager.prepareTrailEmission(component, TRAIL_DEFINITION, 200L);
        assertTrue(first);
        assertEquals(200L + TRAIL_DEFINITION.intervalTicks(), nextAllowed.get());

        boolean second = FeedbackManager.prepareTrailEmission(component, TRAIL_DEFINITION, 200L + 1L);
        assertFalse(second);
    }

    @Test
    void prepareLochTrailEmissionHonorsCombatCooldown() {
        PetComponent component = mock(PetComponent.class);

        when(component.getStateData(eq(TRAIL_DEFINITION.stateKey()), eq(Boolean.class), eq(Boolean.FALSE)))
            .thenReturn(Boolean.TRUE);
        when(component.getLastAttackTick()).thenReturn(195L);
        when(component.getStateData(eq(TRAIL_DEFINITION.nextTickStateKey()), eq(Long.class), eq(0L)))
            .thenReturn(0L);

        boolean shouldEmit = FeedbackManager.prepareTrailEmission(component, TRAIL_DEFINITION, 200L);

        assertFalse(shouldEmit);
        verify(component, never()).setStateData(eq(TRAIL_DEFINITION.nextTickStateKey()), any());
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void differentEventsDoNotShareDebounce() throws Exception {
        Field field = FeedbackManager.class.getDeclaredField("LAST_EMIT_TICK");
        field.setAccessible(true);
        ConcurrentHashMap<Object, Long> debounceMap = (ConcurrentHashMap<Object, Long>) field.get(null);
        debounceMap.clear();

        Class<?> keyClass = Class.forName("woflo.petsplus.ui.FeedbackManager$DebounceKey");
        var constructor = keyClass.getDeclaredConstructor(Object.class, String.class);
        constructor.setAccessible(true);

        UUID sourceId = UUID.randomUUID();
        Object firstKey = constructor.newInstance(sourceId, "test_ground_trail");
        Object secondKey = constructor.newInstance(sourceId, "test_ambient_event");

        debounceMap.put(firstKey, 400L);
        assertEquals(1, debounceMap.size());
        assertNotEquals(firstKey, secondKey);

        debounceMap.put(secondKey, 401L);
        assertEquals(2, debounceMap.size());

        Object duplicateFirst = constructor.newInstance(sourceId, "test_ground_trail");
        long stored = debounceMap.get(firstKey);
        long retrieved = debounceMap.getOrDefault(duplicateFirst, Long.MIN_VALUE);
        assertEquals(stored, retrieved);
        assertEquals(2, debounceMap.size());

        debounceMap.clear();
    }
}
