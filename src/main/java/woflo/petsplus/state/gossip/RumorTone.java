package woflo.petsplus.state.gossip;

import java.util.List;

/**
 * Represents lightweight tone buckets inferred from a rumor's current
 * intensity, confidence, share history, and recency. The tones map directly to
 * localization suffixes so we can keep cue text varied without hard-coding new
 * English strings in multiple places.
 */
public enum RumorTone {
    WHISPER("whisper",
        "petsplus.gossip.story.whisper.0",
        "petsplus.gossip.story.whisper.1",
        "petsplus.gossip.story.whisper.2"),
    COZY("cozy",
        "petsplus.gossip.story.cozy.0",
        "petsplus.gossip.story.cozy.1",
        "petsplus.gossip.story.cozy.2"),
    WONDER("wonder",
        "petsplus.gossip.story.wonder.0",
        "petsplus.gossip.story.wonder.1",
        "petsplus.gossip.story.wonder.2"),
    BRAG("brag",
        "petsplus.gossip.story.brag.0",
        "petsplus.gossip.story.brag.1",
        "petsplus.gossip.story.brag.2"),
    WARNING("warning",
        "petsplus.gossip.story.warning.0",
        "petsplus.gossip.story.warning.1",
        "petsplus.gossip.story.warning.2"),
    SPOOKY("spooky",
        "petsplus.gossip.story.spooky.0",
        "petsplus.gossip.story.spooky.1",
        "petsplus.gossip.story.spooky.2"),
    WEARY("weary",
        "petsplus.gossip.story.weary.0",
        "petsplus.gossip.story.weary.1",
        "petsplus.gossip.story.weary.2"),
    SARCASM("sarcasm",
        "petsplus.gossip.story.sarcasm.0",
        "petsplus.gossip.story.sarcasm.1",
        "petsplus.gossip.story.sarcasm.2");

    private static final long FRESH_WINDOW = 200L;
    private static final long RECENT_WINDOW = 600L;
    private static final long STALE_WINDOW = 1600L;

    private final String key;
    private final List<String> templateKeys;

    RumorTone(String key, String... templateKeys) {
        this.key = key;
        this.templateKeys = List.of(templateKeys);
    }

    public String key() {
        return key;
    }

    public List<String> templateKeys() {
        return templateKeys;
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
