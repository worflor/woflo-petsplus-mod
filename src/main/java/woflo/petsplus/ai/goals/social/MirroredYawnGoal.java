package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.behavior.MomentumState;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
// Species tags: multi-tag gating
import woflo.petsplus.ai.traits.SpeciesProfile;
import woflo.petsplus.ai.traits.SpeciesTraits;
import woflo.petsplus.ai.group.GroupCoordinator;
// GroupTuning: central constants
import woflo.petsplus.ai.group.GroupTuning;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.Map;

/**
 * Subtle behavior: P1
 * MirroredYawnGoal - perform a yawn when another nearby allied pet recently yawned, allowing soft social contagion.
 *
 * Gating:
 * - Species tags: multi-tag gating (cached): socialAffiliative
 *
 * Preconditions:
 * - Calm state; detect nearby yawn (proxy: recent STRETCH_AND_YAW goal) within 4 blocks
 *
 * Cooldown:
 * - 120–240s via "mirrored_yawn"
 * - Variety micro: "social_micro" 20–35s
 */
public class MirroredYawnGoal extends AdaptiveGoal {
    private static final double RADIUS = 4.0;
    private static final int MIN_TICKS = 60;  // 3s
    private static final int MAX_TICKS = 100; // 5s
    private static final int YAWN_RECENT_TICKS = 60; // 3s

    private int ticks;
    private int duration;
    private Vec3d settleFacing;

    // Open invite follower state
    private boolean joiningAsFollower = false; // Species tags: open invite join
    private int followerStaggerTicks = 0;

