package woflo.petsplus.ai.goals.follow;

import net.minecraft.util.Identifier;
import woflo.petsplus.state.PetComponent;

/**
 * Shared heuristics for follow-style behaviors.
 */
public final class FollowTuning {
    private FollowTuning() {
    }

    public static float resolveFollowDistance(PetComponent component) {
        Identifier roleId = component.getRoleId();
        String role = roleId != null ? roleId.getPath() : "";
        return switch (role) {
            case "guardian" -> 5.5f;
            case "scout" -> 9.0f;
            case "support" -> 4.5f;
            default -> 6.0f;
        };
    }

    public static float resolveTeleportDistance(PetComponent component) {
        Identifier roleId = component.getRoleId();
        String role = roleId != null ? roleId.getPath() : "";
        return switch (role) {
            case "scout" -> 16.0f;
            case "support" -> 9.5f;
            default -> 12.0f;
        };
    }

    public static boolean isScoutRole(PetComponent component) {
        Identifier roleId = component.getRoleId();
        return roleId != null && "scout".equals(roleId.getPath());
    }
}
