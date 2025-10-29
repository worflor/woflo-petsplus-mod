package woflo.petsplus.naming;

import woflo.petsplus.Petsplus;
import woflo.petsplus.config.PetsPlusConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses pet names to extract attribute keys based on configured patterns.
 * Supports exact matches, prefix patterns, and regex patterns.
 */
public class NameParser {
    private static final Map<UUID, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;
    private static final AtomicLong CACHE_VERSION = new AtomicLong();

    // Built-in attribute patterns - these are examples and can be expanded via config
    private static final Map<String, Map<String, PatternEntry>> EXACT_PATTERNS = new ConcurrentHashMap<>();

    static {
        registerExactPattern("brave", new AttributeKey("courage", "brave", 1));
        registerExactPattern("swift", new AttributeKey("speed", "swift", 1));
        registerExactPattern("fierce", new AttributeKey("combat", "fierce", 2));
        registerExactPattern("gentle", new AttributeKey("temperament", "gentle", 1));
        registerExactPattern("loyal", new AttributeKey("loyalty", "loyal", 1));
        registerExactPattern("clever", new AttributeKey("intelligence", "clever", 1));
        registerExactPattern("strong", new AttributeKey("strength", "strong", 1));
        registerExactPattern("wise", new AttributeKey("wisdom", "wise", 2));
        SpecialNameDefinitions.bootstrap();
    }

    private static final Map<String, AttributeKey> PREFIX_PATTERNS = Map.of(
        "fire", new AttributeKey("element", "fire", 2),
        "ice", new AttributeKey("element", "ice", 2),
        "storm", new AttributeKey("element", "storm", 2),
        "shadow", new AttributeKey("element", "shadow", 2),
        "light", new AttributeKey("element", "light", 2)
    );

    // Regex patterns for more complex matching
    private static final List<PatternMatcher> REGEX_PATTERNS = List.of(
        new PatternMatcher(Pattern.compile("lv(\\d+)", Pattern.CASE_INSENSITIVE),
            (matcher) -> new AttributeKey("level", matcher.group(1), 3)),
        new PatternMatcher(Pattern.compile("(\\d+)star", Pattern.CASE_INSENSITIVE),
            (matcher) -> new AttributeKey("rating", matcher.group(1), 2)),
        new PatternMatcher(Pattern.compile("gen(\\d+)", Pattern.CASE_INSENSITIVE),
            (matcher) -> new AttributeKey("generation", matcher.group(1), 1))
    );

    /**
     * Parses a pet name and extracts attribute keys.
     *
     * @param rawName The raw pet name to parse
     * @return List of parsed attribute keys, limited by config max_attributes
     */
    public static List<AttributeKey> parse(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return new ArrayList<>();
        }

        PetsPlusConfig config = PetsPlusConfig.getInstance();
        if (!config.isNamedAttributesEnabled()) {
            return new ArrayList<>();
        }

        String cleanName = sanitizeName(rawName);
        boolean caseSensitive = config.isNamedAttributesCaseSensitive();

        List<AttributeKey> attributes = new ArrayList<>();

        // Parse with exact patterns
        if (config.isExactPatternsEnabled()) {
            attributes.addAll(parseExactPatterns(cleanName, caseSensitive));
        }

        // Parse with prefix patterns
        if (config.isPrefixPatternsEnabled()) {
            attributes.addAll(parsePrefixPatterns(cleanName, caseSensitive));
        }

        // Parse with regex patterns
        if (config.isRegexPatternsEnabled()) {
            attributes.addAll(parseRegexPatterns(cleanName));
        }

