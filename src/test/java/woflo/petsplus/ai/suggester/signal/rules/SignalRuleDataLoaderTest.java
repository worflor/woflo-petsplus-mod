package woflo.petsplus.ai.suggester.signal.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SignalRuleDataLoaderTest {
    private static final Identifier MOOD_RULE_ID = Identifier.of("petsplus", "mood_blend");
    private static final Identifier NATURE_RULE_ID = Identifier.of("petsplus", "nature");

    @BeforeEach
    void resetRegistry() {
        SignalRuleRegistry.setMoodRules(MoodSignalRules.empty());
        SignalRuleRegistry.setNatureRules(NatureSignalRules.defaults());
    }

    @AfterEach
    void restoreDefaults() {
        SignalRuleRegistry.resetToDefaults();
    }

    @Test
    void loadsBundledRulesWithoutExplicitType() {
        Map<Identifier, JsonElement> prepared = new LinkedHashMap<>();
        prepared.put(MOOD_RULE_ID, readJsonWithoutType("/data/petsplus/ai_signal_rules/mood_blend.json"));
        prepared.put(NATURE_RULE_ID, readJsonWithoutType("/data/petsplus/ai_signal_rules/nature.json"));

        new SignalRuleDataLoader().apply(prepared, null);

        MoodSignalRules moodRules = SignalRuleRegistry.moodRules();
        NatureSignalRules natureRules = SignalRuleRegistry.natureRules();

        assertFalse(moodRules.moodRules().isEmpty(), "Mood rules should be populated");
        assertFalse(moodRules.emotionRules().isEmpty(), "Emotion rules should be populated");
        assertFalse(natureRules.rules().isEmpty(), "Nature rules should be populated");
    }

    private static JsonElement readJsonWithoutType(String resourcePath) {
        InputStream stream = SignalRuleDataLoaderTest.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IllegalStateException("Missing test resource: " + resourcePath);
        }
        try (InputStream input = stream; Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonObject()) {
                JsonObject object = parsed.getAsJsonObject();
                object.remove("type");
            }
            return parsed;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read JSON resource " + resourcePath, e);
        }
    }
}

