package woflo.petsplus.abilities;

import com.google.gson.*;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Ability;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.Trigger;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.util.TriggerConditions;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.effects.*;
// No wildcard util import; only using TriggerConditions directly

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating abilities from JSON configuration.
 */
public class AbilityFactory {
    
    /**
     * Create an ability from JSON configuration.
     */
    public static Ability fromJson(JsonObject json) {
        try {
            String idString = json.get("id").getAsString();
            Identifier id = Identifier.of(idString);
            
            // Parse trigger
            JsonObject triggerJson = json.getAsJsonObject("trigger");
            Trigger trigger = createTrigger(triggerJson);
            
            // Parse effects
            JsonArray effectsJson = json.getAsJsonArray("effects");
            List<Effect> effects = new ArrayList<>();
            for (JsonElement effectElement : effectsJson) {
                JsonObject effectJson = effectElement.getAsJsonObject();
                Effect effect = createEffect(effectJson);
                if (effect != null) {
                    effects.add(effect);
                }
            }
            
            return new Ability(id, trigger, effects, json);
        } catch (Exception e) {
            Petsplus.LOGGER.error("Failed to parse ability from JSON: {}", json, e);
            return null;
        }
    }
    
    private static Trigger createTrigger(JsonObject triggerJson) {
        String eventType = triggerJson.get("event").getAsString();

        switch (eventType) {
            case "after_pet_redirect":
                return new SimpleEventTrigger("after_pet_redirect", getIntOrDefault(triggerJson, "internal_cd_ticks", 0));

            case "owner_dealt_damage": {
                final double hpBelow = getDoubleOrDefault(triggerJson, "target_hp_pct_below", -1);
                final int cd = getIntOrDefault(triggerJson, "cooldown_ticks", getIntOrDefault(triggerJson, "internal_cd_ticks", 0));
                return new Trigger() {
                    @Override
                    public Identifier getId() { return Identifier.of("petsplus", "owner_dealt_damage"); }

                    @Override
                    public int getInternalCooldownTicks() { return cd; }

                    @Override
                    public boolean shouldActivate(TriggerContext context) {
                        if (!"owner_dealt_damage".equals(context.getEventType())) return false;
                        if (hpBelow >= 0) {
                            return context.getVictimHpPercent() <= hpBelow;
                        }
                        return true;
                    }
                };
            }

            case "owner_begin_fall": {
                final double minFall = getDoubleOrDefault(triggerJson, "min_fall", 0);
                final int cd = getIntOrDefault(triggerJson, "cooldown_ticks", getIntOrDefault(triggerJson, "internal_cd_ticks", 0));
                return new Trigger() {
                    @Override
                    public Identifier getId() { return Identifier.of("petsplus", "owner_begin_fall"); }

                    @Override
                    public int getInternalCooldownTicks() { return cd; }

                    @Override
                    public boolean shouldActivate(TriggerContext context) {
                        if (!"owner_begin_fall".equals(context.getEventType())) return false;
                        return context.getFallDistance() >= minFall;
                    }
                };
            }

            case "interval_while_active": {
                final int every = getIntOrDefault(triggerJson, "ticks", 20);
                final boolean requirePerched = getBooleanOrDefault(triggerJson, "require_perched", false);
                final boolean requireMounted = getBooleanOrDefault(triggerJson, "require_mounted_owner", false);
                final boolean requireInCombat = getBooleanOrDefault(triggerJson, "require_in_combat", false);
                return new Trigger() {
                    @Override
                    public Identifier getId() { return Identifier.of("petsplus", "interval_while_active"); }

                    @Override
                    public int getInternalCooldownTicks() { return Math.max(1, every); }

                    @Override
                    public boolean shouldActivate(TriggerContext context) {
                        // We drive this from StateManager as "interval_tick" once per second; use cooldown spacing here
                        if (!"interval_tick".equals(context.getEventType())) return false;
                        if (requirePerched && !TriggerConditions.isPerched(context.getOwner())) return false;
                        if (requireMounted && !TriggerConditions.isMounted(context.getOwner())) return false;
                        if (requireInCombat && !TriggerConditions.isInCombat(context.getOwner())) return false;
                        return true;
                    }
                };
            }

            case "aggro_acquired": {
                final int cd = getIntOrDefault(triggerJson, "cooldown_ticks", getIntOrDefault(triggerJson, "internal_cd_ticks", 0));
                return new SimpleEventTrigger("aggro_acquired", cd);
            }

            case "on_combat_end": {
                final int cd = getIntOrDefault(triggerJson, "cooldown_ticks", getIntOrDefault(triggerJson, "internal_cd_ticks", 0));
                return new SimpleEventTrigger("on_combat_end", cd);
            }

            case "owner_low_health":
            case "on_owner_low_health": {
                final int cd = getIntOrDefault(triggerJson, "cooldown_ticks", getIntOrDefault(triggerJson, "internal_cd_ticks", 0));
                return new Trigger() {
                    @Override
                    public Identifier getId() { return Identifier.of("petsplus", "owner_low_health"); }

                    @Override
                    public int getInternalCooldownTicks() { return cd; }

                    @Override
                    public boolean shouldActivate(TriggerContext context) {
                        if (!"on_owner_low_health".equals(context.getEventType()) && !"owner_low_health".equals(context.getEventType())) return false;
                        // CombatEventHandler computes health threshold before death; fire as-is
                        return true; // threshold is checked upstream; optional: could read an owner hp pct if added later
                    }
                };
            }

            default:
                Petsplus.LOGGER.warn("Unknown trigger type: {}", eventType);
                return new SimpleEventTrigger(eventType, 0);
        }
    }

