package woflo.petsplus.roles.scout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetSwarmIndex;
import woflo.petsplus.state.StateManager;

/**
 * Shared Scout helpers for effects and role behaviours.
 */
public final class ScoutBehaviors {
    private static final double DEFAULT_RADIUS_SQ = 16.0D * 16.0D;

    private ScoutBehaviors() {
    }

    /**
     * Collects the nearby Scout pets owned by the specified player within the provided radius.
     */
    public static List<PetSwarmIndex.SwarmEntry> collectScoutEntries(StateManager stateManager,
                                                                     ServerPlayerEntity owner,
                                                                     double radiusSq) {
        if (stateManager == null || owner == null) {
            return Collections.emptyList();
        }
        PetSwarmIndex index = stateManager.getSwarmIndex();
        if (index == null) {
            return Collections.emptyList();
        }
        List<PetSwarmIndex.SwarmEntry> snapshot = index.snapshotOwner(owner.getUuid());
        if (snapshot.isEmpty()) {
            return Collections.emptyList();
        }
        double maxDistanceSq = radiusSq <= 0 ? Double.POSITIVE_INFINITY : radiusSq;
        List<PetSwarmIndex.SwarmEntry> results = new ArrayList<>();
        for (PetSwarmIndex.SwarmEntry entry : snapshot) {
            PetComponent component = entry.component();
            if (component == null || !component.isOwnedBy(owner) || !component.hasRole(PetRoleType.SCOUT)) {
                continue;
            }
            LivingEntity pet = entry.pet();
            if (pet == null || pet.isRemoved()) {
                continue;
            }
            if (maxDistanceSq != Double.POSITIVE_INFINITY && pet.squaredDistanceTo(owner) > maxDistanceSq) {
                continue;
            }
            results.add(entry);
        }
        return results;
    }

    /**
     * Returns {@code true} if the owner has an eligible Scout companion within the default radius.
     */
    public static boolean hasNearbyScout(ServerPlayerEntity owner) {
        if (owner == null) {
            return false;
        }
        ServerWorld world = owner.getWorld() instanceof ServerWorld serverWorld ? serverWorld : null;
        StateManager manager = StateManager.forWorld(world);
        if (manager == null) {
            return false;
        }
        return !collectScoutEntries(manager, owner, DEFAULT_RADIUS_SQ).isEmpty();
    }

    /**
     * Apply Gale Pace speed to a mount instead of the owner when mounted.
     */
    public static void applyGalePaceToMount(PlayerEntity owner, StatusEffectInstance speedEffect) {
        if (owner.getVehicle() instanceof LivingEntity mount) {
            mount.addStatusEffect(speedEffect);
        } else {
            owner.addStatusEffect(speedEffect);
        }
    }
}
