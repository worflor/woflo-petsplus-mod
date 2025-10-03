package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.RegistryJsonHelper;

/**
 * Applies a damage multiplier to Eclipsed pet attacks when striking in darkness.
 * Simple effect that checks the in_darkness flag and scales damage accordingly.
 */
public class EclipsedVoidStrikeEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "eclipsed_void_strike");

    private final double multiplier;

    public EclipsedVoidStrikeEffect(JsonObject json) {
        this.multiplier = Math.max(1.0, RegistryJsonHelper.getDouble(json, "multiplier", 1.5));
    }

    public EclipsedVoidStrikeEffect() {
        this.multiplier = 1.5;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        // Must have damage context to modify damage
        if (!context.hasDamageContext()) {
            return false;
        }

        // Check if we're in darkness
        Boolean inDarkness = context.getData("in_darkness", Boolean.class);
        if (inDarkness == null || !inDarkness) {
            return false; // Only apply bonus in darkness
        }

        // Get the damage interception result
        DamageInterceptionResult result = context.getDamageResult();
        if (result == null || result.isCancelled()) {
            return false;
        }

        // Get current damage amount
        double currentDamage = result.getRemainingDamageAmount();
        if (currentDamage <= 0.0) {
            return false;
        }

        // Apply multiplier
        double newDamage = currentDamage * multiplier;
        result.setRemainingDamageAmount(newDamage);

        return true;
    }
}
