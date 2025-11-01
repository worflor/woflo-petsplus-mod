package woflo.petsplus.state.modules;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;

public interface RoleModule extends DataBackedModule<RoleModule.Data> {
    @Nullable Identifier getRoleId();
    void setRoleId(@Nullable Identifier roleId);
    PetRoleType getRoleType(DynamicRegistryManager registryManager, boolean fallbackToDefault);
    void adjustAffinity(@Nullable Identifier roleId, Consumer<float[]> mutator);
    Map<Identifier, float[]> getAffinityVectors();

    record Data(@Nullable Identifier roleId, Map<Identifier, float[]> affinityVectors) {}
}
