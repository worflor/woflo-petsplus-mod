package woflo.petsplus.events;

import woflo.petsplus.api.registry.RegistryJsonHelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;

/**
 * Loads the emotion cue configuration describing per-event cooldowns, batching behaviour,
 * and the baseline emotion mixes that should be applied before contextual adjustments.
 */
public final class EmotionCueConfig {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String RESOURCE_PATH = "/assets/petsplus/configs/emotion_cues.json";

    private static EmotionCueConfig instance;

    private final Map<String, EmotionCueDefinition> definitions;
    private final Map<String, EmotionCueProfile> profiles;
    private final Map<String, CueCategory> categories;
    private final Defaults defaults;
    private final CueModeSettings mode;
    private final boolean hudPulseEnabled;
    private final boolean debugOverlay;
    private final StimulusGroup<BlockStimulus> blockBreakStimuli;
    private final StimulusGroup<BlockStimulus> blockUseStimuli;
    private final StimulusGroup<BlockStimulus> blockPlaceStimuli;
    private final StimulusGroup<ItemStimulus> itemUseStimuli;
    private final StimulusGroup<EntityStimulus> entityUseStimuli;

    private EmotionCueConfig(Map<String, EmotionCueDefinition> definitions,
                             Map<String, EmotionCueProfile> profiles,
                             Map<String, CueCategory> categories,
                             Defaults defaults,
                             CueModeSettings mode,
                             boolean hudPulseEnabled,
                             boolean debugOverlay,
                             StimulusGroup<BlockStimulus> blockBreakStimuli,
                             StimulusGroup<BlockStimulus> blockUseStimuli,
                             StimulusGroup<BlockStimulus> blockPlaceStimuli,
                             StimulusGroup<ItemStimulus> itemUseStimuli,
                             StimulusGroup<EntityStimulus> entityUseStimuli) {
        this.definitions = definitions;
        this.profiles = profiles;
        this.categories = categories;
        this.defaults = defaults;
        this.mode = mode;
        this.hudPulseEnabled = hudPulseEnabled;
        this.debugOverlay = debugOverlay;
        this.blockBreakStimuli = blockBreakStimuli;
        this.blockUseStimuli = blockUseStimuli;
        this.blockPlaceStimuli = blockPlaceStimuli;
        this.itemUseStimuli = itemUseStimuli;
        this.entityUseStimuli = entityUseStimuli;
    }

    public static EmotionCueConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    @Nullable
    public EmotionCueDefinition definition(String id) {
        return definitions.get(id);
    }

    @Nullable
    public String findBlockBreakDefinition(BlockState state) {
        return resolveBlockStimulus(blockBreakStimuli, state);
    }

    @Nullable
    public String findBlockUseDefinition(BlockState state) {
        return resolveBlockStimulus(blockUseStimuli, state);
    }

    @Nullable
    public String findBlockPlaceDefinition(BlockState state) {
        return resolveBlockStimulus(blockPlaceStimuli, state);
    }

    @Nullable
    public String findItemUseDefinition(ItemStack stack) {
        return resolveItemStimulus(itemUseStimuli, stack);
    }

    @Nullable
    public String findEntityUseDefinition(Entity entity, ItemStack stack) {
        return resolveEntityStimulus(entityUseStimuli, entity, stack);
    }

    @Nullable
    private String resolveBlockStimulus(@Nullable StimulusGroup<BlockStimulus> group, BlockState state) {
        if (group == null) {
            return null;
        }
        Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        for (BlockStimulus stimulus : group.matchers()) {
            if (stimulus.matches(state, blockId)) {
                return stimulus.definitionId();
            }
        }
        return group.defaultDefinition();
    }

    @Nullable
    private String resolveItemStimulus(@Nullable StimulusGroup<ItemStimulus> group, ItemStack stack) {
        if (group == null) {
            return null;
        }
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        for (ItemStimulus stimulus : group.matchers()) {
            if (stimulus.matches(stack, itemId)) {
                return stimulus.definitionId();
            }
        }
        return group.defaultDefinition();
    }

    @Nullable
    private String resolveEntityStimulus(@Nullable StimulusGroup<EntityStimulus> group, Entity entity, ItemStack stack) {
        if (group == null) {
            return null;
        }
        Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
        Identifier itemId = stack.isEmpty() ? null : Registries.ITEM.getId(stack.getItem());
        for (EntityStimulus stimulus : group.matchers()) {
            if (stimulus.matches(entity, entityId, stack, itemId)) {
                return stimulus.definitionId();
            }
        }
        return group.defaultDefinition();
    }

