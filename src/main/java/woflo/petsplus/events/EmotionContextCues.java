package woflo.petsplus.events;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import woflo.petsplus.events.EmotionCueConfig.EmotionCueDefinition;
import woflo.petsplus.ui.BossBarManager;

/**
 * Enhanced manager for delivering contextual action-bar cues tied to emotion events.
 * Centralises cooldown management, category batching, digest aggregation, and suppressed
 * cue journals so that the action bar stays readable while still teaching players why
 * their pets' moods are shifting.
 */
public final class EmotionContextCues {

    private static final Map<ServerPlayerEntity, Map<String, Long>> LAST_CUES = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, Map<String, Long>> CATEGORY_COOLDOWNS = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, Deque<StimulusSummary>> PENDING_STIMULI = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, CueDigest> DIGESTS = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, Map<String, StimulusHistory>> HISTORY = new WeakHashMap<>();
    private static final Map<UUID, Deque<Text>> JOURNALS = new HashMap<>();

    private static final int JOURNAL_LIMIT = 24;
    private static final int STIMULUS_RETENTION_TICKS = 200;
    private static final long MAX_STIMULUS_GAP_TICKS = 4L;

    private EmotionContextCues() {}

    /** Records the mood delta that resulted from pushing an emotion stimulus. */
    public static void recordStimulus(ServerPlayerEntity player, StimulusSummary summary) {
        if (player == null || summary == null || summary.isEmpty()) {
            return;
        }
        PENDING_STIMULI.computeIfAbsent(player, p -> new ArrayDeque<>()).addLast(summary);
        // Keep the queue reasonably small.
        Deque<StimulusSummary> queue = PENDING_STIMULI.get(player);
        while (queue.size() > 6) {
            queue.pollFirst();
        }
    }

    /**
     * Send a contextual cue with a configurable cooldown. The method consults the data-driven
     * configuration to determine category throttles, minimum mood deltas, digest windows, and
     * optional HUD pulses.
     */
    public static void sendCue(ServerPlayerEntity player, String cueId, Text text, long fallbackCooldown) {
        if (player == null || cueId == null || cueId.isEmpty() || text == null) {
            return;
        }

        long now = player.getWorld().getTime();
        String definitionId = resolveDefinitionId(cueId);
        EmotionCueDefinition definition = EmotionCueConfig.get().definition(definitionId);
        long cooldown = definition != null ? definition.cooldownTicks() : fallbackCooldown;
        if (cooldown <= 0) {
            cooldown = 100;
        }

        StimulusSummary summary = consumeLatestSummary(player, now);
        boolean hasStimulus = !summary.isEmpty();
        float impact = hasStimulus
            ? summary.totalDelta()
            : (definition != null ? Math.max(definition.minDelta(), 0.1f) : 1f);
        if (definition != null && hasStimulus) {
            impact = adjustForHistory(player, definitionId, impact, now, definition);
        }

        if (!checkCueCooldown(player, cueId, now, cooldown)) {
            addJournalEntry(player, text, SuppressionReason.CUE_COOLDOWN);
            return;
        }

        if (definition != null) {
            long categoryCooldown = Math.max(1L, definition.categoryCooldownTicks());
            if (!checkCategoryCooldown(player, definition.category(), now, categoryCooldown)) {
                addJournalEntry(player, text, SuppressionReason.CATEGORY_COOLDOWN);
                queueDigest(player, definitionId, definition, text, impact, now);
                return;
            }

            if (hasStimulus && !definition.forceShow() && impact < Math.max(0f, definition.minDelta())) {
                queueDigest(player, definitionId, definition, text, impact, now);
                return;
            }

            deliverCue(player, cueId, definition, text, now, impact);
        } else {
            // Legacy fallback when no definition exists.
            deliverCue(player, cueId, null, text, now, impact);
        }
    }

    /** Convenience overload using configuration-defined cooldowns. */
    public static void sendCue(ServerPlayerEntity player, String cueId, Text text) {
        sendCue(player, cueId, text, 100);
    }

    /** Flush digest queues and prune stale stimulus entries once per world tick. */
    public static void tick(ServerWorld world) {
        long now = world.getTime();
        for (ServerPlayerEntity player : world.getPlayers()) {
            pruneStimuli(player, now);
            flushDigest(player, now);
        }
    }

    /** Clear all cue state for a player (e.g., on respawn). */
    public static void clear(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        LAST_CUES.remove(player);
        CATEGORY_COOLDOWNS.remove(player);
        PENDING_STIMULI.remove(player);
        DIGESTS.remove(player);
        HISTORY.remove(player);
    }

