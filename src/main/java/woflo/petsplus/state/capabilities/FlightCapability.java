package woflo.petsplus.state.capabilities;

public record FlightCapability(boolean canFly, FlightCapabilitySource source) {
    private static final FlightCapability NONE = new FlightCapability(false, FlightCapabilitySource.NONE);
    private static final FlightCapability SPECIES_TAG = new FlightCapability(true, FlightCapabilitySource.SPECIES_TAG);
    private static final FlightCapability STATE_METADATA = new FlightCapability(true, FlightCapabilitySource.STATE_METADATA);
    private static final FlightCapability SPECIES_KEYWORD = new FlightCapability(true, FlightCapabilitySource.SPECIES_KEYWORD);
    private static final FlightCapability ROLE_OVERRIDE = new FlightCapability(true, FlightCapabilitySource.ROLE_OVERRIDE);

    public static FlightCapability none() {
        return NONE;
    }

    public static FlightCapability fromSource(FlightCapabilitySource source) {
        return switch (source) {
            case NONE -> NONE;
            case SPECIES_TAG -> SPECIES_TAG;
            case STATE_METADATA -> STATE_METADATA;
            case SPECIES_KEYWORD -> SPECIES_KEYWORD;
            case ROLE_OVERRIDE -> ROLE_OVERRIDE;
        };
    }
}
