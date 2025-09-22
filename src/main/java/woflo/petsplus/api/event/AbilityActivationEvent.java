package woflo.petsplus.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.Ability;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

/**
 * Fired whenever a pet ability is about to run or has just finished running.
 * <p>
 * Listeners may cancel the {@link Phase#PRE PRE} invocation to veto the
 * activation, adjust the cooldown that will be applied, or opt into handling
 * the execution themselves. The {@link Phase#POST POST} invocation reflects
 * the final activation result and still respects the cooldown overrides that
 * were configured during PRE.
 */
public final class AbilityActivationEvent {
    /**
     * Global event hook for ability activations.
     */
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
        listeners -> context -> {
            for (Listener listener : listeners) {
                listener.onAbilityActivation(context);
                if (context.getPhase() == Phase.PRE && context.isCancelled()) {
                    return;
                }
            }
        }
    );

    private AbilityActivationEvent() {
    }

    /**
     * Dispatches the PRE phase for the supplied context.
     */
    public static void firePre(Context context) {
        context.setPhase(Phase.PRE);
        EVENT.invoker().onAbilityActivation(context);
    }

    /**
     * Dispatches the POST phase for the supplied context.
     */
    public static void firePost(Context context) {
        context.setPhase(Phase.POST);
        EVENT.invoker().onAbilityActivation(context);
    }

    /**
     * Listener callback for ability activation events.
     */
    @FunctionalInterface
    public interface Listener {
        /**
         * @param context mutable activation context exposing control over cooldowns
         *                and execution flow
         */
        void onAbilityActivation(Context context);
    }

    /**
     * Activation phase.
     */
    public enum Phase {
        /**
         * Called before the ability attempts to execute. Listeners may cancel the
         * activation, bypass cooldown checks, or override the cooldown that will be
         * applied after execution.
         */
        PRE,
        /**
         * Called after the ability has run (or was short-circuited). Provides access
         * to the final success flag and still honours cooldown overrides.
         */
        POST
    }

    /**
     * Mutable activation context surfaced to listeners.
     */
    public static final class Context {
        private final Ability ability;
        private final TriggerContext triggerContext;
        private final PetComponent component;
        private final @Nullable PetRoleType roleType;
        private final boolean onCooldown;
        private Phase phase;
        private boolean cancelled;
        private boolean runDefaultExecution = true;
        private boolean succeeded;
        private boolean applyCooldown = true;
        private boolean applyCooldownOnFailure;
        private boolean bypassCooldown;
        private int cooldownTicks;

        public Context(Ability ability,
                       TriggerContext triggerContext,
                       PetComponent component,
                       @Nullable PetRoleType roleType,
                       boolean onCooldown,
                       int baseCooldownTicks) {
            this.ability = ability;
            this.triggerContext = triggerContext;
            this.component = component;
            this.roleType = roleType;
            this.onCooldown = onCooldown;
            this.phase = Phase.PRE;
            this.cooldownTicks = baseCooldownTicks;
        }

        /**
         * @return the ability about to execute
         */
        public Ability getAbility() {
            return ability;
        }

        /**
         * @return identifier backing the ability instance
         */
        public Identifier getAbilityId() {
            return ability.getId();
        }

        /**
         * @return pet component backing the activation
         */
        public PetComponent getComponent() {
            return component;
        }

        /**
         * @return role metadata resolved for the pet, or {@code null} if unavailable
         */
        @Nullable
        public PetRoleType getRoleType() {
            return roleType;
        }

        /**
         * @return trigger context supplied to the ability
         */
        public TriggerContext getTriggerContext() {
            return triggerContext;
        }

        /**
         * @return pet executing the ability
         */
        public MobEntity getPet() {
            return triggerContext.getPet();
        }

        /**
         * @return owning player of the pet if available
         */
        @Nullable
        public PlayerEntity getOwner() {
            return triggerContext.getOwner();
        }

        /**
         * @return whether the ability was already cooling down before activation
         */
        public boolean isOnCooldown() {
            return onCooldown;
        }

        Phase getPhase() {
            return phase;
        }

        void setPhase(Phase phase) {
            this.phase = phase;
        }

        /**
         * Cancels the activation when called during {@link Phase#PRE}.
         */
        public void cancel() {
            this.cancelled = true;
        }

        /**
         * @return whether the activation has been cancelled
         */
        public boolean isCancelled() {
            return cancelled;
        }

        /**
         * Marks that the default {@link Ability#tryActivate(TriggerContext)} call
         * should be skipped. Listeners that opt out of the default execution should
         * also set {@link #setSucceeded(boolean)} to communicate their final
         * decision.
         */
        public void setRunDefaultExecution(boolean runDefaultExecution) {
            this.runDefaultExecution = runDefaultExecution;
        }

        /**
         * @return whether the ability will run through its built-in logic
         */
        public boolean shouldRunDefaultExecution() {
            return runDefaultExecution;
        }

        /**
         * @return whether the activation succeeded
         */
        public boolean didSucceed() {
            return succeeded;
        }

        /**
         * Updates the activation success flag, typically used by listeners that
         * perform their own execution or by POST-phase listeners that want to adjust
         * the final outcome.
         */
        public void setSucceeded(boolean succeeded) {
            this.succeeded = succeeded;
        }

        /**
         * Overrides the cooldown duration (in ticks) that will be applied when the
         * activation finishes. Values {@code <= 0} clear the cooldown entirely.
         */
        public void setCooldownTicks(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
        }

        /**
         * @return configured cooldown (in ticks) to apply after activation
         */
        public int getCooldownTicks() {
            return cooldownTicks;
        }

        /**
         * Controls whether a cooldown should be applied at all.
         */
        public void setApplyCooldown(boolean applyCooldown) {
            this.applyCooldown = applyCooldown;
        }

        /**
         * @return whether a cooldown should be applied after activation
         */
        public boolean shouldApplyCooldown() {
            return applyCooldown;
        }

        /**
         * Allows listeners to request that cooldowns are still applied even when the
         * activation failed (default is {@code false}).
         */
        public void setApplyCooldownOnFailure(boolean applyCooldownOnFailure) {
            this.applyCooldownOnFailure = applyCooldownOnFailure;
        }

        /**
         * @return whether cooldowns should still be applied when the activation fails
         */
        public boolean shouldApplyCooldownOnFailure() {
            return applyCooldownOnFailure;
        }

        /**
         * @return whether listeners requested the existing cooldown to be bypassed
         */
        public boolean shouldBypassCooldown() {
            return bypassCooldown;
        }

        /**
         * Allows listeners to ignore the current cooldown gate for this activation.
         */
        public void setBypassCooldown(boolean bypassCooldown) {
            this.bypassCooldown = bypassCooldown;
        }
    }
}