    /** Clear throttles when a player changes dimension to avoid stale cooldowns. */
    public static void clearForDimensionChange(ServerPlayerEntity player) {
        clear(player);
        // Preserve journal entries so players can still review missed cues after travelling.
    }

    /** Dump suppressed cue history to the player and clear the backlog. */
    public static void dumpJournal(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        Deque<Text> entries = JOURNALS.remove(player.getUuid());
        player.sendMessage(Text.translatable("petsplus.emotion_cue.journal.header"), false);
        if (entries == null || entries.isEmpty()) {
            player.sendMessage(Text.translatable("petsplus.emotion_cue.journal.empty"), false);
            return;
        }
        for (Text entry : entries) {
            player.sendMessage(entry, false);
        }
    }

    private static boolean checkCueCooldown(ServerPlayerEntity player, String cueId, long now, long cooldown) {
        Map<String, Long> perPlayer = LAST_CUES.get(player);
        if (perPlayer == null) {
            return true;
        }
        Long last = perPlayer.get(cueId);
        if (last != null && now - last < cooldown) {
            return false;
        }
        return true;
    }

    private static boolean checkCategoryCooldown(ServerPlayerEntity player, String category, long now, long cooldown) {
        if (category == null || category.isEmpty()) {
            return true;
        }
        Map<String, Long> perPlayer = CATEGORY_COOLDOWNS.get(player);
        if (perPlayer == null) {
            return true;
        }
        Long last = perPlayer.get(category);
        if (last != null && now - last < cooldown) {
            return false;
        }
        return true;
    }

    private static float adjustForHistory(ServerPlayerEntity player, String definitionId, float impact,
                                          long now, EmotionCueDefinition definition) {
        if (impact <= 0f) {
            impact = 0f;
        }
        Map<String, StimulusHistory> history = HISTORY.computeIfAbsent(player, p -> new HashMap<>());
        StimulusHistory previous = history.get(definitionId);
        if (previous != null) {
            long elapsed = now - previous.lastTick;
            long cooldown = Math.max(definition.cooldownTicks(), definition.categoryCooldownTicks());
            if (elapsed < cooldown) {
                float scale = Math.max(0.25f, (float) elapsed / (float) cooldown);
                impact *= scale;
            }
        }
        history.put(definitionId, new StimulusHistory(now, impact));
        return impact;
    }

    private static void deliverCue(ServerPlayerEntity player, String cueId, EmotionCueDefinition definition,
                                   Text text, long now, float impact) {
        markCueFired(player, cueId, now);
        player.sendMessage(text, true);

        if (definition != null) {
            CATEGORY_COOLDOWNS.computeIfAbsent(player, p -> new HashMap<>())
                .put(definition.category(), now);
            if (EmotionCueConfig.get().hudPulseEnabled() && definition.highlightHud()) {
                if (impact > definition.minDelta() * 1.5f) {
                    BossBarManager.showOrUpdateInfoBar(player, text.copy(), BossBar.Color.PURPLE, 60);
                }
            }
        }

        if (EmotionCueConfig.get().debugOverlay() && impact > 0f) {
            MutableText debug = text.copy()
                .append(Text.literal(String.format(Locale.ROOT, " [Δ=%.2f]", impact)));
            player.sendMessage(debug, false);
        }
    }

    private static void markCueFired(ServerPlayerEntity player, String cueId, long now) {
        LAST_CUES.computeIfAbsent(player, p -> new HashMap<>()).put(cueId, now);
    }

    private static void queueDigest(ServerPlayerEntity player, String definitionId, EmotionCueDefinition definition,
                                    Text text, float impact, long now) {
        if (!definition.digestEnabled()) {
            addJournalEntry(player, text, SuppressionReason.LOW_IMPACT);
            return;
        }
        CueDigest digest = DIGESTS.computeIfAbsent(player, p -> new CueDigest());
        long window = Math.max(1L, definition.digestWindowTicks());
        digest.add(definitionId, definition, impact, now + window);
        addJournalEntry(player, text, SuppressionReason.LOW_IMPACT);
    }

    private static void flushDigest(ServerPlayerEntity player, long now) {
        CueDigest digest = DIGESTS.get(player);
        if (digest == null || !digest.shouldFlush(now)) {
            return;
        }
        Text message = buildDigestText(digest.entries());
        if (message != null) {
            player.sendMessage(message, false);
        }
        digest.reset();
    }