    private static class SimpleEventTrigger implements Trigger {
        private final String eventType;
        private final int cooldown;

        SimpleEventTrigger(String eventType, int cooldown) {
            this.eventType = eventType;
            this.cooldown = cooldown;
        }

        @Override
        public Identifier getId() {
            return Identifier.of("petsplus", eventType);
        }

        @Override
        public int getInternalCooldownTicks() {
            return cooldown;
        }

        @Override
        public boolean shouldActivate(TriggerContext context) {
            return eventType.equals(context.getEventType());
        }
    }

    private static Effect createEffect(JsonObject effectJson) {
        String type = effectJson.get("type").getAsString();
        
        switch (type) {
            case "owner_next_attack_bonus":
                double bonusPct = getDoubleOrDefault(effectJson, "bonus_damage_pct", 0.0);
                String vsTag = getStringOrDefault(effectJson, "vs_tag", null);
                int expireTicks = getIntOrDefault(effectJson, "expire_ticks", 100);
                
                Effect onHitEffect = null;
                if (effectJson.has("on_hit_effect")) {
                    JsonObject onHitJson = effectJson.getAsJsonObject("on_hit_effect");
                    onHitEffect = createEffect(onHitJson);
                }
                
                return new OwnerNextAttackBonusEffect(bonusPct, vsTag, onHitEffect, expireTicks);
                
            case "tag_target":
                String key = getStringOrDefault(effectJson, "key", "");
                int duration = getIntOrDefault(effectJson, "duration_ticks", 80);
                return new TagTargetEffect(key, duration);
                
            case "retarget_nearest_hostile":
                double radius = getDoubleOrDefault(effectJson, "radius", 8.0);
                String storeAs = getStringOrDefault(effectJson, "store_as", "target");
                return new RetargetNearestHostileEffect(radius, storeAs);
                
            case "buff":
                BuffEffect.Target target = parseBuffTarget(getStringOrDefault(effectJson, "target", "owner"));
                StatusEffectInstance buffEffect = parseStatusEffect(effectJson);
                boolean onlyIfMounted = getBooleanOrDefault(effectJson, "only_if_mounted", false);
                boolean onlyIfPerched = getBooleanOrDefault(effectJson, "only_if_perched", false);
                return new BuffEffect(target, buffEffect, onlyIfMounted, onlyIfPerched);
                
            case "projectile_dr_for_owner":
                double percent = getDoubleOrDefault(effectJson, "percent", 0.10);
                int drDuration = getIntOrDefault(effectJson, "duration_ticks", 40);
                return new ProjectileDrForOwnerEffect(percent, drDuration);
                
            case "perch_potion_sip_reduction":
                return new PerchPotionSipReductionEffect(effectJson);
                
            case "mounted_cone_aura":
                return new MountedConeAuraEffect(effectJson);
                
            case "magnetize_drops_and_xp":
                return new MagnetizeDropsAndXpEffect(effectJson);
                
            case "area_effect":
                return new AreaEffectEffect(effectJson);

            case "effect":
                // Basic status effect application
                BuffEffect.Target effectTarget = parseBuffTarget(getStringOrDefault(effectJson, "target", "owner"));
                StatusEffectInstance statusEffect = parseStatusEffect(effectJson);
                return new BuffEffect(effectTarget, statusEffect, false, false);
                
            case "heal_owner_flat_pct":
                double healValue = getDoubleOrDefault(effectJson, "value", 0.15);
                return new HealOwnerFlatPctEffect(healValue);
                
            case "knockup":
                double strength = getDoubleOrDefault(effectJson, "strength", 0.35);
                String knockupTarget = getStringOrDefault(effectJson, "target", "victim");
                return new KnockupEffect(strength, knockupTarget);

            default:
                Petsplus.LOGGER.warn("Unknown effect type: {}", type);
                return null;
        }
    }
    
