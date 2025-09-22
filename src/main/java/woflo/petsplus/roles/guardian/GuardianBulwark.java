package woflo.petsplus.roles.guardian;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.tag.DamageTypeTags;
import woflo.petsplus.Petsplus;
import woflo.petsplus.state.PetComponent;

import java.util.List;

/**
 * Guardian Bulwark system - redirects damage from owner to pet and provides defensive benefits.
 */
public class GuardianBulwark {

    /**
     * Attempt to redirect damage from owner to their Guardian pet.
     *
     * @param owner  The player taking damage
     * @param damage The incoming damage amount
     * @param source The damage source
     * @return The amount of damage that should be taken by the owner (0 if fully redirected)
     */
    public static float tryRedirectDamage(PlayerEntity owner, float damage, DamageSource source) {
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return damage;
        }

        if (damage <= 0) {
            return damage;
        }

        if (!canRedirectDamageType(source)) {
            return damage;
        }

        List<MobEntity> guardianPets = GuardianCore.findNearbyGuardianPets(serverOwner);
        if (guardianPets.isEmpty()) {
            return damage;
        }

        MobEntity guardian = GuardianCore.findBestGuardianForIntercept(guardianPets);
        if (guardian == null) {
            return damage;
        }

        long currentTime = guardian.getWorld().getTime();
        if (GuardianCore.isOnBulwarkCooldown(guardian, currentTime)) {
            return damage;
        }

        PetComponent petComponent = PetComponent.get(guardian);
        if (petComponent == null) {
            return damage;
        }

        if (!GuardianCore.canGuardianSafelyRedirect(guardian, petComponent)) {
            return damage;
        }

        float healthFraction = guardian.getMaxHealth() <= 0.0f ? 0.0f : guardian.getHealth() / guardian.getMaxHealth();
        float redirectRatio = GuardianCore.calculateRedirectRatio(petComponent.getLevel(), healthFraction);
        float desiredRedirect = damage * redirectRatio;
        float reserveHealth = GuardianCore.getBulwarkReserveHealth(guardian, petComponent);
        float maxGuardianDamage = Math.max(0.0f, guardian.getHealth() - reserveHealth);
        float redirectedDamage = Math.min(desiredRedirect, maxGuardianDamage);

        if (redirectedDamage <= 0.0f) {
            return damage;
        }

        if (!(guardian.getWorld() instanceof ServerWorld guardianWorld)) {
            return damage;
        }

        boolean guardianTookDamage = applyGuardianRedirectDamage(guardian, guardianWorld, source, redirectedDamage);
        if (!guardianTookDamage) {
            return damage;
        }

        float remainingDamage = Math.max(0.0f, damage - redirectedDamage);

        boolean hitReserveLimit = desiredRedirect > maxGuardianDamage + 1.0E-3f;

        GuardianCore.recordSuccessfulRedirect(serverOwner, guardian, source, damage, redirectedDamage,
            reserveHealth, hitReserveLimit);

        Petsplus.LOGGER.debug("Guardian {} redirected {} damage from {} (reserve {:.1f}/{:.1f}, clamped={})",
            guardian.getName().getString(),
            redirectedDamage,
            owner.getName().getString(),
            reserveHealth,
            guardian.getMaxHealth(),
            hitReserveLimit);

        return remainingDamage;
    }

    private static boolean canRedirectDamageType(DamageSource source) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) {
            return false;
        }

        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) ||
            source.isIn(DamageTypeTags.BYPASSES_EFFECTS) ||
            source.isIn(DamageTypeTags.IS_DROWNING) ||
            source.isIn(DamageTypeTags.IS_FALL)) {
            return false;
        }

        return true;
    }

    private static boolean applyGuardianRedirectDamage(MobEntity guardian, ServerWorld guardianWorld,
                                                       DamageSource originalSource, float damageAmount) {
        if (damageAmount <= 0.0f) {
            return false;
        }

        float baseline = guardian.getHealth() + guardian.getAbsorptionAmount();
        if (guardian.damage(guardianWorld, originalSource, damageAmount) &&
            guardianPaidHealthCost(guardian, baseline)) {
            return true;
        }

        baseline = guardian.getHealth() + guardian.getAbsorptionAmount();
        if (originalSource.isOf(DamageTypes.GENERIC)) {
            return false;
        }

        DamageSource neutralSource = guardianWorld.getDamageSources().generic();
        if (guardian.damage(guardianWorld, neutralSource, damageAmount) &&
            guardianPaidHealthCost(guardian, baseline)) {
            return true;
        }

        return false;
    }

    private static boolean guardianPaidHealthCost(MobEntity guardian, float previousEffectiveHealth) {
        float currentEffectiveHealth = guardian.getHealth() + guardian.getAbsorptionAmount();
        return currentEffectiveHealth < previousEffectiveHealth - 1.0E-3f;
    }

}
