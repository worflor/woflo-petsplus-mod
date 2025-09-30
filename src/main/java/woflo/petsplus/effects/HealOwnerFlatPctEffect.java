package woflo.petsplus.effects;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;

/**
 * Effect that heals the owner by combining a flat amount with a percentage of their maximum health.
 */
public class HealOwnerFlatPctEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "heal_owner_flat_pct");

    private final double flatAmount;
    private final double healPercent;

    public HealOwnerFlatPctEffect(double flatAmount, double healPercent) {
        this.flatAmount = flatAmount;
        this.healPercent = healPercent;
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();

        if (owner == null || !owner.isAlive() || owner.isRemoved()) {
            return false;
        }

        float healAmount = calculateHealAmount(owner.getMaxHealth());

        owner.heal(healAmount);

        return true;
    }

    @Override
    public int getDurationTicks() {
        return 0; // Instant effect
    }

    float calculateHealAmount(float maxHealth) {
        return (float) (flatAmount + (maxHealth * healPercent));
    }
}
