package woflo.petsplus.stats;

import java.util.function.Function;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.nature.NatureModifierSampler;

/**
 * Centralises stat-derived scalars so gameplay systems can share the same
 * interpretation of vitality, guard, might, and focus multipliers without
 * duplicating lookup logic.
 */
public final class StatScaling {
    private static final float MIN_MULTIPLIER = 0.4f;
    private static final float MAX_MULTIPLIER = 2.5f;

    private StatScaling() {
    }

    private static float combine(PetComponent component, Function<StatModifierProvider, Float> getter) {
        if (component == null) {
            return 1.0f;
        }

        float multiplier = 1.0f;

        PetImprint imprint = component.getImprint();
        if (imprint != null) {
            multiplier *= Math.max(0.0f, getter.apply(imprint));
        }

        NatureModifierSampler.NatureAdjustment nature = NatureModifierSampler.sample(component);
        if (nature != null && !nature.isEmpty()) {
            multiplier *= Math.max(0.0f, getter.apply(nature));
        }

        return MathHelper.clamp(multiplier, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    public static float vitality(PetComponent component) {
        return combine(component, StatModifierProvider::getVitalityMultiplier);
    }

    public static float might(PetComponent component) {
        return combine(component, StatModifierProvider::getMightMultiplier);
    }

    public static float focus(PetComponent component) {
        return combine(component, StatModifierProvider::getFocusMultiplier);
    }

    public static float guard(PetComponent component) {
        if (component == null) {
            return 1.0f;
        }

        float multiplier = 1.0f;

        PetImprint imprint = component.getImprint();
        if (imprint != null) {
            multiplier *= imprint.getGuardMultiplier();
        }

        float guardModifier = component.getNatureGuardModifier();
        if (guardModifier > 0.0f) {
            multiplier *= guardModifier;
        }

        return MathHelper.clamp(multiplier, MIN_MULTIPLIER, MAX_MULTIPLIER);
    }

    public static float scaleHealingAmount(PetComponent component, float baseAmount) {
        float vitality = vitality(component);
        float bonus = MathHelper.clamp((vitality - 1.0f) * 0.25f, -0.2f, 0.35f);
        return baseAmount * (1.0f + bonus);
    }

    public static float guardSoakMultiplier(PetComponent component) {
        float guard = guard(component);
        float soak = MathHelper.clamp((guard - 1.0f) * 0.08f, -0.08f, 0.08f);
        return MathHelper.clamp(1.0f - soak, 0.7f, 1.2f);
    }

    public static float guardKnockbackBonus(PetComponent component) {
        float guard = guard(component);
        return MathHelper.clamp((guard - 1.0f) * 0.5f, -0.35f, 0.35f);
    }

    public static float mightDamageMultiplier(PetComponent component) {
        return MathHelper.clamp(might(component), 0.6f, 1.8f);
    }

    public static float mightKnockbackBonus(PetComponent component) {
        float multiplier = might(component);
        return MathHelper.clamp((multiplier - 1.0f) * 0.8f, -0.45f, 0.6f);
    }

    public static float focusCooldownScalar(PetComponent component) {
        float focus = focus(component);
        float reduction = MathHelper.clamp((focus - 1.0f) * 0.12f, -0.08f, 0.12f);
        return MathHelper.clamp(1.0f - reduction, 0.7f, 1.15f);
    }

    public static double focusPathToleranceScalar(PetComponent component) {
        float focus = focus(component);
        double tightening = MathHelper.clamp((focus - 1.0f) * 0.2f, -0.18f, 0.18f);
        return MathHelper.clamp(1.0 - tightening, 0.45, 1.15);
    }

    public static long vitalityReviveCooldown(long baseTicks, PetComponent component) {
        float vitality = vitality(component);
        float modifier = MathHelper.clamp((vitality - 1.0f) * 0.18f, -0.2f, 0.2f);
        long adjusted = Math.round(baseTicks * MathHelper.clamp(1.0f - modifier, 0.6f, 1.3f));
        return Math.max(1L, adjusted);
    }
}
