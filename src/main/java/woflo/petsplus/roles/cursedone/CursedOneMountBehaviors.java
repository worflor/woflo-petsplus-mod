package woflo.petsplus.roles.cursedone;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

/**
 * Shared helpers for Cursed One mount-related logic.
 */
public final class CursedOneMountBehaviors {
    private CursedOneMountBehaviors() {
    }

    /**
     * Returns true if the owner has at least one active Cursed One pet within the given radius.
     */
    public static boolean hasNearbyCursedOne(ServerPlayerEntity owner, double radius) {
        if (owner == null || !(owner.getEntityWorld() instanceof ServerWorld world)) {
            return false;
        }
        double effectiveRadius = Math.max(0.0D, radius);
        return !world.getEntitiesByClass(
            MobEntity.class,
            owner.getBoundingBox().expand(effectiveRadius),
            entity -> {
                PetComponent component = PetComponent.get(entity);
                return component != null
                    && component.hasRole(PetRoleType.CURSED_ONE)
                    && component.isOwnedBy(owner)
                    && entity.isAlive();
            }
        ).isEmpty();
    }
}

