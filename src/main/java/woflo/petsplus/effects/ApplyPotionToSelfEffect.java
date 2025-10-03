package woflo.petsplus.effects;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;

/**
 * Applies a potion effect to the pet itself.
 */
public class ApplyPotionToSelfEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "apply_potion_to_self");
    
    private final String effectId;
    private final int duration;
    private final int amplifier;
    
    public ApplyPotionToSelfEffect(String effectId, int duration, int amplifier) {
        this.effectId = effectId;
        this.duration = duration;
        this.amplifier = amplifier;
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        Entity pet = context.getPet();
        if (!(pet instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        
        // Parse effect ID
        Identifier effectIdentifier = Identifier.tryParse(effectId);
        if (effectIdentifier == null) {
            return false;
        }
        
        // Get status effect from registry
        RegistryEntry<StatusEffect> statusEffect = Registries.STATUS_EFFECT.getEntry(effectIdentifier).orElse(null);
        if (statusEffect == null) {
            return false;
        }
        
        // Apply effect to pet
        StatusEffectInstance effectInstance = new StatusEffectInstance(
            statusEffect,
            duration,
            amplifier,
            false, // ambient
            true,  // show particles
            true   // show icon
        );
        
        living.addStatusEffect(effectInstance);
        return true;
    }
}
