package woflo.petsplus.effects;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.state.PetComponent;

/**
 * Sets temporary state data on the pet with an expiration time.
 * Uses the same pattern as OwnerCombatState.tempState.
 */
public class SetPetStateDataEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "set_pet_state_data");
    
    private final String key;
    private final int durationTicks;
    
    public SetPetStateDataEffect(String key, int durationTicks) {
        this.key = key;
        this.durationTicks = durationTicks;
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
        
        if (!(context.getWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        
        // Store expiry time (like OwnerCombatState.tempState)
        long expiryTick = serverWorld.getTime() + durationTicks;
        petComp.setStateData(key, expiryTick);
        
        return true;
    }
}
