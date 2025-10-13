package woflo.petsplus.policy;

/** Policy constants for AI LOD distances and cadence modulation. */
public final class AIPerfPolicy {

    private AIPerfPolicy() { /* prevent instantiation */ }

    // Distance buckets (in blocks)
    public static final int NEAR_DIST = 16;
    public static final int MID_DIST = 64;

    // Tick modulation divisors for mid/far buckets
    public static final int MID_TICK_MOD = 5;
    public static final int FAR_TICK_MOD = 20;

    // Provider base cadence per LOD (in ticks)
    public static final int PROVIDER_BASE_TICKS_NEAR = 10;
    public static final int PROVIDER_BASE_TICKS_MID = 20;
    public static final int PROVIDER_BASE_TICKS_FAR = 40;
}
