package woflo.petsplus.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import woflo.petsplus.Petsplus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Simple data generator for PetsPlus abilities and roles.
 * Generates JSON files for abilities based on the design specification.
 */
public class SimpleDataGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_PATH = FabricLoader.getInstance().getGameDir()
        .resolve("generated_data/petsplus");
    
    /**
     * Generate all data files for PetsPlus.
     */
    public static void generateAll() {
        try {
            Files.createDirectories(DATA_PATH.resolve("abilities"));
            Files.createDirectories(DATA_PATH.resolve("roles"));
            Files.createDirectories(DATA_PATH.resolve("tags/entity_types"));
            
            generateAbilities();
            generateRoles();
            generateTags();
            
            Petsplus.LOGGER.info("Generated PetsPlus data files in {}", DATA_PATH);
        } catch (IOException e) {
            Petsplus.LOGGER.error("Failed to generate data files", e);
        }
    }
    
    private static void generateAbilities() throws IOException {
        // Guardian abilities
        writeFile("abilities/shield_bash_rider.json", createShieldBashRider());

        // Striker abilities
        writeFile("abilities/finisher_mark.json", createFinisherMark());

        // Support abilities
        writeFile("abilities/perch_potion_efficiency.json", createPerchPotionEfficiency());
        writeFile("abilities/mounted_cone_aura.json", createMountedConeAura());

        // Scout abilities
        writeFile("abilities/loot_wisp.json", createLootWisp());
        writeFile("abilities/scout_backpack.json", createScoutBackpack());

        // Skyrider abilities
        writeFile("abilities/windlash_rider.json", createWindlashRider());

        // Cursed One abilities
        writeFile("abilities/doom_echo.json", createDoomEcho());

        // Eclipsed abilities
        writeFile("abilities/voidbrand.json", createVoidbrand());
        writeFile("abilities/void_storage.json", createVoidStorage());
        writeFile("abilities/phase_partner.json", createPhasePartner());
        writeFile("abilities/perch_ping.json", createPerchPing());
    }
    
    private static void generateRoles() throws IOException {
        writeFile("roles/guardian.json", createGuardianRole());
        writeFile("roles/striker.json", createStrikerRole());
        writeFile("roles/support.json", createSupportRole());
        writeFile("roles/scout.json", createScoutRole());
        writeFile("roles/skyrider.json", createSkyriderRole());
        writeFile("roles/enchantment_bound.json", createEnchantmentBoundRole());
        writeFile("roles/cursed_one.json", createCursedOneRole());
        writeFile("roles/eepy_eeper.json", createEepyEeperRole());
        writeFile("roles/eclipsed.json", createEclipsedRole());
    }
    
    private static void generateTags() throws IOException {
        writeFile("tags/entity_types/cc_resistant.json", createCCResistantTag());
        writeFile("tags/entity_types/boss_entities.json", createBossEntitiesTag());
        writeFile("tags/entity_types/tribute_immune.json", createTributeImmuneTag());
    }
    
    private static void writeFile(String path, JsonObject data) throws IOException {
        Path filePath = DATA_PATH.resolve(path);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, GSON.toJson(data), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
    
    // Ability creators
    private static JsonObject createShieldBashRider() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:shield_bash_rider");
        
        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "after_pet_redirect");
        trigger.addProperty("internal_cd_ticks", 120);
        ability.add("trigger", trigger);
        
        JsonArray effects = new JsonArray();
        JsonObject attackBonus = new JsonObject();
        attackBonus.addProperty("type", "owner_next_attack_bonus");
        attackBonus.addProperty("bonus_damage_pct", 0.10);
        attackBonus.addProperty("expire_ticks", 100);
        
        JsonObject onHitEffect = new JsonObject();
        onHitEffect.addProperty("type", "effect");
        onHitEffect.addProperty("target", "victim");
        onHitEffect.addProperty("id", "minecraft:weakness");
        onHitEffect.addProperty("duration", 40);
        onHitEffect.addProperty("amplifier", 0);
        attackBonus.add("on_hit_effect", onHitEffect);
        
        effects.add(attackBonus);
        ability.add("effects", effects);
        
        return ability;
    }
    
    private static JsonObject createFinisherMark() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:finisher_mark");
        
        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_dealt_damage");
        trigger.addProperty("target_hp_pct_below", 0.40);
        trigger.addProperty("cooldown_ticks", 200);
        ability.add("trigger", trigger);
        
        JsonArray effects = new JsonArray();
        
        // Tag target
        JsonObject tagEffect = new JsonObject();
        tagEffect.addProperty("type", "tag_target");
        tagEffect.addProperty("key", "petsplus:finisher");
        tagEffect.addProperty("duration_ticks", "${striker.finisherMarkDurationTicks}");
        effects.add(tagEffect);
        
        // Owner attack bonus
        JsonObject attackBonus = new JsonObject();
        attackBonus.addProperty("type", "owner_next_attack_bonus");
        attackBonus.addProperty("vs_tag", "petsplus:finisher");
        attackBonus.addProperty("bonus_damage_pct", "${striker.finisherMarkBonusPct}");
        attackBonus.addProperty("expire_ticks", 100);
        
        JsonObject onHitEffect = new JsonObject();
        onHitEffect.addProperty("type", "effect");
        onHitEffect.addProperty("target", "victim");
        onHitEffect.addProperty("id", "minecraft:slowness");
        onHitEffect.addProperty("duration", 40);
        onHitEffect.addProperty("amplifier", 0);
        attackBonus.add("on_hit_effect", onHitEffect);
        effects.add(attackBonus);
        
        // Mount speed buff
        JsonObject mountBuff = new JsonObject();
        mountBuff.addProperty("type", "buff");
        mountBuff.addProperty("target", "mount");
        mountBuff.addProperty("id", "minecraft:speed");
        mountBuff.addProperty("duration", 40);
        mountBuff.addProperty("amplifier", 0);
        mountBuff.addProperty("only_if_mounted", true);
        effects.add(mountBuff);
        
        ability.add("effects", effects);
        return ability;
    }
    
    private static JsonObject createLootWisp() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:loot_wisp");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "on_combat_end");
        trigger.addProperty("cooldown_ticks", 200);
        ability.add("trigger", trigger);
        
        JsonArray effects = new JsonArray();
        JsonObject magnetize = new JsonObject();
        magnetize.addProperty("type", "magnetize_drops_and_xp");
        magnetize.addProperty("radius", 12);
        magnetize.addProperty("duration_ticks", "${scout.lootWispDurationTicks}");
        effects.add(magnetize);
        
        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createScoutBackpack() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:scout_backpack");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_signal_proximity_channel");
        trigger.addProperty("cooldown_ticks", 40);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject openBackpack = new JsonObject();
        openBackpack.addProperty("type", "open_pet_backpack");
        effects.add(openBackpack);

        ability.add("effects", effects);
        ability.addProperty("required_level", 5);
        return ability;
    }

    private static JsonObject createWindlashRider() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:windlash_rider");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_begin_fall");
        trigger.addProperty("min_fall", 3);
        trigger.addProperty("cooldown_ticks", 120);
        ability.add("trigger", trigger);
        
        JsonArray effects = new JsonArray();
        
        // Jump boost
        JsonObject jumpBuff = new JsonObject();
        jumpBuff.addProperty("type", "buff");
        jumpBuff.addProperty("target", "owner");
        jumpBuff.addProperty("id", "minecraft:jump_boost");
        jumpBuff.addProperty("duration", 40);
        jumpBuff.addProperty("amplifier", 0);
        effects.add(jumpBuff);
        
        // Knockup on next hit
        JsonObject attackBonus = new JsonObject();
        attackBonus.addProperty("type", "owner_next_attack_bonus");
        attackBonus.addProperty("expire_ticks", 100);
        
        JsonObject knockupEffect = new JsonObject();
        knockupEffect.addProperty("type", "knockup");
        knockupEffect.addProperty("target", "victim");
        knockupEffect.addProperty("strength", 0.35);
        attackBonus.add("on_hit_effect", knockupEffect);
        effects.add(attackBonus);
        
        ability.add("effects", effects);
        return ability;
    }
    
    private static JsonObject createDoomEcho() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:doom_echo");
        
        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "on_owner_low_health");
        trigger.addProperty("owner_hp_pct_below", 0.35);
        trigger.addProperty("internal_cd_ticks", 400);
        ability.add("trigger", trigger);
        
        JsonArray effects = new JsonArray();
        
        // Area weakness
        JsonObject areaEffect = new JsonObject();
        areaEffect.addProperty("type", "area_effect");
        areaEffect.addProperty("radius", 5);
        
        JsonArray areaEffects = new JsonArray();
        JsonObject weakness = new JsonObject();
        weakness.addProperty("id", "minecraft:weakness");
        weakness.addProperty("duration", 60);
        weakness.addProperty("amplifier", 0);
        areaEffects.add(weakness);
        areaEffect.add("effects", areaEffects);
        effects.add(areaEffect);
        
        // Heal on next hit
        JsonObject attackBonus = new JsonObject();
        attackBonus.addProperty("type", "owner_next_attack_bonus");
        attackBonus.addProperty("bonus_damage_pct", 0.00);
        attackBonus.addProperty("expire_ticks", 100);
        
        JsonObject healEffect = new JsonObject();
        healEffect.addProperty("type", "heal_owner_flat_pct");
        healEffect.addProperty("value", "${cursed_one.doomEchoHealOnNextHitPct}");
        attackBonus.add("on_hit_effect", healEffect);
        effects.add(attackBonus);
        
        // Owner nausea
        JsonObject nauseaBuff = new JsonObject();
        nauseaBuff.addProperty("type", "effect");
        nauseaBuff.addProperty("target", "owner");
        nauseaBuff.addProperty("id", "minecraft:nausea");
        nauseaBuff.addProperty("duration", 20);
        nauseaBuff.addProperty("amplifier", 0);
        effects.add(nauseaBuff);
        
        ability.add("effects", effects);
        return ability;
    }
    
    private static JsonObject createVoidbrand() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:voidbrand");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "aggro_acquired");
        trigger.addProperty("cooldown_ticks", 300);
        ability.add("trigger", trigger);
        
        JsonArray effects = new JsonArray();
        
        // Tag target
        JsonObject tagEffect = new JsonObject();
        tagEffect.addProperty("type", "tag_target");
        tagEffect.addProperty("key", "petsplus:voidbrand");
        tagEffect.addProperty("duration_ticks", "${eclipsed.markDurationTicks}");
        effects.add(tagEffect);
        
        // Owner attack bonus vs marked
        JsonObject attackBonus = new JsonObject();
        attackBonus.addProperty("type", "owner_next_attack_bonus");
        attackBonus.addProperty("vs_tag", "petsplus:voidbrand");
        attackBonus.addProperty("bonus_damage_pct", "${eclipsed.ownerBonusVsMarkedPct}");
        attackBonus.addProperty("expire_ticks", 100);
        
        JsonObject witherEffect = new JsonObject();
        witherEffect.addProperty("type", "effect");
        witherEffect.addProperty("target", "victim");
        witherEffect.addProperty("id", "${eclipsed.ownerNextHitEffect}");
        witherEffect.addProperty("duration", "${eclipsed.ownerNextHitEffectDurationTicks}");
        witherEffect.addProperty("amplifier", 0);
        attackBonus.add("on_hit_effect", witherEffect);
        effects.add(attackBonus);
        
        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createVoidStorage() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:void_storage");
        ability.addProperty("required_level", 7);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_signal_proximity_channel");
        trigger.addProperty("cooldown_ticks", 120);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject openEnderChest = new JsonObject();
        openEnderChest.addProperty("type", "open_ender_chest");
        effects.add(openEnderChest);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createPhasePartner() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:phase_partner");
        ability.addProperty("description", "Teleport tether to owner");
        ability.addProperty("required_level", 23);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "on_combat_end");
        trigger.addProperty("cooldown_ticks", 200);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();

        JsonObject retarget = new JsonObject();
        retarget.addProperty("type", "retarget_nearest_hostile");
        retarget.addProperty("radius", 12);
        retarget.addProperty("store_as", "pp_target");
        effects.add(retarget);

        JsonObject tagEffect = new JsonObject();
        tagEffect.addProperty("type", "tag_target");
        tagEffect.addProperty("target", "pp_target");
        tagEffect.addProperty("key", "petsplus:phase_partner");
        tagEffect.addProperty("duration_ticks", 80);
        effects.add(tagEffect);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createPerchPotionEfficiency() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:perch_potion_efficiency");
        ability.addProperty("description", "Perched potion efficiency");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "interval_while_active");
        trigger.addProperty("ticks", 40);
        trigger.addProperty("require_perched", true);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject potionReduction = new JsonObject();
        potionReduction.addProperty("type", "perch_potion_sip_reduction");
        potionReduction.addProperty("discount_percent", 0.2);
        effects.add(potionReduction);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createMountedConeAura() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:mounted_cone_aura");
        ability.addProperty("description", "Mounted support aura");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "interval_while_active");
        trigger.addProperty("ticks", 40);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject coneAura = new JsonObject();
        coneAura.addProperty("type", "mounted_cone_aura");
        coneAura.addProperty("base_radius", 8.0);
        coneAura.addProperty("extra_radius", 2);
        coneAura.addProperty("effect_duration", 60);
        coneAura.addProperty("effect_amplifier", 0);
        effects.add(coneAura);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createPerchPing() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:perch_ping");
        ability.addProperty("description", "Perch reconnaissance ping");
        ability.addProperty("required_level", 27);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "interval_while_active");
        trigger.addProperty("ticks", 40);
        trigger.addProperty("require_perched", true);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();

        JsonObject retarget = new JsonObject();
        retarget.addProperty("type", "retarget_nearest_hostile");
        retarget.addProperty("radius", 12);
        retarget.addProperty("store_as", "pp_target");
        effects.add(retarget);

        JsonObject tagEffect = new JsonObject();
        tagEffect.addProperty("type", "tag_target");
        tagEffect.addProperty("target", "pp_target");
        tagEffect.addProperty("key", "petsplus:voidbrand");
        tagEffect.addProperty("duration_ticks", 60);
        effects.add(tagEffect);

        ability.add("effects", effects);
        return ability;
    }
    
    // Role creators
    private static JsonObject createGuardianRole() {
        JsonObject role = new JsonObject();
        role.addProperty("name", "Guardian");
        role.addProperty("description", "Tank role with damage redirection and protection");
        
        JsonArray abilities = new JsonArray();
        abilities.add("petsplus:shield_bash_rider");
        abilities.add("petsplus:bulwark_redirect");
        role.add("abilities", abilities);
        
        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("3", "Basic bulwark redirect");
        featureLevels.addProperty("10", "Shield bash rider with projectile DR");
        featureLevels.addProperty("20", "Mount knockback resistance buff");
        role.add("feature_levels", featureLevels);
        
        return role;
    }
    
    private static JsonObject createStrikerRole() {
        JsonObject role = new JsonObject();
        role.addProperty("name", "Striker");
        role.addProperty("description", "Execution specialist without pet DPS dependency");
        
        JsonArray abilities = new JsonArray();
        abilities.add("petsplus:finisher_mark");
        abilities.add("petsplus:execution_bonus");
        role.add("abilities", abilities);
        
        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("10", "Executioner fallback for low health targets");
        featureLevels.addProperty("15", "Finisher mark system with tagging");
        featureLevels.addProperty("25", "Enhanced execution bonuses");
        role.add("feature_levels", featureLevels);
        
        return role;
    }
    
    private static JsonObject createSupportRole() {
        JsonObject role = new JsonObject();
        role.addProperty("name", "Support");
        role.addProperty("description", "Quality of life improvements and aura enhancements");
        
        JsonArray abilities = new JsonArray();
        abilities.add("petsplus:perch_potion_efficiency");
        abilities.add("petsplus:mounted_cone_aura");
        role.add("abilities", abilities);
        
        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("5", "Perched potion sip reduction");
        featureLevels.addProperty("15", "Mounted cone bias aura system");
        role.add("feature_levels", featureLevels);
        
        return role;
    }
    
    private static JsonObject createScoutRole() {
        JsonObject role = new JsonObject();
        role.addProperty("name", "Scout");
        role.addProperty("description", "Information gathering and mobility for everyone");

        JsonArray abilities = new JsonArray();
        abilities.add("petsplus:spotter_fallback");
        abilities.add("petsplus:gale_pace");
        abilities.add("petsplus:loot_wisp");
        abilities.add("petsplus:scout_backpack");
        role.add("abilities", abilities);
        
        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("10", "Spotter fallback with glowing effect");
        featureLevels.addProperty("20", "Gale pace for mount speed scaling");
        featureLevels.addProperty("25", "Loot wisp magnetization");
        role.add("feature_levels", featureLevels);
        
        return role;
    }
    
    private static JsonObject createSkyriderRole() {
        JsonObject role = new JsonObject();
        role.addProperty("name", "Skyrider");
        role.addProperty("description", "Air control without pet hit dependency");
        
        JsonArray abilities = new JsonArray();
        abilities.add("petsplus:projectile_levitation");
        abilities.add("petsplus:windlash_rider");
        abilities.add("petsplus:skybond_mount_extension");
        role.add("abilities", abilities);
        
        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("20", "Projectile crit levitation chance");
        featureLevels.addProperty("25", "Windlash rider on fall damage");
        featureLevels.addProperty("30", "Skybond aura applies to mount");
        role.add("feature_levels", featureLevels);
        
        return role;
    }
    
    private static JsonObject createEnchantmentBoundRole() {
        JsonObject role = new JsonObject();
        role.addProperty("name", "Enchantment-Bound");
        role.addProperty("description", "Owner-centric enchantment echoes");
        
        JsonArray abilities = new JsonArray();
        abilities.add("petsplus:perched_haste_bonus");
        abilities.add("petsplus:mounted_extra_rolls");
        role.add("abilities", abilities);
        
        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("12", "Perched haste bonus extension");
        featureLevels.addProperty("22", "Mounted extra rolls for lassoed mobs");
        featureLevels.addProperty("28", "Trampled crop extra rolls");
        role.add("feature_levels", featureLevels);
        
        return role;
    }
    
    private static JsonObject createCursedOneRole() {
        JsonObject role = new JsonObject();
        role.addProperty("name", "Cursed One");
        role.addProperty("description", "Risk fantasy with doom mechanics");
        
        JsonArray abilities = new JsonArray();
        abilities.add("petsplus:doom_echo");
        abilities.add("petsplus:auto_resurrect_mount_buff");
        role.add("abilities", abilities);
        
        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("15", "Doom echo on low health");
        featureLevels.addProperty("25", "Auto-resurrect mount resistance");
        role.add("feature_levels", featureLevels);
        
        return role;
    }
    
    private static JsonObject createEepyEeperRole() {
        JsonObject role = new JsonObject();
        role.addProperty("name", "Eepy Eeper");
        role.addProperty("description", "Cozy vibes everywhere");
        
        JsonArray abilities = new JsonArray();
        abilities.add("petsplus:nap_time_radius");
        role.add("abilities", abilities);
        
        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("8", "Nap time extra radius for sitting/perched pets");
        role.add("feature_levels", featureLevels);
        
        return role;
    }
    
    private static JsonObject createEclipsedRole() {
        JsonObject role = new JsonObject();
        role.addProperty("name", "Eclipsed");
        role.addProperty("description", "Owner/perch loop with void mechanics");
        
        JsonArray abilities = new JsonArray();
        abilities.add("petsplus:voidbrand");
        abilities.add("petsplus:void_storage");
        abilities.add("petsplus:phase_partner");
        abilities.add("petsplus:perch_ping");
        abilities.add("petsplus:event_horizon");
        abilities.add("petsplus:edge_step");
        role.add("abilities", abilities);
        
        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("17", "Voidbrand target marking");
        featureLevels.addProperty("23", "Phase partner blink synergy");
        featureLevels.addProperty("27", "Perch ping targeting system");
        featureLevels.addProperty("30", "Event horizon void save zone");
        featureLevels.addProperty("32", "Edge step fall reduction");
        role.add("feature_levels", featureLevels);
        
        return role;
    }
    
    private static JsonObject createCCResistantTag() {
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        
        JsonArray values = new JsonArray();
        values.add("minecraft:ender_dragon");
        values.add("minecraft:wither");
        values.add("minecraft:elder_guardian");
        values.add("minecraft:warden");
        values.add("minecraft:ravager");
        values.add("minecraft:iron_golem");
        values.add("minecraft:snow_golem");
        values.add("#petsplus:boss_entities");  // Include all boss entities
        
        tag.add("values", values);
        return tag;
    }
    
    private static JsonObject createBossEntitiesTag() {
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        
        JsonArray values = new JsonArray();
        values.add("minecraft:ender_dragon");
        values.add("minecraft:wither");
        values.add("minecraft:elder_guardian");
        values.add("minecraft:warden");
        
        tag.add("values", values);
        return tag;
    }
    
    private static JsonObject createTributeImmuneTag() {
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        
        JsonArray values = new JsonArray();
        values.add("minecraft:player");
        values.add("minecraft:armor_stand");
        values.add("minecraft:item_frame");
        values.add("minecraft:glow_item_frame");
        values.add("minecraft:painting");
        values.add("minecraft:item");
        values.add("minecraft:experience_orb");
        values.add("#petsplus:boss_entities");  // Include all boss entities
        
        tag.add("values", values);
        return tag;
    }
}