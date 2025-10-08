package woflo.petsplus.stats.nature.astrology;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.event.PetBreedEvent;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.nature.NatureFlavorHandler;
import woflo.petsplus.stats.nature.NatureModifierSampler;
import woflo.petsplus.util.BehaviorSeedUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime registry backing the Lunaris nature. Holds star sign definitions,
 * processes datapack overrides, and exposes helpers for assignment and sampling.
 */
public final class AstrologyRegistry {
    public static final Identifier LUNARIS_NATURE_ID = Identifier.of(Petsplus.MOD_ID, "lunaris");
    private static final Identifier OVERWORLD = Identifier.of("minecraft", "overworld");
    private static final String BASE_TITLE = "Lunaris";

    private static final Map<Identifier, AstrologySignDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final List<AstrologySignDefinition> ORDERED = new ArrayList<>();

    private AstrologyRegistry() {
    }

    static {
        reload(null);
    }

    /**
     * Refresh the registry using datapack-defined signs layered over the builtin set.
     */
    public static synchronized void reload(@Nullable List<AstrologySignDefinition> overrides) {
        Map<Identifier, AstrologySignDefinition> merged = new LinkedHashMap<>();
        for (AstrologySignDefinition builtin : builtinDefinitions()) {
            merged.put(builtin.id(), builtin);
        }
        if (overrides != null) {
            for (AstrologySignDefinition override : overrides) {
                merged.put(override.id(), override);
            }
        }

        DEFINITIONS.clear();
        ORDERED.clear();
        DEFINITIONS.putAll(merged);
        ORDERED.addAll(merged.values());
        ORDERED.sort(Comparator.comparingInt(AstrologySignDefinition::order).thenComparing(def -> def.id().toString()));

        propagateFlavorHooks();
    }

    /**
     * Assign the given sign identifier to the pet component.
     */
    public static void applySign(PetComponent component, @Nullable Identifier signId) {
        if (component == null) {
            return;
        }
        component.setAstrologySignId(signId);
    }

    /**
     * Resolve a sign for a breeding context.
     */
    public static @Nullable Identifier resolveSign(PetBreedEvent.BirthContext context, int moonPhase) {
        if (context == null) {
            return null;
        }
        AstrologySignDefinition.AstrologyContext astrologyContext = new AstrologySignDefinition.AstrologyContext(
            context.getDimensionId(),
            moonPhase,
            dayOfYear(context.getWorldTime()),
            context.getTimeOfDay(),
            context.isIndoors(),
            context.isDaytime(),
            context.isRaining(),
            context.isThundering(),
            context.getEnvironment() != null && context.getEnvironment().hasOpenSky(),
            context.getNearbyPlayerCount(),
            context.getNearbyPetCount(),
            context.getEnvironment()
        );
        return resolve(astrologyContext);
    }

    /**
     * Resolve a sign during taming.
     */
    public static @Nullable Identifier resolveSign(PetNatureSelectorContext context, int moonPhase) {
        if (context == null) {
            return null;
        }
        AstrologySignDefinition.AstrologyContext astrologyContext = new AstrologySignDefinition.AstrologyContext(
            context.dimensionId(),
            moonPhase,
            dayOfYear(context.worldTime()),
            context.timeOfDay(),
            context.indoors(),
            context.daytime(),
            context.raining(),
            context.thundering(),
            context.hasOpenSky(),
            context.nearbyPlayers(),
            context.nearbyPets(),
            context.environment()
        );
        return resolve(astrologyContext);
    }

