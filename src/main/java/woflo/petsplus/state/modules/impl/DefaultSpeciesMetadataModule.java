package woflo.petsplus.state.modules.impl;

import net.minecraft.util.Identifier;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.modules.SpeciesMetadataModule;

/**
 * Default implementation of SpeciesMetadataModule that manages cached species
 * descriptor and flight capability data with dirty flag tracking.
 */
public class DefaultSpeciesMetadataModule implements SpeciesMetadataModule {
    private PetComponent parent;
    private Identifier cachedSpeciesDescriptor;
    private PetComponent.FlightCapability cachedFlightCapability = PetComponent.FlightCapability.none();
    private boolean speciesDescriptorDirty = true;
    private boolean flightCapabilityDirty = true;

    @Override
    public void onAttach(PetComponent parent) {
        this.parent = parent;
    }

    @Override
    public Identifier getSpeciesDescriptor() {
        if (speciesDescriptorDirty || cachedSpeciesDescriptor == null) {
            cachedSpeciesDescriptor = parent.computeSpeciesDescriptor();
            speciesDescriptorDirty = false;
        }
        return cachedSpeciesDescriptor;
    }

    @Override
    public PetComponent.FlightCapability getFlightCapability() {
        if (flightCapabilityDirty) {
            cachedFlightCapability = parent.computeFlightCapability();
            flightCapabilityDirty = false;
        }
        return cachedFlightCapability;
    }

    @Override
    public void markSpeciesDirty() {
        this.speciesDescriptorDirty = true;
    }

    @Override
    public void markFlightDirty() {
        this.flightCapabilityDirty = true;
    }

    @Override
    public Data toData() {
        // Species metadata is transient - recomputed on load
        // We return current values for debugging but they won't be persisted
        return new Data(
            cachedSpeciesDescriptor,
            cachedFlightCapability
        );
    }

    @Override
    public void fromData(Data data) {
        // Species metadata is always recomputed on load
        // Mark everything dirty to force recomputation
        this.cachedSpeciesDescriptor = null;
        this.cachedFlightCapability = PetComponent.FlightCapability.none();
        this.speciesDescriptorDirty = true;
        this.flightCapabilityDirty = true;
    }
}
