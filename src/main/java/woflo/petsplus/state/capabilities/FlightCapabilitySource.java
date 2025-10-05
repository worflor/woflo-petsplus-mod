package woflo.petsplus.state.capabilities;

public enum FlightCapabilitySource {
    NONE(false),
    SPECIES_TAG(true),
    STATE_METADATA(true),
    SPECIES_KEYWORD(true),
    ROLE_OVERRIDE(false);

    public final boolean isMetadataDerived;

    FlightCapabilitySource(boolean metadataDerived) {
        this.isMetadataDerived = metadataDerived;
    }

    public boolean isMetadataDerived() {
        return isMetadataDerived;
    }
}
