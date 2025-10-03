package woflo.petsplus.abilities;

import org.jetbrains.annotations.Nullable;

import woflo.petsplus.api.DamageInterceptionResult;

/**
 * Aggregated result emitted after an ability batch has finished processing.
 * Exposes whether any ability successfully executed and surfaces the shared
 * {@link DamageInterceptionResult} when a trigger included lethal-damage
 * metadata.
 */
public final class AbilityTriggerResult {
    private static final AbilityTriggerResult EMPTY = new AbilityTriggerResult(false, null);

    private final boolean anyActivated;
    private final DamageInterceptionResult damageResult;

    private AbilityTriggerResult(boolean anyActivated, @Nullable DamageInterceptionResult damageResult) {
        this.anyActivated = anyActivated;
        this.damageResult = damageResult;
    }

    public static AbilityTriggerResult empty() {
        return EMPTY;
    }

    public static AbilityTriggerResult of(boolean anyActivated,
                                          @Nullable DamageInterceptionResult damageResult) {
        if (!anyActivated && damageResult == null) {
            return EMPTY;
        }
        return new AbilityTriggerResult(anyActivated, damageResult);
    }

    public boolean anyActivated() {
        return anyActivated;
    }

    @Nullable
    public DamageInterceptionResult damageResult() {
        return damageResult;
    }

    public boolean damageCancelled() {
        return damageResult != null && damageResult.isCancelled();
    }

    public boolean damageModified() {
        return damageResult != null && damageResult.isModified();
    }
}
