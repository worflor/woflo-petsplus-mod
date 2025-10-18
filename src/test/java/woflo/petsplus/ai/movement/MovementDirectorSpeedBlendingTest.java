package woflo.petsplus.ai.movement;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import woflo.petsplus.state.PetComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementDirectorSpeedBlendingTest {

    private static final Identifier FIRST = Identifier.of("petsplus", "speed_first");
    private static final Identifier SECOND = Identifier.of("petsplus", "speed_second");

    @Test
    void speedModifiersResolveIndependentlyOfRegistrationOrder() {
        SpeedModifier fast = new SpeedModifier(2.0, 0.5, 0.1, 2.0);
        SpeedModifier cautious = new SpeedModifier(0.5, -0.1, 0.0, 1.5);

        PetComponent.MovementDirector director = new PetComponent.MovementDirector(null);
        director.setSpeedModifier(FIRST, fast);
        director.setSpeedModifier(SECOND, cautious);

        double baseSpeed = 0.6;
        double firstOrderSpeed = director.previewMovement(Vec3d.ZERO, 0.0, baseSpeed).speed();

        PetComponent.MovementDirector reversedDirector = new PetComponent.MovementDirector(null);
        reversedDirector.setSpeedModifier(SECOND, cautious);
        reversedDirector.setSpeedModifier(FIRST, fast);

        double secondOrderSpeed = reversedDirector.previewMovement(Vec3d.ZERO, 0.0, baseSpeed).speed();

        assertEquals(firstOrderSpeed, secondOrderSpeed, 1.0e-6);
        assertEquals(1.0, firstOrderSpeed, 1.0e-6);
    }

    @Test
    void hesitationSpeedModifierScalesWithWeight() {
        double baseSpeed = 0.6;
        double hesitationBoost = 0.2;

        PetComponent.MovementDirector halfWeightDirector = new PetComponent.MovementDirector(null);
        double halfWeight = 0.5;
        halfWeightDirector.setSpeedModifier(FIRST, new SpeedModifier(1.0, Math.max(0.0, hesitationBoost * halfWeight), 0.0, Double.POSITIVE_INFINITY));

        double halfWeightSpeed = halfWeightDirector.previewMovement(Vec3d.ZERO, 0.0, baseSpeed).speed();

        PetComponent.MovementDirector fullWeightDirector = new PetComponent.MovementDirector(null);
        double fullWeight = 1.0;
        fullWeightDirector.setSpeedModifier(FIRST, new SpeedModifier(1.0, Math.max(0.0, hesitationBoost * fullWeight), 0.0, Double.POSITIVE_INFINITY));

        double fullWeightSpeed = fullWeightDirector.previewMovement(Vec3d.ZERO, 0.0, baseSpeed).speed();

        assertTrue(fullWeightSpeed > halfWeightSpeed);
        assertEquals(baseSpeed + hesitationBoost * halfWeight, halfWeightSpeed, 1.0e-6);
        assertEquals(baseSpeed + hesitationBoost * fullWeight, fullWeightSpeed, 1.0e-6);
    }
}
