package woflo.petsplus.ai.suggester.signal.feasibility;

import net.minecraft.util.Identifier;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.NearbyMobAgeProfile;
import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.suggester.signal.FeasibilitySignal;
import woflo.petsplus.ai.suggester.signal.SignalResult;

import java.util.Map;
import java.util.Set;

/**
 * Feasibility signal that distinguishes owner-driven social behaviours from pack-focused ones.
 * Owner centric goals still require the owner to be nearby, while pack centric goals keep a
 * reduced, distance-weighted score when only friendly crowd members are present.
 */
public class SocialProximityFeasibilitySignal implements FeasibilitySignal {
    private static final Identifier ID = Identifier.of("petsplus", "feasibility/social_proximity");
    private static final Set<Identifier> OWNER_CENTRIC_GOALS = Set.of(
        GoalIds.LEAN_AGAINST_OWNER,
        GoalIds.PARALLEL_PLAY,
        GoalIds.SHOW_OFF_TRICK,
        GoalIds.PERCH_ON_SHOULDER,
        GoalIds.ORBIT_SWIM,
        GoalIds.CROUCH_APPROACH_RESPONSE
    );

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public SignalResult evaluate(GoalDefinition goal, PetContext ctx) {
        if (goal.category() != GoalDefinition.Category.SOCIAL) {
            return SignalResult.identity();
        }

        NearbyMobAgeProfile ageProfile = ctx.nearbyMobAgeProfile();
        if (ageProfile != null && ageProfile.babyHostileCount() > 0) {
            double hostileDistance = ageProfile.nearestHostileBabyDistance();
            if (!Double.isFinite(hostileDistance)) {
                hostileDistance = ageProfile.nearestBabyDistance();
            }
            if (Double.isFinite(hostileDistance) && hostileDistance <= 4.0d) {
                return new SignalResult(0.0f, 0.0f, Map.of(
                    "reason", "hostile_baby",
                    "distance", hostileDistance
                ));
            }
        }

        if (isOwnerCentric(goal)) {
            if (!ctx.ownerNearby()) {
                return new SignalResult(0.0f, 0.0f, Map.of("reason", "owner_absent"));
            }

            float distance = ctx.distanceToOwner();
            float applied = Math.max(0.2f, 1.0f - (distance / 16.0f));
            return new SignalResult(applied, applied, Map.of("distance", distance));
        }

        PetContextCrowdSummary summary = ctx.crowdSummary();
        int friendlyCount = summary != null ? summary.friendlyCount() : 0;

        if (friendlyCount <= 0) {
            return new SignalResult(0.0f, 0.0f, Map.of("reason", "no_packmates"));
        }

        double nearestFriendlyDistance = summary.nearestFriendlyDistance();
        float distance = Double.isFinite(nearestFriendlyDistance)
            ? (float) nearestFriendlyDistance
            : Float.POSITIVE_INFINITY;

        float applied = distance == Float.POSITIVE_INFINITY
            ? 0.35f
            : Math.max(0.25f, 0.85f - (distance / 18.0f));
        float energy = distance == Float.POSITIVE_INFINITY
            ? 0.3f
            : Math.max(0.2f, 0.75f - (distance / 20.0f));

        float safetyPenalty = 1.0f;
        if (ageProfile != null && ageProfile.babyNeutralCount() > 0) {
            safetyPenalty = 0.85f;
        }

        return new SignalResult(
            applied,
            energy * safetyPenalty,
            Map.of(
                "friendly_count", friendlyCount,
                "nearest_friendly_distance", distance,
                "owner_present", ctx.ownerNearby(),
                "baby_safety", safetyPenalty
            )
        );
    }

    private static boolean isOwnerCentric(GoalDefinition goal) {
        return OWNER_CENTRIC_GOALS.contains(goal.id());
    }
}
