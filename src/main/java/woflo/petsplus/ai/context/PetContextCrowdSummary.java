package woflo.petsplus.ai.context;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;

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
    double nearestHostileDistance
) {
    private static final PetContextCrowdSummary EMPTY = new PetContextCrowdSummary(0, 0, 0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

    public static PetContextCrowdSummary empty() {
        return EMPTY;
    }

    public static PetContextCrowdSummary fromEntities(MobEntity mob, List<Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return empty();
        }

        int friendly = 0;
        int hostile = 0;
        int neutral = 0;
        double nearestFriendly = Double.POSITIVE_INFINITY;
        double nearestHostile = Double.POSITIVE_INFINITY;

        for (Entity entity : entities) {
            if (entity == null || entity == mob) {
                continue;
            }

            double distance = mob.squaredDistanceTo(entity);

            if (entity instanceof PassiveEntity) {
                friendly++;
                nearestFriendly = Math.min(nearestFriendly, distance);
            } else if (entity instanceof Monster) {
                hostile++;
                nearestHostile = Math.min(nearestHostile, distance);
            } else {
                neutral++;
            }
        }

        return new PetContextCrowdSummary(
            friendly,
            hostile,
            neutral,
            friendly > 0 ? Math.sqrt(nearestFriendly) : Double.POSITIVE_INFINITY,
            hostile > 0 ? Math.sqrt(nearestHostile) : Double.POSITIVE_INFINITY
        );
    }
}

