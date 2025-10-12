package woflo.petsplus.ai.goals.environmental;

import net.minecraft.entity.mob.MobEntity;
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

import java.util.EnumSet;

/**
 * Subtle behavior: P1
 * NightSkyListenGoal - pet gazes upward and performs subtle ear twitches during calm nighttime outdoors.
 *
 * Gating:
 * - Species tags: multi-tag gating (cached): nocturnalLeaning
 *
 * Preconditions:
 * - Nighttime; outdoors or skylight; no threat; low ambient noise if available (omitted if not present)
 *
 * Behavior:
 * - Gaze upward, ear twitch, minimal soft head movement; 3–6s
 *
 * Cooldown:
 * - 120–240s via "night_sky_listen"
 * - Variety micro "environment_micro" 20–35s
 */
public class NightSkyListenGoal extends AdaptiveGoal {
    private static final int MIN_TICKS = 60;  // 3s
    private static final int MAX_TICKS = 120; // 6s

    private int ticks;
    private int duration;
    private Vec3d focus;

    public NightSkyListenGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.NIGHT_SKY_LISTEN), EnumSet.of(Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        // Species tags: multi-tag gating (O(1) profile lookup)
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        if (!profile.nocturnalLeaning()) {
            return false;
        }

        PetComponent pc = PetComponent.get(mob);
        if (pc == null) return false;

        // Variety anti-spam for environment
        if (pc.isOnCooldown("environment_micro") || pc.isOnCooldown("night_sky_listen")) {
            return false;
        }

        // Owner urgency/threat abort
        if (pc.getCachedOwnerEntity() != null) {
            OwnerCombatState ocs = OwnerCombatState.get(pc.getCachedOwnerEntity());
            if (ocs != null && (ocs.isInCombat() || ocs.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
                return false;
            }
        }

        // Momentum gating: high block, mid halve
        MomentumState ms = MomentumState.capture(mob);
        float m = MathHelper.clamp(ms.momentum(), 0f, 1f);
        if (m >= 0.65f) return false;
        if (m >= 0.35f && mob.getRandom().nextBoolean()) return false;

        // Nighttime + outdoors/skylight
        if (!isNight()) {
            return false;
        }
        if (!hasSkyAccess()) {
            return false;
        }

        // Set an upward focus point
        focus = new Vec3d(mob.getX(), mob.getY() + 6.0, mob.getZ());
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        return ticks < duration;
    }

    @Override
    protected void onStartGoal() {
        ticks = 0;
        duration = MIN_TICKS + mob.getRandom().nextInt((MAX_TICKS - MIN_TICKS) + 1);
        if (focus != null) {
            mob.getLookControl().lookAt(focus.x, focus.y, focus.z, 18.0f, 28.0f);
        }
    }

    @Override
    protected void onStopGoal() {
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            int cd = secondsToTicks(120) + mob.getRandom().nextInt(secondsToTicks(121)); // 120–240s
            pc.setCooldown("night_sky_listen", cd);
            pc.setCooldown("environment_micro", secondsToTicks(20) + mob.getRandom().nextInt(secondsToTicks(16))); // 20–35s
        }
        focus = null;
    }

    @Override
    protected void onTickGoal() {
        ticks++;

        // Soft upward gaze and ear twitch approximation: pitch oscillation, minor yaw drift
        float t = (ticks % 24) / 24.0f;
        float pitch = MathHelper.lerp((float) Math.sin(t * MathHelper.TAU) * 0.5f + 0.5f, 5.0f, 18.0f);
        mob.setPitch(MathHelper.clamp(pitch, 0.0f, 25.0f));

        if (ticks % 14 == 0) {
            mob.setYaw(mob.bodyYaw + (mob.getRandom().nextFloat() - 0.5f) * 5.0f);
        }

        if (focus != null && ticks % 6 == 0) {
            mob.getLookControl().lookAt(focus.x, focus.y, focus.z, 16.0f, 28.0f);
        }
    }

    @Override
    protected float calculateEngagement() {
        float e = 0.55f;
        if (isNight()) e += 0.04f;
        return MathHelper.clamp(e, 0f, 1f);
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Quiet, attentive mood
        return woflo.petsplus.ai.goals.EmotionFeedback.single(
            woflo.petsplus.state.PetComponent.Emotion.FOCUSED, 0.06f
        );
    }

    // === Helpers ===

    private boolean isNight() {
        long time = mob.getEntityWorld().getTimeOfDay() % 24000L;
        return time >= 13000L && time < 23000L;
    }

    private boolean hasSkyAccess() {
        BlockPos pos = mob.getBlockPos().up();
        var world = mob.getEntityWorld();
        // Require visible sky at head or just above
        return world.isSkyVisible(pos) || world.isSkyVisible(pos.up());
    }

    private static int secondsToTicks(int s) {
        return Math.max(0, s * 20);
    }
}