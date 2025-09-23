package woflo.petsplus.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

/**
 * Fired when a player attempts to pay tribute for a pet milestone. Listeners
 * can alter the required item, veto the interaction, or short-circuit tribute
 * processing entirely.
 */
public final class TributeCheckEvent {
    /** Global event hook for tribute interactions. */
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
        listeners -> context -> {
            for (Listener listener : listeners) {
                listener.onTributeCheck(context);
                if (context.isCancelled() || context.isHandled()) {
                    return;
                }
            }
        }
    );

    private TributeCheckEvent() {
    }

    /** Dispatches the tribute check event. */
    public static void fire(Context context) {
        EVENT.invoker().onTributeCheck(context);
    }

    /** Listener for tribute check events. */
    @FunctionalInterface
    public interface Listener {
        /**
         * @param context mutable tribute context that exposes control over the
         *                required item, milestone level, and whether the default
         *                tribute logic should run.
         */
        void onTributeCheck(Context context);
    }

    /** Mutable tribute interaction context. */
    public static final class Context {
        private final ServerPlayerEntity player;
        private final MobEntity pet;
        private final PetComponent component;
        private final ItemStack offeredStack;
        private final @Nullable PetRoleType roleType;
        private boolean cancelled;
        private boolean handled;
        private ActionResult result = ActionResult.PASS;
        private boolean consumeItem = true;
        private int milestoneLevel;
        private Item requiredItem;

        public Context(ServerPlayerEntity player,
                       MobEntity pet,
                       PetComponent component,
                       @Nullable PetRoleType roleType,
                       ItemStack offeredStack,
                       int milestoneLevel,
                       @Nullable Item requiredItem) {
            this.player = player;
            this.pet = pet;
            this.component = component;
            this.roleType = roleType;
            this.offeredStack = offeredStack;
            this.milestoneLevel = milestoneLevel;
            this.requiredItem = requiredItem;
        }

        /**
         * @return player attempting to pay tribute
         */
        public ServerPlayerEntity getPlayer() {
            return player;
        }

        /**
         * @return pet receiving the tribute
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
         * @return role metadata associated with the pet, or {@code null} if missing
         */
        @Nullable
        public PetRoleType getRoleType() {
            return roleType;
        }

        /**
         * @return stack offered by the player (live reference)
         */
        public ItemStack getOfferedStack() {
            return offeredStack;
        }

        /**
         * Cancels the tribute interaction entirely.
         */
        public void cancel() {
            this.cancelled = true;
        }

        /**
         * @return whether the tribute was cancelled
         */
        public boolean isCancelled() {
            return cancelled;
        }

        /**
         * Marks the tribute as fully handled by the listener.
         */
        public void markHandled(ActionResult result) {
            this.handled = true;
            this.result = result;
        }

        /**
         * @return whether a listener already handled the tribute
         */
        public boolean isHandled() {
            return handled;
        }

        /**
         * @return result reported by the handling listener
         */
        public ActionResult getResult() {
            return result;
        }

        /**
         * @return level milestone being processed
         */
        public int getMilestoneLevel() {
            return milestoneLevel;
        }

        /**
         * Overrides the milestone level that should be unlocked.
         */
        public void setMilestoneLevel(int milestoneLevel) {
            this.milestoneLevel = milestoneLevel;
        }

        /**
         * @return item required for tribute acceptance (may be {@code null})
         */
        @Nullable
        public Item getRequiredItem() {
            return requiredItem;
        }

        /**
         * Updates the required tribute item. Passing {@code null} allows the default
         * handler to reject the interaction unless another listener accepts it.
         */
        public void setRequiredItem(@Nullable Item requiredItem) {
            this.requiredItem = requiredItem;
        }

        /**
         * @return whether the default handler should consume an item on success
         */
        public boolean shouldConsumeItem() {
            return consumeItem;
        }

        /**
         * Controls whether the default handler should consume the offered item on
         * success.
         */
        public void setConsumeItem(boolean consumeItem) {
            this.consumeItem = consumeItem;
        }
    }
}
