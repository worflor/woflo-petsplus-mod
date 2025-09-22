package woflo.petsplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuration manager for PetsPlus mod settings. The config now mirrors the
 * datapack-driven registry identifiers so that user overrides layer cleanly on
 * top of datapack defaults.
 */
public class PetsPlusConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("petsplus.json");

    private static final String ROLES_KEY = "roles";
    private static final String ABILITIES_KEY = "abilities";

    private static final JsonObject EMPTY_OBJECT = new JsonObject();

    private static final Map<String, Identifier> LEGACY_ROLE_KEYS = Map.ofEntries(
        Map.entry("guardian", PetRoleType.GUARDIAN_ID),
        Map.entry("striker", PetRoleType.STRIKER_ID),
        Map.entry("support", PetRoleType.SUPPORT_ID),
        Map.entry("scout", PetRoleType.SCOUT_ID),
        Map.entry("skyrider", PetRoleType.SKYRIDER_ID),
        Map.entry("enchantment_bound", PetRoleType.ENCHANTMENT_BOUND_ID),
        Map.entry("cursed_one", PetRoleType.CURSED_ONE_ID),
        Map.entry("eepy_eeper", PetRoleType.EEPY_EEPER_ID),
        Map.entry("eclipsed", PetRoleType.ECLIPSED_ID)
    );

    private static final Map<Integer, Identifier> DEFAULT_TRIBUTE_ITEMS = Map.of(
        10, Identifier.of("minecraft", "gold_ingot"),
        20, Identifier.of("minecraft", "diamond"),
        30, Identifier.of("minecraft", "netherite_ingot")
    );

    private static PetsPlusConfig instance;

    private JsonObject config;
    private boolean dirty;
    private final Set<String> legacyScopeWarnings = new HashSet<>();

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
        dirty = false;
        if (Files.exists(CONFIG_PATH)) {
            try {
                String content = Files.readString(CONFIG_PATH);
                config = GSON.fromJson(content, JsonObject.class);
                if (config == null) {
                    config = createDefaultConfig();
                    dirty = true;
                }
                Petsplus.LOGGER.info("Loaded PetsPlus config from {}", CONFIG_PATH);
            } catch (IOException e) {
                Petsplus.LOGGER.error("Failed to load config file", e);
                config = createDefaultConfig();
                dirty = true;
            }
        } else {
            config = createDefaultConfig();
            dirty = true;
        }

        ensureCoreSections();
        if (migrateLegacySections(config)) {
            dirty = true;
        }

        if (dirty) {
            saveConfig();
            dirty = false;
        }

        validateOverrides();
    }

    private void ensureCoreSections() {
        if (!config.has(ROLES_KEY) || !config.get(ROLES_KEY).isJsonObject()) {
            config.add(ROLES_KEY, new JsonObject());
            dirty = true;
        }
        if (!config.has(ABILITIES_KEY) || !config.get(ABILITIES_KEY).isJsonObject()) {
            config.add(ABILITIES_KEY, new JsonObject());
            dirty = true;
        }
    }

    private boolean migrateLegacySections(JsonObject root) {
        boolean migrated = false;
        JsonObject rolesObject = root.getAsJsonObject(ROLES_KEY);
        for (Map.Entry<String, Identifier> entry : LEGACY_ROLE_KEYS.entrySet()) {
            String legacyKey = entry.getKey();
            if (root.has(legacyKey) && root.get(legacyKey).isJsonObject()) {
                JsonObject legacy = root.remove(legacyKey).getAsJsonObject();
                String identifierKey = entry.getValue().toString();
                JsonObject target = rolesObject.has(identifierKey) && rolesObject.get(identifierKey).isJsonObject()
                    ? rolesObject.getAsJsonObject(identifierKey)
                    : new JsonObject();
                mergeInto(target, legacy);
                rolesObject.add(identifierKey, target);
                Petsplus.LOGGER.warn("Migrated petsplus config section '{}' to identifier '{}'. Update custom overrides to the new schema.", legacyKey, identifierKey);
                migrated = true;
            }
        }
        return migrated;
    }

    private void validateOverrides() {
        validateIdentifiers(getRolesObject(), PetsPlusRegistries.petRoleTypeRegistry(), "role");
        validateIdentifiers(getAbilitiesObject(), PetsPlusRegistries.abilityTypeRegistry(), "ability");
    }

    private <T> void validateIdentifiers(JsonObject overrides, Registry<T> registry, String type) {
        if (overrides == null) {
            return;
        }
        for (String key : overrides.keySet()) {
            Identifier id = Identifier.tryParse(key);
            if (id == null) {
                Petsplus.LOGGER.warn("PetsPlus config contains {} override '{}' that is not a valid identifier; ignoring.", type, key);
                continue;
            }
            if (!registry.containsId(id)) {
                Petsplus.LOGGER.warn("PetsPlus config references {} identifier '{}' that is not present in the registry. Overrides will be ignored until a datapack registers it.", type, id);
            }
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
        JsonObject root = new JsonObject();

        JsonObject roles = new JsonObject();
        roles.add(PetRoleType.GUARDIAN_ID.toString(), createGuardianDefaults());
        roles.add(PetRoleType.STRIKER_ID.toString(), createStrikerDefaults());
        roles.add(PetRoleType.SUPPORT_ID.toString(), createSupportDefaults());
        roles.add(PetRoleType.SCOUT_ID.toString(), createScoutDefaults());
        roles.add(PetRoleType.SKYRIDER_ID.toString(), createSkyriderDefaults());
        roles.add(PetRoleType.ENCHANTMENT_BOUND_ID.toString(), createEnchantmentDefaults());
        roles.add(PetRoleType.CURSED_ONE_ID.toString(), createCursedDefaults());
        roles.add(PetRoleType.EEPY_EEPER_ID.toString(), createEepyDefaults());
        roles.add(PetRoleType.ECLIPSED_ID.toString(), createEclipsedDefaults());
        root.add(ROLES_KEY, roles);

        root.add(ABILITIES_KEY, new JsonObject());

        root.add("petting", createPettingDefaults());
        root.add("pet_leveling", createPetLevelingDefaults());
        root.add("tribute_items", createDefaultTributeJson());
        root.add("pets", new JsonObject());
        root.add("visuals", createVisualDefaults());

        return root;
    }

    private static JsonObject createGuardianDefaults() {
        JsonObject guardian = new JsonObject();
        guardian.addProperty("projectileDrOnRedirectPct", 0.10);
        guardian.addProperty("shieldBashIcdTicks", 120);
        guardian.add("tribute_items", createDefaultTributeJson());
        return guardian;
    }

    private static JsonObject createStrikerDefaults() {
        JsonObject striker = new JsonObject();
        striker.addProperty("executeThresholdPct", 0.35);
        striker.addProperty("executeChainBonusPerStackPct", 0.02);
        striker.addProperty("executeChainMaxStacks", 5);
        striker.addProperty("executeChainDurationTicks", 60);
        striker.addProperty("finisherMarkBonusPct", 0.20);
        striker.addProperty("finisherMarkDurationTicks", 80);
        striker.add("tribute_items", createDefaultTributeJson());
        return striker;
    }

    private static JsonObject createSupportDefaults() {
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
        support.add("tribute_items", createDefaultTributeJson());
        return support;
    }

    private static JsonObject createScoutDefaults() {
        JsonObject scout = new JsonObject();
        scout.addProperty("lootWispDurationTicks", 80);
        scout.add("tribute_items", createDefaultTributeJson());
        return scout;
    }

    private static JsonObject createSkyriderDefaults() {
        JsonObject skyrider = new JsonObject();
        skyrider.addProperty("ownerProjLevitateChance", 0.10);
        skyrider.addProperty("ownerProjLevitateIcdTicks", 200);
        skyrider.add("tribute_items", createDefaultTributeJson());
        return skyrider;
    }

    private static JsonObject createEnchantmentDefaults() {
        JsonObject enchantment = new JsonObject();
        enchantment.addProperty("perchedHasteBonusTicks", 10);
        enchantment.addProperty("mountedExtraRollsEnabled", true);
        enchantment.addProperty("miningHasteBaseTicks", 40);
        enchantment.addProperty("durabilityNoLossChance", 0.025);
        enchantment.addProperty("extraDuplicationChanceBase", 0.05);
        enchantment.addProperty("focusSurgeDurationTicks", 200);
        enchantment.addProperty("focusCooldownTicks", 1200);
        enchantment.add("tribute_items", createDefaultTributeJson());
        return enchantment;
    }

    private static JsonObject createCursedDefaults() {
        JsonObject cursed = new JsonObject();
        cursed.addProperty("doomEchoHealOnNextHitPct", 0.15);
        cursed.addProperty("doomEchoWeaknessDurationTicks", 60);
        cursed.add("tribute_items", createDefaultTributeJson());
        return cursed;
    }

    private static JsonObject createEepyDefaults() {
        JsonObject eepy = new JsonObject();
        eepy.addProperty("perchNapExtraRadius", 1.0);
        eepy.addProperty("sleepLevelUpChance", 0.5);
        eepy.add("tribute_items", createDefaultTributeJson());
        return eepy;
    }

    private static JsonObject createEclipsedDefaults() {
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
        eclipsed.add("tribute_items", createDefaultTributeJson());
        return eclipsed;
    }

    private static JsonObject createPettingDefaults() {
        JsonObject petting = new JsonObject();
        petting.addProperty("cooldownTicks", 200);
        petting.addProperty("xpBonusEnabled", true);
        petting.addProperty("roleBoostEnabled", false);
        petting.addProperty("healingEnabled", false);
        petting.addProperty("boostMultiplier", 1.0);
        return petting;
    }

    private static JsonObject createPetLevelingDefaults() {
        JsonObject leveling = new JsonObject();
        leveling.addProperty("xp_modifier", 1.0);
        leveling.addProperty("max_xp_distance", 32);
        leveling.addProperty("participation_bonus", 0.5);
        leveling.addProperty("kill_bonus", 0.25);
        leveling.addProperty("adventure_bonus", 0.15);
        leveling.addProperty("afk_penalty", 0.25);
        return leveling;
    }

    private static JsonObject createVisualDefaults() {
        JsonObject visuals = new JsonObject();
        JsonObject styles = new JsonObject();

        JsonObject defaultAfterimage = new JsonObject();
        defaultAfterimage.addProperty("block_id", "minecraft:black_stained_glass");
        defaultAfterimage.addProperty("encase_ticks", 25);
        defaultAfterimage.addProperty("column_count", 6);
        defaultAfterimage.addProperty("vertical_slices", 5);
        defaultAfterimage.addProperty("initial_shard_count", 16);
        defaultAfterimage.addProperty("shimmer_count", 8);
        defaultAfterimage.addProperty("shimmer_interval", 5);
        defaultAfterimage.addProperty("burst_particle_count", 28);
        defaultAfterimage.addProperty("burst_speed", 0.32);
        defaultAfterimage.addProperty("shell_offset", 0.35);
        defaultAfterimage.addProperty("shimmer_speed", 0.02);
        defaultAfterimage.addProperty("encase_shard_speed", 0.05);
        styles.add("default", defaultAfterimage);

        JsonObject cursedReanimation = new JsonObject();
        cursedReanimation.addProperty("block_id", "minecraft:black_stained_glass");
        cursedReanimation.addProperty("encase_ticks", 28);
        cursedReanimation.addProperty("column_count", 7);
        cursedReanimation.addProperty("vertical_slices", 6);
        cursedReanimation.addProperty("initial_shard_count", 20);
        cursedReanimation.addProperty("shimmer_count", 10);
        cursedReanimation.addProperty("shimmer_interval", 4);
        cursedReanimation.addProperty("burst_particle_count", 36);
        cursedReanimation.addProperty("burst_speed", 0.36);
        cursedReanimation.addProperty("shell_offset", 0.4);
        cursedReanimation.addProperty("shimmer_speed", 0.025);
        cursedReanimation.addProperty("encase_shard_speed", 0.06);
        styles.add("cursed_reanimation", cursedReanimation);

        visuals.add("afterimage_styles", styles);
        return visuals;
    }

    private static JsonObject createDefaultTributeJson() {
        JsonObject tributes = new JsonObject();
        DEFAULT_TRIBUTE_ITEMS.forEach((level, id) -> tributes.addProperty(String.valueOf(level), id.toString()));
        return tributes;
    }

    private static void mergeInto(JsonObject target, JsonObject additions) {
        for (Map.Entry<String, JsonElement> entry : additions.entrySet()) {
            target.add(entry.getKey(), entry.getValue());
        }
    }

    private JsonObject getRolesObject() {
        return config.has(ROLES_KEY) && config.get(ROLES_KEY).isJsonObject()
            ? config.getAsJsonObject(ROLES_KEY)
            : null;
    }

    private JsonObject getAbilitiesObject() {
        return config.has(ABILITIES_KEY) && config.get(ABILITIES_KEY).isJsonObject()
            ? config.getAsJsonObject(ABILITIES_KEY)
            : null;
    }

    public JsonObject getConfig() {
        return config;
    }

    public JsonObject getSection(String key) {
        JsonObject section = getObject(config, key);
        return section != null ? section : EMPTY_OBJECT;
    }

    public JsonObject getRoleOverrides(Identifier roleId) {
        return lookupRoleOverrides(roleId);
    }

    public JsonObject getAbilityOverrides(Identifier abilityId) {
        return lookupAbilityOverrides(abilityId);
    }

    public double getRoleDouble(Identifier roleId, String key, double defaultValue) {
        return readDouble(lookupRoleOverrides(roleId), key, defaultValue);
    }

    public int getRoleInt(Identifier roleId, String key, int defaultValue) {
        return readInt(lookupRoleOverrides(roleId), key, defaultValue);
    }

    public boolean getRoleBoolean(Identifier roleId, String key, boolean defaultValue) {
        return readBoolean(lookupRoleOverrides(roleId), key, defaultValue);
    }

    public String getRoleString(Identifier roleId, String key, String defaultValue) {
        return readString(lookupRoleOverrides(roleId), key, defaultValue);
    }

    public double getAbilityDouble(Identifier abilityId, String key, double defaultValue) {
        return readDouble(lookupAbilityOverrides(abilityId), key, defaultValue);
    }

    public double getSectionDouble(String section, String key, double defaultValue) {
        return readDouble(getSection(section), key, defaultValue);
    }

    public int getSectionInt(String section, String key, int defaultValue) {
        return readInt(getSection(section), key, defaultValue);
    }

    public boolean getSectionBoolean(String section, String key, boolean defaultValue) {
        return readBoolean(getSection(section), key, defaultValue);
    }

    public String getSectionString(String section, String key, String defaultValue) {
        return readString(getSection(section), key, defaultValue);
    }

    @Deprecated
    public double getDouble(String section, String key, double defaultValue) {
        return getSectionDouble(section, key, defaultValue);
    }

    @Deprecated
    public int getInt(String section, String key, int defaultValue) {
        return getSectionInt(section, key, defaultValue);
    }

    @Deprecated
    public boolean getBoolean(String section, String key, boolean defaultValue) {
        return getSectionBoolean(section, key, defaultValue);
    }

    @Deprecated
    public String getString(String section, String key, String defaultValue) {
        return getSectionString(section, key, defaultValue);
    }

    public double resolveScopedDouble(String scope, String key, double defaultValue) {
        Identifier id = Identifier.tryParse(scope);
        if (id != null) {
            return readDouble(getIdentifierOverrides(id), key, defaultValue);
        }

        Identifier legacy = LEGACY_ROLE_KEYS.get(scope);
        if (legacy != null) {
            warnLegacyScope(scope, legacy);
            return readDouble(lookupRoleOverrides(legacy), key, defaultValue);
        }

        return getSectionDouble(scope, key, defaultValue);
    }

    public int resolveScopedInt(String scope, String key, int defaultValue) {
        Identifier id = Identifier.tryParse(scope);
        if (id != null) {
            return readInt(getIdentifierOverrides(id), key, defaultValue);
        }

        Identifier legacy = LEGACY_ROLE_KEYS.get(scope);
        if (legacy != null) {
            warnLegacyScope(scope, legacy);
            return readInt(lookupRoleOverrides(legacy), key, defaultValue);
        }

        return getSectionInt(scope, key, defaultValue);
    }

    public Identifier resolveTributeItem(PetRoleType roleType, int level) {
        Identifier override = findRoleTributeOverride(roleType != null ? roleType.id() : null, level);
        if (override != null) {
            return override;
        }

        if (roleType != null) {
            Identifier datapackDefault = roleType.tributeDefaults().itemForLevel(level);
            if (datapackDefault != null) {
                return datapackDefault;
            }
        }

        Identifier globalOverride = getGlobalTributeOverride(level);
        if (globalOverride != null) {
            return globalOverride;
        }

        return DEFAULT_TRIBUTE_ITEMS.get(level);
    }

    public boolean hasTributeLevel(PetRoleType roleType, int level) {
        return resolveTributeItem(roleType, level) != null;
    }

    public Identifier getFallbackTributeItem(int level) {
        return DEFAULT_TRIBUTE_ITEMS.get(level);
    }

    public int getPettingCooldownTicks() {
        return getSectionInt("petting", "cooldownTicks", 200);
    }

    public boolean isPettingXpBonusEnabled() {
        return getSectionBoolean("petting", "xpBonusEnabled", true);
    }

    public boolean isPettingRoleBoostEnabled() {
        return getSectionBoolean("petting", "roleBoostEnabled", true);
    }

    public boolean isPettingHealingEnabled() {
        return getSectionBoolean("petting", "healingEnabled", true);
    }

    public double getPettingBoostMultiplier() {
        return getSectionDouble("petting", "boostMultiplier", 1.1);
    }

    public void reload() {
        Petsplus.LOGGER.info("Reloading PetsPlus configuration...");
        loadConfig();
    }

    private JsonObject lookupRoleOverrides(Identifier roleId) {
        if (roleId == null) {
            return EMPTY_OBJECT;
        }
        JsonObject roles = getRolesObject();
        if (roles == null) {
            return EMPTY_OBJECT;
        }
        JsonElement element = roles.get(roleId.toString());
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : EMPTY_OBJECT;
    }

    private JsonObject lookupAbilityOverrides(Identifier abilityId) {
        if (abilityId == null) {
            return EMPTY_OBJECT;
        }
        JsonObject abilities = getAbilitiesObject();
        if (abilities == null) {
            return EMPTY_OBJECT;
        }
        JsonElement element = abilities.get(abilityId.toString());
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : EMPTY_OBJECT;
    }

    private JsonObject getIdentifierOverrides(Identifier id) {
        JsonObject roleOverrides = lookupRoleOverrides(id);
        if (roleOverrides != EMPTY_OBJECT) {
            return roleOverrides;
        }
        JsonObject abilityOverrides = lookupAbilityOverrides(id);
        return abilityOverrides != EMPTY_OBJECT ? abilityOverrides : EMPTY_OBJECT;
    }

    public Identifier getRoleTributeOverride(Identifier roleId, int level) {
        return findRoleTributeOverride(roleId, level);
    }

    private Identifier findRoleTributeOverride(Identifier roleId, int level) {
        if (roleId == null) {
            return null;
        }
        JsonObject role = lookupRoleOverrides(roleId);
        JsonObject tributes = getObject(role, "tribute_items");
        if (tributes == null) {
            return null;
        }
        JsonElement element = tributes.get(Integer.toString(level));
        return parseIdentifier(element);
    }

    private Identifier getGlobalTributeOverride(int level) {
        JsonObject global = getSection("tribute_items");
        if (global == EMPTY_OBJECT) {
            return null;
        }
        JsonElement element = global.get(Integer.toString(level));
        return parseIdentifier(element);
    }

    private static Identifier parseIdentifier(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String raw = element.getAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            Petsplus.LOGGER.warn("Invalid identifier '{}' in petsplus configuration", raw);
        }
        return id;
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        if (parent == null) {
            return null;
        }
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static double readDouble(JsonObject json, String key, double defaultValue) {
        if (json == null || json == EMPTY_OBJECT) {
            return defaultValue;
        }
        JsonElement element = json.get(key);
        if (element == null) {
            return defaultValue;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsDouble();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            try {
                return Double.parseDouble(element.getAsString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static int readInt(JsonObject json, String key, int defaultValue) {
        if (json == null || json == EMPTY_OBJECT) {
            return defaultValue;
        }
        JsonElement element = json.get(key);
        if (element == null) {
            return defaultValue;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            try {
                return Integer.parseInt(element.getAsString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static boolean readBoolean(JsonObject json, String key, boolean defaultValue) {
        if (json == null || json == EMPTY_OBJECT) {
            return defaultValue;
        }
        JsonElement element = json.get(key);
        if (element == null) {
            return defaultValue;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return Boolean.parseBoolean(element.getAsString());
        }
        return defaultValue;
    }

    private static String readString(JsonObject json, String key, String defaultValue) {
        if (json == null || json == EMPTY_OBJECT) {
            return defaultValue;
        }
        JsonElement element = json.get(key);
        if (element == null) {
            return defaultValue;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        return defaultValue;
    }

    private void warnLegacyScope(String legacyScope, Identifier newIdentifier) {
        if (legacyScopeWarnings.add(legacyScope)) {
            Petsplus.LOGGER.warn("Legacy config scope '{}' detected; automatically redirecting to identifier '{}'. Update datapacks and configs to reference identifiers directly.", legacyScope, newIdentifier);
        }
    }
}
