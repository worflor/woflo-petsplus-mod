package woflo.petsplus.roles.striker;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.effects.TagTargetEffect;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.util.BossSafetyUtil;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Striker execution system - provides damage bonuses against low-health enemies.
 */
public class StrikerExecution {
    // Track recent damage stamps per target to validate execution window and ownership
    private static final Map<LivingEntity, DamageStamp> RECENT_DAMAGE = new WeakHashMap<>();
    private record DamageStamp(UUID ownerId, long tick) {}

    // Track short-lived execution momentum stacks per owner for the temporary threshold bonus
    private static final Map<UUID, ExecutionMomentum> EXECUTION_MOMENTUM = new HashMap<>();
    private record ExecutionMomentum(int stacks, long expiresAtTick, int durationTicks) {
        float normalizedFill(long now) {
            if (expiresAtTick <= now) {
                return 0.0f;
            }
            if (durationTicks <= 0) {
                return 1.0f;
            }
            return MathHelper.clamp((expiresAtTick - now) / (float) durationTicks, 0.0f, 1.0f);
        }
    }

    private record MomentumState(int stacks, float fill) {
        static final MomentumState EMPTY = new MomentumState(0, 0.0f);
    }

    private static final double DEFAULT_CHAIN_STACK_BONUS = 0.02; // +2% threshold per stack
    private static final int DEFAULT_CHAIN_MAX_STACKS = 5; // Caps at +10%
    private static final int DEFAULT_CHAIN_DURATION_TICKS = 60; // 3 seconds @20 tps
    private static final double MAX_EXECUTE_THRESHOLD = 0.45; // Hard cap for momentum window

    /** Result returned when evaluating a potential execution. */
    public record ExecutionResult(float bonusDamage,
                                  boolean triggered,
                                  float appliedThresholdPct,
                                  int strikerLevel,
                                  int momentumStacks,
                                  float momentumFill) {
        public float totalDamage(float baseDamage) {
            return baseDamage + bonusDamage;
        }
    }

    private record CachedExecution(UUID ownerId, UUID targetId, ExecutionResult result) {}

    private static final ThreadLocal<CachedExecution> LAST_EXECUTION = new ThreadLocal<>();

    /**
     * Cache the most recent execution result for reuse by fallback systems.
     */
    public static void cacheExecutionResult(PlayerEntity owner, LivingEntity target, ExecutionResult result) {
        if (owner == null || target == null || result == null) {
            LAST_EXECUTION.remove();
            return;
        }

        cacheExecutionResult(owner.getUuid(), target.getUuid(), result);
    }

    /**
     * Consume the cached execution result if it matches the supplied owner/target pair.
     */
    public static ExecutionResult consumeCachedExecutionResult(PlayerEntity owner, LivingEntity target) {
        if (owner == null || target == null) {
            return null;
        }

        return consumeCachedExecutionResult(owner.getUuid(), target.getUuid());
    }

    public static void cacheExecutionResult(UUID ownerId, UUID targetId, ExecutionResult result) {
        if (ownerId == null || targetId == null || result == null) {
            LAST_EXECUTION.remove();
            return;
        }

        LAST_EXECUTION.set(new CachedExecution(ownerId, targetId, result));
    }

    public static ExecutionResult consumeCachedExecutionResult(UUID ownerId, UUID targetId) {
        CachedExecution cached = LAST_EXECUTION.get();
        if (cached == null || ownerId == null || targetId == null) {
            return null;
        }

        if (!cached.ownerId().equals(ownerId) || !cached.targetId().equals(targetId)) {
            return null;
        }

        LAST_EXECUTION.remove();
        return cached.result();
    }

    /**
     * Clear any cached execution result. Primarily intended for testing hooks.
     */
    public static void clearCachedExecutionResult() {
        LAST_EXECUTION.remove();
    }
    
    /**
     * Calculate execution bonus damage for owner attacks.
     * @param attacker The attacking player
     * @param target The target being attacked
     * @param baseDamage The base damage amount
     * @return The additional execution damage to apply
     */
    public static float calculateExecutionBonus(PlayerEntity attacker, LivingEntity target, float baseDamage) {
        return evaluateExecution(attacker, target, baseDamage, true).bonusDamage();
    }

