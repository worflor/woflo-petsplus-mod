package woflo.petsplus.state.gossip;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Utility class that houses shared gossip topic identifiers so we can talk
 * about broad subjects (combat, exploration, etc.) without sprinkling hard
 * coded longs throughout the codebase. The identifiers are derived from stable
 * name-based UUIDs so datapacks or future systems can reference the same
 * values without code changes.
 */
public final class GossipTopics {

    private static final String ABSTRACT_PREFIX = "petsplus:gossip/abstract/";
    private static final String CONCRETE_PREFIX = "petsplus:gossip/concrete/";

    public static final long OWNER_KILL_HOSTILE = concrete("combat/owner_kill_hostile");
    public static final long OWNER_KILL_PASSIVE = concrete("combat/owner_kill_passive");
    public static final long OWNER_KILL_BOSS = concrete("combat/owner_kill_boss");
    public static final long EXPLORE_NEW_BIOME = concrete("exploration/new_biome");
    public static final long ENTER_NETHER = concrete("exploration/dimension/nether");
    public static final long ENTER_END = concrete("exploration/dimension/end");
    public static final long RETURN_FROM_DIMENSION = concrete("exploration/dimension/return");
    public static final long TRADE_GENERIC = concrete("life/trade_generic");
    public static final long TRADE_FOOD = concrete("life/trade_food");
    public static final long TRADE_MYSTIC = concrete("life/trade_mystic");
    public static final long TRADE_GUARD = concrete("life/trade_guard");
    public static final long SOCIAL_CAMPFIRE = concrete("social/campfire_moment");

    private GossipTopics() {
    }

    public static Optional<AbstractTopic> findAbstract(long topicId) {
        return Optional.ofNullable(AbstractTopic.BY_ID.get(topicId));
    }

    public static boolean isAbstract(long topicId) {
        return AbstractTopic.BY_ID.containsKey(topicId);
    }

    private static long computeId(String key) {
        UUID uuid = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return most ^ least;
    }

    public static long concrete(String key) {
        return computeId(CONCRETE_PREFIX + key.toLowerCase(Locale.ROOT));
    }
    
    /**
     * Get a human-readable name for a topic ID.
     * Returns a formatted name for known topics, or "Unknown" for unrecognized IDs.
     */
    public static String getTopicName(long topicId) {
        // Check abstract topics first
        Optional<AbstractTopic> abstractTopic = findAbstract(topicId);
        if (abstractTopic.isPresent()) {
            AbstractTopic topic = abstractTopic.get();
            return formatName(topic.name());
        }
        
        // Check known concrete topics
        if (topicId == OWNER_KILL_HOSTILE) return "Combat Victory";
        if (topicId == OWNER_KILL_PASSIVE) return "Gentle Moment";
        if (topicId == OWNER_KILL_BOSS) return "Epic Battle";
        if (topicId == EXPLORE_NEW_BIOME) return "New Discovery";
        if (topicId == ENTER_NETHER) return "Nether Journey";
        if (topicId == ENTER_END) return "End Arrival";
        if (topicId == RETURN_FROM_DIMENSION) return "Return Home";
        if (topicId == TRADE_GENERIC) return "Trade";
        if (topicId == TRADE_FOOD) return "Food Trade";
        if (topicId == TRADE_MYSTIC) return "Mystic Trade";
        if (topicId == TRADE_GUARD) return "Guard Trade";
        if (topicId == SOCIAL_CAMPFIRE) return "Campfire Gathering";
        
        return "Something interesting";
    }
    
    private static String formatName(String enumName) {
        return enumName.substring(0, 1).toUpperCase() + 
               enumName.substring(1).toLowerCase().replace('_', ' ');
    }

    public enum AbstractTopic {
        COMBAT("combat", 0.18f, 0.22f),
        EXPLORATION("exploration", 0.16f, 0.25f),
        SOCIAL("social", 0.14f, 0.3f),
        FAMILY("family", 0.12f, 0.32f),
        LIFE("life", 0.15f, 0.2f);

        private static final Long2ObjectMap<AbstractTopic> BY_ID = new Long2ObjectOpenHashMap<>();

        private final long topicId;
        private final float baseIntensity;
        private final float baseConfidence;
        private final String translationKey;

        AbstractTopic(String key, float baseIntensity, float baseConfidence) {
            this.topicId = computeId(ABSTRACT_PREFIX + key.toLowerCase(Locale.ROOT));
            this.baseIntensity = baseIntensity;
            this.baseConfidence = baseConfidence;
            this.translationKey = "petsplus.gossip.abstract." + key.toLowerCase(Locale.ROOT);
        }

        public long topicId() {
            return topicId;
        }

        public float baseIntensity() {
            return baseIntensity;
        }

        public float baseConfidence() {
            return baseConfidence;
        }

        public String translationKey() {
            return translationKey;
        }

        static {
            for (AbstractTopic topic : values()) {
                BY_ID.put(topic.topicId(), topic);
            }
        }
    }
}
