package woflo.petsplus.stats.nature;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

/**
 * Placeholder handler retained for API compatibility while the mood overhaul
 * work is in flight. Once the emotion pipeline is available again the
 * implementation can be restored without touching call sites.
 */
public final class NatureFlavorHandler {
    private NatureFlavorHandler() {
    }

    public static void applyAmbientFlavor(MobEntity pet, PetComponent component, ServerWorld world,
                                          @Nullable ServerPlayerEntity owner) {
        // Intentionally left blank until the mood/emotion system is ready.
    }
}
