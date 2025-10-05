package woflo.petsplus.state.modules;

import java.util.Collections;
import java.util.List;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.state.PetComponent;

public interface PetModule {
    void onAttach(PetComponent parent);

    default void onDetach() {}

    default void onTick(ServerWorld world, long tick) {}

    default List<ValidationIssue> validate() {
        return Collections.emptyList();
    }

    record ValidationIssue(String code, String message) {}
}
