package woflo.petsplus.mixin;

import net.minecraft.entity.Entity;
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
import java.util.Map;
import java.util.WeakHashMap;

@Mixin(MobEntity.class)
public abstract class MobEntityTickMixin {
    private static final Identifier MOMENTUM_SPEED_MODIFIER_ID = Identifier.of(Petsplus.MOD_ID, "momentum_speed");
    private static final Field LIMB_ANIMATOR_FIELD;
    private static final Field LIMB_SPEED_FIELD;
    private static final Field LIMB_LAST_SPEED_FIELD;
    private static final Map<MobEntity, Long> LAST_SERVER_UPDATE = new WeakHashMap<>();
    private static final Map<MobEntity, Integer> LAST_CLIENT_UPDATE = new WeakHashMap<>();

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
        Entity entity = (Entity) (Object) this;
        World world = entity.getEntityWorld();
        MobEntity mob = (MobEntity) entity;

        if (world instanceof ServerWorld serverWorld) {
            AfterimageManager.handleMobTick(mob, serverWorld);
            CursedOneResurrection.handleMobTick(mob, serverWorld);
            MagnetizeDropsAndXpEffect.handleMobTick(mob, serverWorld);
        }

        PetComponent petComponent = PetComponent.get(mob);
        if (petComponent == null) {
            if (!world.isClient()) {
                SpeedModifierHelper.clearMovementSpeedModifier(mob, MOMENTUM_SPEED_MODIFIER_ID);
            }
            return;
        }

        PetMoodEngine moodEngine = petComponent.getMoodEngine();
        if (moodEngine == null) {
            if (!world.isClient()) {
                SpeedModifierHelper.clearMovementSpeedModifier(mob, MOMENTUM_SPEED_MODIFIER_ID);
            }
            return;
        }

        if (world instanceof ServerWorld) {
            ServerWorld sw = (ServerWorld) world;
            long now = sw.getTime();
            Long last = LAST_SERVER_UPDATE.get(mob);
            if (last == null || now - last >= 5) {
                LAST_SERVER_UPDATE.put(mob, now);
                float multiplier = moodEngine.getMomentumSpeedMultiplier();
                SpeedModifierHelper.applyMovementSpeedMultiplier(mob, MOMENTUM_SPEED_MODIFIER_ID, multiplier);
            }
        } else if (world.isClient()) {
            // Recompute client animation speed at most every 2 ticks per mob
            int age = mob.age;
            Integer last = LAST_CLIENT_UPDATE.get(mob);
            if (last == null || age - last >= 2) {
                LAST_CLIENT_UPDATE.put(mob, age);
                float animationMultiplier = MathHelper.clamp(moodEngine.getMomentumAnimationSpeed(), 0.6f, 1.6f);
                applyClientAnimationScaling(mob, animationMultiplier);
            }
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
