package woflo.petsplus.ai.context.perception;

import java.util.function.Consumer;

/**
 * Compact bit-mask representation for {@link ContextSlice} sets used by
 * perception stimuli and caches. The mask caches all 2^N combinations so that
 * callers can freely compose slices without cloning {@code EnumSet} instances
 * on hot paths.
 */
public final class ContextSliceMask {
    private static final ContextSlice[] VALUES = ContextSlice.values();
    private static final int MASK_SIZE = 1 << VALUES.length;
    private static final ContextSliceMask[] CACHE = new ContextSliceMask[MASK_SIZE];
    private static final int ALL_BIT = 1 << ContextSlice.ALL.ordinal();
    private static final int FULL_MASK = MASK_SIZE - 1;
    public static final ContextSliceMask EMPTY = resolve(0);
    public static final ContextSliceMask ALL = resolve(FULL_MASK);

    private final int bits;

    private ContextSliceMask(int bits) {
        this.bits = bits;
    }

    public static ContextSliceMask of(ContextSlice slice) {
        if (slice == null) {
            return EMPTY;
        }
        return resolve(bitFor(slice));
    }

    public static ContextSliceMask of(ContextSlice first, ContextSlice second, ContextSlice... rest) {
        int bits = bitFor(first) | bitFor(second);
        if (rest != null) {
            for (ContextSlice slice : rest) {
                bits |= bitFor(slice);
            }
        }
        return resolve(bits);
    }

    public static ContextSliceMask fromMask(int rawBits) {
        return resolve(rawBits);
    }

    private static ContextSliceMask resolve(int rawBits) {
        int bits = normalize(rawBits);
        ContextSliceMask cached = CACHE[bits];
        if (cached == null) {
            cached = new ContextSliceMask(bits);
            CACHE[bits] = cached;
        }
        return cached;
    }

    private static int normalize(int bits) {
        if ((bits & ALL_BIT) != 0) {
            return FULL_MASK;
        }
        return bits & FULL_MASK;
    }

    private static int bitFor(ContextSlice slice) {
        if (slice == null) {
            return 0;
        }
        return 1 << slice.ordinal();
    }

    public ContextSliceMask union(ContextSlice slice) {
        return resolve(bits | bitFor(slice));
    }

    public ContextSliceMask union(ContextSliceMask other) {
        if (other == null || other == EMPTY) {
            return this;
        }
        if (this == ALL || other == ALL) {
            return ALL;
        }
        return resolve(bits | other.bits);
    }

    public boolean isAll() {
        return this == ALL || (bits & ALL_BIT) != 0;
    }

    public boolean isEmpty() {
        return this == EMPTY || bits == 0;
    }

    public boolean contains(ContextSlice slice) {
        if (slice == null) {
            return false;
        }
        if (isAll()) {
            return true;
        }
        int bit = bitFor(slice);
        return (bits & bit) != 0;
    }

    public int bits() {
        return bits;
    }

    public void forEach(Consumer<ContextSlice> consumer) {
        if (consumer == null) {
            return;
        }
        if (isAll()) {
            for (ContextSlice slice : VALUES) {
                consumer.accept(slice);
            }
            return;
        }
        for (ContextSlice slice : VALUES) {
            int bit = bitFor(slice);
            if ((bits & bit) != 0) {
                consumer.accept(slice);
            }
        }
    }

    @Override
    public int hashCode() {
        return bits;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ContextSliceMask other)) {
            return false;
        }
        return bits == other.bits;
    }

    @Override
    public String toString() {
        if (isAll()) {
            return "ContextSliceMask[ALL]";
        }
        StringBuilder builder = new StringBuilder("ContextSliceMask[");
        boolean first = true;
        for (ContextSlice slice : VALUES) {
            int bit = bitFor(slice);
            if ((bits & bit) == 0) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            builder.append(slice.name());
            first = false;
        }
        return builder.append(']').toString();
    }
}
