package woflo.petsplus.stats.nature.astrology;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.event.PetBreedEvent;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.nature.NatureModifierSampler;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable description of a Lunaris star sign. Encodes the stat and emotion
 * profile alongside the environmental window that qualifies a pet for the sign.
 */
public final class AstrologySignDefinition {
    private final Identifier id;
    private final int order;
    private final String displayName;
    private final DayWindow dayWindow;
    private final WeatherWindow weatherWindow;
    private final boolean requiresOpenSky;
    private final boolean allowIndoors;
    private final Set<Integer> moonPhases;
    private final Range dayRange;
    private final Set<Identifier> allowedDimensions;
    private final Set<EnvironmentTag> requiredEnvironment;
    private final NearbyConstraints nearbyConstraints;
    private final String displayEpithet;
    private final DisplayStyle displayStyle;
    private final String displayConnector;
    private final StatProfile statProfile;
    private final EmotionProfile emotionProfile;
    private final List<AstrologyFlavorHook> flavorHooks;

    AstrologySignDefinition(Identifier id,
                            int order,
                            @Nullable String displayName,
                            Range dayRange,
                            DayWindow dayWindow,
                            WeatherWindow weatherWindow,
                            boolean requiresOpenSky,
                            boolean allowIndoors,
                            Set<Integer> moonPhases,
                            Set<Identifier> allowedDimensions,
                            Set<EnvironmentTag> requiredEnvironment,
                            NearbyConstraints nearbyConstraints,
                            @Nullable String displayEpithet,
                            DisplayStyle displayStyle,
                            String displayConnector,
                            StatProfile statProfile,
                            EmotionProfile emotionProfile,
                            List<AstrologyFlavorHook> flavorHooks) {
        this.id = Objects.requireNonNull(id, "id");
        this.order = order;
        this.displayName = displayName != null ? displayName : id.getPath();
        this.dayRange = Objects.requireNonNull(dayRange, "dayRange");
        this.dayWindow = Objects.requireNonNull(dayWindow, "dayWindow");
        this.weatherWindow = Objects.requireNonNull(weatherWindow, "weatherWindow");
        this.requiresOpenSky = requiresOpenSky;
        this.allowIndoors = allowIndoors;
        this.moonPhases = moonPhases != null ? Set.copyOf(moonPhases) : Set.of();
        this.allowedDimensions = allowedDimensions != null ? Set.copyOf(allowedDimensions) : Set.of();
        this.requiredEnvironment = requiredEnvironment != null ? EnumSet.copyOf(requiredEnvironment) : EnumSet.noneOf(EnvironmentTag.class);
        this.nearbyConstraints = Objects.requireNonNull(nearbyConstraints, "nearbyConstraints");
        this.displayEpithet = displayEpithet;
        this.displayStyle = displayStyle != null ? displayStyle : DisplayStyle.SUFFIX;
        this.displayConnector = displayConnector != null ? displayConnector : " ";
        this.statProfile = Objects.requireNonNull(statProfile, "statProfile");
        this.emotionProfile = Objects.requireNonNull(emotionProfile, "emotionProfile");
        this.flavorHooks = flavorHooks != null ? List.copyOf(flavorHooks) : List.of();
    }

    public Identifier id() {
        return id;
    }

    public int order() {
        return order;
    }

    public String displayName() {
        return displayName;
    }

    public Range dayRange() {
        return dayRange;
    }

    public DayWindow dayWindow() {
        return dayWindow;
    }

    public WeatherWindow weatherWindow() {
        return weatherWindow;
    }

    public boolean requiresOpenSky() {
        return requiresOpenSky;
    }

    public boolean allowIndoors() {
        return allowIndoors;
    }

    public Set<Integer> moonPhases() {
        return moonPhases;
    }

    public Set<Identifier> allowedDimensions() {
        return allowedDimensions;
    }

    public Set<EnvironmentTag> requiredEnvironment() {
        return requiredEnvironment;
    }

    public NearbyConstraints nearbyConstraints() {
        return nearbyConstraints;
    }

