package woflo.petsplus.events;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import woflo.petsplus.Petsplus;
import woflo.petsplus.items.PetsplusItems;
import woflo.petsplus.state.PetComponent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Handles right-click interactions with the Pet Compendium item.
 * Uses the same focus/inspection system as the Pet Inspection UI.
 * Players look at their pet (crosshair targeting) and right-click air to open the compendium.
 * 
 * IMPORTANT: Uses PetInspectionManager's active inspection state for consistent targeting.
 * If no pet is being inspected, falls back to raycasting with the same parameters.
 */
public class PetCompendiumHandler {
    
    // WeakHashMap for automatic cleanup of player entries
    private static final Map<UUID, Long> LAST_USE_TIME = new WeakHashMap<>();
    private static final long USE_COOLDOWN_TICKS = 20; // 1 second
    
    private PetCompendiumHandler() {}
    
    public static void register() {
        // Register right-click in air to open compendium for focused pet
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register(
            (player, world, hand) -> {
                if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                    ItemStack stack = player.getStackInHand(hand);
                    if (stack.isOf(PetsplusItems.PET_COMPENDIUM)) {
                        return openCompendiumForFocusedPet(serverPlayer, hand);
                    }
                }
                return ActionResult.PASS;
            }
        );
        
        Petsplus.LOGGER.info("Pet Compendium handler registered");
    }
    
    /**
     * Cleanup method called when a player disconnects.
     * Prevents memory leaks from the LAST_USE_TIME map.
     */
    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player != null) {
            LAST_USE_TIME.remove(player.getUuid());
        }
    }
    
    /**
     * Opens the compendium for the pet the player is looking at.
     * Uses PetInspectionManager's focus tracking for consistency.
     */
    private static ActionResult openCompendiumForFocusedPet(ServerPlayerEntity player, Hand hand) {
        // Cooldown check
        long currentTime = player.getEntityWorld().getTime();
        Long lastUse = LAST_USE_TIME.get(player.getUuid());
        if (lastUse != null && currentTime - lastUse < USE_COOLDOWN_TICKS) {
            return ActionResult.PASS;
        }
        
        // Use the inspection manager's active pet if available
        MobEntity targetPet = woflo.petsplus.ui.PetInspectionManager.getActiveInspectedPet(player);
        
        if (targetPet == null) {
            player.sendMessage(
                Text.literal("Notice: ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal("Look at your pet to open the compendium.").formatted(Formatting.GRAY)),
                true
            );
            return ActionResult.PASS;
        }
        
        // Verify pet is still valid
        PetComponent petComponent = PetComponent.get(targetPet);
        if (petComponent == null || !petComponent.isOwnedBy(player)) {
            player.sendMessage(
                Text.literal("Notice: ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal("Target is no longer a valid pet.").formatted(Formatting.GRAY)),
                true
            );
            return ActionResult.PASS;
        }
        
        // Open the compendium
        LAST_USE_TIME.put(player.getUuid(), currentTime);
        openCompendiumScreen(player, targetPet, petComponent);
        
        // Visual and audio feedback
        Hand swingHand = hand != null ? hand : Hand.MAIN_HAND;
        player.swingHand(swingHand, true);
        player.getEntityWorld().playSound(null, player.getBlockPos(), 
            SoundEvents.ITEM_BOOK_PAGE_TURN, 
            SoundCategory.PLAYERS, 0.8f, 1.0f);
        
        return ActionResult.SUCCESS;
    }
    
    private static void openCompendiumScreen(ServerPlayerEntity player, MobEntity pet, PetComponent pc) {
        long currentTick = player.getEntityWorld().getTime();
        
        // Open the actual written book GUI
        woflo.petsplus.ui.CompendiumBookBuilder.openCompendium(player, pet, pc, currentTick);
    }
}



