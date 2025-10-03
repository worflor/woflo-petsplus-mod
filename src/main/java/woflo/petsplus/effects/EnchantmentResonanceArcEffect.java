package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Creates enchantment arcs that damage nearby enemies. 
 * Perfect timing (within perfect window) deals more damage and refunds cooldown.
 */
public final class EnchantmentResonanceArcEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "enchantment_resonance_arc");

    private final double arcRange;
    private final int maxTargets;
    private final double baseDamagePercent;
    private final double perfectDamagePercent;
    private final String resonanceWindowKey;
    private final String perfectWindowKey;
    private final String particleType;
    private final int arcParticleCount;

    public EnchantmentResonanceArcEffect(JsonObject json) {
        this.arcRange = RegistryJsonHelper.getDouble(json, "arc_range", 4.0);
        this.maxTargets = RegistryJsonHelper.getInt(json, "max_targets", 2);
        this.baseDamagePercent = RegistryJsonHelper.getDouble(json, "base_damage_percent", 0.35);
        this.perfectDamagePercent = RegistryJsonHelper.getDouble(json, "perfect_damage_percent", 0.5);
        this.resonanceWindowKey = RegistryJsonHelper.getString(json, "resonance_window_key", "enchantment_resonance_window");
        this.perfectWindowKey = RegistryJsonHelper.getString(json, "perfect_window_key", "enchantment_perfect_window");
        this.particleType = RegistryJsonHelper.getString(json, "particle_type", "minecraft:enchant");
        this.arcParticleCount = RegistryJsonHelper.getInt(json, "arc_particle_count", 12);
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        TriggerContext trigger = context.getTriggerContext();
        if (trigger == null) {
            return false;
        }

        PlayerEntity owner = context.getOwner();
        if (!(owner instanceof ServerPlayerEntity serverOwner)) {
            return false;
        }

        ServerWorld world = context.getWorld();
        if (world == null) {
            return false;
        }

        Entity victimEntity = trigger.getVictim();
        if (!(victimEntity instanceof LivingEntity primaryVictim)) {
            return false;
        }

        double baseDamage = Math.max(0.0, trigger.getDamage());
        if (baseDamage <= 0.0) {
            return false;
        }

        // Check if resonance window is active
        OwnerCombatState combatState = OwnerCombatState.get(serverOwner);
        if (combatState == null || !combatState.hasTempState(resonanceWindowKey)) {
            return false;
        }

        long resonanceExpiry = combatState.getTempState(resonanceWindowKey);
        if (world.getTime() > resonanceExpiry) {
            return false;
        }

        // Check if this was a perfect timing hit
        boolean isPerfect = combatState.hasTempState(perfectWindowKey) && 
                           world.getTime() <= combatState.getTempState(perfectWindowKey);

        double damagePercent = isPerfect ? perfectDamagePercent : baseDamagePercent;
        double arcDamage = baseDamage * damagePercent;

        // Find nearby enemies to arc to
        List<LivingEntity> nearbyEnemies = findNearbyEnemies(world, serverOwner, primaryVictim, arcRange);
        int arcsCreated = 0;

        for (LivingEntity target : nearbyEnemies) {
            if (arcsCreated >= maxTargets) {
                break;
            }

            // Deal arc damage
            DamageSource damageSource = world.getDamageSources().indirectMagic(serverOwner, serverOwner);
            target.damage(world, damageSource, (float) arcDamage);

            // Spawn arc particles from primary victim to target
            spawnArcParticles(world, primaryVictim.getPos(), target.getPos());

            arcsCreated++;
        }

        // If perfect timing, refund the trigger's cooldown
        if (isPerfect && arcsCreated > 0) {
            trigger.withData("refund_cooldown", Boolean.TRUE);
            
            // Extra particle burst for perfect timing
            world.spawnParticles(
                ParticleTypes.ENCHANTED_HIT,
                primaryVictim.getX(),
                primaryVictim.getY() + primaryVictim.getHeight() / 2,
                primaryVictim.getZ(),
                8, 0.3, 0.3, 0.3, 0.1
            );
        }

        return arcsCreated > 0;
    }

    private List<LivingEntity> findNearbyEnemies(ServerWorld world, PlayerEntity owner, LivingEntity exclude, double range) {
        Box searchBox = new Box(
            exclude.getX() - range, exclude.getY() - range, exclude.getZ() - range,
            exclude.getX() + range, exclude.getY() + range, exclude.getZ() + range
        );

        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : world.getOtherEntities(exclude, searchBox)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (!living.isAlive()) {
                continue;
            }
            if (living == owner) {
                continue;
            }
            // Skip friendly entities
            if (owner.isTeammate(living)) {
                continue;
            }
            // Skip owner's pets
            if (living instanceof MobEntity mob) {
                PetComponent petComp = PetComponent.get(mob);
                if (petComp != null && petComp.getOwner() == owner) {
                    continue;
                }
            }

            double dist = living.squaredDistanceTo(exclude);
            if (dist <= range * range) {
                candidates.add(living);
            }
        }

        // Sort by distance, closest first
        candidates.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(exclude)));
        return candidates;
    }

    private void spawnArcParticles(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d direction = to.subtract(from);
        double distance = direction.length();
        if (distance < 0.1) {
            return;
        }

        Vec3d step = direction.normalize().multiply(distance / arcParticleCount);
        
        for (int i = 0; i < arcParticleCount; i++) {
            Vec3d pos = from.add(step.multiply(i));
            world.spawnParticles(
                ParticleTypes.ENCHANT,
                pos.x, pos.y, pos.z,
                1, 0.05, 0.05, 0.05, 0.01
            );
        }
    }
}
