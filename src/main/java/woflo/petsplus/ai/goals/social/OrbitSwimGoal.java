package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Orbit swim - aquatic pets swim circles around owner underwater.
 * Creates playful, protective aquatic companionship.
 */
public class OrbitSwimGoal extends AdaptiveGoal {
    private int orbitTicks = 0;
    private static final int MAX_ORBIT_TICKS = 200; // 10 seconds
    private static final double ORBIT_RADIUS = 3.0;
    private double orbitAngle = 0.0;
    
    public OrbitSwimGoal(MobEntity mob) {
        super(mob, GoalType.ORBIT_SWIM, EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        return mob.isTouchingWater() && 
               owner != null && 
               owner.isTouchingWater() &&
               ctx.ownerNearby() &&
               ctx.distanceToOwner() < 10.0;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        PetContext ctx = getContext();
        return orbitTicks < MAX_ORBIT_TICKS && 
               mob.isTouchingWater() &&
               ctx.ownerNearby() &&
               ctx.distanceToOwner() < 12.0;
    }
    
    @Override
    protected void onStartGoal() {
        orbitTicks = 0;
        orbitAngle = mob.getRandom().nextDouble() * Math.PI * 2;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
    }
    
    @Override
    protected void onTickGoal() {
        orbitTicks++;
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        if (owner == null) return;
        
        // Circular orbit around owner
        orbitAngle += 0.05; // Rotation speed
        
        double offsetX = Math.cos(orbitAngle) * ORBIT_RADIUS;
        double offsetZ = Math.sin(orbitAngle) * ORBIT_RADIUS;
        
        Vec3d targetPos = owner.getEntityPos().add(offsetX, 0, offsetZ);
        
        // Navigate to orbit position
        mob.getNavigation().startMovingTo(
            targetPos.x, 
            targetPos.y, 
            targetPos.z, 
            0.9
        );
        
        // Look at owner while orbiting
        mob.getLookControl().lookAt(owner);
        
        // Playful vertical bobbing
        if (orbitTicks % 20 == 0) {
            mob.setVelocity(
                mob.getVelocity().x,
                mob.getRandom().nextDouble() * 0.1 - 0.05,
                mob.getVelocity().z
            );
        }
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.GUARDIAN_VIGIL, 0.12f,
            woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.10f
        );
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.85f;
        
        // Very engaging for playful pets
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.4f)) {
            engagement = 0.95f;
        }
        
        // Engaging for bonded pets
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.BONDED, 0.3f)) {
            engagement += 0.1f;
        }
        
        return Math.min(1.0f, engagement);
    }
}

