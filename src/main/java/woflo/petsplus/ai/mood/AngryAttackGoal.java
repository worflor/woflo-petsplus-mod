package woflo.petsplus.ai.mood;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.ai.MoodBasedGoal;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When pets are ANGRY, they become more aggressive and likely to attack nearby threats.
 */
public class AngryAttackGoal extends MoodBasedGoal {

    private LivingEntity target;
    private int aggressionTicks;
    private static final int MAX_AGGRESSION_TICKS = 200; // 10 seconds
    
    // Named constants for magic numbers
    private static final double ATTACK_RANGE = 4.0; // 2 blocks
    private static final double TARGET_UPDATE_RANGE = 256.0; // 16 blocks
    private static final double SEARCH_RADIUS = 12.0; // 12 blocks search radius
    private static final double MOVEMENT_SPEED = 1.3; // Fast approach speed
    
    // Caching for entity search results
    private long lastSearchTick = -1;
    private LivingEntity cachedTarget = null;
    private static final long SEARCH_CACHE_TICKS = 10; // Cache search results for 0.5 seconds
    
    // Cache for entity lists to avoid repeated expensive searches
    private static final ConcurrentHashMap<Integer, List<HostileEntity>> ENTITY_CACHE = new ConcurrentHashMap<>();
    private static final long ENTITY_CACHE_TICKS = 20; // Cache for 1 second
    private long lastEntityCacheTick = -1;

    public AngryAttackGoal(MobEntity mob) {
        super(mob, PetComponent.Mood.ANGRY);
        this.setControls(EnumSet.of(Control.MOVE, Control.TARGET));
    }

    @Override
    protected boolean canStartMoodBehavior() {
        // Look for hostile entities to attack
        LivingEntity nearestThreat = findNearestThreat(mob.getWorld().getTime());
        if (nearestThreat != null) {
            this.target = nearestThreat;
            return true;
        }
        return false;
    }

    @Override
    protected boolean shouldContinueMoodBehavior() {
        return target != null && target.isAlive() && aggressionTicks < MAX_AGGRESSION_TICKS;
    }

    @Override
    protected boolean shouldBypassCooldown(boolean moodReady) {
        return moodReady && target != null && target.isAlive();
    }

    @Override
    public void start() {
        super.start();
        aggressionTicks = 0;
        mob.setTarget(target);
    }

    @Override
    public void tick() {
        aggressionTicks++;

        if (target == null || !target.isAlive()) {
            stop();
            return;
        }

        // Move toward target aggressively
        double distance = mob.squaredDistanceTo(target);
        if (distance > ATTACK_RANGE) { // If far away, get closer
            mob.getNavigation().startMovingTo(target, MOVEMENT_SPEED); // Fast approach
        } else if (!mob.getWorld().isClient()) {
            performAttack(target);
        }

        // Update target if it moves too far away
        if (distance > TARGET_UPDATE_RANGE) { // Use named constant
            LivingEntity newTarget = findNearestThreat(mob.getWorld().getTime());
            if (newTarget != null) {
                this.target = newTarget;
                mob.setTarget(newTarget);
            } else {
                stop();
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        target = null;
        aggressionTicks = 0;
        mob.setTarget(null);
        mob.getNavigation().stop();
    }

    private LivingEntity findNearestThreat(long currentTick) {
        // Check if we can use cached results
        if (currentTick - lastSearchTick < SEARCH_CACHE_TICKS && cachedTarget != null && cachedTarget.isAlive()) {
            return cachedTarget;
        }
        
        // Get cached entity list or perform a new search
        List<HostileEntity> threats = getCachedHostileEntities(currentTick);
        
        LivingEntity nearest = null;
        double closestDistance = Double.MAX_VALUE;

        for (HostileEntity threat : threats) {
            double distance = mob.squaredDistanceTo(threat);
            if (distance < closestDistance) {
                closestDistance = distance;
                nearest = threat;
            }
        }

        // Update cache
        lastSearchTick = currentTick;
        cachedTarget = nearest;

        return nearest;
    }
    
    /**
     * Gets a cached list of hostile entities or performs a new search if cache is expired.
     * This optimizes the expensive entity search operation.
     */
    private List<HostileEntity> getCachedHostileEntities(long currentTick) {
        // Check if we can use cached entity list
        if (currentTick - lastEntityCacheTick < ENTITY_CACHE_TICKS) {
            List<HostileEntity> cached = ENTITY_CACHE.get(mob.hashCode());
            if (cached != null) {
                return cached;
            }
        }
        
        // Perform new search and cache results
        List<HostileEntity> threats = mob.getWorld().getEntitiesByClass(
            HostileEntity.class,
            mob.getBoundingBox().expand(SEARCH_RADIUS),
            entity -> entity.isAlive() && !entity.isRemoved()
        );
        
        // Update cache
        ENTITY_CACHE.put(mob.hashCode(), threats);
        lastEntityCacheTick = currentTick;
        
        // Clean up old cache entries periodically
        if (currentTick % 100 == 0) { // Every 5 seconds
            ENTITY_CACHE.entrySet().removeIf(entry -> {
                List<HostileEntity> list = entry.getValue();
                return list == null || list.isEmpty() || list.stream().allMatch(e -> e.isRemoved());
            });
        }
        
        return threats;
    }

    private void performAttack(LivingEntity target) {
        if (target == null) {
            return;
        }

        if (mob.getWorld() instanceof ServerWorld serverWorld) {
            mob.tryAttack(serverWorld, target);
        }
    }
}
