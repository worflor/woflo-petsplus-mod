package woflo.petsplus.pet;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.Petsplus;
import woflo.petsplus.advancement.AdvancementCriteriaRegistry;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;

/**
 * Handles the core logic of transferring pet ownership between players.
 * Provides validation, state management, and proper cleanup during transfers.
 */
public class PetOwnershipTransfers {
    
    /**
     * Transfers ownership of a pet from one player to another.
     * Preserves all pet data including history, stats, role, and emotions.
     * 
     * @param initiator The player giving away the pet
     * @param recipient The player receiving the pet
     * @param pet The pet being transferred
     * @return TransferResult indicating success or failure reason
     */
    public static TransferResult transfer(
        ServerPlayerEntity initiator,
        ServerPlayerEntity recipient,
        MobEntity pet
    ) {
        // Null safety checks
        if (initiator == null || recipient == null || pet == null) {
            return TransferResult.SERVER_ERROR;
        }
        
        try {
            // Validation
            TransferResult validation = validateTransfer(initiator, recipient, pet);
            if (validation != TransferResult.SUCCESS) {
                return validation;
            }
            
            // Get components
            PetComponent component = PetComponent.get(pet);
            PetsplusTameable tameable = (PetsplusTameable) pet;
            
            // Additional safety checks for components
            if (component == null) {
                Petsplus.LOGGER.error("PetComponent is null for pet: " + pet.getType().getTranslationKey());
                return TransferResult.INCOMPATIBLE;
            }
            
            // Detach leash
            if (pet.isLeashed()) {
                pet.detachLeash();
            }
            
            // Transfer ownership with null checks
            if (tameable != null) {
                tameable.petsplus$setOwner(recipient);
                tameable.petsplus$setOwnerUuid(recipient.getUuid());
            }
            if (component != null) {
                component.setOwner(recipient);
                component.setOwnerUuid(recipient.getUuid());
            }
            
            // Refresh component state
            component.ensureImprint();
            
            // Reattach leash to recipient
            pet.attachLeash(recipient, true);
            
            // Update state manager
            StateManager stateManager = StateManager.forWorld((ServerWorld) pet.getWorld());
            if (stateManager != null) {
                stateManager.getPetComponent(pet);
                // Note: invalidatePetSwarmCache is a static method in CombatEventHandler
                woflo.petsplus.events.CombatEventHandler.invalidatePetSwarmCache(initiator.getUuid());
                woflo.petsplus.events.CombatEventHandler.invalidatePetSwarmCache(recipient.getUuid());
            }
            
            // Handle lead transfer to recipient
            giveLeadToRecipient(initiator, recipient);
            
            // Apply mood impact if enabled
            if (PetsPlusConfig.getInstance().isLeashTradingMoodImpactEnabled()) {
                applyMoodImpact(component, initiator, recipient);
            }
            
            // Record trade event in history
            woflo.petsplus.history.HistoryManager.recordTradeEvent(pet, initiator, recipient, "leash");
            
            // Trigger advancement criteria
            triggerAdvancementCriteria(initiator, recipient, pet);
            
            return TransferResult.SUCCESS;
            
        } catch (Exception e) {
            Petsplus.LOGGER.error("Error during pet transfer", e);
            return TransferResult.SERVER_ERROR;
        }
    }
    
    /**
     * Validates that a transfer can proceed.
     */
    private static TransferResult validateTransfer(
        ServerPlayerEntity initiator,
        ServerPlayerEntity recipient,
        MobEntity pet
    ) {
        // Check pet validity
        if (pet == null || !pet.isAlive()) {
            return TransferResult.INCOMPATIBLE;
        }
        
        // Check if pet implements PetsplusTameable
        if (!(pet instanceof PetsplusTameable)) {
            return TransferResult.INCOMPATIBLE;
        }
        
        // Get component
        PetComponent component = PetComponent.get(pet);
        if (component == null) {
            return TransferResult.INCOMPATIBLE;
        }
        
        // Check ownership
        if (!component.isOwnedBy(initiator)) {
            return TransferResult.NOT_OWNED;
        }
        
        // Check leash state
        if (!pet.isLeashed() || pet.getLeashHolder() != initiator) {
            return TransferResult.NOT_LEASHED;
        }
        
        // Check recipient validity
        if (!recipient.isAlive() || recipient.isSpectator()) {
            return TransferResult.RECIPIENT_INVALID;
        }
        
        return TransferResult.SUCCESS;
    }
    
    /**
     * Gives a lead to the recipient after a pet trade.
     * The lead was previously attached to the mob being traded.
     * The pet is automatically re-leashed to the recipient, and we give them
     * a lead item so they can detach/reattach as needed.
     */
    private static void giveLeadToRecipient(
        ServerPlayerEntity initiator,
        ServerPlayerEntity recipient
    ) {
        // Null safety checks
        if (initiator == null || recipient == null) {
            return;
        }
        
        // Give a lead to the recipient (they now control the pet)
        if (!recipient.isCreative()) {
            ItemStack newLead = new ItemStack(net.minecraft.item.Items.LEAD);
            if (!recipient.getInventory().insertStack(newLead)) {
                // If inventory is full, drop it at their feet
                recipient.dropItem(newLead, false);
            }
        }
    }
    
    /**
     * Applies mood impact to the pet based on the ownership transfer.
     */
    private static void applyMoodImpact(PetComponent component, ServerPlayerEntity initiator, ServerPlayerEntity recipient) {
        // Null safety check
        if (component == null) {
            return;
        }
        
        try {
            // Add emotions based on the transfer
            // Pet feels curious about new owner
            component.pushEmotion(PetComponent.Emotion.CURIOUS, 0.5f);
            
            // Pet feels a bit sad leaving previous owner
            component.pushEmotion(PetComponent.Emotion.MELANCHOLY, 0.3f);
            
            // Pet feels hopeful about new relationship
            component.pushEmotion(PetComponent.Emotion.HOPEFUL, 0.4f);
            
            // Update mood
            component.updateMood();
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to apply mood impact during pet transfer", e);
        }
    }
    
    /**
     * Triggers advancement criteria for both players involved in the trade.
     */
    private static void triggerAdvancementCriteria(ServerPlayerEntity initiator, ServerPlayerEntity recipient, MobEntity pet) {
        // Null safety checks
        if (initiator == null || recipient == null || pet == null) {
            return;
        }
        
        try {
            EntityType<?> petType = pet.getType();
            
            // Get or initialize trade counts for both players
            // In a full implementation, these would be tracked in player data components
            // For now, we'll trigger with count 1 (can be enhanced later with proper stat tracking)
            int initiatorCount = 1; // Could be enhanced with persistent storage
            int recipientCount = 1; // Could be enhanced with persistent storage
            
            // Trigger advancement for initiator (giving away pet)
            AdvancementCriteriaRegistry.PET_TRADING.triggerInitiator(initiator, petType, initiatorCount);
            
            // Trigger advancement for recipient (receiving pet)
            AdvancementCriteriaRegistry.PET_TRADING.triggerRecipient(recipient, petType, recipientCount);
        } catch (Exception e) {
            Petsplus.LOGGER.warn("Failed to trigger advancement criteria during pet transfer", e);
        }
    }
}
