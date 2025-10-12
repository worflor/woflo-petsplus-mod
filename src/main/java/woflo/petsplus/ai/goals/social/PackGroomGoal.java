package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.traits.SpeciesProfile;
import woflo.petsplus.ai.traits.SpeciesTraits;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * PackGroomGoal - social micro behavior where an initiator and a follower engage in brief grooming.
 * Subtle behavior: P0
 * Species tags: multi-tag gating
 *
 * Gating:
 *  - profile.groomingSpecies && profile.socialAffiliative
 *
 * Preconditions:
 *  - Nearby allied pet (same owner) within 4 blocks
 *  - Partner is damp (recent water) OR flagged post-combat ≤ 15s (300 ticks)
 *
 * Coordination:
 *  - Initiator "publishes" invite implicitly by starting; follower joins with 0.4–1.2s stagger
 *  - Duration: 6–10s
 *  - Per-pair cooldown 120–240s using key "pack_groom_pair:{uuidA}:{uuidB}"
 *
 * Abort:
 *  - Base goal safety aborts on threat/target; also abort if partner disappears
 */
public class PackGroomGoal extends AdaptiveGoal {
    private static final double SCAN_RANGE = 4.0;
    private static final double MEET_DISTANCE_SQ = 2.25; // ~1.5 blocks
    private static final int MIN_DURATION_TICKS = 6 * 20;
    private static final int MAX_DURATION_TICKS = 10 * 20;
    private static final int FOLLOWER_STAGGER_MIN = 8;   // 0.4s
    private static final int FOLLOWER_STAGGER_MAX = 24;  // 1.2s
    private static final int POST_COMBAT_WINDOW_TICKS = 15 * 20;

    private MobEntity partner;
    private int groomTicks;
    private int groomDuration;
    private int followerStagger;
    private boolean isInitiator;
    private Vec3d meetPos;