    /**
     * Produce a stat/emotion adjustment for Lunaris based on the stored sign.
     */
    public static NatureModifierSampler.NatureAdjustment sampleAdjustment(PetComponent component, long seed) {
        Objects.requireNonNull(component, "component");
        Identifier signId = component.getAstrologySignId();
        AstrologySignDefinition definition = signId != null ? DEFINITIONS.get(signId) : null;

        if (definition == null) {
            Identifier fallback = fallbackSign(seed);
            if (fallback == null) {
                return NatureModifierSampler.NatureAdjustment.NONE;
            }
            applySign(component, fallback);
            signId = fallback;
            definition = DEFINITIONS.get(signId);
            if (definition == null) {
                return NatureModifierSampler.NatureAdjustment.NONE;
            }
        }

        AstrologySignDefinition.StatProfile stats = definition.statProfile();
        AstrologySignDefinition.EmotionProfile emotions = definition.emotionProfile();

        long baseSeed = seed ^ (long) signId.hashCode();
        long mixedSeed = BehaviorSeedUtil.mixBehaviorSeed(baseSeed, baseSeed);
        java.util.Random random = new java.util.Random(mixedSeed);

        float majorModifier = rollStatModifier(random, stats.majorBase());
        float minorModifier = rollStatModifier(random, stats.minorBase());

        NatureModifierSampler.NatureRoll majorRoll = new NatureModifierSampler.NatureRoll(
            stats.majorStat(), stats.majorBase(), majorModifier);
        NatureModifierSampler.NatureRoll minorRoll = new NatureModifierSampler.NatureRoll(
            stats.minorStat(), stats.minorBase(), minorModifier);

        float volatility = rollMultiplier(random, stats.volatilityMultiplier(), 0.3f, 1.75f);
        float resilience = rollMultiplier(random, stats.resilienceMultiplier(), 0.5f, 1.5f);
        float contagion = rollMultiplier(random, stats.contagionModifier(), 0.5f, 1.5f);
        float guard = rollMultiplier(random, stats.guardModifier(), 0.5f, 1.5f);

        PetComponent.NatureEmotionProfile emotionProfile = new PetComponent.NatureEmotionProfile(
            emotions.majorEmotion(), rollEmotion(random, emotions.majorStrength()),
            emotions.minorEmotion(), rollEmotion(random, emotions.minorStrength()),
            emotions.quirkEmotion(), rollEmotion(random, emotions.quirkStrength()));

        return new NatureModifierSampler.NatureAdjustment(
            majorRoll,
            minorRoll,
            volatility,
            resilience,
            contagion,
            guard,
            emotionProfile
        );
    }

    /**
     * Lightweight projection of the taming selection context so the registry doesn't need the full selector class.
     */
    public record PetNatureSelectorContext(Identifier dimensionId,
                                           long worldTime,
                                           long timeOfDay,
                                           boolean indoors,
                                           boolean daytime,
                                           boolean raining,
                                           boolean thundering,
                                           boolean hasOpenSky,
                                           int nearbyPlayers,
                                           int nearbyPets,
                                           PetBreedEvent.BirthContext.Environment environment) {
    }

    public static Set<Identifier> identifiers() {
        return Collections.unmodifiableSet(DEFINITIONS.keySet());
    }

    public static @Nullable AstrologySignDefinition get(Identifier id) {
        return DEFINITIONS.get(id);
    }

    private static @Nullable Identifier resolve(AstrologySignDefinition.AstrologyContext context) {
        if (ORDERED.isEmpty()) {
            return null;
        }
        List<AstrologySignDefinition> matches = new ArrayList<>();
        for (AstrologySignDefinition definition : ORDERED) {
            if (definition.matches(context)) {
                matches.add(definition);
            }
        }
        if (matches.isEmpty()) {
            int slot = MathHelper.clamp(context.dayOfYear() / Math.max(1, 360 / ORDERED.size()), 0, ORDERED.size() - 1);
            return ORDERED.get(slot).id();
        }
        matches.sort(Comparator.comparingInt(def -> distance(def.dayRange(), context.dayOfYear())));
        return matches.get(0).id();
    }

    private static int dayOfYear(long worldTime) {
        long day = worldTime / 24000L;
        return (int) (day % 360L);
    }

    private static int distance(AstrologySignDefinition.Range range, int day) {
        if (range.wrap()) {
            if (day >= range.startDay() || day <= range.endDay()) {
                return 0;
            }
            int toStart = (range.startDay() - day + 360) % 360;
            int toEnd = (day - range.endDay() + 360) % 360;
            return Math.min(toStart, toEnd);
        }
        if (day < range.startDay()) {
            return range.startDay() - day;
        }
        if (day > range.endDay()) {
            return day - range.endDay();
        }
        return 0;
    }

    private static float rollStatModifier(java.util.Random random, float base) {
        if (base == 0.0f) {
            return 1.0f;
        }
        float curve = (random.nextFloat() + random.nextFloat()) * 0.5f;
        return 0.92f + curve * 0.16f;
    }

    private static float rollMultiplier(java.util.Random random, float base, float min, float max) {
        if (base == 0.0f) {
            return 0.0f;
        }
        float curve = (random.nextFloat() + random.nextFloat()) * 0.5f;
        float scale = 0.9f + curve * 0.2f;
        return MathHelper.clamp(base * scale, min, max);
    }

    private static float rollEmotion(java.util.Random random, float base) {
        if (base <= 0.0f) {
            return 0.0f;
        }
        float curve = (random.nextFloat() + random.nextFloat()) * 0.5f;
        float scale = 0.85f + curve * 0.3f;
        return MathHelper.clamp(base * scale, 0.05f, 1.0f);
    }

