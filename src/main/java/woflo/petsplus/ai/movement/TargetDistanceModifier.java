package woflo.petsplus.ai.movement;

/**
 * Adjusts the preferred distance between the pet and its movement anchor.
 */
public record TargetDistanceModifier(double minimumDistance, double distanceOffset, double weight) {
    public TargetDistanceModifier {
        minimumDistance = Math.max(0.0, minimumDistance);
        weight = Math.max(0.0, weight);
    }
}
