package woflo.petsplus.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
        if (owner.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            StateManager manager = StateManager.getIfLoaded(serverWorld);
            if (manager != null) {
                return manager.getOwnerState(owner);
            }
            if (StateManager.isCreationAllowed(serverWorld)) {
                return StateManager.forWorld(serverWorld).getOwnerState(owner);
            }
        }
        return STATES.computeIfAbsent(owner, OwnerCombatState::new);
    }

    @Nullable
    public static OwnerCombatState get(PlayerEntity owner) {
        if (owner.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            StateManager manager = StateManager.getIfLoaded(serverWorld);
            if (manager != null) {
                OwnerCombatState state = manager.getOwnerStateIfPresent(owner);
                if (state != null) {
                    return state;
                }
            }
        }
        return STATES.get(owner);
    }

    public static void set(PlayerEntity owner, OwnerCombatState state) {
        STATES.put(owner, state);
    }

    public static void remove(PlayerEntity owner) {
        if (owner.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            StateManager manager = StateManager.getIfLoaded(serverWorld);
            if (manager != null) {
                manager.removeOwner(owner);
            }
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
            lastHitTick = owner.getEntityWorld().getTime();
            // Fire aggro-acquired style triggers when combat first starts
            if (owner.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld) {
                woflo.petsplus.events.CombatEventHandler.triggerAbilitiesForOwner(owner, "aggro_acquired");
            }
        }
        updateCombatTimer();
    }
    
    public void updateCombatTimer() {
        long currentTime = owner.getEntityWorld().getTime();
        combatEndTick = currentTime + COMBAT_TIMEOUT;
        
        // Update last action times
        lastHitTick = currentTime;
    }
    
    public void onHitTaken() {
        lastHitTakenTick = owner.getEntityWorld().getTime();
        enterCombat();
        markOwnerInterference(lastHitTakenTick);
    }

    public void tick() {
        long currentTime = owner.getEntityWorld().getTime();

        // Check if combat should end
        if (inCombat && currentTime >= combatEndTick) {
            exitCombat();
        }

        if (owner.getEntityWorld() instanceof ServerWorld serverWorld) {
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
        if (owner.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld) {
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
        return owner.getEntityWorld().getTime() - lastHitTick;
    }
    
    public long getTimeSinceLastHitTaken() {
        return owner.getEntityWorld().getTime() - lastHitTakenTick;
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
     * Convert to Data for serialization.
     */
    public Data toData() {
        return new Data(
            inCombat,
            combatEndTick,
            lastHitTick,
            lastHitTakenTick,
            Optional.ofNullable(activeAggroTargetId),
            activeAggroTargetExpireTick,
            activeAggroAggression,
            activeAggroUrgency,
            activeAggroTargetHostile,
            assistChainCount,
            lastAssistResetTick,
            Map.copyOf(tempState)
        );
    }
    
    /**
     * Load from Data.
     */
    public void fromData(Data data) {
        this.inCombat = data.inCombat();
        this.combatEndTick = data.combatEndTick();
        this.lastHitTick = data.lastHitTick();
        this.lastHitTakenTick = data.lastHitTakenTick();
        this.activeAggroTargetId = data.activeAggroTargetId().orElse(null);
        this.activeAggroTargetExpireTick = data.activeAggroTargetExpireTick();
        this.activeAggroAggression = data.activeAggroAggression();
        this.activeAggroUrgency = data.activeAggroUrgency();
        this.activeAggroTargetHostile = data.activeAggroTargetHostile();
        this.assistChainCount = data.assistChainCount();
        this.lastAssistResetTick = data.lastAssistResetTick();
        this.tempState.clear();
        this.tempState.putAll(data.tempState());
        // Note: Attack riders are not persisted as they are typically short-lived
    }
    
    /**
     * Data record for Codec serialization.
     */
    public record Data(
        boolean inCombat,
        long combatEndTick,
        long lastHitTick,
        long lastHitTakenTick,
        Optional<UUID> activeAggroTargetId,
        long activeAggroTargetExpireTick,
        float activeAggroAggression,
        float activeAggroUrgency,
        boolean activeAggroTargetHostile,
        int assistChainCount,
        long lastAssistResetTick,
        Map<String, Long> tempState
    ) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.BOOL.optionalFieldOf("inCombat", false).forGetter(Data::inCombat),
                Codec.LONG.optionalFieldOf("combatEndTick", 0L).forGetter(Data::combatEndTick),
                Codec.LONG.optionalFieldOf("lastHitTick", 0L).forGetter(Data::lastHitTick),
                Codec.LONG.optionalFieldOf("lastHitTakenTick", 0L).forGetter(Data::lastHitTakenTick),
                Uuids.CODEC.optionalFieldOf("activeAggroTargetId").forGetter(Data::activeAggroTargetId),
                Codec.LONG.optionalFieldOf("activeAggroTargetExpireTick", 0L).forGetter(Data::activeAggroTargetExpireTick),
                Codec.FLOAT.optionalFieldOf("activeAggroAggression", 0f).forGetter(Data::activeAggroAggression),
                Codec.FLOAT.optionalFieldOf("activeAggroUrgency", 0f).forGetter(Data::activeAggroUrgency),
                Codec.BOOL.optionalFieldOf("activeAggroTargetHostile", false).forGetter(Data::activeAggroTargetHostile),
                Codec.INT.optionalFieldOf("assistChainCount", 0).forGetter(Data::assistChainCount),
                Codec.LONG.optionalFieldOf("lastAssistResetTick", 0L).forGetter(Data::lastAssistResetTick),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("tempState", Map.of()).forGetter(Data::tempState)
            ).apply(instance, Data::new)
        );
    }

    public record AggroSnapshot(
        LivingEntity target,
        float aggressionSignal,
        float urgencySignal,
        boolean targetHostile,
        long expireTick
    ) {}
}
