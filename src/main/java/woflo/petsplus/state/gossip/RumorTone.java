package woflo.petsplus.state.gossip;

/**
 * Represents lightweight tone buckets inferred from a rumor's current
 * intensity, confidence, share history, and recency. The tones map directly to
 * localization suffixes so we can keep cue text varied without hard-coding new
 * English strings in multiple places.
 */
public enum RumorTone {
    WHISPER("whisper"),
    COZY("cozy"),
    WONDER("wonder"),
    BRAG("brag"),
    WARNING("warning"),
    SPOOKY("spooky"),
    WEARY("weary"),
    SARCASM("sarcasm");

    private static final long FRESH_WINDOW = 200L;
    private static final long RECENT_WINDOW = 600L;
    private static final long STALE_WINDOW = 1600L;

    private final String key;

    RumorTone(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static RumorTone classify(RumorEntry rumor, long currentTick) {
        float intensity = rumor.intensity();
        float confidence = rumor.confidence();
        int shares = rumor.shareCount();
        float delta = intensity - confidence;

        if (shares == 0 && intensity < 0.3f && confidence < 0.35f) {
            return WHISPER;
        }
        if (intensity >= 0.65f && confidence >= 0.55f) {
            return BRAG;
        }
        if (delta >= 0.25f && intensity >= 0.4f) {
            return WARNING;
        }
        if (rumor.heardRecently(currentTick, FRESH_WINDOW) && intensity >= 0.4f && confidence < 0.5f) {
            return WONDER;
        }
        if (rumor.heardRecently(currentTick, RECENT_WINDOW) && confidence <= 0.4f && intensity <= 0.5f) {
            return SPOOKY;
        }
        if (confidence <= 0.3f && shares >= 2 && intensity <= 0.55f) {
            return SARCASM;
        }
        if (!rumor.heardRecently(currentTick, STALE_WINDOW) && (intensity <= 0.3f || confidence <= 0.3f)) {
            return WEARY;
        }
        if (confidence >= 0.6f && intensity <= 0.45f && shares >= 2) {
            return COZY;
        }
        return COZY;
    }
}
