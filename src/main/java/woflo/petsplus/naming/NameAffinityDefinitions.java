package woflo.petsplus.naming;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.stats.PetImprint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for name-driven affinity definitions. This allows name parsing
 * to remain data-oriented while the attribute registry simply looks up the
 * configured affinity vectors.
 */
public final class NameAffinityDefinitions {
    private static final float EPSILON = 1.0e-6f;
    private static final String ATTRIBUTE_TYPE = "name_affinity";
    private static final int ATTRIBUTE_PRIORITY = 5;
    private static final Map<String, List<RoleAffinityProfile>> DEFINITIONS = new ConcurrentHashMap<>();

    static {
        registerUniformSet(
            "rei",
            Map.of(
                PetRoleType.GUARDIAN_ID, 0.10f,
                PetRoleType.SUPPORT_ID, 0.10f
            )
        );
    }

    private NameAffinityDefinitions() {
    }

    /**
     * Register one or more role affinity profiles for the provided key.
     */
    public static void register(String key, RoleAffinityProfile... profiles) {
        if (key == null || key.isBlank() || profiles == null || profiles.length == 0) {
            return;
        }

        String normalizedKey = normalizeKey(key);
        List<RoleAffinityProfile> sanitized = sanitizeProfiles(Arrays.asList(profiles));
        if (sanitized.isEmpty()) {
            return;
        }

        DEFINITIONS.compute(normalizedKey, (ignored, existing) -> {
            List<RoleAffinityProfile> merged = new ArrayList<>();
            if (existing != null) {
                merged.addAll(existing);
            }
            merged.addAll(sanitized);
            if (merged.isEmpty()) {
                unregisterPattern(key);
                return null;
            }

            registerPattern(key, normalizedKey);
            return List.copyOf(merged);
        });
    }

    /**
     * Convenience helper for registering uniform affinity bonuses derived from a map of role IDs.
     */
    public static void registerUniformSet(String key, Map<Identifier, Float> roleBonuses) {
        if (key == null || key.isBlank()) {
            return;
        }

        if (roleBonuses == null || roleBonuses.isEmpty()) {
            unregister(key);
            return;
        }

        List<RoleAffinityProfile> profiles = new ArrayList<>(roleBonuses.size());
        for (Map.Entry<Identifier, Float> entry : roleBonuses.entrySet()) {
            Identifier roleId = entry.getKey();
            Float bonus = entry.getValue();
            if (roleId == null || bonus == null) {
                continue;
            }

            float value = bonus;
            if (Math.abs(value) <= EPSILON) {
                continue;
            }

            profiles.add(RoleAffinityProfile.uniform(roleId, value));
        }

        if (profiles.isEmpty()) {
            unregister(key);
            return;
        }

        setDefinitions(key, profiles);
    }

    /**
     * Resolve the affinity vectors for the given key. The resulting vectors are
     * tailored to the currently registered role definitions.
     */
    public static List<RoleAffinityVector> resolveVectors(String key) {
        if (key == null || key.isBlank()) {
            return List.of();
        }

        String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
        List<RoleAffinityProfile> profiles = DEFINITIONS.get(normalizedKey);
        if (profiles == null || profiles.isEmpty()) {
            return List.of();
        }

        List<RoleAffinityVector> vectors = new ArrayList<>();
        for (RoleAffinityProfile profile : profiles) {
            RoleAffinityVector vector = createVector(profile);
            if (vector != null) {
                vectors.add(vector);
            }
        }
        return vectors.isEmpty() ? List.of() : List.copyOf(vectors);
    }

    /**
     * Replace the affinity profiles registered for a key.
     */
    public static void setDefinitions(String key, Collection<RoleAffinityProfile> profiles) {
        if (key == null || key.isBlank()) {
            return;
        }

        String normalizedKey = normalizeKey(key);
        if (profiles == null || profiles.isEmpty()) {
            removeDefinitions(normalizedKey, key);
            return;
        }

        List<RoleAffinityProfile> sanitized = sanitizeProfiles(profiles);
        if (sanitized.isEmpty()) {
            removeDefinitions(normalizedKey, key);
            return;
        }

        DEFINITIONS.put(normalizedKey, List.copyOf(sanitized));
        registerPattern(key, normalizedKey);
    }

    /**
     * Unregister all affinity definitions for the provided key.
     */
    public static void unregister(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        String normalizedKey = normalizeKey(key);
        removeDefinitions(normalizedKey, key);
    }

    private static void removeDefinitions(String normalizedKey, String key) {
        List<RoleAffinityProfile> removed = DEFINITIONS.remove(normalizedKey);
        if (removed != null && !removed.isEmpty()) {
            unregisterPattern(key);
        }
    }

