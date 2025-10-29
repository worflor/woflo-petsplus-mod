package woflo.petsplus.naming;

import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import woflo.petsplus.naming.AttributeKey;

/**
 * Centralizes registration and handling metadata for special pet names.
 * Each entry wires parser patterns, optional affinity definitions, and
 * declarative state mutations so future additions only require data changes.
 */
public final class SpecialNameDefinitions {
    private static final String ATTRIBUTE_TYPE = "special_name";
    private static final int DEFAULT_PRIORITY = 5;
    private static final Object LOCK = new Object();
    private static final Map<String, RegisteredEntry> ENTRIES = new LinkedHashMap<>();
    private static volatile boolean bootstrapped;

    private SpecialNameDefinitions() {
    }

    /**
     * Seed the known special names. Safe to call multiple times.
     */
    public static void bootstrap() {
        synchronized (LOCK) {
            if (bootstrapped) {
                return;
            }
            bootstrapped = true;
        }

        register(
            new SpecialNameEntry(
                "woflo",
                List.of("woflo"),
                NameParser.MatchMode.WORD_BOUNDARY,
                DEFAULT_PRIORITY,
                true,
                Map.of(),
                "Your pet has been blessed with the developer's crown!",
                List.of()
            )
        );

        Map<String, Object> friendTag = Map.of("loch_n_load_tag", true);

        register(
            new SpecialNameEntry(
                "gabe",
                List.of("gabe"),
                NameParser.MatchMode.WORD_BOUNDARY,
                DEFAULT_PRIORITY,
                false,
                friendTag,
                null,
                List.of()
            )
        );

        register(
            new SpecialNameEntry(
                "loch",
                List.of("loch"),
                NameParser.MatchMode.WORD_BOUNDARY,
                DEFAULT_PRIORITY,
                false,
                friendTag,
                null,
                List.of()
            )
        );

        register(
            new SpecialNameEntry(
                "rei",
                List.of("rei"),
                NameParser.MatchMode.WORD_BOUNDARY,
                DEFAULT_PRIORITY,
                false,
                Map.of(),
                null,
                List.of(
                    NameAffinityDefinitions.RoleAffinityProfile.uniform(PetRoleType.GUARDIAN_ID, 0.10f),
                    NameAffinityDefinitions.RoleAffinityProfile.uniform(PetRoleType.SUPPORT_ID, 0.10f)
                )
            )
        );
    }

    /**
     * Register or replace a special name entry.
     *
     * @param entry metadata describing the special name
     * @return sanitized entry actually stored
     */
    @Nullable
    public static SpecialNameEntry register(SpecialNameEntry entry) {
        if (entry == null) {
            return null;
        }

        SpecialNameEntry sanitized = sanitize(entry);
        if (sanitized == null || sanitized.patterns().isEmpty() || sanitized.key().isEmpty()) {
            return null;
        }

        synchronized (LOCK) {
            RegisteredEntry previous = ENTRIES.get(sanitized.key());
            if (previous != null && previous.entry().equals(sanitized)) {
                return previous.entry();
            }

            if (previous != null) {
                unregisterPatterns(previous.patterns());
            }

            AttributeKey attributeKey = new AttributeKey(ATTRIBUTE_TYPE, sanitized.key(), sanitized.priority());
            List<PatternHandle> registeredPatterns = registerPatterns(sanitized, attributeKey);
            registerAffinity(sanitized, previous);

            RegisteredEntry stored = new RegisteredEntry(sanitized, registeredPatterns);
            ENTRIES.put(sanitized.key(), stored);
            return stored.entry();
        }
    }

    /**
     * Fetch metadata for the normalized key if present.
     */
    @Nullable
    public static SpecialNameEntry get(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        synchronized (LOCK) {
            RegisteredEntry entry = ENTRIES.get(key.trim().toLowerCase(Locale.ROOT));
            return entry == null ? null : entry.entry();
        }
    }

    /**
     * @return immutable snapshot of all registered entries.
     */
    public static List<SpecialNameEntry> allEntries() {
        synchronized (LOCK) {
            return ENTRIES.values().stream().map(RegisteredEntry::entry).toList();
        }
    }

