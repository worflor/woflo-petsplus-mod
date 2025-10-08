package woflo.petsplus.roles.support;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.StateManager;

import java.util.List;

/**
 * Implements Support role mechanics: potion aura system and life support abilities.
 * 
 * Core Features:
 * - Baseline: Potion Carrier - load potions, emit aura pulses at reduced potency
 * - L5 Perch Potion Efficiency: Reduced consumption while perched
 * - Higher tiers intentionally unfilled while we explore richer mounted support options
 * 
 * Design Philosophy:
 * - Support archetype focused on sustaining owner and allies
 * - Hands-free, persistent buffs with minimal micromanagement
 * - Sacrifices individual potion strength for longer, shared coverage
 */
public class SupportCore {
    
    public static void initialize() {
        // Support mechanics are driven by event hooks and per-pet upkeep.
    }
    
    /**
     * Check if a player has active Support aura coverage.
     */
    public static boolean hasActiveSupportAura(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            return false;
        }

        double auraRadius = 16.0; // Support aura range
        double radiusSq = auraRadius * auraRadius;
        StateManager stateManager = StateManager.forWorld(world);
        List<PetSwarmIndex.SwarmEntry> swarm = stateManager.getSwarmIndex().snapshotOwner(player.getUuid());
        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            PetComponent component = entry.component();
            if (component == null || !component.hasRole(PetRoleType.SUPPORT)) {
                continue;
            }
            var pet = entry.pet();
            if (pet == null || !pet.isAlive()) {
                continue;
            }
            if (pet.squaredDistanceTo(player) <= radiusSq) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the potion sip discount from nearby Support pets.
     */
    public static double getSupportPotionDiscount(ServerPlayerEntity player) {
        return SupportBehaviors.getPotionSipDiscount(player);
    }

}

