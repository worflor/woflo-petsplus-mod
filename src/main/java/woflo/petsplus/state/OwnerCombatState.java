package woflo.petsplus.state;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Manages temporary state for pet owners including combat status and attack riders.
 */
public class OwnerCombatState {
    private static final Map<PlayerEntity, OwnerCombatState> STATES = new WeakHashMap<>();
    
    private final PlayerEntity owner;
    private boolean inCombat;
    private long combatEndTick;
    private long lastHitTick;
    private long lastHitTakenTick;
    private final Map<String, Object> nextAttackRiders;
    private final Map<String, Long> tempState;
    
    // Combat timeout in ticks (6 seconds)
    private static final int COMBAT_TIMEOUT = 120;
    
    public OwnerCombatState(PlayerEntity owner) {
        this.owner = owner;
        this.inCombat = false;
        this.combatEndTick = 0;
        this.lastHitTick = 0;
        this.lastHitTakenTick = 0;
        this.nextAttackRiders = new HashMap<>();
        this.tempState = new HashMap<>();
    }
    
    public static OwnerCombatState getOrCreate(PlayerEntity owner) {
        if (owner.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            return StateManager.forWorld(serverWorld).getOwnerState(owner);
        }
        return STATES.computeIfAbsent(owner, OwnerCombatState::new);
    }
    
    @Nullable
    public static OwnerCombatState get(PlayerEntity owner) {
        if (owner.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            StateManager manager = StateManager.forWorld(serverWorld);
            return manager.getAllOwnerStates().get(owner);
        }
        return STATES.get(owner);
    }
    
    public static void set(PlayerEntity owner, OwnerCombatState state) {
        STATES.put(owner, state);
    }
    
    public static void remove(PlayerEntity owner) {
        if (owner.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            StateManager.forWorld(serverWorld).removeOwner(owner);
        }
        STATES.remove(owner);
    }
    
    public PlayerEntity getOwner() {
        return owner;
    }
    
    public boolean isInCombat() {
        return inCombat;
    }
    
    public void enterCombat() {
        if (!inCombat) {
            inCombat = true;
            lastHitTick = owner.getWorld().getTime();
            // Fire aggro-acquired style triggers when combat first starts
            if (owner.getWorld() instanceof net.minecraft.server.world.ServerWorld) {
                woflo.petsplus.events.CombatEventHandler.triggerAbilitiesForOwner(owner, "aggro_acquired");
            }
        }
        updateCombatTimer();
    }
    
    public void updateCombatTimer() {
        long currentTime = owner.getWorld().getTime();
        combatEndTick = currentTime + COMBAT_TIMEOUT;
        
        // Update last action times
        lastHitTick = currentTime;
    }
    
    public void onHitTaken() {
        lastHitTakenTick = owner.getWorld().getTime();
        enterCombat();
    }
    
    public void tick() {
        long currentTime = owner.getWorld().getTime();
        
        // Check if combat should end
        if (inCombat && currentTime >= combatEndTick) {
            exitCombat();
        }
        
        // Clean up expired temporary state
        cleanupExpiredTempState(currentTime);
        
        // Clean up expired attack riders
        cleanupExpiredAttackRiders(currentTime);
    }
    
    private void exitCombat() {
        if (inCombat) {
            inCombat = false;
            
            // Trigger combat end events
            triggerCombatEndAbilities();
            
            // Clear combat-specific temporary state
            clearCombatState();
        }
    }
    
    private void triggerCombatEndAbilities() {
        // This would trigger "on_combat_end" abilities for nearby pets
        if (owner.getWorld() instanceof net.minecraft.server.world.ServerWorld) {
            woflo.petsplus.events.CombatEventHandler.triggerAbilitiesForOwner(owner, "on_combat_end");
        }
    }
    
    private void clearCombatState() {
        // Clear any combat-specific riders and state
        nextAttackRiders.clear();
        
        // Keep some state for post-combat analysis but clear temporary effects
        tempState.entrySet().removeIf(entry -> 
            entry.getKey().startsWith("combat_") || 
            entry.getKey().startsWith("temp_")
        );
    }
    
