package woflo.petsplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Configuration manager for PetsPlus mod settings. The config now mirrors the
 * datapack-driven registry identifiers so that user overrides layer cleanly on
 * top of datapack defaults.
 */
public class PetsPlusConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("petsplus.json");
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("petsplus");
    private static final Path CORE_CONFIG_PATH = CONFIG_DIR.resolve("core.json");
    private static final Path ROLES_DIR = CONFIG_DIR.resolve("roles");

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
        30, Identifier.of("minecraft", "netherite_scrap")
    );

    private static PetsPlusConfig instance;

    private JsonObject config;
    private volatile int configGeneration;
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
        ensureConfigDirectories();
        migrateLegacyConfigIfNeeded();

        JsonObject core = loadCoreConfig();
        boolean coreDirty = false;

        Map<Identifier, JsonObject> roleConfigs = loadRoleConfigs();
        Map<Identifier, JsonObject> extracted = extractRolesFromCore(core);
        if (!extracted.isEmpty()) {
            coreDirty = true;
            extracted.forEach((id, legacy) -> {
                JsonObject merged = roleConfigs.computeIfAbsent(id, key -> new JsonObject());
                mergeInto(merged, legacy);
                writeRoleConfig(id, merged);
            });
        }

        if (ensureCoreSections(core)) {
            coreDirty = true;
        }

        if (coreDirty) {
            writeJsonFile(CORE_CONFIG_PATH, core, "core");
        }

        ensureRoleFilesForRegistry(roleConfigs);

        config = core.deepCopy();
        JsonObject rolesObject = new JsonObject();
        roleConfigs.forEach((id, json) -> rolesObject.add(id.toString(), json));
        config.add(ROLES_KEY, rolesObject);

        validateOverrides();

        configGeneration++;
    }

    private LoadResult readJsonObject(Path path, String description) {
        if (!Files.exists(path)) {
            return new LoadResult(null, LoadFailure.MISSING);
        }

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            JsonElement element = GSON.fromJson(reader, JsonElement.class);
            if (element == null || element.isJsonNull()) {
                Petsplus.LOGGER.warn("PetsPlus {} {} was empty; regenerating defaults.", description, path);
                return new LoadResult(null, LoadFailure.EMPTY);
            }
            if (!element.isJsonObject()) {
                Petsplus.LOGGER.warn("PetsPlus {} {} must be a JSON object. Leaving it untouched and using built-in defaults until it is corrected.", description, path);
                return new LoadResult(null, LoadFailure.NOT_OBJECT);
            }

            Petsplus.LOGGER.info("Loaded PetsPlus {} from {}", description, path);
            return new LoadResult(element.getAsJsonObject(), LoadFailure.NONE);
        } catch (JsonParseException e) {
            Petsplus.LOGGER.error("PetsPlus {} {} is malformed JSON. Leaving it untouched and using built-in defaults until it is corrected.", description, path, e);
            return new LoadResult(null, LoadFailure.MALFORMED);
        } catch (IOException e) {
            Petsplus.LOGGER.error("Failed to read PetsPlus {} {}; using built-in defaults in memory and leaving the file untouched.", description, path, e);
            return new LoadResult(null, LoadFailure.IO_ERROR);
        }
    }

    private record LoadResult(JsonObject config, LoadFailure failure) {
        boolean shouldWriteDefaults() {
            return failure == LoadFailure.MISSING || failure == LoadFailure.EMPTY;
        }
    }

    private enum LoadFailure {
        NONE,
        MISSING,
        EMPTY,
        NOT_OBJECT,
        MALFORMED,
        IO_ERROR
    }

    private boolean ensureCoreSections(JsonObject core) {
        boolean changed = false;
        if (!core.has(ABILITIES_KEY) || !core.get(ABILITIES_KEY).isJsonObject()) {
            core.add(ABILITIES_KEY, new JsonObject());
            changed = true;
        }
        if (!core.has("petting") || !core.get("petting").isJsonObject()) {
            core.add("petting", createPettingDefaults());
            changed = true;
        }
        if (!core.has("pet_leveling") || !core.get("pet_leveling").isJsonObject()) {
            core.add("pet_leveling", createPetLevelingDefaults());
            changed = true;
        }
        if (!core.has("tribute_items") || !core.get("tribute_items").isJsonObject()) {
            core.add("tribute_items", createDefaultTributeJson());
            changed = true;
        }
        if (!core.has("pets") || !core.get("pets").isJsonObject()) {
            core.add("pets", new JsonObject());
            changed = true;
        }
        if (!core.has("visuals") || !core.get("visuals").isJsonObject()) {
            core.add("visuals", createVisualDefaults());
            changed = true;
        }
        if (!core.has("emotion_cues") || !core.get("emotion_cues").isJsonObject()) {
            core.add("emotion_cues", createEmotionCueDefaults());
            changed = true;
        }
        if (!core.has("action_bar") || !core.get("action_bar").isJsonObject()) {
            core.add("action_bar", createActionBarDefaults());
            changed = true;
        }
        if (!core.has("tribute_orbital") || !core.get("tribute_orbital").isJsonObject()) {
            core.add("tribute_orbital", createTributeOrbitalDefaults());
            changed = true;
        }
        if (!core.has("named_attributes") || !core.get("named_attributes").isJsonObject()) {
            core.add("named_attributes", createNamedAttributesDefaults());
            changed = true;
        }
        if (!core.has("leash_trading") || !core.get("leash_trading").isJsonObject()) {
            core.add("leash_trading", createLeashTradingDefaults());
            changed = true;
        }
        return changed;
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

    private JsonObject createDefaultCoreConfig() {
        JsonObject root = new JsonObject();
        root.add(ABILITIES_KEY, new JsonObject());
        root.add("petting", createPettingDefaults());
        root.add("pet_leveling", createPetLevelingDefaults());
        root.add("tribute_items", createDefaultTributeJson());
        root.add("pets", new JsonObject());
        root.add("visuals", createVisualDefaults());
        root.add("emotion_cues", createEmotionCueDefaults());
        root.add("action_bar", createActionBarDefaults());
        root.add("named_attributes", createNamedAttributesDefaults());
        return root;
    }

    private void ensureConfigDirectories() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.createDirectories(ROLES_DIR);
        } catch (IOException e) {
            Petsplus.LOGGER.error("Failed to prepare PetsPlus config directories under {}", CONFIG_DIR, e);
        }
    }

    private void migrateLegacyConfigIfNeeded() {
        if (!Files.exists(LEGACY_CONFIG_PATH)) {
            return;
        }
        if (Files.exists(CORE_CONFIG_PATH) || hasAnyRoleFiles()) {
            return;
        }

        LoadResult result = readJsonObject(LEGACY_CONFIG_PATH, "config");
        if (result.config() == null) {
            return;
        }

        JsonObject legacyRoot = result.config();
        Map<Identifier, JsonObject> roles = extractRolesFromCore(legacyRoot);
        JsonObject core = legacyRoot;

        writeJsonFile(CORE_CONFIG_PATH, core, "core");
        roles.forEach(this::writeRoleConfig);

        Path backup = LEGACY_CONFIG_PATH.resolveSibling("petsplus.json.legacy");
        try {
            Files.move(LEGACY_CONFIG_PATH, backup, StandardCopyOption.REPLACE_EXISTING);
            Petsplus.LOGGER.info("Migrated PetsPlus config to {} (legacy file backed up to {}).", CONFIG_DIR, backup);
        } catch (IOException e) {
            Petsplus.LOGGER.warn("Migrated PetsPlus config to multi-file layout but failed to back up legacy file {}.", LEGACY_CONFIG_PATH, e);
        }
    }

    private boolean hasAnyRoleFiles() {
        if (!Files.exists(ROLES_DIR)) {
            return false;
        }
        try (Stream<Path> stream = Files.walk(ROLES_DIR)) {
            return stream.anyMatch(path -> Files.isRegularFile(path) && path.toString().endsWith(".json"));
        } catch (IOException e) {
            Petsplus.LOGGER.error("Failed to inspect PetsPlus role overrides under {}", ROLES_DIR, e);
            return false;
        }
    }

    private JsonObject loadCoreConfig() {
        LoadResult result = readJsonObject(CORE_CONFIG_PATH, "core config");
        if (result.config() != null) {
            return result.config();
        }

        JsonObject defaults = createDefaultCoreConfig();
        if (result.shouldWriteDefaults()) {
            writeJsonFile(CORE_CONFIG_PATH, defaults, "core");
        } else {
            Petsplus.LOGGER.warn("Using in-memory PetsPlus core defaults; fix or delete {} to restore saved overrides.", CORE_CONFIG_PATH);
        }
        return defaults;
    }

    private Map<Identifier, JsonObject> loadRoleConfigs() {
        Map<Identifier, JsonObject> roles = new LinkedHashMap<>();
        if (!Files.exists(ROLES_DIR)) {
            return roles;
        }

        try (Stream<Path> stream = Files.walk(ROLES_DIR)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> {
                    Identifier id = identifierFromRolePath(path);
                    if (id == null) {
                        Petsplus.LOGGER.warn("Ignoring PetsPlus role override with unparseable identifier at {}", path);
                        return;
                    }
                    LoadResult result = readJsonObject(path, "role config for " + id);
                    JsonObject json = result.config();
                    if (json != null) {
                        roles.put(id, json);
                    } else {
                        JsonObject defaults = createDefaultRoleConfig(id);
                        roles.put(id, defaults);
                        if (result.shouldWriteDefaults()) {
                            writeRoleConfig(id, defaults);
                        } else {
                            Petsplus.LOGGER.warn("Using in-memory defaults for role {} while {} remains unreadable.", id, path);
                        }
                    }
                });
        } catch (IOException e) {
            Petsplus.LOGGER.error("Failed to read PetsPlus role overrides from {}", ROLES_DIR, e);
        }
        return roles;
    }

    private Map<Identifier, JsonObject> extractRolesFromCore(JsonObject source) {
        Map<Identifier, JsonObject> extracted = new LinkedHashMap<>();
        if (source == null) {
            return extracted;
        }

        JsonElement rolesElement = source.remove(ROLES_KEY);
        if (rolesElement != null && rolesElement.isJsonObject()) {
            JsonObject rolesObj = rolesElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : rolesObj.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                Identifier id = parseRoleIdentifier(entry.getKey());
                if (id == null) {
                    continue;
                }
                JsonObject role = entry.getValue().getAsJsonObject();
                extracted.merge(id, role, (existing, addition) -> {
                    mergeInto(existing, addition);
                    return existing;
                });
            }
        }

        for (Map.Entry<String, Identifier> entry : LEGACY_ROLE_KEYS.entrySet()) {
            String legacyKey = entry.getKey();
            if (!source.has(legacyKey)) {
                continue;
            }
            JsonElement element = source.remove(legacyKey);
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            Identifier id = entry.getValue();
            warnLegacyScope(legacyKey, id);
            JsonObject legacy = element.getAsJsonObject();
            extracted.merge(id, legacy, (existing, addition) -> {
                mergeInto(existing, addition);
                return existing;
            });
        }

        return extracted;
    }

    private Identifier parseRoleIdentifier(String key) {
        Identifier id = Identifier.tryParse(key);
        if (id != null) {
            return id;
        }
        Identifier legacy = LEGACY_ROLE_KEYS.get(key);
        if (legacy != null) {
            warnLegacyScope(key, legacy);
        } else {
            Petsplus.LOGGER.warn("Ignoring PetsPlus config entry '{}' because it is not a valid identifier.", key);
        }
        return legacy;
    }

    private void ensureRoleFilesForRegistry(Map<Identifier, JsonObject> roles) {
        Registry<PetRoleType> registry = PetsPlusRegistries.petRoleTypeRegistry();
        for (Identifier id : registry.getIds()) {
            JsonObject overrides = roles.get(id);
            Path path = resolveRolePath(id);
            if (overrides == null) {
                JsonObject defaults = createDefaultRoleConfig(id);
                roles.put(id, defaults);
                writeRoleConfig(id, defaults);
            } else if (!Files.exists(path)) {
                writeRoleConfig(id, overrides);
            }
        }
    }

    public static void onRoleRegistered(Identifier roleId) {
        PetsPlusConfig current = instance;
        if (current != null) {
            current.ensureRoleConfigFile(roleId);
        }
    }

    private synchronized void ensureRoleConfigFile(Identifier roleId) {
        Path path = resolveRolePath(roleId);
        if (!Files.exists(path)) {
            JsonObject defaults = createDefaultRoleConfig(roleId);
            writeRoleConfig(roleId, defaults);
            updateInMemoryRoleOverrides(roleId, defaults);
            return;
        }

        // Ensure the in-memory snapshot knows about the newly registered role.
        if (config != null) {
            LoadResult result = readJsonObject(path, "role config for " + roleId);
            JsonObject json = result.config();
            updateInMemoryRoleOverrides(roleId, json != null ? json : new JsonObject());
        }
    }

    public synchronized void regenerateRoleConfig(Identifier roleId) {
        Path path = resolveRolePath(roleId);
        if (Files.exists(path)) {
            backupRoleConfig(path);
        }

        JsonObject defaults = createDefaultRoleConfig(roleId);
        writeRoleConfig(roleId, defaults);
        updateInMemoryRoleOverrides(roleId, defaults);
        Petsplus.LOGGER.info("Regenerated PetsPlus role config template for {}", roleId);
    }

    private void updateInMemoryRoleOverrides(Identifier roleId, JsonObject overrides) {
        if (config == null) {
            return;
        }
        JsonObject rolesObject = getRolesObject();
        if (rolesObject == null) {
            rolesObject = new JsonObject();
            config.add(ROLES_KEY, rolesObject);
        }
        rolesObject.add(roleId.toString(), overrides != null ? overrides.deepCopy() : new JsonObject());
    }

    private JsonObject createDefaultRoleConfig(Identifier roleId) {
        Registry<PetRoleType> registry = PetsPlusRegistries.petRoleTypeRegistry();
        PetRoleType roleType = registry.get(roleId);
        return RoleConfigTemplate.fromRole(roleId, roleType);
    }

    private void writeRoleConfig(Identifier roleId, JsonObject json) {
        Path path = resolveRolePath(roleId);
        writeJsonFile(path, json, "role " + roleId);
    }

    private Path resolveRolePath(Identifier roleId) {
        return ROLES_DIR.resolve(roleId.getNamespace()).resolve(roleId.getPath() + ".json");
    }


    private Identifier identifierFromRolePath(Path path) {
        Path relative = ROLES_DIR.relativize(path);
        int count = relative.getNameCount();
        if (count < 2) {
            return null;
        }
        String namespace = relative.getName(0).toString();
        Path remainder = relative.subpath(1, count);
        String filename = remainder.getFileName().toString();
        if (!filename.endsWith(".json")) {
            return null;
        }
        String withoutExtension = remainder.toString().substring(0, remainder.toString().length() - 5);
        String identifierPath = withoutExtension.replace(File.separatorChar, '/');
        return Identifier.tryParse(namespace + ":" + identifierPath);
    }

    private void backupRoleConfig(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path backup = path.resolveSibling(path.getFileName().toString() + ".bak");
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            Petsplus.LOGGER.info("Backed up PetsPlus role config {} to {}", path, backup);
        } catch (IOException e) {
            Petsplus.LOGGER.warn("Failed to back up PetsPlus role config at {}", path, e);
        }
    }

    private void writeJsonFile(Path path, JsonObject json, String description) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, GSON.toJson(json));
            Petsplus.LOGGER.info("Saved PetsPlus {} to {}", description, path);
        } catch (IOException e) {
            Petsplus.LOGGER.error("Failed to save PetsPlus {} at {}", description, path, e);
        }
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

    private static JsonObject createEmotionCueDefaults() {
        JsonObject cues = new JsonObject();
        cues.addProperty("mode", "immersive");
        cues.addProperty("min_delta_override", -1.0);
        cues.addProperty("digest_window_override", -1);
        cues.addProperty("hud_pulse", true);
        cues.addProperty("debug_overlay", false);
        return cues;
    }

    private static JsonObject createActionBarDefaults() {
        JsonObject actionBar = new JsonObject();
        actionBar.addProperty("recent_pet_limit", 1);
        return actionBar;
    }

    private static JsonObject createTributeOrbitalDefaults() {
        JsonObject orbital = new JsonObject();
        orbital.addProperty("enabled", true);
        orbital.addProperty("intensity_multiplier", 1.0);
        orbital.addProperty("visibility_range", 32.0);
        orbital.addProperty("combat_pause", true);
        orbital.addProperty("time_scale", 0.05);

        // Orbital configurations for each level
        JsonObject levelConfigs = new JsonObject();

        // Level 10 configuration
        JsonObject level10 = new JsonObject();
        level10.addProperty("radius_base", 1.5);
        level10.addProperty("particle_count", 6);
        level10.addProperty("has_trails", false);
        level10.addProperty("inclination_angles", "0");
        levelConfigs.add("10", level10);

        // Level 20 configuration
        JsonObject level20 = new JsonObject();
        level20.addProperty("radius_base", 1.5);
        level20.addProperty("particle_count", 8);
        level20.addProperty("has_trails", true);
        level20.addProperty("inclination_angles", "0,45");
        levelConfigs.add("20", level20);

        // Level 30 configuration
        JsonObject level30 = new JsonObject();
        level30.addProperty("radius_base", 1.5);
        level30.addProperty("particle_count", 12);
        level30.addProperty("has_trails", true);
        level30.addProperty("inclination_angles", "0,45,90");
        level30.addProperty("burst_effects", true);
        levelConfigs.add("30", level30);

        orbital.add("level_configs", levelConfigs);
        return orbital;
    }

    private static JsonObject createDefaultTributeJson() {
        JsonObject tributes = new JsonObject();
        DEFAULT_TRIBUTE_ITEMS.forEach((level, id) -> tributes.addProperty(String.valueOf(level), id.toString()));
        return tributes;
    }

    private static JsonObject createNamedAttributesDefaults() {
        JsonObject namedAttributes = new JsonObject();
        namedAttributes.addProperty("enabled", true);
        namedAttributes.addProperty("max_attributes", 5);
        namedAttributes.addProperty("case_sensitive", false);

        JsonObject patterns = new JsonObject();

        // Exact patterns
        JsonObject exact = new JsonObject();
        exact.addProperty("enabled", true);
        patterns.add("exact", exact);

        // Prefix patterns
        JsonObject prefix = new JsonObject();
        prefix.addProperty("enabled", true);
        patterns.add("prefix", prefix);

        // Regex patterns
        JsonObject regex = new JsonObject();
        regex.addProperty("enabled", false);
        patterns.add("regex", regex);

        namedAttributes.add("patterns", patterns);
        return namedAttributes;
    }

    private static JsonObject createLeashTradingDefaults() {
        JsonObject leashTrading = new JsonObject();
        leashTrading.addProperty("enabled", true);
        leashTrading.addProperty("max_distance", 15.0);
        leashTrading.addProperty("max_eligible_pets", 10);
        leashTrading.addProperty("require_sneak", true);
        leashTrading.addProperty("consume_lead", true);
        leashTrading.addProperty("mood_impact", true);
        leashTrading.addProperty("role_compatibility_check", true);
        return leashTrading;
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

    public int getConfigGeneration() {
        return configGeneration;
    }

    public String getEmotionCueMode() {
        return readString(getSection("emotion_cues"), "mode", "immersive");
    }

    public double getEmotionCueMinDeltaOverride() {
        return readDouble(getSection("emotion_cues"), "min_delta_override", -1.0);
    }

    public int getEmotionCueDigestWindowOverride() {
        return readInt(getSection("emotion_cues"), "digest_window_override", -1);
    }

    public boolean isEmotionCueHudPulseEnabled() {
        return readBoolean(getSection("emotion_cues"), "hud_pulse", true);
    }

    public boolean isEmotionCueDebugOverlayEnabled() {
        return readBoolean(getSection("emotion_cues"), "debug_overlay", false);
    }

    // Tribute Orbital Configuration
    public JsonObject getTributeOrbitalSection() {
        return getSection("tribute_orbital");
    }

    public boolean isTributeOrbitalEnabled() {
        return readBoolean(getTributeOrbitalSection(), "enabled", true);
    }

    public double getTributeOrbitalIntensityMultiplier() {
        return readDouble(getTributeOrbitalSection(), "intensity_multiplier", 1.0);
    }

    public double getTributeOrbitalVisibilityRange() {
        return readDouble(getTributeOrbitalSection(), "visibility_range", 32.0);
    }

    public boolean isTributeOrbitalCombatPauseEnabled() {
        return readBoolean(getTributeOrbitalSection(), "combat_pause", true);
    }

    public double getTributeOrbitalTimeScale() {
        return readDouble(getTributeOrbitalSection(), "time_scale", 0.05);
    }

    public JsonObject getTributeOrbitalLevelConfig(int level) {
        JsonObject levelConfigs = getObject(getTributeOrbitalSection(), "level_configs");
        if (levelConfigs == null) return EMPTY_OBJECT;

        return getObject(levelConfigs, String.valueOf(level)) != null
            ? getObject(levelConfigs, String.valueOf(level))
            : EMPTY_OBJECT;
    }

    public double getTributeOrbitalRadiusBase(int level) {
        return readDouble(getTributeOrbitalLevelConfig(level), "radius_base", 1.5);
    }

    public int getTributeOrbitalParticleCount(int level) {
        return readInt(getTributeOrbitalLevelConfig(level), "particle_count",
            level == 10 ? 6 : level == 20 ? 8 : 12);
    }

    public boolean getTributeOrbitalHasTrails(int level) {
        return readBoolean(getTributeOrbitalLevelConfig(level), "has_trails", level >= 20);
    }

    public boolean getTributeOrbitalHasBurstEffects(int level) {
        return readBoolean(getTributeOrbitalLevelConfig(level), "burst_effects", level >= 30);
    }

    // Developer Crown Configuration (Easter Egg)
    public JsonObject getDevCrownSection() {
        return getSection("dev_crown");
    }

    public boolean isDevCrownEnabled() {
        return readBoolean(getDevCrownSection(), "enabled", true);
    }

    public int getActionBarRecentPetLimit() {
        return readInt(getSection("action_bar"), "recent_pet_limit", 1);
    }

    // Named Attributes Configuration
    public JsonObject getNamedAttributesSection() {
        return getSection("named_attributes");
    }

    public boolean isNamedAttributesEnabled() {
        return readBoolean(getNamedAttributesSection(), "enabled", true);
    }

    public int getMaxNamedAttributes() {
        return readInt(getNamedAttributesSection(), "max_attributes", 5);
    }

    public boolean isNamedAttributesCaseSensitive() {
        return readBoolean(getNamedAttributesSection(), "case_sensitive", false);
    }

    public boolean isExactPatternsEnabled() {
        JsonObject patterns = getObject(getNamedAttributesSection(), "patterns");
        JsonObject exact = getObject(patterns, "exact");
        return readBoolean(exact, "enabled", true);
    }

    public boolean isPrefixPatternsEnabled() {
        JsonObject patterns = getObject(getNamedAttributesSection(), "patterns");
        JsonObject prefix = getObject(patterns, "prefix");
        return readBoolean(prefix, "enabled", true);
    }

    public boolean isRegexPatternsEnabled() {
        JsonObject patterns = getObject(getNamedAttributesSection(), "patterns");
        JsonObject regex = getObject(patterns, "regex");
        return readBoolean(regex, "enabled", false);
    }

    // Leash Trading Configuration
    public JsonObject getLeashTradingSection() {
        return getSection("leash_trading");
    }

    public boolean isLeashTradingEnabled() {
        return readBoolean(getLeashTradingSection(), "enabled", true);
    }

    public double getLeashTradingMaxDistance() {
        return readDouble(getLeashTradingSection(), "max_distance", 15.0);
    }

    public int getLeashTradingMaxEligiblePets() {
        return readInt(getLeashTradingSection(), "max_eligible_pets", 10);
    }

    public boolean isLeashTradingSneakRequired() {
        return readBoolean(getLeashTradingSection(), "require_sneak", true);
    }

    public boolean isLeashTradingLeadConsumed() {
        return readBoolean(getLeashTradingSection(), "consume_lead", true);
    }

    public boolean isLeashTradingMoodImpactEnabled() {
        return readBoolean(getLeashTradingSection(), "mood_impact", true);
    }

    public boolean isLeashTradingRoleCompatibilityCheckEnabled() {
        return readBoolean(getLeashTradingSection(), "role_compatibility_check", true);
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

    public int getPassiveAuraInterval(PetRoleType roleType, PetRoleType.PassiveAura aura) {
        return readInt(getPassiveAuraOverrides(roleType, aura), "interval", aura.intervalTicks());
    }

    public double getPassiveAuraRadius(PetRoleType roleType, PetRoleType.PassiveAura aura) {
        return readDouble(getPassiveAuraOverrides(roleType, aura), "radius", aura.radius());
    }

    public int getSupportPotionInterval(PetRoleType roleType, PetRoleType.SupportPotionBehavior behavior) {
        return readInt(getSupportPotionOverrides(roleType), "interval", behavior.intervalTicks());
    }

    public double getSupportPotionRadius(PetRoleType roleType, PetRoleType.SupportPotionBehavior behavior) {
        return readDouble(getSupportPotionOverrides(roleType), "radius", behavior.radius());
    }

    public int getSupportPotionDuration(PetRoleType roleType, PetRoleType.SupportPotionBehavior behavior) {
        return readInt(getSupportPotionOverrides(roleType), "effect_duration", behavior.effectDurationTicks());
    }

    public boolean isSupportPotionAppliedToPet(PetRoleType roleType, PetRoleType.SupportPotionBehavior behavior) {
        return readBoolean(getSupportPotionOverrides(roleType), "apply_to_pet", behavior.applyToPet());
    }

    public double getAbilityDouble(Identifier abilityId, String key, double defaultValue) {
        return readDouble(lookupAbilityOverrides(abilityId), key, defaultValue);
    }

    public int getAbilityInt(Identifier abilityId, String key, int defaultValue) {
        return readInt(lookupAbilityOverrides(abilityId), key, defaultValue);
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

    public double resolveScopedDouble(String scope, String key, double defaultValue) {
        Identifier id = Identifier.tryParse(scope);
        if (id != null) {
            if (PetsPlusRegistries.petRoleTypeRegistry().containsId(id)) {
                return getRoleDouble(id, key, defaultValue);
            }
            if (PetsPlusRegistries.abilityTypeRegistry().containsId(id)) {
                return getAbilityDouble(id, key, defaultValue);
            }
            return readDouble(getIdentifierOverrides(id), key, defaultValue);
        }

        Identifier legacy = LEGACY_ROLE_KEYS.get(scope);
        if (legacy != null) {
            warnLegacyScope(scope, legacy);
            return getRoleDouble(legacy, key, defaultValue);
        }

        return getSectionDouble(scope, key, defaultValue);
    }

    public int resolveScopedInt(String scope, String key, int defaultValue) {
        Identifier id = Identifier.tryParse(scope);
        if (id != null) {
            if (PetsPlusRegistries.petRoleTypeRegistry().containsId(id)) {
                return getRoleInt(id, key, defaultValue);
            }
            if (PetsPlusRegistries.abilityTypeRegistry().containsId(id)) {
                return getAbilityInt(id, key, defaultValue);
            }
            return readInt(getIdentifierOverrides(id), key, defaultValue);
        }

        Identifier legacy = LEGACY_ROLE_KEYS.get(scope);
        if (legacy != null) {
            warnLegacyScope(scope, legacy);
            return getRoleInt(legacy, key, defaultValue);
        }

        return getSectionInt(scope, key, defaultValue);
    }

    public Identifier resolveTributeItem(PetRoleType roleType, int level) {
        Identifier override = findRoleTributeOverride(roleType != null ? roleType.id() : null, level);
        if (override != null) {
            return override;
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

    public Map<Integer, Identifier> getResolvedGlobalTributeItems() {
        Map<Integer, Identifier> resolved = new TreeMap<>(DEFAULT_TRIBUTE_ITEMS);
        JsonObject global = getSection("tribute_items");
        if (global != EMPTY_OBJECT) {
            for (Map.Entry<String, JsonElement> entry : global.entrySet()) {
                int level;
                try {
                    level = Integer.parseInt(entry.getKey());
                } catch (NumberFormatException e) {
                    Petsplus.LOGGER.warn(
                        "PetsPlus tribute_items config has non-numeric level '{}'; ignoring.",
                        entry.getKey()
                    );
                    continue;
                }

                Identifier parsed = parseIdentifier(entry.getValue());
                if (parsed != null) {
                    resolved.put(level, parsed);
                } else {
                    Petsplus.LOGGER.warn(
                        "PetsPlus tribute_items config has invalid item id '{}' for level {}; ignoring.",
                        entry.getValue(),
                        level
                    );
                }
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(resolved));
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

    private JsonObject getPassiveAuraOverrides(PetRoleType roleType, PetRoleType.PassiveAura aura) {
        if (roleType == null || aura == null) {
            return EMPTY_OBJECT;
        }
        return getPassiveAuraOverrides(roleType.id(), aura.id());
    }

    private JsonObject getPassiveAuraOverrides(Identifier roleId, String auraId) {
        if (roleId == null || auraId == null) {
            return EMPTY_OBJECT;
        }
        JsonObject role = lookupRoleOverrides(roleId);
        JsonObject auras = getObject(role, "passive_auras");
        if (auras == null) {
            return EMPTY_OBJECT;
        }
        JsonObject aura = getObject(auras, auraId);
        return aura != null ? aura : EMPTY_OBJECT;
    }

    private JsonObject getSupportPotionOverrides(PetRoleType roleType) {
        if (roleType == null) {
            return EMPTY_OBJECT;
        }
        return getSupportPotionOverrides(roleType.id());
    }

    private JsonObject getSupportPotionOverrides(Identifier roleId) {
        if (roleId == null) {
            return EMPTY_OBJECT;
        }
        JsonObject role = lookupRoleOverrides(roleId);
        JsonObject support = getObject(role, "support_potion");
        return support != null ? support : EMPTY_OBJECT;
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