    public static float calculateExecutionBonus(PlayerEntity attacker, LivingEntity target, float baseDamage, boolean applyMomentum) {
        return evaluateExecution(attacker, target, baseDamage, applyMomentum).bonusDamage();
    }

    public static ExecutionResult evaluateExecution(PlayerEntity attacker, LivingEntity target, float baseDamage) {
        return evaluateExecution(attacker, target, baseDamage, true);
    }

    public static ExecutionResult evaluateExecution(PlayerEntity attacker, LivingEntity target, float baseDamage, boolean applyMomentum) {
        if (attacker == null || target == null || baseDamage <= 0) {
            return new ExecutionResult(0.0f, false, 0.0f, 0, 0, 0.0f);
        }
        if (!target.isAlive()) {
            return new ExecutionResult(0.0f, false, 0.0f, 0, 0, 0.0f);
        }

        // Find the strongest nearby Striker pet contributing to the execution
        MobEntity strikerPet = findNearbyStrikerPet(attacker);
        if (strikerPet == null) {
            return new ExecutionResult(0.0f, false, 0.0f, 0, 0, 0.0f);
        }

        PetComponent strikerComponent = PetComponent.get(strikerPet);
        int strikerLevel = strikerComponent != null ? Math.max(1, strikerComponent.getLevel()) : 1;
        float levelProgress = strikerComponent != null
                ? MathHelper.clamp((strikerLevel - 1) / 29.0f, 0.0f, 1.0f)
                : 0.0f;

        // Safety: avoid allies/teammates and bosses
        if (attacker.isTeammate(target)) {
            return new ExecutionResult(0.0f, false, 0.0f, strikerLevel, 0, 0.0f);
        }
        if (BossSafetyUtil.isBossEntity(target)) {
            return new ExecutionResult(0.0f, false, 0.0f, strikerLevel, 0, 0.0f);
        }

        PetsPlusConfig config = PetsPlusConfig.getInstance();
        double baseThreshold = clamp01(config.getRoleDouble(PetRoleType.STRIKER.id(), "executeThresholdPct", 0.35));
        baseThreshold = Math.min(baseThreshold, MAX_EXECUTE_THRESHOLD);

        double chainBonusPerStack = clamp01(config.getRoleDouble(PetRoleType.STRIKER.id(), "executeChainBonusPerStackPct", DEFAULT_CHAIN_STACK_BONUS));
        double leveledBonusPerStack = Math.min(MAX_EXECUTE_THRESHOLD,
                chainBonusPerStack * (1.0 + 0.25 * levelProgress));
        int chainMaxStacks = Math.max(0, config.getRoleInt(PetRoleType.STRIKER.id(), "executeChainMaxStacks", DEFAULT_CHAIN_MAX_STACKS));
        int baseChainDurationTicks = Math.max(1, config.getRoleInt(PetRoleType.STRIKER.id(), "executeChainDurationTicks", DEFAULT_CHAIN_DURATION_TICKS));
        int leveledChainDurationTicks = Math.max(baseChainDurationTicks,
                (int) Math.round(baseChainDurationTicks * (1.0 + 0.25 * levelProgress)));

        MomentumState momentumState = chainMaxStacks > 0 ? getMomentumState(attacker) : MomentumState.EMPTY;
        int activeStacks = chainMaxStacks > 0 ? Math.min(chainMaxStacks, momentumState.stacks()) : 0;
        float momentumFill = chainMaxStacks > 0 ? momentumState.fill() : 0.0f;

        double appliedThreshold = computeAppliedThreshold(baseThreshold, leveledBonusPerStack, chainMaxStacks, activeStacks);

        float targetHealthPercent = Math.max(0f, target.getHealth()) / Math.max(1f, target.getMaxHealth());
        if (targetHealthPercent > (float) appliedThreshold) {
            return new ExecutionResult(0.0f, false, (float) appliedThreshold, strikerLevel, activeStacks, momentumFill);
        }

        // Check if target has been recently damaged by this owner (or their pet)
        if (!hasRecentDamageFromOwnerOrPet(target, attacker)) {
            return new ExecutionResult(0.0f, false, (float) appliedThreshold, strikerLevel, activeStacks, momentumFill);
        }

        float effectiveHealth = Math.max(0f, target.getHealth()) + Math.max(0f, target.getAbsorptionAmount());
        if (effectiveHealth <= 0f) {
            return new ExecutionResult(0.0f, false, (float) appliedThreshold, strikerLevel, activeStacks, momentumFill);
        }

        // Return enough bonus damage to finish the target outright without heavy overkill.
        float bonusDamage = Math.max(0f, effectiveHealth - Math.max(0f, baseDamage));

        MomentumState resultingState = momentumState;
        if (applyMomentum && chainMaxStacks > 0) {
            resultingState = incrementMomentumStacks(attacker, chainMaxStacks, leveledChainDurationTicks);
        }
        int resultingStacks = chainMaxStacks > 0 ? resultingState.stacks() : 0;
        float resultingFill = chainMaxStacks > 0 ? resultingState.fill() : momentumFill;

        if (applyMomentum) {
            double nextThreshold = computeAppliedThreshold(baseThreshold, leveledBonusPerStack, chainMaxStacks, resultingStacks);
            Petsplus.LOGGER.debug("Striker execution triggered: L{} executed {} at {}% HP (threshold {}% -> next {}% | chain stacks: {} -> +{}%)",
                    strikerLevel,
                    target.getName().getString(),
                    String.format(Locale.ROOT, "%.1f", targetHealthPercent * 100f),
                    String.format(Locale.ROOT, "%.1f", appliedThreshold * 100.0),
                    String.format(Locale.ROOT, "%.1f", nextThreshold * 100.0),
                    resultingStacks,
                    String.format(Locale.ROOT, "%.1f", Math.max(0, nextThreshold - baseThreshold) * 100.0));
        }

        return new ExecutionResult(bonusDamage, true, (float) appliedThreshold, strikerLevel, resultingStacks, resultingFill);
    }
    
