package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.roles.eclipsed.EclipsedVoid;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.UIFeedbackManager;

/**
 * Reduces incoming fall damage for Eclipsed owners when their companion is perched nearby.
 */
public class EclipsedEdgeStepEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "eclipsed_edge_step");

    private final double reductionPercent;
    private final double minFallDistance;
    private final int minLevel;
    private final boolean requirePerched;
    private final int slowFallingDuration;
    private final int speedDuration;
    private final int speedAmplifier;
    private final double healFlat;
    private final double healPercent;

    public EclipsedEdgeStepEffect(JsonObject json) {
        this.reductionPercent = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "reduction_pct", EclipsedVoid.getEdgeStepFallReductionPct()));
        this.minFallDistance = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "min_fall_distance", 3.0D));
        this.minLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 27));
        this.requirePerched = RegistryJsonHelper.getBoolean(json, "require_perched", true);
        this.slowFallingDuration = Math.max(0, RegistryJsonHelper.getInt(json, "slow_falling_duration_ticks", 60));
        this.speedDuration = Math.max(0, RegistryJsonHelper.getInt(json, "speed_duration_ticks", 80));
        this.speedAmplifier = Math.max(0, RegistryJsonHelper.getInt(json, "speed_amplifier", 1));
        this.healFlat = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "heal_flat", 0.0D));
        this.healPercent = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "heal_percent", 0.0D));
    }

    public EclipsedEdgeStepEffect() {
        this.reductionPercent = EclipsedVoid.getEdgeStepFallReductionPct();
        this.minFallDistance = 3.0D;
        this.minLevel = 27;
        this.requirePerched = true;
        this.slowFallingDuration = 60;
        this.speedDuration = 80;
        this.speedAmplifier = 1;
        this.healFlat = 0.0D;
        this.healPercent = 0.0D;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!context.hasDamageContext()) {
            return false;
        }
        DamageInterceptionResult result = context.getDamageResult();
        if (result == null || result.isCancelled()) {
            return false;
        }
        DamageSource source = context.getIncomingDamageSource();
        if (source == null || !source.isOf(DamageTypes.FALL)) {
            return false;
        }
        if (reductionPercent <= 0.0D) {
            return false;
        }

        MobEntity pet = context.getPet();
        PlayerEntity owner = context.getOwner();
        if (!(context.getWorld() instanceof ServerWorld world) || pet == null || owner == null) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.ECLIPSED) || component.getLevel() < minLevel) {
            return false;
        }

        if (requirePerched && !EclipsedVoid.isPetPerched(pet, owner)) {
            return false;
        }

        double fallDistance = owner.fallDistance;
        if (context.getTriggerContext() != null) {
            double reported = context.getTriggerContext().getFallDistance();
            if (reported > 0.0D) {
                fallDistance = reported;
            }
        }
        if (fallDistance < minFallDistance) {
            return false;
        }

        double remaining = result.getRemainingDamageAmount();
        if (remaining <= 0.0D) {
            return false;
        }

        double reducedAmount = remaining * Math.max(0.0D, 1.0D - reductionPercent);
        result.setRemainingDamageAmount(reducedAmount);

        applyRecovery(owner);
        emitFeedback(world, owner, pet);
        return true;
    }

    private void applyRecovery(PlayerEntity owner) {
        if (slowFallingDuration > 0) {
            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, slowFallingDuration, 0));
        }
        if (speedDuration > 0) {
            owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, speedDuration, speedAmplifier));
        }
        double healAmount = healFlat;
        if (healPercent > 0.0D) {
            healAmount += owner.getMaxHealth() * healPercent;
        }
        if (healAmount > 0.0D) {
            owner.heal((float) healAmount);
        }
        // Removed edge step spam - already has particle/sound feedback in emitFeedback()
    }

    private void emitFeedback(ServerWorld world, PlayerEntity owner, MobEntity pet) {
        world.spawnParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY() + 0.2D, owner.getZ(), 12, 0.3D, 0.2D, 0.3D, 0.02D);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 0.4F, 1.6F);
        world.spawnParticles(ParticleTypes.END_ROD, pet.getX(), pet.getY() + 1.0D, pet.getZ(), 4, 0.2D, 0.2D, 0.2D, 0.01D);
    }
}
