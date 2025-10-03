package woflo.petsplus.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityTriggerRegistrationTest {

    @Test
    void edgeStepAbilityUsesNewFallDamageTriggerId() throws IOException {
        JsonObject trigger = loadAbilityTrigger("edge_step.json");
        assertEquals("owner_incoming_damage", trigger.get("event").getAsString());
    }

    @Test
    void projectileLevitationAbilityUsesProjectileTriggerId() throws IOException {
        JsonObject trigger = loadAbilityTrigger("projectile_levitation.json");
        assertEquals("owner_shot_projectile", trigger.get("event").getAsString());
    }

    @Test
    void strikerExecutionAbilityUsesOutgoingDamageTriggerId() throws IOException {
        JsonObject trigger = loadAbilityTrigger("striker_execution.json");
        assertEquals("owner_outgoing_damage", trigger.get("event").getAsString());
    }

    @Test
    void ownerBeginFallAbilitiesUseNewMinFallField() throws IOException {
        JsonObject windlashTrigger = loadAbilityTrigger("windlash_rider.json");
        assertTrue(windlashTrigger.has("min_fall"), "Windlash Rider trigger should define min_fall");
        assertEquals(3.0, windlashTrigger.get("min_fall").getAsDouble());

        JsonObject galePaceTrigger = loadAbilityTrigger("gale_pace.json");
        assertTrue(galePaceTrigger.has("min_fall"), "Gale Pace trigger should define min_fall");
        assertEquals(2.0, galePaceTrigger.get("min_fall").getAsDouble());
    }

    @Test
    void mountedExtraRollsRequiresMountedOwnerKey() throws IOException {
        JsonObject trigger = loadAbilityTrigger("mounted_extra_rolls.json");
        assertTrue(trigger.has("require_mounted_owner"), "Mounted Extra Rolls should require mounted owner");
        assertEquals(true, trigger.get("require_mounted_owner").getAsBoolean());
    }

    @Test
    void bulwarkRedirectUsesPercentFieldForProjectileDr() throws IOException {
        JsonObject ability = loadAbilityJson("bulwark_redirect.json");
        JsonObject trigger = ability.getAsJsonObject("trigger");
        assertNotNull(trigger, "Bulwark Redirect should define a trigger");
        assertEquals("owner_incoming_damage", trigger.get("event").getAsString());

        JsonArray effects = ability.getAsJsonArray("effects");
        assertNotNull(effects, "Bulwark Redirect should define effects");

        JsonObject redirectEffect = effects.get(0).getAsJsonObject();
        assertEquals("guardian_bulwark_redirect", redirectEffect.get("type").getAsString(),
            "First effect should run the guardian bulwark redirect handler");

        JsonObject drEffect = null;
        for (int i = 0; i < effects.size(); i++) {
            JsonObject effect = effects.get(i).getAsJsonObject();
            if ("projectile_dr_for_owner".equals(effect.get("type").getAsString())) {
                drEffect = effect;
                break;
            }
        }

        assertNotNull(drEffect, "Bulwark Redirect should include projectile DR effect");
        assertTrue(drEffect.has("percent"), "Projectile DR effect should use percent field");
        assertEquals(0.25, drEffect.get("percent").getAsDouble(), 1.0E-6);
        assertEquals("guardian_bulwark_redirect_success", drEffect.get("require_data_flag").getAsString(),
            "Projectile DR should require bulwark success flag");
    }

    private static JsonObject loadAbilityTrigger(String fileName) throws IOException {
        JsonObject json = loadAbilityJson(fileName);
        JsonObject trigger = json.getAsJsonObject("trigger");
        assertNotNull(trigger, "Ability JSON should contain a trigger object");
        assertNotNull(trigger.get("event"), "Ability trigger must define an event");
        return trigger;
    }

    private static JsonObject loadAbilityJson(String fileName) throws IOException {
        Path path = Path.of("src", "main", "resources", "data", "petsplus", "abilities", fileName);
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
