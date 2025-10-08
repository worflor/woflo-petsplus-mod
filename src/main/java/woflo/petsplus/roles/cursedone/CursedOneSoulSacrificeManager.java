package woflo.petsplus.roles.cursedone;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.state.PetComponent;

import java.util.Objects;

/**
 * Handles persistent state for the Cursed One "Soul Sacrifice" ability.
 * Tracks the reanimation penalty multiplier and temporary death burst suppression
 * so other systems can respond without duplicating JSON logic.
 */
public final class CursedOneSoulSacrificeManager {
    private static final String REANIMATION_MULTIPLIER_KEY = "cursed_soul_sacrifice_reanimation_multiplier";
    private static final String SACRIFICE_EXPIRY_KEY = "cursed_soul_sacrifice_expiry";

    private CursedOneSoulSacrificeManager() {
    }

    /**
     * Marks a soul sacrifice activation for the given pet.
     *
     * @return {@code true} if state could be recorded.
     */
    public static boolean markActivation(MobEntity pet, int durationTicks, double reanimationMultiplier) {
        if (!(pet.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null) {
            return false;
        }

        long expiry = serverWorld.getTime() + durationTicks;
        component.setStateData(REANIMATION_MULTIPLIER_KEY, reanimationMultiplier);
        component.setStateData(SACRIFICE_EXPIRY_KEY, expiry);
        return true;
    }

    /**
     * Resolves the active reanimation duration modifier for the pet.
     * If no modifier is active this returns {@code baseDuration}.
     */
    public static int resolveReanimationDuration(MobEntity pet, int baseDuration) {
        PetComponent component = PetComponent.get(pet);
        if (component == null || !(pet.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return baseDuration;
        }

        Long expiry = component.getStateData(SACRIFICE_EXPIRY_KEY, Long.class);
        if (expiry == null || expiry < serverWorld.getTime()) {
            clearExpired(component);
            return baseDuration;
        }

        Double multiplier = component.getStateData(REANIMATION_MULTIPLIER_KEY, Double.class);
        if (multiplier == null || multiplier <= 1.0) {
            return baseDuration;
        }

        int adjusted = (int) Math.round(baseDuration * Math.max(1.0, multiplier));
        component.clearStateData(REANIMATION_MULTIPLIER_KEY);
        return Math.max(baseDuration, adjusted);
    }

    /**
     * Returns {@code true} if death burst should be suppressed for this pet.
     */
    public static boolean isDeathBurstSuppressed(MobEntity pet) {
        PetComponent component = PetComponent.get(pet);
        if (component == null || !(pet.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }

        Long expiry = component.getStateData(SACRIFICE_EXPIRY_KEY, Long.class);
        if (expiry == null) {
            return false;
        }

        if (expiry < serverWorld.getTime()) {
            clearExpired(component);
            return false;
        }

        return true;
    }

    /**
     * Clears sacrifice state if the stored expiry is in the past.
     */
    private static void clearExpired(PetComponent component) {
        component.clearStateData(REANIMATION_MULTIPLIER_KEY);
        component.clearStateData(SACRIFICE_EXPIRY_KEY);
    }

    /**
     * Emits a subtle particle pulse to highlight the active sacrifice state.
     */
    public static void emitActivationParticles(MobEntity pet, ServerPlayerEntity owner) {
        if (!(pet.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        Vec3d pos = pet.getEntityPos();
        serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
            pos.x, pos.y + 0.6, pos.z, 40,
            0.6, 0.4, 0.6, 0.02);
        serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
            pos.x, pos.y + 0.4, pos.z, 24,
            0.4, 0.3, 0.4, 0.05);

        if (!Objects.equals(owner.getEntityWorld(), serverWorld)) {
            return;
        }

        owner.playSound(net.minecraft.sound.SoundEvents.ITEM_TOTEM_USE, 0.65f, 0.6f);
        owner.playSound(net.minecraft.sound.SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 1.25f);
    }
}


