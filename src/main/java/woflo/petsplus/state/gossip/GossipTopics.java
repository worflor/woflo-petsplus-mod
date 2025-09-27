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
