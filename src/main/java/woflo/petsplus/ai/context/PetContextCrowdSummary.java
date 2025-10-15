package woflo.petsplus.ai.context;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;

import java.util.List;

/**
 * Lightweight aggregate of nearby entity composition captured alongside the
 * {@link PetContext}. The summary is recalculated during capture to avoid
 * storing mutable collections on the context object.
 */
public record PetContextCrowdSummary(
    int friendlyCount,
    int hostileCount,
    int neutralCount,
    double nearestFriendlyDistance,
    double nearestHostileDistance,
    int babyFriendlyCount,
    int babyHostileCount,
    int babyNeutralCount
) {
    private static final PetContextCrowdSummary EMPTY = new PetContextCrowdSummary(
        0,
        0,
        0,
        Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        0,
        0,
        0
    );

    public static PetContextCrowdSummary empty() {
        return EMPTY;
    }

    public static PetContextCrowdSummary fromEntities(MobEntity mob, List<Entity> entities) {
        CrowdScan.Result result = CrowdScan.analyze(mob, entities);
        return result.summary();
    }
}

