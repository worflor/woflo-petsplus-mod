package woflo.petsplus.api;

/**
 * Mutable carrier used when ability effects want to intercept or scale incoming
 * damage. Instances are shared across every ability invocation for a specific
 * trigger so an effect can flag that the hit should be cancelled or adjust the
 * amount that will ultimately be applied.
 */
public final class DamageInterceptionResult {
    private final double initialDamageAmount;
    private double remainingDamageAmount;
    private boolean cancelled;
    private boolean modified;

    public DamageInterceptionResult(double initialDamageAmount) {
        this.initialDamageAmount = Math.max(0.0D, initialDamageAmount);
        this.remainingDamageAmount = this.initialDamageAmount;
    }

    /**
     * @return the original damage amount that was supplied when the interception
     *         result was created.
     */
    public double getInitialDamageAmount() {
        return initialDamageAmount;
    }

    /**
     * @return the remaining amount of damage that should be applied. Returns
     *         {@code 0} when the hit has been cancelled.
     */
    public double getRemainingDamageAmount() {
        return cancelled ? 0.0D : remainingDamageAmount;
    }

    /**
     * Replaces the remaining damage amount with the supplied value. Passing a
     * value {@code <= 0} will cancel the hit entirely.
     */
    public void setRemainingDamageAmount(double damageAmount) {
        double clamped = Math.max(0.0D, damageAmount);
        this.remainingDamageAmount = clamped;
        this.cancelled = clamped <= 0.0D;
        this.modified = true;
    }

    /**
     * Marks the interception as fully cancelled.
     */
    public void cancel() {
        this.cancelled = true;
        this.modified = true;
        this.remainingDamageAmount = 0.0D;
    }

    /**
     * @return {@code true} when an effect has cancelled the hit.
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * @return {@code true} when any effect adjusted or cancelled the hit.
     */
    public boolean isModified() {
        return modified || cancelled;
    }
}
