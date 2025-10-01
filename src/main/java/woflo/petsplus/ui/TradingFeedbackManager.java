package woflo.petsplus.ui;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import woflo.petsplus.pet.TransferResult;

/**
 * Handles UI feedback for pet trading operations including chat messages
 * and action bar notifications for both initiator and recipient.
 */
public class TradingFeedbackManager {
    
    /**
     * Sends appropriate feedback messages to both players based on the transfer result.
     * 
     * @param initiator The player who initiated the trade
     * @param recipient The player who was to receive the pet
     * @param result The result of the transfer operation
     * @param pet The pet involved in the transfer (may be null for some failures)
     */
    public static void sendTransferResult(
        ServerPlayerEntity initiator,
        ServerPlayerEntity recipient,
        TransferResult result,
        MobEntity pet
    ) {
        String petName = pet != null ? pet.getDisplayName().getString() : "Unknown";
        
        switch (result) {
            case SUCCESS:
                sendSuccessMessage(initiator, recipient, petName);
                break;
            case NOT_OWNED:
                sendErrorMessage(initiator, "petsplus.trading.not_owned", petName);
                break;
            case NOT_LEASHED:
                sendErrorMessage(initiator, "petsplus.trading.not_leashed", petName);
                break;
            case OUT_OF_RANGE:
                sendErrorMessage(initiator, "petsplus.trading.out_of_range");
                break;
            case RECIPIENT_INVALID:
                sendErrorMessage(initiator, "petsplus.trading.recipient_invalid");
                break;
            case INCOMPATIBLE:
                sendErrorMessage(initiator, "petsplus.trading.incompatible", petName);
                break;
            case NO_ELIGIBLE_PETS:
                sendErrorMessage(initiator, "petsplus.trading.no_eligible_pets");
                break;
            case SERVER_ERROR:
                sendErrorMessage(initiator, "petsplus.trading.server_error");
                break;
        }
    }
    
    /**
     * Sends a message when no eligible pets are found for trading.
     * 
     * @param player The player to receive the message
     */
    public static void sendNoEligiblePetsMessage(ServerPlayerEntity player) {
        sendErrorMessage(player, "petsplus.trading.no_eligible_pets");
    }
    
    /**
     * Sends success messages to both initiator and recipient.
     * 
     * @param initiator The player who gave the pet
     * @param recipient The player who received the pet
     * @param petName The name of the pet that was transferred
     */
    private static void sendSuccessMessage(ServerPlayerEntity initiator, ServerPlayerEntity recipient, String petName) {
        // Send to initiator
        UIFeedbackManager.sendChatMessage(initiator, "petsplus.trading.success.initiator", petName, recipient.getName().getString());
        
        // Send to recipient
        UIFeedbackManager.sendChatMessage(recipient, "petsplus.trading.success.recipient", petName, initiator.getName().getString());
        
        // Send action bar to both
        UIFeedbackManager.sendActionBarMessage(initiator, "petsplus.trading.success.action_bar", petName);
        UIFeedbackManager.sendActionBarMessage(recipient, "petsplus.trading.success.action_bar", petName);
    }
    
    /**
     * Sends an error message to a player via both chat and action bar.
     * 
     * @param player The player to receive the error message
     * @param translationKey The translation key for the error message
     * @param args Optional arguments for the translation
     */
    private static void sendErrorMessage(ServerPlayerEntity player, String translationKey, Object... args) {
        UIFeedbackManager.sendChatMessage(player, translationKey, args);
        UIFeedbackManager.sendActionBarMessage(player, translationKey, args);
    }
    
    /**
     * Sends a generic message to a player using a localization key.
     */
    public static void sendGenericMessage(ServerPlayerEntity player, String key) {
        UIFeedbackManager.sendChatMessage(player, key);
    }
}
