package woflo.petsplus.ai.goals.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.goals.GoalDefinition;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class GoalDataLoaderAssetsTest {

    @Test
    void packagedStretchAndYawnDefinitionMatchesDefaults() {
        GoalDefinition definition = parse("stretch_and_yawn");

        assertEquals(Identifier.of("petsplus", "stretch_and_yawn"), definition.id());
        assertEquals(GoalDefinition.Category.IDLE_QUIRK, definition.category());
        assertEquals(28, definition.priority());
        assertEquals(5, definition.minCooldownTicks());
        assertEquals(80, definition.maxCooldownTicks());
        assertEquals(GoalDefinition.IdleStaminaBias.LOW, definition.idleStaminaBias());
        assertFalse(definition.socialIdleBias());
        assertEquals(0.3f, definition.energyRange().x, 1e-6);
        assertEquals(0.7f, definition.energyRange().y, 1e-6);

        MobCapabilities.CapabilityProfile profile = new MobCapabilities.CapabilityProfile(
            false, false, false, false,
            false, false, false, false,
            false, false, false, false,
            false
        );
        assertTrue(definition.isCompatible(profile), "Any requirement should accept all capability profiles");
    }

    @Test
    void packagedSniffGroundDefinitionRequiresLandWalker() {
        GoalDefinition definition = parse("sniff_ground");

        MobCapabilities.CapabilityProfile landWalker = new MobCapabilities.CapabilityProfile(
            true,  // canWander
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            true,   // canMakeSound
            true,   // prefersLand
            false,
            false,
            false
        );

        MobCapabilities.CapabilityProfile swimmer = new MobCapabilities.CapabilityProfile(
            false,
            false,
            true,
            false,
            false,
            false,
            false,
            false,
            true,
            false,
            true,
            false,
            false
        );

        assertTrue(definition.isCompatible(landWalker), "Sniff ground should accept land wanderers");
        assertFalse(definition.isCompatible(swimmer), "Sniff ground should reject mobs without land wandering capability");
    }

    @Test
    void packagedPerchOnShoulderDefinitionCombinesCapabilities() {
        GoalDefinition definition = parse("perch_on_shoulder");

        MobCapabilities.CapabilityProfile matching = new MobCapabilities.CapabilityProfile(
            false,
            true,
            false,
            false,
            true,
            false,
            false,
            false,
            true,
            false,
            false,
            true,
            true
        );

        MobCapabilities.CapabilityProfile missingOwner = new MobCapabilities.CapabilityProfile(
            false,
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            true,
            false,
            false,
            true,
            true
        );

        assertTrue(definition.isCompatible(matching), "Perch on shoulder should accept capable flyers with owners");
        assertFalse(definition.isCompatible(missingOwner), "Perch on shoulder should require an owner");
        assertEquals(0.0f, definition.energyRange().x, 1e-6);
        assertEquals(0.7f, definition.energyRange().y, 1e-6);
    }

    private GoalDefinition parse(String id) {
        String path = "/data/petsplus/goal_catalogue/data_" + id + ".json";
        try (InputStream stream = GoalDataLoaderAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Expected resource " + path + " to be present");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return GoalDataParser.parse(Identifier.of("petsplus", id), json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load goal definition resource " + path, e);
        }
    }
}
