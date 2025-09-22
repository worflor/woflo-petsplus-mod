package woflo.petsplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import woflo.petsplus.Petsplus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;

/**
 * Configuration manager for PetsPlus mod settings.
 */
public class PetsPlusConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("petsplus.json");
    
    // Default tribute items for each milestone level
    private static final Map<Integer, String> DEFAULT_TRIBUTE_ITEMS = new HashMap<>();
    static {
        DEFAULT_TRIBUTE_ITEMS.put(10, "minecraft:gold_ingot");
        DEFAULT_TRIBUTE_ITEMS.put(20, "minecraft:diamond");
        DEFAULT_TRIBUTE_ITEMS.put(30, "minecraft:netherite_ingot");
    }
    
    private static PetsPlusConfig instance;
    private JsonObject config;
    
    private PetsPlusConfig() {
        loadConfig();
    }
    
    public static PetsPlusConfig getInstance() {
        if (instance == null) {
            instance = new PetsPlusConfig();
        }
        return instance;
    }
    
    private void loadConfig() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String content = Files.readString(CONFIG_PATH);
                config = GSON.fromJson(content, JsonObject.class);
                Petsplus.LOGGER.info("Loaded PetsPlus config from {}", CONFIG_PATH);
            } catch (IOException e) {
                Petsplus.LOGGER.error("Failed to load config file", e);
                config = createDefaultConfig();
            }
        } else {
            config = createDefaultConfig();
            saveConfig();
        }
    }
    
    private void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config));
            Petsplus.LOGGER.info("Saved PetsPlus config to {}", CONFIG_PATH);
        } catch (IOException e) {
            Petsplus.LOGGER.error("Failed to save config file", e);
        }
    }
    
    private JsonObject createDefaultConfig() {
        JsonObject config = new JsonObject();
        
        // Guardian config
        JsonObject guardian = new JsonObject();
        guardian.addProperty("projectileDrOnRedirectPct", 0.10);
        guardian.addProperty("shieldBashIcdTicks", 120);
        config.add("guardian", guardian);
        
        // Petting config
        JsonObject petting = new JsonObject();
        petting.addProperty("cooldownTicks", 200); // 10 seconds
        petting.addProperty("xpBonusEnabled", true);
        petting.addProperty("roleBoostEnabled", false); // Disabled for cosmetic-only
        petting.addProperty("healingEnabled", false); // Disabled for cosmetic-only
        petting.addProperty("boostMultiplier", 1.0); // No boost for cosmetic-only
        config.add("petting", petting);
        
        // Striker config
        JsonObject striker = new JsonObject();
        striker.addProperty("ownerExecuteBonusPct", 0.10);
        striker.addProperty("finisherMarkBonusPct", 0.20);
        striker.addProperty("finisherMarkDurationTicks", 80);
        config.add("striker", striker);
        
        // Support config
        JsonObject support = new JsonObject();
        support.addProperty("perchSipDiscount", 0.20);
        support.addProperty("mountedConeExtraRadius", 2);
        support.addProperty("auraRadius", 6.0);
        support.addProperty("minLevel", 5);
        support.addProperty("particleDensity", 0.4);
        support.addProperty("particleHeight", 2.5);
        support.addProperty("particleSpeed", 0.025);
        support.addProperty("minParticles", 4);
        support.addProperty("maxParticles", 16);
        support.addProperty("particlesPerEntity", 3);
        support.addProperty("swirlFactor", 0.8);
        support.addProperty("companionChance", 0.3);
        support.addProperty("subtleIntensity", 0.7);
        config.add("support", support);
        
        // Scout config
        JsonObject scout = new JsonObject();
        scout.addProperty("lootWispDurationTicks", 80);
        config.add("scout", scout);
        
        // Skyrider config
        JsonObject skyrider = new JsonObject();
        skyrider.addProperty("ownerProjLevitateChance", 0.10);
        skyrider.addProperty("ownerProjLevitateIcdTicks", 200);
        config.add("skyrider", skyrider);
        
        // Eclipsed config
        JsonObject eclipsed = new JsonObject();
        eclipsed.addProperty("markDurationTicks", 80);
        eclipsed.addProperty("ownerBonusVsMarkedPct", 0.10);
        eclipsed.addProperty("ownerNextHitEffect", "minecraft:wither");
        eclipsed.addProperty("ownerNextHitEffectDurationTicks", 40);
        eclipsed.addProperty("phaseChargeInternalCdTicks", 400);
        eclipsed.addProperty("phaseChargeBonusDamagePct", 0.25);
        eclipsed.addProperty("phaseChargeWindowTicks", 100);
        eclipsed.addProperty("perchPingIntervalTicks", 140);
        eclipsed.addProperty("perchPingRadius", 8);
        eclipsed.addProperty("eventHorizonDurationTicks", 100);
        eclipsed.addProperty("eventHorizonRadius", 6.0);
        eclipsed.addProperty("eventHorizonProjectileDrPct", 0.25);
        eclipsed.addProperty("edgeStepFallReductionPct", 0.25);
        eclipsed.addProperty("edgeStepCooldownTicks", 240);
        config.add("eclipsed", eclipsed);
        
        // Enchantment-bound config
        JsonObject enchantmentBound = new JsonObject();
        enchantmentBound.addProperty("perchedHasteBonusTicks", 10);
        enchantmentBound.addProperty("mountedExtraRollsEnabled", true);
    enchantmentBound.addProperty("miningHasteBaseTicks", 40);
    enchantmentBound.addProperty("durabilityNoLossChance", 0.025);
    enchantmentBound.addProperty("extraDuplicationChanceBase", 0.05);
    enchantmentBound.addProperty("focusSurgeDurationTicks", 200);
    enchantmentBound.addProperty("focusCooldownTicks", 1200);
        config.add("enchantment_bound", enchantmentBound);
        
        // Cursed One config
        JsonObject cursedOne = new JsonObject();
        cursedOne.addProperty("doomEchoHealOnNextHitPct", 0.15);
        cursedOne.addProperty("doomEchoWeaknessDurationTicks", 60);
        config.add("cursed_one", cursedOne);
        
        // Eepy Eeper config
        JsonObject eepyEeper = new JsonObject();
        eepyEeper.addProperty("perchNapExtraRadius", 1.0);
        eepyEeper.addProperty("sleepLevelUpChance", 0.5); // 50% chance to level up when sleeping
        config.add("eepy_eeper", eepyEeper);
        
        // Pet leveling config
        JsonObject petLeveling = new JsonObject();
        petLeveling.addProperty("xp_modifier", 1.0); // Pets get 1:1 XP sharing (was 0.5)
        petLeveling.addProperty("max_xp_distance", 32); // Max distance to share XP
        petLeveling.addProperty("participation_bonus", 0.5); // +50% XP for combat participation
        petLeveling.addProperty("kill_bonus", 0.25); // +25% XP for getting killing blow
        petLeveling.addProperty("adventure_bonus", 0.15); // +15% XP from mining/smelting
        petLeveling.addProperty("afk_penalty", 0.25); // 75% XP reduction when AFK
        config.add("pet_leveling", petLeveling);

        // Tribute item config
        JsonObject tributeItems = new JsonObject();
        DEFAULT_TRIBUTE_ITEMS.forEach((level, id) -> tributeItems.addProperty(String.valueOf(level), id));
        config.add("tribute_items", tributeItems);

    // Pets config (misc/global)
    JsonObject pets = new JsonObject();
    // Trusted/leash behavior is now defaulted in code; no toggles required
    config.add("pets", pets);
        
        return config;
    }
    
    public JsonObject getConfig() {
        return config;
    }
    
    public JsonObject getRoleConfig(String role) {
        if (config.has(role) && config.get(role).isJsonObject()) {
            return config.getAsJsonObject(role);
        }
        return new JsonObject();
    }
    
    public double getDouble(String role, String key, double defaultValue) {
        JsonObject roleConfig = getRoleConfig(role);
        if (roleConfig.has(key) && roleConfig.get(key).isJsonPrimitive()) {
            return roleConfig.get(key).getAsDouble();
        }
        return defaultValue;
    }
    
    public int getInt(String role, String key, int defaultValue) {
        JsonObject roleConfig = getRoleConfig(role);
        if (roleConfig.has(key) && roleConfig.get(key).isJsonPrimitive()) {
            return roleConfig.get(key).getAsInt();
        }
        return defaultValue;
    }
    
    public boolean getBoolean(String role, String key, boolean defaultValue) {
        JsonObject roleConfig = getRoleConfig(role);
        if (roleConfig.has(key) && roleConfig.get(key).isJsonPrimitive()) {
            return roleConfig.get(key).getAsBoolean();
        }
        return defaultValue;
    }
    
    public String getString(String role, String key, String defaultValue) {
        JsonObject roleConfig = getRoleConfig(role);
        if (roleConfig.has(key) && roleConfig.get(key).isJsonPrimitive()) {
            return roleConfig.get(key).getAsString();
        }
        return defaultValue;
    }
    
    /**
     * Get the configured tribute item ID for a specific level.
     */
    public String getTributeItemId(int level) {
        JsonObject tributeItems = getRoleConfig("tribute_items");
        String levelKey = String.valueOf(level);
        if (tributeItems.has(levelKey) && tributeItems.get(levelKey).isJsonPrimitive()) {
            return tributeItems.get(levelKey).getAsString();
        }
        return getDefaultTributeItemId(level);
    }
    
    /**
     * Get the default tribute item ID for a specific level.
     */
    public String getDefaultTributeItemId(int level) {
        return DEFAULT_TRIBUTE_ITEMS.getOrDefault(level, "minecraft:gold_ingot");
    }
    
    /**
     * Check if a level has a configured tribute item.
     */
    public boolean hasTributeLevel(int level) {
        JsonObject tributeItems = getRoleConfig("tribute_items");
        String levelKey = String.valueOf(level);
        return tributeItems.has(levelKey) || DEFAULT_TRIBUTE_ITEMS.containsKey(level);
    }
    
    // Petting configuration methods
    public int getPettingCooldownTicks() {
        return getInt("petting", "cooldownTicks", 200);
    }
    
    public boolean isPettingXpBonusEnabled() {
        return getBoolean("petting", "xpBonusEnabled", true);
    }
    
    public boolean isPettingRoleBoostEnabled() {
        return getBoolean("petting", "roleBoostEnabled", true);
    }
    
    public boolean isPettingHealingEnabled() {
        return getBoolean("petting", "healingEnabled", true);
    }
    
    public double getPettingBoostMultiplier() {
        JsonObject pettingConfig = getRoleConfig("petting");
        if (pettingConfig.has("boostMultiplier") && pettingConfig.get("boostMultiplier").isJsonPrimitive()) {
            return pettingConfig.get("boostMultiplier").getAsDouble();
        }
        return 1.1; // Default 10% boost
    }
    
    /**
     * Get all configured tribute levels.
     */
    public java.util.Set<Integer> getTributeLevels() {
        java.util.Set<Integer> levels = new java.util.HashSet<>(DEFAULT_TRIBUTE_ITEMS.keySet());
        
        JsonObject tributeItems = getRoleConfig("tribute_items");
        for (String key : tributeItems.keySet()) {
            try {
                int level = Integer.parseInt(key);
                levels.add(level);
            } catch (NumberFormatException e) {
                Petsplus.LOGGER.warn("Invalid tribute level key '{}' in config", key);
            }
        }
        
        return levels;
    }
    
    /**
     * Reload the configuration from disk.
     */
    public void reload() {
        Petsplus.LOGGER.info("Reloading PetsPlus configuration...");
        loadConfig();
    }
}
