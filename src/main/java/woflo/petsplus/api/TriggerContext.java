package woflo.petsplus.api;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Context object containing all relevant data for trigger evaluation.
 */
public class TriggerContext {
    private final ServerWorld world;
    private final MobEntity pet;
    private final PlayerEntity owner;
    private final String eventType;
    private final Map<String, Object> eventData;
    
    public TriggerContext(ServerWorld world, MobEntity pet, PlayerEntity owner, String eventType) {
        this.world = world;
        this.pet = pet;
        this.owner = owner;
        this.eventType = eventType;
        this.eventData = new HashMap<>();
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
    
    public String getEventType() {
        return eventType;
    }
    
    public Map<String, Object> getEventData() {
        return eventData;
    }
    
    public TriggerContext withData(String key, Object value) {
        this.eventData.put(key, value);
        return this;
    }
    
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getData(String key, Class<T> type) {
        Object value = eventData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    @Nullable
    public Entity getVictim() {
        return getData("victim", Entity.class);
    }
    
    public double getDamage() {
        Double damage = getData("damage", Double.class);
        return damage != null ? damage : 0.0;
    }
    
    public double getVictimHpPercent() {
        Double hpPercent = getData("victim_hp_pct", Double.class);
        return hpPercent != null ? hpPercent : 1.0;
    }
    
    public double getFallDistance() {
        Double fallDistance = getData("fall_distance", Double.class);
        return fallDistance != null ? fallDistance : 0.0;
    }
}