    private static List<RoleAffinityProfile> sanitizeProfiles(Collection<RoleAffinityProfile> profiles) {
        List<RoleAffinityProfile> sanitized = new ArrayList<>();
        for (RoleAffinityProfile profile : profiles) {
            RoleAffinityProfile normalized = RoleAffinityProfile.normalized(profile);
            if (normalized != null) {
                sanitized.add(normalized);
            }
        }
        return sanitized;
    }

    private static String normalizeKey(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }

    private static void registerPattern(String key, String normalizedKey) {
        String patternKey = key.trim().isEmpty() ? normalizedKey : key.trim();
        NameParser.registerExactPattern(
            patternKey,
            new AttributeKey(ATTRIBUTE_TYPE, normalizedKey, ATTRIBUTE_PRIORITY),
            NameParser.MatchMode.EXACT
        );
    }

    private static void unregisterPattern(String key) {
        NameParser.unregisterExactPattern(key);
    }

    @Nullable
    private static RoleAffinityVector createVector(RoleAffinityProfile profile) {
        if (profile == null) {
            return null;
        }

        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(profile.roleId());
        if (roleType == null) {
            return null;
        }

        float defaultBonus = profile.defaultBonus();
        Map<String, Float> overrides = profile.statBonuses();
        boolean hasDefault = Math.abs(defaultBonus) > EPSILON;
        boolean hasOverrides = !overrides.isEmpty();

        if (!hasDefault && !hasOverrides) {
            return null;
        }

        List<String> baseKeys;
        if (hasDefault) {
            if (!roleType.statAffinities().isEmpty()) {
                baseKeys = new ArrayList<>(roleType.statAffinities().keySet());
            } else {
                baseKeys = new ArrayList<>(Arrays.asList(PetImprint.statKeyArray()));
            }
        } else {
            baseKeys = List.of();
        }

        Set<String> seen = new LinkedHashSet<>(baseKeys);
        for (String key : overrides.keySet()) {
            if (key != null && !key.isBlank()) {
                seen.add(key);
            }
        }

        if (seen.isEmpty()) {
            return null;
        }

        List<String> orderedKeys = new ArrayList<>(seen.size());
        orderedKeys.addAll(baseKeys);
        for (String key : seen) {
            if (!orderedKeys.contains(key)) {
                orderedKeys.add(key);
            }
        }

        String[] statKeys = orderedKeys.toArray(String[]::new);
        float[] bonuses = new float[statKeys.length];

        Set<String> defaultSet = new LinkedHashSet<>(baseKeys);
        boolean mutated = false;
        for (int i = 0; i < statKeys.length; i++) {
            String statKey = statKeys[i];
            float value = defaultSet.contains(statKey) ? defaultBonus : 0.0f;
            Float override = overrides.get(statKey);
            if (override != null) {
                value = override;
            }
            bonuses[i] = value;
            mutated |= Math.abs(value) > EPSILON;
        }

        if (!mutated) {
            return null;
        }

        return new RoleAffinityVector(profile.roleId(), statKeys, bonuses);
    }

    /**
     * Immutable description of a role affinity vector ready to apply to a component.
     */
    public record RoleAffinityVector(Identifier roleId, String[] statKeys, float[] bonuses) {
        public RoleAffinityVector {
            Objects.requireNonNull(roleId, "roleId");
            statKeys = statKeys == null ? new String[0] : statKeys.clone();
            bonuses = bonuses == null ? new float[0] : bonuses.clone();
        }
    }

    /**
     * Definition used to construct role affinity vectors.
     */
    public record RoleAffinityProfile(Identifier roleId, float defaultBonus, Map<String, Float> statBonuses) {
        public RoleAffinityProfile {
            Objects.requireNonNull(roleId, "roleId");
            statBonuses = sanitize(statBonuses);
        }

        private static Map<String, Float> sanitize(Map<String, Float> source) {
            if (source == null || source.isEmpty()) {
                return Map.of();
            }

            Map<String, Float> sanitized = new LinkedHashMap<>();
            for (Map.Entry<String, Float> entry : source.entrySet()) {
                String key = entry.getKey();
                Float value = entry.getValue();
                if (key == null || key.isBlank() || value == null || Math.abs(value) <= EPSILON) {
                    continue;
                }
                sanitized.put(key.toLowerCase(Locale.ROOT), value);
            }
            return sanitized.isEmpty() ? Map.of() : Collections.unmodifiableMap(sanitized);
        }

        @Nullable
        static RoleAffinityProfile normalized(RoleAffinityProfile profile) {
            if (profile == null) {
                return null;
            }
            if (profile.statBonuses().isEmpty() && Math.abs(profile.defaultBonus()) <= EPSILON) {
                return null;
            }
            return profile;
        }

        public static RoleAffinityProfile uniform(Identifier roleId, float bonus) {
            return new RoleAffinityProfile(roleId, bonus, Map.of());
        }

        public static RoleAffinityProfile targeted(Identifier roleId, Map<String, Float> statBonuses) {
            return new RoleAffinityProfile(roleId, 0.0f, statBonuses);
        }
    }
}
