package woflo.petsplus.mixin;

import net.minecraft.entity.passive.OcelotEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker to access the trusting state on {@link OcelotEntity}.
 */
@Mixin(OcelotEntity.class)
public interface OcelotEntityInvoker {

    @Invoker("isTrusting")
    boolean petsplus$invokeIsTrusting();
}
