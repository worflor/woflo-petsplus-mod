package woflo.petsplus.effects;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.roles.cursedone.CursedOneSoulSacrificeManager;
import woflo.petsplus.ui.UIFeedbackManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes the cursed death burst detonation when a Cursed One pet dies permanently or
 * completes its reanimation cycle.
 */
public class CursedOneDeathBurstEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "cursed_one_death_burst");

    private final double radius;
    private final double damage;
    private final boolean ignite;
    private final List<StatusEffectInstance> enemyEffects;
    private final double reanimationRadiusScale;
    private final double reanimationDamageScale;
    private final boolean reanimationIgnite;
    private final double reanimationEffectDurationScale;

    public CursedOneDeathBurstEffect(double radius, double damage, boolean ignite,
                                     List<StatusEffectInstance> enemyEffects,
                                     double reanimationRadiusScale,
                                     double reanimationDamageScale,
                                     boolean reanimationIgnite,
                                     double reanimationEffectDurationScale) {
        this.radius = Math.max(0.5, radius);
        this.damage = Math.max(0.0, damage);
        this.ignite = ignite;
        this.enemyEffects = new ArrayList<>(enemyEffects);
        this.reanimationRadiusScale = Math.max(0.0, reanimationRadiusScale);
        this.reanimationDamageScale = Math.max(0.0, reanimationDamageScale);
        this.reanimationIgnite = reanimationIgnite;
        this.reanimationEffectDurationScale = Math.max(0.0, reanimationEffectDurationScale);
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getEntityWorld() instanceof ServerWorld world)) {
            return false;
        }

        MobEntity pet = context.getPet();
        if (pet == null) {
            return false;
        }

        if (CursedOneSoulSacrificeManager.isDeathBurstSuppressed(pet)) {
            return false;
        }

        boolean fromReanimation = isReanimationBurst(context);
        double activeRadius = resolveRadius(fromReanimation);
        double activeDamage = resolveDamage(fromReanimation);
        boolean shouldIgnite = resolveIgnite(fromReanimation);
        List<StatusEffectInstance> effectsToApply = resolveEnemyEffects(fromReanimation);

        if (activeRadius <= 0.0 && activeDamage <= 0.0 && !shouldIgnite && effectsToApply.isEmpty()) {
            return false;
        }

        Vec3d center = pet.getEntityPos();
        Box area = Box.of(center, activeRadius * 2.0, activeRadius * 2.0, activeRadius * 2.0);

        List<HostileEntity> targets = world.getEntitiesByClass(HostileEntity.class, area,
            entity -> entity.isAlive() && entity.squaredDistanceTo(center) <= activeRadius * activeRadius);

        playVisuals(world, center, activeRadius);

        for (HostileEntity target : targets) {
            if (activeDamage > 0.0) {
                target.damage(world, world.getDamageSources().magic(), (float) activeDamage);
            }

            if (shouldIgnite) {
                target.setOnFireFor(4);
            }

            for (StatusEffectInstance effect : effectsToApply) {
                if (effect == null) {
                    continue;
                }
                target.addStatusEffect(new StatusEffectInstance(effect));
            }
        }

        // Removed death burst spam - will add particle/sound in ability JSON

        return true;
    }

    private boolean isReanimationBurst(EffectContext context) {
        TriggerContext trigger = context.getTriggerContext();
        if (trigger == null) {
            return false;
        }
        String reason = trigger.getData("death_burst_reason", String.class);
        return "reanimation".equals(reason);
    }

    private double resolveRadius(boolean reanimation) {
        if (!reanimation) {
            return radius;
        }
        double scaled = radius * reanimationRadiusScale;
        return scaled > 0.0 ? Math.max(1.0, scaled) : 0.0;
    }

    private double resolveDamage(boolean reanimation) {
        if (!reanimation) {
            return damage;
        }
        double scaled = damage * reanimationDamageScale;
        return scaled > 0.0 ? scaled : 0.0;
    }

    private boolean resolveIgnite(boolean reanimation) {
        if (!reanimation) {
            return ignite;
        }
        return ignite && reanimationIgnite;
    }

    private List<StatusEffectInstance> resolveEnemyEffects(boolean reanimation) {
        if (!reanimation || reanimationEffectDurationScale == 1.0) {
            return enemyEffects;
        }

        double scale = reanimationEffectDurationScale;
        if (scale <= 0.0) {
            return List.of();
        }

        List<StatusEffectInstance> scaled = new ArrayList<>(enemyEffects.size());
        for (StatusEffectInstance effect : enemyEffects) {
            if (effect == null) {
                continue;
            }
            int duration = effect.getDuration();
            int scaledDuration = Math.max(1, (int) Math.round(duration * scale));
            StatusEffectInstance adjusted = new StatusEffectInstance(effect.getEffectType(), scaledDuration,
                effect.getAmplifier(), effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());
            scaled.add(adjusted);
        }
        return scaled;
    }

    private void playVisuals(ServerWorld world, Vec3d center, double visualRadius) {
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_GENERIC_EXPLODE,
            SoundCategory.HOSTILE, 0.9f, 0.6f);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK,
            SoundCategory.HOSTILE, 0.6f, 0.4f);

        world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
            center.x, center.y + 0.3, center.z, 60,
            visualRadius * 0.4, visualRadius * 0.3, visualRadius * 0.4, 0.05);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
            center.x, center.y + 0.5, center.z, 45,
            visualRadius * 0.5, visualRadius * 0.4, visualRadius * 0.5, 0.02);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.ASH,
            center.x, center.y + 0.2, center.z, 30,
            visualRadius * 0.35, visualRadius * 0.25, visualRadius * 0.35, 0.01);
    }
}



