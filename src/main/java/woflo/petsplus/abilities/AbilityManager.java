package woflo.petsplus.abilities;

import com.google.gson.JsonParser;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Ability;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.state.PetComponent;

import java.util.*;

/**
 * Manages all abilities and their activation for pets.
 */
public class AbilityManager {
    private static final Map<PetRole, List<Ability>> ROLE_ABILITIES = new EnumMap<>(PetRole.class);
    private static final Map<Identifier, Ability> ALL_ABILITIES = new HashMap<>();
    
    /**
     * Initialize the ability system with default abilities.
     */
    public static void initialize() {
        loadDefaultAbilities();
        Petsplus.LOGGER.info("Loaded {} abilities across {} roles", ALL_ABILITIES.size(), ROLE_ABILITIES.size());
    }
    
    private static void loadDefaultAbilities() {
        // Guardian abilities (Level 3 feature unlock)
        registerAbility(PetRole.GUARDIAN, createShieldBashRider());
        
        // Striker abilities (Level 7 feature unlock)
        registerAbility(PetRole.STRIKER, createFinisherMark());
        
        // Scout abilities (Level 3 feature unlock)
        registerAbility(PetRole.SCOUT, createLootWisp());
        
        // Skyrider abilities (Level 7 feature unlock)
        registerAbility(PetRole.SKYRIDER, createWindlashRider());
        
        // Cursed One abilities (Level 12 feature unlock)
        registerAbility(PetRole.CURSED_ONE, createDoomEcho());
        
        // Eclipsed abilities (Level 17, 23, 27 feature unlocks)
        registerAbility(PetRole.ECLIPSED, createVoidbrand());      // Level 17
        registerAbility(PetRole.ECLIPSED, createPhasePartner());   // Level 23
        registerAbility(PetRole.ECLIPSED, createPerchPing());      // Level 27

    // Support abilities (Level 5, 15 feature unlocks)
    registerAbility(PetRole.SUPPORT, createSupportPerchPotionEfficiency()); // Level 5
    registerAbility(PetRole.SUPPORT, createSupportMountedConeAura());       // Level 15
    }
    