    public double fallbackRadius() {
        return defaults.radius;
    }

    public boolean hudPulseEnabled() {
        return hudPulseEnabled;
    }

    public boolean debugOverlay() {
        return debugOverlay;
    }

    public Text resolveText(String definitionId, Object... args) {
        EmotionCueDefinition definition = definition(definitionId);
        if (definition == null || definition.textKey() == null) {
            return Text.empty();
        }
        return Text.translatable(definition.textKey(), args);
    }

    public float resolveMinDelta(String definitionId) {
        EmotionCueDefinition definition = definition(definitionId);
        if (definition != null) {
            return definition.minDelta();
        }
        double override = PetsPlusConfig.getInstance().getEmotionCueMinDeltaOverride();
        if (override >= 0d) {
            return (float) override;
        }
        String categoryId = guessCategory(definitionId);
        CueCategory category = categories.get(categoryId);
        float base = category != null ? category.minDelta() : defaults.minDelta();
        return base * mode.minDeltaScale();
    }

    public boolean isForceShowEnabled() {
        return mode.forceShow();
    }

    private static EmotionCueConfig load() {
        JsonObject root = loadRoot();
        Defaults defaults = parseDefaults(root.getAsJsonObject("defaults"));
        Map<String, CueCategory> categories = parseCategories(root.getAsJsonObject("categories"), defaults);
        Map<String, EmotionCueProfile> profiles = parseProfiles(root.getAsJsonObject("profiles"));
        Map<String, CueModeSettings> modes = parseModes(root.getAsJsonObject("modes"));

        PetsPlusConfig userConfig = PetsPlusConfig.getInstance();
        String modeKey = userConfig.getEmotionCueMode();
        CueModeSettings mode = modes.getOrDefault(modeKey, modes.getOrDefault("immersive",
            new CueModeSettings(1f, 1f, 1f, 1f, false, false)));

        Map<String, EmotionCueDefinition> definitions = parseDefinitions(
            root.getAsJsonObject("events"), defaults, categories, profiles, mode, userConfig);

        JsonObject stimuliRoot = root.getAsJsonObject("stimuli");
        StimulusGroup<BlockStimulus> blockBreakStimuli = parseBlockStimulusGroup(
            stimuliRoot != null ? stimuliRoot.getAsJsonObject("block_break") : null,
            "block_break.generic");
        StimulusGroup<BlockStimulus> blockUseStimuli = parseBlockStimulusGroup(
            stimuliRoot != null ? stimuliRoot.getAsJsonObject("block_use") : null,
            null);
        StimulusGroup<BlockStimulus> blockPlaceStimuli = parseBlockStimulusGroup(
            stimuliRoot != null ? stimuliRoot.getAsJsonObject("block_place") : null,
            "block_place.generic");
        StimulusGroup<ItemStimulus> itemUseStimuli = parseItemStimulusGroup(
            stimuliRoot != null ? stimuliRoot.getAsJsonObject("item_use") : null);
        StimulusGroup<EntityStimulus> entityUseStimuli = parseEntityStimulusGroup(
            stimuliRoot != null ? stimuliRoot.getAsJsonObject("entity_use") : null);

        boolean hudPulse = userConfig.isEmotionCueHudPulseEnabled();
        boolean debugOverlay = mode.debugOverlay() || userConfig.isEmotionCueDebugOverlayEnabled();

        return new EmotionCueConfig(definitions, profiles, categories, defaults, mode, hudPulse, debugOverlay,
            blockBreakStimuli, blockUseStimuli, blockPlaceStimuli, itemUseStimuli, entityUseStimuli);
    }

