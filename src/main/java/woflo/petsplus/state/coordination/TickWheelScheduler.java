package woflo.petsplus.state.coordination;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Generic tick wheel scheduler that stores scheduled entries in a fixed-size
 * ring buffer and promotes far-future items through hierarchical overflow
 * buckets. The wheel guarantees {@code O(1)} amortised insertion and drain
 * costs while avoiding per-tick list allocation churn.
 */
public final class TickWheelScheduler<T> {

    private static final int DEFAULT_BITS = 12; // 4096 slots
    private static final int MIN_BITS = 8;      // 256 slots

    private final int wheelBits;
    private final int wheelSize;
    private final int mask;
    private final Slot<T>[] slots;
    private final ArrayDeque<List<T>> listPool = new ArrayDeque<>();
    private final ArrayDeque<Segment> segmentPool = new ArrayDeque<>();
    private final Long2ObjectOpenHashMap<Segment> overflowSegments = new Long2ObjectOpenHashMap<>();
    private final LongHeapPriorityQueue overflowQueue = new LongHeapPriorityQueue();
    private final List<T> immediate = new ArrayList<>();

    private long nextTick;

    public TickWheelScheduler() {
        this(DEFAULT_BITS);
    }

    @SuppressWarnings("unchecked")
    public TickWheelScheduler(int wheelBits) {
        this.wheelBits = Math.max(MIN_BITS, wheelBits);
        this.wheelSize = 1 << this.wheelBits;
        this.mask = this.wheelSize - 1;
        this.slots = (Slot<T>[]) new Slot[this.wheelSize];
        for (int i = 0; i < this.wheelSize; i++) {
            this.slots[i] = new Slot<>();
        }
        this.nextTick = 0L;
    }

    public void schedule(long tick, T value) {
        if (value == null) {
            return;
        }
        long sanitized = Math.max(0L, tick);
        if (sanitized < nextTick) {
            immediate.add(value);
            return;
        }
        long delta = sanitized - nextTick;
        if (delta >= wheelSize) {
            enqueueOverflow(sanitized, value);
            return;
        }
        assignToSlot(sanitized, value);
    }

    public void drainTo(long targetTick, Consumer<T> consumer) {
        if (consumer == null) {
            return;
        }
        long sanitizedTarget = Math.max(targetTick, nextTick - 1);
        while (nextTick <= sanitizedTarget) {
            promoteOverflow();
            int index = (int) (nextTick & mask);
            Slot<T> slot = slots[index];
            if (slot.tick == nextTick && slot.bucket != null && !slot.bucket.isEmpty()) {
                List<T> bucket = slot.bucket;
                slot.bucket = null;
                slot.tick = Long.MIN_VALUE;
                for (int i = 0; i < bucket.size(); i++) {
                    consumer.accept(bucket.get(i));
                }
                recycleList(bucket);
            } else if (slot.tick < nextTick) {
                clearSlot(slot);
            }
            nextTick++;
        }
        if (!immediate.isEmpty()) {
            for (int i = 0; i < immediate.size(); i++) {
                consumer.accept(immediate.get(i));
            }
            immediate.clear();
        }
    }

    public void clear() {
        nextTick = 0L;
        immediate.clear();
        for (Slot<T> slot : slots) {
            clearSlot(slot);
        }
        if (!overflowSegments.isEmpty()) {
            ObjectIterator<Long2ObjectMap.Entry<Segment>> iterator = overflowSegments.long2ObjectEntrySet().iterator();
            while (iterator.hasNext()) {
                Segment segment = iterator.next().getValue();
                iterator.remove();
                recycle(segment);
            }
        }
        overflowQueue.clear();
    }

    private void promoteOverflow() {
        if (overflowQueue.isEmpty()) {
            return;
        }
        long currentSegment = nextTick >> wheelBits;
        while (!overflowQueue.isEmpty()) {
            long segmentIndex = overflowQueue.firstLong();
            if (segmentIndex > currentSegment) {
                break;
            }
            overflowQueue.dequeueLong();
            Segment segment = overflowSegments.remove(segmentIndex);
            if (segment == null) {
                continue;
            }
            segment.promote();
            recycle(segment);
        }
    }

    private void assignToSlot(long tick, T value) {
        int index = (int) (tick & mask);
        Slot<T> slot = slots[index];
        if (slot.tick == tick) {
            ensureBucket(slot).add(value);
            return;
        }
        if (slot.tick == Long.MIN_VALUE || slot.tick < nextTick) {
            resetSlot(slot, tick);
            ensureBucket(slot).add(value);
            return;
        }
        if (slot.tick < tick) {
            enqueueOverflow(tick, value);
            return;
        }
        List<T> displaced = slot.bucket;
        long displacedTick = slot.tick;
        slot.bucket = null;
        slot.tick = Long.MIN_VALUE;
        if (displaced != null && !displaced.isEmpty()) {
            mergeBucketIntoOverflow(displacedTick, displaced);
        } else {
            recycleList(displaced);
        }
        resetSlot(slot, tick);
        ensureBucket(slot).add(value);
    }

