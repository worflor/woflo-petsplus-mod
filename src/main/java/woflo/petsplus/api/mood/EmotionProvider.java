package woflo.petsplus.api.mood;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.state.PetComponent;

/**
 * Supplies small emotion contributions based on world/pet state.
 * Should be lightweight: avoid scans, use cheap checks, and rate-limit internally if needed.
 */
public interface EmotionProvider {
    /** A short unique id for logging/tuning. */
    String id();

    /** Rough frequency hint (ticks) used by the service for per-pet rate-limiting. */
    default int periodHintTicks() { return 40; }

    /** Contribute emotions. Keep it fast; avoid allocations. */
    void contribute(ServerWorld world, MobEntity pet, PetComponent comp, long time, MoodAPI api);
}
