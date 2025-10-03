package woflo.petsplus.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;

/**
 * Applies a potion effect to the victim entity.
 * Reusable effect for any ability that needs to debuff/buff victims on hit.
 */
public class ApplyPotionToVictimEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "apply_potion_to_victim");
    
    private final String effectId;
    private final int duration;
    private final int amplifier;
    private final String victimKey;
    
    public ApplyPotionToVictimEffect(String effectId, int duration, int amplifier) {
        this(effectId, duration, amplifier, "victim");
    }
    
    public ApplyPotionToVictimEffect(String effectId, int duration, int amplifier, String victimKey) {
        this.effectId = effectId;
        this.duration = duration;
        this.amplifier = amplifier;
        this.victimKey = victimKey;
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        LivingEntity victim = context.getData(victimKey, LivingEntity.class);
        if (victim == null || !victim.isAlive()) {
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
        
        // Apply effect
        StatusEffectInstance effectInstance = new StatusEffectInstance(
            statusEffect,
            duration,
            amplifier,
            false, // ambient
            true,  // show particles
            true   // show icon
        );
        
        victim.addStatusEffect(effectInstance);
        return true;
    }
}
