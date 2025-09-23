package woflo.petsplus.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

/**
 * Fired before experience is applied to a pet, allowing listeners to adjust the
 * amount or veto the award entirely.
 */
public final class XpAwardEvent {
    /** Identifier describing XP that originated from the owner's XP gain share. */
    public static final Identifier OWNER_XP_SHARE = Identifier.of("petsplus", "owner_xp_share");

    /** Global event hook for pet XP awards. */
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
        listeners -> context -> {
            for (Listener listener : listeners) {
                listener.onXpAward(context);
                if (context.isCancelled()) {
                    return;
                }
            }
        }
    );

    private XpAwardEvent() {
    }

    /**
     * Helper to dispatch the event.
     */
    public static void fire(Context context) {
        EVENT.invoker().onXpAward(context);
    }

    /** Callback for XP award listeners. */
    @FunctionalInterface
    public interface Listener {
        /**
         * @param context mutable XP award context. Listeners may adjust the amount
         *                or call {@link Context#cancel()} to prevent the award.
         */
        void onXpAward(Context context);
    }

    /**
     * Mutable XP award context surfaced to listeners.
     */
    public static final class Context {
        private final @Nullable ServerPlayerEntity owner;
        private final MobEntity pet;
        private final PetComponent component;
        private final int baseAmount;
        private Identifier source;
        private int amount;
        private boolean cancelled;

        public Context(@Nullable ServerPlayerEntity owner,
                       MobEntity pet,
                       PetComponent component,
                       int baseAmount,
                       int amount,
                       Identifier source) {
            this.owner = owner;
            this.pet = pet;
            this.component = component;
            this.baseAmount = baseAmount;
            this.amount = amount;
            this.source = source;
        }

        /**
         * @return owner responsible for the award, if available
         */
        @Nullable
        public ServerPlayerEntity getOwner() {
            return owner;
        }

        /**
         * @return pet receiving the award
         */
        public MobEntity getPet() {
            return pet;
        }

        /**
         * @return component backing the pet
         */
        public PetComponent getComponent() {
            return component;
        }

        /**
         * @return original XP before modifiers were applied
         */
        public int getBaseAmount() {
            return baseAmount;
        }

        /**
         * @return mutable XP amount that will be granted
         */
        public int getAmount() {
            return amount;
        }

        /**
         * Updates the final XP amount that will be granted.
         */
        public void setAmount(int amount) {
            this.amount = Math.max(0, amount);
        }

        /**
         * @return categorisation identifier describing the XP source
         */
        public Identifier getSource() {
            return source;
        }

        /**
         * Updates the XP source identifier.
         */
        public void setSource(Identifier source) {
            this.source = source;
        }

        /**
         * Prevents the XP award from being applied.
         */
        public void cancel() {
            this.cancelled = true;
        }

        /**
         * @return whether the award has been cancelled
         */
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
