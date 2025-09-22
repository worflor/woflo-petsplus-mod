package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.config.PetsPlusConfig;

import java.util.List;

/**
 * Effect that applies aura in a forward cone when owner is mounted.
 * Used by Support role for mounted cone bias aura system.
 */
public class MountedConeAuraEffect implements Effect {
    private final double baseRadius;
    private final int extraRadius;
    private final int effectDuration;
    private final int effectAmplifier;
    
    public MountedConeAuraEffect(JsonObject config) {
        this.baseRadius = getDoubleOrDefault(config, "base_radius", 8.0);
        this.extraRadius = getIntOrDefault(config, "extra_radius",
            PetsPlusConfig.getInstance().getInt("support", "mountedConeExtraRadius", 2));
        this.effectDuration = getIntOrDefault(config, "effect_duration", 60);
        this.effectAmplifier = getIntOrDefault(config, "effect_amplifier", 0);
    }

    private static double getDoubleOrDefault(JsonObject json, String key, double defaultValue) {
        if (!json.has(key)) return defaultValue;

        com.google.gson.JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsDouble();
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            return parseConfigVariable(value, defaultValue);
        }

        return defaultValue;
    }

    private static int getIntOrDefault(JsonObject json, String key, int defaultValue) {
        if (!json.has(key)) return defaultValue;

        com.google.gson.JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            return (int) parseConfigVariable(value, defaultValue);
        }

        return defaultValue;
    }

    private static double parseConfigVariable(String value, double defaultValue) {
        if (value.startsWith("${") && value.endsWith("}")) {
            String configPath = value.substring(2, value.length() - 1);
            String[] parts = configPath.split("\\.");
            if (parts.length == 2) {
                return PetsPlusConfig.getInstance().getDouble(parts[0], parts[1], defaultValue);
            }
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    @Override
    public Identifier getId() {
        return Identifier.of("petsplus", "mounted_cone_aura");
    }
    
    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();
        MobEntity pet = context.getPet();
        ServerWorld world = context.getWorld();
        
        if (owner == null || pet == null || world == null) {
            return false;
        }
        
        // Check if owner is mounted
        Entity mount = owner.getVehicle();
        if (mount == null) {
            return false;
        }
        
        // Calculate cone parameters
        double totalRadius = baseRadius + extraRadius;
        Vec3d ownerPos = owner.getPos();
        Vec3d lookDirection = owner.getRotationVector();
        
        // Create search box around owner
        Box searchBox = Box.of(ownerPos, totalRadius * 2, totalRadius * 2, totalRadius * 2);
        
        // Find entities in the area
        List<LivingEntity> nearbyEntities = world.getEntitiesByClass(
            LivingEntity.class, 
            searchBox, 
            entity -> entity != owner && entity.isAlive() && entity.distanceTo(owner) <= totalRadius
        );
        
        // Apply cone filter and effects
        for (LivingEntity entity : nearbyEntities) {
            if (isInForwardCone(ownerPos, lookDirection, entity.getPos(), totalRadius)) {
                applyEffectToEntity(entity, world);
            }
        }
        
        return true;
    }
    
    /**
     * Check if a position is within the forward cone of the owner.
     */
    private boolean isInForwardCone(Vec3d ownerPos, Vec3d lookDirection, Vec3d targetPos, double radius) {
        Vec3d toTarget = targetPos.subtract(ownerPos);
        double distance = toTarget.length();
        
        if (distance > radius) {
            return false;
        }
        
        // Normalize vectors for dot product
        Vec3d normalizedToTarget = toTarget.normalize();
        Vec3d normalizedLookDirection = lookDirection.normalize();
        
        // Calculate cone angle (45 degrees forward, so cos(45°) ≈ 0.707)
        double dotProduct = normalizedToTarget.dotProduct(normalizedLookDirection);
        return dotProduct > 0.707; // 45-degree cone
    }
    
    /**
     * Apply the aura effect to a specific entity.
     */
    private void applyEffectToEntity(LivingEntity entity, ServerWorld world) {
        // Apply regeneration or other beneficial effect
        // This is a simplified implementation - in practice you'd parse the effect ID
        // and create the appropriate StatusEffectInstance
        
        // For now, we'll just apply regeneration as an example
        // In a full implementation, this would use the configured effect ID
        
        // Skip if entity already has the effect with sufficient duration
        if (entity.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION)) {
            var existing = entity.getStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION);
            if (existing != null && existing.getDuration() > 20) {
                return; // Skip if effect still has more than 1 second
            }
        }
        
        // Apply new effect
        entity.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
            net.minecraft.entity.effect.StatusEffects.REGENERATION,
            effectDuration,
            effectAmplifier,
            false,
            false,
            true
        ));
    }
}