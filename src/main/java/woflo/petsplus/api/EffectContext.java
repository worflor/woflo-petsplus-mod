package woflo.petsplus.api;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Context object containing all relevant data for effect execution.
 */
public class EffectContext {
    private final ServerWorld world;
    private final MobEntity pet;
    private final PlayerEntity owner;
    private final TriggerContext triggerContext;
    private final Map<String, Object> effectData;
    
    public EffectContext(ServerWorld world, MobEntity pet, PlayerEntity owner, TriggerContext triggerContext) {
        this.world = world;
        this.pet = pet;
        this.owner = owner;
        this.triggerContext = triggerContext;
        this.effectData = new HashMap<>();
    }
    
    public ServerWorld getWorld() {
        return world;
    }
    
    public MobEntity getPet() {
        return pet;
    }
    
    public PlayerEntity getOwner() {
        return owner;
    }
    
    public TriggerContext getTriggerContext() {
        return triggerContext;
    }
    
    public Map<String, Object> getEffectData() {
        return effectData;
    }
    
    public EffectContext withData(String key, Object value) {
        this.effectData.put(key, value);
        return this;
    }

    public boolean hasDamageContext() {
        return triggerContext.hasDamageContext();
    }

    @Nullable
    public DamageSource getIncomingDamageSource() {
        return triggerContext.getIncomingDamageSource();
    }

    public double getIncomingDamageAmount() {
        return triggerContext.getIncomingDamageAmount();
    }

    public boolean isLethalDamage() {
        return triggerContext.isLethalDamage();
    }

    @Nullable
    public DamageInterceptionResult getDamageResult() {
        return triggerContext.getDamageResult();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getData(String key, Class<T> type) {
        Object value = effectData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    @Nullable
    public Entity getTarget() {
        return getData("target", Entity.class);
    }
    
    @Nullable
    public Entity getMount() {
        if (owner.getVehicle() != null) {
            return owner.getVehicle();
        }
        return null;
    }
}