package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.roles.enchantmentbound.EnchantmentBoundEchoes;

/**
 * Applies the Enchantment-Bound perched haste bonus via the ability system.
 */
public class EnchantmentBoundPerchedHasteEffect implements Effect {
    private final int baseDurationTicks;
    private final int amplifier;

    public EnchantmentBoundPerchedHasteEffect(JsonObject config) {
        this.baseDurationTicks = Math.max(20, config.has("base_duration") ? config.get("base_duration").getAsInt() : 120);
        this.amplifier = Math.max(0, config.has("amplifier") ? config.get("amplifier").getAsInt() : 0);
    }

    @Override
    public Identifier getId() {
        return Identifier.of("petsplus", "enchantment_perched_haste");
    }

    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();

        if (owner == null) {
            return false;
        }

        EnchantmentBoundEchoes.applyEnhancedHaste(owner, baseDurationTicks, amplifier);
        return true;
    }
}
