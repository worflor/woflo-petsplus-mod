package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.traits.SpeciesProfile;
import woflo.petsplus.ai.traits.SpeciesTraits;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * Sunbeam sprawl idle behavior - pet finds and stretches out in sunlight.
 * Multi-tag gated: requires sun_basker OR feline_like + daytime.
 * Subtle behavior: P0
 * Species tags: multi-tag gating
 */
public class SunbeamSprawlGoal extends AdaptiveGoal {
    private int sprawlTicks = 0;
    private Vec3d targetPos = null;
    private static final int SPRAWL_DURATION = 80;
    private static final int SUNLIGHT_CHECK_INTERVAL = 20;
    
    public SunbeamSprawlGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.SUNBEAM_SPRAWL), EnumSet.noneOf(Control.class));
    }
    
    @Override
    protected boolean canStartGoal() {
        // Species tags: multi-tag gating (cached profile, O(1) lookup)
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        if (!hasRequiredSpeciesTags(profile)) {
            return false;
        }
        
        // World predicates for Subtle behavior: P0
        if (!isDaytime() || mob.getEntityWorld().isRaining()) {
            return false;
        }
        
        // Must be idle and not moving
        if (!mob.getNavigation().isIdle() || mob.getVelocity().horizontalLength() > 0.1) {
            return false;
        }
        
        // Check for sunlight at current position; otherwise search within ~8 blocks
        if (!isInSunlight(mob.getBlockPos())) {
            targetPos = findNearbySunlight(8);
            return targetPos != null;
        }
        
        return true;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return sprawlTicks < SPRAWL_DURATION && isInSunlight(mob.getBlockPos());
    }
    
    @Override
    protected void onStartGoal() {
        sprawlTicks = 0;
        
        // Move to target position if needed
        if (targetPos != null) {
            if (mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld) {
                mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 0.6);
            }
        }
    }
    
    @Override
    protected void onStopGoal() {
        // Apply PetComponent cooldown as per spec
        // Subtle behavior: P0
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            int cooldown = 90 * 20 + mob.getRandom().nextInt((180 - 90 + 1) * 20); // 90–180s
            pc.setCooldown("sunbeam_sprawl", cooldown);
        }
        // Reset to standing pose
        mob.setPitch(0);
        mob.setYaw(mob.bodyYaw);
        targetPos = null;
    }
    
    @Override
    protected void onTickGoal() {
        sprawlTicks++;
        
        // Check sunlight periodically
        if (sprawlTicks % SUNLIGHT_CHECK_INTERVAL == 0 && !isInSunlight(mob.getBlockPos())) {
            // Try to find new sunlight spot
            targetPos = findNearbySunlight(8);
            if (targetPos != null) {
                if (mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld) {
                    mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 0.6);
                }
            }
        }
        
        // Perform sprawl animation
        performSprawlAnimation();
        
        // Occasionally roll over for comfort
        if (sprawlTicks > 20 && sprawlTicks % 30 == 0 && mob.getRandom().nextFloat() < 0.3f) {
            performComfortRoll();
        }
    }
    
    @Override
    protected float calculateEngagement() {
        var ctx = getContext();
        var profile = SpeciesTraits.getProfile(mob);
        float engagement = 0.6f; // Base engagement
        
        // More engaging for sun_basker species
        if (profile.sunBasker()) {
            engagement += 0.2f;
        }
        
        // More engaging for feline species
        if (profile.felineLike()) {
            engagement += 0.1f;
        }
        
        // Less engaging in bad weather
        if (mob.getEntityWorld().isRaining() || mob.getEntityWorld().isThundering()) {
            engagement -= 0.2f;
        }
        
        // More engaged if in content/calm mood
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.CALM, 0.3f)) {
            engagement += 0.2f;
        }
        
        engagement *= IdleEnergyTuning.restorativeStaminaMultiplier(ctx.physicalStamina());
        
        return MathHelper.clamp(engagement, 0f, 1f);
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.12f,
            woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.10f
        );
    }
    
    /**
     * Multi-tag gating: check if pet has required species tags.
     * Species tags: multi-tag gating
     */
    private boolean hasRequiredSpeciesTags(SpeciesProfile profile) {
        // Primary: sun_basker species
        if (profile.sunBasker()) {
            return true;
        }
        // Secondary: feline species (only in daytime)
        return profile.felineLike() && isDaytime();
    }
    
    /**
     * Check if it's currently daytime.
     */
    private boolean isDaytime() {
        long time = mob.getEntityWorld().getTimeOfDay() % 24000L;
        return time >= 0L && time < 12000L; // 0 to 12000 is day
    }
    
    /**
     * Check if position is in direct sunlight with sufficient light level.
     * Subtle behavior: P0
     */
    private boolean isInSunlight(net.minecraft.util.math.BlockPos pos) {
        var world = mob.getEntityWorld();
        if (!world.isSkyVisible(pos)) {
            return false;
        }
        // Require strong light at target (>= 12) and not raining
        int light = world.getLightLevel(pos);
        return light >= 12 && !world.isRaining();
    }
    
    /**
     * Find nearby sunlight position within the given horizontal range.
     * Subtle behavior: P0
     */
    private Vec3d findNearbySunlight(int range) {
        net.minecraft.util.math.BlockPos currentPos = mob.getBlockPos();
        
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                if (MathHelper.sqrt((float)(x * x + z * z)) > range) continue;
                net.minecraft.util.math.BlockPos checkPos = currentPos.add(x, 0, z);

                if (!mob.getEntityWorld().isAir(checkPos)) {
                    continue;
                }

                net.minecraft.util.math.BlockPos groundPos = checkPos.down();

                if (!mob.getEntityWorld().getBlockState(groundPos).isSolidBlock(mob.getEntityWorld(), groundPos)) {
                    continue;
                }

                // Require the air space itself to have direct sunlight
                if (isInSunlight(checkPos)) {
                    return Vec3d.ofCenter(checkPos);
                }
            }
        }

        return null;
    }
    
    /**
     * Perform sprawl animation.
     */
    private void performSprawlAnimation() {
        // Lie down and stretch out
        mob.setPitch(30);
        
        // Slightly adjust orientation for natural micro-movements
        if (sprawlTicks % 10 == 0) {
            mob.setYaw(mob.bodyYaw + mob.getRandom().nextFloat() * 10f - 5f);
        }
    }
    
    /**
     * Perform comfort roll animation.
     */
    private void performComfortRoll() {
        // Roll onto side briefly
        mob.setPitch(45);
        mob.setYaw(mob.bodyYaw + 90);
        
        // Reset after a moment
        if (sprawlTicks % 30 == 5) {
            mob.setPitch(30);
            mob.setYaw(mob.bodyYaw);
        }
    }
}