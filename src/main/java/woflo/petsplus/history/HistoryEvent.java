package woflo.petsplus.history;

import java.util.UUID;

/**
 * A compact, data-driven record of a significant event in a pet's life.
 * Designed for efficient NBT storage and minimal memory footprint.
 */
public record HistoryEvent(
    long timestamp,        // Game tick when event occurred
    String eventType,      // "trade", "level_up", "combat", "mood_milestone", "ownership_start", "role_change"
    UUID ownerUuid,       // Who owned the pet at the time
    String ownerName,      // Cached owner name (for display when owner is offline)
    String eventData       // Minimal JSON-encoded event-specific data
) {
    
    /**
     * Event types that can be recorded in pet history.
     */
    public static final class EventType {
        public static final String TRADE = "trade";
        public static final String LEVEL_UP = "level_up";
        public static final String COMBAT = "combat";
        public static final String MOOD_MILESTONE = "mood_milestone";
        public static final String OWNERSHIP_START = "ownership_start";
        public static final String ROLE_CHANGE = "role_change";
        public static final String ACHIEVEMENT = "achievement";
        
        private EventType() {} // Utility class
    }
    
    /**
     * Achievement types that can be tracked in pet history.
     * These are modular and can be extended for the wellbeing system.
     */
    public static final class AchievementType {
        public static final String DREAM_ESCAPE = "dream_escape";
        public static final String PET_SACRIFICE = "pet_sacrifice";
        public static final String GUARDIAN_PROTECTION = "guardian_protection";
        public static final String ALLY_HEALED = "ally_healed";
        public static final String BEST_FRIEND_FOREVERER = "best_friend_foreverer";
        public static final String OR_NOT = "or_not";
        
        private AchievementType() {} // Utility class
    }
    
    /**
     * Creates a trade event record.
     */
    public static HistoryEvent trade(long timestamp, UUID fromOwner, String fromName, 
                                    UUID toOwner, String toName, String method) {
        String data = String.format("{\"from\":\"%s\",\"from_name\":\"%s\",\"to\":\"%s\",\"to_name\":\"%s\",\"method\":\"%s\"}", 
            fromOwner.toString(), fromName, toOwner.toString(), toName, method);
        return new HistoryEvent(timestamp, EventType.TRADE, toOwner, toName, data);
    }
    
    /**
     * Creates a level up event record.
     */
    public static HistoryEvent levelUp(long timestamp, UUID ownerUuid, String ownerName, 
                                      int newLevel, String source) {
        String data = String.format("{\"level\":%d,\"source\":\"%s\"}", newLevel, source);
        return new HistoryEvent(timestamp, EventType.LEVEL_UP, ownerUuid, ownerName, data);
    }
    
    /**
     * Creates a combat event record.
     */
    public static HistoryEvent combat(long timestamp, UUID ownerUuid, String ownerName, 
                                     String result, String opponentType) {
        String data = String.format("{\"result\":\"%s\",\"opponent\":\"%s\"}", result, opponentType);
        return new HistoryEvent(timestamp, EventType.COMBAT, ownerUuid, ownerName, data);
    }
    
    /**
     * Creates a mood milestone event record.
     */
    public static HistoryEvent moodMilestone(long timestamp, UUID ownerUuid, String ownerName, 
                                            String mood, float intensity) {
        String data = String.format("{\"mood\":\"%s\",\"intensity\":%.2f}", mood, intensity);
        return new HistoryEvent(timestamp, EventType.MOOD_MILESTONE, ownerUuid, ownerName, data);
    }
    
    /**
     * Creates an ownership start event record.
     */
    public static HistoryEvent ownershipStart(long timestamp, UUID ownerUuid, String ownerName, 
                                            String method) {
        String data = String.format("{\"method\":\"%s\"}", method);
        return new HistoryEvent(timestamp, EventType.OWNERSHIP_START, ownerUuid, ownerName, data);
    }
    
    /**
     * Creates a role change event record.
     */
    public static HistoryEvent roleChange(long timestamp, UUID ownerUuid, String ownerName, 
                                         String fromRole, String toRole) {
        String data = String.format("{\"from\":\"%s\",\"to\":\"%s\"}", fromRole, toRole);
        return new HistoryEvent(timestamp, EventType.ROLE_CHANGE, ownerUuid, ownerName, data);
    }
    
    /**
     * Creates a generic achievement event record.
     * This is the foundation for modular achievement tracking.
     */
    public static HistoryEvent achievement(long timestamp, UUID ownerUuid, String ownerName, 
                                          String achievementType, String additionalData) {
        String data = String.format("{\"achievement_type\":\"%s\",\"data\":%s}", 
            achievementType, additionalData);
        return new HistoryEvent(timestamp, EventType.ACHIEVEMENT, ownerUuid, ownerName, data);
    }
    
    /**
     * Creates a dream escape achievement (Eepy Eeper).
     */
    public static HistoryEvent dreamEscape(long timestamp, UUID ownerUuid, String ownerName) {
        String data = String.format("{\"achievement_type\":\"%s\"}", AchievementType.DREAM_ESCAPE);
        return new HistoryEvent(timestamp, EventType.ACHIEVEMENT, ownerUuid, ownerName, data);
    }
    
    /**
     * Creates a pet sacrifice achievement (Cursed One).
     */
    public static HistoryEvent petSacrifice(long timestamp, UUID ownerUuid, String ownerName, 
                                           float resurrectionHealth) {
        String data = String.format("{\"achievement_type\":\"%s\",\"health\":%.2f}", 
            AchievementType.PET_SACRIFICE, resurrectionHealth);
        return new HistoryEvent(timestamp, EventType.ACHIEVEMENT, ownerUuid, ownerName, data);
    }
    
    /**
     * Creates a guardian protection achievement (Guardian).
     */
    public static HistoryEvent guardianProtection(long timestamp, UUID ownerUuid, String ownerName, 
                                                  float damageRedirected) {
        String data = String.format("{\"achievement_type\":\"%s\",\"damage\":%.2f}", 
            AchievementType.GUARDIAN_PROTECTION, damageRedirected);
        return new HistoryEvent(timestamp, EventType.ACHIEVEMENT, ownerUuid, ownerName, data);
    }
    
    /**
     * Creates an ally healed achievement (Support).
     */
    public static HistoryEvent allyHealed(long timestamp, UUID ownerUuid, String ownerName, 
                                         UUID allyUuid, long day) {
        String data = String.format("{\"achievement_type\":\"%s\",\"ally\":\"%s\",\"day\":%d}", 
            AchievementType.ALLY_HEALED, allyUuid.toString(), day);
        return new HistoryEvent(timestamp, EventType.ACHIEVEMENT, ownerUuid, ownerName, data);
    }
    
    /**
     * Creates a best friend foreverer achievement.
     */
    public static HistoryEvent bestFriendForeverer(long timestamp, UUID ownerUuid, String ownerName) {
        String data = String.format("{\"achievement_type\":\"%s\"}", AchievementType.BEST_FRIEND_FOREVERER);
        return new HistoryEvent(timestamp, EventType.ACHIEVEMENT, ownerUuid, ownerName, data);
    }
    
    /**
     * Creates an "or not" achievement (first permanent pet death).
     */
    public static HistoryEvent orNot(long timestamp, UUID ownerUuid, String ownerName) {
        String data = String.format("{\"achievement_type\":\"%s\"}", AchievementType.OR_NOT);
        return new HistoryEvent(timestamp, EventType.ACHIEVEMENT, ownerUuid, ownerName, data);
    }
    
    /**
     * Checks if this event occurred with the specified owner.
     */
    public boolean isWithOwner(UUID ownerUuid) {
        return this.ownerUuid.equals(ownerUuid);
    }
    
    /**
     * Checks if this event is of the specified type.
     */
    public boolean isType(String eventType) {
        return this.eventType.equals(eventType);
    }
    
    /**
     * Gets a simple string representation for debugging.
     */
    @Override
    public String toString() {
        return String.format("HistoryEvent[type=%s, owner=%s, time=%d]", eventType, ownerName, timestamp);
    }
}
