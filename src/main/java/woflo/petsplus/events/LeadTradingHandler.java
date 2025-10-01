package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import woflo.petsplus.Petsplus;
import woflo.petsplus.handler.PetTradingHandler;

/**
 * Event handler for lead trading interactions.
 * Uses UseEntityCallback to detect when players use leads on other players while sneaking.
 */
public class LeadTradingHandler {
    
    /**
     * Registers the lead trading event handler.
     */
    public static void register() {
        // Register for use entity events to detect lead interactions with players
        // This follows the same pattern as other interaction handlers in the codebase
        UseEntityCallback.EVENT.register(LeadTradingHandler::onUseEntity);
        Petsplus.LOGGER.info("Lead trading handler registered");
    }
    
    /**
     * Handles use entity events for lead trading.
     * This method is called when a player right-clicks on an entity.
     * 
     * Key mechanic: When a lead is attached to a mob (mob is leashed), the lead item
     * is no longer in the player's hand - it's an active connection. So we detect
     * shift-right-click on another player and check if the clicking player has any
     * leashed mobs nearby (where mob.getLeashHolder() == player).
     * 
     * @param player The player using the item
     * @param world The world where the interaction occurs
     * @param hand The hand being used
     * @param entity The entity being targeted
     * @param hitResult The hit result from the interaction
     * @return ActionResult from the interaction
     */
    private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity, net.minecraft.util.hit.EntityHitResult hitResult) {
        // Early validation - only proceed on server side
        if (world.isClient()) {
            return ActionResult.PASS;
        }
        
        // Check if player is sneaking and targeting another player
        if (player.isSneaking() && entity instanceof PlayerEntity targetPlayer) {
            // When a player has leashed pets, they can trade them by shift-right-clicking another player
            // The lead is already attached to the mob, not in hand
            ActionResult result = PetTradingHandler.tryInitiateTrade(player, targetPlayer, hand);
            if (result != ActionResult.PASS) {
                return result;
            }
        }
        
        return ActionResult.PASS;
    }
}
