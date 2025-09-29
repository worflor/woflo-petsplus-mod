package woflo.petsplus.naming;

import woflo.petsplus.Petsplus;
import woflo.petsplus.config.PetsPlusConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses pet names to extract attribute keys based on configured patterns.
 * Supports exact matches, prefix patterns, and regex patterns.
 */
public class NameParser {
    private static final Map<UUID, List<AttributeKey>> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    // Built-in attribute patterns - these are examples and can be expanded via config
    private static final Map<String, AttributeKey> EXACT_PATTERNS = Map.ofEntries(
        Map.entry("brave", new AttributeKey("courage", "brave", 1)),
        Map.entry("swift", new AttributeKey("speed", "swift", 1)),
        Map.entry("fierce", new AttributeKey("combat", "fierce", 2)),
        Map.entry("gentle", new AttributeKey("temperament", "gentle", 1)),
        Map.entry("loyal", new AttributeKey("loyalty", "loyal", 1)),
        Map.entry("clever", new AttributeKey("intelligence", "clever", 1)),
        Map.entry("strong", new AttributeKey("strength", "strong", 1)),
        Map.entry("wise", new AttributeKey("wisdom", "wise", 2)),
        Map.entry("woflo", new AttributeKey("creator", "woflo", 3))
    );

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

        // Check cache first
        List<AttributeKey> cached = CACHE.get(entityUuid);
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        // Parse and cache result
        List<AttributeKey> result = parse(rawName);

        // Manage cache size
        if (CACHE.size() >= MAX_CACHE_SIZE) {
            CACHE.clear(); // Simple cache eviction strategy
        }

        CACHE.put(entityUuid, new ArrayList<>(result));
        return result;
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

        for (Map.Entry<String, AttributeKey> entry : EXACT_PATTERNS.entrySet()) {
            String pattern = caseSensitive ? entry.getKey() : entry.getKey().toLowerCase();

            if (searchName.contains(pattern)) {
                attributes.add(entry.getValue());
                Petsplus.LOGGER.debug("Found exact pattern '{}' in name '{}'", pattern, name);
            }
        }

        return attributes;
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