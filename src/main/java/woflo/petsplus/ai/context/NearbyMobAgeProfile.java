package woflo.petsplus.ai.context;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;

import java.util.List;
import java.util.UUID;

/**
 * Captures lightweight aggregates about nearby mob ages so adaptive goals can
 * react to babies without triggering extra scans.
 */
public record NearbyMobAgeProfile(
    int babyFriendlyCount,
    int babyHostileCount,
    int babyNeutralCount,
    double nearestBabyDistance,
    double nearestFriendlyBabyDistance,
    double nearestHostileBabyDistance,
    double nearestNeutralBabyDistance,
    UUID nearestBabyId
) {
    private static final NearbyMobAgeProfile EMPTY = new NearbyMobAgeProfile(
        0,
        0,
        0,
        Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        null
    );

    public static NearbyMobAgeProfile empty() {
        return EMPTY;
    }

    public boolean hasBabies() {
        return babyFriendlyCount > 0 || babyHostileCount > 0 || babyNeutralCount > 0;
    }

    public static NearbyMobAgeProfile fromEntities(MobEntity mob, List<Entity> entities) {
        CrowdScan.Result result = CrowdScan.analyze(mob, entities);
        return result.ageProfile();
    }
}
