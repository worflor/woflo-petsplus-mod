package woflo.petsplus.state.gossip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Maintains the per-pet ledger of rumors that can be exchanged during social
 * routines. Uses primitive collections so queries remain O(1) even when pets
 * are roaming in dense crowds.
 */
public final class PetGossipLedger {

    private static final int MAX_RUMOR_COUNT = 64;
    private static final long SHARE_COOLDOWN_TICKS = 80L;
    private static final long IDLE_DECAY_INTERVAL = 200L;
    private static final long ACTIVE_DECAY_INTERVAL = 80L;
    private static final long MAX_RUMOR_AGE = 24000L;
    private static final float MIN_SHARE_INTENSITY = 0.08f;
    private static final float MIN_SHARE_CONFIDENCE = 0.1f;
    private static final long ABSTRACT_COOLDOWN = 200L;

    public static final Codec<PetGossipLedger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RumorEntry.CODEC.listOf().fieldOf("rumors").forGetter(PetGossipLedger::serializeRumors),
        Codec.LONG.listOf().optionalFieldOf("shareQueue").forGetter(PetGossipLedger::serializeQueue),
        Codec.unboundedMap(Codec.LONG, Codec.LONG).optionalFieldOf("heardHistory")
            .forGetter(PetGossipLedger::serializeHeardHistory)
    ).apply(instance, (rumors, queue, heardHistory) -> {
        PetGossipLedger ledger = new PetGossipLedger();
        ledger.loadSerialized(rumors, queue.orElse(List.of()), heardHistory.orElse(Map.of()));
        return ledger;
    }));

    private static final long RECENT_HEARD_WINDOW = 600L;

    private final Long2ObjectMap<RumorEntry> rumors = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet shareQueue = new LongLinkedOpenHashSet();
    private final Long2LongMap heardHistory = new Long2LongOpenHashMap();
    private final Long2LongMap abstractLastShared = new Long2LongOpenHashMap();
    private final GossipTopics.AbstractTopic[] abstractTopics = GossipTopics.AbstractTopic.values();
    private int abstractCursor = 0;

    public PetGossipLedger() {
    }

    private void loadSerialized(List<RumorEntry> entries, List<Long> queueOrder, Map<Long, Long> heardTimestamps) {
        clear();
        for (RumorEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            RumorEntry copy = entry.copy();
            rumors.put(copy.topicId(), copy);
        }
        for (Long topicId : queueOrder) {
            if (topicId == null) {
                continue;
            }
            if (rumors.containsKey(topicId)) {
                enqueueForSharing(topicId);
            }
        }
        if (!heardTimestamps.isEmpty()) {
            for (Map.Entry<Long, Long> entry : heardTimestamps.entrySet()) {
                if (entry == null) {
                    continue;
                }
                Long topicId = entry.getKey();
                Long tick = entry.getValue();
                if (topicId == null || tick == null) {
                    continue;
                }
                heardHistory.put(topicId.longValue(), Math.max(0L, tick.longValue()));
            }
        }
    }

    private List<RumorEntry> serializeRumors() {
        if (rumors.isEmpty()) {
            return List.of();
        }
        List<RumorEntry> list = new ArrayList<>(rumors.size());
        for (RumorEntry entry : rumors.values()) {
            list.add(entry.copy());
        }
        return list;
    }

    private Optional<List<Long>> serializeQueue() {
        if (shareQueue.isEmpty()) {
            return Optional.empty();
        }
        List<Long> order = new ArrayList<>(shareQueue.size());
        for (LongIterator iterator = shareQueue.iterator(); iterator.hasNext(); ) {
            order.add(iterator.nextLong());
        }
        return Optional.of(order);
    }

    private Optional<Map<Long, Long>> serializeHeardHistory() {
        if (heardHistory.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Long> snapshot = new HashMap<>(heardHistory.size());
        for (Long2LongMap.Entry entry : heardHistory.long2LongEntrySet()) {
            snapshot.put(entry.getLongKey(), entry.getLongValue());
        }
        return Optional.of(snapshot);
    }

    public void copyFrom(PetGossipLedger other) {
        if (other == this) {
            return;
        }
        loadSerialized(other.serializeRumors(), other.serializeQueue().orElse(List.of()),
            other.serializeHeardHistory().orElse(Map.of()));
    }

    public void clear() {
        rumors.clear();
        shareQueue.clear();
        heardHistory.clear();
        abstractLastShared.clear();
        abstractCursor = 0;
    }

    public boolean isEmpty() {
        return rumors.isEmpty();
    }

    public Collection<RumorEntry> values() {
        return Collections.unmodifiableCollection(rumors.values());
    }

    public Stream<RumorEntry> stream() {
        return rumors.values().stream().map(RumorEntry::copy);
    }

    public long scheduleNextDecayDelay() {
        return isEmpty() ? IDLE_DECAY_INTERVAL : ACTIVE_DECAY_INTERVAL;
    }

    public boolean hasAbstractTopicsReady(long currentTick) {
        if (abstractTopics.length == 0) {
            return false;
        }
        for (int i = 0; i < abstractTopics.length; i++) {
            int index = (abstractCursor + i) % abstractTopics.length;
            GossipTopics.AbstractTopic topic = abstractTopics[index];
            long lastShared = abstractLastShared.getOrDefault(topic.topicId(), Long.MIN_VALUE);
            if (currentTick - lastShared >= ABSTRACT_COOLDOWN) {
                return true;
            }
        }
        return false;
    }

    public void recordRumor(long topicId, float intensity, float confidence, long currentTick,
                            @Nullable java.util.UUID sourceUuid, @Nullable Text paraphrased) {
        RumorEntry existing = rumors.get(topicId);
        if (existing == null) {
            enforceCapacity();
            RumorEntry entry = RumorEntry.create(topicId, intensity, confidence, currentTick, sourceUuid, paraphrased);
            rumors.put(topicId, entry);
            enqueueForSharing(topicId);
        } else {
            existing.reinforce(intensity, confidence, currentTick, sourceUuid, paraphrased, false);
            enqueueForSharing(topicId);
        }
        markHeard(topicId, currentTick);
    }

    public void ingestRumorFromPeer(RumorEntry shared, long currentTick, boolean corroborated) {
        if (shared == null) {
            return;
        }
        RumorEntry existing = rumors.get(shared.topicId());
        if (existing == null) {
            enforceCapacity();
            existing = shared.copy();
            existing.reinforce(shared.intensity(), shared.confidence(), currentTick,
                shared.sourceUuid(), shared.paraphrasedCopy(), corroborated);
            rumors.put(existing.topicId(), existing);
        } else {
            existing.reinforce(shared.intensity(), shared.confidence(), currentTick,
                shared.sourceUuid(), shared.paraphrasedCopy(), corroborated);
        }
        enqueueForSharing(shared.topicId());
        markHeard(shared.topicId(), currentTick);
    }

    public boolean hasRumor(long topicId) {
        return rumors.containsKey(topicId);
    }

    public boolean hasShareableRumors(long currentTick) {
        if (shareQueue.isEmpty()) {
            return false;
        }
        for (LongIterator iterator = shareQueue.iterator(); iterator.hasNext(); ) {
            long topicId = iterator.nextLong();
            RumorEntry entry = rumors.get(topicId);
            if (entry == null) {
                iterator.remove();
                continue;
            }
            if (entry.shouldShare(currentTick, SHARE_COOLDOWN_TICKS,
                MIN_SHARE_INTENSITY, MIN_SHARE_CONFIDENCE)) {
                return true;
            }
        }
        return false;
    }

    public List<RumorEntry> peekAbstractRumors(int limit, long currentTick) {
        if (limit <= 0 || !hasAbstractTopicsReady(currentTick)) {
            return List.of();
        }
        int requested = Math.min(limit, abstractTopics.length);
        List<RumorEntry> list = new ArrayList<>(requested);
        int found = 0;
        for (int offset = 0; offset < abstractTopics.length && found < requested; offset++) {
            int index = (abstractCursor + offset) % abstractTopics.length;
            GossipTopics.AbstractTopic topic = abstractTopics[index];
            long lastShared = abstractLastShared.getOrDefault(topic.topicId(), Long.MIN_VALUE);
            if (currentTick - lastShared < ABSTRACT_COOLDOWN) {
                continue;
            }
            list.add(new RumorEntry(topic.topicId(), topic.baseIntensity(), topic.baseConfidence(),
                Math.max(0L, currentTick), Math.max(0L, lastShared), 0, null, null));
            found++;
        }
        return list;
    }

    public List<RumorEntry> peekFreshRumors(int limit, long currentTick) {
        if (limit <= 0 || shareQueue.isEmpty()) {
            return List.of();
        }
        List<RumorEntry> list = new ArrayList<>(Math.min(limit, shareQueue.size()));
        for (LongIterator iterator = shareQueue.iterator(); iterator.hasNext() && list.size() < limit; ) {
            long topicId = iterator.nextLong();
            RumorEntry entry = rumors.get(topicId);
            if (entry == null) {
                iterator.remove();
                continue;
            }
            if (!entry.shouldShare(currentTick, SHARE_COOLDOWN_TICKS, MIN_SHARE_INTENSITY, MIN_SHARE_CONFIDENCE)) {
                continue;
            }
            list.add(entry.copy());
        }
        return list;
    }

    public RumorEntry pollForSharing(long currentTick) {
        for (LongIterator iterator = shareQueue.iterator(); iterator.hasNext(); ) {
            long topicId = iterator.nextLong();
            RumorEntry entry = rumors.get(topicId);
            if (entry == null) {
                iterator.remove();
                continue;
            }
            if (!entry.shouldShare(currentTick, SHARE_COOLDOWN_TICKS, MIN_SHARE_INTENSITY, MIN_SHARE_CONFIDENCE)) {
                continue;
            }
            iterator.remove();
            return entry.copy();
        }
        return null;
    }

    public void markShared(long topicId, long currentTick) {
        RumorEntry entry = rumors.get(topicId);
        if (entry == null) {
            if (GossipTopics.isAbstract(topicId)) {
                markAbstractShared(topicId, currentTick);
            }
            return;
        }
        entry.markShared(currentTick);
    }

    public void markAbstractShared(long topicId, long currentTick) {
        abstractLastShared.put(topicId, currentTick);
        for (int i = 0; i < abstractTopics.length; i++) {
            if (abstractTopics[i].topicId() == topicId) {
                abstractCursor = (i + 1) % abstractTopics.length;
                break;
            }
        }
        markHeard(topicId, currentTick);
    }

    public void deferRumor(long topicId) {
        if (!rumors.containsKey(topicId)) {
            return;
        }
        enqueueForSharing(topicId);
    }

    public void tickDecay(long currentTick) {
        if (rumors.isEmpty()) {
            return;
        }
        List<Long> removals = new ArrayList<>();
        for (Long2ObjectMap.Entry<RumorEntry> entry : rumors.long2ObjectEntrySet()) {
            long topicId = entry.getLongKey();
            RumorEntry rumor = entry.getValue();
            rumor.applyDecay(currentTick);
            if (rumor.isExpired(currentTick, MAX_RUMOR_AGE)
                || (rumor.intensity() < MIN_SHARE_INTENSITY * 0.5f
                && rumor.confidence() < MIN_SHARE_CONFIDENCE * 0.5f)) {
                removals.add(topicId);
                continue;
            }
            if (!shareQueue.contains(topicId)
                && rumor.shouldShare(currentTick, SHARE_COOLDOWN_TICKS, MIN_SHARE_INTENSITY, MIN_SHARE_CONFIDENCE)) {
                enqueueForSharing(topicId);
            }
        }
        if (!removals.isEmpty()) {
            for (Long topicId : removals) {
                if (topicId == null) {
                    continue;
                }
                rumors.remove(topicId);
                shareQueue.remove(topicId);
                heardHistory.remove(topicId);
            }
        }
        if (!heardHistory.isEmpty()) {
            List<Long> stale = new ArrayList<>();
            for (Long2LongMap.Entry entry : heardHistory.long2LongEntrySet()) {
                long topicId = entry.getLongKey();
                long tick = entry.getLongValue();
                if (!rumors.containsKey(topicId) || currentTick - tick > MAX_RUMOR_AGE) {
                    stale.add(topicId);
                }
            }
            for (Long topicId : stale) {
                heardHistory.remove(topicId);
            }
        }
    }

    private void enforceCapacity() {
        if (rumors.size() < MAX_RUMOR_COUNT) {
            return;
        }
        long weakestId = Long.MIN_VALUE;
        float weakestScore = Float.MAX_VALUE;
        for (Long2ObjectMap.Entry<RumorEntry> entry : rumors.long2ObjectEntrySet()) {
            RumorEntry rumor = entry.getValue();
            float score = rumor.intensity() * 0.7f + rumor.confidence() * 0.3f;
            if (score < weakestScore) {
                weakestScore = score;
                weakestId = entry.getLongKey();
            }
        }
        if (weakestId != Long.MIN_VALUE) {
            rumors.remove(weakestId);
            shareQueue.remove(weakestId);
        }
    }

    private void enqueueForSharing(long topicId) {
        if (shareQueue.contains(topicId)) {
            shareQueue.remove(topicId);
        }
        shareQueue.add(topicId);
    }

    public void forEachRumor(Consumer<RumorEntry> consumer) {
        if (consumer == null) {
            return;
        }
        for (RumorEntry entry : rumors.values()) {
            consumer.accept(entry.copy());
        }
    }

    public DataResult<NbtElement> encodeToNbt() {
        return CODEC.encodeStart(NbtOps.INSTANCE, this);
    }

    public long size() {
        return rumors.size();
    }

    public boolean hasHeardRecently(long topicId, long currentTick) {
        return hasHeardRecently(topicId, currentTick, RECENT_HEARD_WINDOW);
    }

    public boolean hasHeardRecently(long topicId, long currentTick, long window) {
        if (window <= 0L) {
            return false;
        }
        RumorEntry rumor = rumors.get(topicId);
        if (rumor != null && rumor.heardRecently(currentTick, window)) {
            return true;
        }
        long heardTick = heardHistory.getOrDefault(topicId, -1L);
        return heardTick >= 0L && currentTick - heardTick <= window;
    }

    public void registerDuplicateHeard(long topicId, long currentTick) {
        RumorEntry rumor = rumors.get(topicId);
        if (rumor != null) {
            rumor.registerDuplicate(currentTick);
        }
        markHeard(topicId, currentTick);
    }

    public void registerAbstractHeard(long topicId, long currentTick) {
        markHeard(topicId, currentTick);
    }

    public float knowledgeScore(long currentTick) {
        if (rumors.isEmpty()) {
            return 0f;
        }
        float total = 0f;
        for (RumorEntry rumor : rumors.values()) {
            float freshness = 1f - Math.min(1f, Math.max(0f, (currentTick - rumor.lastHeardTick()) / (float) MAX_RUMOR_AGE));
            total += (rumor.intensity() * 0.7f + rumor.confidence() * 0.3f) * (0.5f + 0.5f * freshness);
        }
        return total;
    }

    private void markHeard(long topicId, long currentTick) {
        heardHistory.put(topicId, Math.max(0L, currentTick));
    }
}
