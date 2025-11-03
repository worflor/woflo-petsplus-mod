package woflo.petsplus.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.SpeedModifierHelper;
import woflo.petsplus.effects.MagnetizeDropsAndXpEffect;
import woflo.petsplus.mechanics.CursedOneResurrection;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.PetMoodEngine;
import woflo.petsplus.ui.AfterimageManager;

import java.lang.reflect.Field;

@Mixin(MobEntity.class)
public abstract class MobEntityTickMixin {
    private static final Identifier MOMENTUM_SPEED_MODIFIER_ID = Identifier.of(Petsplus.MOD_ID, "momentum_speed");
    private static final Field LIMB_ANIMATOR_FIELD;
    private static final Field LIMB_SPEED_FIELD;
    private static final Field LIMB_LAST_SPEED_FIELD;
    
    private static final int SERVER_UPDATE_INTERVAL = 5;  // Every 5 ticks (250ms)
    private static final int CLIENT_UPDATE_INTERVAL = 2;  // Every 2 ticks (100ms)

    static {
        Field animator = null;
        Field speed = null;
        Field lastSpeed = null;
        try {
            animator = LivingEntity.class.getDeclaredField("limbAnimator");
            animator.setAccessible(true);
            Class<?> limbClass = animator.getType();
            speed = limbClass.getDeclaredField("speed");
            speed.setAccessible(true);
            lastSpeed = limbClass.getDeclaredField("lastSpeed");
            lastSpeed.setAccessible(true);
        } catch (ReflectiveOperationException ignored) {
            animator = null;
            speed = null;
            lastSpeed = null;
        }
        LIMB_ANIMATOR_FIELD = animator;
        LIMB_SPEED_FIELD = speed;
        LIMB_LAST_SPEED_FIELD = lastSpeed;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void petsplus(CallbackInfo ci) {
        MobEntity mob = (MobEntity) (Object) this;
        
        // Vanilla mobs get instant exit (zero overhead)
        PetComponent petComponent = PetComponent.getOrCreate(mob);
        if (petComponent == null) return;
        
        World world = mob.getEntityWorld();
        
        // Server-side special effects
        if (world instanceof ServerWorld serverWorld) {
            AfterimageManager.handleMobTick(mob, serverWorld);
            CursedOneResurrection.handleMobTick(mob, serverWorld);
            MagnetizeDropsAndXpEffect.handleMobTick(mob, serverWorld);
        }

        // No mood engine = no momentum to update
        PetMoodEngine moodEngine = petComponent.getMoodEngine();
        if (moodEngine == null) return;

        // Update momentum effects at throttled intervals
        switch (world) {
            case ServerWorld sw when mob.age % SERVER_UPDATE_INTERVAL == 0 -> {
                float multiplier = moodEngine.getMomentumSpeedMultiplier();
                SpeedModifierHelper.applyMovementSpeedMultiplier(mob, MOMENTUM_SPEED_MODIFIER_ID, multiplier);
            }
            case World w when world.isClient() && mob.age % CLIENT_UPDATE_INTERVAL == 0 -> {
                float animationMultiplier = MathHelper.clamp(moodEngine.getMomentumAnimationSpeed(), 0.6f, 1.6f);
                applyClientAnimationScaling(mob, animationMultiplier);
            }
            default -> {}
        }
    }

    private void applyClientAnimationScaling(MobEntity mob, float multiplier) {
        if (LIMB_ANIMATOR_FIELD == null || LIMB_SPEED_FIELD == null || LIMB_LAST_SPEED_FIELD == null) {
            return;
        }
        try {
            Object limbAnimator = LIMB_ANIMATOR_FIELD.get(mob);
            if (limbAnimator == null) {
                return;
            }
            float speed = LIMB_SPEED_FIELD.getFloat(limbAnimator);
            float lastSpeed = LIMB_LAST_SPEED_FIELD.getFloat(limbAnimator);
            LIMB_SPEED_FIELD.setFloat(limbAnimator, MathHelper.clamp(speed * multiplier, 0.0f, 5.0f));
            LIMB_LAST_SPEED_FIELD.setFloat(limbAnimator, MathHelper.clamp(lastSpeed * multiplier, 0.0f, 5.0f));
        } catch (IllegalAccessException ignored) {
        }
    }
}
