package woflo.petsplus.mixin;

import net.minecraft.component.ComponentType;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin exposing the protected component access helpers on {@link Entity}.
 */
@Mixin(Entity.class)
public interface EntityComponentAccessor {

    @Invoker("castComponentValue")
    static <T> T petsplus$castComponentValue(ComponentType<T> type, Object fallback) {
        throw new AssertionError();
    }
}
