package woflo.petsplus.effects;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.state.PetComponent;

/**
 * Clears temporary state data from the pet.
 */
public class ClearPetStateDataEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "clear_pet_state_data");
    
    private final String key;
    
    public ClearPetStateDataEffect(String key) {
        this.key = key;
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getPet() instanceof MobEntity mobPet)) {
            return false;
        }
        
        PetComponent petComp = PetComponent.get(mobPet);
        if (petComp == null) {
            return false;
        }
        
        petComp.clearStateData(key);
        return true;
    }
}