    public StatProfile statProfile() {
        return statProfile;
    }

    public EmotionProfile emotionProfile() {
        return emotionProfile;
    }

    public List<AstrologyFlavorHook> flavorHooks() {
        return flavorHooks;
    }

    public @Nullable String displayEpithet() {
        return displayEpithet;
    }

    public DisplayStyle displayStyle() {
        return displayStyle;
    }

    public String displayConnector() {
        return displayConnector;
    }

    public String formatTitle(String baseTitle) {
        if (displayEpithet == null || displayEpithet.isBlank()) {
            return baseTitle;
        }
        return switch (displayStyle) {
            case PREFIX -> displayEpithet + displayConnector + baseTitle;
            case SUFFIX -> baseTitle + displayConnector + displayEpithet;
        };
    }

    /**
     * Test whether this sign's environmental window matches the supplied context.
     */
    public boolean matches(AstrologyContext context) {
        Objects.requireNonNull(context, "context");

        if (!dayRange.containsDay(context.dayOfYear())) {
            return false;
        }

        if (!allowedDimensions.isEmpty() && !allowedDimensions.contains(context.dimensionId())) {
            return false;
        }

        if (!moonPhases.isEmpty() && !moonPhases.contains(context.moonPhase())) {
            return false;
        }

        if (requiresOpenSky && !context.hasOpenSky()) {
            return false;
        }

        if (!allowIndoors && context.indoors()) {
            return false;
        }

        if (!nearbyConstraints.test(context.nearbyPlayers(), context.nearbyPets())) {
            return false;
        }

        if (!matchesWeather(context)) {
            return false;
        }

        if (!matchesDayWindow(context)) {
            return false;
        }

        if (!requiredEnvironment.isEmpty() && !EnvironmentTag.matchesAll(requiredEnvironment, context.environment())) {
            return false;
        }

        return true;
    }

    private boolean matchesWeather(AstrologyContext context) {
        return switch (weatherWindow) {
            case ANY -> true;
            case CLEAR -> !context.raining() && !context.thundering();
            case RAIN -> context.raining() && !context.thundering();
            case STORM -> context.thundering();
        };
    }

    private boolean matchesDayWindow(AstrologyContext context) {
        return switch (dayWindow) {
            case ANY -> true;
            case DAY -> context.daytime();
            case NIGHT -> !context.daytime();
            case DAWN -> context.isDawn();
            case DUSK -> context.isDusk();
        };
    }

    /**
     * Range of in-game days (0-359) that map to a sign.
     */
    public record Range(int startDay, int endDay, boolean wrap) {

        public Range {
            if (startDay < 0 || startDay >= 360) {
                throw new IllegalArgumentException("startDay must be 0-359");
            }
            if (endDay < 0 || endDay >= 360) {
                throw new IllegalArgumentException("endDay must be 0-359");
            }
        }

        public boolean containsDay(int day) {
            if (wrap) {
                return day >= startDay || day <= endDay;
            }
            if (startDay <= endDay) {
                return day >= startDay && day <= endDay;
            }
            // implicit wrap if start > end
            return day >= startDay || day <= endDay;
        }
    }

    public enum DayWindow {
        ANY,
        DAY,
        NIGHT,
        DAWN,
        DUSK
    }

    public enum WeatherWindow {
        ANY,
        CLEAR,
        RAIN,
        STORM
    }

    public enum DisplayStyle {
        PREFIX,
        SUFFIX
    }

    /**
     * Environmental flags exposed by the birth/taming context.
     */
    public enum EnvironmentTag {
        OPEN_SKY,
        COZY,
        LUSH,
        VALUABLE,
        SUBMERGED,
        LAVA,
        POWDER_SNOW,
        MUDDY,
        STRUCTURE,
        ARCHAEOLOGY,
        TRIAL,
        CHERRY,
        REDSTONE,
        HOMESTEAD,
        RECENT_COMBAT,
        DEEP_DARK,
        MUSHROOM,
        OCEAN,
        SWAMP,
        SNOWY,
        HIGH_ALTITUDE,
        LOW_SKYLIGHT;