    private static void propagateFlavorHooks() {
        Map<Identifier, NatureFlavorHandler.NatureFlavorOverride> overrides = new LinkedHashMap<>();
        for (AstrologySignDefinition definition : ORDERED) {
            if (definition.flavorHooks().isEmpty()) {
                continue;
            }
            List<NatureFlavorHandler.HookConfig> hooks = new ArrayList<>();
            for (AstrologySignDefinition.AstrologyFlavorHook hook : definition.flavorHooks()) {
                hooks.add(new NatureFlavorHandler.HookConfig(
                    hook.trigger(),
                    hook.slot().toNatureSlot(),
                    hook.scale(),
                    hook.cooldownTicks(),
                    hook.append()
                ));
            }
            overrides.put(definition.id(), new NatureFlavorHandler.NatureFlavorOverride(false, hooks));
        }
        NatureFlavorHandler.reloadAstrologyOverrides(overrides);
    }

    private static @Nullable Identifier fallbackSign(long seed) {
        if (ORDERED.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(seed, ORDERED.size());
        return ORDERED.get(index).id();
    }

    private static List<AstrologySignDefinition> builtinDefinitions() {
        List<AstrologySignDefinition> list = new ArrayList<>();
        list.add(createSign("Capricorn", 355, 19, AstrologySignDefinition.DisplayStyle.SUFFIX, "Aegis", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.DEFENSE, 0.06f,
                NatureModifierSampler.NatureStat.FOCUS, 0.03f,
                0.80f, 1.35f, 0.85f, 1.30f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.STOIC, 0.44f,
                PetComponent.Emotion.GAMAN, 0.30f,
                PetComponent.Emotion.PROTECTIVE, 0.24f
            )));
        list.add(createSign("Aquarius", 20, 49, AstrologySignDefinition.DisplayStyle.PREFIX, "Astra", "-",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.FOCUS, 0.05f,
                NatureModifierSampler.NatureStat.SPEED, 0.03f,
                1.10f, 0.95f, 1.20f, 0.95f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.ARCANE_OVERFLOW, 0.42f,
                PetComponent.Emotion.ENNUI, 0.30f,
                PetComponent.Emotion.CURIOUS, 0.26f
            )));
        list.add(createSign("Pisces", 50, 79, AstrologySignDefinition.DisplayStyle.SUFFIX, "Unda", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.VITALITY, 0.05f,
                NatureModifierSampler.NatureStat.LOYALTY, 0.03f,
                0.85f, 1.25f, 1.15f, 0.90f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.EMPATHY, 0.44f,
                PetComponent.Emotion.YUGEN, 0.32f,
                PetComponent.Emotion.HIRAETH, 0.26f
            )));
        list.add(createSign("Aries", 80, 109, AstrologySignDefinition.DisplayStyle.PREFIX, "Ignis", "-",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.ATTACK, 0.07f,
                NatureModifierSampler.NatureStat.SPEED, 0.03f,
                1.45f, 0.95f, 1.05f, 0.85f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.KEFI, 0.44f,
                PetComponent.Emotion.RESTLESS, 0.30f,
                PetComponent.Emotion.SISU, 0.24f
            )));
        list.add(createSign("Taurus", 110, 140, AstrologySignDefinition.DisplayStyle.SUFFIX, "Verdura", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.VITALITY, 0.06f,
                NatureModifierSampler.NatureStat.DEFENSE, 0.03f,
                0.85f, 1.30f, 0.90f, 1.25f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.CONTENT, 0.42f,
                PetComponent.Emotion.SOBREMESA, 0.30f,
                PetComponent.Emotion.WABI_SABI, 0.26f
            )));
        list.add(createSign("Gemini", 141, 171, AstrologySignDefinition.DisplayStyle.SUFFIX, "Gemina", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.SPEED, 0.06f,
                NatureModifierSampler.NatureStat.FOCUS, 0.03f,
                1.20f, 0.95f, 1.25f, 0.90f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.GLEE, 0.42f,
                PetComponent.Emotion.CURIOUS, 0.30f,
                PetComponent.Emotion.QUERECIA, 0.26f
            )));
        list.add(createSign("Cancer", 172, 203, AstrologySignDefinition.DisplayStyle.SUFFIX, "Haven", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.LOYALTY, 0.06f,
                NatureModifierSampler.NatureStat.VITALITY, 0.03f,
                0.95f, 1.30f, 1.20f, 1.30f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.LOYALTY, 0.44f,
                PetComponent.Emotion.PROTECTIVENESS, 0.30f,
                PetComponent.Emotion.WORRIED, 0.24f
            )));
        list.add(createSign("Leo", 204, 234, AstrologySignDefinition.DisplayStyle.SUFFIX, "Regis", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.ATTACK, 0.06f,
                NatureModifierSampler.NatureStat.FOCUS, 0.03f,
                1.25f, 1.05f, 1.20f, 1.05f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.PRIDE, 0.44f,
                PetComponent.Emotion.BLISSFUL, 0.30f,
                PetComponent.Emotion.UBUNTU, 0.26f
            )));
        list.add(createSign("Virgo", 235, 265, AstrologySignDefinition.DisplayStyle.PREFIX, "Seren", "-",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.FOCUS, 0.06f,
                NatureModifierSampler.NatureStat.AGILITY, 0.03f,
                0.95f, 1.20f, 0.95f, 1.20f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.FOCUSED, 0.42f,
                PetComponent.Emotion.REGRET, 0.30f,
                PetComponent.Emotion.LAGOM, 0.24f
            )));
        list.add(createSign("Libra", 266, 295, AstrologySignDefinition.DisplayStyle.SUFFIX, "Lumen", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.AGILITY, 0.05f,
                NatureModifierSampler.NatureStat.LOYALTY, 0.03f,
                1.05f, 1.05f, 1.15f, 1.05f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.HOPEFUL, 0.42f,
                PetComponent.Emotion.RELIEF, 0.30f,
                PetComponent.Emotion.PACK_SPIRIT, 0.24f
            )));
        list.add(createSign("Scorpio", 296, 325, AstrologySignDefinition.DisplayStyle.PREFIX, "Vesper", "-",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.ATTACK, 0.06f,
                NatureModifierSampler.NatureStat.DEFENSE, 0.03f,
                1.30f, 1.10f, 0.90f, 1.30f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.VIGILANT, 0.44f,
                PetComponent.Emotion.FOREBODING, 0.30f,
                PetComponent.Emotion.MELANCHOLY, 0.24f
            )));
        list.add(createSign("Sagittarius", 326, 354, AstrologySignDefinition.DisplayStyle.SUFFIX, "Stratos", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.SPEED, 0.06f,
                NatureModifierSampler.NatureStat.VITALITY, 0.03f,
                1.30f, 1.00f, 1.10f, 0.95f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.FERNWEH, 0.44f,
                PetComponent.Emotion.PLAYFULNESS, 0.30f,
                PetComponent.Emotion.HANYAUKU, 0.24f
            )));
        return list;
    }

    private static AstrologySignDefinition createSign(String displayName,
                                                      int startDay,
                                                      int endDay,
                                                      AstrologySignDefinition.DisplayStyle displayStyle,
                                                      String displayEpithet,
                                                      String connector,
                                                      AstrologySignDefinition.StatProfile statProfile,
                                                      AstrologySignDefinition.EmotionProfile emotionProfile) {
        String slug = displayName.toLowerCase(Locale.ROOT);
        Identifier id = Identifier.of(Petsplus.MOD_ID, "lunaris/" + slug);
        return AstrologySignDefinition.builder(id)
            .displayName(displayName)
            .order(startDay)
            .dayRange(new AstrologySignDefinition.Range(startDay, endDay, startDay > endDay))
            .dayWindow(AstrologySignDefinition.DayWindow.ANY)
            .weatherWindow(AstrologySignDefinition.WeatherWindow.ANY)
            .requiresOpenSky(false)
            .allowIndoors(false)
            .allowedDimensions(Set.of(OVERWORLD))
            .displayEpithet(displayEpithet)
            .displayStyle(displayStyle)
            .displayConnector(connector)
            .statProfile(statProfile)
            .emotionProfile(emotionProfile)
            .build();
    }

    public static String getDisplayTitle(@Nullable Identifier signId) {
        if (signId == null) {
            return BASE_TITLE;
        }
        AstrologySignDefinition definition = DEFINITIONS.get(signId);
        if (definition == null) {
            return BASE_TITLE;
        }
        return definition.formatTitle(BASE_TITLE);
    }

    public static String getNatureTitle(@Nullable Identifier natureId, @Nullable Identifier signId) {
        if (natureId == null) {
            return "None";
        }
        if (!natureId.equals(LUNARIS_NATURE_ID)) {
            return natureId.toString();
        }
        return getDisplayTitle(signId);
    }

    public static Text getNatureText(PetComponent component) {
        if (component == null) {
            return Text.literal("None");
        }
        Identifier natureId = component.getNatureId();
        if (natureId == null) {
            return Text.literal("None");
        }
        String title = getNatureTitle(natureId, component.getAstrologySignId());
        return Text.literal(title);
    }
}

