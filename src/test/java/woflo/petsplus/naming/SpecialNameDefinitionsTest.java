package woflo.petsplus.naming;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.naming.AttributeKey;
import woflo.petsplus.naming.NameAffinityDefinitions;
import woflo.petsplus.naming.SpecialNameDefinitions.SpecialNameEntry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialNameDefinitionsTest {

    @BeforeAll
    static void bootstrapDefinitions() throws Exception {
        SpecialNameDefinitions.bootstrap();
    }

    @Test
    void wofloEntryGrantsCreatorCrownAndMessage() {
        SpecialNameEntry entry = SpecialNameDefinitions.get("woflo");
        assertNotNull(entry, "Woflo entry should be registered");
        assertTrue(entry.grantsCreatorCrown(), "Woflo should grant the creator crown");
        assertEquals("Your pet has been blessed with the developer's crown!", entry.ownerMessage());
        assertTrue(entry.stateData().isEmpty(), "Woflo should not add extra friend tags");
    }

    @Test
    void gabeEntryProvidesSharedFriendTagWithoutCrown() {
        SpecialNameEntry entry = SpecialNameDefinitions.get("gabe");
        assertNotNull(entry, "Gabe entry should be registered");
        assertTrue(Boolean.TRUE.equals(entry.stateData().get("loch_n_load_tag")), "Gabe friend tag expected");
        assertEquals(1, entry.stateData().size(), "Gabe should only register the shared friend tag");
        assertTrue(!entry.grantsCreatorCrown(), "Gabe should not grant the creator crown");
    }

    @Test
    void lochEntrySharesFriendTag() {
        SpecialNameEntry entry = SpecialNameDefinitions.get("loch");
        assertNotNull(entry, "Loch entry should be registered");
        assertTrue(Boolean.TRUE.equals(entry.stateData().get("loch_n_load_tag")), "Loch discrete tag expected");
        assertEquals(1, entry.stateData().size(), "Loch should only register the shared friend tag");
        assertTrue(!entry.grantsCreatorCrown(), "Loch should not grant the creator crown");

        SpecialNameEntry gabeEntry = SpecialNameDefinitions.get("gabe");
        assertEquals(gabeEntry.stateData(), entry.stateData(), "Gabe and Loch should share the same friend tag");
    }

    @Test
    void reiEntryCarriesAffinityProfiles() {
        SpecialNameEntry entry = SpecialNameDefinitions.get("rei");
        assertNotNull(entry, "Rei entry should be registered");
        assertTrue(!entry.affinityProfiles().isEmpty(), "Rei should maintain affinity definitions");
    }

    @Test
    void customEntriesCanBeReplacedAndRemoved() {
        SpecialNameEntry first = new SpecialNameEntry(
            "custom_friend",
            List.of("Custom"),
            NameParser.MatchMode.WORD_BOUNDARY,
            3,
            false,
            Map.of("custom_tag", true),
            null,
            List.of()
        );

        SpecialNameDefinitions.register(first);
        SpecialNameEntry registered = SpecialNameDefinitions.get("custom_friend");
        assertNotNull(registered, "Custom entry should be available after registration");
        assertEquals(Map.of("custom_tag", true), registered.stateData(), "Initial state tag should match");

        SpecialNameEntry replacement = new SpecialNameEntry(
            "custom_friend",
            List.of("Custom"),
            NameParser.MatchMode.WORD_BOUNDARY,
            3,
            false,
            Map.of("custom_tag_updated", true),
            null,
            List.of()
        );

        SpecialNameDefinitions.register(replacement);
        SpecialNameEntry replaced = SpecialNameDefinitions.get("custom_friend");
        assertNotNull(replaced, "Replacement entry should be accessible");
        assertEquals(Map.of("custom_tag_updated", true), replaced.stateData(), "State map should reflect replacement metadata");

        assertTrue(SpecialNameDefinitions.unregister("custom_friend"), "Custom entry should unregister successfully");
        assertNull(SpecialNameDefinitions.get("custom_friend"), "Entry should be absent after unregistering");
    }

    @Test
    void reiEmitsSpecialNameAndAffinityAttributes() throws Exception {
        List<AttributeKey> attributes = parseExactAttributes("Rei");
        Map<String, AttributeKey> byType = attributesByType(attributes);
        AttributeKey specialName = byType.get("special_name");
        AttributeKey affinity = byType.get("name_affinity");

        assertNotNull(specialName, "Rei should emit the special_name attribute");
        assertEquals("rei", specialName.normalizedValue(), "Special name attribute should normalize to rei");
        assertNotNull(affinity, "Rei should emit the name_affinity attribute");
        assertEquals("rei", affinity.normalizedValue(), "Affinity attribute should normalize to rei");
    }

    @Test
    void dualMetadataEntriesApplyBothHandlers() throws Exception {
        SpecialNameEntry dualEntry = new SpecialNameEntry(
            "dualtest",
            List.of("DualTest"),
            NameParser.MatchMode.WORD_BOUNDARY,
            6,
            false,
            Map.of("dual_flag", true),
            null,
            List.of(NameAffinityDefinitions.RoleAffinityProfile.uniform(PetRoleType.GUARDIAN_ID, 0.05f))
        );

        SpecialNameDefinitions.register(dualEntry);
        try {
            List<AttributeKey> attributes = parseExactAttributes("DualTest");
            Map<String, AttributeKey> byType = attributesByType(attributes);

            AttributeKey special = byType.get("special_name");
            AttributeKey affinity = byType.get("name_affinity");

            assertNotNull(special, "DualTest should retain its special_name attribute");
            assertNotNull(affinity, "DualTest should retain its name_affinity attribute");
            assertEquals("dualtest", special.normalizedValue(), "Special name should target the dualtest key");
            assertEquals("dualtest", affinity.normalizedValue(), "Affinity should target the dualtest key");

            List<NameAffinityDefinitions.RoleAffinityVector> vectors = NameAffinityDefinitions.resolveVectors("dualtest");
            assertTrue(!vectors.isEmpty(), "Affinity vectors should resolve even without runtime registries");
            assertEquals(PetRoleType.GUARDIAN_ID, vectors.getFirst().roleId(), "Guardian affinity should be registered");
        } finally {
            SpecialNameDefinitions.unregister("dualtest");
        }
    }

    @Test
    void targetedUnregisterRemovesOnlySpecifiedAttribute() throws Exception {
        AttributeKey first = new AttributeKey("custom_type_one", "primary", 1);
        AttributeKey second = new AttributeKey("custom_type_two", "secondary", 1);

        NameParser.registerExactPattern("TargetPattern", first, NameParser.MatchMode.WORD_BOUNDARY);
        NameParser.registerExactPattern("TargetPattern", second, NameParser.MatchMode.WORD_BOUNDARY);

        try {
            assertDoesNotThrow(() -> NameParser.unregisterExactPattern("TargetPattern", "custom_type_one", "primary"));

            List<AttributeKey> attributes = parseExactAttributes("TargetPattern");
            boolean firstPresent = attributes.stream()
                .anyMatch(attribute -> attribute.normalizedType().equals("custom_type_one"));
            boolean secondPresent = attributes.stream()
                .anyMatch(attribute -> attribute.normalizedType().equals("custom_type_two"));

            assertFalse(firstPresent, "Targeted removal should eliminate the first attribute");
            assertTrue(secondPresent, "Targeted removal should preserve unrelated attributes");
        } finally {
            NameParser.unregisterExactPattern("TargetPattern");
        }
    }

    private static List<AttributeKey> parseExactAttributes(String rawName) throws Exception {
        Method sanitize = NameParser.class.getDeclaredMethod("sanitizeName", String.class);
        sanitize.setAccessible(true);
        String sanitized = (String) sanitize.invoke(null, rawName);

        Method parse = NameParser.class.getDeclaredMethod("parseExactPatterns", String.class, boolean.class);
        parse.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<AttributeKey> exact = (List<AttributeKey>) parse.invoke(null, sanitized, false);

        Method process = NameParser.class.getDeclaredMethod("processAttributes", List.class, int.class);
        process.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<AttributeKey> processed = (List<AttributeKey>) process.invoke(null, exact, Integer.MAX_VALUE);
        return processed;
    }

    private static Map<String, AttributeKey> attributesByType(List<AttributeKey> attributes) {
        Map<String, AttributeKey> byType = new HashMap<>();
        for (AttributeKey attribute : attributes) {
            byType.put(attribute.normalizedType(), attribute);
        }
        return byType;
    }
}
