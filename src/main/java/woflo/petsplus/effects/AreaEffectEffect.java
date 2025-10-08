package woflo.petsplus.effects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Effect that applies status effects to all entities in an area.
 */
public class AreaEffectEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "area_effect");
    
    private final double radius;
    private final List<StatusEffectData> effects;
    private final boolean hostilesOnly;
    
    public AreaEffectEffect(double radius, List<StatusEffectData> effects, boolean hostilesOnly) {
        this.radius = radius;
        this.effects = effects;
        this.hostilesOnly = hostilesOnly;
    }
    
    public AreaEffectEffect(JsonObject config) {
        this.radius = config.has("radius") ? config.get("radius").getAsDouble() : 5.0;
        this.hostilesOnly = config.has("hostiles_only") ? config.get("hostiles_only").getAsBoolean() : true;
        
        this.effects = new ArrayList<>();
        if (config.has("effects") && config.get("effects").isJsonArray()) {
            JsonArray effectsArray = config.getAsJsonArray("effects");
            for (JsonElement effectElement : effectsArray) {
                if (effectElement.isJsonObject()) {
                    JsonObject effectObj = effectElement.getAsJsonObject();
                    String effectId = effectObj.has("id") ? effectObj.get("id").getAsString() : "minecraft:weakness";
                    int duration = effectObj.has("duration") ? effectObj.get("duration").getAsInt() : 60;
                    int amplifier = effectObj.has("amplifier") ? effectObj.get("amplifier").getAsInt() : 0;
                    effects.add(new StatusEffectData(effectId, duration, amplifier));
                }
            }
        }
        
        // If no effects specified, default to weakness
        if (effects.isEmpty()) {
            effects.add(new StatusEffectData("minecraft:weakness", 60, 0));
        }
    }
    
    public AreaEffectEffect(double radius, List<StatusEffectData> effects) {
        this(radius, effects, true); // Default to hostiles only
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        
        // Get center position (pet or owner)
        var centerEntity = context.getPet();
        var centerPos = centerEntity.getEntityPos();
        
        // Create search area
        Box searchBox = Box.of(centerPos, radius * 2, radius * 2, radius * 2);
        
        // Find entities in area
        List<LivingEntity> targets = serverWorld.getEntitiesByClass(
            LivingEntity.class,
            searchBox,
            entity -> {
                if (entity == centerEntity || entity == context.getOwner()) {
                    return false; // Don't affect self or owner
                }
                
                if (hostilesOnly && !(entity instanceof HostileEntity)) {
                    return false; // Only affect hostiles if specified
                }
                
                return entity.squaredDistanceTo(centerEntity) <= radius * radius;
            }
        );
        
        // Apply effects to all targets
        for (LivingEntity target : targets) {
            for (StatusEffectData effectData : effects) {
                applyStatusEffect(target, effectData);
            }
        }
        
        return !targets.isEmpty();
    }
    
    private void applyStatusEffect(LivingEntity target, StatusEffectData effectData) {
        try {
            var statusEffect = Registries.STATUS_EFFECT.get(Identifier.of(effectData.id));
            if (statusEffect != null) {
                StatusEffectInstance instance = new StatusEffectInstance(
                    Registries.STATUS_EFFECT.getEntry(statusEffect),
                    effectData.duration,
                    effectData.amplifier
                );
                target.addStatusEffect(instance);
            }
        } catch (Exception e) {
            // Log error but don't crash
        }
    }
    
    public static class StatusEffectData {
        public final String id;
        public final int duration;
        public final int amplifier;
        
        public StatusEffectData(String id, int duration, int amplifier) {
            this.id = id;
            this.duration = duration;
            this.amplifier = amplifier;
        }
    }
}


