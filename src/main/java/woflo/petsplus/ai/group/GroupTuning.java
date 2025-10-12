package woflo.petsplus.ai.group;

// GroupTuning: central constants
/**
 * Centralized tuning constants for group behavior mechanics.
 */
public final class GroupTuning {

    private GroupTuning() {}

    // Open Invite Defaults
    public static final double GROUP_RADIUS_DEFAULT = 6.0D;
    public static final int MAX_FOLLOWERS_DEFAULT = 2;
    // 3 seconds @ 20 TPS
    public static final int INVITE_EXPIRY_TICKS_DEFAULT = 60;

    // Follower Mechanics
    // 0.2s to 1.2s stagger (~4–24 ticks). We use “max” for a random 0..max and add 4 for minimum.
    public static final int FOLLOWER_STAGGER_TICKS_MIN = 4;
    public static final int FOLLOWER_STAGGER_TICKS_MAX = 24;
    public static final double OWNER_SNEAK_BOOST_RADIUS_SQ = GROUP_RADIUS_DEFAULT * GROUP_RADIUS_DEFAULT;

    // Cleanup cadence (10 seconds)
    public static final int CLEANUP_PERIOD_TICKS = 200;
}