    private static Ability createShieldBashRider() {
        String json = """
            {
              "id": "petsplus:shield_bash_rider",
              "required_level": 3,
              "trigger": {
                "event": "after_pet_redirect",
                "internal_cd_ticks": 300
              },
              "effects": [
                {
                  "type": "owner_next_attack_bonus",
                  "bonus_damage_pct": 0.10,
                  "on_hit_effect": {
                    "type": "effect",
                    "target": "victim",
                    "id": "minecraft:weakness",
                    "duration": 40,
                    "amplifier": 0
                  },
                  "expire_ticks": 100
                }
              ]
            }
            """;
        return AbilityFactory.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
    
    private static Ability createFinisherMark() {
        String json = """
            {
              "id": "petsplus:finisher_mark",
              "required_level": 7,
              "trigger": {
                "event": "owner_dealt_damage",
                "target_hp_pct_below": 0.40,
                "cooldown_ticks": 200
              },
              "effects": [
                {
                  "type": "tag_target",
                  "key": "petsplus:finisher",
                  "duration_ticks": 200
                },
                {
                  "type": "owner_next_attack_bonus",
                  "vs_tag": "petsplus:finisher",
                  "bonus_damage_pct": 1.5,
                  "on_hit_effect": {
                    "type": "effect",
                    "target": "victim",
                    "id": "minecraft:slowness",
                    "duration": 40,
                    "amplifier": 0
                  },
                  "expire_ticks": 100
                },
                {
                  "type": "buff",
                  "target": "mount",
                  "id": "minecraft:speed",
                  "duration": 40,
                  "amplifier": 0,
                  "only_if_mounted": true
                }
              ]
            }
            """;
        return AbilityFactory.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
    
    private static Ability createLootWisp() {
        String json = """
            {
              "id": "petsplus:loot_wisp",
              "required_level": 3,
              "trigger": {
                "event": "on_combat_end",
                "cooldown_ticks": 200
              },
              "effects": [
                {
                  "type": "magnetize_drops_and_xp",
                  "radius": 12,
                  "duration_ticks": 100
                }
              ]
            }
            """;
        return AbilityFactory.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
    
    private static Ability createWindlashRider() {
        String json = """
            {
              "id": "petsplus:windlash_rider",
              "required_level": 7,
              "trigger": {
                "event": "owner_begin_fall",
                "min_fall": 3,
                "cooldown_ticks": 120
              },
              "effects": [
                {
                  "type": "buff",
                  "target": "owner",
                  "id": "minecraft:jump_boost",
                  "duration": 40,
                  "amplifier": 0
                },
                {
                  "type": "owner_next_attack_bonus",
                  "on_hit_effect": {
                    "type": "knockup",
                    "target": "victim",
                    "strength": 0.35
                  },
                  "expire_ticks": 100
                }
              ]
            }
            """;
        return AbilityFactory.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
    
    private static Ability createDoomEcho() {
        String json = """
            {
              "id": "petsplus:doom_echo",
              "required_level": 12,
              "trigger": {
                "event": "owner_low_health",
                "owner_hp_pct_below": 0.35,
                "internal_cd_ticks": 400
              },
              "effects": [
                {
                  "type": "area_effect",
                  "radius": 5,
                  "effects": [
                    {
                      "id": "minecraft:weakness",
                      "duration": 60,
                      "amplifier": 0
                    }
                  ]
                },
                {
                  "type": "owner_next_attack_bonus",
                  "bonus_damage_pct": 0.00,
                  "on_hit_effect": {
                    "type": "heal_owner_flat_pct",
                    "value": 0.15
                  },
                  "expire_ticks": 100
                },
                {
                  "type": "buff",
                  "target": "owner",
                  "id": "minecraft:nausea",
                  "duration": 20,
                  "amplifier": 0
                }
              ]
            }
            """;
        return AbilityFactory.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
    
    private static Ability createVoidbrand() {
        String json = """
            {
              "id": "petsplus:voidbrand",
              "required_level": 17,
              "trigger": {
                "event": "aggro_acquired",
                "cooldown_ticks": 300
              },
              "effects": [
                {
                  "type": "tag_target",
                  "key": "petsplus:voidbrand",
                  "duration_ticks": 150
                },
                {
                  "type": "owner_next_attack_bonus",
                  "vs_tag": "petsplus:voidbrand",
                  "bonus_damage_pct": 1.25,
                  "on_hit_effect": {
                    "type": "effect",
                    "target": "victim",
                    "id": "minecraft:slowness",
                    "duration": 40,
                    "amplifier": 0
                  },
                  "expire_ticks": 100
                }
              ]
            }
            """;
        return AbilityFactory.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
    
    private static Ability createPhasePartner() {
        String json = """
            {
              "id": "petsplus:phase_partner",
              "required_level": 23,
              "trigger": {
                "event": "interval_while_active",
                "ticks": 200,
                "require_perched": true
              },
              "effects": [
                {
                  "type": "buff",
                  "target": "owner",
                  "id": "minecraft:speed",
                  "duration": 40,
                  "amplifier": 0,
                  "only_if_perched": true
                },
                {
                  "type": "owner_next_attack_bonus",
                  "bonus_damage_pct": 2.0,
                  "on_hit_effect": {
                    "type": "effect",
                    "target": "victim",
                    "id": "minecraft:slowness",
                    "duration": 40,
                    "amplifier": 0
                  },
                  "expire_ticks": 80
                }
              ]
            }
            """;
        return AbilityFactory.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
    
    private static Ability createPerchPing() {
        String json = """
            {
              "id": "petsplus:perch_ping",
              "required_level": 27,
              "trigger": {
                "event": "interval_while_active",
                "ticks": 40,
                "require_perched": true,
                "require_in_combat": true
              },
              "effects": [
                {
                  "type": "retarget_nearest_hostile",
                  "radius": 8,
                  "store_as": "pp_target"
                },
                {
                  "type": "effect",
                  "target": "pp_target",
                  "id": "minecraft:darkness",
                  "duration": 10,
                  "amplifier": 0,
                  "boss_safe": true
                },
                {
                  "type": "tag_target",
                  "target": "pp_target",
                  "key": "petsplus:voidbrand",
                  "duration_ticks": 60
                }
              ]
            }
            """;
        return AbilityFactory.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    private static Ability createSupportPerchPotionEfficiency() {
        String json = """
            {
              "id": "petsplus:perch_potion_efficiency",
              "required_level": 5,
              "trigger": {
                "event": "interval_while_active",
                "ticks": 40,
                "require_perched": true
              },
              "effects": [
                {
                  "type": "perch_potion_sip_reduction",
                  "discount_percent": "${support.perchSipDiscount}"
                }
              ]
            }
            """;
        return AbilityFactory.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    private static Ability createSupportMountedConeAura() {
        String json = """
            {
              "id": "petsplus:mounted_cone_aura",
              "required_level": 15,
              "trigger": {
                "event": "interval_while_active",
                "ticks": 40
              },
              "effects": [
                {
                  "type": "mounted_cone_aura",
                  "base_radius": 8.0,
                  "extra_radius": "${support.mountedConeExtraRadius}",
                  "effect_duration": 60,
                  "effect_amplifier": 0
                }
              ]
            }
            """;
        return AbilityFactory.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
    
    private static void registerAbility(PetRole role, Ability ability) {
        if (ability == null) {
            Petsplus.LOGGER.warn("Failed to register null ability for role {}", role);
            return;
        }
        
        ROLE_ABILITIES.computeIfAbsent(role, k -> new ArrayList<>()).add(ability);
        ALL_ABILITIES.put(ability.getId(), ability);
        Petsplus.LOGGER.debug("Registered ability {} for role {}", ability.getId(), role);
    }
    
    /**
     * Trigger abilities for a pet based on the given context.
     */
    public static void triggerAbilities(MobEntity pet, TriggerContext context) {
        if (pet == null) return;

        PetComponent component = PetComponent.get(pet);
        if (component == null) return;
        
        List<Ability> abilities = ROLE_ABILITIES.get(component.getRole());
        if (abilities == null) return;
        
    // Create a pet-aware context that preserves event data
    TriggerContext petContext = new TriggerContext(
      context.getWorld(),
      pet,
      context.getOwner(),
      context.getEventType()
    );
    for (var e : context.getEventData().entrySet()) {
      petContext.withData(e.getKey(), e.getValue());
    }

        for (Ability ability : abilities) {
            // Check if ability is on cooldown
            if (component.isOnCooldown(ability.getId().toString())) {
                continue;
            }
            
            // Try to activate ability
      if (ability.tryActivate(petContext)) {
                // Set cooldown if the trigger has one
        int cooldown = ability.getTrigger().getInternalCooldownTicks();
                if (cooldown > 0) {
                    component.setCooldown(ability.getId().toString(), cooldown);
                }
            }
        }
    }
    
    /**
     * Get all abilities for a specific role.
     */
    public static List<Ability> getAbilitiesForRole(PetRole role) {
        return ROLE_ABILITIES.getOrDefault(role, Collections.emptyList());
    }
    
    /**
     * Get a specific ability by ID.
     */
    public static Ability getAbility(Identifier id) {
        return ALL_ABILITIES.get(id);
    }
    
    /**
     * Trigger an ability for testing purposes.
     */
    public static boolean triggerAbilityForTest(String abilityId, net.minecraft.server.network.ServerPlayerEntity player) {
        try {
            Identifier id = Identifier.of("petsplus", abilityId);
            Ability ability = ALL_ABILITIES.get(id);
            
            if (ability != null) {
                Petsplus.LOGGER.info("Test triggered ability: {}", abilityId);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            Petsplus.LOGGER.error("Error triggering ability for test", e);
            return false;
        }
    }
}