    /**
     * Remove the entry associated with the provided key, if present.
     */
    public static boolean unregister(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        synchronized (LOCK) {
            RegisteredEntry removed = ENTRIES.remove(key.trim().toLowerCase(Locale.ROOT));
            if (removed == null) {
                return false;
            }

            unregisterPatterns(removed.patterns());
            if (!removed.entry().affinityProfiles().isEmpty()) {
                NameAffinityDefinitions.unregister(removed.entry().key());
            }
            return true;
        }
    }

    private static List<PatternHandle> registerPatterns(SpecialNameEntry entry, AttributeKey attributeKey) {
        List<PatternHandle> registered = new ArrayList<>(entry.patterns().size());
        for (String pattern : entry.patterns()) {
            NameParser.registerExactPattern(pattern, attributeKey, entry.matchMode());
            registered.add(new PatternHandle(pattern, attributeKey));
        }
        return List.copyOf(registered);
    }

    private static void registerAffinity(SpecialNameEntry entry, @Nullable RegisteredEntry previous) {
        List<NameAffinityDefinitions.RoleAffinityProfile> profiles = entry.affinityProfiles();
        List<NameAffinityDefinitions.RoleAffinityProfile> previousProfiles = previous == null
            ? List.of()
            : previous.entry().affinityProfiles();

        if (profiles.equals(previousProfiles)) {
            return;
        }

        if (profiles.isEmpty()) {
            if (!previousProfiles.isEmpty()) {
                NameAffinityDefinitions.unregister(entry.key());
            }
            return;
        }

        NameAffinityDefinitions.setDefinitions(entry.key(), profiles);
    }

    private static void unregisterPatterns(List<PatternHandle> patterns) {
        for (PatternHandle handle : patterns) {
            NameParser.unregisterExactPattern(handle.pattern(), handle.attribute().normalizedType(), handle.attribute().normalizedValue());
        }
    }

    @Nullable
    private static SpecialNameEntry sanitize(SpecialNameEntry entry) {
        String normalizedKey = normalize(entry.key());
        if (normalizedKey.isEmpty()) {
            return null;
        }

        Set<String> sanitizedPatterns = new LinkedHashSet<>();
        for (String pattern : entry.patterns()) {
            if (pattern == null) {
                continue;
            }
            String sanitized = pattern.trim();
            if (!sanitized.isEmpty()) {
                sanitizedPatterns.add(sanitized);
            }
        }

        NameParser.MatchMode mode = entry.matchMode() == null
            ? NameParser.MatchMode.WORD_BOUNDARY
            : entry.matchMode();

        int priority = Math.max(entry.priority(), 0);

        Map<String, Object> sanitizedState = new LinkedHashMap<>();
        for (Map.Entry<String, Object> stateEntry : entry.stateData().entrySet()) {
            String key = stateEntry.getKey();
            Object value = stateEntry.getValue();
            if (key == null || key.isBlank() || value == null) {
                continue;
            }
            sanitizedState.put(key.trim(), value);
        }

        String message = entry.ownerMessage();
        if (message != null && message.isBlank()) {
            message = null;
        }

        List<NameAffinityDefinitions.RoleAffinityProfile> sanitizedProfiles = new ArrayList<>();
        for (NameAffinityDefinitions.RoleAffinityProfile profile : entry.affinityProfiles()) {
            if (profile != null) {
                sanitizedProfiles.add(profile);
            }
        }

        return new SpecialNameEntry(
            normalizedKey,
            List.copyOf(sanitizedPatterns),
            mode,
            priority,
            entry.grantsCreatorCrown(),
            sanitizedState.isEmpty() ? Map.of() : Map.copyOf(sanitizedState),
            message,
            sanitizedProfiles.isEmpty() ? List.of() : List.copyOf(sanitizedProfiles)
        );
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Immutable description of a special name and its behavior.
     */
    public record SpecialNameEntry(
        String key,
        List<String> patterns,
        NameParser.MatchMode matchMode,
        int priority,
        boolean grantsCreatorCrown,
        Map<String, Object> stateData,
        @Nullable String ownerMessage,
        List<NameAffinityDefinitions.RoleAffinityProfile> affinityProfiles
    ) {
        public SpecialNameEntry {
            Objects.requireNonNull(patterns, "patterns");
            Objects.requireNonNull(stateData, "stateData");
            Objects.requireNonNull(affinityProfiles, "affinityProfiles");
        }
    }

    private record RegisteredEntry(SpecialNameEntry entry, List<PatternHandle> patterns) {
    }

    private record PatternHandle(String pattern, AttributeKey attribute) {
    }
}
