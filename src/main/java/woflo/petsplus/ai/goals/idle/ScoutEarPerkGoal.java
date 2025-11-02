package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.api.registry.PetRoleType;

import java.util.EnumSet;
import java.util.List;

/**
 * Scout-specific idle behavior: Perks ears and scans when hostiles are nearby.
 * Adds subtle scout "awareness" personality - they're always alert.
 */
public class ScoutEarPerkGoal extends AdaptiveGoal {
    private static final double THREAT_DETECTION_RADIUS = 16.0;
    
    private int animationTick;
    private boolean detectedThreat;
    
    public ScoutEarPerkGoal(MobEntity mob, GoalDefinition goalDefinition) {
        super(mob, goalDefinition, EnumSet.of(Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        // Only for Scout role
        if (petComponent == null || !petComponent.hasRole(PetRoleType.SCOUT)) {
            return false;
        }
        
        // Only when idle (not attacking, not moving much)
        if (mob.getTarget() != null || mob.getNavigation().isFollowingPath()) {
            return false;
        }
        
        // Check for nearby threats
        detectedThreat = hasNearbyHostiles();
        
        // Trigger more frequently when threats are present
        if (detectedThreat) {
            return mob.getRandom().nextInt(60) == 0; // ~5% per second
        }
        
        return mob.getRandom().nextInt(200) == 0; // Rare idle animation otherwise
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return animationTick < 40; // 2-second animation
    }
    
    @Override
    protected void onStartGoal() {
        animationTick = 0;
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onStopGoal() {
        animationTick = 0;
    }
    
    @Override
    protected void onTickGoal() {
        animationTick++;
        
        // Look around scanning motion
        if (animationTick % 10 == 0) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double distance = 5.0;
            double lookX = mob.getX() + Math.cos(angle) * distance;
            double lookZ = mob.getZ() + Math.sin(angle) * distance;
            mob.getLookControl().lookAt(lookX, mob.getY() + 1, lookZ, 30.0f, 30.0f);
        }
        
        // Subtle particles when threat detected
        if (detectedThreat && animationTick == 10 && mob.getEntityWorld() instanceof ServerWorld sw) {
            // Single cloud puff near head
            sw.spawnParticles(
                ParticleTypes.CLOUD,
                mob.getX(), mob.getY() + mob.getStandingEyeHeight(), mob.getZ(),
                2, 0.1, 0.05, 0.1, 0.0
            );
        }
    }
    
    private boolean hasNearbyHostiles() {
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return false;
        }
        
        Box searchBox = mob.getBoundingBox().expand(THREAT_DETECTION_RADIUS);
        List<LivingEntity> hostiles = sw.getEntitiesByClass(
            LivingEntity.class,
            searchBox,
            entity -> entity instanceof HostileEntity && entity.isAlive() && !entity.isRemoved()
        );
        
        return !hostiles.isEmpty();
    }
    
    @Override
    protected float calculateEngagement() {
        return detectedThreat ? 0.6f : 0.4f;
    }
}
