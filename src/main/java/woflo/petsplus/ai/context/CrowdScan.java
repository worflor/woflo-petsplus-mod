package woflo.petsplus.ai.context;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;

import java.util.List;
import java.util.UUID;

/**
 * Utility that walks a captured crowd list once and produces both the general
 * crowd summary and the age-aware snapshot. Centralising the scan keeps the
 * baby-aware interactions lightweight – callers reuse the same aggregates
 * instead of re-iterating the entity list. Baby detection delegates to the
 * built-in {@link LivingEntity#isBaby()} flag so we honour vanilla tags
 * without hard-coded overrides.
 */
public final class CrowdScan {

    private static final Result EMPTY = new Result(
        PetContextCrowdSummary.empty(),
        NearbyMobAgeProfile.empty()
    );

    private CrowdScan() {
    }

    public static Result analyze(MobEntity mob, List<Entity> entities) {
        if (mob == null || entities == null || entities.isEmpty()) {
            return EMPTY;
        }

        int friendly = 0;
        int hostile = 0;
        int neutral = 0;

        double nearestFriendlySq = Double.POSITIVE_INFINITY;
        double nearestHostileSq = Double.POSITIVE_INFINITY;

        int babyFriendly = 0;
        int babyHostile = 0;
        int babyNeutral = 0;

        double nearestBabySq = Double.POSITIVE_INFINITY;
        double nearestFriendlyBabySq = Double.POSITIVE_INFINITY;
        double nearestHostileBabySq = Double.POSITIVE_INFINITY;
        double nearestNeutralBabySq = Double.POSITIVE_INFINITY;
        UUID nearestBabyId = null;

        for (Entity entity : entities) {
            if (entity == null || entity == mob || entity.isRemoved() || entity.isSpectator()) {
                continue;
            }
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }

            double squared = mob.squaredDistanceTo(entity);
            if (!Double.isFinite(squared) || squared < 0.0D) {
                continue;
            }

            boolean isBaby = living.isBaby();

            if (entity instanceof PassiveEntity) {
                friendly++;
                nearestFriendlySq = Math.min(nearestFriendlySq, squared);
                if (isBaby) {
                    babyFriendly++;
                    if (squared < nearestFriendlyBabySq) {
                        nearestFriendlyBabySq = squared;
                    }
                }
            } else if (entity instanceof Monster) {
                hostile++;
                nearestHostileSq = Math.min(nearestHostileSq, squared);
                if (isBaby) {
                    babyHostile++;
                    if (squared < nearestHostileBabySq) {
                        nearestHostileBabySq = squared;
                    }
                }
            } else {
                neutral++;
                if (isBaby) {
                    babyNeutral++;
                    if (squared < nearestNeutralBabySq) {
                        nearestNeutralBabySq = squared;
                    }
                }
            }

            if (isBaby && squared < nearestBabySq) {
                nearestBabySq = squared;
                nearestBabyId = entity.getUuid();
            }
        }

        PetContextCrowdSummary summary = new PetContextCrowdSummary(
            friendly,
            hostile,
            neutral,
            friendly > 0 ? Math.sqrt(nearestFriendlySq) : Double.POSITIVE_INFINITY,
            hostile > 0 ? Math.sqrt(nearestHostileSq) : Double.POSITIVE_INFINITY,
            babyFriendly,
            babyHostile,
            babyNeutral
        );

        NearbyMobAgeProfile ageProfile = new NearbyMobAgeProfile(
            babyFriendly,
            babyHostile,
            babyNeutral,
            (babyFriendly + babyHostile + babyNeutral) > 0 ? Math.sqrt(nearestBabySq) : Double.POSITIVE_INFINITY,
            babyFriendly > 0 ? Math.sqrt(nearestFriendlyBabySq) : Double.POSITIVE_INFINITY,
            babyHostile > 0 ? Math.sqrt(nearestHostileBabySq) : Double.POSITIVE_INFINITY,
            babyNeutral > 0 ? Math.sqrt(nearestNeutralBabySq) : Double.POSITIVE_INFINITY,
            nearestBabyId
        );

        return new Result(summary, ageProfile);
    }

    public static Result empty() {
        return EMPTY;
    }

    public record Result(
        PetContextCrowdSummary summary,
        NearbyMobAgeProfile ageProfile
    ) {
    }
}