        // Remove duplicates and apply priority/limits
        return processAttributes(attributes, config.getMaxNamedAttributes());
    }

    /**
     * Parses a name with caching for performance.
     *
     * @param entityUuid The UUID of the entity for caching
     * @param rawName    The raw pet name to parse
     * @return Cached or newly parsed attribute keys
     */
    public static List<AttributeKey> parseWithCache(UUID entityUuid, String rawName) {
        if (entityUuid == null) {
            return parse(rawName);
        }

        String sanitizedName = sanitizeName(rawName);
        long currentVersion = CACHE_VERSION.get();

        CacheEntry cached = CACHE.get(entityUuid);
        if (cached != null && cached.matches(sanitizedName, currentVersion)) {
            return cached.copyAttributes();
        }

        // Parse and cache result
        List<AttributeKey> result = parse(rawName);
        CacheEntry entry = new CacheEntry(sanitizedName, result, currentVersion);

        // Manage cache size
        if (CACHE.size() >= MAX_CACHE_SIZE) {
            CACHE.clear(); // Simple cache eviction strategy
        }

        CACHE.put(entityUuid, entry);
        return new ArrayList<>(result);
    }

    /**
     * Clears the parsing cache for a specific entity.
     */
    public static void clearCache(UUID entityUuid) {
        if (entityUuid != null) {
            CACHE.remove(entityUuid);
        }
    }

    /**
     * Clears the entire parsing cache.
     */
    public static void clearAllCache() {
        CACHE.clear();
    }

    private static void touchCacheVersion() {
        CACHE_VERSION.incrementAndGet();
    }

    private static String sanitizeName(String name) {
        if (name == null) return "";

        // Remove common formatting codes and special characters
        String clean = name.replaceAll("§[0-9a-fk-or]", ""); // Minecraft color codes
        clean = clean.replaceAll("[\\[\\]{}()<>]", ""); // Brackets
        clean = clean.trim();

        return clean;
    }

    private static List<AttributeKey> parseExactPatterns(String name, boolean caseSensitive) {
        List<AttributeKey> attributes = new ArrayList<>();
        String searchName = caseSensitive ? name : name.toLowerCase();

        for (Map<String, PatternEntry> entries : EXACT_PATTERNS.values()) {
            for (PatternEntry entry : entries.values()) {
                AttributeKey attribute = entry.attribute();
                String pattern = caseSensitive ? entry.pattern() : entry.pattern().toLowerCase();

                boolean matched = switch (entry.mode()) {
                    case EXACT -> searchName.equals(pattern);
                    case WORD_BOUNDARY -> matchesWordBoundary(searchName, pattern);
                    case SUBSTRING -> searchName.contains(pattern);
                };

                if (matched) {
                    attributes.add(attribute);
                    Petsplus.LOGGER.debug(
                        "Found exact pattern '{}' via {} match in name '{}'",
                        entry.pattern(),
                        entry.mode().name().toLowerCase(Locale.ROOT),
                        name
                    );
                }
            }
        }

        return attributes;
    }

    /**
     * Register an exact pattern dynamically.
     *
     * @param pattern   The literal pattern to search for
     * @param attribute The attribute produced when the pattern is found
     */
    public static void registerExactPattern(String pattern, AttributeKey attribute) {
        registerExactPattern(pattern, attribute, MatchMode.SUBSTRING);
    }

    /**
     * Register an exact pattern dynamically with a specific match mode.
     */
    public static void registerExactPattern(String pattern, AttributeKey attribute, MatchMode mode) {
        if (pattern == null || pattern.trim().isEmpty() || attribute == null) {
            return;
        }

        String normalized = normalizePattern(pattern);
        MatchMode resolvedMode = mode == null ? MatchMode.SUBSTRING : mode;
        PatternEntry entry = new PatternEntry(pattern.trim(), attribute, resolvedMode);

        Map<String, PatternEntry> bucket = EXACT_PATTERNS.computeIfAbsent(normalized, ignored -> new ConcurrentHashMap<>());
        PatternEntry previous = bucket.put(attributeKey(attribute), entry);
        if (!entry.equals(previous)) {
            touchCacheVersion();
        }
    }

    /**
     * Unregister a previously registered exact pattern.
     *
     * @param pattern The literal pattern that was registered
     */
    public static void unregisterExactPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return;
        }

        String normalized = normalizePattern(pattern);
        Map<String, PatternEntry> removed = EXACT_PATTERNS.remove(normalized);
        if (removed != null && !removed.isEmpty()) {
            touchCacheVersion();
        }
    }

    /**
     * Unregister a specific attribute bound to the provided literal pattern.
     *
     * @param pattern       the literal pattern that was registered
     * @param attributeType the normalized attribute type to remove
     */
    public static void unregisterExactPattern(String pattern, String attributeType) {
        unregisterExactPattern(pattern, attributeType, null);
    }

    /**
     * Unregister a specific attribute bound to the provided literal pattern.
     *
     * @param pattern        the literal pattern that was registered
     * @param attributeType  the normalized attribute type to remove
     * @param attributeValue optional normalized attribute value constraint
     */
    public static void unregisterExactPattern(String pattern, String attributeType, String attributeValue) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return;
        }
        if (attributeType == null || attributeType.trim().isEmpty()) {
            unregisterExactPattern(pattern);
            return;
        }

        String normalizedPattern = normalizePattern(pattern);
        Map<String, PatternEntry> bucket = EXACT_PATTERNS.get(normalizedPattern);
        if (bucket == null || bucket.isEmpty()) {
            return;
        }

        String normalizedType = attributeType.trim().toLowerCase(Locale.ROOT);
        String normalizedValue = attributeValue == null || attributeValue.trim().isEmpty()
            ? null
            : attributeValue.trim().toLowerCase(Locale.ROOT);

        boolean removed = false;
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, PatternEntry> entry : bucket.entrySet()) {
            AttributeKey attribute = entry.getValue().attribute();
            if (!attribute.normalizedType().equals(normalizedType)) {
                continue;
            }
            if (normalizedValue != null && !attribute.normalizedValue().equals(normalizedValue)) {
                continue;
            }
            keysToRemove.add(entry.getKey());
        }

        if (!keysToRemove.isEmpty()) {
            removed = true;
            for (String key : keysToRemove) {
                bucket.remove(key);
            }
        }

        if (bucket.isEmpty()) {
            EXACT_PATTERNS.remove(normalizedPattern);
        }

        if (removed) {
            touchCacheVersion();
        }
    }

    private static String attributeKey(AttributeKey attribute) {
        return attribute.normalizedType() + "|" + attribute.normalizedValue();
    }

    private static String normalizePattern(String pattern) {
        return pattern.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesWordBoundary(String searchName, String pattern) {
        if (pattern.isEmpty()) {
            return false;
        }

        int index = searchName.indexOf(pattern);
        while (index >= 0) {
            boolean startOk = index == 0 || !Character.isLetterOrDigit(searchName.charAt(index - 1));
            int endIndex = index + pattern.length();
            boolean endOk = endIndex == searchName.length() || !Character.isLetterOrDigit(searchName.charAt(endIndex));
            if (startOk && endOk) {
                return true;
            }
            index = searchName.indexOf(pattern, index + 1);
        }
        return false;
    }

    private record PatternEntry(String pattern, AttributeKey attribute, MatchMode mode) {
    }

    public enum MatchMode {
        SUBSTRING,
        EXACT,
        WORD_BOUNDARY
    }

    private record CacheEntry(String sanitizedName, List<AttributeKey> attributes, long version) {
        CacheEntry(String sanitizedName, List<AttributeKey> attributes, long version) {
            this.sanitizedName = sanitizedName;
            this.attributes = List.copyOf(attributes);
            this.version = version;
        }

        boolean matches(String candidate, long currentVersion) {
            if (currentVersion != this.version) {
                return false;
            }
            if (sanitizedName == null) {
                return candidate == null || candidate.isEmpty();
            }
            return sanitizedName.equals(candidate);
        }

        List<AttributeKey> copyAttributes() {
            return new ArrayList<>(attributes);
        }
    }

    private static List<AttributeKey> parsePrefixPatterns(String name, boolean caseSensitive) {
        List<AttributeKey> attributes = new ArrayList<>();
        String searchName = caseSensitive ? name : name.toLowerCase();

        // Split name into words for prefix matching
        String[] words = searchName.split("\\s+");

        for (String word : words) {
            for (Map.Entry<String, AttributeKey> entry : PREFIX_PATTERNS.entrySet()) {
                String prefix = caseSensitive ? entry.getKey() : entry.getKey().toLowerCase();

                if (word.startsWith(prefix)) {
                    attributes.add(entry.getValue());
                    Petsplus.LOGGER.debug("Found prefix pattern '{}' in word '{}' from name '{}'",
                        prefix, word, name);
                }
            }
        }

        return attributes;
    }

    private static List<AttributeKey> parseRegexPatterns(String name) {
        List<AttributeKey> attributes = new ArrayList<>();

        for (PatternMatcher patternMatcher : REGEX_PATTERNS) {
            Matcher matcher = patternMatcher.pattern().matcher(name);

            while (matcher.find()) {
                try {
                    AttributeKey attribute = patternMatcher.handler().apply(matcher);
                    if (attribute != null) {
                        attributes.add(attribute);
                        Petsplus.LOGGER.debug("Found regex pattern match '{}' in name '{}'",
                            matcher.group(), name);
                    }
                } catch (Exception e) {
                    Petsplus.LOGGER.warn("Error processing regex pattern in name '{}': {}", name, e.getMessage());
                }
            }
        }

        return attributes;
    }

    private static List<AttributeKey> processAttributes(List<AttributeKey> attributes, int maxAttributes) {
        if (attributes.isEmpty()) {
            return attributes;
        }

        // Remove duplicates by type, keeping highest priority
        Map<String, AttributeKey> uniqueAttributes = new HashMap<>();

        for (AttributeKey attr : attributes) {
            String type = attr.normalizedType();
            AttributeKey existing = uniqueAttributes.get(type);

            if (existing == null || attr.priority() > existing.priority()) {
                uniqueAttributes.put(type, attr);
            }
        }

        // Convert back to list and sort by priority (highest first)
        List<AttributeKey> result = new ArrayList<>(uniqueAttributes.values());
        result.sort((a, b) -> Integer.compare(b.priority(), a.priority()));

        // Apply max attributes limit
        if (result.size() > maxAttributes) {
            result = result.subList(0, maxAttributes);
        }

        return result;
    }

    /**
     * Helper record for regex pattern matching.
     */
    private record PatternMatcher(Pattern pattern, MatcherHandler handler) {}

    /**
     * Functional interface for handling regex matches.
     */
    @FunctionalInterface
    private interface MatcherHandler {
        AttributeKey apply(Matcher matcher);
    }
}
