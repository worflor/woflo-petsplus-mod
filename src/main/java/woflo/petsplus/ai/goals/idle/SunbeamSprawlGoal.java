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
    private static final int SPRAWL_DURATION = 80;
    private static final int MAX_REPOSITION_TICKS = 100;
    private static final int MAX_NAVIGATION_STALL_TICKS = 30;
    private static final int MIN_COOLDOWN_TICKS = 90 * 20;
    private static final int MAX_COOLDOWN_TICKS = 180 * 20;

    private int sprawlTicks = 0;
    private Vec3d targetPos = null;

    private int getSprawlDuration() {
        PetContext ctx = getContext();
        float stamina = ctx.physicalStamina();
        // Longer sprawl for lower stamina
        return (int) (SPRAWL_DURATION * (1.5f - stamina));
    }

    private int repositionTicks = 0;
    private int navigationStalledTicks = 0;
    
    public SunbeamSprawlGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.SUNBEAM_SPRAWL), EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        // Species tags: multi-tag gating (cached profile, O(1) lookup)
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        if (!hasRequiredSpeciesTags(profile)) {
            return false;
        }

        PetComponent pc = PetComponent.get(mob);
        if (pc != null && pc.isOnCooldown("sunbeam_sprawl")) {
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
        if (sprawlTicks >= getSprawlDuration()) {
            return false;
        }
        if (isInSunlight(mob.getBlockPos())) {
            return true;
        }
        return repositionTicks < MAX_REPOSITION_TICKS;
    }

    @Override
    protected void onStartGoal() {
        sprawlTicks = 0;
        repositionTicks = 0;
        navigationStalledTicks = 0;

        // Move to target position if needed
        if (targetPos != null) {
            if (mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld) {
                mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 0.6);
            }
        }
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            int cooldown = mob.getRandom().nextInt(MAX_COOLDOWN_TICKS - MIN_COOLDOWN_TICKS + 1) + MIN_COOLDOWN_TICKS;
            pc.setCooldown("sunbeam_sprawl", cooldown);
        }
        // Reset to standing pose
        mob.setPitch(0);
        mob.setYaw(mob.bodyYaw);
        targetPos = null;
        repositionTicks = 0;
        navigationStalledTicks = 0;
    }

    @Override
    protected void onTickGoal() {
        sprawlTicks++;

        boolean inSunlight = isInSunlight(mob.getBlockPos());

        if (!inSunlight) {
            repositionTicks = Math.min(repositionTicks + 1, MAX_REPOSITION_TICKS + 1);

            if (targetPos == null) {
                targetPos = findNearbySunlight(8);
                navigationStalledTicks = 0;
                if (targetPos == null) {
                    requestStop();
                    return;
                }
                if (mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld) {
                    mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 0.6);
                }
            }

            if (targetPos != null) {
                if (mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld) {
                    if (mob.getNavigation().isIdle()) {
                        navigationStalledTicks++;
                        mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 0.6);
                    } else {
                        navigationStalledTicks = 0;
                    }
                }

                if (mob.squaredDistanceTo(targetPos) < 1.0) {
                    // Close enough to reassess sunlight; clear target so next tick can rescan
                    targetPos = null;
                }
            }

            if (repositionTicks > MAX_REPOSITION_TICKS || navigationStalledTicks > MAX_NAVIGATION_STALL_TICKS) {
                requestStop();
                return;
            }
        } else {
            repositionTicks = 0;
            navigationStalledTicks = 0;
            targetPos = null;
        }

        // Perform sprawl animation
        performSprawlAnimation();

        // Occasionally roll over for comfort
        if (sprawlTicks > 20 && sprawlTicks % 30 == 0) {
            PetContext ctx = getContext();
            float rollChance = 0.3f;
            if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(PetComponent.Mood.CALM, 0.5f)) {
                rollChance = 0.6f;
            }
            if (mob.getRandom().nextFloat() < rollChance) {
                performComfortRoll();
            }
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
        var world = mob.getEntityWorld();

        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                if (MathHelper.sqrt((float)(x * x + z * z)) > range) continue;
                net.minecraft.util.math.BlockPos groundPos = currentPos.add(x, 0, z);
                net.minecraft.util.math.BlockPos airPos = groundPos.up();

                if (!world.isAir(airPos)) {
                    continue;
                }

                if (!world.getBlockState(groundPos).isSolidBlock(world, groundPos)) {
                    continue;
                }

                // Require the air space itself to have direct sunlight
                if (isInSunlight(airPos)) {
                    return Vec3d.ofCenter(airPos);
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
