package woflo.petsplus.roles.guardian;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.state.PetComponent;

/**
 * Shared helpers and state carriers used by Guardian Bulwark redirection logic.
 */
public final class GuardianBulwark {
    public static final String STATE_DATA_KEY = "guardian_bulwark_state";
    public static final float MINIMUM_REDIRECT = 0.001f;
    public static final float RESERVE_TOLERANCE = 0.0001f;

    private GuardianBulwark() {
    }

    public static boolean isDisallowedSource(DamageSource source) {
        if (source == null) {
            return true;
        }
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

    public static boolean applyDamageToGuardian(MobEntity guardian, DamageSource source, float amount) {
        if (!(guardian.getEntityWorld() instanceof ServerWorld world)) {
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

    /**
     * Shared mutable state threaded through the owner damage trigger so that every
     * guardian evaluates the same redirection results without recomputing.
     */
    public static final class SharedState {
        private final Map<UUID, RedirectRecord> redirectRecords = new HashMap<>();
        private boolean processed;
        private double finalOwnerDamage;

        public boolean isProcessed() {
            return processed;
        }

        public void markProcessed() {
            this.processed = true;
        }

        public void setFinalOwnerDamage(double finalOwnerDamage) {
            this.finalOwnerDamage = finalOwnerDamage;
        }

        public double getFinalOwnerDamage() {
            return finalOwnerDamage;
        }

        public void recordRedirect(MobEntity guardian,
                                    float requestedAmount,
                                    float appliedAmount,
                                    boolean hitReserveLimit,
                                    PetComponent component) {
            if (guardian == null || component == null) {
                return;
            }
            redirectRecords.put(guardian.getUuid(), new RedirectRecord(requestedAmount, appliedAmount, hitReserveLimit,
                component.getLevel()));
        }

        public RedirectRecord getRecord(MobEntity guardian) {
            if (guardian == null) {
                return null;
            }
            return redirectRecords.get(guardian.getUuid());
        }

        public boolean hasRedirects() {
            return !redirectRecords.isEmpty();
        }
    }

    /**
     * Captures the outcome of a single guardian's redirect attempt so follow-up
     * effects can decide whether to apply buffs or feedback.
     */
    public static final class RedirectRecord {
        private final float requestedAmount;
        private final float appliedAmount;
        private final boolean hitReserveLimit;
        private final int guardianLevel;

        RedirectRecord(float requestedAmount, float appliedAmount, boolean hitReserveLimit, int guardianLevel) {
            this.requestedAmount = requestedAmount;
            this.appliedAmount = appliedAmount;
            this.hitReserveLimit = hitReserveLimit;
            this.guardianLevel = guardianLevel;
        }

        public float requestedAmount() {
            return requestedAmount;
        }

        public float appliedAmount() {
            return appliedAmount;
        }

        public boolean hitReserveLimit() {
            return hitReserveLimit;
        }

        public int guardianLevel() {
            return guardianLevel;
        }

        public boolean succeeded() {
            return appliedAmount > MINIMUM_REDIRECT;
        }

        public float healthFractionAfterRedirect(MobEntity guardian, float reserveFraction) {
            if (guardian == null) {
                return 0.0f;
            }
            float maxHealth = guardian.getMaxHealth();
            if (maxHealth <= 0.0f) {
                return 0.0f;
            }
            float reserve = maxHealth * reserveFraction;
            float remaining = Math.max(0.0f, guardian.getHealth() - appliedAmount);
            float usable = Math.max(0.0f, remaining - reserve);
            return MathHelper.clamp(usable / maxHealth, 0.0f, 1.0f);
        }
    }
}