    public MirroredYawnGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.MIRRORED_YAWN), EnumSet.of(Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        if (!profile.socialAffiliative()) {
            return false;
        }

        PetComponent pc = PetComponent.get(mob);
        if (pc == null) return false;

        // Momentum gate + owner urgency
        MomentumState ms = MomentumState.capture(mob);
        float m = MathHelper.clamp(ms.momentum(), 0f, 1f);
        if (m >= 0.65f) return false;
        if (m >= 0.35f && mob.getRandom().nextBoolean()) return false;

        // Variety anti-spam
        if (pc.isOnCooldown("social_micro") || pc.isOnCooldown("mirrored_yawn")) {
            return false;
        }

        // Owner urgency abort
        if (pc.getCachedOwnerEntity() != null) {
            OwnerCombatState ocs = OwnerCombatState.get(pc.getCachedOwnerEntity());
            if (ocs != null && (ocs.isInCombat() || ocs.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
                return false;
            }
        }

        // Follower path: try to join a nearby open invite
        // Species tags: open invite join
        GroupCoordinator.OpenInvite invite = GroupCoordinator
            .findNearbyOpenInvite(GoalIds.MIRRORED_YAWN, mob, GroupTuning.GROUP_RADIUS_DEFAULT)
            .orElse(null);
        if (invite != null) {
            float joinChance = 0.35f;
            // Owner dimension guard for any boosts (though allowOwnerSneakBoost is false for yawn publish; still guard pattern)
            var owner = pc.getCachedOwnerEntity();
            if (owner != null
                && owner.getEntityWorld().getRegistryKey() == mob.getEntityWorld().getRegistryKey()
                && owner.isSneaking()
                && owner.squaredDistanceTo(mob) <= GroupTuning.OWNER_SNEAK_BOOST_RADIUS_SQ) {
                // allow a slight nudge even if not used in publish here
                joinChance = Math.min(0.9f, joinChance * 1.1f);
            }
            if (mob.getRandom().nextFloat() < joinChance && GroupCoordinator.tryJoinOpenInvite(mob, invite)) {
                if (mob.getEntityWorld() instanceof ServerWorld sw) {
                    var e = sw.getEntity(invite.initiatorUuid);
                    if (e instanceof net.minecraft.entity.mob.MobEntity initMob) {
                        settleFacing = new Vec3d(initMob.getX(), initMob.getY(), initMob.getZ());
                    }
                }
                joiningAsFollower = true;
                // Use GroupTuning follower stagger
                followerStaggerTicks = GroupTuning.FOLLOWER_STAGGER_TICKS_MIN
                    + mob.getRandom().nextInt(GroupTuning.FOLLOWER_STAGGER_TICKS_MAX - GroupTuning.FOLLOWER_STAGGER_TICKS_MIN + 1);
                return true;
            }
        }

        // Detect recent yawn around us (proxy: STRETCH_AND_YAW goal) within last 3s
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return false;
        }

        var neighbors = sw.getEntitiesByClass(MobEntity.class, mob.getBoundingBox().expand(RADIUS),
            e -> {
                if (e == mob || !e.isAlive() || e.isRemoved()) return false;
                PetComponent epc = PetComponent.get(e);
                if (epc == null) return false;
                // same owner preferred
                return pc.getOwnerUuid() != null && pc.getOwnerUuid().equals(epc.getOwnerUuid());
            });

        if (neighbors.isEmpty()) return false;

        long now = mob.getEntityWorld().getTime();
        boolean observedYawn = false;
        for (MobEntity e : neighbors) {
            PetComponent epc = PetComponent.get(e);
            if (epc == null) continue;
            Map<net.minecraft.util.Identifier, Long> stamps = epc.getGoalExecutionTimestamps();
            Long lastYawn = stamps.get(GoalIds.STRETCH_AND_YAW);
            if (lastYawn != null && now - lastYawn <= 60) { // keep 3s window
                observedYawn = true;
                // face toward that pet
                settleFacing = new Vec3d(e.getX(), e.getY(), e.getZ());
                break;
            }
        }

        return observedYawn;
    }

    @Override
    protected boolean shouldContinueGoal() {
        // Abort on owner urgency
        PetComponent pc = PetComponent.get(mob);
        if (pc != null && pc.getCachedOwnerEntity() != null) {
            OwnerCombatState ocs = OwnerCombatState.get(pc.getCachedOwnerEntity());
            if (ocs != null && (ocs.isInCombat() || ocs.recentlyDamaged(mob.getEntityWorld().getTime(), 80))) {
                return false;
            }
        }
        return ticks < duration;
    }

    @Override
    protected void onStartGoal() {
        ticks = 0;
        duration = MIN_TICKS + mob.getRandom().nextInt((MAX_TICKS - MIN_TICKS) + 1);

        // As initiator, publish an open invite with tiny join window
        // Species tags: open invite join
        if (!joiningAsFollower) {
            GroupCoordinator.publishOpenInvite(
                this.mob,
                GoalIds.MIRRORED_YAWN,
                5.0D, // acceptable per spec; otherwise could use GroupTuning.GROUP_RADIUS_DEFAULT
                GroupTuning.MAX_FOLLOWERS_DEFAULT,
                GroupTuning.INVITE_EXPIRY_TICKS_DEFAULT,
                false
            );
        }

        // small orientation toward observed yawner
        if (settleFacing != null && followerStaggerTicks == 0) {
            mob.getLookControl().lookAt(settleFacing.x, settleFacing.y + 0.2, settleFacing.z, 20.0f, 20.0f);
        }
    }

    @Override
    protected void onStopGoal() {
        PetComponent pc = PetComponent.get(mob);
        if (pc != null) {
            int mainCd = secondsToTicks(120) + mob.getRandom().nextInt(secondsToTicks(121)); // 120–240s
            pc.setCooldown("mirrored_yawn", mainCd);
            pc.setCooldown("social_micro", secondsToTicks(20) + mob.getRandom().nextInt(secondsToTicks(16))); // 20–35s
        }
        settleFacing = null;
        joiningAsFollower = false;
        followerStaggerTicks = 0;
    }

    @Override
    protected void onTickGoal() {
        ticks++;

        // Followers: respect stagger before starting visible action
        if (joiningAsFollower && ticks < Math.max(1, followerStaggerTicks)) {
            return;
        }

        // Perform yawn: gradual head tilt up then down and slight body sway
        float t = ticks / (float) Math.max(1, duration);
        float phase = (float) Math.sin(t * MathHelper.PI); // 0->1->0
        float pitch = MathHelper.lerp(phase, 0f, 25f);
        mob.setPitch(MathHelper.clamp(pitch, -5f, 30f));

        if (ticks % 12 == 0) {
            mob.setYaw(mob.bodyYaw + (mob.getRandom().nextFloat() - 0.5f) * 4.0f);
        }

        // Keep softly looking at the facing point
        if (settleFacing != null && ticks % 5 == 0) {
            mob.getLookControl().lookAt(settleFacing.x, settleFacing.y + 0.2, settleFacing.z, 18.0f, 18.0f);
        }
    }

    @Override
    protected float calculateEngagement() {
        float e = 0.58f;
        return MathHelper.clamp(e, 0f, 1f);
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Calm social contagion; allow contagion to two nearby pets via stimulus system naturally
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.10f)
            .add(woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.08f)
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.015f)
            .build();
    }

    private static int secondsToTicks(int s) {
        return Math.max(0, s * 20);
    }
}