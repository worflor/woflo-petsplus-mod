package woflo.petsplus.roles.support;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetSwarmIndex;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.util.PetPerchUtil;
import woflo.petsplus.util.TriggerConditions;

import java.util.List;

/**
 * Support role behaviors for pet-agnostic utility enhancement.
 */
public class SupportBehaviors {
    
    /**
     * Check if the owner should get perch sip discount.
     * Called during potion consumption to reduce sip cost.
     */
    public static double getPotionSipDiscount(PlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return 0.0;
        }

        double discount = PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.SUPPORT.id(), "perchSipDiscount", 0.20);

        if (PetPerchUtil.ownerHasPerchedRole(owner, PetRoleType.SUPPORT)) {
            return discount;
        }

        StateManager stateManager = StateManager.forWorld(serverWorld);
        List<PetSwarmIndex.SwarmEntry> swarm = stateManager.getSwarmIndex().snapshotOwner(owner.getUuid());
        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            PetComponent component = entry.component();
            if (component == null || !component.hasRole(PetRoleType.SUPPORT)) {
                continue;
            }
            if (!component.isOwnedBy(owner)) {
                continue;
            }
            if (!PetPerchUtil.isPetPerched(component)) {
                continue;
            }
            var pet = entry.pet();
            if (pet == null || !pet.isAlive()) {
                continue;
            }
            return discount;
        }

        return 0.0;
    }

    /**
     * Get extra radius for aura pulses when owner is mounted.
     * Called by aura effects to determine radius bonus.
     */
    public static double getMountedAuraExtraRadius(PlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return 0.0;
        }
        
        // Check if owner is mounted
        if (!TriggerConditions.isMounted(owner)) {
            return 0.0;
        }

        // Find nearby Support pets
        StateManager stateManager = StateManager.forWorld(serverWorld);
        List<PetSwarmIndex.SwarmEntry> swarm = stateManager.getSwarmIndex().snapshotOwner(owner.getUuid());
        boolean hasNearbySupport = false;
        double radiusSq = 16 * 16;
        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            PetComponent component = entry.component();
            if (component == null || !component.hasRole(PetRoleType.SUPPORT)) {
                continue;
            }
            if (!component.isOwnedBy(owner)) {
                continue;
            }
            var pet = entry.pet();
            if (pet == null || !pet.isAlive()) {
                continue;
            }
            if (pet.squaredDistanceTo(owner) <= radiusSq) {
                hasNearbySupport = true;
                break;
            }
        }

        boolean eligibleMount = TriggerConditions.isMountedOnSaddled(owner);

        return hasNearbySupport && eligibleMount ?
            PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.SUPPORT.id(), "mountedConeExtraRadius", 2.0) : 0.0;
    }
    
    /**
     * Check if aura should use forward cone bias when owner is mounted.
     */
    public static boolean shouldUseForwardConeBias(PlayerEntity owner) {
        return TriggerConditions.isMounted(owner) && getMountedAuraExtraRadius(owner) > 0;
    }
}