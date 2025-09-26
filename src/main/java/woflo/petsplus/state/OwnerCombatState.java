package woflo.petsplus.state;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

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
    private @Nullable UUID activeAggroTargetId;
    private long activeAggroTargetExpireTick;
    private int assistChainCount;
    private long lastAssistResetTick;
    private float activeAggroAggression;
    private float activeAggroUrgency;
    private boolean activeAggroTargetHostile;

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
        this.activeAggroTargetId = null;
        this.activeAggroTargetExpireTick = 0L;
        this.assistChainCount = 0;
        this.lastAssistResetTick = 0L;
        this.activeAggroAggression = 0f;
        this.activeAggroUrgency = 0f;
        this.activeAggroTargetHostile = false;
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
        markOwnerInterference(lastHitTakenTick);
    }

    public void tick() {
        long currentTime = owner.getWorld().getTime();

        // Check if combat should end
        if (inCombat && currentTime >= combatEndTick) {
            exitCombat();
        }

        if (owner.getWorld() instanceof ServerWorld serverWorld) {
            pruneAggroTarget(currentTime, serverWorld);
        } else if (currentTime >= activeAggroTargetExpireTick) {
            clearAggroTarget();
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
            clearAggroTarget();
        }
    }

    private void pruneAggroTarget(long now, ServerWorld world) {
        if (activeAggroTargetId == null) {
            return;
        }

        if (now >= activeAggroTargetExpireTick) {
            clearAggroTarget();
            return;
        }

        LivingEntity resolved = resolveAggroTarget(world);
        if (resolved == null || !resolved.isAlive() || resolved.isRemoved()) {
            clearAggroTarget();
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

    public void rememberAggroTarget(LivingEntity target, long now, int ttlTicks) {
        boolean hostile = target instanceof HostileEntity;
        rememberAggroTarget(target, now, ttlTicks, true,
            hostile ? 0.65f : 0.45f,
            hostile ? 0.45f : 0.20f,
            hostile);
    }

    public void rememberAggroTarget(LivingEntity target, long now, int ttlTicks, boolean resetChain) {
        boolean hostile = target instanceof HostileEntity;
        rememberAggroTarget(target, now, ttlTicks, resetChain,
            hostile ? 0.65f : 0.45f,
            hostile ? 0.45f : 0.20f,
            hostile);
    }

    public void rememberAggroTarget(
        LivingEntity target,
        long now,
        int ttlTicks,
        boolean resetChain,
        float aggressionSignal,
        float urgencySignal,
        boolean targetHostile
    ) {
        if (target == null) {
            clearAggroTarget();
            return;
        }

        this.activeAggroTargetId = target.getUuid();
        this.activeAggroTargetExpireTick = now + Math.max(1, ttlTicks);
        this.activeAggroAggression = MathHelper.clamp(aggressionSignal, 0f, 1f);
        this.activeAggroUrgency = MathHelper.clamp(urgencySignal, 0f, 1f);
        this.activeAggroTargetHostile = targetHostile;
        if (resetChain) {
            this.assistChainCount = 0;
            this.lastAssistResetTick = now;
        }
    }

    public void clearAggroTarget() {
        this.activeAggroTargetId = null;
        this.activeAggroTargetExpireTick = 0L;
        this.activeAggroAggression = 0f;
        this.activeAggroUrgency = 0f;
        this.activeAggroTargetHostile = false;
    }

    public @Nullable LivingEntity resolveAggroTarget(ServerWorld world) {
        if (activeAggroTargetId == null) {
            return null;
        }
        var entity = world.getEntity(activeAggroTargetId);
        if (entity instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    public @Nullable AggroSnapshot getAggroSnapshot(ServerWorld world) {
        LivingEntity target = resolveAggroTarget(world);
        if (target == null) {
            return null;
        }
        return new AggroSnapshot(target, activeAggroAggression, activeAggroUrgency, activeAggroTargetHostile, activeAggroTargetExpireTick);
    }

    public float getActiveAggroAggression() {
        return activeAggroAggression;
    }

    public float getActiveAggroUrgency() {
        return activeAggroUrgency;
    }

    public boolean isActiveAggroTargetHostile() {
        return activeAggroTargetHostile;
    }

    public boolean hasAggroTarget() {
        return activeAggroTargetId != null;
    }

    public boolean isAggroTarget(UUID uuid) {
        return activeAggroTargetId != null && activeAggroTargetId.equals(uuid);
    }

    public void extendAggroIfMatching(LivingEntity target, long now, int ttlTicks) {
        if (target == null || activeAggroTargetId == null) {
            return;
        }
        if (activeAggroTargetId.equals(target.getUuid())) {
            activeAggroTargetExpireTick = now + Math.max(1, ttlTicks);
        }
    }

    public void extendAggroIfMatching(
        LivingEntity target,
        long now,
        int ttlTicks,
        float aggressionSignal,
        float urgencySignal,
        boolean targetHostile
    ) {
        if (target == null || activeAggroTargetId == null) {
            return;
        }
        if (!activeAggroTargetId.equals(target.getUuid())) {
            return;
        }
        activeAggroTargetExpireTick = now + Math.max(1, ttlTicks);
        this.activeAggroAggression = MathHelper.clamp(Math.max(activeAggroAggression * 0.75f, aggressionSignal), 0f, 1f);
        this.activeAggroUrgency = MathHelper.clamp(Math.max(activeAggroUrgency * 0.7f, urgencySignal), 0f, 1f);
        this.activeAggroTargetHostile = this.activeAggroTargetHostile || targetHostile;
    }

    public int getAssistChainCount() {
        return assistChainCount;
    }

    public boolean canChainAssist(int chainLimit) {
        return assistChainCount < chainLimit;
    }

    public void incrementAssistChain(long now) {
        assistChainCount++;
        activeAggroTargetExpireTick = Math.max(activeAggroTargetExpireTick, now);
    }

    public void resetAssistChain(long now) {
        assistChainCount = 0;
        lastAssistResetTick = now;
    }

    public void markOwnerInterference(long now) {
        assistChainCount = 0;
        lastAssistResetTick = now;
    }

    public long getLastAssistResetTick() {
        return lastAssistResetTick;
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

    /** Returns true if the owner took damage within the last given window ticks. */
    public boolean recentlyDamaged(long now, int windowTicks) {
        return now - lastHitTakenTick <= windowTicks;
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
        if (activeAggroTargetId != null) {
            nbt.putString("activeAggroTargetId", activeAggroTargetId.toString());
        } else {
            nbt.remove("activeAggroTargetId");
        }
        nbt.putLong("activeAggroTargetExpireTick", activeAggroTargetExpireTick);
        nbt.putFloat("activeAggroAggression", activeAggroAggression);
        nbt.putFloat("activeAggroUrgency", activeAggroUrgency);
        nbt.putBoolean("activeAggroTargetHostile", activeAggroTargetHostile);
        nbt.putInt("assistChainCount", assistChainCount);
        nbt.putLong("lastAssistResetTick", lastAssistResetTick);

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
        if (nbt.contains("activeAggroTargetId")) {
            nbt.getString("activeAggroTargetId").ifPresent(value -> {
                try {
                    this.activeAggroTargetId = UUID.fromString(value);
                } catch (IllegalArgumentException e) {
                    this.activeAggroTargetId = null;
                }
            });
        } else {
            this.activeAggroTargetId = null;
        }
        if (nbt.contains("activeAggroTargetExpireTick")) {
            nbt.getLong("activeAggroTargetExpireTick").ifPresent(value -> this.activeAggroTargetExpireTick = value);
        }
        if (nbt.contains("activeAggroAggression")) {
            nbt.getFloat("activeAggroAggression").ifPresent(value -> this.activeAggroAggression = MathHelper.clamp(value, 0f, 1f));
        }
        if (nbt.contains("activeAggroUrgency")) {
            nbt.getFloat("activeAggroUrgency").ifPresent(value -> this.activeAggroUrgency = MathHelper.clamp(value, 0f, 1f));
        }
        if (nbt.contains("activeAggroTargetHostile")) {
            nbt.getBoolean("activeAggroTargetHostile").ifPresent(value -> this.activeAggroTargetHostile = value);
        }
        if (nbt.contains("assistChainCount")) {
            nbt.getInt("assistChainCount").ifPresent(value -> this.assistChainCount = value);
        }
        if (nbt.contains("lastAssistResetTick")) {
            nbt.getLong("lastAssistResetTick").ifPresent(value -> this.lastAssistResetTick = value);
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

    public record AggroSnapshot(
        LivingEntity target,
        float aggressionSignal,
        float urgencySignal,
        boolean targetHostile,
        long expireTick
    ) {}
}