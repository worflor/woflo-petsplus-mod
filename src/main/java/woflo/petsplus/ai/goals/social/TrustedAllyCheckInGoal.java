package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.social.SocialSnapshot;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.relationships.InteractionType;
import woflo.petsplus.state.relationships.RelationshipProfile;
import woflo.petsplus.state.relationships.RelationshipType;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

/**
 * Short micro-interaction where a pet checks in on a trusted nearby ally that
 * currently has low comfort or has gone without a recent bonding interaction.
 * The pet approaches, offers gentle reassurance, and briefly exchanges a
 * comforting gesture before returning control to other behaviours.
 */
public class TrustedAllyCheckInGoal extends AdaptiveGoal {
    private static final float MIN_TRUST = 0.45f;
    private static final float COMFORT_ATTENTION_THRESHOLD = 0.58f;
    private static final float HARMONY_FLOOR = -0.25f;
    private static final long MIN_ATTENTION_GAP_TICKS = 2400L; // 2 minutes
    private static final double MAX_TARGET_DISTANCE_SQ = 100.0d; // 10 blocks
    private static final double ARRIVAL_DISTANCE_SQ = 4.0d; // within 2 blocks
    private static final int MAX_ACTIVE_TICKS = 160;
    private static final int REASSURANCE_WARMUP_TICKS = 18;
    private static final int REASSURANCE_WINDOW_TICKS = 60;

    private MobEntity pendingCandidate;
    private MobEntity activeTarget;
    private UUID activeTargetId;
    private Vec3d approachOffset = Vec3d.ZERO;
    private int reassuranceTicks;
    private boolean reassuranceTriggered;

