package woflo.petsplus.roles.support;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utilities for working with potion items for the Support role.
 */
public final class SupportPotionUtils {
    private SupportPotionUtils() {}

    /**
     * Extract potion effects from the given ItemStack suitable for an aura pulse.
     * Returns duration-adjusted copies based on original potion duration + pet level modifiers.
     */
    public static List<StatusEffectInstance> getAuraEffects(ItemStack stack, int petLevel) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return Collections.emptyList();

        List<StatusEffectInstance> out = new ArrayList<>();
        for (StatusEffectInstance inst : contents.getEffects()) {
            if (inst == null || inst.getEffectType() == null) continue;

            // Calculate duration: base potion duration + level-based modifier
            int baseDuration = inst.getDuration();
            int levelBonus = petLevel * 20; // 1 second (20 ticks) per pet level
            int finalDuration = Math.max(60, baseDuration + levelBonus); // Minimum 3 seconds

            // Recreate with calculated duration, preserve amplifier
            out.add(new StatusEffectInstance(inst.getEffectType(), finalDuration, inst.getAmplifier(), false, true, true));
        }
        return out;
    }

    /**
     * Get the effective aura duration for storing in component state.
     * This should be the pulse duration that gets applied each tick.
     */
    public static int getAuraPulseDuration(int basePotionDuration, int petLevel) {
        int levelBonus = petLevel * 10; // 0.5 seconds (10 ticks) per level for aura pulses
        return Math.max(40, Math.min(basePotionDuration / 3, 80) + levelBonus); // Between 2-4+ seconds
    }
}
