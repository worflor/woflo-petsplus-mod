package woflo.petsplus.ai.goals.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.behavior.MomentumState;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
// Species tags: multi-tag gating
import woflo.petsplus.ai.traits.SpeciesProfile;
import woflo.petsplus.ai.traits.SpeciesTraits;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

/**
 * Subtle behavior: P1
 * BurrowPeekGoal - pet walks to a dirt/sand/gravel patch and performs a brief pawing/sniff animation.
 *
 * Gating:
 * - Species tags: multi-tag gating (cached): reptileShelly OR explicit rabbits (burrowers) when available
 *
 * Preconditions:
 * - Dirt/sand/gravel patch within 5 blocks; safe area; on ground
 *
 * Momentum gating:
 * - High (≥0.65) blocks start
 * - Mid (0.35–0.65) halves the internal start chance
 *
 * Cooldowns:
 * - Main: 75–150s via "burrow_peek"
 * - Variety: 20–35s via "world_micro"
 */
public class BurrowPeekGoal extends AdaptiveGoal {
    private static final int MIN_DURATION_TICKS = 100; // 5s
    private static final int MAX_DURATION_TICKS = 160; // 8s
    private static final int SCAN_RANGE = 5;

    private int ticks;
    private int duration;
    private Vec3d targetPos;

    public BurrowPeekGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.BURROW_PEEK), java.util.EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        // Species tags: multi-tag gating
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        boolean burrowerSpecies = profile.reptileShelly() || mob.getType() == EntityType.RABBIT;
        if (!burrowerSpecies) {
            return false;
        }

        // Must be on ground (no flying/swimming)
        if (!mob.isOnGround()) {
            return false;
        }

        // Variety micro cooldown for world interactions
        PetComponent pc = PetComponent.get(mob);
        if (pc != null && pc.isOnCooldown("world_micro")) {
            return false;
        }
        if (pc != null && pc.isOnCooldown("burrow_peek")) {
            return false;
        }

        // Momentum gating
        MomentumState ms = MomentumState.capture(mob);
        float m = MathHelper.clamp(ms.momentum(), 0f, 1f);
        if (m >= 0.65f) {
            return false;
        }
        if (m >= 0.35f && mob.getRandom().nextBoolean()) {
            return false;
        }

        // Abort on owner urgency
        if (pc != null && pc.getCachedOwnerEntity() != null) {
            var state = OwnerCombatState.get(pc.getCachedOwnerEntity());
            if (state != null && (state.isInCombat() || state.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
                return false;
            }
        }

        // Find a nearby suitable patch
        BlockPos patch = findLooseSoilPatch(SCAN_RANGE);
        if (patch == null) {
            return false;
        }

        targetPos = Vec3d.ofCenter(patch.up()); // stand just above the patch
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        return ticks < duration;
    }

    @Override
    protected void onStartGoal() {
        ticks = 0;
        duration = MIN_DURATION_TICKS + mob.getRandom().nextInt((MAX_DURATION_TICKS - MIN_DURATION_TICKS) + 1);
        if (targetPos != null) {
            mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 0.85);
        }
    }

    @Override
    protected void onStopGoal() {
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            int mainCd = secondsToTicks(75) + mob.getRandom().nextInt(secondsToTicks(76)); // 75–150s
            pc.setCooldown("burrow_peek", mainCd);

            int varietyCd = secondsToTicks(20) + mob.getRandom().nextInt(secondsToTicks(16)); // 20–35s
            pc.setCooldown("world_micro", varietyCd);
        }
        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        targetPos = null;
    }

    @Override
    protected void onTickGoal() {
        ticks++;

        // Pawing/sniff animation: small repetitive head dips and slight side yaws
        float phase = (ticks % 24) / 24.0f;
        float dip = (float) Math.sin(phase * MathHelper.TAU) * 12.0f;
        mob.setPitch(MathHelper.clamp(dip, -18.0f, 18.0f));

        if (ticks % 10 == 0) {
            mob.setYaw(mob.bodyYaw + (mob.getRandom().nextFloat() - 0.5f) * 10.0f);
        }

        // Continue moving until close
        if (targetPos != null && mob.squaredDistanceTo(targetPos) > 1.0) {
            if (mob.getNavigation().isIdle()) {
                mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 0.85);
            }
        } else if (targetPos != null) {
            // Once arrived, stop movement and focus downward slightly
            mob.getNavigation().stop();
            mob.getLookControl().lookAt(targetPos.x, targetPos.y - 0.2, targetPos.z, 18.0f, 18.0f);
        }
    }

    @Override
    protected float calculateEngagement() {
        float e = 0.56f;
        if (targetPos != null) {
            double d = Math.sqrt(mob.squaredDistanceTo(targetPos));
            e += MathHelper.clamp((float) ((SCAN_RANGE - d) / (SCAN_RANGE * 2.0)), 0.0f, 0.08f);
        }
        return MathHelper.clamp(e, 0f, 1f);
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Calm curiosity
        return woflo.petsplus.ai.goals.EmotionFeedback.single(
            woflo.petsplus.state.PetComponent.Emotion.CURIOUS, 0.07f
        );
    }

    // === Helpers ===

    private BlockPos findLooseSoilPatch(int range) {
        BlockPos origin = mob.getBlockPos();
        var world = mob.getEntityWorld();

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (MathHelper.sqrt((float) (dx * dx + dz * dz)) > range) continue;

                BlockPos base = origin.add(dx, -1, dz); // ground level just beneath feet
                // probe small vertical band to handle uneven terrain
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = base.add(0, dy, 0);
                    BlockState state = world.getBlockState(pos);
                    if (isLooseSoil(state)) {
                        // require headroom
                        if (world.isAir(pos.up())) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isLooseSoil(BlockState state) {
        // Accept broad dirt tags and explicit gravel/sand
        if (state.isIn(BlockTags.DIRT)) return true;
        if (state.isOf(Blocks.GRAVEL)) return true;
        if (state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND)) return true;
        // Coarse dirt, podzol, rooted dirt, etc.
        return state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.ROOTED_DIRT);
    }

    private static int secondsToTicks(int s) {
        return Math.max(0, s * 20);
    }
}