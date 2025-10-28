package woflo.petsplus.state.morality;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetComponent.HarmonyState;
import woflo.petsplus.state.relationships.RelationshipType;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MalevolenceLedgerVirtueViceTest {

    private static final Identifier CRUELTY = Identifier.of("petsplus", "vice/cruelty");
    private static final Identifier BETRAYAL = Identifier.of("petsplus", "vice/betrayal");
    private static final Identifier COMPASSION = Identifier.of("petsplus", "virtue/compassion");
    private static final Identifier LOYALTY = Identifier.of("petsplus", "virtue/loyalty");

    @BeforeEach
    void setUpRegistry() {
        Map<Identifier, MoralityAspectDefinition> definitions = new LinkedHashMap<>();
        definitions.put(CRUELTY, new MoralityAspectDefinition(
            CRUELTY,
            MoralityAspectDefinition.Kind.VICE,
            0f,
            1.0f,
            0f,
            1.0f,
            Map.of(COMPASSION, -0.1f)
        ));
        definitions.put(BETRAYAL, new MoralityAspectDefinition(
            BETRAYAL,
            MoralityAspectDefinition.Kind.VICE,
            0f,
            1.0f,
            0f,
            1.0f,
            Map.of(LOYALTY, -0.08f)
        ));
        definitions.put(COMPASSION, new MoralityAspectDefinition(
            COMPASSION,
            MoralityAspectDefinition.Kind.VIRTUE,
            0.6f,
            1.0f,
            0f,
            1.0f,
            Map.of()
        ));
        definitions.put(LOYALTY, new MoralityAspectDefinition(
            LOYALTY,
            MoralityAspectDefinition.Kind.VIRTUE,
            0.55f,
            1.0f,
            0f,
            1.0f,
            Map.of()
        ));

        MoralityAspectRegistry.reload(Map.of(
            Identifier.of("petsplus", "test_traits"),
            new MoralityAspectRegistry.Pack(true, definitions)
        ));
    }

    @AfterEach
    void clearRules() {
        MalevolenceRulesRegistry.reload(Map.of());
    }

    @Test
    void virtueRequirementsGateViceUntilSuppressedFalls() throws Exception {
        MalevolenceRules.TagRule crueltyRule = new MalevolenceRules.TagRule(
            1.0f,
            Map.of(),
            0f,
            0f,
            Map.of(CRUELTY, 1.0f),
            Map.of(COMPASSION, -0.45f),
            Map.of(COMPASSION, new MalevolenceRules.RequirementRange(0f, 0.5f))
        );

        MalevolenceRules rules = new MalevolenceRules(
            Map.of("cruelty", crueltyRule),
            Map.of(),
            Map.of(),
            Map.of(),
            MalevolenceRules.TelemetrySettings.EMPTY,
            new MalevolenceRules.Thresholds(0.5f, 0.2f, 20L, 1.0f, 0f, 1.0f, 1.0f, 0.2f, 2.0f),
            new MalevolenceRules.SpreeSettings(400L, 1.0f, 0.25f, 1.5f, 0.2f),
            MalevolenceRules.DisharmonySettings.DEFAULT,
            MalevolenceRules.ForgivenessSettings.DEFAULT,
            Identifier.of("petsplus", "morality/test"),
            true
        );

        MalevolenceRulesRegistry.reload(Map.of(Identifier.of("petsplus", "rules/cruelty"), rules));

        TestPetComponent pet = new TestPetComponent();
        pet.setOwnerUuid(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        pet.setStateData(PetComponent.StateKeys.MALEVOLENCE_PERSONA, List.of(1.0f, 1.0f, 0.0f));

        MalevolenceLedger ledger = pet.getMalevolenceLedger();
        ledger.onStateDataRehydrated(0L);

        MalevolenceLedger.DarkDeedContext context = new MalevolenceLedger.DarkDeedContext(
            null,
            null,
            Set.<Identifier>of(),
            Set.of("cruelty"),
            null,
            0f,
            false,
            RelationshipType.NEUTRAL
        );

        MalevolenceLedger.TriggerOutcome firstOutcome = ledger.recordDarkDeed(context, 10L);
        assertTrue(firstOutcome.suppressed(), "Virtue requirement should suppress the first deed");
        assertEquals("petsplus:virtue/compassion", firstOutcome.reason());
        assertTrue(firstOutcome.virtueLevels().get(COMPASSION) < 0.6f, "Compassion should erode when suppressed");

        Map<Identifier, MoralityAspectState> viceStates = extractViceStates(ledger);
        MoralityAspectState crueltyState = viceStates.get(CRUELTY);
        assertNotNull(crueltyState);
        assertEquals(0f, crueltyState.value(), 1.0e-5f, "Suppressed cruelty should not accrue score");
        assertTrue(crueltyState.suppressedCharge() > 0f, "Suppressed cruelty stores charge for narrative feedback");

        Map<Identifier, MoralityAspectState> virtueStates = extractVirtueStates(ledger);
        MoralityAspectState compassionState = virtueStates.get(COMPASSION);
        assertNotNull(compassionState);
        float compassionBefore = compassionState.value();

        float initialSuppressedCharge = crueltyState.suppressedCharge();
        MalevolenceLedger.TriggerOutcome secondOutcome = ledger.recordDarkDeed(context, 30L);
        assertFalse(secondOutcome.suppressed(), "Compassion collapse should let cruelty break through on the second deed");
        MoralityAspectState crueltyAfterSecond = extractViceStates(ledger).get(CRUELTY);
        assertTrue(crueltyAfterSecond.value() > 0f, "Cruelty should start accumulating once the gate falls");
        assertTrue(crueltyAfterSecond.suppressedCharge() <= initialSuppressedCharge,
            "Suppression charge should bleed once the vice begins scoring");
        float compassionAfterSecond = extractVirtueStates(ledger).get(COMPASSION).value();
        assertTrue(compassionAfterSecond <= compassionBefore,
            "Compassion should not rebound without counteracting deeds");

        MalevolenceLedger.TriggerOutcome thirdOutcome = ledger.recordDarkDeed(context, 50L);
        assertFalse(thirdOutcome.suppressed(), "Third deed should continue feeding the awakened cruelty");
        MoralityAspectState crueltyAfterThird = extractViceStates(ledger).get(CRUELTY);
        assertTrue(crueltyAfterThird.value() >= crueltyAfterSecond.value(),
            "Cruelty should not recede once the vice awakens");
        assertTrue(crueltyAfterThird.spreeCount() >= crueltyAfterSecond.spreeCount(),
            "Spree tracking should advance as cruelty repeats");
    }

    @Test
    void suppressedDeedDoesNotAdvanceGlobalSpree() throws Exception {
        MalevolenceRules.TagRule crueltyRule = new MalevolenceRules.TagRule(
            1.0f,
            Map.of(),
            0f,
            0f,
            Map.of(CRUELTY, 1.0f),
            Map.of(COMPASSION, -0.45f),
            Map.of(COMPASSION, new MalevolenceRules.RequirementRange(0f, 0.5f))
        );

        MalevolenceRules rules = new MalevolenceRules(
            Map.of("cruelty", crueltyRule),
            Map.of(),
            Map.of(),
            Map.of(),
            MalevolenceRules.TelemetrySettings.EMPTY,
            new MalevolenceRules.Thresholds(0.5f, 0.2f, 20L, 1.0f, 0f, 1.0f, 1.0f, 0.2f, 2.0f),
            new MalevolenceRules.SpreeSettings(400L, 1.0f, 0.25f, 1.5f, 0.2f),
            MalevolenceRules.DisharmonySettings.DEFAULT,
            MalevolenceRules.ForgivenessSettings.DEFAULT,
            Identifier.of("petsplus", "morality/test"),
            true
        );

        MalevolenceRulesRegistry.reload(Map.of(Identifier.of("petsplus", "rules/cruelty_spree"), rules));

        TestPetComponent pet = new TestPetComponent();
        pet.setOwnerUuid(UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb"));
        pet.setStateData(PetComponent.StateKeys.MALEVOLENCE_PERSONA, List.of(1.0f, 1.0f, 0.0f));

        MalevolenceLedger ledger = pet.getMalevolenceLedger();
        ledger.onStateDataRehydrated(0L);

        MalevolenceLedger.DarkDeedContext context = new MalevolenceLedger.DarkDeedContext(
            null,
            null,
            Set.<Identifier>of(),
            Set.of("cruelty"),
            null,
            0f,
            false,
            RelationshipType.NEUTRAL
        );

        MalevolenceLedger.TriggerOutcome suppressed = ledger.recordDarkDeed(context, 20L);
        assertTrue(suppressed.suppressed(), "Virtue requirement should block the first deed");
        assertEquals(0, extractLedgerSpreeCount(ledger),
            "Suppressed deed should not advance the ledger spree count");

        MalevolenceLedger.TriggerOutcome breakthrough = ledger.recordDarkDeed(context, 40L);
        assertFalse(breakthrough.suppressed(), "Collapsed virtue should let the vice score on the second deed");
        assertEquals(1, extractLedgerSpreeCount(ledger),
            "First actual vice contribution should advance the spree once");
    }

    @Test
    void suppressedChargeRespectsConfiguredWeights() throws Exception {
        MalevolenceRules.TagRule sharedRule = new MalevolenceRules.TagRule(
            1.0f,
            Map.of(),
            0f,
            0f,
            Map.of(CRUELTY, 1.0f, BETRAYAL, 0.25f),
            Map.of(COMPASSION, -0.35f),
            Map.of(COMPASSION, new MalevolenceRules.RequirementRange(0.65f, 1.0f))
        );

        MalevolenceRules rules = new MalevolenceRules(
            Map.of("cruelty", sharedRule),
            Map.of(),
            Map.of(),
            Map.of(),
            MalevolenceRules.TelemetrySettings.EMPTY,
            new MalevolenceRules.Thresholds(0.5f, 0.2f, 20L, 1.0f, 0f, 1.0f, 1.0f, 0.2f, 2.0f),
            new MalevolenceRules.SpreeSettings(400L, 1.0f, 0.25f, 1.5f, 0.2f),
            MalevolenceRules.DisharmonySettings.DEFAULT,
            MalevolenceRules.ForgivenessSettings.DEFAULT,
            Identifier.of("petsplus", "morality/test"),
            true
        );

        MalevolenceRulesRegistry.reload(Map.of(Identifier.of("petsplus", "rules/weights"), rules));

        TestPetComponent pet = new TestPetComponent();
        pet.setOwnerUuid(UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff"));
        pet.setStateData(PetComponent.StateKeys.MALEVOLENCE_PERSONA, List.of(1.0f, 1.0f, 0.0f));

        MalevolenceLedger ledger = pet.getMalevolenceLedger();
        ledger.onStateDataRehydrated(0L);

        MalevolenceLedger.DarkDeedContext context = new MalevolenceLedger.DarkDeedContext(
            null,
            null,
            Set.<Identifier>of(),
            Set.of("cruelty"),
            null,
            0f,
            false,
            RelationshipType.NEUTRAL
        );

        MalevolenceLedger.TriggerOutcome outcome = ledger.recordDarkDeed(context, 120L);
        assertTrue(outcome.suppressed(), "Virtue requirement should block the vice contribution");

        Map<Identifier, MoralityAspectState> viceStates = extractViceStates(ledger);
        MoralityAspectState crueltyState = viceStates.get(CRUELTY);
        MoralityAspectState betrayalState = viceStates.get(BETRAYAL);
        assertNotNull(crueltyState);
        assertNotNull(betrayalState);
        assertEquals(1.0f, crueltyState.suppressedCharge(), 1.0e-5f, "Primary weight should store the full severity");
        assertEquals(0.25f, betrayalState.suppressedCharge(), 1.0e-5f,
            "Secondary weight should scale suppression proportionally");
    }

    @Test
    void personaAmplifiesViceAndSynergyDrainsVirtue() throws Exception {
        MalevolenceRules.TagRule betrayalRule = new MalevolenceRules.TagRule(
            1.2f,
            Map.of(),
            0.1f,
            0.2f,
            Map.of(BETRAYAL, 1.0f),
            Map.of(LOYALTY, -0.3f),
            Map.of()
        );

        MalevolenceRules rules = new MalevolenceRules(
            Map.of("betrayal", betrayalRule),
            Map.of(),
            Map.of(),
            Map.of(),
            MalevolenceRules.TelemetrySettings.EMPTY,
            new MalevolenceRules.Thresholds(1.0f, 0.3f, 30L, 1.0f, 0f, 1.0f, 1.0f, 0.1f, 2.0f),
            new MalevolenceRules.SpreeSettings(400L, 1.0f, 0.25f, 1.5f, 0.2f),
            MalevolenceRules.DisharmonySettings.DEFAULT,
            MalevolenceRules.ForgivenessSettings.DEFAULT,
            Identifier.of("petsplus", "morality/test"),
            true
        );

        MalevolenceRulesRegistry.reload(Map.of(Identifier.of("petsplus", "rules/betrayal"), rules));

        TestPetComponent pet = new TestPetComponent();
        pet.setOwnerUuid(UUID.fromString("ffffffff-1111-2222-3333-444444444444"));
        pet.setStateData(PetComponent.StateKeys.MALEVOLENCE_PERSONA, List.of(1.4f, 1.1f, 0.25f));

        MalevolenceLedger ledger = pet.getMalevolenceLedger();
        ledger.onStateDataRehydrated(0L);

        MalevolenceLedger.DarkDeedContext context = new MalevolenceLedger.DarkDeedContext(
            null,
            null,
            Set.<Identifier>of(),
            Set.of("betrayal"),
            null,
            0.3f,
            false,
            RelationshipType.NEUTRAL
        );

        MalevolenceLedger.TriggerOutcome outcome = ledger.recordDarkDeed(context, 200L);
        assertTrue(outcome.triggered(), "Persona-biased betrayal should trigger immediately");

        Map<Identifier, MoralityAspectState> viceStates = extractViceStates(ledger);
        MoralityAspectState betrayalState = viceStates.get(BETRAYAL);
        assertNotNull(betrayalState);
        assertTrue(betrayalState.value() > 1.2f, "Persona susceptibility should amplify vice accumulation");

        Map<Identifier, MoralityAspectState> virtueStates = extractVirtueStates(ledger);
        MoralityAspectState loyaltyState = virtueStates.get(LOYALTY);
        assertNotNull(loyaltyState);
        assertTrue(loyaltyState.value() < 0.55f, "Synergy should drain loyalty below its baseline");
    }

    @SuppressWarnings("unchecked")
    private static Map<Identifier, MoralityAspectState> extractViceStates(MalevolenceLedger ledger)
        throws NoSuchFieldException, IllegalAccessException {
        Field field = MalevolenceLedger.class.getDeclaredField("viceStates");
        field.setAccessible(true);
        return (Map<Identifier, MoralityAspectState>) field.get(ledger);
    }

    private static int extractLedgerSpreeCount(MalevolenceLedger ledger)
        throws NoSuchFieldException, IllegalAccessException {
        Field field = MalevolenceLedger.class.getDeclaredField("spreeCount");
        field.setAccessible(true);
        return field.getInt(ledger);
    }

    @SuppressWarnings("unchecked")
    private static Map<Identifier, MoralityAspectState> extractVirtueStates(MalevolenceLedger ledger)
        throws NoSuchFieldException, IllegalAccessException {
        Field field = MalevolenceLedger.class.getDeclaredField("virtueStates");
        field.setAccessible(true);
        return (Map<Identifier, MoralityAspectState>) field.get(ledger);
    }

    private static final class TestPetComponent extends PetComponent {
        private HarmonyState harmonyState = HarmonyState.empty();
        private Map<String, Object> overrides;

        private TestPetComponent() {
            super(null);
        }

        @Override
        public void onOwnerChanged(UUID previousOwnerUuid, UUID newOwnerUuid) {
            // Skip world interactions for isolated ledger tests.
        }

        @Override
        public HarmonyState getHarmonyState() {
            return harmonyState;
        }

        @Override
        public void applyHarmonyState(HarmonyState state) {
            harmonyState = state == null ? HarmonyState.empty() : state;
        }

        @Override
        public void setOwnerUuid(UUID ownerUuid) {
            getOwnerModule().setOwnerUuid(ownerUuid);
        }

        @Override
        public void setStateData(String key, Object value) {
            overrides().put(key, value);
        }

        @Override
        public void clearStateData(String key) {
            if (overrides != null) {
                overrides.remove(key);
            }
        }

        @Override
        public <T> T getStateData(String key, Class<T> type) {
            if (overrides != null) {
                Object value = overrides.get(key);
                if (type.isInstance(value)) {
                    return type.cast(value);
                }
            }
            return super.getStateData(key, type);
        }

        @Override
        public <T> T getStateData(String key, Class<T> type, T defaultValue) {
            if (overrides != null) {
                Object value = overrides.get(key);
                if (type.isInstance(value)) {
                    return type.cast(value);
                }
            }
            return defaultValue;
        }

        private Map<String, Object> overrides() {
            if (overrides == null) {
                overrides = new LinkedHashMap<>();
            }
            return overrides;
        }
    }
}

