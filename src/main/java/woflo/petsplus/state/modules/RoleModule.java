package woflo.petsplus.state.modules;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.registry.PetRoleType;

public interface RoleModule extends DataBackedModule<RoleModule.Data> {
    Identifier getRoleId();
    void setRoleId(Identifier roleId);
    PetRoleType getRoleType(DynamicRegistryManager registryManager, boolean fallbackToDefault);
    void adjustAffinity(Identifier roleId, Consumer<float[]> mutator);
    Map<Identifier, float[]> getAffinityVectors();

    record Data(Identifier roleId, Map<Identifier, float[]> affinityVectors) {}
}
