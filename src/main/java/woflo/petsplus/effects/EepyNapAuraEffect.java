package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.UIFeedbackManager;
import woflo.petsplus.util.EffectConfigHelper;

/**
 * Emits the nap time regeneration aura for sitting Eepy Eeper companions.
 */
public class EepyNapAuraEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "eepy_nap_aura");
    private static final String STATE_LAST_MESSAGE = "eepy_nap_last_msg";

    private final double radius;
    private final int durationTicks;
    private final int amplifier;
    private final int minLevel;
    private final boolean requireSitting;
    private final int particleCount;
    private final int messageIntervalTicks;

    public EepyNapAuraEffect(JsonObject json) {
        this.radius = EffectConfigHelper.parseRadius(json, "radius", 4.0);
        this.durationTicks = EffectConfigHelper.parseDuration(json, "duration_ticks", 120);
        this.amplifier = EffectConfigHelper.parseAmplifier(json, "amplifier", 0);
        this.minLevel = EffectConfigHelper.parseMinLevel(json, "min_level", 10);
        this.requireSitting = RegistryJsonHelper.getBoolean(json, "require_sitting", true);
        this.particleCount = EffectConfigHelper.parseParticleCount(json, "particle_count", 4);
        this.messageIntervalTicks = EffectConfigHelper.parseDuration(json, "message_interval_ticks", 200);
    }

    public EepyNapAuraEffect() {
        this.radius = 4.0D;
        this.durationTicks = 120;
        this.amplifier = 0;
        this.minLevel = 10;
        this.requireSitting = true;
        this.particleCount = 4;
        this.messageIntervalTicks = 200;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        ServerWorld world = context.getEntityWorld();
        MobEntity pet = context.getPet();
        PlayerEntity owner = context.getOwner();

        if (world == null || pet == null) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.EEPY_EEPER) || component.getLevel() < minLevel) {
            return false;
        }

        if (requireSitting && pet instanceof PetsplusTameable tameable && !tameable.petsplus$isSitting()) {
            return false;
        }

        Box area = pet.getBoundingBox().expand(radius);
        List<LivingEntity> targets = world.getEntitiesByClass(
            LivingEntity.class,
            area,
            entity -> entity != pet && entity.isAlive() && (entity instanceof PlayerEntity || hasPetComponent(entity))
        );

        boolean applied = false;
        StatusEffectInstance regen = new StatusEffectInstance(StatusEffects.REGENERATION, durationTicks, amplifier, false, true, true);
        for (LivingEntity target : targets) {
            target.addStatusEffect(regen);
            applied = true;
        }

        if (!applied) {
            return false;
        }

        emitParticlesAndAmbient(world, pet);
        maybeSendMessage(world, component, owner, pet);
        return true;
    }

    private static boolean hasPetComponent(LivingEntity entity) {
        if (!(entity instanceof MobEntity mob)) {
            return false;
        }
        return PetComponent.get(mob) != null;
    }

    private void emitParticlesAndAmbient(ServerWorld world, MobEntity pet) {
        Vec3d pos = pet.getEntityPos();
        if (particleCount > 0) {
            world.spawnParticles(
                net.minecraft.particle.ParticleTypes.END_ROD,
                pos.x,
                pos.y + 1.0D,
                pos.z,
                particleCount,
                0.45D,
                0.25D,
                0.45D,
                0.01D
            );
            world.spawnParticles(
                net.minecraft.particle.ParticleTypes.SPORE_BLOSSOM_AIR,
                pos.x,
                pos.y + 0.8D,
                pos.z,
                Math.max(1, particleCount / 2),
                0.35D,
                0.2D,
                0.35D,
                0.008D
            );
        }

        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_CAT_PURR, SoundCategory.NEUTRAL, 0.25F, 0.8F + world.random.nextFloat() * 0.1F);
    }

    private void maybeSendMessage(ServerWorld world, PetComponent component, PlayerEntity owner, MobEntity pet) {
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return;
        }
        long now = world.getTime();
        long last = component.getStateData(STATE_LAST_MESSAGE, Long.class, 0L);
        if (now - last < messageIntervalTicks) {
            return;
        }
        component.setStateData(STATE_LAST_MESSAGE, now);
        // Removed nap time spam - too frequent, not meaningful
    }
}



