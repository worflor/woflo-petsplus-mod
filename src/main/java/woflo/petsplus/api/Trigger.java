package woflo.petsplus.api;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Base interface for all ability triggers in the PetsPlus system.
 * Triggers determine when abilities should activate based on game events.
 */
public interface Trigger {
    /**
     * Gets the unique identifier for this trigger type.
     * @return The trigger identifier
     */
    Identifier getId();
    
    /**
     * Checks if this trigger should activate given the current context.
     * @param context The trigger context containing event data
     * @return true if the trigger should activate
     */
    boolean shouldActivate(TriggerContext context);
    
    /**
     * Gets the internal cooldown for this trigger in ticks.
     * @return Cooldown in ticks, or 0 if no cooldown
     */
    default int getInternalCooldownTicks() {
        return 0;
    }
}