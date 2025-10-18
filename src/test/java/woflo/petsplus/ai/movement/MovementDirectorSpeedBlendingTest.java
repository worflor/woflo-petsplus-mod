package woflo.petsplus.ai.movement;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import woflo.petsplus.state.PetComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