    /**
     * Handle owner dealing damage to trigger Striker abilities.
     */
    public static void onOwnerDealDamage(PlayerEntity owner, LivingEntity victim, float damage) {
        if (!(owner.getWorld() instanceof ServerWorld)) return;
        // Update combat state
        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);
        combatState.enterCombat();
        // Mark target as recently damaged (used to gate execution bonus on subsequent hits)
        markRecentDamage(victim, owner);
    }
    
    /**
     * Check if the target has the finisher mark applied.
     */
    public static boolean hasFinisherMark(LivingEntity target) {
        return TagTargetEffect.hasTag(target, "petsplus:finisher");
    }
    
    /**
     * Apply finisher mark effects when owner attacks a marked target.
     */
    public static void onAttackFinisherMark(PlayerEntity owner, LivingEntity target, float damage) {
        if (!hasFinisherMark(target)) return;
        
        // Remove the mark after use
        TagTargetEffect.removeTag(target, "petsplus:finisher");
        
        // Apply bonus damage and effects (handled by OwnerNextAttackBonusEffect)
        double bonusPercent = PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.STRIKER.id(), "finisherMarkBonusPct", 0.20);
        
        Petsplus.LOGGER.debug("Finisher mark triggered: {}% bonus damage against {}", 
                             bonusPercent * 100, target.getName().getString());
    }
    
    private static MobEntity findNearbyStrikerPet(PlayerEntity owner) {
        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }
        
        // Search for Striker pets within 16 blocks and pick the nearest valid one
        double searchRadius = 16.0;
        return serverWorld.getEntitiesByClass(
                MobEntity.class,
                owner.getBoundingBox().expand(searchRadius),
                entity -> {
                    PetComponent component = PetComponent.get(entity);
                    return component != null &&
                           component.hasRole(PetRoleType.STRIKER) &&
                           entity.isAlive() &&
                           component.isOwnedBy(owner);
                }
        ).stream().max(
                Comparator
                        .comparingInt((MobEntity entity) -> {
                            PetComponent component = PetComponent.get(entity);
                            return component != null ? component.getLevel() : 0;
                        })
                        .thenComparingDouble(entity -> -entity.squaredDistanceTo(owner))
        ).orElse(null);
    }
    
    private static boolean hasRecentDamageFromOwnerOrPet(LivingEntity target, PlayerEntity owner) {
        DamageStamp stamp = RECENT_DAMAGE.get(target);
        if (stamp == null) return false;
        if (owner == null || owner.getWorld() == null) return false;
        long now = owner.getWorld().getTime();
        long window = Math.max(1, (long) PetsPlusConfig.getInstance().getRoleInt(PetRoleType.STRIKER.id(), "recentDamageWindowTicks", 100));
        return owner.getUuid().equals(stamp.ownerId()) && (now - stamp.tick()) <= window;
    }

    /**
     * Public accessor for recent-damage validation to keep fallback and other systems consistent.
     */
    public static boolean hasRecentDamageWindow(LivingEntity target, PlayerEntity owner) {
        return hasRecentDamageFromOwnerOrPet(target, owner);
    }

    /**
     * Public method to mark recent owner damage (for central event flow).
     */
    public static void noteOwnerDamage(LivingEntity target, PlayerEntity owner) {
        markRecentDamage(target, owner);
    }
    
    private static void markRecentDamage(LivingEntity target, PlayerEntity owner) {
        if (owner == null || target == null || owner.getWorld() == null) return;
        RECENT_DAMAGE.put(target, new DamageStamp(owner.getUuid(), owner.getWorld().getTime()));
    }

    private static MomentumState getMomentumState(PlayerEntity owner) {
        if (owner == null) {
            return MomentumState.EMPTY;
        }

        ExecutionMomentum momentum = EXECUTION_MOMENTUM.get(owner.getUuid());
        if (momentum == null) {
            return MomentumState.EMPTY;
        }

        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return new MomentumState(momentum.stacks(), 1.0f);
        }

        long now = serverWorld.getTime();
        if (momentum.expiresAtTick() <= now) {
            if (momentum.stacks() > 1) {
                int downgradedStacks = momentum.stacks() - 1;
                long expiresAt = now + Math.max(1, momentum.durationTicks());
                ExecutionMomentum downgraded = new ExecutionMomentum(downgradedStacks, expiresAt, momentum.durationTicks());
                EXECUTION_MOMENTUM.put(owner.getUuid(), downgraded);
                return new MomentumState(downgraded.stacks(), downgraded.normalizedFill(now));
            }
            EXECUTION_MOMENTUM.remove(owner.getUuid());
            return MomentumState.EMPTY;
        }

        return new MomentumState(momentum.stacks(), momentum.normalizedFill(now));
    }

    private static int getActiveMomentumStacks(PlayerEntity owner) {
        return getMomentumState(owner).stacks();
    }

    private static MomentumState incrementMomentumStacks(PlayerEntity owner, int maxStacks, int durationTicks) {
        if (owner == null || maxStacks <= 0 || durationTicks <= 0) {
            return MomentumState.EMPTY;
        }

        if (!(owner.getWorld() instanceof ServerWorld serverWorld)) {
            return MomentumState.EMPTY;
        }

        long now = serverWorld.getTime();
        ExecutionMomentum momentum = EXECUTION_MOMENTUM.get(owner.getUuid());
        int stacks;
        if (momentum != null && momentum.expiresAtTick() > now) {
            stacks = Math.min(maxStacks, momentum.stacks() + 1);
        } else {
            stacks = 1;
        }

        long expiresAt = now + Math.max(1, durationTicks);
        ExecutionMomentum updated = new ExecutionMomentum(stacks, expiresAt, durationTicks);
        EXECUTION_MOMENTUM.put(owner.getUuid(), updated);
        return new MomentumState(updated.stacks(), updated.normalizedFill(now));
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    private static double computeAppliedThreshold(double baseThreshold, double perStackBonus, int chainMaxStacks, int stacks) {
        double sanitizedBase = clamp01(Math.min(baseThreshold, MAX_EXECUTE_THRESHOLD));
        if (perStackBonus <= 0 || chainMaxStacks <= 0 || stacks <= 0) {
            return sanitizedBase;
        }

        double capacity = Math.max(0.0, MAX_EXECUTE_THRESHOLD - sanitizedBase);
        if (capacity <= 0.0) {
            return sanitizedBase;
        }

        double effectivePerStack = Math.min(perStackBonus, capacity);
        double appliedStacks = Math.min(stacks, chainMaxStacks);
        double bonus = Math.min(capacity, appliedStacks * effectivePerStack);
        return clamp01(sanitizedBase + bonus);
    }
}
