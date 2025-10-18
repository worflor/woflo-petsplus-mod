package woflo.petsplus.ai.movement;

import net.minecraft.util.math.Vec3d;

/**
 * Resolved movement command emitted by the {@link woflo.petsplus.state.PetComponent.MovementDirector}.
 */
public record MovementCommand(Vec3d targetPosition, double desiredDistance, double speed) {
    public static MovementCommand of(Vec3d targetPosition, double desiredDistance, double speed) {
        return new MovementCommand(targetPosition, desiredDistance, speed);
    }
}
