package woflo.petsplus.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.state.PetComponent;

/**
 * Fired when a pet gains one or more levels. Listeners can award additional
 * rewards or suppress the default celebration feedback.
 */
public final class PetLevelUpEvent {
    /** Global event hook for pet level ups. */
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
        listeners -> context -> {
            for (Listener listener : listeners) {
                listener.onPetLevelUp(context);
            }
        }
    );

    private PetLevelUpEvent() {
    }

    /** Dispatches the level up event. */
    public static void fire(Context context) {
        EVENT.invoker().onPetLevelUp(context);
    }

    /** Listener for pet level up events. */
    @FunctionalInterface
    public interface Listener {
        /**
         * @param context mutable level-up context. Listeners may call
         *                {@link Context#suppressDefaultCelebration()} to prevent the
         *                built-in celebration effects.
         */
        void onPetLevelUp(Context context);
    }

    /** Mutable level-up context surfaced to listeners. */
    public static final class Context {
        private final ServerPlayerEntity owner;
        private final MobEntity pet;
        private final PetComponent component;
        private final int previousLevel;
        private final int newLevel;
        private final int xpAwarded;
        private boolean suppressDefaultCelebration;

        public Context(ServerPlayerEntity owner,
                       MobEntity pet,
                       PetComponent component,
                       int previousLevel,
                       int newLevel,
                       int xpAwarded) {
            this.owner = owner;
            this.pet = pet;
            this.component = component;
            this.previousLevel = previousLevel;
            this.newLevel = newLevel;
            this.xpAwarded = xpAwarded;
        }

        /**
         * @return pet owner responsible for the level up
         */
        public ServerPlayerEntity getOwner() {
            return owner;
        }

        /**
         * @return the pet that leveled up
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
         * @return the pet's level before applying the XP award
         */
        public int getPreviousLevel() {
            return previousLevel;
        }

        /**
         * @return the new pet level after the award
         */
        public int getNewLevel() {
            return newLevel;
        }

        /**
         * @return XP amount that triggered the level up (after all modifiers)
         */
        public int getXpAwarded() {
            return xpAwarded;
        }

        /**
         * Prevents the stock celebration feedback and advancement hooks from
         * running.
         */
        public void suppressDefaultCelebration() {
            this.suppressDefaultCelebration = true;
        }

        /**
         * @return whether the caller requested the default celebration to be skipped
         */
        public boolean isDefaultCelebrationSuppressed() {
            return suppressDefaultCelebration;
        }
    }
}
