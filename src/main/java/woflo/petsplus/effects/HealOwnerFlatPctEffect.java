package woflo.petsplus.effects;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;

/**
 * Effect that heals the owner by a flat percentage of their max health.
 */
public class HealOwnerFlatPctEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "heal_owner_flat_pct");
    
    private final double healPercent;
    
    public HealOwnerFlatPctEffect(double healPercent) {
        this.healPercent = healPercent;
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();
        
        float maxHealth = owner.getMaxHealth();
        float healAmount = (float) (maxHealth * healPercent);
        
        owner.heal(healAmount);
        
        return true;
    }
    
    @Override
    public int getDurationTicks() {
        return 0; // Instant effect
    }
}