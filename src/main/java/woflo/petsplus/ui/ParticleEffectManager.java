package woflo.petsplus.ui;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Manages subtle particle effects for different pet roles.
 * Each role has a unique particle pattern that helps identify the pet's role.
 */
public class ParticleEffectManager {

    /**
     * Emit role-specific particle effects for a pet if enough time has passed.
     * Now delegates to the new FeedbackManager system for consistency.
     */
    public static void emitRoleParticles(MobEntity pet, ServerWorld world, long currentTick) {
        // Delegate to the new feedback system
        FeedbackManager.emitRoleAmbientParticles(pet, world, currentTick);
    }

    /**
     * Check if pet should emit particles (only when visible and not in combat for subtlety).
     */
    public static boolean shouldEmitParticles(MobEntity pet, ServerWorld world) {
        // Don't emit particles if pet is in active combat (to reduce visual noise)
        var component = woflo.petsplus.state.PetComponent.get(pet);
        if (component != null) {
            long lastAttack = component.getLastAttackTick();
            if (world.getTime() - lastAttack < 60) { // 3 seconds after combat
                return false;
            }
        }

        // Only emit if pet is alive and in a loaded chunk
        return pet.isAlive() && !pet.isRemoved();
    }
}