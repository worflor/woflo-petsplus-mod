package woflo.petsplus.handler;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import woflo.petsplus.Petsplus;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.pet.PetOwnershipTransfers;
import woflo.petsplus.pet.TradingContext;
import woflo.petsplus.pet.TransferResult;
import woflo.petsplus.ui.TradingFeedbackManager;

import java.util.List;

/**
 * Main handler for pet trading operations initiated by lead usage.
 * Orchestrates the trading process from initiation to completion.
 */
public class PetTradingHandler {
    
    /**
     * Registers the pet trading system.
     */
    public static void register() {
        // Register any necessary event callbacks or initialization
        Petsplus.LOGGER.info("Pet trading handler registered");
    }
    
    /**
     * Attempts to initiate a pet trade when a player shift-right-clicks another player
     * while having leashed mobs nearby.
     *
     * @param initiator The player initiating the trade
     * @param recipient The player being targeted
     * @param hand The hand used for the interaction
     * @return ActionResult indicating success, failure, or pass (to let vanilla handle)
     */
    public static ActionResult tryInitiateTrade(PlayerEntity initiator, PlayerEntity recipient, Hand hand) {
        // Null safety checks
        if (initiator == null || recipient == null || hand == null) {
            return ActionResult.PASS;
        }
        
        // Server-side validation
        if (!(initiator instanceof ServerPlayerEntity) || !(recipient instanceof ServerPlayerEntity)) {
            return ActionResult.PASS;
        }
        
        // Check if trading is enabled
        if (!PetsPlusConfig.getInstance().isLeashTradingEnabled()) {
            TradingFeedbackManager.sendGenericMessage((ServerPlayerEntity) initiator, "petsplus.trading.failed.disabled");
            return ActionResult.FAIL;
        }
        
        // Check if player is sneaking (if required)
        if (PetsPlusConfig.getInstance().isLeashTradingSneakRequired() && !initiator.isSneaking()) {
            TradingFeedbackManager.sendGenericMessage((ServerPlayerEntity) initiator, "petsplus.trading.require_sneak");
            return ActionResult.FAIL;
        }
        
        // Basic validation (distance and recipient validity)
        if (!isValidTrade(initiator, recipient)) {
            return ActionResult.PASS;
        }
        
        // Create trading context
        TradingContext context = new TradingContext(initiator, recipient, hand);
        
        // Find eligible pets (this looks for mobs where mob.getLeashHolder() == initiator)
        List<MobEntity> eligiblePets = context.findEligiblePets();
        if (eligiblePets.isEmpty()) {
            // Don't send message if no leashed pets - just pass through
            // This allows other interactions to work normally
            return ActionResult.PASS;
        }
        
        // Sort by distance to recipient
        eligiblePets.sort((a, b) -> Double.compare(
            a.squaredDistanceTo(recipient),
            b.squaredDistanceTo(recipient)
        ));
        
        // Limit to max eligible pets from config
        int maxPets = PetsPlusConfig.getInstance().getLeashTradingMaxEligiblePets();
        if (eligiblePets.size() > maxPets) {
            eligiblePets = eligiblePets.subList(0, maxPets);
        }
        
        // Attempt transfer with closest eligible pet
        MobEntity selectedPet = eligiblePets.get(0);
        TransferResult result = PetOwnershipTransfers.transfer(
            (ServerPlayerEntity) initiator,
            (ServerPlayerEntity) recipient,
            selectedPet
        );
        
        // Send feedback
        TradingFeedbackManager.sendTransferResult(
            (ServerPlayerEntity) initiator,
            (ServerPlayerEntity) recipient,
            result,
            selectedPet
        );
        
        return result == TransferResult.SUCCESS ? ActionResult.SUCCESS : ActionResult.PASS;
    }
    
    /**
     * Validates basic trade conditions.
     * 
     * @param initiator The player initiating the trade
     * @param recipient The player receiving the pet
     * @return true if the trade can proceed
     */
    private static boolean isValidTrade(PlayerEntity initiator, PlayerEntity recipient) {
        // Check distance from config
        double maxDistance = PetsPlusConfig.getInstance().getLeashTradingMaxDistance();
        if (initiator.squaredDistanceTo(recipient) > maxDistance * maxDistance) {
            TradingFeedbackManager.sendGenericMessage((ServerPlayerEntity) initiator, "petsplus.trading.failed.distance");
            return false;
        }
        
        // Check recipient validity
        if (recipient.isSpectator() || !recipient.isAlive()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Public API for other systems to transfer a specific pet.
     * Bypasses the automatic pet selection and transfers the specified pet.
     * 
     * @param initiator The player giving away the pet
     * @param recipient The player receiving the pet
     * @param pet The specific pet to transfer
     * @return TransferResult indicating success or failure reason
     */
    public static TransferResult transferSpecificPet(
        ServerPlayerEntity initiator,
        ServerPlayerEntity recipient,
        MobEntity pet
    ) {
        // Null safety checks
        if (initiator == null || recipient == null || pet == null) {
            return TransferResult.SERVER_ERROR;
        }
        
        return PetOwnershipTransfers.transfer(initiator, recipient, pet);
    }
}
