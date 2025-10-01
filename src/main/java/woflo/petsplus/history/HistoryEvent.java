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
        
        private EventType() {} // Utility class
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