    private static Text buildDigestText(List<CueDigest.Entry> entries) {
        if (entries.isEmpty()) {
            return null;
        }
        List<Text> parts = new ArrayList<>();
        for (CueDigest.Entry entry : entries) {
            EmotionCueDefinition definition = EmotionCueConfig.get().definition(entry.definitionId());
            Text label;
            if (definition != null && definition.digestLabelKey() != null) {
                label = Text.translatable(definition.digestLabelKey());
            } else if (definition != null && definition.textKey() != null) {
                label = Text.translatable(definition.textKey());
            } else {
                label = Text.literal(entry.definitionId());
            }
            parts.add(Text.translatable("petsplus.emotion_cue.digest.entry", entry.count(), label));
        }
        return Text.translatable("petsplus.emotion_cue.digest",
            Texts.join(parts, Text.literal(" • ")));
    }

    private static StimulusSummary consumeLatestSummary(ServerPlayerEntity player, long now) {
        Deque<StimulusSummary> deque = PENDING_STIMULI.get(player);
        if (deque == null || deque.isEmpty()) {
            return StimulusSummary.empty(now);
        }
        while (!deque.isEmpty()) {
            StimulusSummary summary = deque.peekLast();
            if (summary == null) {
                deque.pollLast();
                continue;
            }
            long age = now - summary.tick();
            if (age < 0L || age <= MAX_STIMULUS_GAP_TICKS) {
                deque.pollLast();
                if (deque.isEmpty()) {
                    PENDING_STIMULI.remove(player);
                }
                return summary;
            }
            // Too old to correlate with the cue; discard and try the next oldest snapshot.
            deque.pollLast();
        }
        if (deque.isEmpty()) {
            PENDING_STIMULI.remove(player);
        }
        return StimulusSummary.empty(now);
    }

    private static void pruneStimuli(ServerPlayerEntity player, long now) {
        Deque<StimulusSummary> deque = PENDING_STIMULI.get(player);
        if (deque == null || deque.isEmpty()) {
            return;
        }
        Iterator<StimulusSummary> iterator = deque.iterator();
        while (iterator.hasNext()) {
            StimulusSummary summary = iterator.next();
            if (now - summary.tick() > STIMULUS_RETENTION_TICKS) {
                iterator.remove();
            }
        }
        if (deque.isEmpty()) {
            PENDING_STIMULI.remove(player);
        }
    }

    private static void addJournalEntry(ServerPlayerEntity player, Text cueText, SuppressionReason reason) {
        if (player == null || cueText == null) {
            return;
        }
        Text entry = switch (reason) {
            case CUE_COOLDOWN -> Text.translatable("petsplus.emotion_cue.journal.cooldown", cueText.copy());
            case CATEGORY_COOLDOWN -> Text.translatable("petsplus.emotion_cue.journal.category", cueText.copy());
            case LOW_IMPACT -> Text.translatable("petsplus.emotion_cue.journal.low", cueText.copy());
        };
        Deque<Text> log = JOURNALS.computeIfAbsent(player.getUuid(), id -> new ArrayDeque<>());
        if (log.size() >= JOURNAL_LIMIT) {
            log.removeFirst();
        }
        log.addLast(entry);
    }

    private static String resolveDefinitionId(String cueId) {
        int idx = cueId.lastIndexOf('.');
        if (idx < 0) {
            return cueId;
        }
        String tail = cueId.substring(idx + 1);
        if (looksLikeUuid(tail)) {
            return cueId.substring(0, idx);
        }
        return cueId;
    }

    private static boolean looksLikeUuid(String value) {
        if (value.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private enum SuppressionReason {
        CUE_COOLDOWN,
        CATEGORY_COOLDOWN,
        LOW_IMPACT
    }

    private static final class StimulusHistory {
        final long lastTick;
        final float lastImpact;

        StimulusHistory(long lastTick, float lastImpact) {
            this.lastTick = lastTick;
            this.lastImpact = lastImpact;
        }
    }

    private static final class CueDigest {
        private final Map<String, Entry> entries = new HashMap<>();
        private long flushTick = -1L;

        void add(String id, EmotionCueDefinition definition, float impact, long flushAt) {
            entries.merge(id, new Entry(id, 1, impact), Entry::merge);
            if (flushTick < 0L || flushAt > flushTick) {
                flushTick = flushAt;
            }
        }

        boolean shouldFlush(long now) {
            return flushTick > 0L && now >= flushTick;
        }

        List<Entry> entries() {
            return new ArrayList<>(entries.values());
        }

        void reset() {
            entries.clear();
            flushTick = -1L;
        }

        private record Entry(String definitionId, int count, float impactSum) {
            static Entry merge(Entry a, Entry b) {
                return new Entry(a.definitionId, a.count + b.count, a.impactSum + b.impactSum);
            }
        }
    }
}