    private static JsonObject loadRoot() {
        try (InputStream stream = EmotionCueConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                Petsplus.LOGGER.error("Missing emotion cue config resource {}", RESOURCE_PATH);
                return new JsonObject();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                JsonElement element = GSON.fromJson(reader, JsonElement.class);
                if (element == null || !element.isJsonObject()) {
                    Petsplus.LOGGER.error("Emotion cue config {} was not a JSON object", RESOURCE_PATH);
                    return new JsonObject();
                }
                return element.getAsJsonObject();
            }
        } catch (IOException | JsonParseException e) {
            Petsplus.LOGGER.error("Failed to load emotion cue config {}", RESOURCE_PATH, e);
            return new JsonObject();
        }
    }

    private static Defaults parseDefaults(@Nullable JsonObject object) {
        if (object == null) {
            return new Defaults(24d, 200L, 80L, 120L, 0.05f, true);
        }
        double radius = RegistryJsonHelper.getDouble(object, "radius", 24d);
        long cooldown = RegistryJsonHelper.getLong(object, "cooldown", 200L);
        long categoryCooldown = RegistryJsonHelper.getLong(object, "category_cooldown", 80L);
        long digestWindow = RegistryJsonHelper.getLong(object, "digest_window", 120L);
        float minDelta = RegistryJsonHelper.getFloat(object, "min_delta", 0.05f);
        boolean highlightHud = RegistryJsonHelper.getBoolean(object, "highlight_hud", true);
        return new Defaults(radius, cooldown, categoryCooldown, digestWindow, minDelta, highlightHud);
    }

    private static Map<String, CueCategory> parseCategories(@Nullable JsonObject object, Defaults defaults) {
        Map<String, CueCategory> categories = new HashMap<>();
        if (object == null) {
            return categories;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject json = entry.getValue().getAsJsonObject();
            String id = entry.getKey();
            long cooldown = RegistryJsonHelper.getLong(json, "cooldown", defaults.cooldown);
            long categoryCooldown = RegistryJsonHelper.getLong(json, "category_cooldown", defaults.categoryCooldown);
            long digestWindow = RegistryJsonHelper.getLong(json, "digest_window", defaults.digestWindow);
            float minDelta = RegistryJsonHelper.getFloat(json, "min_delta", defaults.minDelta);
            boolean highlight = json.has("highlight_hud") ? json.get("highlight_hud").getAsBoolean()
                : defaults.highlightHud;
            categories.put(id, new CueCategory(id, cooldown, categoryCooldown, digestWindow, minDelta, highlight));
        }
        return categories;
    }

    private static Map<String, EmotionCueProfile> parseProfiles(@Nullable JsonObject object) {
        Map<String, EmotionCueProfile> profiles = new HashMap<>();
        if (object == null) {
            return profiles;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject json = entry.getValue().getAsJsonObject();
            double radius = RegistryJsonHelper.getDouble(json, "radius", Double.NaN);
            EnumMap<PetComponent.Emotion, Float> emotions = readEmotionMap(json.getAsJsonObject("emotions"));
            profiles.put(entry.getKey(), new EmotionCueProfile(entry.getKey(), radius, emotions));
        }
        return profiles;
    }

    private static Map<String, CueModeSettings> parseModes(@Nullable JsonObject object) {
        Map<String, CueModeSettings> modes = new HashMap<>();
        if (object == null) {
            modes.put("immersive", new CueModeSettings(1f, 1f, 1f, 1f, false, false));
            modes.put("minimal", new CueModeSettings(1.8f, 1.2f, 1.8f, 1.4f, false, false));
            modes.put("debug", new CueModeSettings(0.2f, 0.2f, 0.6f, 0.0f, true, true));
            return modes;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject json = entry.getValue().getAsJsonObject();
            float cooldownScale = RegistryJsonHelper.getFloat(json, "cooldown_scale", 1f);
            float categoryScale = RegistryJsonHelper.getFloat(json, "category_cooldown_scale", cooldownScale);
            float digestScale = RegistryJsonHelper.getFloat(json, "digest_scale", 1f);
            float minDeltaScale = RegistryJsonHelper.getFloat(json, "min_delta_scale", 1f);
            boolean forceShow = RegistryJsonHelper.getBoolean(json, "force_show", false);
            boolean debugOverlay = RegistryJsonHelper.getBoolean(json, "debug_overlay", false);
            modes.put(entry.getKey(), new CueModeSettings(cooldownScale, categoryScale, digestScale,
                minDeltaScale, forceShow, debugOverlay));
        }
        return modes;
    }

    private static Map<String, EmotionCueDefinition> parseDefinitions(JsonObject object,
                                                                       Defaults defaults,
                                                                       Map<String, CueCategory> categories,
                                                                       Map<String, EmotionCueProfile> profiles,
                                                                       CueModeSettings mode,
                                                                       PetsPlusConfig userConfig) {
        Map<String, EmotionCueDefinition> map = new HashMap<>();
        if (object == null) {
            return map;
        }
        double minOverride = userConfig.getEmotionCueMinDeltaOverride();
        int digestOverride = userConfig.getEmotionCueDigestWindowOverride();

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            String id = entry.getKey();
            JsonObject json = entry.getValue().getAsJsonObject();
            String categoryId = RegistryJsonHelper.getString(json, "category", guessCategory(id));
            CueCategory category = categories.getOrDefault(categoryId,
                new CueCategory(categoryId, defaults.cooldown, defaults.categoryCooldown,
                    defaults.digestWindow, defaults.minDelta, defaults.highlightHud));

            EmotionCueProfile profile = profiles.get(json.has("profile") ? json.get("profile").getAsString() : "");
            if (profile == null) {
                profile = EmotionCueProfile.EMPTY;
            }

            long cooldown = scaleLong(RegistryJsonHelper.getLong(json, "cooldown", category.cooldownTicks()), mode.cooldownScale());
            long categoryCooldown = scaleLong(RegistryJsonHelper.getLong(json, "category_cooldown", category.categoryCooldownTicks()),
                mode.categoryCooldownScale());
            long digestWindow = scaleLong(RegistryJsonHelper.getLong(json, "digest_window", category.digestWindowTicks()),
                mode.digestScale());
            float minDelta = RegistryJsonHelper.getFloat(json, "min_delta", category.minDelta()) * mode.minDeltaScale();
            if (minOverride >= 0) {
                minDelta = (float) minOverride;
            }
            if (digestOverride > 0) {
                digestWindow = digestOverride;
            }

            boolean highlightHud = json.has("highlight_hud") ? json.get("highlight_hud").getAsBoolean()
                : category.highlightHud();
            boolean digestEnabled = RegistryJsonHelper.getBoolean(json, "digest", true);
            boolean forceShow = RegistryJsonHelper.getBoolean(json, "force_show", mode.forceShow());
            boolean alwaysJournal = RegistryJsonHelper.getBoolean(json, "journal", false);

            double radius = json.has("radius") ? json.get("radius").getAsDouble()
                : (!Double.isNaN(profile.radius()) ? profile.radius() : defaults.radius);

            EnumMap<PetComponent.Emotion, Float> base = new EnumMap<>(PetComponent.Emotion.class);
            base.putAll(profile.emotions());
            EnumMap<PetComponent.Emotion, Float> overrides = readEmotionMap(json.getAsJsonObject("emotions"));
            base.putAll(overrides);

            String textKey = json.has("text") ? json.get("text").getAsString() : null;
            String digestLabel = json.has("digest_label") ? json.get("digest_label").getAsString() : textKey;

            map.put(id, new EmotionCueDefinition(id, categoryId, cooldown, categoryCooldown, digestWindow,
                minDelta, digestEnabled, highlightHud, forceShow, alwaysJournal, radius, textKey, digestLabel,
                Collections.unmodifiableMap(base)));
        }
        return map;
    }

    private static StimulusGroup<BlockStimulus> parseBlockStimulusGroup(@Nullable JsonObject object,
                                                                        @Nullable String defaultDefinition) {
        if (object == null) {
            return new StimulusGroup<>(defaultDefinition, Collections.emptyList());
        }
        String defaultId = object.has("default") && object.get("default").isJsonPrimitive()
            ? object.get("default").getAsString()
            : defaultDefinition;
        List<BlockStimulus> matchers = new ArrayList<>();
        if (object.has("matchers") && object.get("matchers").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("matchers")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject matcher = element.getAsJsonObject();
                String definition = RegistryJsonHelper.getString(matcher, "definition", null);
                if (definition == null) {
                    continue;
                }
                List<TagKey<Block>> tags = parseBlockTags(matcher.get("block_tags"));
                Set<Identifier> blocks = parseIdentifierSet(matcher.get("blocks"));
                matchers.add(new BlockStimulus(definition, tags, blocks));
            }
        }
        return new StimulusGroup<>(defaultId, List.copyOf(matchers));
    }

    private static StimulusGroup<ItemStimulus> parseItemStimulusGroup(@Nullable JsonObject object) {
        if (object == null) {
            return new StimulusGroup<>(null, Collections.emptyList());
        }
        String defaultId = object.has("default") && object.get("default").isJsonPrimitive()
            ? object.get("default").getAsString()
            : null;
        List<ItemStimulus> matchers = new ArrayList<>();
        if (object.has("matchers") && object.get("matchers").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("matchers")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject matcher = element.getAsJsonObject();
                String definition = RegistryJsonHelper.getString(matcher, "definition", null);
                if (definition == null) {
                    continue;
                }
                boolean requiresFood = RegistryJsonHelper.getBoolean(matcher, "requires_food", false);
                List<TagKey<Item>> tags = parseItemTags(matcher.get("item_tags"));
                Set<Identifier> items = parseIdentifierSet(matcher.get("items"));
                matchers.add(new ItemStimulus(definition, requiresFood, tags, items));
            }
        }
        return new StimulusGroup<>(defaultId, List.copyOf(matchers));
    }

    private static StimulusGroup<EntityStimulus> parseEntityStimulusGroup(@Nullable JsonObject object) {
        if (object == null) {
            return new StimulusGroup<>(null, Collections.emptyList());
        }
        String defaultId = object.has("default") && object.get("default").isJsonPrimitive()
            ? object.get("default").getAsString()
            : null;
        List<EntityStimulus> matchers = new ArrayList<>();
        if (object.has("matchers") && object.get("matchers").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("matchers")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject matcher = element.getAsJsonObject();
                String definition = RegistryJsonHelper.getString(matcher, "definition", null);
                if (definition == null) {
                    continue;
                }
                List<TagKey<EntityType<?>>> entityTags = parseEntityTypeTags(matcher.get("entity_tags"));
                Set<Identifier> entityTypes = parseIdentifierSet(matcher.get("entity_types"));
                List<TagKey<Item>> itemTags = parseItemTags(matcher.get("item_tags"));
                Set<Identifier> items = parseIdentifierSet(matcher.get("items"));
                boolean requireMob = RegistryJsonHelper.getBoolean(matcher, "requires_mob", false);
                matchers.add(new EntityStimulus(definition, entityTags, entityTypes, itemTags, items, requireMob));
            }
        }
        return new StimulusGroup<>(defaultId, List.copyOf(matchers));
    }

    private static List<TagKey<Block>> parseBlockTags(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return Collections.emptyList();
        }
        List<TagKey<Block>> tags = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                continue;
            }
            Identifier id = parseIdentifier(entry.getAsString());
            if (id != null) {
                tags.add(TagKey.of(RegistryKeys.BLOCK, id));
            }
        }
        return tags;
    }

    private static List<TagKey<Item>> parseItemTags(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return Collections.emptyList();
        }
        List<TagKey<Item>> tags = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                continue;
            }
            Identifier id = parseIdentifier(entry.getAsString());
            if (id != null) {
                tags.add(TagKey.of(RegistryKeys.ITEM, id));
            }
        }
        return tags;
    }

    private static List<TagKey<EntityType<?>>> parseEntityTypeTags(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return Collections.emptyList();
        }
        List<TagKey<EntityType<?>>> tags = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                continue;
            }
            Identifier id = parseIdentifier(entry.getAsString());
            if (id != null) {
                tags.add(TagKey.of(RegistryKeys.ENTITY_TYPE, id));
            }
        }
        return tags;
    }

    private static Set<Identifier> parseIdentifierSet(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return Collections.emptySet();
        }
        Set<Identifier> ids = new HashSet<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                continue;
            }
            Identifier id = parseIdentifier(entry.getAsString());
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    @Nullable
    private static Identifier parseIdentifier(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Identifier.of(value);
        } catch (IllegalArgumentException ex) {
            Petsplus.LOGGER.warn("Invalid identifier '{}' in emotion cue config", value);
            return null;
        }
    }

    private static long scaleLong(long base, float scale) {
        return Math.max(1L, Math.round(base * scale));
    }

    private static EnumMap<PetComponent.Emotion, Float> readEmotionMap(@Nullable JsonObject object) {
        EnumMap<PetComponent.Emotion, Float> map = new EnumMap<>(PetComponent.Emotion.class);
        if (object == null) {
            return map;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                continue;
            }
            JsonPrimitive primitive = entry.getValue().getAsJsonPrimitive();
            if (!primitive.isNumber()) {
                continue;
            }
            PetComponent.Emotion emotion = parseEmotion(entry.getKey());
            if (emotion == null) {
                continue;
            }
            map.put(emotion, primitive.getAsFloat());
        }
        return map;
    }

    @Nullable
    private static PetComponent.Emotion parseEmotion(String key) {
        try {
            return PetComponent.Emotion.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            Petsplus.LOGGER.warn("Unknown emotion key '{}' in emotion cue config", key);
            return null;
        }
    }

    private static String guessCategory(String id) {
        int dot = id.indexOf('.');
        return dot > 0 ? id.substring(0, dot) : id;
    }

    private record StimulusGroup<T>(@Nullable String defaultDefinition, List<T> matchers) {}

    private record BlockStimulus(String definitionId,
                                 List<TagKey<Block>> tags,
                                 Set<Identifier> blocks) {
        boolean matches(BlockState state, @Nullable Identifier blockId) {
            boolean tagMatched = tags.isEmpty();
            if (!tagMatched) {
                for (TagKey<Block> tag : tags) {
                    if (state.isIn(tag)) {
                        tagMatched = true;
                        break;
                    }
                }
            }
            if (!tagMatched) {
                return false;
            }
            if (!blocks.isEmpty()) {
                return blockId != null && blocks.contains(blockId);
            }
            return true;
        }
    }

    private record ItemStimulus(String definitionId,
                                 boolean requiresFood,
                                 List<TagKey<Item>> tags,
                                 Set<Identifier> items) {
        boolean matches(ItemStack stack, @Nullable Identifier itemId) {
            if (stack.isEmpty()) {
                return false;
            }
            if (requiresFood && stack.get(DataComponentTypes.FOOD) == null) {
                return false;
            }
            if (!tags.isEmpty()) {
                boolean matched = false;
                for (TagKey<Item> tag : tags) {
                    if (stack.isIn(tag)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
            if (!items.isEmpty()) {
                return itemId != null && items.contains(itemId);
            }
            return true;
        }
    }

    private record EntityStimulus(String definitionId,
                                   List<TagKey<EntityType<?>>> entityTags,
                                   Set<Identifier> entityTypes,
                                   List<TagKey<Item>> itemTags,
                                   Set<Identifier> items,
                                   boolean requiresMob) {
        boolean matches(Entity entity, @Nullable Identifier entityId, ItemStack stack, @Nullable Identifier itemId) {
            if (requiresMob && !(entity instanceof MobEntity)) {
                return false;
            }
            if (!entityTags.isEmpty()) {
                boolean matched = false;
                for (TagKey<EntityType<?>> tag : entityTags) {
                    if (entity.getType().isIn(tag)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
            if (!entityTypes.isEmpty()) {
                if (entityId == null || !entityTypes.contains(entityId)) {
                    return false;
                }
            }
            if (!itemTags.isEmpty() || !items.isEmpty()) {
                if (stack.isEmpty()) {
                    return false;
                }
                if (!itemTags.isEmpty()) {
                    boolean matched = false;
                    for (TagKey<Item> tag : itemTags) {
                        if (stack.isIn(tag)) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        return false;
                    }
                }
                if (!items.isEmpty()) {
                    if (itemId == null || !items.contains(itemId)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    public record EmotionCueDefinition(String id,
                                       String category,
                                       long cooldownTicks,
                                       long categoryCooldownTicks,
                                       long digestWindowTicks,
                                       float minDelta,
                                       boolean digestEnabled,
                                       boolean highlightHud,
                                       boolean forceShow,
                                       boolean alwaysJournal,
                                       double radius,
                                       @Nullable String textKey,
                                       @Nullable String digestLabelKey,
                                       Map<PetComponent.Emotion, Float> baseEmotions) {

        public void applyBaseEmotions(PetComponent component) {
            for (Map.Entry<PetComponent.Emotion, Float> entry : baseEmotions.entrySet()) {
                component.pushEmotion(entry.getKey(), entry.getValue());
            }
        }

        public double resolvedRadius(double fallback) {
            return radius > 0 ? radius : fallback;
        }
    }

    private record EmotionCueProfile(String name,
                                     double radius,
                                     Map<PetComponent.Emotion, Float> emotions) {
        static final EmotionCueProfile EMPTY = new EmotionCueProfile("", Double.NaN,
            Collections.emptyMap());
    }

    private record CueCategory(String id,
                               long cooldownTicks,
                               long categoryCooldownTicks,
                               long digestWindowTicks,
                               float minDelta,
                               boolean highlightHud) {}

    private record CueModeSettings(float cooldownScale,
                                   float categoryCooldownScale,
                                   float digestScale,
                                   float minDeltaScale,
                                   boolean forceShow,
                                   boolean debugOverlay) {}

    private record Defaults(double radius,
                             long cooldown,
                             long categoryCooldown,
                             long digestWindow,
                             float minDelta,
                             boolean highlightHud) {}
}

