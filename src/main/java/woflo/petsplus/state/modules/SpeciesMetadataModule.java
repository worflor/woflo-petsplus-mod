package woflo.petsplus.state.modules;

import net.minecraft.util.Identifier;
import woflo.petsplus.state.PetComponent;

public interface SpeciesMetadataModule extends DataBackedModule<SpeciesMetadataModule.Data> {
    Identifier getSpeciesDescriptor();
    PetComponent.FlightCapability getFlightCapability();
    void markSpeciesDirty();
    void markFlightDirty();

    record Data(Identifier speciesDescriptor, PetComponent.FlightCapability flightCapability) {}
}
