package woflo.petsplus.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.stats.nature.NatureFlavorHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads datapack overrides for nature ambient flavor triggers so modpacks can
 * tweak or append new hooks without modifying code.
 */
public final class NatureFlavorDataLoader extends BaseJsonDataLoader<NatureFlavorHandler.NatureFlavorOverride> {

    public NatureFlavorDataLoader() {
        super("nature_flavors", "nature_flavor_data");
    }

    @Override
    protected String getResourceTypeName() {
        return "nature flavor";
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager) {
        Map<Identifier, NatureFlavorHandler.NatureFlavorOverride> overrides = new LinkedHashMap<>();

        for (Map.Entry<Identifier, JsonElement> entry : prepared.entrySet()) {
            Identifier fileId = entry.getKey();
            JsonElement element = entry.getValue();
            String source = describeSource(fileId);

            if (!element.isJsonObject()) {
                Petsplus.LOGGER.error("Nature flavor override at {} must be a JSON object", source);
                continue;
            }

            JsonObject json = element.getAsJsonObject();
            boolean replace = RegistryJsonHelper.getBoolean(json, "replace", false);
            JsonArray hooksArray = RegistryJsonHelper.getArray(json, "hooks");
            List<NatureFlavorHandler.HookConfig> hooks = new ArrayList<>();

            if (hooksArray != null) {
                for (int i = 0; i < hooksArray.size(); i++) {
                    JsonElement hookElement = hooksArray.get(i);
                    if (!hookElement.isJsonObject()) {
                        Petsplus.LOGGER.error("Nature flavor override {} has non-object hook at index {}", source, i);
                        continue;
                    }

                    JsonObject hookObject = hookElement.getAsJsonObject();
                    NatureFlavorHandler.Trigger trigger = parseTrigger(hookObject, source, i);
                    NatureFlavorHandler.Slot slot = parseSlot(hookObject, source, i);
                    if (trigger == null || slot == null) {
                        continue;
                    }

                    float scale = RegistryJsonHelper.getFloat(hookObject, "scale", 0.0f);
                    long cooldown = RegistryJsonHelper.getLong(hookObject, "cooldown", 200L);
                    boolean append = RegistryJsonHelper.getBoolean(hookObject, "append", false);
                    hooks.add(new NatureFlavorHandler.HookConfig(trigger, slot, scale, cooldown, append));
                }
            }

            overrides.put(fileId, new NatureFlavorHandler.NatureFlavorOverride(replace, List.copyOf(hooks)));
        }

        NatureFlavorHandler.reloadFromDatapack(overrides);
    }

    private static NatureFlavorHandler.Trigger parseTrigger(JsonObject hookObject, String source, int index) {
        String value = RegistryJsonHelper.getString(hookObject, "trigger", null);
        if (value == null) {
            Petsplus.LOGGER.error("Nature flavor override {} is missing trigger at index {}", source, index);
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return NatureFlavorHandler.Trigger.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            Petsplus.LOGGER.error("Nature flavor override {} references unknown trigger '{}' at index {}", source, value, index);
            return null;
        }
    }

    private static NatureFlavorHandler.Slot parseSlot(JsonObject hookObject, String source, int index) {
        String value = RegistryJsonHelper.getString(hookObject, "slot", null);
        if (value == null) {
            Petsplus.LOGGER.error("Nature flavor override {} is missing slot at index {}", source, index);
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return NatureFlavorHandler.Slot.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            Petsplus.LOGGER.error("Nature flavor override {} references unknown slot '{}' at index {}", source, value, index);
            return null;
        }
    }
}
