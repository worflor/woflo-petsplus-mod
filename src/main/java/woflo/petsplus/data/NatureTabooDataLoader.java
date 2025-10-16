package woflo.petsplus.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.stats.nature.NatureFlavorHandler;
import woflo.petsplus.stats.nature.NatureTabooHandler;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads datapack overrides for nature taboo definitions.
 */
public final class NatureTabooDataLoader extends BaseJsonDataLoader<NatureTabooHandler.TabooOverride> {

    public NatureTabooDataLoader() {
        super("nature_taboos", "nature_taboo_data");
    }

    @Override
    protected String getResourceTypeName() {
        return "nature taboo";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        Map<Identifier, NatureTabooHandler.TabooOverride> overrides = new LinkedHashMap<>();

        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier fileId = entry.getKey();
            JsonElement element = entry.getValue();
            String source = describeSource(fileId);

            if (!element.isJsonObject()) {
                Petsplus.LOGGER.error("Nature taboo override at {} must be a JSON object", source);
                continue;
            }

            JsonObject json = element.getAsJsonObject();
            NatureTabooHandler.TabooTrigger trigger = parseTrigger(json, source);
            NatureFlavorHandler.Slot warningSlot = parseSlot(json, source, "warning_slot");
            NatureFlavorHandler.Slot panicSlot = parseSlot(json, source, "panic_slot");
            if (trigger == null || warningSlot == null || panicSlot == null) {
                continue;
            }

            float warningScale = RegistryJsonHelper.getFloat(json, "warning_scale", -0.25f);
            float panicScale = RegistryJsonHelper.getFloat(json, "panic_scale", -0.5f);
            int warningThreshold = RegistryJsonHelper.getInt(json, "warning_threshold", 3);
            int panicThreshold = RegistryJsonHelper.getInt(json, "panic_threshold", 5);
            long window = RegistryJsonHelper.getLong(json, "accumulation_window", 200L);
            long cooldown = RegistryJsonHelper.getLong(json, "cooldown", 400L);
            String warningCue = RegistryJsonHelper.getString(json, "warning_cue", null);
            String panicCue = RegistryJsonHelper.getString(json, "panic_cue", null);

            NatureTabooHandler.TabooOverride override = new NatureTabooHandler.TabooOverride(
                trigger,
                warningSlot,
                warningScale,
                panicSlot,
                panicScale,
                warningThreshold,
                panicThreshold,
                window,
                cooldown,
                warningCue,
                panicCue
            );

            overrides.put(fileId, override);
        }

        NatureTabooHandler.reloadOverrides(overrides);
    }

    private static NatureTabooHandler.TabooTrigger parseTrigger(JsonObject object, String source) {
        String value = RegistryJsonHelper.getString(object, "trigger", null);
        if (value == null) {
            Petsplus.LOGGER.error("Nature taboo override {} is missing trigger", source);
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return NatureTabooHandler.TabooTrigger.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            Petsplus.LOGGER.error("Nature taboo override {} references unknown trigger '{}'", source, value);
            return null;
        }
    }

    private static NatureFlavorHandler.Slot parseSlot(JsonObject object, String source, String key) {
        String value = RegistryJsonHelper.getString(object, key, null);
        if (value == null) {
            Petsplus.LOGGER.error("Nature taboo override {} is missing {}", source, key);
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return NatureFlavorHandler.Slot.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            Petsplus.LOGGER.error("Nature taboo override {} references unknown slot '{}' for {}", source, value, key);
            return null;
        }
    }
}
