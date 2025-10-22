package woflo.petsplus.ai.goals.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import woflo.petsplus.Petsplus;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.EmotionFeedback;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.List;

/**
 * Self-preservation goal that triggers when Fear emotion exceeds threshold.
 * Role-based behaviors determine how the pet responds to threats.
 */
public class SelfPreservationGoal extends AdaptiveGoal {
    private static final int MAX_DURATION_TICKS = 200; // 10 seconds
    private static final double RETREAT_DISTANCE = 12.0;
    private static final double OWNER_PROTECTION_RADIUS = 6.0;
    private static final float FEAR_THRESHOLD = 0.6f; // Configurable threshold for activation
    
    // Behavior states
    private enum PreservationBehavior {
        RETREAT,      // Default: run away from threat
        DEFENSIVE,    // Guardian: use defensive stance
        OWNER_RETURN  // Scavenger: run to owner for safety
    }
    
    private LivingEntity threat;
    private PlayerEntity owner;
    private PreservationBehavior behavior;
    private Vec3d retreatTarget;
    private int defensiveStanceTicks;
    private boolean abilityUsed;

    public SelfPreservationGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.SELF_PRESERVATION), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        // Check if Fear emotion exceeds threshold
        PetComponent pc = PetComponent.get(mob);
        if (pc == null || pc.getMoodEngine() == null) {
            return false;
        }
        
        float fearLevel = pc.getActiveEmotions().getOrDefault(PetComponent.Emotion.STARTLE, 0f);
        if (fearLevel < FEAR_THRESHOLD) {
            return false;
        }
        
        // Identify threat source
        threat = findThreat();
        if (threat == null) {
            return false;
        }
        
        // Get owner for potential retreat target
        owner = pc.getOwner();
        
        // Determine behavior based on pet role
        behavior = determineBehavior(pc);
        
        // Set initial target based on behavior
        retreatTarget = calculateRetreatTarget();
        
        return retreatTarget != null;
    }

    @Override
    protected boolean shouldContinueGoal() {
        // Stop if threat is gone or pet is no longer afraid
        if (threat == null || !threat.isAlive()) {
            return false;
        }
        
        PetComponent pc = PetComponent.get(mob);
        if (pc == null || pc.getMoodEngine() == null) {
            return false;
        }
        
        float fearLevel = pc.getActiveEmotions().getOrDefault(PetComponent.Emotion.STARTLE, 0f);
        if (fearLevel < FEAR_THRESHOLD * 0.8f) { // Hysteresis to prevent rapid toggling
            return false;
        }
        
        // Continue if we have a valid target and haven't exceeded duration
        if (getActiveTicks() >= MAX_DURATION_TICKS) {
            return false;
        }
        
        return retreatTarget != null || behavior == PreservationBehavior.DEFENSIVE;
    }

    @Override
    protected void onStartGoal() {
        defensiveStanceTicks = 0;
        abilityUsed = false;
        
        // Initialize behavior-specific state
        switch (behavior) {
            case DEFENSIVE -> {
                defensiveStanceTicks = 0;
                Petsplus.LOGGER.debug("[SelfPreservation] Pet {} entering defensive stance", mob.getDisplayName().getString());
            }
            case RETREAT -> {
                Petsplus.LOGGER.debug("[SelfPreservation] Pet {} retreating from threat", mob.getDisplayName().getString());
            }
            case OWNER_RETURN -> {
                Petsplus.LOGGER.debug("[SelfPreservation] Pet {} returning to owner for safety", mob.getDisplayName().getString());
            }
        }
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        threat = null;
        owner = null;
        retreatTarget = null;
        defensiveStanceTicks = 0;
        abilityUsed = false;
    }

    @Override
    protected void onTickGoal() {
        // Always look at the threat
        if (threat != null) {
            mob.getLookControl().lookAt(threat, 30.0f, 30.0f);
        }
        
        switch (behavior) {
            case DEFENSIVE -> handleDefensiveBehavior();
            case RETREAT -> handleRetreatBehavior();
            case OWNER_RETURN -> handleOwnerReturnBehavior();
        }
    }
    
    private void handleDefensiveBehavior() {
        defensiveStanceTicks++;
        
        // Use defensive ability if available and not used yet
        if (!abilityUsed && defensiveStanceTicks > 20) {
            tryUseDefensiveAbility();
        }
        
        // Slowly back away from threat while maintaining defensive stance
        if (threat != null) {
            Vec3d mobPos = new Vec3d(mob.getX(), mob.getY(), mob.getZ());
            Vec3d threatPos = new Vec3d(threat.getX(), threat.getY(), threat.getZ());
            Vec3d delta = mobPos.subtract(threatPos);
            double lengthSq = delta.lengthSquared();
            if (lengthSq < 1.0E-6d) {
                return;
            }
            Vec3d awayFromThreat = delta.multiply(1.0d / Math.sqrt(lengthSq));
            Vec3d backupTarget = mobPos.add(awayFromThreat.multiply(2.0));

            // Move backward slowly
            mob.getNavigation().startMovingTo(backupTarget.x, backupTarget.y, backupTarget.z, 0.6);
        }
    }
    
    private void handleRetreatBehavior() {
        if (retreatTarget != null) {
            double distance = mob.squaredDistanceTo(retreatTarget.x, retreatTarget.y, retreatTarget.z);
            
            if (distance < 2.0) {
                // Reached retreat point, find a new one if still threatened
                retreatTarget = calculateRetreatTarget();
            } else {
                // Move to retreat target at increased speed
                mob.getNavigation().startMovingTo(retreatTarget.x, retreatTarget.y, retreatTarget.z, 1.4);
            }
        }
    }
    
    private void handleOwnerReturnBehavior() {
        if (owner != null) {
            double distanceToOwner = mob.squaredDistanceTo(owner);
            
            if (distanceToOwner > OWNER_PROTECTION_RADIUS * OWNER_PROTECTION_RADIUS) {
                // Move toward owner
                mob.getNavigation().startMovingTo(owner, 1.5);
            } else {
                // Close enough to owner, stay close and hide
                mob.getNavigation().stop();
                retreatTarget = null;
            }
        }
    }
    
    private void tryUseDefensiveAbility() {
        if (abilityUsed) return;
        
        PetComponent pc = PetComponent.get(mob);
        if (pc == null) return;
        
        // Check for Guardian role abilities
        if (pc.hasRole(PetRoleType.GUARDIAN)) {
            // Try to use Guardian Bulwark or similar defensive ability
            // This would integrate with the Guardian role system
            abilityUsed = true;
            Petsplus.LOGGER.debug("[SelfPreservation] Pet {} used defensive ability", mob.getDisplayName().getString());

            // Trigger a defensive stance animation
            // This could be expanded to call actual ability methods
            mob.getEntityWorld().sendEntityStatus(mob, (byte) 30); // Generic status for defensive stance
        }
    }

    private LivingEntity findThreat() {
        // Check current attacker or target
        if (mob.getAttacker() instanceof LivingEntity attacker && attacker.isAlive()) {
            return attacker;
        }
        
        if (mob.getTarget() instanceof LivingEntity target && target.isAlive()) {
            return target;
        }
        
        double detectionRadius = 16.0;
        List<LivingEntity> candidates = mob.getEntityWorld().getEntitiesByClass(
            LivingEntity.class,
            mob.getBoundingBox().expand(detectionRadius),
            entity -> !(entity instanceof PlayerEntity)
                && entity != mob
                && entity.isAlive()
                && mob.canSee(entity)
                && isHostileToPet(entity)
        );

        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distanceSq = candidate.squaredDistanceTo(mob);
            if (distanceSq < closestDistance) {
                closestDistance = distanceSq;
                closest = candidate;
            }
        }

        return closest;
    }

    private boolean isHostileToPet(Entity entity) {
        // Simple check - can be expanded with more sophisticated threat detection
        return entity instanceof LivingEntity living &&
               living.getAttacking() != null && 
               living.getAttacking().equals(mob);
    }
    
    private PreservationBehavior determineBehavior(PetComponent pc) {
        PetRoleType role = pc.getRoleType();
        
        if (role.id().equals(PetRoleType.GUARDIAN_ID)) {
            return PreservationBehavior.DEFENSIVE;
        } else if (role.id().equals(PetRoleType.SCOUT_ID)) {
            return PreservationBehavior.OWNER_RETURN;
        } else {
            return PreservationBehavior.RETREAT;
        }
    }
    
    private Vec3d calculateRetreatTarget() {
        Vec3d currentPos = new Vec3d(mob.getX(), mob.getY(), mob.getZ());
        Vec3d threatPos = threat != null ? new Vec3d(threat.getX(), threat.getY(), threat.getZ()) : currentPos;

        // Calculate direction away from threat
        Vec3d awayFromThreat = currentPos.subtract(threatPos);
        double awayLengthSq = awayFromThreat.lengthSquared();
        if (awayLengthSq >= 1.0E-4d) {
            awayFromThreat = awayFromThreat.multiply(1.0d / Math.sqrt(awayLengthSq));
        } else {
            // If too close to threat, pick a random direction
            Vec3d randomDirection = new Vec3d(
                mob.getRandom().nextDouble() * 2 - 1,
                0,
                mob.getRandom().nextDouble() * 2 - 1
            );
            double randomLengthSq = randomDirection.lengthSquared();
            awayFromThreat = randomLengthSq >= 1.0E-4d
                ? randomDirection.multiply(1.0d / Math.sqrt(randomLengthSq))
                : Vec3d.ZERO;
        }
        
        // Find a safe position at retreat distance
        Vec3d retreatPos = currentPos.add(awayFromThreat.multiply(RETREAT_DISTANCE));
        
        // Try to find a safe y-level
        int sampleX = MathHelper.floor(retreatPos.x);
        int sampleZ = MathHelper.floor(retreatPos.z);
        int groundY = mob.getEntityWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);
        retreatPos = new Vec3d(retreatPos.x, MathHelper.clamp(retreatPos.y, mob.getEntityWorld().getBottomY(), groundY), retreatPos.z);
        
        return retreatPos;
    }

    @Override
    protected float calculateEngagement() {
        // Higher engagement when more afraid (fight or flight response)
        PetComponent pc = PetComponent.get(mob);
        if (pc == null || pc.getMoodEngine() == null) {
            return 0.5f;
        }
        
        float fearLevel = pc.getActiveEmotions().getOrDefault(PetComponent.Emotion.STARTLE, 0f);
        float baseEngagement = MathHelper.clamp(fearLevel, 0.3f, 1.0f);
        
        // Increase engagement if owner is nearby (protective instinct)
        if (owner != null && mob.squaredDistanceTo(owner) < 100) { // 10 blocks
            baseEngagement += 0.2f;
        }
        
        return Math.min(baseEngagement, 1.0f);
    }

    @Override
    protected EmotionFeedback defineEmotionFeedback() {
        return new EmotionFeedback.Builder()
            .add(PetComponent.Emotion.STARTLE, 0.15f)       // Fear reinforces itself
            .add(PetComponent.Emotion.VIGILANT, 0.10f)     // Increased alertness
            .add(PetComponent.Emotion.WORRIED, 0.08f)       // Concern about threat
            .withContagion(PetComponent.Emotion.STARTLE, 0.015f)  // Spread fear to nearby pets
            .build();
    }
}
