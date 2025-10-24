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
import java.util.EnumSet;
import java.util.HashMap;
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
    private static final Map<PhaseNightSlot, AstrologySignDefinition> PHASE_NIGHT_INDEX = new HashMap<>();
    private static int signCycleCursor = 0;

    private AstrologyRegistry() {
    }

    static {
        reload(null);
    }

    /**
     * Refresh the registry using datapack-defined signs layered over the builtin set.
     */
    public static synchronized void reload(@Nullable List<AstrologySignDefinition> overrides) {
        reloadInternal(overrides, true);
    }

    static synchronized void reloadForTesting(@Nullable List<AstrologySignDefinition> definitions) {
        reloadInternal(definitions, false);
    }

    private static void reloadInternal(@Nullable List<AstrologySignDefinition> overrides, boolean includeBuiltins) {
        Map<Identifier, AstrologySignDefinition> merged = new LinkedHashMap<>();
        if (includeBuiltins) {
            for (AstrologySignDefinition builtin : builtinDefinitions()) {
                merged.put(builtin.id(), builtin);
            }
        }
        List<AstrologySignDefinition> overrideList = overrides != null ? overrides : List.of();
        for (AstrologySignDefinition override : overrideList) {
            merged.put(override.id(), override);
        }

        DEFINITIONS.clear();
        ORDERED.clear();
        DEFINITIONS.putAll(merged);
        ORDERED.addAll(merged.values());
        ORDERED.sort(Comparator.comparingInt(AstrologySignDefinition::order).thenComparing(def -> def.id().toString()));
        rebuildPhaseNightIndex();
        resetSignCycle();

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

    public static synchronized int signCount() {
        return ORDERED.size();
    }

    public static synchronized @Nullable Identifier signAt(int index) {
        if (ORDERED.isEmpty()) {
            return null;
        }
        int wrapped = Math.floorMod(index, ORDERED.size());
        return ORDERED.get(wrapped).id();
    }

    public static synchronized SignCycleSnapshot advanceSignCycle() {
        if (ORDERED.isEmpty()) {
            return SignCycleSnapshot.EMPTY;
        }
        int total = ORDERED.size();
        int index = Math.floorMod(signCycleCursor, total);
        AstrologySignDefinition assignedDefinition = ORDERED.get(index);
        signCycleCursor = (index + 1) % total;
        Identifier nextIdentifier = total > 1 ? ORDERED.get(signCycleCursor).id() : null;
        return new SignCycleSnapshot(assignedDefinition.id(), nextIdentifier, index, total);
    }

    public static synchronized void resetSignCycle() {
        signCycleCursor = 0;
    }

    public static @Nullable AstrologySignDefinition get(Identifier id) {
        return DEFINITIONS.get(id);
    }

    private static @Nullable Identifier resolve(AstrologySignDefinition.AstrologyContext context) {
        if (ORDERED.isEmpty()) {
            return null;
        }

        if (!PHASE_NIGHT_INDEX.isEmpty()) {
            PhaseNightSlot slot = PhaseNightSlot.from(context);
            if (slot != null) {
                AstrologySignDefinition direct = PHASE_NIGHT_INDEX.get(slot);
                if (direct != null && direct.matches(context)) {
                    return direct.id();
                }
            }
        }

        // First, try to find matches with moon phase and night period criteria
        List<AstrologySignDefinition> moonPhaseMatches = new ArrayList<>();
        for (AstrologySignDefinition definition : ORDERED) {
            if (!definition.phaseNightWindows().isEmpty()
                || !definition.moonPhases().isEmpty()
                || !definition.nightPeriods().isEmpty()) {
                if (definition.matches(context)) {
                    moonPhaseMatches.add(definition);
                }
            }
        }

        // If we found moon phase matches, use the first one
        if (!moonPhaseMatches.isEmpty()) {
            moonPhaseMatches.sort(Comparator.comparingInt(AstrologySignDefinition::order));
            return moonPhaseMatches.get(0).id();
        }

        // Fall back to traditional day-based matching
        List<AstrologySignDefinition> dayMatches = new ArrayList<>();
        for (AstrologySignDefinition definition : ORDERED) {
            if (definition.matches(context)) {
                dayMatches.add(definition);
            }
        }

        if (dayMatches.isEmpty()) {
            int slot = MathHelper.clamp(context.dayOfYear() / Math.max(1, 360 / ORDERED.size()), 0, ORDERED.size() - 1);
            return ORDERED.get(slot).id();
        }

        dayMatches.sort(Comparator.comparingInt(def -> distance(def.dayRange(), context.dayOfYear())));
        return dayMatches.get(0).id();
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

    private static void rebuildPhaseNightIndex() {
        PHASE_NIGHT_INDEX.clear();
        for (AstrologySignDefinition definition : ORDERED) {
            Map<Integer, Set<AstrologySignDefinition.NightPeriod>> windows = definition.phaseNightWindows();
            if (windows.isEmpty()) {
                continue;
            }
            for (Map.Entry<Integer, Set<AstrologySignDefinition.NightPeriod>> entry : windows.entrySet()) {
                int phase = entry.getKey();
                Set<AstrologySignDefinition.NightPeriod> periods = entry.getValue();
                if (periods == null || periods.isEmpty()) {
                    continue;
                }
                for (AstrologySignDefinition.NightPeriod period : periods) {
                    PhaseNightSlot slot = new PhaseNightSlot(phase, period);
                    AstrologySignDefinition existing = PHASE_NIGHT_INDEX.putIfAbsent(slot, definition);
                    if (existing != null && existing != definition) {
                        Petsplus.LOGGER.warn(
                            "Duplicate Lunaris phase/night slot {} claimed by {} and {}; keeping {}",
                            slot,
                            existing.id(),
                            definition.id(),
                            existing.id()
                        );
                    }
                }
            }
        }
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

        // Aries → Pisces consolidated to 12 zodiac signs, each claiming two unique moon phase/night-period slots
        list.add(createMoonPhaseSign("Aries", 0,
            AstrologySignDefinition.DisplayStyle.SUFFIX, "Vanguard", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.MIGHT, 0.07f,
                NatureModifierSampler.NatureStat.SWIFTNESS, 0.03f,
                1.45f, 0.95f, 1.05f, 0.85f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.KEFI, 0.44f,
                PetComponent.Emotion.RESTLESS, 0.30f,
                PetComponent.Emotion.SISU, 0.24f
            ),
            slot(5, AstrologySignDefinition.NightPeriod.EARLY_NIGHT),
            slot(1, AstrologySignDefinition.NightPeriod.EARLY_NIGHT)));
        list.add(createMoonPhaseSign("Taurus", 1,
            AstrologySignDefinition.DisplayStyle.PREFIX, "Bulwark", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.VITALITY, 0.06f,
                NatureModifierSampler.NatureStat.GUARD, 0.03f,
                0.85f, 1.30f, 0.90f, 1.25f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.CONTENT, 0.42f,
                PetComponent.Emotion.SOBREMESA, 0.30f,
                PetComponent.Emotion.WABI_SABI, 0.26f
            ),
            slot(6, AstrologySignDefinition.NightPeriod.MIDDLE_NIGHT),
            slot(2, AstrologySignDefinition.NightPeriod.MIDDLE_NIGHT)));
        list.add(createMoonPhaseSign("Gemini", 2,
            AstrologySignDefinition.DisplayStyle.PREFIX, "Syzygy", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.SWIFTNESS, 0.06f,
                NatureModifierSampler.NatureStat.FOCUS, 0.03f,
                1.20f, 0.95f, 1.25f, 0.90f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.CHEERFUL, 0.42f,
                PetComponent.Emotion.CURIOUS, 0.30f,
                PetComponent.Emotion.QUERECIA, 0.26f
            ),
            slot(7, AstrologySignDefinition.NightPeriod.LATE_NIGHT),
            slot(3, AstrologySignDefinition.NightPeriod.LATE_NIGHT)));
        list.add(createMoonPhaseSign("Cancer", 3,
            AstrologySignDefinition.DisplayStyle.SUFFIX, "Haven", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.FOCUS, 0.06f,
                NatureModifierSampler.NatureStat.VITALITY, 0.03f,
                0.95f, 1.30f, 1.20f, 1.30f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.LOYALTY, 0.44f,
                PetComponent.Emotion.GUARDIAN_VIGIL, 0.30f,
                PetComponent.Emotion.WORRIED, 0.24f
            ),
            slot(0, AstrologySignDefinition.NightPeriod.EARLY_NIGHT),
            slot(4, AstrologySignDefinition.NightPeriod.EARLY_NIGHT)));
        list.add(createMoonPhaseSign("Leo", 4,
            AstrologySignDefinition.DisplayStyle.PREFIX, "Solara", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.MIGHT, 0.06f,
                NatureModifierSampler.NatureStat.FOCUS, 0.03f,
                1.25f, 1.05f, 1.20f, 1.05f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.PRIDE, 0.44f,
                PetComponent.Emotion.CONTENT, 0.30f,
                PetComponent.Emotion.UBUNTU, 0.26f
            ),
            slot(1, AstrologySignDefinition.NightPeriod.MIDDLE_NIGHT),
            slot(5, AstrologySignDefinition.NightPeriod.MIDDLE_NIGHT)));
        list.add(createMoonPhaseSign("Virgo", 5,
            AstrologySignDefinition.DisplayStyle.PREFIX, "Calibra", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.FOCUS, 0.06f,
                NatureModifierSampler.NatureStat.AGILITY, 0.03f,
                0.95f, 1.20f, 0.95f, 1.20f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.FOCUSED, 0.42f,
                PetComponent.Emotion.REGRET, 0.30f,
                PetComponent.Emotion.LAGOM, 0.24f
            ),
            slot(2, AstrologySignDefinition.NightPeriod.LATE_NIGHT),
            slot(6, AstrologySignDefinition.NightPeriod.LATE_NIGHT)));
        list.add(createMoonPhaseSign("Libra", 6,
            AstrologySignDefinition.DisplayStyle.SUFFIX, "Equinox", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.AGILITY, 0.05f,
                NatureModifierSampler.NatureStat.FOCUS, 0.03f,
                1.05f, 1.05f, 1.15f, 1.05f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.HOPEFUL, 0.42f,
                PetComponent.Emotion.RELIEF, 0.30f,
                PetComponent.Emotion.PACK_SPIRIT, 0.24f
            ),
            slot(3, AstrologySignDefinition.NightPeriod.EARLY_NIGHT),
            slot(7, AstrologySignDefinition.NightPeriod.EARLY_NIGHT)));
        list.add(createMoonPhaseSign("Scorpio", 7,
            AstrologySignDefinition.DisplayStyle.PREFIX, "Vesper", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.MIGHT, 0.06f,
                NatureModifierSampler.NatureStat.GUARD, 0.03f,
                1.30f, 1.10f, 0.90f, 1.30f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.VIGILANT, 0.44f,
                PetComponent.Emotion.FOREBODING, 0.30f,
                PetComponent.Emotion.MELANCHOLY, 0.24f
            ),
            slot(4, AstrologySignDefinition.NightPeriod.MIDDLE_NIGHT),
            slot(0, AstrologySignDefinition.NightPeriod.MIDDLE_NIGHT)));
        list.add(createMoonPhaseSign("Sagittarius", 8,
            AstrologySignDefinition.DisplayStyle.PREFIX, "Zenith", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.SWIFTNESS, 0.06f,
                NatureModifierSampler.NatureStat.VITALITY, 0.03f,
                1.30f, 1.00f, 1.10f, 0.95f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.FERNWEH, 0.44f,
                PetComponent.Emotion.PLAYFULNESS, 0.30f,
                PetComponent.Emotion.HANYAUKU, 0.24f
            ),
            slot(5, AstrologySignDefinition.NightPeriod.LATE_NIGHT),
            slot(1, AstrologySignDefinition.NightPeriod.LATE_NIGHT)));
        list.add(createMoonPhaseSign("Capricorn", 9,
            AstrologySignDefinition.DisplayStyle.SUFFIX, "Apex", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.GUARD, 0.06f,
                NatureModifierSampler.NatureStat.FOCUS, 0.03f,
                0.80f, 1.35f, 0.85f, 1.30f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.STOIC, 0.44f,
                PetComponent.Emotion.GAMAN, 0.30f,
                PetComponent.Emotion.PROTECTIVE, 0.24f
            ),
            slot(6, AstrologySignDefinition.NightPeriod.EARLY_NIGHT),
            slot(2, AstrologySignDefinition.NightPeriod.EARLY_NIGHT)));
        list.add(createMoonPhaseSign("Aquarius", 10,
            AstrologySignDefinition.DisplayStyle.PREFIX, "Cascade", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.FOCUS, 0.05f,
                NatureModifierSampler.NatureStat.SWIFTNESS, 0.03f,
                1.10f, 0.95f, 1.20f, 0.95f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.ARCANE_OVERFLOW, 0.42f,
                PetComponent.Emotion.ENNUI, 0.30f,
                PetComponent.Emotion.CURIOUS, 0.26f
            ),
            slot(7, AstrologySignDefinition.NightPeriod.MIDDLE_NIGHT),
            slot(3, AstrologySignDefinition.NightPeriod.MIDDLE_NIGHT)));
        list.add(createMoonPhaseSign("Pisces", 11,
            AstrologySignDefinition.DisplayStyle.PREFIX, "Eidolon", " ",
            new AstrologySignDefinition.StatProfile(
                NatureModifierSampler.NatureStat.VITALITY, 0.05f,
                NatureModifierSampler.NatureStat.FOCUS, 0.03f,
                0.85f, 1.25f, 1.15f, 0.90f
            ),
            new AstrologySignDefinition.EmotionProfile(
                PetComponent.Emotion.UBUNTU, 0.44f,
                PetComponent.Emotion.YUGEN, 0.32f,
                PetComponent.Emotion.HIRAETH, 0.26f
            ),
            slot(0, AstrologySignDefinition.NightPeriod.LATE_NIGHT),
            slot(4, AstrologySignDefinition.NightPeriod.LATE_NIGHT)));

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

    private static PhaseNightSlot slot(int moonPhase, AstrologySignDefinition.NightPeriod nightPeriod) {
        return new PhaseNightSlot(moonPhase, nightPeriod);
    }

    private static AstrologySignDefinition createMoonPhaseSign(String displayName,
                                                               int order,
                                                               AstrologySignDefinition.DisplayStyle displayStyle,
                                                               String displayEpithet,
                                                               String connector,
                                                               AstrologySignDefinition.StatProfile statProfile,
                                                               AstrologySignDefinition.EmotionProfile emotionProfile,
                                                               PhaseNightSlot... slots) {
        if (slots == null || slots.length == 0) {
            throw new IllegalArgumentException("A Lunaris sign requires at least one phase/night slot");
        }
        String slug = displayName.toLowerCase(Locale.ROOT);
        Identifier id = Identifier.of(Petsplus.MOD_ID, "lunaris/" + slug);
        Map<Integer, Set<AstrologySignDefinition.NightPeriod>> phaseNightWindows = new LinkedHashMap<>();
        EnumSet<AstrologySignDefinition.NightPeriod> nightPeriods = EnumSet.noneOf(AstrologySignDefinition.NightPeriod.class);
        for (PhaseNightSlot slot : slots) {
            if (slot == null) {
                continue;
            }
            Set<AstrologySignDefinition.NightPeriod> periods = phaseNightWindows
                .computeIfAbsent(slot.moonPhase(), key -> EnumSet.noneOf(AstrologySignDefinition.NightPeriod.class));
            periods.add(slot.nightPeriod());
            nightPeriods.add(slot.nightPeriod());
        }
        if (phaseNightWindows.isEmpty()) {
            throw new IllegalArgumentException("A Lunaris sign requires at least one valid phase/night slot");
        }
        Set<Integer> moonPhases = Set.copyOf(phaseNightWindows.keySet());
        return AstrologySignDefinition.builder(id)
            .displayName(displayName)
            .order(order)
            .dayRange(new AstrologySignDefinition.Range(0, 359, true)) // All days as fallback
            .dayWindow(AstrologySignDefinition.DayWindow.NIGHT) // Only at night
            .weatherWindow(AstrologySignDefinition.WeatherWindow.ANY)
            .requiresOpenSky(true) // Require open sky for moon visibility
            .allowIndoors(false)
            .moonPhases(moonPhases)
            .nightPeriods(nightPeriods)
            .phaseNightWindows(phaseNightWindows)
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

    private record PhaseNightSlot(int moonPhase, AstrologySignDefinition.NightPeriod nightPeriod) {
        private PhaseNightSlot {
            moonPhase = Math.floorMod(moonPhase, 8);
            Objects.requireNonNull(nightPeriod, "nightPeriod");
        }

        private static @Nullable PhaseNightSlot from(AstrologySignDefinition.AstrologyContext context) {
            AstrologySignDefinition.NightPeriod nightPeriod = AstrologySignDefinition.resolveNightPeriod(context);
            if (nightPeriod == null) {
                return null;
            }
            return new PhaseNightSlot(
                context.moonPhase(),
                nightPeriod
            );
        }
    }

    public record SignCycleSnapshot(@Nullable Identifier assigned,
                                    @Nullable Identifier next,
                                    int assignedIndex,
                                    int total) {
        private static final int NO_INDEX = -1;
        public static final SignCycleSnapshot EMPTY = new SignCycleSnapshot(null, null, NO_INDEX, 0);

        public boolean hasAssignment() {
            return assigned != null;
        }

        public int displayIndex() {
            return assignedIndex >= 0 ? assignedIndex + 1 : 0;
        }
    }
}