    private void assignBucketToSlot(long tick, List<T> bucket) {
        if (bucket == null || bucket.isEmpty()) {
            recycleList(bucket);
            return;
        }
        int index = (int) (tick & mask);
        Slot<T> slot = slots[index];
        if (slot.tick == Long.MIN_VALUE || slot.tick < nextTick) {
            clearSlot(slot);
            slot.tick = tick;
            slot.bucket = bucket;
            return;
        }
        if (slot.tick == tick) {
            ensureBucket(slot).addAll(bucket);
            recycleList(bucket);
            return;
        }
        if (slot.tick < tick) {
            mergeBucketIntoOverflow(tick, bucket);
            return;
        }
        List<T> displaced = slot.bucket;
        long displacedTick = slot.tick;
        slot.bucket = bucket;
        slot.tick = tick;
        if (displaced != null && !displaced.isEmpty()) {
            mergeBucketIntoOverflow(displacedTick, displaced);
        } else {
            recycleList(displaced);
        }
    }

    private void resetSlot(Slot<T> slot, long tick) {
        clearSlot(slot);
        slot.tick = tick;
    }

    private void clearSlot(Slot<T> slot) {
        if (slot.bucket != null) {
            recycleList(slot.bucket);
            slot.bucket = null;
        }
        slot.tick = Long.MIN_VALUE;
    }

    private List<T> ensureBucket(Slot<T> slot) {
        if (slot.bucket == null) {
            slot.bucket = borrowList();
        }
        return slot.bucket;
    }

    private List<T> borrowList() {
        List<T> list = listPool.pollFirst();
        return list != null ? list : new ArrayList<>();
    }

    private void recycleList(List<T> list) {
        if (list == null) {
            return;
        }
        list.clear();
        listPool.offerLast(list);
    }

    private void enqueueOverflow(long tick, T value) {
        Segment segment = ensureSegment(tick);
        segment.addValue(tick, value);
    }

    private void mergeBucketIntoOverflow(long tick, List<T> bucket) {
        if (bucket == null || bucket.isEmpty()) {
            recycleList(bucket);
            return;
        }
        Segment segment = ensureSegment(tick);
        segment.addBucket(tick, bucket);
    }

    private Segment ensureSegment(long tick) {
        long segmentIndex = tick >> wheelBits;
        Segment segment = overflowSegments.get(segmentIndex);
        if (segment == null) {
            segment = borrowSegment();
            overflowSegments.put(segmentIndex, segment);
            overflowQueue.enqueue(segmentIndex);
        }
        return segment;
    }

    private Segment borrowSegment() {
        Segment segment = segmentPool.pollFirst();
        return segment != null ? segment : new Segment();
    }

    private void recycle(Segment segment) {
        if (segment == null) {
            return;
        }
        segment.release();
        segmentPool.offerLast(segment);
    }

    private final class Segment {
        private final Long2ObjectOpenHashMap<List<T>> buckets = new Long2ObjectOpenHashMap<>();

        void addValue(long tick, T value) {
            List<T> bucket = buckets.get(tick);
            if (bucket == null) {
                bucket = borrowList();
                buckets.put(tick, bucket);
            }
            bucket.add(value);
        }

        void addBucket(long tick, List<T> bucket) {
            List<T> existing = buckets.get(tick);
            if (existing == null) {
                buckets.put(tick, bucket);
                return;
            }
            existing.addAll(bucket);
            recycleList(bucket);
        }

        void promote() {
            ObjectIterator<Long2ObjectMap.Entry<List<T>>> iterator = buckets.long2ObjectEntrySet().iterator();
            while (iterator.hasNext()) {
                Long2ObjectMap.Entry<List<T>> entry = iterator.next();
                long tick = entry.getLongKey();
                List<T> bucket = entry.getValue();
                iterator.remove();
                assignBucketToSlot(tick, bucket);
            }
        }

        void release() {
            ObjectIterator<Long2ObjectMap.Entry<List<T>>> iterator = buckets.long2ObjectEntrySet().iterator();
            while (iterator.hasNext()) {
                List<T> bucket = iterator.next().getValue();
                iterator.remove();
                recycleList(bucket);
            }
        }
    }

    private static final class Slot<T> {
        private long tick = Long.MIN_VALUE;
        private List<T> bucket;
    }
}
