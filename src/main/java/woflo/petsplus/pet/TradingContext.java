package woflo.petsplus.pet;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.state.PetComponent;

import java.util.List;

/**
 * Context object that encapsulates all information needed for a pet trading operation.
 * Provides utility methods for finding eligible pets and validating trade conditions.
 */
public class TradingContext {
    private final PlayerEntity initiator;
    private final PlayerEntity recipient;
    private final Hand hand;
    private final Vec3d tradeCenter;
    private final Box searchBox;
    
    public TradingContext(PlayerEntity initiator, PlayerEntity recipient, Hand hand) {
        // Null safety checks
        if (initiator == null || recipient == null || hand == null) {
            throw new IllegalArgumentException("TradingContext parameters cannot be null");
        }
        
        this.initiator = initiator;
        this.recipient = recipient;
        this.hand = hand;
        this.tradeCenter = recipient.getEntityPos();
        this.searchBox = createSearchBox(tradeCenter);
    }
    
    /**
     * Creates a search box around the trade center for finding eligible pets.
     * 10 block radius horizontally, 3 blocks vertically.
     */
    private Box createSearchBox(Vec3d center) {
        return new Box(
            center.x - 10, center.y - 3, center.z - 10,
            center.x + 10, center.y + 3, center.z + 10
        );
    }
    
    /**
     * Finds all eligible pets that can be traded from the initiator to the recipient.
     * A pet is eligible if:
     * - It is owned by the initiator
     * - It is leashed to the initiator
     * - It implements PetsplusTameable
     * - It has a PetComponent
     */
    public List<MobEntity> findEligiblePets() {
        return initiator.getEntityWorld().getEntitiesByClass(
            MobEntity.class,
            searchBox,
            this::isEligiblePet
        );
    }
    
    /**
     * Checks if a pet is eligible for trading.
     */
    private boolean isEligiblePet(MobEntity pet) {
        // Null safety check
        if (pet == null) {
            return false;
        }
        
        // Check if pet is alive
        if (!pet.isAlive()) {
            return false;
        }
        
        // Check if pet implements PetsplusTameable
        if (!(pet instanceof PetsplusTameable)) {
            return false;
        }
        
        // Check if pet has PetComponent
        PetComponent component = PetComponent.get(pet);
        if (component == null) {
            return false;
        }
        
        // Check if pet is owned by initiator
        if (!component.isOwnedBy(initiator)) {
            return false;
        }
        
        // Check if pet is leashed to initiator
        if (!pet.isLeashed() || pet.getLeashHolder() != initiator) {
            return false;
        }
        
        return true;
    }
    
    // Getters
    public PlayerEntity getInitiator() { 
        return initiator; 
    }
    
    public PlayerEntity getRecipient() { 
        return recipient; 
    }
    
    public Hand getHand() { 
        return hand; 
    }
    
    public Vec3d getTradeCenter() { 
        return tradeCenter; 
    }
    
    public Box getSearchBox() { 
        return searchBox; 
    }
}


