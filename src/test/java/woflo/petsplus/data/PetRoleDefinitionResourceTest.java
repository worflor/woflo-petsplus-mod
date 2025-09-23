package woflo.petsplus.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetRoleDefinitionResourceTest {
    private static final List<String> BUILT_IN_ROLE_IDS = List.of(
        "guardian",
        "striker",
        "support",
        "scout",
        "skyrider",
        "enchantment_bound",
        "cursed_one",
        "eepy_eeper",
        "eclipsed"
    );

    @Test
    void allBuiltInRolesHaveDatapackDefinitions() {
        ClassLoader loader = getClass().getClassLoader();

        for (String roleId : BUILT_IN_ROLE_IDS) {
            String path = "data/petsplus/roles/" + roleId + ".json";
            try (InputStream stream = loader.getResourceAsStream(path)) {
                assertNotNull(stream, "Missing datapack definition for role petsplus:" + roleId);

                JsonElement element = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                assertTrue(element.isJsonObject(), "Role definition " + path + " must be a JSON object");
            } catch (Exception e) {
                throw new AssertionError("Failed to read role definition " + path, e);
            }
        }
    }
}
