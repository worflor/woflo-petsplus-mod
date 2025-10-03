package woflo.petsplus.api.rewards;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.LevelReward;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.state.PetComponent;

/**
 * Unlocks a specific ability for the pet at a given level.
 * 
 * <h2>JSON Format:</h2>
 * <pre>{@code
 * {
 *   "type": "ability_unlock",
 *   "ability": "petsplus:shield_bash"
 * }
 * }</pre>
 * 
 * <h2>Behavior:</h2>
 * <ul>
 *   <li>Calls {@link PetComponent#unlockAbility(Identifier)} to mark the ability as available</li>
 *   <li>Ability tracking is persistent via NBT</li>
 *   <li>Multiple abilities can be unlocked at the same level</li>
 *   <li>Unlocking does not automatically add to loadout - that's handled separately</li>
 * </ul>
 * 
 * @see PetComponent#unlockAbility(Identifier)
 * @see PetComponent#isAbilityUnlocked(Identifier)
 */
public final class AbilityUnlockReward implements LevelReward {
    private final Identifier abilityId;
    
    public AbilityUnlockReward(Identifier abilityId) {
        this.abilityId = abilityId;
    }
    
    @Override
    public void apply(MobEntity pet, PetComponent comp, ServerPlayerEntity owner, int level) {
        if (abilityId == null) {
            Petsplus.LOGGER.error("AbilityUnlockReward has null abilityId for pet {} at level {}",
                pet.getDisplayName().getString(), level);
            return;
        }
        
        // Validate ability exists in registry
        if (!PetsPlusRegistries.abilityTypeRegistry().containsId(abilityId)) {
            Petsplus.LOGGER.error("Attempted to unlock non-existent ability '{}' for pet {} at level {}. " +
                "Ability may not be registered or datapack is missing.",
                abilityId, pet.getDisplayName().getString(), level);
            return;
        }
        
        // Mark the ability as unlocked at this level
        comp.unlockAbility(abilityId);
        
        Petsplus.LOGGER.debug("Pet {} unlocked ability {} at level {}", 
            pet.getDisplayName().getString(), abilityId, level);
    }
    
    @Override
    public String getType() {
        return "ability_unlock";
    }
    
    @Override
    public String getDescription() {
        return "Unlock ability: " + abilityId;
    }
    
    public Identifier getAbilityId() {
        return abilityId;
    }
}
