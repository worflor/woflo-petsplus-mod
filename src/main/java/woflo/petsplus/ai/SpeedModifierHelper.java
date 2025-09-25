package woflo.petsplus.ai;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

/**
 * Utility helpers for applying and clearing movement speed multipliers via attribute modifiers.
 */
public final class SpeedModifierHelper {
    private static final double SPEED_EPSILON = 1.0e-4;

    private SpeedModifierHelper() {
    }

    /**
     * Applies or updates a multiplicative movement speed modifier for the provided entity.
     * When the multiplier is effectively 1, any existing modifier with the supplied id is cleared.
     */
    public static void applyMovementSpeedMultiplier(MobEntity pet, Identifier modifierId, double multiplier) {
        EntityAttributeInstance speedAttribute = pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speedAttribute == null) {
            return;
        }

        if (Math.abs(multiplier - 1.0) < SPEED_EPSILON) {
            speedAttribute.removeModifier(modifierId);
            return;
        }

        EntityAttributeModifier existing = speedAttribute.getModifier(modifierId);
        if (existing != null) {
            double currentMultiplier = existing.value() + 1.0;
            if (Math.abs(currentMultiplier - multiplier) < SPEED_EPSILON) {
                return;
            }
        }

        double amount = multiplier - 1.0;
        EntityAttributeModifier modifier = new EntityAttributeModifier(modifierId, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        speedAttribute.removeModifier(modifierId);
        speedAttribute.addTemporaryModifier(modifier);
    }

    /**
     * Clears any speed modifier with the supplied id if present.
     */
    public static void clearMovementSpeedModifier(MobEntity pet, Identifier modifierId) {
        EntityAttributeInstance speedAttribute = pet.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speedAttribute == null) {
            return;
        }
        speedAttribute.removeModifier(modifierId);
    }
}
