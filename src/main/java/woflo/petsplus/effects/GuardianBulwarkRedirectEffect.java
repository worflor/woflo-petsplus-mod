package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import java.util.List;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.roles.guardian.GuardianBulwark;
import woflo.petsplus.roles.guardian.GuardianCore;
import woflo.petsplus.roles.guardian.GuardianCore.GuardianCandidate;
import woflo.petsplus.state.PetComponent;

import static woflo.petsplus.roles.guardian.GuardianFortressBondManager.computeRedirectedDamage;
import static woflo.petsplus.roles.guardian.GuardianFortressBondManager.modifyOwnerDamage;

/**
 * Redirects incoming owner damage to nearby Guardian pets while populating shared
 * state for the follow-up ability effects.
 */
public class GuardianBulwarkRedirectEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "guardian_bulwark_redirect");
    private static final String SUCCESS_FLAG = "guardian_bulwark_redirect_success";

    public GuardianBulwarkRedirectEffect() {
    }

    public GuardianBulwarkRedirectEffect(JsonObject json) {
        // Reserved for future configuration options.
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        context.withData(SUCCESS_FLAG, Boolean.FALSE);

        if (!context.hasDamageContext()) {
            return false;
        }

        DamageInterceptionResult result = context.getDamageResult();
        if (result == null) {
            return false;
        }

        PlayerEntity ownerEntity = context.getOwner();
        if (!(ownerEntity instanceof ServerPlayerEntity owner)) {
            return false;
        }

        TriggerContext trigger = context.getTriggerContext();
        GuardianBulwark.SharedState state = resolveState(trigger);
        if (state == null) {
            return false;
        }

        DamageSource source = context.getIncomingDamageSource();
        if (!state.isProcessed()) {
            processRedirect(state, owner, source, result);
        }

        MobEntity pet = context.getPet();
        GuardianBulwark.RedirectRecord record = state.getRecord(pet);
        if (record == null || !record.succeeded()) {
            return false;
        }

        context.withData(SUCCESS_FLAG, Boolean.TRUE);
        return true;
    }

    private GuardianBulwark.SharedState resolveState(TriggerContext trigger) {
        if (trigger == null) {
            return null;
        }
        GuardianBulwark.SharedState state = trigger.getData(GuardianBulwark.STATE_DATA_KEY, GuardianBulwark.SharedState.class);
        if (state == null) {
            Object value = trigger.getEventData().get(GuardianBulwark.STATE_DATA_KEY);
            if (value instanceof GuardianBulwark.SharedState shared) {
                state = shared;
            }
        }
        return state;
    }

    private void processRedirect(GuardianBulwark.SharedState state,
                                 ServerPlayerEntity owner,
                                 DamageSource source,
                                 DamageInterceptionResult result) {
        float ownerDamage = (float) result.getRemainingDamageAmount();
        float initialDamage = (float) result.getInitialDamageAmount();

        if (ownerDamage <= 0.0f) {
            float modified = modifyOwnerDamage(owner, 0.0f);
            result.setRemainingDamageAmount(modified);
            state.setFinalOwnerDamage(modified);
            state.markProcessed();
            return;
        }

        if (GuardianBulwark.isDisallowedSource(source)) {
            float modified = modifyOwnerDamage(owner, ownerDamage);
            result.setRemainingDamageAmount(modified);
            state.setFinalOwnerDamage(modified);
            state.markProcessed();
            return;
        }

        List<GuardianCandidate> candidates = GuardianCore.collectGuardiansForIntercept(owner);
        if (candidates.isEmpty()) {
            float modified = modifyOwnerDamage(owner, ownerDamage);
            result.setRemainingDamageAmount(modified);
            state.setFinalOwnerDamage(modified);
            state.markProcessed();
            return;
        }

        float remainingOwnerDamage = ownerDamage;

        for (GuardianCandidate candidate : candidates) {
            if (remainingOwnerDamage <= GuardianBulwark.MINIMUM_REDIRECT) {
                break;
            }

            MobEntity guardian = candidate.pet();
            PetComponent component = candidate.component();
            float reserveFraction = candidate.reserveFraction();

            if (!GuardianCore.canGuardianSafelyRedirect(guardian, reserveFraction)) {
                continue;
            }

            float scaledRatio = GuardianCore.computeRedirectRatio(component.getLevel()) * candidate.healthFraction();
            if (scaledRatio <= 0.0f) {
                continue;
            }

            float desiredRedirect = initialDamage * scaledRatio;
            float available = Math.max(0.0f, guardian.getHealth() - candidate.reserveHealth());
            float redirectedAmount = Math.min(Math.min(desiredRedirect, available), remainingOwnerDamage);
            if (redirectedAmount <= GuardianBulwark.MINIMUM_REDIRECT) {
                continue;
            }

            boolean hitReserveLimit = available > 0.0f && redirectedAmount >= available - GuardianBulwark.RESERVE_TOLERANCE;
            float reducedRedirect = computeRedirectedDamage(owner, guardian, redirectedAmount);
            if (reducedRedirect <= GuardianBulwark.MINIMUM_REDIRECT) {
                continue;
            }

            if (!GuardianBulwark.applyDamageToGuardian(guardian, source, redirectedAmount)) {
                continue;
            }

            GuardianCore.recordSuccessfulRedirect(owner, guardian, component, initialDamage, redirectedAmount,
                reserveFraction, hitReserveLimit, reducedRedirect);
            state.recordRedirect(guardian, redirectedAmount, reducedRedirect, hitReserveLimit, component);
            remainingOwnerDamage = Math.max(0.0f, remainingOwnerDamage - reducedRedirect);
        }

        float modified = modifyOwnerDamage(owner, remainingOwnerDamage);
        result.setRemainingDamageAmount(modified);
        state.setFinalOwnerDamage(modified);
        state.markProcessed();
    }
}