        private static boolean matchesAll(Set<EnvironmentTag> required, PetBreedEvent.BirthContext.Environment env) {
            for (EnvironmentTag tag : required) {
                if (!matches(tag, env)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean matches(EnvironmentTag tag, PetBreedEvent.BirthContext.Environment env) {
            return switch (tag) {
                case OPEN_SKY -> env.hasOpenSky();
                case COZY -> env.hasCozyBlocks();
                case LUSH -> env.hasLushFoliage();
                case VALUABLE -> env.hasValuableOres();
                case SUBMERGED -> env.isFullySubmerged();
                case LAVA -> env.isNearLavaOrMagma();
                case POWDER_SNOW -> env.isNearPowderSnow();
                case MUDDY -> env.isNearMudOrMangrove();
                case STRUCTURE -> env.isNearMajorStructure();
                case ARCHAEOLOGY -> env.hasArchaeologySite();
                case TRIAL -> env.isNearTrialChamber();
                case CHERRY -> env.hasCherryBloom();
                case REDSTONE -> env.hasActiveRedstone();
                case HOMESTEAD -> env.hasHomesteadBlocks();
                case RECENT_COMBAT -> env.hasRecentCombat();
                case DEEP_DARK -> env.isDeepDarkBiome();
                case MUSHROOM -> env.isMushroomFieldsBiome();
                case OCEAN -> env.isOceanBiome();
                case SWAMP -> env.isSwampBiome();
                case SNOWY -> env.hasSnowyPrecipitation();
                case HIGH_ALTITUDE -> env.getHeight() >= 100;
                case LOW_SKYLIGHT -> env.getSkyLightLevel() <= 4;
            };
        }
    }

    /**
     * Nearby entity constraints to keep a sign scoped to certain social settings.
     */
    public record NearbyConstraints(int minPlayers, int maxPlayers, int minPets, int maxPets) {
        public static final NearbyConstraints ANY = new NearbyConstraints(0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);

        public boolean test(int players, int pets) {
            return players >= minPlayers
                && players <= maxPlayers
                && pets >= minPets
                && pets <= maxPets;
        }
    }

    public record StatProfile(NatureModifierSampler.NatureStat majorStat,
                              float majorBase,
                              NatureModifierSampler.NatureStat minorStat,
                              float minorBase,
                              float volatilityMultiplier,
                              float resilienceMultiplier,
                              float contagionModifier,
                              float guardModifier) {
    }

    public record EmotionProfile(@Nullable PetComponent.Emotion majorEmotion,
                                 float majorStrength,
                                 @Nullable PetComponent.Emotion minorEmotion,
                                 float minorStrength,
                                 @Nullable PetComponent.Emotion quirkEmotion,
                                 float quirkStrength) {
    }

    public record AstrologyFlavorHook(AstrologyFlavorHookSlot slot,
                                      woflo.petsplus.stats.nature.NatureFlavorHandler.Trigger trigger,
                                      float scale,
                                      long cooldownTicks,
                                      boolean append) {
    }

    public enum AstrologyFlavorHookSlot {
        MAJOR,
        MINOR,
        QUIRK;

        public woflo.petsplus.stats.nature.NatureFlavorHandler.Slot toNatureSlot() {
            return switch (this) {
                case MAJOR -> woflo.petsplus.stats.nature.NatureFlavorHandler.Slot.MAJOR;
                case MINOR -> woflo.petsplus.stats.nature.NatureFlavorHandler.Slot.MINOR;
                case QUIRK -> woflo.petsplus.stats.nature.NatureFlavorHandler.Slot.QUIRK;
            };
        }
    }

    /**
     * Lightweight context wrapper supplied during sign resolution.
     */
    public record AstrologyContext(Identifier dimensionId,
                                   int moonPhase,
                                   int dayOfYear,
                                   long timeOfDay,
                                   boolean indoors,
                                   boolean daytime,
                                   boolean raining,
                                   boolean thundering,
                                   boolean hasOpenSky,
                                   int nearbyPlayers,
                                   int nearbyPets,
                                   PetBreedEvent.BirthContext.Environment environment) {

        public boolean isDawn() {
            long t = timeOfDay % 24000L;
            return t >= 23000L || t <= 1000L;
        }

        public boolean isDusk() {
            long t = timeOfDay % 24000L;
            return t >= 12000L && t <= 14000L;
        }
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private int order;
        private String displayName;
        private Range dayRange;
        private DayWindow dayWindow = DayWindow.ANY;
        private WeatherWindow weatherWindow = WeatherWindow.ANY;
        private boolean requiresOpenSky;
        private boolean allowIndoors = true;
        private Set<Integer> moonPhases = Collections.emptySet();
        private Set<Identifier> allowedDimensions = Collections.emptySet();
        private Set<EnvironmentTag> requiredEnvironment = EnumSet.noneOf(EnvironmentTag.class);
        private NearbyConstraints nearbyConstraints = NearbyConstraints.ANY;
        private String displayEpithet;
        private DisplayStyle displayStyle = DisplayStyle.SUFFIX;
        private String displayConnector = " ";
        private StatProfile statProfile;
        private EmotionProfile emotionProfile;
        private List<AstrologyFlavorHook> flavorHooks = List.of();

        private Builder(Identifier id) {
            this.id = id;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder displayName(@Nullable String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder dayRange(Range range) {
            this.dayRange = range;
            return this;
        }

        public Builder dayWindow(DayWindow window) {
            this.dayWindow = window;
            return this;
        }

        public Builder weatherWindow(WeatherWindow window) {
            this.weatherWindow = window;
            return this;
        }

        public Builder requiresOpenSky(boolean value) {
            this.requiresOpenSky = value;
            return this;
        }

        public Builder allowIndoors(boolean value) {
            this.allowIndoors = value;
            return this;
        }

        public Builder moonPhases(Set<Integer> phases) {
            this.moonPhases = phases;
            return this;
        }

        public Builder allowedDimensions(Set<Identifier> dimensions) {
            this.allowedDimensions = dimensions;
            return this;
        }

        public Builder requiredEnvironment(Set<EnvironmentTag> environment) {
            this.requiredEnvironment = environment;
            return this;
        }

        public Builder nearbyConstraints(NearbyConstraints constraints) {
            this.nearbyConstraints = constraints;
            return this;
        }

        public Builder displayEpithet(@Nullable String epithet) {
            this.displayEpithet = epithet;
            return this;
        }

        public Builder displayStyle(DisplayStyle style) {
            if (style != null) {
                this.displayStyle = style;
            }
            return this;
        }

        public Builder displayConnector(@Nullable String connector) {
            if (connector != null) {
                this.displayConnector = connector;
            }
            return this;
        }

        public Builder statProfile(StatProfile profile) {
            this.statProfile = profile;
            return this;
        }

        public Builder emotionProfile(EmotionProfile profile) {
            this.emotionProfile = profile;
            return this;
        }

        public Builder flavorHooks(List<AstrologyFlavorHook> hooks) {
            this.flavorHooks = hooks;
            return this;
        }

        public AstrologySignDefinition build() {
            if (dayRange == null) {
                dayRange = new Range(0, 29, false);
            }
            if (statProfile == null) {
                throw new IllegalStateException("Missing stat profile for " + id);
            }
            if (emotionProfile == null) {
                throw new IllegalStateException("Missing emotion profile for " + id);
            }
            return new AstrologySignDefinition(
                id,
                order,
                displayName,
                dayRange,
                dayWindow,
                weatherWindow,
                requiresOpenSky,
                allowIndoors,
                moonPhases,
                allowedDimensions,
                requiredEnvironment,
                nearbyConstraints,
                displayEpithet,
                displayStyle,
                displayConnector,
                statProfile,
                emotionProfile,
                flavorHooks
            );
        }
    }
}
