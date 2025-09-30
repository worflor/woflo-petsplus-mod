package woflo.petsplus.roles.guardian;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.state.PetComponent;

import static woflo.petsplus.roles.guardian.GuardianFortressBondManager.applyRedirectReduction;
import static woflo.petsplus.roles.guardian.GuardianFortressBondManager.modifyOwnerDamage;

import java.util.List;

/**
 * Entry point for Guardian Bulwark redirection invoked from the player damage mixin.
 */
public final class GuardianBulwark {
    private static final float MINIMUM_REDIRECT = 0.001f;
    private static final float RESERVE_TOLERANCE = 0.0001f;

    private GuardianBulwark() {
    }

    /**
     * Attempt to redirect a portion of incoming damage away from the owner to their Guardian pet.
     *
     * @param victim         The player being damaged.
     * @param incomingDamage The damage amount before redirection.
     * @param source         The original damage source.
     * @return The amount of damage the owner should still take after redirection.
     */
    public static float tryRedirectDamage(PlayerEntity victim, float incomingDamage, DamageSource source) {
        if (!(victim instanceof ServerPlayerEntity owner)) {
            return incomingDamage;
        }
        if (incomingDamage <= 0.0f || source == null) {
            return modifyOwnerDamage(owner, incomingDamage);
        }
        if (isDisallowedSource(source)) {
            return modifyOwnerDamage(owner, incomingDamage);
        }

        List<GuardianCore.GuardianCandidate> candidates = GuardianCore.collectGuardiansForIntercept(owner);
        if (candidates.isEmpty()) {
            return modifyOwnerDamage(owner, incomingDamage);
        }

        float ownerDamage = incomingDamage;

        for (GuardianCore.GuardianCandidate candidate : candidates) {
            if (ownerDamage <= MINIMUM_REDIRECT) {
                break;
            }

            MobEntity guardian = candidate.pet();
            PetComponent component = candidate.component();
            float reserveFraction = candidate.reserveFraction();

            if (!GuardianCore.canGuardianSafelyRedirect(guardian, reserveFraction)) {
                continue;
            }

            float healthFraction = MathHelper.clamp(guardian.getHealth() / guardian.getMaxHealth(), 0.0f, 1.0f);
            float redirectRatio = GuardianCore.computeRedirectRatio(component.getLevel());
            float scaledRatio = redirectRatio * healthFraction;
            if (scaledRatio <= 0.0f) {
                continue;
            }

            float desiredRedirect = incomingDamage * scaledRatio;
            float reserveHealth = guardian.getMaxHealth() * reserveFraction;
            float available = Math.max(0.0f, guardian.getHealth() - reserveHealth);
            float redirectedAmount = Math.min(Math.min(desiredRedirect, available), ownerDamage);
            if (redirectedAmount <= MINIMUM_REDIRECT) {
                continue;
            }

            boolean hitReserveLimit = available > 0.0f && redirectedAmount >= available - RESERVE_TOLERANCE;
            float adjustedRedirect = applyRedirectReduction(owner, guardian, redirectedAmount);
            if (!applyDamageToGuardian(guardian, source, adjustedRedirect)) {
                continue;
            }

            GuardianCore.recordSuccessfulRedirect(owner, guardian, component, incomingDamage, redirectedAmount,
                reserveFraction, hitReserveLimit, adjustedRedirect);
            ownerDamage = Math.max(0.0f, ownerDamage - adjustedRedirect);
        }

        return modifyOwnerDamage(owner, ownerDamage);
    }

    private static boolean isDisallowedSource(DamageSource source) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) {
            return true;
        }
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.isIn(DamageTypeTags.BYPASSES_EFFECTS)) {
            return true;
        }
        if (source.isIn(DamageTypeTags.IS_DROWNING) || source.isIn(DamageTypeTags.IS_FALL)) {
            return true;
        }
        return false;
    }

    private static boolean applyDamageToGuardian(MobEntity guardian, DamageSource source, float amount) {
        if (!(guardian.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        if (amount <= 0.0f) {
            return false;
        }

        if (guardian.damage(world, source, amount)) {
            return true;
        }

        DamageSource fallback = world.getDamageSources().generic();
        return guardian.damage(world, fallback, amount);
    }
}