    private static StatusEffectInstance parseStatusEffect(JsonObject effectJson) {
        try {
            // Check if the id field exists and is not null
            JsonElement idElement = effectJson.get("id");
            if (idElement == null || idElement.isJsonNull()) {
                Petsplus.LOGGER.warn("Status effect missing 'id' field in JSON: {}", effectJson);
                return null;
            }
            
            String effectId = idElement.getAsString();
            int duration = getIntOrDefault(effectJson, "duration", 60);
            int amplifier = getIntOrDefault(effectJson, "amplifier", 0);
            
            StatusEffect statusEffect = Registries.STATUS_EFFECT.get(Identifier.of(effectId));
            if (statusEffect != null) {
                return new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(statusEffect), duration, amplifier);
            } else {
                Petsplus.LOGGER.warn("Unknown status effect: {}", effectId);
            }
        } catch (Exception e) {
            Petsplus.LOGGER.error("Failed to parse status effect from JSON: {}", effectJson, e);
        }
        return null;
    }
    
    private static BuffEffect.Target parseBuffTarget(String targetString) {
        switch (targetString.toLowerCase()) {
            case "pet": return BuffEffect.Target.PET;
            case "victim": return BuffEffect.Target.VICTIM;
            case "mount": return BuffEffect.Target.MOUNT;
            default: return BuffEffect.Target.OWNER;
        }
    }
    
    // Utility methods for JSON parsing with config variable substitution
    private static int getIntOrDefault(JsonObject json, String key, int defaultValue) {
        if (!json.has(key)) return defaultValue;
        
        JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            // Handle config variable substitution like ${striker.finisherMarkDurationTicks}
            String value = element.getAsString();
            return parseConfigVariable(value, defaultValue);
        }
        
        return defaultValue;
    }
    
    private static double getDoubleOrDefault(JsonObject json, String key, double defaultValue) {
        if (!json.has(key)) return defaultValue;
        
        JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsDouble();
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            return parseConfigVariable(value, defaultValue);
        }
        
        return defaultValue;
    }
    
    private static boolean getBooleanOrDefault(JsonObject json, String key, boolean defaultValue) {
        if (!json.has(key)) return defaultValue;
        
        JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        
        return defaultValue;
    }
    
    private static String getStringOrDefault(JsonObject json, String key, String defaultValue) {
        if (!json.has(key)) return defaultValue;
        
        JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        
        return defaultValue;
    }
    
    private static int parseConfigVariable(String value, int defaultValue) {
        if (value.startsWith("${") && value.endsWith("}")) {
            String configPath = value.substring(2, value.length() - 1);
            String[] parts = configPath.split("\\.");
            if (parts.length == 2) {
                return PetsPlusConfig.getInstance().getInt(parts[0], parts[1], defaultValue);
            }
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static double parseConfigVariable(String value, double defaultValue) {
        if (value.startsWith("${") && value.endsWith("}")) {
            String configPath = value.substring(2, value.length() - 1);
            String[] parts = configPath.split("\\.");
            if (parts.length == 2) {
                return PetsPlusConfig.getInstance().getDouble(parts[0], parts[1], defaultValue);
            }
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}