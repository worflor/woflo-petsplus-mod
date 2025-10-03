package woflo.petsplus.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.LevelReward;
import woflo.petsplus.api.rewards.LevelRewardParser;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parsed representation of a datapack-provided pet role definition. Converts raw
 * JSON payloads into {@link PetRoleType.Definition} instances for registry
 * hydration.
 */
public record PetRoleDefinition(
    Identifier id,
    String translationKey,
    Map<String, Float> baseStatScalars,
    List<Identifier> defaultAbilities,
    PetRoleType.XpCurve xpCurve,
    PetRoleType.Visual visual,
    Map<String, Float> statAffinities,
    PetRoleType.AttributeScaling attributeScaling,
    List<PetRoleType.PassiveAura> passiveAuras,
    PetRoleType.SupportPotionBehavior supportPotionBehavior,
    List<PetRoleType.MilestoneAdvancement> milestoneAdvancements,
    PetRoleType.Presentation presentation,
    Map<Integer, List<LevelReward>> levelRewards
) {
    private static final PetRoleType.XpCurve DEFAULT_XP_CURVE = new PetRoleType.XpCurve(
        30,
        List.of(3, 7, 12, 17, 23, 27),
        List.of(10, 20, 30),
        20,
        8,
        0.75f
    );

    public PetRoleType.Definition toDefinition() {
        return new PetRoleType.Definition(
            id,
            translationKey,
            baseStatScalars,
            defaultAbilities,
            xpCurve,
            visual,
            statAffinities,
            attributeScaling,
            passiveAuras,
            supportPotionBehavior,
            milestoneAdvancements,
            presentation,
            levelRewards
        );
    }

    public static PetRoleDefinition fromJson(Identifier sourceId, JsonObject json, String sourceDescription) {
        Identifier id = parseId(json, sourceId, sourceDescription);
        String translationKey = RegistryJsonHelper.getString(json, "translation_key", null);

        Map<String, Float> scalars = parseScalars(json, sourceDescription);
        List<Identifier> abilities = parseAbilityList(json, sourceDescription);
        PetRoleType.XpCurve xpCurve = parseXpCurve(json, sourceDescription);
        PetRoleType.Visual visual = parseVisual(json, sourceDescription);
        Map<String, Float> statAffinities = parseStatAffinities(json, sourceDescription);
        PetRoleType.AttributeScaling attributeScaling = parseAttributeScaling(json, sourceDescription);
        List<PetRoleType.PassiveAura> passiveAuras = parsePassiveAuras(json, sourceDescription);
        PetRoleType.SupportPotionBehavior supportPotionBehavior = parseSupportPotion(json, sourceDescription);
        List<PetRoleType.MilestoneAdvancement> milestoneAdvancements = parseMilestoneAdvancements(json, sourceDescription);
        PetRoleType.Presentation presentation = parsePresentation(json, sourceDescription);
        
        // Parse level rewards using LevelRewardParser
        Map<Integer, List<LevelReward>> levelRewards = Map.of();
        if (json.has("level_rewards")) {
            JsonObject levelRewardsJson = RegistryJsonHelper.getObject(json, "level_rewards");
            if (levelRewardsJson != null) {
                levelRewards = LevelRewardParser.parseRewards(levelRewardsJson, sourceDescription);
            }
        }

        if (abilities.isEmpty()) {
            Petsplus.LOGGER.warn("Role {} from {} defines no default abilities; pets will not have loadout entries until datapacks provide them.", id, sourceDescription);
        }

        return new PetRoleDefinition(
            id,
            translationKey,
            scalars,
            abilities,
            xpCurve,
            visual,
            statAffinities,
            attributeScaling,
            passiveAuras,
            supportPotionBehavior,
            milestoneAdvancements,
            presentation,
            levelRewards
        );
    }

    private static Identifier parseId(JsonObject json, Identifier fallback, String source) {
        String idValue = RegistryJsonHelper.getString(json, "id", null);
        if (idValue == null || idValue.isBlank()) {
            return fallback;
        }
        Identifier parsed = Identifier.tryParse(idValue);
        if (parsed == null) {
            Petsplus.LOGGER.error("Role definition at {} has invalid id '{}'", source, idValue);
            return fallback;
        }
        return parsed;
    }

    private static Map<String, Float> parseScalars(JsonObject json, String source) {
        Map<String, Float> scalars = new LinkedHashMap<>();
        JsonObject scalarsObject = RegistryJsonHelper.getObject(json, "base_stat_scalars");
        if (scalarsObject == null) {
            return scalars;
        }

        for (Map.Entry<String, JsonElement> entry : scalarsObject.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                scalars.put(key, value.getAsFloat());
            } else {
                Petsplus.LOGGER.warn("Role definition at {} has non-numeric base stat scalar '{}'", source, key);
            }
        }
        return scalars;
    }

    private static List<Identifier> parseAbilityList(JsonObject json, String source) {
        List<Identifier> abilities = new ArrayList<>();
        JsonArray abilityArray = RegistryJsonHelper.getArray(json, "default_abilities");
        if (abilityArray == null) {
            return abilities;
        }

        for (int i = 0; i < abilityArray.size(); i++) {
            JsonElement element = abilityArray.get(i);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                Petsplus.LOGGER.error("Role definition at {} has non-string ability entry at index {}", source, i);
                continue;
            }
            String raw = element.getAsString();
            Identifier abilityId = Identifier.tryParse(raw);
            if (abilityId == null) {
                abilityId = Identifier.of(Petsplus.MOD_ID, raw.toLowerCase(Locale.ROOT));
                Petsplus.LOGGER.warn("Role definition at {} used non-namespaced ability id '{}' at index {}; assuming petsplus:{}", source, raw, i, abilityId.getPath());
            }
            abilities.add(abilityId);
        }
        return abilities;
    }

    private static Map<String, Float> parseStatAffinities(JsonObject json, String source) {
        Map<String, Float> affinities = new LinkedHashMap<>();
        JsonObject affinityObject = RegistryJsonHelper.getObject(json, "stat_affinities");
        if (affinityObject == null) {
            return affinities;
        }

        for (Map.Entry<String, JsonElement> entry : affinityObject.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                affinities.put(entry.getKey(), value.getAsFloat());
            } else {
                Petsplus.LOGGER.warn("Role definition at {} has non-numeric stat affinity '{}'", source, entry.getKey());
            }
        }

        return affinities;
    }

    private static PetRoleType.AttributeScaling parseAttributeScaling(JsonObject json, String source) {
        JsonObject scalingObject = RegistryJsonHelper.getObject(json, "attribute_scaling");
        if (scalingObject == null) {
            return PetRoleType.AttributeScaling.DEFAULT;
        }

        PetRoleType.AttributeScaling.Builder builder = PetRoleType.AttributeScaling.builder();

        builder.healthBonusPerLevel((float) RegistryJsonHelper.getDouble(scalingObject, "health_bonus_per_level", 0.0));
        builder.healthPostSoftcapBonusPerLevel((float) RegistryJsonHelper.getDouble(scalingObject, "health_post_softcap_bonus_per_level", 0.0));
        builder.healthSoftcapLevel(RegistryJsonHelper.getInt(scalingObject, "health_softcap_level", PetRoleType.AttributeScaling.DEFAULT.healthSoftcapLevel()));
        builder.healthMaxBonus((float) RegistryJsonHelper.getDouble(scalingObject, "health_max_bonus", PetRoleType.AttributeScaling.DEFAULT.healthMaxBonus()));

        builder.speedBonusPerLevel((float) RegistryJsonHelper.getDouble(scalingObject, "speed_bonus_per_level", 0.0));
        builder.speedMaxBonus((float) RegistryJsonHelper.getDouble(scalingObject, "speed_max_bonus", PetRoleType.AttributeScaling.DEFAULT.speedMaxBonus()));

        builder.attackBonusPerLevel((float) RegistryJsonHelper.getDouble(scalingObject, "attack_bonus_per_level", 0.0));
        builder.attackPostSoftcapBonusPerLevel((float) RegistryJsonHelper.getDouble(scalingObject, "attack_post_softcap_bonus_per_level", 0.0));
        builder.attackSoftcapLevel(RegistryJsonHelper.getInt(scalingObject, "attack_softcap_level", PetRoleType.AttributeScaling.DEFAULT.attackSoftcapLevel()));
        builder.attackMaxBonus((float) RegistryJsonHelper.getDouble(scalingObject, "attack_max_bonus", PetRoleType.AttributeScaling.DEFAULT.attackMaxBonus()));

        return builder.build();
    }

    private static PetRoleType.XpCurve parseXpCurve(JsonObject json, String source) {
        JsonObject xpObject = RegistryJsonHelper.getObject(json, "xp_curve");
        if (xpObject == null) {
            return DEFAULT_XP_CURVE;
        }

        int maxLevel = RegistryJsonHelper.getInt(xpObject, "max_level", DEFAULT_XP_CURVE.maxLevel());
        List<Integer> featureLevels = parseIntArray(xpObject, "feature_levels", DEFAULT_XP_CURVE.featureLevels(), source);
        List<Integer> tributeMilestones = parseIntArray(xpObject, "tribute_milestones", DEFAULT_XP_CURVE.tributeMilestones(), source);
        int baseLinear = RegistryJsonHelper.getInt(xpObject, "base_linear_per_level", DEFAULT_XP_CURVE.baseLinearPerLevel());
        int quadratic = RegistryJsonHelper.getInt(xpObject, "quadratic_factor", DEFAULT_XP_CURVE.quadraticFactor());
        float featureBonus = (float) RegistryJsonHelper.getDouble(xpObject, "feature_level_bonus_multiplier", DEFAULT_XP_CURVE.featureLevelBonusMultiplier());

        return new PetRoleType.XpCurve(maxLevel, featureLevels, tributeMilestones, baseLinear, quadratic, featureBonus);
    }

    private static List<Integer> parseIntArray(JsonObject json, String key, List<Integer> fallback, String source) {
        JsonArray array = RegistryJsonHelper.getArray(json, key);
        if (array == null) {
            return fallback;
        }

        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                values.add(element.getAsInt());
            } else {
                Petsplus.LOGGER.warn("Role definition at {} has non-numeric entry in '{}' at index {}", source, key, i);
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private static PetRoleType.Visual parseVisual(JsonObject json, String source) {
        JsonObject visualObject = RegistryJsonHelper.getObject(json, "visual");
        if (visualObject == null) {
            return PetRoleType.Visual.DEFAULT;
        }

        int primary = parseColor(visualObject, "primary_color", PetRoleType.Visual.DEFAULT.primaryColor(), source);
        int secondary = parseColor(visualObject, "secondary_color", PetRoleType.Visual.DEFAULT.secondaryColor(), source);
        String ambient = RegistryJsonHelper.getString(visualObject, "ambient_event", PetRoleType.Visual.DEFAULT.ambientEvent());
        String prefix = RegistryJsonHelper.getString(visualObject, "ability_event_prefix", PetRoleType.Visual.DEFAULT.abilityEventPrefix());
        return new PetRoleType.Visual(primary, secondary, ambient, prefix);
    }

    private static int parseColor(JsonObject json, String key, int fallback, String source) {
        String value = RegistryJsonHelper.getString(json, key, null);
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = "0x" + normalized.substring(1);
        }

        try {
            return Integer.decode(normalized);
        } catch (NumberFormatException e) {
            Petsplus.LOGGER.error("Role definition at {} has invalid color '{}' for key '{}'", source, value, key);
            return fallback;
        }
    }

    private static List<PetRoleType.PassiveAura> parsePassiveAuras(JsonObject json, String source) {
        JsonArray array = RegistryJsonHelper.getArray(json, "passive_auras");
        if (array == null || array.isEmpty()) {
            return List.of();
        }

        List<PetRoleType.PassiveAura> auras = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) {
                Petsplus.LOGGER.warn("Role definition at {} has non-object passive aura entry at index {}", source, i);
                continue;
            }

            JsonObject auraObject = element.getAsJsonObject();
            String auraId = RegistryJsonHelper.getString(auraObject, "id", "aura_" + i);
            int interval = RegistryJsonHelper.getInt(auraObject, "interval", 160);
            double radius = RegistryJsonHelper.getDouble(auraObject, "radius", 6.0);
            int minLevel = RegistryJsonHelper.getInt(auraObject, "min_level", 1);
            boolean requireSitting = RegistryJsonHelper.getBoolean(auraObject, "require_sitting", false);
            String particleEvent = RegistryJsonHelper.getString(auraObject, "particle_event", "");

            List<PetRoleType.AuraEffect> effects = parseAuraEffects(
                RegistryJsonHelper.getArray(auraObject, "effects"),
                source,
                auraId
            );

            PetRoleType.Message message = parseMessage(RegistryJsonHelper.getObject(auraObject, "message"));
            PetRoleType.SoundCue sound = parseSound(RegistryJsonHelper.getObject(auraObject, "sound"), source, "passive aura " + auraId);

            auras.add(new PetRoleType.PassiveAura(
                auraId,
                interval,
                radius,
                minLevel,
                requireSitting,
                effects,
                message,
                sound,
                particleEvent
            ));
        }

        return auras;
    }

    private static List<PetRoleType.AuraEffect> parseAuraEffects(JsonArray array, String source, String auraId) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }

        List<PetRoleType.AuraEffect> effects = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) {
                Petsplus.LOGGER.warn("Role definition at {} has non-object aura effect at {} index {}", source, auraId, i);
                continue;
            }

            JsonObject effectObject = element.getAsJsonObject();
            String effectRaw = RegistryJsonHelper.getString(effectObject, "effect", null);
            if (effectRaw == null || effectRaw.isBlank()) {
                Petsplus.LOGGER.warn("Role definition at {} aura {} missing effect identifier at index {}", source, auraId, i);
                continue;
            }

            Identifier effectId = Identifier.tryParse(effectRaw);
            if (effectId == null) {
                Petsplus.LOGGER.warn("Role definition at {} aura {} has invalid effect identifier '{}' at index {}", source, auraId, effectRaw, i);
                continue;
            }

            String targetRaw = RegistryJsonHelper.getString(effectObject, "target", "owner");
            PetRoleType.AuraTarget target = parseAuraTarget(targetRaw);
            int duration = RegistryJsonHelper.getInt(effectObject, "duration", 120);
            int amplifier = RegistryJsonHelper.getInt(effectObject, "amplifier", 0);
            int minLevel = RegistryJsonHelper.getInt(effectObject, "min_level", 1);

            effects.add(new PetRoleType.AuraEffect(effectId, target, duration, amplifier, minLevel));
        }

        return effects;
    }

    private static PetRoleType.AuraTarget parseAuraTarget(String target) {
        if (target == null) {
            return PetRoleType.AuraTarget.OWNER;
        }

        return switch (target.toLowerCase(Locale.ROOT)) {
            case "pet" -> PetRoleType.AuraTarget.PET;
            case "owner_and_pet" -> PetRoleType.AuraTarget.OWNER_AND_PET;
            case "owner_and_allies" -> PetRoleType.AuraTarget.OWNER_AND_ALLIES;
            case "nearby_players" -> PetRoleType.AuraTarget.NEARBY_PLAYERS;
            case "nearby_allies" -> PetRoleType.AuraTarget.NEARBY_ALLIES;
            default -> PetRoleType.AuraTarget.OWNER;
        };
    }

    private static PetRoleType.SupportPotionBehavior parseSupportPotion(JsonObject json, String source) {
        JsonObject supportObject = RegistryJsonHelper.getObject(json, "support_potion");
        if (supportObject == null) {
            return null;
        }

        String behaviorId = RegistryJsonHelper.getString(supportObject, "id", "stored_potion");
        int interval = RegistryJsonHelper.getInt(supportObject, "interval", 140);
        double radius = RegistryJsonHelper.getDouble(supportObject, "radius", 6.0);
        int minLevel = RegistryJsonHelper.getInt(supportObject, "min_level", 5);
        boolean requireSitting = RegistryJsonHelper.getBoolean(supportObject, "require_sitting", true);
        String fallbackEffectRaw = RegistryJsonHelper.getString(supportObject, "fallback_effect", null);
        Identifier fallbackEffect = null;
        if (fallbackEffectRaw != null && !fallbackEffectRaw.isBlank()) {
            fallbackEffect = Identifier.tryParse(fallbackEffectRaw);
            if (fallbackEffect == null) {
                Petsplus.LOGGER.warn("Role definition at {} has invalid fallback_effect '{}' for support potion {}", source, fallbackEffectRaw, behaviorId);
            }
        }

        int duration = RegistryJsonHelper.getInt(supportObject, "effect_duration", 120);
        boolean applyToPet = RegistryJsonHelper.getBoolean(supportObject, "apply_to_pet", true);
        String particleEvent = RegistryJsonHelper.getString(supportObject, "particle_event", "");

        PetRoleType.Message message = parseMessage(RegistryJsonHelper.getObject(supportObject, "message"));
        PetRoleType.SoundCue sound = parseSound(RegistryJsonHelper.getObject(supportObject, "sound"), source, "support potion " + behaviorId);

        return new PetRoleType.SupportPotionBehavior(
            behaviorId,
            interval,
            radius,
            minLevel,
            requireSitting,
            fallbackEffect,
            duration,
            applyToPet,
            message,
            sound,
            particleEvent
        );
    }

    private static PetRoleType.Presentation parsePresentation(JsonObject json, String source) {
        JsonObject presentationObject = RegistryJsonHelper.getObject(json, "presentation");
        if (presentationObject == null) {
            return PetRoleType.Presentation.DEFAULT;
        }

        PetRoleType.Petting petting = parsePettingPresentation(
            RegistryJsonHelper.getObject(presentationObject, "petting")
        );
        List<PetRoleType.Message> epithets = parseMemorialEpithets(
            RegistryJsonHelper.getArray(presentationObject, "memorial_epithets"),
            source
        );
        PetRoleType.Message adminSummary = parseMessage(RegistryJsonHelper.getObject(presentationObject, "admin_summary"));

        return new PetRoleType.Presentation(petting, epithets, adminSummary);
    }

    private static PetRoleType.Petting parsePettingPresentation(JsonObject pettingObject) {
        if (pettingObject == null) {
            return PetRoleType.Petting.EMPTY;
        }

        PetRoleType.Message message = parseMessage(RegistryJsonHelper.getObject(pettingObject, "message"));
        String feedbackEvent = RegistryJsonHelper.getString(pettingObject, "feedback_event", "");
        return new PetRoleType.Petting(message, feedbackEvent);
    }

    private static List<PetRoleType.Message> parseMemorialEpithets(JsonArray array, String source) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }

        List<PetRoleType.Message> epithets = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString();
                if (value.isBlank()) {
                    Petsplus.LOGGER.warn("Role definition at {} has empty memorial epithet at index {}", source, i);
                    continue;
                }
                epithets.add(new PetRoleType.Message(null, value));
            } else if (element.isJsonObject()) {
                PetRoleType.Message message = parseMessage(element.getAsJsonObject());
                if (message.isPresent()) {
                    epithets.add(message);
                } else {
                    Petsplus.LOGGER.warn("Role definition at {} has empty memorial epithet message at index {}", source, i);
                }
            } else {
                Petsplus.LOGGER.warn("Role definition at {} has invalid memorial epithet entry at index {}", source, i);
            }
        }

        return epithets;
    }

    private static List<PetRoleType.MilestoneAdvancement> parseMilestoneAdvancements(JsonObject json, String source) {
        JsonArray array = RegistryJsonHelper.getArray(json, "milestone_advancements");
        if (array == null || array.isEmpty()) {
            return List.of();
        }

        List<PetRoleType.MilestoneAdvancement> milestones = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) {
                Petsplus.LOGGER.warn("Role definition at {} has non-object milestone advancement at index {}", source, i);
                continue;
            }

            JsonObject milestoneObject = element.getAsJsonObject();
            int level = RegistryJsonHelper.getInt(milestoneObject, "level", 0);
            String advancementRaw = RegistryJsonHelper.getString(milestoneObject, "advancement", null);
            if (advancementRaw == null || advancementRaw.isBlank()) {
                Petsplus.LOGGER.warn("Role definition at {} milestone entry {} missing advancement id", source, i);
                continue;
            }

            Identifier advancementId = Identifier.tryParse(advancementRaw);
            if (advancementId == null) {
                Petsplus.LOGGER.warn("Role definition at {} milestone entry {} has invalid advancement id '{}'", source, i, advancementRaw);
                continue;
            }

            PetRoleType.Message message = parseMessage(RegistryJsonHelper.getObject(milestoneObject, "message"));
            PetRoleType.SoundCue sound = parseSound(RegistryJsonHelper.getObject(milestoneObject, "sound"), source, "milestone " + advancementId);

            milestones.add(new PetRoleType.MilestoneAdvancement(level, advancementId, message, sound));
        }

        return milestones;
    }

    private static PetRoleType.Message parseMessage(JsonObject messageObject) {
        if (messageObject == null) {
            return PetRoleType.Message.EMPTY;
        }

        String translationKey = RegistryJsonHelper.getString(messageObject, "translation_key", null);
        String fallback = RegistryJsonHelper.getString(messageObject, "fallback", null);
        if ((translationKey == null || translationKey.isBlank()) && (fallback == null || fallback.isBlank())) {
            return PetRoleType.Message.EMPTY;
        }

        return new PetRoleType.Message(translationKey, fallback);
    }

    private static PetRoleType.SoundCue parseSound(JsonObject soundObject, String source, String context) {
        if (soundObject == null) {
            return PetRoleType.SoundCue.NONE;
        }

        String soundRaw = RegistryJsonHelper.getString(soundObject, "id", null);
        if (soundRaw == null || soundRaw.isBlank()) {
            return PetRoleType.SoundCue.NONE;
        }

        Identifier soundId = Identifier.tryParse(soundRaw);
        if (soundId == null) {
            Petsplus.LOGGER.warn("Role definition at {} has invalid sound identifier '{}' for {}", source, soundRaw, context);
            return PetRoleType.SoundCue.NONE;
        }

        float volume = (float) RegistryJsonHelper.getDouble(soundObject, "volume", 1.0);
        float pitch = (float) RegistryJsonHelper.getDouble(soundObject, "pitch", 1.0);
        return new PetRoleType.SoundCue(soundId, volume, pitch);
    }
}
