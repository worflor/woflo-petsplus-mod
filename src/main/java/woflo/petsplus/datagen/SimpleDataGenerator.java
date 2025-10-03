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
        writeFile("abilities/bulwark_redirect.json", createBulwarkRedirectAbility());
        writeFile("abilities/fortress_bond.json", createFortressBondAbility());
        writeFile("abilities/fortress_bond_pet_guard.json", createFortressBondPetGuardAbility());

        // Striker abilities
        writeFile("abilities/finisher_mark.json", createFinisherMark());
        writeFile("abilities/striker_execution.json", createStrikerExecutionAbility());
        writeFile("abilities/bloodlust_surge.json", createBloodlustSurge());

        // Support abilities
        writeFile("abilities/perch_potion_efficiency.json", createPerchPotionEfficiency());
        writeFile("abilities/support_potion_pulse.json", createSupportPotionPulse());

        // Scout abilities
        writeFile("abilities/loot_wisp.json", createLootWisp());
        writeFile("abilities/spotter_fallback.json", createSpotterFallback());
        writeFile("abilities/scout_backpack.json", createScoutBackpack());

        // Skyrider abilities
        writeFile("abilities/projectile_levitation.json", createSkyriderProjectileLevitation());
        writeFile("abilities/windlash_rider.json", createWindlashRider());
        writeFile("abilities/gust_upwards.json", createGustUpwards());
        writeFile("abilities/skyrider_fall_guard_owner.json", createSkyriderFallGuardOwner());
        writeFile("abilities/skyrider_fall_guard_pet.json", createSkyriderFallGuardPet());

        // Eepy Eeper abilities
        writeFile("abilities/drowsy_mist.json", createDrowsyMistAbility());
        writeFile("abilities/nap_time_radius.json", createNapTimeRadiusAbility());
        writeFile("abilities/restful_dreams.json", createRestfulDreamsAbility());

        // Cursed One abilities
        writeFile("abilities/doom_echo.json", createDoomEcho());
        writeFile("abilities/soul_sacrifice.json", createSoulSacrifice());
        writeFile("abilities/death_burst.json", createDeathBurst());
        writeFile("abilities/cursed_reanimation.json", createCursedReanimation());
        writeFile("abilities/cursed_mount_resilience.json", createCursedMountResilience());

        // Enchantment-Bound abilities
        writeFile("abilities/enchant_strip.json", createEnchantStrip());
        writeFile("abilities/gear_swap.json", createGearSwap());
        writeFile("abilities/enchantment_magic_guard.json", createEnchantmentMagicGuard());
        writeFile("abilities/enchantment_mining_echo.json", createEnchantmentMiningEcho());
        writeFile("abilities/enchantment_combat_focus.json", createEnchantmentCombatFocus());
        writeFile("abilities/enchantment_swim_echo.json", createEnchantmentSwimEcho());
        writeFile("abilities/enchantment_extra_loot.json", createEnchantmentExtraLoot());
        writeFile("abilities/perched_haste_bonus.json", createEnchantmentPerchedHaste());

        // Eclipsed abilities
        writeFile("abilities/voidbrand.json", createVoidbrand());
        writeFile("abilities/void_storage.json", createVoidStorage());
        writeFile("abilities/phase_partner.json", createPhasePartner());
        writeFile("abilities/perch_ping.json", createPerchPing());
        writeFile("abilities/eclipsed_darkness_owner.json", createEclipsedDarknessOwner());
        writeFile("abilities/eclipsed_darkness_pet.json", createEclipsedDarknessPet());
        writeFile("abilities/event_horizon.json", createEventHorizon());
        writeFile("abilities/edge_step.json", createEdgeStep());
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

    private static JsonObject createBulwarkRedirectAbility() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:bulwark_redirect");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_incoming_damage");
        trigger.addProperty("internal_cd_ticks", 40);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();

        JsonObject redirect = new JsonObject();
        redirect.addProperty("type", "guardian_bulwark_redirect");
        effects.add(redirect);

        JsonObject petReduction = new JsonObject();
        petReduction.addProperty("type", "guardian_fortress_bond_pet_dr");
        effects.add(petReduction);

        JsonObject projectileDr = new JsonObject();
        projectileDr.addProperty("type", "projectile_dr_for_owner");
        projectileDr.addProperty("percent", 0.25);
        projectileDr.addProperty("duration_ticks", 100);
        projectileDr.addProperty("require_data_flag", "guardian_bulwark_redirect_success");
        effects.add(projectileDr);

        JsonObject petResistance = new JsonObject();
        petResistance.addProperty("type", "buff");
        petResistance.addProperty("target", "pet");
        petResistance.addProperty("id", "minecraft:resistance");
        petResistance.addProperty("duration", 60);
        petResistance.addProperty("amplifier", 0);
        petResistance.addProperty("require_data_flag", "guardian_bulwark_redirect_success");
        effects.add(petResistance);

        JsonObject retarget = new JsonObject();
        retarget.addProperty("type", "retarget_nearest_hostile");
        retarget.addProperty("radius", 6.0);
        retarget.addProperty("require_data_flag", "guardian_bulwark_redirect_success");
        effects.add(retarget);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createFortressBondAbility() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:fortress_bond");
        ability.addProperty("required_level", 7);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_signal_proximity_channel");
        trigger.addProperty("cooldown_ticks", 240);
        trigger.addProperty("internal_cd_ticks", 240);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();

        JsonObject bond = new JsonObject();
        bond.addProperty("type", "guardian_fortress_bond");
        bond.addProperty("reduction_pct", 0.5);
        bond.addProperty("duration_ticks", 200);
        effects.add(bond);

        JsonObject petReduction = new JsonObject();
        petReduction.addProperty("type", "guardian_fortress_bond_pet_dr");
        effects.add(petReduction);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createFortressBondPetGuardAbility() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:fortress_bond_pet_guard");
        ability.addProperty("required_level", 7);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "pet_incoming_damage");
        trigger.addProperty("internal_cd_ticks", 0);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();

        JsonObject petReduction = new JsonObject();
        petReduction.addProperty("type", "guardian_fortress_bond_pet_dr");
        effects.add(petReduction);

        ability.add("effects", effects);
        return ability;
    }
    
    private static JsonObject createFinisherMark() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:finisher_mark");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_dealt_damage");
        trigger.addProperty("target_hp_pct_below", 0.45);
        trigger.addProperty("cooldown_ticks", 160);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();

        JsonObject feedback = new JsonObject();
        feedback.addProperty("type", "striker_mark_feedback");
        feedback.addProperty("particle_count", 10);
        feedback.addProperty("particle_spread", 0.45);
        effects.add(feedback);

        // Tag target
        JsonObject tagEffect = new JsonObject();
        tagEffect.addProperty("type", "tag_target");
        tagEffect.addProperty("key", "petsplus:finisher");
        tagEffect.addProperty("duration_ticks", 100);
        effects.add(tagEffect);

        // Owner attack bonus
        JsonObject attackBonus = new JsonObject();
        attackBonus.addProperty("type", "owner_next_attack_bonus");
        attackBonus.addProperty("vs_tag", "petsplus:finisher");
        attackBonus.addProperty("bonus_damage_pct", 0.25);
        attackBonus.addProperty("expire_ticks", 120);

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
        mountBuff.addProperty("duration", 60);
        mountBuff.addProperty("amplifier", 0);
        mountBuff.addProperty("only_if_mounted", true);
        effects.add(mountBuff);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createStrikerExecutionAbility() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:striker_execution");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_outgoing_damage");
        trigger.addProperty("cooldown_ticks", 0);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "striker_execution");
        effect.addProperty("emit_feedback", true);
        effect.addProperty("publish_preview", true);
        effects.add(effect);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createEnchantmentMagicGuard() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:enchantment_magic_guard");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "pet_incoming_damage");
        trigger.addProperty("cooldown_ticks", 10);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "magic_damage_shield");
        effect.addProperty("max_damage", 1.0);
        effects.add(effect);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createEnchantmentMiningEcho() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:enchantment_mining_echo");
        ability.addProperty("required_level", 1);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_broke_block");
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();

        JsonObject focus = new JsonObject();
        focus.addProperty("type", "enchantment_arcane_focus");
        focus.addProperty("bucket", "mining");
        focus.addProperty("duration_ticks", 200);
        focus.addProperty("cooldown_ticks", 1200);
        focus.addProperty("charges_at_level_30", 2);
        effects.add(focus);

        JsonObject haste = new JsonObject();
        haste.addProperty("type", "enchantment_mining_haste");
        haste.addProperty("base_duration_ticks", 40);
        effects.add(haste);

        JsonObject durability = new JsonObject();
        durability.addProperty("type", "enchantment_durability_refund");
        durability.addProperty("base_chance", 0.025);
        durability.addProperty("focus_multiplier", 2.0);
        effects.add(durability);

        JsonObject extra = new JsonObject();
        extra.addProperty("type", "enchantment_extra_drops");
        extra.addProperty("mode", "block");
        extra.addProperty("base_chance", 0.05);
        extra.addProperty("per_level_bonus", 0.02);
        extra.addProperty("focus_multiplier", 2.0);
        effects.add(extra);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createEnchantmentCombatFocus() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:enchantment_combat_focus");
        ability.addProperty("required_level", 20);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_killed_entity");
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject focus = new JsonObject();
        focus.addProperty("type", "enchantment_arcane_focus");
        focus.addProperty("bucket", "combat");
        focus.addProperty("duration_ticks", 200);
        focus.addProperty("cooldown_ticks", 1200);
        focus.addProperty("charges_at_level_30", 2);
        focus.addProperty("play_sound", true);
        focus.addProperty("require_hostile_victim", true);
        effects.add(focus);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createEnchantmentSwimEcho() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:enchantment_swim_echo");
        ability.addProperty("required_level", 1);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "interval_while_active");
        trigger.addProperty("ticks", 10);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();

        JsonObject swim = new JsonObject();
        swim.addProperty("type", "enchantment_swim_grace");
        swim.addProperty("duration_ticks", 40);
        swim.addProperty("min_level", 1);
        effects.add(swim);

        JsonObject focus = new JsonObject();
        focus.addProperty("type", "enchantment_arcane_focus");
        focus.addProperty("bucket", "swim");
        focus.addProperty("duration_ticks", 200);
        focus.addProperty("cooldown_ticks", 1200);
        focus.addProperty("charges_at_level_30", 2);
        focus.addProperty("play_sound", true);
        effects.add(focus);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createEnchantmentExtraLoot() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:enchantment_extra_loot");
        ability.addProperty("required_level", 1);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "loot_table_modify");
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject extra = new JsonObject();
        extra.addProperty("type", "enchantment_extra_drops");
        extra.addProperty("mode", "mob");
        extra.addProperty("base_chance", 0.05);
        extra.addProperty("per_level_bonus", 0.02);
        extra.addProperty("focus_multiplier", 2.0);
        extra.addProperty("require_hostile", true);
        extra.addProperty("play_feedback", true);
        effects.add(extra);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createEnchantmentPerchedHaste() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:perched_haste_bonus");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "interval_while_active");
        trigger.addProperty("ticks", 200);
        trigger.addProperty("internal_cd_ticks", 200);
        trigger.addProperty("require_perched", true);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject haste = new JsonObject();
        haste.addProperty("type", "enchantment_perched_haste");
        haste.addProperty("base_duration", 140);
        haste.addProperty("amplifier", 1);
        effects.add(haste);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createSkyriderFallGuardOwner() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:skyrider_fall_guard_owner");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_incoming_damage");
        trigger.addProperty("internal_cd_ticks", 10);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "skyrider_fall_guard");
        effect.addProperty("mode", "owner");
        effect.addProperty("radius", 16.0);
        effect.addProperty("max_damage", 4.0);
        effect.addProperty("min_fall_distance", 3.0);
        effect.addProperty("apply_to_mount", true);
        effect.addProperty("mount_slowfall_ticks", 100);
        effect.addProperty("pet_slowfall_ticks", 80);
        effects.add(effect);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createSkyriderFallGuardPet() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:skyrider_fall_guard_pet");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "pet_incoming_damage");
        trigger.addProperty("internal_cd_ticks", 10);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "skyrider_fall_guard");
        effect.addProperty("mode", "pet");
        effect.addProperty("max_damage", 2.0);
        effect.addProperty("pet_slowfall_ticks", 80);
        effects.add(effect);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createBloodlustSurge() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:bloodlust_surge");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_killed_entity");
        trigger.addProperty("require_execution", true);
        trigger.addProperty("cooldown_ticks", 0);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject surge = new JsonObject();
        surge.addProperty("type", "striker_bloodlust_surge");
        surge.addProperty("base_duration_ticks", 100);
        surge.addProperty("per_stack_duration_ticks", 40);
        surge.addProperty("owner_strength_per_stack", 0.7);
        surge.addProperty("owner_speed_per_stack", 0.5);
        surge.addProperty("pet_strength_per_stack", 0.45);
        surge.addProperty("pet_speed_per_stack", 0.35);
        surge.addProperty("max_amplifier", 3);
        surge.addProperty("swing_owner", true);
        surge.addProperty("buff_pet", true);
        surge.addProperty("play_feedback", true);
        effects.add(surge);

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

    private static JsonObject createSpotterFallback() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:spotter_fallback");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_dealt_damage");
        trigger.addProperty("internal_cd_ticks", 5);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "scout_spotter_fallback");
        effect.addProperty("radius", 16.0);
        effect.addProperty("min_level", 10);
        effect.addProperty("idle_ticks", 60);
        effect.addProperty("cooldown_ticks", 300);
        effect.addProperty("glow_duration_ticks", 20);
        effect.addProperty("send_message", true);
        effects.add(effect);

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
        ability.addProperty("required_level", 7);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_begin_fall");
        trigger.addProperty("min_fall", 3);
        trigger.addProperty("internal_cd_ticks", 120);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject windlash = new JsonObject();
        windlash.addProperty("type", "skyrider_windlash");
        windlash.addProperty("radius", 16.0);
        windlash.addProperty("min_level", 7);
        windlash.addProperty("min_fall_distance", 3.0);
        windlash.addProperty("jump_boost_duration_ticks", 40);
        windlash.addProperty("jump_boost_amplifier", 0);
        windlash.addProperty("bonus_damage_pct", 0.15);
        windlash.addProperty("bonus_expire_ticks", 100);
        windlash.addProperty("knockup_strength", 0.35);
        windlash.addProperty("owner_slowfall_ticks", 60);
        windlash.addProperty("duplicate_gate_ticks", 5);
        windlash.addProperty("send_message", true);
        effects.add(windlash);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createSkyriderProjectileLevitation() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:projectile_levitation");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_shot_projectile");
        trigger.addProperty("projectile_type", "arrow");
        trigger.addProperty("internal_cd_ticks", 60);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject levitation = new JsonObject();
        levitation.addProperty("type", "skyrider_projectile_levitation");
        levitation.addProperty("radius", 16.0);
        levitation.addProperty("chance", 0.1);
        levitation.addProperty("require_critical", true);
        levitation.addProperty("victim_duration_ticks", 40);
        levitation.addProperty("splash_radius", 5.0);
        levitation.addProperty("splash_duration_ticks", 40);
        levitation.addProperty("owner_slowfall_ticks", 80);
        levitation.addProperty("pet_slowfall_ticks", 80);
        levitation.addProperty("duplicate_gate_ticks", 5);
        levitation.addProperty("send_message", true);
        effects.add(levitation);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createGustUpwards() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:gust_upwards");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_signal_shift_interact");
        trigger.addProperty("cooldown_ticks", 160);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject gust = new JsonObject();
        gust.addProperty("type", "skyrider_gust_upwards");
        gust.addProperty("vertical_boost", 1.0);
        gust.addProperty("forward_push", 0.25);
        gust.addProperty("pet_lift_scale", 0.6);
        gust.addProperty("owner_slowfall_ticks", 100);
        gust.addProperty("pet_slowfall_ticks", 80);
        gust.addProperty("lift_pet", true);
        gust.addProperty("mount_inherit", true);
        gust.addProperty("swing_owner", true);
        gust.addProperty("play_feedback", true);
        effects.add(gust);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createDrowsyMistAbility() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:drowsy_mist");
        ability.addProperty("required_level", 7);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_signal_shift_interact");
        trigger.addProperty("cooldown_ticks", 200);
        ability.add("trigger", trigger);

        JsonObject effect = new JsonObject();
        effect.addProperty("type", "eepy_drowsy_mist");
        effect.addProperty("radius_base", 4.5);
        effect.addProperty("radius_per_level", 0.1);
        effect.addProperty("radius_max", 8.0);
        effect.addProperty("duration_base_ticks", 100);
        effect.addProperty("duration_per_level_ticks", 2);
        effect.addProperty("duration_max_ticks", 160);
        effect.addProperty("pulse_interval_ticks", 10);
        effect.addProperty("effect_pulse_ticks", 60);
        effect.addProperty("slowness_base_amplifier", 2);
        effect.addProperty("slowness_level_threshold", 24);
        effect.addProperty("slowness_max_amplifier", 3);
        effect.addProperty("weakness_level_threshold", 17);
        effect.addProperty("weakness_amplifier", 1);
        effect.addProperty("activation_range", 10.0);
        effect.addProperty("target_tag", "minecraft:hostile");
        effect.addProperty("immune_tag", "petsplus:cc_resistant");

        JsonArray effects = new JsonArray();
        effects.add(effect);
        ability.add("effects", effects);

        return ability;
    }

    private static JsonObject createNapTimeRadiusAbility() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:nap_time_radius");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "interval_while_active");
        trigger.addProperty("ticks", 100);
        trigger.addProperty("internal_cd_ticks", 100);
        ability.add("trigger", trigger);

        JsonObject aura = new JsonObject();
        aura.addProperty("type", "eepy_nap_aura");
        aura.addProperty("radius", 4.0);
        aura.addProperty("duration_ticks", 120);
        aura.addProperty("amplifier", 0);
        aura.addProperty("min_level", 10);
        aura.addProperty("require_sitting", true);
        aura.addProperty("message_interval_ticks", 200);

        JsonArray effects = new JsonArray();
        effects.add(aura);
        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createRestfulDreamsAbility() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:restful_dreams");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_sleep_complete");
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject restful = new JsonObject();
        restful.addProperty("type", "eepy_restful_dreams");
        restful.addProperty("search_radius", 16.0);
        restful.addProperty("owner_regen_duration_ticks", 200);
        restful.addProperty("owner_regen_amplifier", 0);
        restful.addProperty("owner_saturation_duration_ticks", 200);
        restful.addProperty("owner_saturation_amplifier", 1);
        restful.addProperty("pet_resistance_duration_ticks", 200);
        restful.addProperty("pet_resistance_amplifier", 0);
        effects.add(restful);
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

    private static JsonObject createSoulSacrifice() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:soul_sacrifice");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_signal_proximity_channel");
        trigger.addProperty("cooldown_ticks", 1200);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject sacrifice = new JsonObject();
        sacrifice.addProperty("type", "cursed_one_soul_sacrifice");
        sacrifice.addProperty("xp_cost_levels", 4);
        sacrifice.addProperty("duration_ticks", 600);
        sacrifice.addProperty("reanimation_multiplier", 2.0);
        sacrifice.addProperty("owner_heal_pct", 0.3);

        JsonArray ownerEffects = new JsonArray();
        JsonObject strength = new JsonObject();
        strength.addProperty("id", "minecraft:strength");
        strength.addProperty("duration", 600);
        strength.addProperty("amplifier", 1);
        ownerEffects.add(strength);

        JsonObject resistance = new JsonObject();
        resistance.addProperty("id", "minecraft:resistance");
        resistance.addProperty("duration", 600);
        resistance.addProperty("amplifier", 1);
        ownerEffects.add(resistance);

        JsonObject absorption = new JsonObject();
        absorption.addProperty("id", "minecraft:absorption");
        absorption.addProperty("duration", 200);
        absorption.addProperty("amplifier", 2);
        ownerEffects.add(absorption);

        JsonObject speed = new JsonObject();
        speed.addProperty("id", "minecraft:speed");
        speed.addProperty("duration", 400);
        speed.addProperty("amplifier", 1);
        ownerEffects.add(speed);

        sacrifice.add("owner_effects", ownerEffects);
        effects.add(sacrifice);

        ability.add("effects", effects);
        ability.addProperty("required_level", 20);
        return ability;
    }

    private static JsonObject createCursedReanimation() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:cursed_reanimation");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "pet_lethal_damage");
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "cursed_one_reanimation");
        effects.add(effect);
        ability.add("effects", effects);

        return ability;
    }

    private static JsonObject createCursedMountResilience() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:cursed_mount_resilience");
        ability.addProperty("required_level", 25);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_respawn");
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject buff = new JsonObject();
        buff.addProperty("type", "cursed_mount_resilience");
        buff.addProperty("search_radius", 16.0);
        buff.addProperty("duration_ticks", 60);
        buff.addProperty("amplifier", 0);
        buff.addProperty("require_death_respawn", true);
        effects.add(buff);
        ability.add("effects", effects);

        return ability;
    }

    private static JsonObject createDeathBurst() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:death_burst");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "on_pet_death");
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject burst = new JsonObject();
        burst.addProperty("type", "cursed_one_death_burst");
        burst.addProperty("radius", 6.0);
        burst.addProperty("damage", 14.0);
        burst.addProperty("ignite", true);

        JsonObject reanimation = new JsonObject();
        reanimation.addProperty("radius_scale", 0.6);
        reanimation.addProperty("damage_scale", 0.5);
        reanimation.addProperty("ignite", false);
        reanimation.addProperty("effect_duration_scale", 0.5);
        burst.add("reanimation", reanimation);

        JsonArray enemyEffects = new JsonArray();
        JsonObject wither = new JsonObject();
        wither.addProperty("id", "minecraft:wither");
        wither.addProperty("duration", 100);
        wither.addProperty("amplifier", 0);
        enemyEffects.add(wither);

        JsonObject slowness = new JsonObject();
        slowness.addProperty("id", "minecraft:slowness");
        slowness.addProperty("duration", 80);
        slowness.addProperty("amplifier", 1);
        enemyEffects.add(slowness);

        burst.add("enemy_effects", enemyEffects);
        effects.add(burst);

        ability.add("effects", effects);
        ability.addProperty("required_level", 15);
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
        trigger.addProperty("internal_cd_ticks", 200);
        ability.add("trigger", trigger);

        JsonObject effect = new JsonObject();
        effect.addProperty("type", "eclipsed_phase_partner");
        effect.addProperty("radius", 12.0);
        effect.addProperty("tag_duration_ticks", 80);
        effect.addProperty("min_level", 23);
        effect.addProperty("require_perched", true);

        JsonArray effects = new JsonArray();
        effects.add(effect);
        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createEventHorizon() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:event_horizon");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_low_health");
        trigger.addProperty("owner_hp_pct_below", 0.3);
        trigger.addProperty("internal_cd_ticks", 600);
        ability.add("trigger", trigger);

        JsonObject effect = new JsonObject();
        effect.addProperty("type", "eclipsed_event_horizon");
        effect.addProperty("radius", 6.0);
        effect.addProperty("enemy_effect_duration_ticks", 100);

        JsonArray enemyEffects = new JsonArray();
        JsonObject blind = new JsonObject();
        blind.addProperty("id", "minecraft:blindness");
        blind.addProperty("duration", 80);
        blind.addProperty("amplifier", 0);
        enemyEffects.add(blind);
        effect.add("enemy_effects", enemyEffects);

        JsonArray ownerEffects = new JsonArray();
        JsonObject invis = new JsonObject();
        invis.addProperty("id", "minecraft:invisibility");
        invis.addProperty("duration", 100);
        invis.addProperty("amplifier", 0);
        ownerEffects.add(invis);

        JsonObject speed = new JsonObject();
        speed.addProperty("id", "minecraft:speed");
        speed.addProperty("duration", 120);
        speed.addProperty("amplifier", 2);
        ownerEffects.add(speed);
        effect.add("owner_effects", ownerEffects);

        effect.addProperty("heal_flat", 4.0);
        effect.addProperty("heal_percent", 0.15);
        effect.addProperty("projectile_dr", 0.25);
        effect.addProperty("projectile_dr_duration_ticks", 100);
        effect.addProperty("min_level", 30);
        effect.addProperty("require_perched", false);

        JsonArray effects = new JsonArray();
        effects.add(effect);
        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createEdgeStep() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:edge_step");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_incoming_damage");
        trigger.addProperty("cooldown_ticks", 20);
        trigger.addProperty("internal_cd_ticks", 100);
        ability.add("trigger", trigger);

        JsonObject effect = new JsonObject();
        effect.addProperty("type", "eclipsed_edge_step");
        effect.addProperty("reduction_pct", 0.25);
        effect.addProperty("min_fall_distance", 3.0);
        effect.addProperty("min_level", 27);
        effect.addProperty("require_perched", true);
        effect.addProperty("slow_falling_duration_ticks", 60);
        effect.addProperty("speed_duration_ticks", 80);
        effect.addProperty("speed_amplifier", 1);
        effect.addProperty("heal_flat", 2.0);
        effect.addProperty("heal_percent", 0.0);

        JsonArray effects = new JsonArray();
        effects.add(effect);
        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createEnchantStrip() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:enchant_strip");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_signal_shift_interact");
        trigger.addProperty("cooldown_ticks", 120);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject strip = new JsonObject();
        strip.addProperty("type", "enchant_strip");
        strip.addProperty("xp_cost_levels", 3);
        strip.addProperty("prefer_mainhand", true);
        strip.addProperty("allow_offhand", true);
        strip.addProperty("drop_as_book", true);
        effects.add(strip);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createGearSwap() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:gear_swap");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_signal_double_crouch");
        trigger.addProperty("cooldown_ticks", 80);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject swap = new JsonObject();
        swap.addProperty("type", "gear_swap");
        swap.addProperty("store_sound", "minecraft:item.armor.equip_chain");
        swap.addProperty("swap_sound", "minecraft:item.armor.equip_diamond");
        effects.add(swap);

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
        potionReduction.addProperty("linger_ticks", 80);
        effects.add(potionReduction);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createSupportPotionPulse() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:support_potion_pulse");
        ability.addProperty("description", "Manual potion aura release");
        ability.addProperty("required_level", 5);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_signal_double_crouch");
        trigger.addProperty("cooldown_ticks", 200);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject pulse = new JsonObject();
        pulse.addProperty("type", "support_potion_pulse");
        pulse.addProperty("charge_cost_multiplier", 1.5);
        effects.add(pulse);

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

    private static JsonObject createEclipsedDarknessOwner() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:eclipsed_darkness_owner");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "owner_incoming_damage");
        trigger.addProperty("cooldown_ticks", 20);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "darkness_damage_shield");
        effect.addProperty("mode", "owner");
        effect.addProperty("max_damage", 3.0);
        effect.addProperty("radius", 16.0);
        effect.addProperty("require_owner_darkness", true);
        effect.addProperty("require_pet_darkness", false);
        effects.add(effect);

        ability.add("effects", effects);
        return ability;
    }

    private static JsonObject createEclipsedDarknessPet() {
        JsonObject ability = new JsonObject();
        ability.addProperty("id", "petsplus:eclipsed_darkness_pet");

        JsonObject trigger = new JsonObject();
        trigger.addProperty("event", "pet_incoming_damage");
        trigger.addProperty("cooldown_ticks", 10);
        ability.add("trigger", trigger);

        JsonArray effects = new JsonArray();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "darkness_damage_shield");
        effect.addProperty("mode", "pet");
        effect.addProperty("max_damage", 2.0);
        effect.addProperty("require_pet_darkness", true);
        effects.add(effect);

        ability.add("effects", effects);
        return ability;
    }

    // Role creators
    private static JsonObject createGuardianRole() {
        JsonObject role = new JsonObject();
        role.addProperty("name", "Guardian");
        role.addProperty("description", "Tank role with damage redirection and protection");
        
        JsonArray abilities = new JsonArray();
        abilities.add("petsplus:aegis_protocol");
        abilities.add("petsplus:fortress_bond");
        abilities.add("petsplus:fortress_bond_pet_guard");
        role.add("abilities", abilities);

        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("3", "Aegis protocol defense stacks");
        featureLevels.addProperty("7", "Fortress bond protective channel");
        featureLevels.addProperty("15", "Mount knockback resistance buff");
        role.add("feature_levels", featureLevels);
        
        return role;
    }
    
    private static JsonObject createStrikerRole() {
        JsonObject role = new JsonObject();
        role.addProperty("name", "Striker");
        role.addProperty("description", "Execution specialist without pet DPS dependency");
        
        JsonArray abilities = new JsonArray();
        abilities.add("petsplus:striker_execution");
        abilities.add("petsplus:finisher_mark");
        abilities.add("petsplus:execution_bonus");
        abilities.add("petsplus:bloodlust_surge");
        role.add("abilities", abilities);

        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("10", "Executioner fallback for low health targets");
        featureLevels.addProperty("15", "Finisher mark system with tagging");
        featureLevels.addProperty("22", "Bloodlust surge momentum buffs");
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
        abilities.add("petsplus:support_potion_pulse");
        role.add("abilities", abilities);

        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("5", "Perched potion sip reduction");
        featureLevels.addProperty("9", "Manual stored potion pulse");
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
        abilities.add("petsplus:gust_upwards");
        abilities.add("petsplus:skyrider_fall_guard_owner");
        abilities.add("petsplus:skyrider_fall_guard_pet");
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
        abilities.add("petsplus:phase_partner");
        abilities.add("petsplus:enchant_strip");
        abilities.add("petsplus:gear_swap");
        abilities.add("petsplus:enchantment_magic_guard");
        abilities.add("petsplus:enchantment_mining_echo");
        abilities.add("petsplus:enchantment_combat_focus");
        abilities.add("petsplus:enchantment_swim_echo");
        abilities.add("petsplus:enchantment_extra_loot");
        abilities.add("petsplus:perched_haste_bonus");
        role.add("abilities", abilities);

        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("15", "Shift-strip enchantment channel");
        featureLevels.addProperty("22", "Double crouch gear swap");
        featureLevels.addProperty("30", "Enhanced phase partner tether");
        role.add("feature_levels", featureLevels);

        return role;
    }
    
    private static JsonObject createCursedOneRole() {
        JsonObject role = new JsonObject();
        role.addProperty("name", "Cursed One");
        role.addProperty("description", "Risk fantasy with doom mechanics");

        JsonArray abilities = new JsonArray();
        abilities.add("petsplus:doom_echo");
        abilities.add("petsplus:soul_sacrifice");
        abilities.add("petsplus:auto_resurrect_owner");
        abilities.add("petsplus:cursed_reanimation");
        abilities.add("petsplus:death_burst");
        abilities.add("petsplus:cursed_mount_resilience");
        role.add("abilities", abilities);

        JsonObject featureLevels = new JsonObject();
        featureLevels.addProperty("15", "Death burst detonates on final demise");
        featureLevels.addProperty("20", "Soul sacrifice proximity channel");
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
        abilities.add("petsplus:drowsy_mist");
        abilities.add("petsplus:perch_ping");
        abilities.add("petsplus:restful_dreams");
        abilities.add("petsplus:dream_escape");
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
        abilities.add("petsplus:void_save");
        abilities.add("petsplus:eclipsed_darkness_owner");
        abilities.add("petsplus:eclipsed_darkness_pet");
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