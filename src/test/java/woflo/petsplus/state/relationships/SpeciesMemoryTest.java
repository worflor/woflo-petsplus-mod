package woflo.petsplus.state.relationships;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeciesMemoryTest {

    private static final long ONE_DAY_TICKS = 24_000L;

    @Test
    void decayRateMultiplierScalesMemoryLoss() {
        SpeciesMemory.InteractionContext context = new SpeciesMemory.InteractionContext(0.6f, 0.2f, 0.3f);

        SpeciesMemory defaultDecayMemory = createMemoryWithRelationship(context);
        defaultDecayMemory.applyDecay(ONE_DAY_TICKS, 1.0f);
        float defaultFear = defaultDecayMemory.getFear(null);

        SpeciesMemory fastDecayMemory = createMemoryWithRelationship(context);
        fastDecayMemory.applyDecay(ONE_DAY_TICKS, 2.0f);
        float fastFear = fastDecayMemory.getFear(null);

        SpeciesMemory slowDecayMemory = createMemoryWithRelationship(context);
        slowDecayMemory.applyDecay(ONE_DAY_TICKS, 0.5f);
        float slowFear = slowDecayMemory.getFear(null);

        assertTrue(fastFear < defaultFear, "Higher decay rate should reduce fear more quickly");
        assertTrue(slowFear > defaultFear, "Lower decay rate should retain more fear");
    }

    @Test
    void zeroDecayRatePreservesMemories() {
        SpeciesMemory.InteractionContext context = new SpeciesMemory.InteractionContext(0.5f, 0.1f, 0.2f);

        SpeciesMemory memory = createMemoryWithRelationship(context);
        float fearBeforeDecay = memory.getFear(null);
        float huntingBeforeDecay = memory.getHuntingPreference(null);
        float cautionBeforeDecay = memory.getCaution(null);

        memory.applyDecay(ONE_DAY_TICKS, 0.0f);

        assertEquals(fearBeforeDecay, memory.getFear(null), 1.0e-6f,
            "Zero decay rate should leave fear unchanged");
        assertEquals(huntingBeforeDecay, memory.getHuntingPreference(null), 1.0e-6f,
            "Zero decay rate should leave hunting preference unchanged");
        assertEquals(cautionBeforeDecay, memory.getCaution(null), 1.0e-6f,
            "Zero decay rate should leave caution unchanged");
    }

    @Test
    void defaultApplyDecayMatchesExplicitUnitRate() {
        SpeciesMemory.InteractionContext context = new SpeciesMemory.InteractionContext(0.4f, 0.3f, 0.25f);

        SpeciesMemory implicitMemory = createMemoryWithRelationship(context);
        implicitMemory.applyDecay(ONE_DAY_TICKS);

        SpeciesMemory explicitMemory = createMemoryWithRelationship(context);
        explicitMemory.applyDecay(ONE_DAY_TICKS, 1.0f);

        assertEquals(explicitMemory.getFear(null), implicitMemory.getFear(null), 1.0e-6f,
            "Default decay method should match explicit 1.0 rate");
        assertEquals(explicitMemory.getHuntingPreference(null), implicitMemory.getHuntingPreference(null), 1.0e-6f,
            "Default decay should match explicit 1.0 rate for hunting preference");
        assertEquals(explicitMemory.getCaution(null), implicitMemory.getCaution(null), 1.0e-6f,
            "Default decay should match explicit 1.0 rate for caution");
    }

    private static SpeciesMemory createMemoryWithRelationship(SpeciesMemory.InteractionContext context) {
        SpeciesMemory memory = new SpeciesMemory();
        SpeciesMemory.SpeciesRelationship relationship = new SpeciesMemory.SpeciesRelationship();
        relationship.recordInteraction(context);

        Map<?, SpeciesMemory.SpeciesRelationship> memories = getMemoriesMap(memory);
        memories.put(null, relationship);
        return memory;
    }

    @SuppressWarnings("unchecked")
    private static Map<?, SpeciesMemory.SpeciesRelationship> getMemoriesMap(SpeciesMemory memory) {
        try {
            Field field = SpeciesMemory.class.getDeclaredField("memories");
            field.setAccessible(true);
            return (Map<?, SpeciesMemory.SpeciesRelationship>) field.get(memory);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to access species memories map", e);
        }
    }
}
