package woflo.petsplus.roles.skyrider;

import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.util.ChanceValidationUtil;

/**
 * Skyrider role implementation for air control features.
 *
 * Features:
 * - Projectile crit levitation: On owner projectile crit, chance to apply Levitation I (10-20 ticks)
 * - Windlash rider: On owner fall >3 blocks, apply jump boost and next attack knockup
 * - Skybond aura update: fall_reduction_near_owner gains apply_to_mount: true
 */
public class SkyriderWinds {

    public static final String PROJ_LEVITATION_LAST_TRIGGER_KEY = "skyrider_proj_levitation_last_trigger";
    public static final String WINDLASH_LAST_TRIGGER_KEY = "skyrider_windlash_last_trigger";

    /**
     * Get the projectile levitation chance from config.
     */
    public static double getProjLevitateChance() {
        return ChanceValidationUtil.getValidatedChance(
            PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.SKYRIDER.id(), "ownerProjLevitateChance", 0.10),
            "skyrider.ownerProjLevitateChance"
        );
    }
    
    /**
     * Get the projectile levitation internal cooldown ticks.
     */
    public static int getProjLevitateIcdTicks() {
        return PetsPlusConfig.getInstance().getRoleInt(PetRoleType.SKYRIDER.id(), "ownerProjLevitateIcdTicks", 200);
    }
    
    /**
     * Get the minimum fall distance for windlash trigger.
     */
    public static double getWindlashMinFallBlocks() {
        return 3.0;
    }
    
    /**
     * Get the windlash cooldown ticks.
     */
    public static int getWindlashCooldownTicks() {
        return 120; // 6 seconds
    }

    private SkyriderWinds() {}
}
