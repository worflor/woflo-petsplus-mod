package woflo.petsplus.ai.goals.special.survey;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Registry describing the structures and points of interest that aerial survey
 * goals should try to highlight. Definitions are loaded from data packs so
 * designers can retune the catalogue without touching code. A small baked-in
 * fallback list keeps behaviour functional when data is missing or fails to
 * load, but datapacks always fully replace the active list on successful
 * reload.
 */
public final class SurveyTargetRegistry {

    private static final List<SurveyTarget> BUILT_IN_DEFAULTS = List.of(
        new SurveyTarget(
            Identifier.of("petsplus", "survey/villages"),
            Kind.STRUCTURE_TAG,
            Identifier.of("minecraft", "overworld"),
            1.0f,
            64.0,
            320.0,
            256,
            true,
            1200
        ),
        new SurveyTarget(
            Identifier.of("petsplus", "survey/trail_ruins"),
            Kind.STRUCTURE_TAG,
            Identifier.of("minecraft", "overworld"),
            0.85f,
            48.0,
            256.0,
            224,
            true,
            1400
        ),
        new SurveyTarget(
            Identifier.of("petsplus", "survey/ancient_cities"),
            Kind.STRUCTURE_TAG,
            Identifier.of("minecraft", "overworld"),
            0.65f,
            96.0,
            480.0,
            320,
            true,
            1800
        ),
        new SurveyTarget(
            Identifier.of("petsplus", "survey/shipwrecks"),
            Kind.STRUCTURE_TAG,
            Identifier.of("minecraft", "overworld"),
            0.55f,
            48.0,
            280.0,
            256,
            true,
            1600
        ),
        new SurveyTarget(
            Identifier.of("petsplus", "survey/monuments"),
            Kind.STRUCTURE_TAG,
            Identifier.of("minecraft", "overworld"),
            0.5f,
            96.0,
            420.0,
            320,
            true,
            2000
        )
    );

    private static volatile List<SurveyTarget> activeTargets = BUILT_IN_DEFAULTS;

    private SurveyTargetRegistry() {
    }

    /**
     * Replace the active catalogue with the provided list. If the list is null
     * or empty the registry reverts to the baked-in defaults.
     */
    public static synchronized void reload(@Nullable List<SurveyTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            activeTargets = BUILT_IN_DEFAULTS;
            Petsplus.LOGGER.debug("Survey target registry reverting to built-in defaults ({} entries)",
                BUILT_IN_DEFAULTS.size());
            return;
        }
        activeTargets = List.copyOf(targets);
        Petsplus.LOGGER.debug("Loaded {} aerial survey targets from data", activeTargets.size());
    }

    /**
     * Returns the active list of survey targets filtered to the given
     * dimension. Callers should treat the returned list as immutable.
     */
    public static List<SurveyTarget> targetsFor(@Nullable RegistryKey<World> dimensionKey) {
        List<SurveyTarget> snapshot = activeTargets;
        if (snapshot.isEmpty()) {
            return List.of();
        }
        if (dimensionKey == null) {
            return snapshot;
        }
        Identifier dimensionId = dimensionKey.getValue();
        List<SurveyTarget> filtered = new ArrayList<>();
        for (SurveyTarget target : snapshot) {
            if (target.dimension() == null || Objects.equals(target.dimension(), dimensionId)) {
                filtered.add(target);
            }
        }
        return filtered.isEmpty() ? List.of() : Collections.unmodifiableList(filtered);
    }

    /**
     * Description of a surveyable point of interest.
     */
    public record SurveyTarget(
        Identifier id,
        Kind kind,
        @Nullable Identifier dimension,
        float weight,
        double minDistance,
        double maxDistance,
        int searchRadius,
        boolean skipKnown,
        int recacheCooldownTicks
    ) {
        public SurveyTarget {
            if (id == null) {
                throw new IllegalArgumentException("Survey target id cannot be null");
            }
            if (kind == null) {
                throw new IllegalArgumentException("Survey target kind cannot be null");
            }
            if (weight <= 0f || !Float.isFinite(weight)) {
                weight = 1.0f;
            }
            if (minDistance < 0.0) {
                minDistance = 0.0;
            }
            if (maxDistance <= 0.0) {
                maxDistance = Math.max(minDistance + 1.0, 128.0);
            }
            if (searchRadius <= 0) {
                searchRadius = 192;
            }
            if (recacheCooldownTicks < 0) {
                recacheCooldownTicks = 0;
            }
        }

        public boolean matchesDimension(@Nullable RegistryKey<World> key) {
            if (dimension == null || key == null) {
                return true;
            }
            return Objects.equals(dimension, key.getValue());
        }
    }

    /**
     * Supported target kinds. Structure tags are used so datapacks can group
     * structures without touching code. Additional kinds can be introduced in
     * the future if the locate API grows more hooks.
     */
    public enum Kind {
        STRUCTURE_TAG;

        public static Kind fromString(String raw) {
            if (raw == null || raw.isEmpty()) {
                throw new IllegalArgumentException("Target kind cannot be null/empty");
            }
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "structure_tag" -> STRUCTURE_TAG;
                default -> throw new IllegalArgumentException("Unknown survey target kind '" + raw + "'");
            };
        }
    }
}
