package woflo.petsplus.ai.goals.world;

import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.api.registry.PetRoleType;

import java.util.EnumSet;
import java.util.List;

/**
 * Scout-specific: Scurries toward nearby dropped items or XP orbs.
 * Adds personality—scout actively seeks loot rather than passively magnetizing.
 * 
 * Triggers when:
 * - Scout is idle or wandering
 * - Valuable loot (items or XP) detected within range
 * - Not in combat or following owner urgently
 */
public class ScoutLootScurryGoal extends AdaptiveGoal {
    private static final double DETECTION_RADIUS = 8.0;
    private static final double PICKUP_DISTANCE_SQ = 2.25; // 1.5 blocks
    private static final double MAX_OWNER_DISTANCE_SQ = 16.0 * 16.0; // Don't stray beyond 16 blocks
    
    private Object targetLoot; // ItemEntity or ExperienceOrbEntity
    private int recheckCooldown;
    private int stuckTicks;
    private double lastDistanceSq;
    
    public ScoutLootScurryGoal(MobEntity mob, GoalDefinition goalDefinition) {
        super(mob, goalDefinition, EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        // Only for Scout role
        if (petComponent == null || !petComponent.hasRole(PetRoleType.SCOUT)) {
            return false;
        }
        
        // Don't interrupt combat or urgent following
        if (mob.getTarget() != null || petComponent.isInCombat()) {
            return false;
        }
        
        // Don't run if too far from owner
        if (petComponent.getOwner() != null) {
            double ownerDistSq = mob.squaredDistanceTo(petComponent.getOwner());
            if (ownerDistSq > MAX_OWNER_DISTANCE_SQ) {
                return false; // Too far, let follow goal take over
            }
        }
        
        // Throttle detection checks
        if (recheckCooldown > 0) {
            recheckCooldown--;
            return false;
        }
        
        // Find nearby loot
        targetLoot = findNearestLoot();
        if (targetLoot == null) {
            recheckCooldown = 20; // Check again in 1 second
            return false;
        }
        
        return true;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        if (targetLoot == null) {
            return false;
        }
        
        // Stop if owner too far - safety check
        if (petComponent.getOwner() != null) {
            double ownerDistSq = mob.squaredDistanceTo(petComponent.getOwner());
            if (ownerDistSq > MAX_OWNER_DISTANCE_SQ * 1.5) { // Give some leeway
                return false;
            }
        }
        
        // Stop if loot disappeared
        if (targetLoot instanceof ItemEntity item && (item.isRemoved() || !item.isAlive())) {
            return false;
        }
        if (targetLoot instanceof ExperienceOrbEntity orb && (orb.isRemoved() || !orb.isAlive())) {
            return false;
        }
        
        // Stop if reached loot
        double distSq = mob.squaredDistanceTo(
            targetLoot instanceof ItemEntity item ? item : (ExperienceOrbEntity) targetLoot
        );
        if (distSq < PICKUP_DISTANCE_SQ) {
            return false;
        }
        
        // Detect if stuck
        if (Math.abs(distSq - lastDistanceSq) < 0.01) {
            stuckTicks++;
            if (stuckTicks > 40) { // Stuck for 2 seconds
                return false;
            }
        } else {
            stuckTicks = 0;
        }
        lastDistanceSq = distSq;
        
        // Stop if combat starts
        return mob.getTarget() == null && !petComponent.isInCombat();
    }
    
    @Override
    protected void onStartGoal() {
        stuckTicks = 0;
        lastDistanceSq = Double.MAX_VALUE;
        
        // Subtle excitement particle (single enchant sparkle)
        if (mob.getEntityWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(
                net.minecraft.particle.ParticleTypes.ENCHANT,
                mob.getX(), mob.getY() + 0.4, mob.getZ(),
                1, 0.1, 0.1, 0.1, 0.02
            );
        }
    }
    
    @Override
    protected void onStopGoal() {
        targetLoot = null;
        recheckCooldown = 30; // Brief cooldown before next search
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        if (targetLoot == null) {
            return;
        }
        
        double targetX, targetY, targetZ;
        if (targetLoot instanceof ItemEntity item) {
            targetX = item.getX();
            targetY = item.getY();
            targetZ = item.getZ();
        } else {
            ExperienceOrbEntity orb = (ExperienceOrbEntity) targetLoot;
            targetX = orb.getX();
            targetY = orb.getY();
            targetZ = orb.getZ();
        }
        
        // Look at loot
        mob.getLookControl().lookAt(targetX, targetY, targetZ, 20.0f, 20.0f);
        
        // Navigate toward loot
        double distSq = mob.squaredDistanceTo(targetX, targetY, targetZ);
        if (distSq > PICKUP_DISTANCE_SQ) {
            // Use faster speed for scurrying - scouts are quick!
            mob.getNavigation().startMovingTo(targetX, targetY, targetZ, 1.2);
        }
    }
    
    private Object findNearestLoot() {
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return null;
        }
        
        Box searchBox = mob.getBoundingBox().expand(DETECTION_RADIUS);
        
        // Prioritize XP orbs (scouts love progression!)
        List<ExperienceOrbEntity> orbs = sw.getEntitiesByClass(
            ExperienceOrbEntity.class, 
            searchBox,
            orb -> orb.isAlive() && !orb.isRemoved()
        );
        
        if (!orbs.isEmpty()) {
            return orbs.stream()
                .min((a, b) -> Double.compare(
                    mob.squaredDistanceTo(a),
                    mob.squaredDistanceTo(b)
                ))
                .orElse(null);
        }
        
        // Fall back to items
        List<ItemEntity> items = sw.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            item -> item.isAlive() && !item.isRemoved() && item.getStack() != null
        );
        
        if (!items.isEmpty()) {
            return items.stream()
                .min((a, b) -> Double.compare(
                    mob.squaredDistanceTo(a),
                    mob.squaredDistanceTo(b)
                ))
                .orElse(null);
        }
        
        return null;
    }
    
    @Override
    protected float calculateEngagement() {
        // High engagement when actively tracking loot
        if (targetLoot != null && !isPaused()) {
            return 0.8f;
        }
        return 0.3f;
    }
    
    private boolean isPaused() {
        return !mob.getNavigation().isFollowingPath();
    }
}
