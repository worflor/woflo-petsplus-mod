package woflo.petsplus.ai.movement;

/**
 * Alters the resolved movement speed.
 */
public record SpeedModifier(double multiplier, double additive, double minSpeed, double maxSpeed) {
    public SpeedModifier {
        multiplier = multiplier <= 0.0 ? 1.0 : multiplier;
        minSpeed = Math.max(0.0, minSpeed);
        maxSpeed = maxSpeed > 0.0 ? maxSpeed : Double.POSITIVE_INFINITY;
    }
}
