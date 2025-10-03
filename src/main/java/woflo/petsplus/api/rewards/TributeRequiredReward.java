package woflo.petsplus.api.rewards;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.LevelReward;
import woflo.petsplus.state.PetComponent;

/**
 * Marks a level as requiring tribute payment before the pet can continue progressing.
 * 
 * <h2>JSON Format:</h2>
 * <pre>{@code
 * {
 *   "type": "tribute_required",
 *   "item": "minecraft:gold_ingot"
 * }
 * }</pre>
 * 
 * <h2>Behavior:</h2>
 * <ul>
 *   <li>Pet cannot gain XP at this level until tribute is paid</li>
 *   <li>Owner must sneak + right-click pet with the specified item</li>
 *   <li>Item is consumed on successful tribute payment</li>
 *   <li>Integrates with existing milestone/advancement system</li>
 *   <li>Overrides legacy {@code tribute_defaults} if both are present</li>
 * </ul>
 * 
 * <h2>Integration:</h2>
 * <p>
 * The {@link woflo.petsplus.events.TributeHandler} checks for tribute requirements in this order:
 * <ol>
 *   <li>Level rewards ({@link PetComponent#hasTributeMilestone(int)})</li>
 *   <li>Legacy tribute_defaults (fallback)</li>
 * </ol>
 * </p>
 * 
 * @see PetComponent#setTributeMilestone(int, Identifier)
 * @see PetComponent#getTributeMilestone(int)
 * @see PetComponent#hasTributeMilestone(int)
 * @see woflo.petsplus.events.TributeHandler
 */
public final class TributeRequiredReward implements LevelReward {
    private final Identifier itemId;
    
    public TributeRequiredReward(Identifier itemId) {
        this.itemId = itemId;
    }
    
    @Override
    public void apply(MobEntity pet, PetComponent comp, ServerPlayerEntity owner, int level) {
        if (itemId == null) {
            Petsplus.LOGGER.error("TributeRequiredReward has null itemId for pet {} at level {}",
                pet.getDisplayName().getString(), level);
            return;
        }
        
        comp.setTributeMilestone(level, itemId);
        
        Petsplus.LOGGER.debug("Pet {} requires tribute {} at level {}", 
            pet.getDisplayName().getString(), itemId, level);
    }
    
    @Override
    public String getType() {
        return "tribute_required";
    }
    
    @Override
    public String getDescription() {
        return "Tribute required: " + itemId;
    }
    
    public Identifier getItemId() {
        return itemId;
    }
}
