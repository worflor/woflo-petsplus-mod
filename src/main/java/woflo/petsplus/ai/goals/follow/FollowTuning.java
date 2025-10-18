package woflo.petsplus.ai.goals.follow;

import net.minecraft.util.Identifier;
import woflo.petsplus.state.PetComponent;

/**
 * Shared heuristics for follow-style behaviors.
 */
public final class FollowTuning {
    private FollowTuning() {
    }

    public static final float DEFAULT_BASELINE_DISTANCE = 4.2f;
    public static final float DEFAULT_TELEPORT_DISTANCE = 16.0f;

    public static float resolveFollowDistance(PetComponent component) {
        Identifier roleId = component.getRoleId();
        String role = roleId != null ? roleId.getPath() : "";
        return switch (role) {
            case "guardian" -> 3.8f;
            case "scout" -> 5.5f;
            case "support" -> 3.2f;
            default -> DEFAULT_BASELINE_DISTANCE;
        };
    }

    public static float resolveTeleportDistance(PetComponent component) {
        Identifier roleId = component.getRoleId();
        String role = roleId != null ? roleId.getPath() : "";
        return switch (role) {
            case "scout" -> 18.0f;
            case "support" -> 14.0f;
            default -> DEFAULT_TELEPORT_DISTANCE;
        };
    }

    public static boolean isScoutRole(PetComponent component) {
        Identifier roleId = component.getRoleId();
        return roleId != null && "scout".equals(roleId.getPath());
    }
}
