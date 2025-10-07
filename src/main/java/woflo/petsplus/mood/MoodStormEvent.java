package woflo.petsplus.mood;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.mood.storm.MoodStormDefinition;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;

import java.util.List;
import java.util.UUID;

/**
 * Fired when a chorus of pets reaches a mood resonance strong enough to trigger
 * a mood storm. Listeners can use this event to react with custom visuals,
 * rewards, or penalties.
 */
public final class MoodStormEvent {
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
        listeners -> context -> {
            for (Listener listener : listeners) {
                listener.onMoodStorm(context);
            }
        }
    );

    private MoodStormEvent() {
    }

    public static void fire(Context context) {
        EVENT.invoker().onMoodStorm(context);
    }

    @FunctionalInterface
    public interface Listener {
        void onMoodStorm(Context context);
    }

    public static final class Context {
        private final ServerWorld world;
        private final @Nullable ServerPlayerEntity owner;
        private final UUID ownerId;
        private final PetComponent.Mood mood;
        private final List<PetSwarmIndex.SwarmEntry> chorusMembers;
        private final List<PetSwarmIndex.SwarmEntry> pushTargets;
        private final Vec3d center;
        private final float averageLevel;
        private final float averageStrength;
        private final long tick;
        private final double pushRadius;
        private final double particleRadius;
        private final int particleCount;
        private final double particleSpeed;
        private final float pushAmount;
        private final @Nullable MoodStormDefinition definition;

        public Context(ServerWorld world,
                       @Nullable ServerPlayerEntity owner,
                       UUID ownerId,
                       PetComponent.Mood mood,
                       List<PetSwarmIndex.SwarmEntry> chorusMembers,
                       List<PetSwarmIndex.SwarmEntry> pushTargets,
                       Vec3d center,
                       float averageLevel,
                       float averageStrength,
                       long tick,
                       double pushRadius,
                       double particleRadius,
                       int particleCount,
                       double particleSpeed,
                       float pushAmount,
                       @Nullable MoodStormDefinition definition) {
            this.world = world;
            this.owner = owner;
            this.ownerId = ownerId;
            this.mood = mood;
            this.chorusMembers = List.copyOf(chorusMembers);
            this.pushTargets = List.copyOf(pushTargets);
            this.center = center;
            this.averageLevel = averageLevel;
            this.averageStrength = averageStrength;
            this.tick = tick;
            this.pushRadius = pushRadius;
            this.particleRadius = particleRadius;
            this.particleCount = particleCount;
            this.particleSpeed = particleSpeed;
            this.pushAmount = pushAmount;
            this.definition = definition;
        }

        public ServerWorld world() {
            return world;
        }

        @Nullable
        public ServerPlayerEntity owner() {
            return owner;
        }

        public UUID ownerId() {
            return ownerId;
        }

        public PetComponent.Mood mood() {
            return mood;
        }

        public List<PetSwarmIndex.SwarmEntry> chorusMembers() {
            return chorusMembers;
        }

        public List<PetSwarmIndex.SwarmEntry> pushTargets() {
            return pushTargets;
        }

        public Vec3d center() {
            return center;
        }

        public float averageLevel() {
            return averageLevel;
        }

        public float averageStrength() {
            return averageStrength;
        }

        public long tick() {
            return tick;
        }

        public double pushRadius() {
            return pushRadius;
        }

        public double particleRadius() {
            return particleRadius;
        }

        public int particleCount() {
            return particleCount;
        }

        public double particleSpeed() {
            return particleSpeed;
        }

        public float pushAmount() {
            return pushAmount;
        }

        @Nullable
        public MoodStormDefinition definition() {
            return definition;
        }
    }
}
