package woflo.petsplus.ai;

import net.minecraft.entity.mob.MobEntity;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.context.NearbyMobAgeProfile;
import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

/**
 * Compact behavioural profile describing how the pet should react to nearby
 * baby mobs. Derived once per context refresh and cached on the component.
 */
public record PetMobInteractionProfile(
    boolean shouldApproachBabies,
    boolean maintainBuffer,
    boolean softenAnimations
) {
    private static final PetMobInteractionProfile DEFAULT = new PetMobInteractionProfile(false, false, false);

    public static PetMobInteractionProfile defaultProfile() {
        return DEFAULT;
    }

    public static PetMobInteractionProfile derive(
        MobEntity pet,
        @Nullable PetComponent component,
        NearbyMobAgeProfile ageProfile,
        PetContextCrowdSummary crowdSummary
    ) {
        if (pet == null || ageProfile == null || !ageProfile.hasBabies()) {
            return DEFAULT;
        }

        PetRoleType.RoleArchetype archetype = component != null && component.getRoleType() != null
            ? component.getRoleType().archetype()
            : null;

        boolean friendlyNearby = ageProfile.babyFriendlyCount() > 0;
        boolean neutralNearby = ageProfile.babyNeutralCount() > 0;
        boolean hostileNearby = ageProfile.babyHostileCount() > 0;

        double comfortRadius = computeComfortRadius(pet);
        double friendlyComfort = comfortRadius * (archetype == PetRoleType.RoleArchetype.MOBILITY ? 1.25d : 1.0d);
        double neutralBuffer = comfortRadius * (archetype == PetRoleType.RoleArchetype.SUPPORT ? 1.15d : 0.95d);
        double hostileBuffer = comfortRadius * (archetype == PetRoleType.RoleArchetype.TANK ? 1.35d : 1.15d);

        boolean hostileClose = hostileNearby && within(ageProfile.nearestHostileBabyDistance(), hostileBuffer);
        boolean neutralClose = neutralNearby && within(ageProfile.nearestNeutralBabyDistance(), neutralBuffer);
        boolean friendlyClose = friendlyNearby && within(ageProfile.nearestFriendlyBabyDistance(), comfortRadius * 0.8d);

        boolean maintainBuffer = hostileClose || neutralClose;
        if (crowdSummary != null && crowdSummary.hostileCount() > 0) {
            maintainBuffer = true;
        }
        if (archetype == PetRoleType.RoleArchetype.SUPPORT) {
            maintainBuffer = maintainBuffer || friendlyClose;
        }

        double friendlyDistance = friendlyNearby ? ensureFinite(ageProfile.nearestFriendlyBabyDistance()) : Double.POSITIVE_INFINITY;
        boolean friendlyWithinReach = friendlyNearby && Double.isFinite(friendlyDistance)
            && friendlyDistance <= friendlyComfort + 1.5d;

        boolean shouldApproach = friendlyWithinReach && !hostileClose && (!neutralClose || archetype == PetRoleType.RoleArchetype.MOBILITY);
        if (archetype == PetRoleType.RoleArchetype.TANK && hostileClose) {
            shouldApproach = false;
        }
        if (maintainBuffer) {
            shouldApproach = false;
        }

        boolean softenAnimations = friendlyNearby && !hostileClose;
        if (archetype == PetRoleType.RoleArchetype.DPS) {
            softenAnimations = false;
        }

        return new PetMobInteractionProfile(shouldApproach, maintainBuffer, softenAnimations);
    }

    private static double computeComfortRadius(MobEntity pet) {
        if (pet == null) {
            return 2.2d;
        }
        float width = pet.getWidth();
        float height = pet.getHeight();
        double derived = width * 2.4d + height * 0.2d;
        return Math.max(1.8d, derived);
    }

    private static double ensureFinite(double value) {
        return Double.isFinite(value) ? value : Double.POSITIVE_INFINITY;
    }

    private static boolean within(double value, double limit) {
        return Double.isFinite(value) && value <= limit;
    }
}
