package woflo.petsplus.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

/**
 * Fired when two vanilla animals finish breeding and a child has been spawned.
 * Consumers can listen to inherit additional metadata, grant bonuses or emit
 * custom feedback without adding more mixins.
 */
public final class PetBreedEvent {
    /** Global hook invoked each time a tracked breeding pair produces a child. */
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
        listeners -> context -> {
            for (Listener listener : listeners) {
                listener.onPetBred(context);
            }
        }
    );

    private PetBreedEvent() {
    }

    /** Dispatches the breeding event. */
    public static void fire(Context context) {
        EVENT.invoker().onPetBred(context);
    }

    /** Listener for breeding events. */
    @FunctionalInterface
    public interface Listener {
        void onPetBred(Context context);
    }

    /** Immutable context describing the breeding interaction. */
    public static final class Context {
        private final AnimalEntity primaryParent;
        private final AnimalEntity partner;
        private final PassiveEntity child;
        private final @Nullable PetComponent primaryComponent;
        private final @Nullable PetComponent partnerComponent;
        private final @Nullable PetComponent childComponent;
        private final boolean roleInherited;
        private final boolean characteristicsInherited;
        private final @Nullable BirthContext birthContext;
        private final @Nullable Identifier assignedNature;

        public Context(AnimalEntity primaryParent,
                       AnimalEntity partner,
                       PassiveEntity child,
                       @Nullable PetComponent primaryComponent,
                       @Nullable PetComponent partnerComponent,
                       @Nullable PetComponent childComponent,
                       boolean roleInherited,
                       boolean characteristicsInherited,
                       @Nullable BirthContext birthContext,
                       @Nullable Identifier assignedNature) {
            this.primaryParent = primaryParent;
            this.partner = partner;
            this.child = child;
            this.primaryComponent = primaryComponent;
            this.partnerComponent = partnerComponent;
            this.childComponent = childComponent;
            this.roleInherited = roleInherited;
            this.characteristicsInherited = characteristicsInherited;
            this.birthContext = birthContext;
            this.assignedNature = assignedNature;
        }

        public AnimalEntity getPrimaryParent() {
            return primaryParent;
        }

        public AnimalEntity getPartner() {
            return partner;
        }

        public PassiveEntity getChild() {
            return child;
        }

        public @Nullable PetComponent getPrimaryComponent() {
            return primaryComponent;
        }

        public @Nullable PetComponent getPartnerComponent() {
            return partnerComponent;
        }

        public @Nullable PetComponent getChildComponent() {
            return childComponent;
        }

        public boolean isRoleInherited() {
            return roleInherited;
        }

        public boolean isCharacteristicsInherited() {
            return characteristicsInherited;
        }

        public @Nullable BirthContext getBirthContext() {
            return birthContext;
        }

        public @Nullable Identifier getAssignedNature() {
            return assignedNature;
        }
    }

    /** Snapshot of the world context surrounding a newborn's spawn moment. */
    public static final class BirthContext {
        private final long worldTime;
        private final long timeOfDay;
        private final Identifier dimensionId;
        private final boolean indoors;
        private final boolean daytime;
        private final boolean raining;
        private final boolean thundering;
        private final int nearbyPlayerCount;
        private final int nearbyPetCount;

        public BirthContext(long worldTime,
                            long timeOfDay,
                            Identifier dimensionId,
                            boolean indoors,
                            boolean daytime,
                            boolean raining,
                            boolean thundering,
                            int nearbyPlayerCount,
                            int nearbyPetCount) {
            this.worldTime = worldTime;
            this.timeOfDay = timeOfDay;
            this.dimensionId = dimensionId;
            this.indoors = indoors;
            this.daytime = daytime;
            this.raining = raining;
            this.thundering = thundering;
            this.nearbyPlayerCount = nearbyPlayerCount;
            this.nearbyPetCount = nearbyPetCount;
        }

        public long getWorldTime() {
            return worldTime;
        }

        public long getTimeOfDay() {
            return timeOfDay;
        }

        public Identifier getDimensionId() {
            return dimensionId;
        }

        public boolean isIndoors() {
            return indoors;
        }

        public boolean isDaytime() {
            return daytime;
        }

        public boolean isRaining() {
            return raining;
        }

        public boolean isThundering() {
            return thundering;
        }

        public int getNearbyPlayerCount() {
            return nearbyPlayerCount;
        }

        public int getNearbyPetCount() {
            return nearbyPetCount;
        }
    }
}
