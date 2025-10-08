package woflo.petsplus.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.state.PetComponent;

/**
 * Scout: Apply enhanced mark (glowing + weakness II) to victim if pet has scout_enhanced_mark state.
 * Consumes the state on application.
 */
public class ScoutEnhancedMarkEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "scout_enhanced_mark");
    
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
        
        // Check if pet has enhanced mark state and it hasn't expired
        if (!(context.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        
        Long expiryTick = petComp.getStateData("scout_enhanced_mark", Long.class);
        if (expiryTick == null || serverWorld.getTime() > expiryTick) {
            return false; // State expired or doesn't exist
        }
        
        LivingEntity victim = context.getData("victim", LivingEntity.class);
        if (victim == null || !victim.isAlive()) {
            return false;
        }
        
        // Apply enhanced mark: glowing + weakness II for 200 ticks
        victim.addStatusEffect(new StatusEffectInstance(
            StatusEffects.GLOWING,
            200, 0, false, true, true
        ));
        
        victim.addStatusEffect(new StatusEffectInstance(
            StatusEffects.WEAKNESS,
            200, 1, false, true, true
        ));
        
        // Clear the state (consume it)
        petComp.clearStateData("scout_enhanced_mark");
        
        // Subtle particle burst - white smoke ring at victim's chest
        serverWorld.spawnParticles(
            net.minecraft.particle.ParticleTypes.WHITE_SMOKE,
            victim.getX(), victim.getY() + victim.getHeight() * 0.6, victim.getZ(),
            8, 0.3, 0.2, 0.3, 0.02
        );
        
        // Tiny soul particles rising (cursed mark aesthetic)
        serverWorld.spawnParticles(
            net.minecraft.particle.ParticleTypes.SOUL,
            victim.getX(), victim.getY() + victim.getHeight() * 0.3, victim.getZ(),
            3, 0.2, 0.1, 0.2, 0.01
        );
        
        return true;
    }
}


