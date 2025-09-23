package woflo.petsplus.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.registry.PetRoleType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds per-role configuration templates derived from datapack definitions.
 * Keeping this logic isolated keeps PetsPlusConfig lean and makes it easier to
 * evolve the template output without touching loader plumbing.
 */
public final class RoleConfigTemplate {

    private RoleConfigTemplate() {
    }

    public static JsonObject fromRole(Identifier roleId, PetRoleType roleType) {
        return roleType != null ? fromDefinition(roleType) : generic(roleId);
    }

    private static JsonObject fromDefinition(PetRoleType roleType) {
        JsonObject template = generic(roleType.id());
        template.addProperty(
            "_comment",
            "Overrides for role '" + roleType.id() + "'. Remove keys you don't tweak to keep datapack defaults."
        );
        template.add("tribute_items", roleTributeJson(roleType.tributeDefaults()));
        template.add("passive_auras", passiveAuras(roleType.passiveAuras()));
        template.add("support_potion", supportPotion(roleType.supportPotionBehavior()));
        addAbilityHints(template, roleType.defaultAbilities());
        return template;
    }

    private static JsonObject generic(Identifier roleId) {
        JsonObject template = new JsonObject();
        template.addProperty(
            "_comment",
            "Overrides for role '" + roleId + "'. Add or remove keys to customise behaviour."
        );
        template.add(
            "tribute_items",
            placeholderObject("Map level milestones to item IDs. Delete to fall back to datapack defaults.")
        );
        template.add(
            "passive_auras",
            placeholderObject("Add objects keyed by aura id to override pulse timing, radius, and effects.")
        );
        template.add(
            "support_potion",
            placeholderObject("Tweak stored potion broadcasts here (interval, radius, potion effect).")
        );
        return template;
    }

    private static JsonObject placeholderObject(String comment) {
        JsonObject placeholder = new JsonObject();
        placeholder.addProperty("_comment", comment);
        return placeholder;
    }

    private static JsonObject roleTributeJson(PetRoleType.TributeDefaults defaults) {
        JsonObject tributes = placeholderObject("Override milestone tribute items (level -> item id).");
        if (defaults != null) {
            for (Map.Entry<Integer, Identifier> entry : defaults.milestoneItems().entrySet()) {
                Identifier itemId = entry.getValue();
                if (itemId != null) {
                    tributes.addProperty(Integer.toString(entry.getKey()), itemId.toString());
                }
            }
        }
        return tributes;
    }

    private static JsonObject passiveAuras(List<PetRoleType.PassiveAura> passiveAuras) {
        if (passiveAuras == null || passiveAuras.isEmpty()) {
            return placeholderObject("Add aura overrides keyed by datapack aura id.");
        }

        JsonObject auras = new JsonObject();
        auras.addProperty("_comment", "Override individual aura entries keyed by their datapack id.");
        for (PetRoleType.PassiveAura aura : passiveAuras) {
            JsonObject auraJson = new JsonObject();
            auraJson.addProperty(
                "_comment",
                "Defaults derived from datapack aura '" + aura.id() + "'. Remove keys to inherit datapack values."
            );
            auraJson.addProperty("interval", aura.intervalTicks());
            auraJson.addProperty("radius", aura.radius());
            auraJson.addProperty("min_level", aura.minLevel());
            auraJson.addProperty("require_sitting", aura.requireSitting());
            if (aura.particleEvent() != null && !aura.particleEvent().isBlank()) {
                auraJson.addProperty("particle_event", aura.particleEvent());
            }
            auraJson.add("effects", auraEffectsArray(aura.effects()));
            auraJson.add("message", messageTemplate(aura.message(), "Set translation/fallback text or remove."));
            auraJson.add("sound", soundTemplate(aura.sound()));
            auras.add(aura.id(), auraJson);
        }
        return auras;
    }

    private static JsonArray auraEffectsArray(List<PetRoleType.AuraEffect> effects) {
        JsonArray array = new JsonArray();
        if (effects == null) {
            return array;
        }
        for (PetRoleType.AuraEffect effect : effects) {
            JsonObject effectJson = new JsonObject();
            effectJson.addProperty("effect", effect.effectId().toString());
            effectJson.addProperty("target", effect.target().name().toLowerCase(Locale.ROOT));
            effectJson.addProperty("duration_ticks", effect.durationTicks());
            effectJson.addProperty("amplifier", effect.amplifier());
            effectJson.addProperty("min_level", effect.minLevel());
            array.add(effectJson);
        }
        return array;
    }

    private static JsonObject supportPotion(PetRoleType.SupportPotionBehavior behavior) {
        if (behavior == null) {
            return placeholderObject("Add support potion overrides (interval, radius, fallback effect).");
        }

        JsonObject support = new JsonObject();
        support.addProperty(
            "_comment",
            "Defaults derived from datapack entry '" + behavior.id() + "'. Remove keys to inherit datapack values."
        );
        support.addProperty("interval", behavior.intervalTicks());
        support.addProperty("radius", behavior.radius());
        support.addProperty("min_level", behavior.minLevel());
        support.addProperty("require_sitting", behavior.requireSitting());
        support.addProperty("effect_duration", behavior.effectDurationTicks());
        support.addProperty("apply_to_pet", behavior.applyToPet());
        if (behavior.fallbackEffect() != null) {
            support.addProperty("fallback_effect", behavior.fallbackEffect().toString());
        }
        if (behavior.particleEvent() != null && !behavior.particleEvent().isBlank()) {
            support.addProperty("particle_event", behavior.particleEvent());
        }
        support.add("message", messageTemplate(behavior.message(), "Set broadcast messaging or remove."));
        support.add("sound", soundTemplate(behavior.sound()));
        return support;
    }

    private static JsonObject messageTemplate(PetRoleType.Message message, String comment) {
        JsonObject json = new JsonObject();
        if (message != null && message.isPresent()) {
            if (message.translationKey() != null && !message.translationKey().isBlank()) {
                json.addProperty("translation_key", message.translationKey());
            }
            if (message.fallback() != null && !message.fallback().isBlank()) {
                json.addProperty("fallback", message.fallback());
            }
        } else {
            json.addProperty("_comment", comment);
        }
        return json;
    }

    private static JsonObject soundTemplate(PetRoleType.SoundCue sound) {
        JsonObject json = new JsonObject();
        if (sound != null && sound.isPresent()) {
            json.addProperty("sound", sound.soundId().toString());
            json.addProperty("volume", sound.volume());
            json.addProperty("pitch", sound.pitch());
        } else {
            json.addProperty("_comment", "Set minecraft sound id/volume/pitch or remove to stay silent.");
        }
        return json;
    }

    private static void addAbilityHints(JsonObject template, List<Identifier> abilityIds) {
        if (abilityIds == null || abilityIds.isEmpty()) {
            return;
        }

        JsonObject hint = new JsonObject();
        hint.addProperty(
            "_comment",
            "These abilities unlock by default. Adjust ability overrides in core.json > abilities.<id>."
        );
        JsonArray ids = new JsonArray();
        for (Identifier abilityId : abilityIds) {
            ids.add(abilityId.toString());
        }
        hint.add("ids", ids);
        template.add("_ability_hints", hint);
    }
}