    public PackGroomGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.PACK_GROOM), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        // Species tags: multi-tag gating (cached profile, O(1))
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        if (!(profile.groomingSpecies() && profile.socialAffiliative())) {
            return false;
        }

        PetComponent selfPc = PetComponent.get(mob);
        if (selfPc == null || !(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return false;
        }

        UUID ownerId = selfPc.getOwnerUuid();
        if (ownerId == null) {
            return false;
        }

        // Scan nearby for allied partner who is damp or post-combat within the window
        List<MobEntity> nearby = sw.getEntitiesByClass(MobEntity.class,
            mob.getBoundingBox().expand(SCAN_RANGE),
            other -> other != mob && other.isAlive());

        long now = sw.getTime();
        MobEntity chosen = null;
        double best = Double.MAX_VALUE;

        for (MobEntity other : nearby) {
            PetComponent otherPc = PetComponent.get(other);
            if (otherPc == null) continue;
            UUID otherOwner = otherPc.getOwnerUuid();
            if (otherOwner == null || !otherOwner.equals(ownerId)) continue;

            boolean damp = other.isTouchingWaterOrRain();
            boolean postCombat = (now - otherPc.getLastAttackTick()) <= POST_COMBAT_WINDOW_TICKS;

            if (!(damp || postCombat)) continue;

            // Respect per-pair cooldown
            if (isPairOnCooldown(selfPc, otherPc, now)) continue;

            double d = mob.squaredDistanceTo(other);
            if (d < best) {
                best = d;
                chosen = other;
            }
        }

        if (chosen == null) {
            return false;
        }

        this.partner = chosen;
        // Deterministic initiator selection by UUID ordering to avoid both starting simultaneously
        this.isInitiator = mob.getUuid().compareTo(partner.getUuid()) <= 0;

        // Meeting point: midpoint between both, slightly biased toward safe approach
        Vec3d mid = mob.getEntityPos().add(partner.getEntityPos()).multiply(0.5);
        this.meetPos = new Vec3d(mid.x, mid.y, mid.z);

        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        // Stop on partner loss or when time completes
        if (partner == null || !partner.isAlive()) {
            return false;
        }
        return groomTicks < groomDuration;
    }

    @Override
    protected void onStartGoal() {
        groomTicks = 0;
        groomDuration = MIN_DURATION_TICKS + mob.getRandom().nextInt((MAX_DURATION_TICKS - MIN_DURATION_TICKS) + 1);
        followerStagger = FOLLOWER_STAGGER_MIN + mob.getRandom().nextInt((FOLLOWER_STAGGER_MAX - FOLLOWER_STAGGER_MIN) + 1);

        // Begin approach toward meeting point
        if (meetPos != null) {
            mob.getNavigation().startMovingTo(meetPos.x, meetPos.y, meetPos.z, 1.05);
        }
    }

    @Override
    protected void onStopGoal() {
        // Apply per-pair cooldown on both pets
        PetComponent selfPc = PetComponent.get(mob);
        PetComponent partnerPc = partner != null ? PetComponent.get(partner) : null;
        if (selfPc != null) {
            int cooldown = 120 * 20 + mob.getRandom().nextInt((240 - 120 + 1) * 20);
            String key = makePairCooldownKey(mob.getUuid(), partner != null ? partner.getUuid() : mob.getUuid());
            selfPc.setCooldown(key, cooldown);
            if (partnerPc != null) {
                partnerPc.setCooldown(key, cooldown);
            }
        }

        // Stop movement and reset gentle pose
        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        mob.setPitch(0);
        if (partner != null) {
            partner.setPitch(0);
        }

        partner = null;
        meetPos = null;
    }

    @Override
    protected void onTickGoal() {
        groomTicks++;

        // Approach until within close distance, then hold position
        if (meetPos != null) {
            double distSq = mob.squaredDistanceTo(meetPos);
            double speed = distSq > MEET_DISTANCE_SQ ? 1.05 : 0.0;
            if (speed > 0.0) {
                if (mob.getNavigation().isIdle()) {
                    mob.getNavigation().startMovingTo(meetPos.x, meetPos.y, meetPos.z, speed);
                }
            } else {
                mob.getNavigation().stop();
            }
        }

        // Follower joins after stagger; initiator starts cues immediately
        boolean activeCues = isInitiator || groomTicks >= followerStagger;

        if (activeCues) {
            // Subtle mutual grooming cues: pitch bob and micro-yaw
            float phase = (groomTicks % 16) / 16.0f;
            float bob = (float) Math.sin(phase * MathHelper.TAU) * 8.0f;
            mob.setPitch(bob);
            if (groomTicks % 12 == 0) {
                mob.setYaw(mob.bodyYaw + mob.getRandom().nextFloat() * 6f - 3f);
            }
        }
    }

    @Override
    protected float calculateEngagement() {
        // Social micro; slightly boosted when partner is close
        float engagement = 0.6f;
        if (partner != null) {
            double d = Math.sqrt(Math.max(0.0, mob.squaredDistanceTo(partner)));
            engagement += MathHelper.clamp((float) ((SCAN_RANGE - d) / (SCAN_RANGE * 2.0)), 0.0f, 0.15f);
        }
        return MathHelper.clamp(engagement, 0f, 1f);
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Gentle affiliative pulse
        return woflo.petsplus.ai.goals.EmotionFeedback.dual(
            woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.10f,
            woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.08f
        );
    }

    // === Helpers ===

    private static String makePairCooldownKey(UUID a, UUID b) {
        // Order UUIDs to make key symmetric
        String sa = a.toString();
        String sb = b.toString();
        String first = sa.compareTo(sb) <= 0 ? sa : sb;
        String second = sa.compareTo(sb) <= 0 ? sb : sa;
        return "pack_groom_pair:" + first + ":" + second;
    }

    private static boolean isPairOnCooldown(PetComponent self, PetComponent other, long now) {
        String key = makePairCooldownKey(self.getPet().getUuid(), other.getPet().getUuid());
        // PetComponent is backed by scheduling module with absolute tick expiry;
        // isOnCooldown uses current world time internally, so we just query it.
        return self.isOnCooldown(key) || other.isOnCooldown(key);
    }
}