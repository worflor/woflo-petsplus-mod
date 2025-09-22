package woflo.petsplus.roles.striker;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.Petsplus;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.effects.TagTargetEffect;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.util.BossSafetyUtil;

import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

/**
 * Striker execution system - provides damage bonuses against low-health enemies.
 */
public class StrikerExecution {
    // Track recent damage stamps per target to validate execution window and ownership
    private static final Map<LivingEntity, DamageStamp> RECENT_DAMAGE = new WeakHashMap<>();
    private record DamageStamp(UUID ownerId, long tick) {}
    
    /**
     * Calculate execution bonus damage for owner attacks.
     * @param attacker The attacking player
     * @param target The target being attacked
     * @param baseDamage The base damage amount
     * @return The additional execution damage to apply
     */
    public static float calculateExecutionBonus(PlayerEntity attacker, LivingEntity target, float baseDamage) {
        if (attacker == null || target == null || baseDamage <= 0) return 0.0f;
        if (!target.isAlive()) return 0.0f;

        // Find nearby Striker pet
        MobEntity strikerPet = findNearbyStrikerPet(attacker);
        if (strikerPet == null) {
            return 0.0f;
        }
        
        // Safety: avoid allies/teammates and bosses
        if (attacker.isTeammate(target)) return 0.0f;
        if (BossSafetyUtil.isBossEntity(target)) return 0.0f;

        // Configurable execution threshold
        double threshold = clamp01(PetsPlusConfig.getInstance().getDouble("striker", "executeThresholdPct", 0.35));
        float targetHealthPercent = Math.max(0f, target.getHealth()) / Math.max(1f, target.getMaxHealth());
        if (targetHealthPercent > (float) threshold) {
            return 0.0f; // Target not low enough for execution
        }
        
        // Check if target has been recently damaged by this owner (or their pet)
        if (!hasRecentDamageFromOwnerOrPet(target, attacker)) {
            return 0.0f;
        }
        
        // Calculate execution bonus
        double bonusPercent = PetsPlusConfig.getInstance().getDouble("striker", "ownerExecuteBonusPct", 0.10);
        float bonusDamage = (float) (baseDamage * bonusPercent);
        
        Petsplus.LOGGER.debug("Striker execution bonus: {}% extra damage ({}) against {}", 
                             bonusPercent * 100, bonusDamage, target.getName().getString());
        
        return bonusDamage;
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
        double bonusPercent = PetsPlusConfig.getInstance().getDouble("striker", "finisherMarkBonusPct", 0.20);
        
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
                           component.getRole() != null &&
                           component.getRole().equals(woflo.petsplus.api.PetRole.STRIKER) &&
                           entity.isAlive() &&
                           component.isOwnedBy(owner);
                }
        ).stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(owner))).orElse(null);
    }
    
    private static boolean hasRecentDamageFromOwnerOrPet(LivingEntity target, PlayerEntity owner) {
        DamageStamp stamp = RECENT_DAMAGE.get(target);
        if (stamp == null) return false;
        if (owner == null || owner.getWorld() == null) return false;
        long now = owner.getWorld().getTime();
        long window = Math.max(1, (long) PetsPlusConfig.getInstance().getInt("striker", "recentDamageWindowTicks", 100));
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

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}