    public TrustedAllyCheckInGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.TRUSTED_ALLY_CHECK_IN), EnumSet.of(Control.MOVE));
    }

    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        if (ctx == null || ctx.dormant()) {
            return false;
        }

        if (!hasEnergyForCheckIn(ctx)) {
            return false;
        }

        pendingCandidate = findCandidate(ctx);
        return pendingCandidate != null;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (activeTarget == null || !activeTarget.isAlive()) {
            return false;
        }

        if (getActiveTicks() >= MAX_ACTIVE_TICKS) {
            return false;
        }

        MobEntity mobEntity = this.mob;
        if (mobEntity == null || mobEntity.getEntityWorld() == null) {
            return false;
        }

        double distanceSq = mobEntity.squaredDistanceTo(activeTarget);
        if (distanceSq > MAX_TARGET_DISTANCE_SQ * 1.25d) {
            return false;
        }

        PetContext ctx = getContext();
        return ctx != null && stillNeedsAttention(ctx, activeTarget);
    }

    @Override
    protected void onStartGoal() {
        activeTarget = pendingCandidate;
        pendingCandidate = null;
        reassuranceTicks = 0;
        reassuranceTriggered = false;

        if (activeTarget != null) {
            activeTargetId = activeTarget.getUuid();
            approachOffset = computeApproachOffset(activeTarget);
        } else {
            activeTargetId = null;
            approachOffset = Vec3d.ZERO;
        }
    }

    @Override
    protected void onStopGoal() {
        MobEntity mobEntity = this.mob;
        if (mobEntity != null) {
            mobEntity.getNavigation().stop();
        }
        activeTarget = null;
        activeTargetId = null;
        pendingCandidate = null;
        approachOffset = Vec3d.ZERO;
        reassuranceTicks = 0;
        reassuranceTriggered = false;
    }

    @Override
    protected void onTickGoal() {
        if (activeTarget == null) {
            requestStop();
            return;
        }

        MobEntity mobEntity = this.mob;
        if (mobEntity == null) {
            requestStop();
            return;
        }

        double distanceSq = mobEntity.squaredDistanceTo(activeTarget);
        if (distanceSq > ARRIVAL_DISTANCE_SQ) {
            reassuranceTicks = 0;
            reassuranceTriggered = false;

            Vec3d anchor = targetAnchorPosition(activeTarget);
            steerToward(anchor, distanceSq);
        } else {
            holdReassuranceStance();
        }
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(PetComponent.Emotion.UBUNTU, 0.22f)
            .add(PetComponent.Emotion.RELIEF, 0.18f)
            .add(PetComponent.Emotion.LOYALTY, 0.14f)
            .add(PetComponent.Emotion.QUERECIA, 0.10f)
            .withContagion(PetComponent.Emotion.UBUNTU, 0.02f)
            .build();
    }

    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        if (ctx == null) {
            return 0.55f;
        }

        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);
        float stamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);

        float socialBias = MathHelper.clamp((socialCharge - 0.30f) / 0.35f, 0.0f, 1.0f);
        float staminaBoost = MathHelper.clamp((stamina - 0.25f) / 0.40f, 0.0f, 1.0f);

        float engagement = MathHelper.lerp(socialBias, 0.52f, 0.92f);
        engagement *= MathHelper.lerp(staminaBoost, 0.84f, 1.06f);

        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(PetComponent.Mood.BONDED, 0.35f)) {
            engagement += 0.08f;
        }
        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }

    private boolean hasEnergyForCheckIn(PetContext ctx) {
        PetComponent component = petComponent;
        if (component == null) {
            return true;
        }

        if (component.hasMoodAbove(PetComponent.Mood.ANGRY, 0.35f)) {
            return false;
        }

        if (component.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.65f)) {
            return false;
        }

        return ctx.socialCharge() >= 0.25f;
    }

    @Nullable
    private MobEntity findCandidate(PetContext ctx) {
        MobEntity mobEntity = this.mob;
        if (mobEntity == null) {
            return null;
        }

        SocialSnapshot snapshot = ctx.socialSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }

        Map<UUID, SocialSnapshot.Edge> edges = snapshot.edges();
        if (edges.isEmpty()) {
            return null;
        }

        Pair<MobEntity, Double> best = null;
        for (Entity entity : ctx.nearbyEntities()) {
            if (!(entity instanceof MobEntity candidate)) {
                continue;
            }
            if (candidate == mobEntity || !candidate.isAlive()) {
                continue;
            }
            SocialSnapshot.Edge edge = edges.get(candidate.getUuid());
            if (edge == null) {
                continue;
            }
            if (!isRelationshipEligible(edge)) {
                continue;
            }
            double distanceSq = mobEntity.squaredDistanceTo(candidate);
            if (distanceSq > MAX_TARGET_DISTANCE_SQ) {
                continue;
            }
            if (!relationshipAligned(candidate, edge, distanceSq)) {
                continue;
            }
            double score = scoreCandidate(edge, distanceSq, mobEntity);
            if (best == null || score > best.getRight()) {
                best = new Pair<>(candidate, score);
            }
        }

        return best != null ? best.getLeft() : null;
    }

    private boolean stillNeedsAttention(PetContext ctx, MobEntity target) {
        SocialSnapshot snapshot = ctx.socialSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            return false;
        }

        SocialSnapshot.Edge edge = snapshot.edges().get(target.getUuid());
        if (edge == null) {
            return false;
        }

        if (!isRelationshipEligible(edge)) {
            return false;
        }

        return relationshipAligned(target, edge, this.mob != null ? this.mob.squaredDistanceTo(target) : Double.POSITIVE_INFINITY);
    }

    private boolean isRelationshipEligible(SocialSnapshot.Edge edge) {
        if (edge == null || edge.trust() < MIN_TRUST) {
            return false;
        }
        RelationshipType type = edge.type();
        return type == RelationshipType.FRIEND
            || type == RelationshipType.COMPANION
            || type == RelationshipType.FUN_ACQUAINTANCE
            || type == RelationshipType.TRUSTED_AUTHORITY;
    }

    private boolean relationshipAligned(MobEntity candidate, SocialSnapshot.Edge edge, double distanceSq) {
        MobEntity mobEntity = this.mob;
        if (mobEntity == null) {
            return false;
        }

        PetComponent selfComponent = petComponent;
        if (selfComponent == null) {
            selfComponent = PetComponent.get(mobEntity);
        }
        PetComponent allyComponent = PetComponent.get(candidate);

        if (selfComponent != null && allyComponent != null) {
            UUID owner = selfComponent.getOwnerUuid();
            UUID allyOwner = allyComponent.getOwnerUuid();
            if (owner != null && allyOwner != null && !owner.equals(allyOwner)) {
                return false;
            }
        }

        long worldTime = 0L;
        if (mobEntity.getEntityWorld() != null) {
            worldTime = mobEntity.getEntityWorld().getTime();
        }

        if (!candidateNeedsAttention(edge, candidate.getUuid(), worldTime, selfComponent)) {
            return false;
        }

        PetComponent.HarmonyCompatibility compatibility = edge.harmonyCompatibility();
        if (compatibility != null && compatibility.netHarmony() < HARMONY_FLOOR) {
            return false;
        }

        return true;
    }

    private boolean candidateNeedsAttention(SocialSnapshot.Edge edge, UUID candidateId, long worldTime, @Nullable PetComponent selfComponent) {
        boolean comfortLow = edge.comfort() <= COMFORT_ATTENTION_THRESHOLD;
        if (comfortLow) {
            return true;
        }

        if (selfComponent == null) {
            return true;
        }

        RelationshipProfile profile = selfComponent.getRelationshipWith(candidateId);
        if (profile == null) {
            return true;
        }

        long lastInteraction = profile.lastInteractionTick();
        if (lastInteraction <= 0L || worldTime <= 0L) {
            return true;
        }

        long sinceInteraction = Math.abs(worldTime - lastInteraction);
        return sinceInteraction >= MIN_ATTENTION_GAP_TICKS;
    }

    private double scoreCandidate(SocialSnapshot.Edge edge, double distanceSq, MobEntity mobEntity) {
        double trustScore = MathHelper.clamp(edge.trust(), -1.0f, 1.0f) + 1.0;
        double comfortNeed = MathHelper.clamp(COMFORT_ATTENTION_THRESHOLD - edge.comfort(), 0.0f, 1.0f);
        double proximity = 1.0 / Math.max(1.0, Math.sqrt(distanceSq));

        double harmonyBonus = 0.15d;
        PetComponent.HarmonyCompatibility compatibility = edge.harmonyCompatibility();
        if (compatibility != null) {
            harmonyBonus += Math.max(0.0f, compatibility.netHarmony()) * 0.2f;
        }

        int hash = mobEntity.getUuid().hashCode() ^ edge.hashCode();
        double variety = ((hash & 0xff) / 255.0d) * 0.05d;

        return trustScore * 0.9 + comfortNeed * 1.4 + proximity * 0.9 + harmonyBonus + variety;
    }

    private Vec3d targetAnchorPosition(MobEntity target) {
        Vec3d targetPos = target.getEntityPos();
        if (approachOffset == Vec3d.ZERO) {
            approachOffset = computeApproachOffset(target);
        }
        return targetPos.add(approachOffset);
    }

    private Vec3d computeApproachOffset(MobEntity target) {
        MobEntity mobEntity = this.mob;
        if (mobEntity == null) {
            return Vec3d.ZERO;
        }

        int hash = mobEntity.getUuid().hashCode() ^ target.getUuid().hashCode();
        double angle = ((hash & 0xFFFF) / 65535.0d) * MathHelper.TAU;
        double radius = Math.max(1.35d, (mobEntity.getWidth() + target.getWidth()) * 0.75d);

        double ox = Math.cos(angle) * radius;
        double oz = Math.sin(angle) * radius;

        return new Vec3d(ox, 0.0, oz);
    }

    private void steerToward(Vec3d anchor, double distanceSq) {
        MobEntity mobEntity = this.mob;
        if (mobEntity == null) {
            return;
        }

        double speed = computeApproachSpeed(distanceSq);
        if ((getActiveTicks() & 3) == 0 || mobEntity.getNavigation().isIdle()) {
            orientTowards(anchor.x, anchor.y + 0.2d, anchor.z, 32.0f, 24.0f, 18.0f);
            EntityNavigation navigation = mobEntity.getNavigation();
            navigation.startMovingTo(anchor.x, anchor.y, anchor.z, speed);
        }
    }

    private double computeApproachSpeed(double distanceSq) {
        if (distanceSq > 49.0d) {
            return 1.08d;
        }
        if (distanceSq > 25.0d) {
            return 0.94d;
        }
        return 0.82d;
    }

    private void holdReassuranceStance() {
        MobEntity mobEntity = this.mob;
        if (mobEntity == null || activeTarget == null) {
            requestStop();
            return;
        }

        mobEntity.getNavigation().stop();
        orientTowards(activeTarget, 42.0f, 22.0f, 10.0f);
        reassuranceTicks++;

        if (!reassuranceTriggered && reassuranceTicks >= REASSURANCE_WARMUP_TICKS) {
            triggerReassurancePulse();
        }

        if (reassuranceTicks >= REASSURANCE_WINDOW_TICKS) {
            requestStop();
        }
    }

    private void triggerReassurancePulse() {
        if (reassuranceTriggered || activeTarget == null) {
            return;
        }
        reassuranceTriggered = true;

        MobEntity mobEntity = this.mob;
        if (mobEntity == null) {
            return;
        }

        if (mobEntity.getEntityWorld() != null) {
            mobEntity.getEntityWorld().sendEntityStatus(activeTarget, (byte) 7);
        }

        if (petComponent != null && activeTargetId != null) {
            petComponent.recordEntityInteraction(
                activeTargetId,
                InteractionType.GENTLE_APPROACH,
                1.0f,
                0.9f,
                0.8f
            );
        }

        PetComponent allyComponent = PetComponent.get(activeTarget);
        if (allyComponent != null) {
            allyComponent.pushEmotion(PetComponent.Emotion.RELIEF, 0.22f);
            allyComponent.pushEmotion(PetComponent.Emotion.UBUNTU, 0.18f);
            allyComponent.pushEmotion(PetComponent.Emotion.LAGOM, 0.12f);
        }

        ServerWorld serverWorld = mobEntity.getEntityWorld() instanceof ServerWorld sw ? sw : null;
        if (serverWorld != null) {
            woflo.petsplus.ui.FeedbackManager.emitFeedback("pet_generic_happy", activeTarget, serverWorld);
        }
    }
}