    private void cleanupExpiredTempState(long currentTime) {
        // Remove expired temporary state entries
        tempState.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            if (key.endsWith("_end")) {
                return currentTime >= entry.getValue();
            }
            return false;
        });
    }
    
    private void cleanupExpiredAttackRiders(long currentTime) {
        // Clean up expired attack riders
        woflo.petsplus.combat.OwnerAttackRider.clearExpiredRiders(owner);
    }
    
    public void addNextAttackRider(String key, Object data) {
        nextAttackRiders.put(key, data);
    }
    
    public boolean hasNextAttackRider(String key) {
        return nextAttackRiders.containsKey(key);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getNextAttackRider(String key, Class<T> type) {
        Object value = nextAttackRiders.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    public void clearNextAttackRider(String key) {
        nextAttackRiders.remove(key);
    }
    
    public void clearAllNextAttackRiders() {
        nextAttackRiders.clear();
    }
    
    public void setTempState(String key, long value) {
        tempState.put(key, value);
    }
    
    public long getTempState(String key) {
        return tempState.getOrDefault(key, 0L);
    }

    public void clearTempState(String key) {
        tempState.remove(key);
    }

    public boolean hasTempState(String key) {
        return tempState.containsKey(key);
    }
    
    public long getLastHitTick() {
        return lastHitTick;
    }
    
    public long getLastHitTakenTick() {
        return lastHitTakenTick;
    }
    
    public long getTimeSinceLastHit() {
        return owner.getWorld().getTime() - lastHitTick;
    }
    
    public long getTimeSinceLastHitTaken() {
        return owner.getWorld().getTime() - lastHitTakenTick;
    }
    
    /**
     * Check if owner is mounted on any vehicle.
     */
    public boolean isMounted() {
        return owner.getVehicle() != null;
    }
    
    /**
     * Apply a buff to the owner with duration.
     */
    public void applyBuff(StatusEffectInstance effect) {
        owner.addStatusEffect(effect);
    }
    
    /**
     * Serialize state to NBT for persistence.
     */
    public void writeToNbt(NbtCompound nbt) {
        nbt.putBoolean("inCombat", inCombat);
        nbt.putLong("combatEndTick", combatEndTick);
        nbt.putLong("lastHitTick", lastHitTick);
        nbt.putLong("lastHitTakenTick", lastHitTakenTick);
        
        // Save attack riders (keys only, since they'll be re-registered)
        NbtCompound ridersNbt = new NbtCompound();
        for (var entry : nextAttackRiders.entrySet()) {
            ridersNbt.putString(entry.getKey(), entry.getValue().getClass().getSimpleName());
        }
        nbt.put("nextAttackRiders", ridersNbt);
        
        // Save temporary state
        NbtCompound tempNbt = new NbtCompound();
        for (var entry : tempState.entrySet()) {
            tempNbt.putLong(entry.getKey(), entry.getValue());
        }
        nbt.put("tempState", tempNbt);
    }
    
    /**
     * Deserialize state from NBT.
     */
    public void readFromNbt(NbtCompound nbt) {
        if (nbt.contains("inCombat")) {
            nbt.getBoolean("inCombat").ifPresent(value -> this.inCombat = value);
        }
        if (nbt.contains("combatEndTick")) {
            nbt.getLong("combatEndTick").ifPresent(value -> this.combatEndTick = value);
        }
        if (nbt.contains("lastHitTick")) {
            nbt.getLong("lastHitTick").ifPresent(value -> this.lastHitTick = value);
        }
        if (nbt.contains("lastHitTakenTick")) {
            nbt.getLong("lastHitTakenTick").ifPresent(value -> this.lastHitTakenTick = value);
        }
        
        // Load temporary state
        if (nbt.contains("tempState")) {
            nbt.getCompound("tempState").ifPresent(tempNbt -> {
                tempState.clear();
                for (String key : tempNbt.getKeys()) {
                    tempNbt.getLong(key).ifPresent(value -> tempState.put(key, value));
                }
            });
        }
        
        // Note: Attack riders are not persisted as they are typically short-lived
        // and will be re-applied when triggers fire again
    }
}