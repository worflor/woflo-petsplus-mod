package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.OcelotEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.context.perception.PerceptionStimulusType;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.EmotionFeedback;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.traits.SpeciesProfile;
import woflo.petsplus.ai.traits.SpeciesTraits;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;
import woflo.petsplus.util.TriggerConditions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Crowd-aware lookout behaviour that posts a sentry when the local pack looks vulnerable.
 *
 * <p>The goal is intentionally conservative – it activates only for pets with protective
 * tendencies (species/role/nature) when the cached pack context reports hostiles nearby or
 * allies spread thin. While active the pet anchors to a vantage point, keeps nearby allies
 * close, and only hands control to combat when a safe, high-priority threat breaches the
 * perimeter. Combat is delegated to the base AI to respect existing attack heuristics.</p>
 */
public class PackSentinelWatchGoal extends AdaptiveGoal {

    private static final double ALERT_RADIUS = 18.0D;
    private static final double ALERT_RADIUS_Y = 10.0D;
    private static final double ENGAGE_RADIUS = 11.0D;
    private static final double SUPPORT_RADIUS = 12.0D;
    private static final double CLOSE_ENOUGH_SQ = 2.4D;
    private static final float MAX_HEALTH_RATIO = 3.25F;
    private static final long RECENT_STIMULUS_WINDOW = 200L;
    private static final int MIN_DURATION = 120;
    private static final int MAX_DURATION = 220;
    private static final float MIN_BOND_GATE = 0.32F;
    private static final float MIN_HEALTH_THRESHOLD = 0.48F;
    private static final float PREFERRED_HEALTH_THRESHOLD = 0.64F;

    private record SentinelProfile(float readiness, boolean guardianInclined, boolean sentinelNature, float cautionBias) {
    }

    private Vec3d sentryPoint;
    private LivingEntity trackedThreat;
    private MobEntity supportAlly;
    private List<MobEntity> cachedPackmates = List.of();
    private int ticks;
    private float sentinelReadiness;

    public PackSentinelWatchGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.PACK_SENTINEL_WATCH), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        PetContext context = getContext();
        sentinelReadiness = 0.0F;
        if (context == null) {
            return false;
        }

        if (context.owner() == null) {
            return false;
        }

        if (mob instanceof TameableEntity tameable && tameable.isSitting()) {
            return false;
        }

        if (!hasSufficientHealth()) {
            return false;
        }

        float bond = MathHelper.clamp(context.bondStrength(), 0.0F, 1.0F);
        SentinelProfile profile = evaluateSentinelProfile(context, bond);
        sentinelReadiness = profile.readiness();

        if (!meetsBondAndLevelExpectations(context, bond, profile)) {
            return false;
        }

        float readinessThreshold = computeReadinessThreshold(context, bond, profile);
        if (sentinelReadiness < readinessThreshold) {
            return false;
        }

        if (!context.ownerNearby() && context.distanceToOwner() > 24.0F) {
            return false;
        }

        cachedPackmates = List.copyOf(findPackmates(context));

        PetContextCrowdSummary summary = context.crowdSummary();
        boolean hostilesNearby = summary != null
            && summary.hostileCount() > 0
            && summary.nearestHostileDistance() <= ALERT_RADIUS;

        boolean packSpreadThin = isPackSpreadThin(context, cachedPackmates);
        boolean recentCrowdStimulus = hasRecentCrowdStimulus(context);

        if (!(hostilesNearby || (packSpreadThin && recentCrowdStimulus))) {
            return false;
        }

        LivingEntity candidateThreat = locatePriorityThreat(context.owner(), cachedPackmates);
        if (candidateThreat != null && !isSafeToConsider(candidateThreat)) {
            return false;
        }

        sentryPoint = computeSentryPoint(context, candidateThreat);
        trackedThreat = candidateThreat;
        supportAlly = selectSupportAlly(cachedPackmates, candidateThreat);

        return sentryPoint != null;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (sentryPoint == null) {
            return false;
        }
        if (ticks >= MAX_DURATION) {
            return false;
        }

        PetContext context = getContext();
        if (context == null || context.owner() == null) {
            return false;
        }

        // Refresh packmates every few seconds to keep the roster accurate.
        if (ticks % 40 == 0) {
            cachedPackmates = List.copyOf(findPackmates(context));
            if (supportAlly != null && (supportAlly.isRemoved() || !supportAlly.isAlive())) {
                supportAlly = null;
            }
            if (supportAlly == null) {
                supportAlly = selectSupportAlly(cachedPackmates, trackedThreat);
            }
        }

        boolean stillThin = isPackSpreadThin(context, cachedPackmates);
        boolean hostilesNearby = context.crowdSummary() != null
            && context.crowdSummary().hostileCount() > 0
            && context.crowdSummary().nearestHostileDistance() <= ALERT_RADIUS;

        if (ticks < MIN_DURATION) {
            return true;
        }

        if (!(stillThin || hostilesNearby)) {
            // Stand down if the pack has recovered and no threats are in range.
            return false;
        }

        if (trackedThreat != null && (!trackedThreat.isAlive() || trackedThreat.isRemoved())) {
            trackedThreat = null;
        }

        if (trackedThreat == null || ticks % 20 == 0) {
            LivingEntity refreshed = locatePriorityThreat(context.owner(), cachedPackmates);
            if (refreshed != null && isSafeToConsider(refreshed)) {
                trackedThreat = refreshed;
            }
        }

        return true;
    }

    @Override
    protected void onStartGoal() {
        ticks = 0;
        if (sentryPoint != null) {
            moveTowards(sentryPoint, 1.05D);
        }
        callSupportToPosition();
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        ticks = 0;
        sentryPoint = null;
        trackedThreat = null;
        supportAlly = null;
        cachedPackmates = List.of();
        sentinelReadiness = 0.0F;
    }

    @Override
    protected void onTickGoal() {
        ticks++;

        PetContext context = getContext();
        if (context != null) {
            if (trackedThreat != null) {
                Vec3d updated = computeSentryPoint(context, trackedThreat);
                if (updated != null) {
                    sentryPoint = updated;
                }
            } else if (sentryPoint == null) {
                sentryPoint = computeSentryPoint(context, null);
            }
        }

        if (sentryPoint != null && mob.squaredDistanceTo(sentryPoint) > CLOSE_ENOUGH_SQ) {
            moveTowards(sentryPoint, 1.05D);
        } else {
            mob.getNavigation().stop();
        }

        callSupportToPosition();

        if (trackedThreat != null) {
            mob.getLookControl().lookAt(trackedThreat, 30.0F, 30.0F);
            if (ticks % 10 == 0 && supportAlly == null) {
                supportAlly = selectSupportAlly(cachedPackmates, trackedThreat);
            }
            if (supportAlly != null && ticks % 15 == 0) {
                coordinateSupport(supportAlly, trackedThreat);
            }

            double distanceSq = mob.squaredDistanceTo(trackedThreat);
            if (distanceSq <= ENGAGE_RADIUS * ENGAGE_RADIUS && isEngagementSafe(mob, trackedThreat)) {
                mob.setTarget(trackedThreat);
                if (supportAlly != null) {
                    coordinateSupport(supportAlly, trackedThreat);
                }
                requestStop();
            }
        }
    }

    @Override
    protected float calculateEngagement() {
        PetContext context = getContext();
        float readiness = sentinelReadiness;
        if (context != null) {
            float bond = MathHelper.clamp(context.bondStrength(), 0.0F, 1.0F);
            SentinelProfile profile = evaluateSentinelProfile(context, bond);
            readiness = profile.readiness();
            sentinelReadiness = readiness;
        }

        float base = 0.35F + MathHelper.clamp(readiness, 0.0F, 1.0F) * 0.45F;
        if (trackedThreat != null) {
            double distance = Math.sqrt(Math.max(1.0E-4D, mob.squaredDistanceTo(trackedThreat)));
            base += MathHelper.clamp((ALERT_RADIUS - distance) / ALERT_RADIUS, 0.0F, 0.3F);
        }
        if (!cachedPackmates.isEmpty()) {
            base += 0.05F + MathHelper.clamp(readiness * 0.08F, 0.0F, 0.12F);
        }
        PetComponent component = PetComponent.get(mob);
        if (component != null && component.getRoleType() != null
            && component.getRoleType().archetype() == PetRoleType.RoleArchetype.TANK) {
            base += 0.08F;
        }
        return MathHelper.clamp(base, 0.0F, 1.0F);
    }

    @Override
    protected EmotionFeedback defineEmotionFeedback() {
        return new EmotionFeedback.Builder()
            .add(PetComponent.Emotion.VIGILANT, 0.22F)
            .add(PetComponent.Emotion.FOCUSED, 0.18F)
            .add(PetComponent.Emotion.PRIDE, 0.08F)
            .build();
    }

    private boolean meetsBondAndLevelExpectations(PetContext context, float bond, SentinelProfile profile) {
        if (bond < MIN_BOND_GATE) {
            return false;
        }

        int level = Math.max(context.level(), 0);

        if (profile.guardianInclined()) {
            if (bond >= 0.8F) {
                return level >= 1;
            }
            if (bond >= 0.7F) {
                return level >= 2;
            }
            if (bond >= 0.6F) {
                return level >= 3;
            }
            return level >= 5;
        }

        if (bond >= 0.85F) {
            return level >= 5;
        }
        if (bond >= 0.75F) {
            return level >= 7;
        }
        if (bond >= 0.65F) {
            return level >= 9;
        }

        return false;
    }

    private SentinelProfile evaluateSentinelProfile(PetContext context, float bond) {
        SpeciesProfile profile = SpeciesTraits.getProfile(mob);
        PetRoleType role = context.role();
        boolean canFly = MobCapabilities.canFly(mob);
        boolean sentinelNature = context.natureId() != null && "sentinel".equals(context.natureId().getPath());

        float readiness = 0.16F;
        boolean guardianInclined = false;
        float caution = 0.0F;

        if (profile.canineLike()) {
            readiness += 0.27F;
            guardianInclined = true;
        }
        if (profile.scentDriven()) {
            readiness += 0.09F;
            guardianInclined = true;
        }
        if (profile.felineLike()) {
            readiness += 0.13F;
            guardianInclined = true;
        }
        if (profile.socialAffiliative()) {
            readiness += 0.05F;
        }
        if (canFly) {
            readiness += profile.avian() ? 0.22F : 0.12F;
            if (profile.avian() || profile.socialAffiliative()) {
                guardianInclined = true;
            }
        }

        if (role != null) {
            readiness += switch (role.archetype()) {
                case TANK -> {
                    guardianInclined = true;
                    yield 0.24F;
                }
                case SUPPORT -> {
                    guardianInclined = true;
                    yield 0.18F;
                }
                case MOBILITY -> {
                    yield 0.12F;
                }
                case DPS -> {
                    yield 0.06F;
                }
                case UTILITY -> {
                    yield 0.04F;
                }
            };
        }

        if (sentinelNature) {
            readiness += 0.18F;
            guardianInclined = true;
        }

        readiness += MathHelper.clamp(bond, 0.0F, 1.0F) * 0.46F;

        int level = Math.max(context.level(), 0);
        readiness += MathHelper.clamp(level / 28.0F, 0.0F, 0.18F);

        readiness += MathHelper.clamp((context.behavioralMomentum() - 0.45F) * 0.25F, -0.08F, 0.08F);

        BehaviouralEnergyProfile energy = context.behaviouralEnergyProfile();
        if (energy != null) {
            readiness += MathHelper.clamp((energy.mentalFocus() - 0.5F) * 0.35F, -0.1F, 0.1F);
            readiness += MathHelper.clamp((energy.socialCharge() - 0.45F) * 0.25F, -0.06F, 0.06F);
        }

        if (!context.ownerNearby()) {
            readiness -= 0.08F;
            caution += 0.03F;
            if (context.distanceToOwner() > 20.0F) {
                readiness -= 0.12F;
                caution += 0.04F;
            }
        }

        if (profile.playfulSpecies() && !guardianInclined) {
            readiness -= 0.2F;
            caution += 0.08F;
        }
        if (profile.groomingSpecies() && !guardianInclined) {
            readiness -= 0.09F;
            caution += 0.04F;
        }
        if (profile.aquatic() && !canFly) {
            readiness -= 0.25F;
            caution += 0.08F;
        }
        if (profile.sunBasker() && !guardianInclined) {
            readiness -= 0.05F;
            caution += 0.03F;
        }
        if (mob.getWidth() < 0.45F) {
            readiness -= 0.05F;
            caution += 0.03F;
        }
        if (!guardianInclined) {
            readiness -= 0.18F;
            caution += 0.05F;
        }

        return new SentinelProfile(MathHelper.clamp(readiness, 0.0F, 1.0F), guardianInclined, sentinelNature, MathHelper.clamp(caution, 0.0F, 0.25F));
    }

    private float computeReadinessThreshold(PetContext context, float bond, SentinelProfile profile) {
        float t = MathHelper.clamp(1.0F - MathHelper.clamp(bond, 0.0F, 1.0F), 0.0F, 1.0F);
        float threshold = MathHelper.lerp(t, 0.42F, 0.74F);

        if (profile.guardianInclined()) {
            threshold -= 0.07F;
        }
        if (profile.sentinelNature()) {
            threshold -= 0.05F;
        }

        threshold += profile.cautionBias();

        int level = Math.max(context.level(), 0);
        threshold -= MathHelper.clamp(level / 30.0F, 0.0F, 0.07F);

        float healthRatio = getHealthRatio(mob);
        if (healthRatio < PREFERRED_HEALTH_THRESHOLD) {
            threshold += MathHelper.clamp((PREFERRED_HEALTH_THRESHOLD - healthRatio) * 0.45F, 0.0F, 0.12F);
        }

        BehaviouralEnergyProfile energy = context.behaviouralEnergyProfile();
        if (energy != null) {
            float focusPenalty = MathHelper.clamp(0.55F - energy.mentalFocus(), 0.0F, 0.25F);
            threshold += focusPenalty * 0.35F;
        }

        if (!context.ownerNearby()) {
            threshold += 0.05F;
        }

        return MathHelper.clamp(threshold, 0.36F, 0.82F);
    }

    private boolean hasSufficientHealth() {
        return getHealthRatio(mob) >= MIN_HEALTH_THRESHOLD;
    }

    private float getHealthRatio(LivingEntity entity) {
        float maxHealth = entity.getMaxHealth();
        if (maxHealth <= 0.0F) {
            return 1.0F;
        }
        return MathHelper.clamp(entity.getHealth() / maxHealth, 0.0F, 1.0F);
    }

    private boolean isPackSpreadThin(PetContext context, List<MobEntity> packmates) {
        PetContextCrowdSummary summary = context.crowdSummary();
        int nearbyAllies = summary != null ? summary.friendlyCount() : 0;
        int hostileCount = summary != null ? summary.hostileCount() : 0;
        double nearestFriendly = summary != null ? summary.nearestFriendlyDistance() : Double.POSITIVE_INFINITY;

        boolean fewPackmates = packmates.size() <= 1;
        boolean alliesFar = Double.isFinite(nearestFriendly) && nearestFriendly > 8.0D;
        boolean hostilesOutnumber = hostileCount > 0 && hostileCount >= Math.max(1, nearbyAllies);

        return fewPackmates || alliesFar || hostilesOutnumber;
    }

    private boolean hasRecentCrowdStimulus(PetContext context) {
        PetComponent component = context.component();
        if (component != null) {
            long lastCrowdTick = component.getLastCrowdStimulusTick();
            if (lastCrowdTick != Long.MIN_VALUE && context.worldTime() - lastCrowdTick <= RECENT_STIMULUS_WINDOW) {
                return true;
            }
        }

        StimulusSnapshot snapshot = context.stimuli();
        if (snapshot == null || snapshot.isEmpty()) {
            return false;
        }
        for (StimulusSnapshot.Event event : snapshot.events()) {
            if (event == null || event.ageTicks() > RECENT_STIMULUS_WINDOW) {
                continue;
            }
            if (PerceptionStimulusType.CROWD_SUMMARY.equals(event.type())) {
                return true;
            }
        }
        return false;
    }

    private List<MobEntity> findPackmates(PetContext context) {
        List<MobEntity> packmates = new ArrayList<>();
        List<Entity> nearby = context.nearbyEntities();
        if (nearby == null || nearby.isEmpty()) {
            return packmates;
        }

        UUID ownerId = null;
        PetComponent component = context.component();
        if (component != null) {
            ownerId = component.getOwnerUuid();
        }
        if (ownerId == null && context.owner() != null) {
            ownerId = context.owner().getUuid();
        }

        for (Entity entity : nearby) {
            if (!(entity instanceof MobEntity mobEntity)) {
                continue;
            }
            if (mobEntity == mob || !mobEntity.isAlive() || mobEntity.isRemoved()) {
                continue;
            }
            if (isSameOwner(mobEntity, context.owner(), ownerId)) {
                packmates.add(mobEntity);
            }
        }

        return packmates;
    }

    private boolean isSameOwner(MobEntity candidate, @Nullable LivingEntity owner, @Nullable UUID ownerId) {
        PetComponent component = PetComponent.get(candidate);
        if (component != null && component.getOwnerUuid() != null) {
            return ownerId != null && ownerId.equals(component.getOwnerUuid());
        }
        if (candidate instanceof TameableEntity tameable && tameable.isTamed()) {
            LivingEntity tameOwner = tameable.getOwner();
            if (tameOwner != null) {
                if (ownerId != null && ownerId.equals(tameOwner.getUuid())) {
                    return true;
                }
                return owner != null && owner.getUuid().equals(tameOwner.getUuid());
            }
        }
        return false;
    }

    @Nullable
    private LivingEntity locatePriorityThreat(@Nullable LivingEntity owner, List<MobEntity> packmates) {
        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }
        Box search = mob.getBoundingBox().expand(ALERT_RADIUS, ALERT_RADIUS_Y, ALERT_RADIUS);
        List<HostileEntity> hostiles = serverWorld.getEntitiesByClass(HostileEntity.class, search, this::isCandidateThreat);
        if (hostiles.isEmpty()) {
            return null;
        }

        return hostiles.stream()
            .filter(Objects::nonNull)
            .filter(entity -> isSafeToConsider(entity))
            .max(Comparator.comparingDouble(entity -> threatScore(entity, owner, packmates)))
            .orElse(null);
    }

    private boolean isCandidateThreat(HostileEntity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        if (entity instanceof MobEntity mobEntity && mobEntity.getTarget() == null && mobEntity.getAttacker() == null) {
            // Idle hostiles are still relevant if they are very close.
            return mob.squaredDistanceTo(entity) <= ALERT_RADIUS * ALERT_RADIUS;
        }
        return mob.squaredDistanceTo(entity) <= ALERT_RADIUS * ALERT_RADIUS;
    }

    private double threatScore(LivingEntity entity, @Nullable LivingEntity owner, List<MobEntity> packmates) {
        double score = 0.0D;
        double distance = Math.sqrt(Math.max(1.0E-4D, mob.squaredDistanceTo(entity)));
        score -= distance; // Closer threats score higher.

        if (owner != null) {
            double ownerDistance = Math.sqrt(Math.max(1.0E-4D, owner.squaredDistanceTo(entity)));
            if (ownerDistance < ALERT_RADIUS) {
                score += 6.0D;
            }
            if (entity instanceof MobEntity mobEntity && owner.equals(mobEntity.getTarget())) {
                score += 8.0D;
            }
        }

        if (entity instanceof MobEntity mobEntity) {
            if (mob.equals(mobEntity.getTarget())) {
                score += 5.0D;
            }
            for (MobEntity ally : packmates) {
                if (ally == null || ally.isRemoved() || !ally.isAlive()) {
                    continue;
                }
                if (ally.equals(mobEntity.getTarget())) {
                    score += 4.0D;
                }
                double allyDistance = Math.sqrt(Math.max(1.0E-4D, ally.squaredDistanceTo(entity)));
                if (allyDistance < distance) {
                    score += 1.5D;
                }
            }
        }

        if (entity instanceof HostileEntity hostile && hostile.isAttacking()) {
            score += 1.0D;
        }

        return score;
    }

    private Vec3d computeSentryPoint(PetContext context, @Nullable LivingEntity threat) {
        Vec3d ownerPos = context.owner() != null
            ? new Vec3d(context.owner().getX(), context.owner().getY(), context.owner().getZ())
            : new Vec3d(mob.getX(), mob.getY(), mob.getZ());
        if (threat == null) {
            return ownerPos;
        }

        Vec3d threatPos = new Vec3d(threat.getX(), threat.getY(), threat.getZ());
        Vec3d delta = ownerPos.subtract(threatPos);
        if (delta.lengthSquared() < 1.0E-4D) {
            return ownerPos;
        }
        Vec3d direction = delta.normalize();
        double offset = Math.min(6.0D, threatPos.distanceTo(ownerPos) * 0.5D);
        Vec3d point = threatPos.add(direction.multiply(offset));
        return new Vec3d(point.x, ownerPos.y, point.z);
    }

    private boolean isSafeToConsider(LivingEntity threat) {
        if (threat == null) {
            return true;
        }
        if (TriggerConditions.isBossEntity(threat)) {
            return false;
        }
        return true;
    }

    private boolean isEngagementSafe(MobEntity fighter, LivingEntity threat) {
        if (fighter == null || threat == null) {
            return false;
        }
        if (!isSafeToConsider(threat)) {
            return false;
        }
        float fighterHealth = fighter.getMaxHealth();
        float threatHealth = threat instanceof LivingEntity living ? living.getMaxHealth() : 20.0F;
        if (fighterHealth <= 0.0F) {
            return false;
        }
        float ratio = threatHealth / fighterHealth;
        if (ratio > MAX_HEALTH_RATIO) {
            return false;
        }
        return true;
    }

    private void moveTowards(Vec3d point, double speed) {
        EntityNavigation navigation = mob.getNavigation();
        if (navigation == null || navigation.isFollowingPath()) {
            return;
        }
        navigation.startMovingTo(point.x, point.y, point.z, speed);
    }

    private void callSupportToPosition() {
        if (supportAlly == null || sentryPoint == null) {
            return;
        }
        if (!supportAlly.isAlive() || supportAlly.isRemoved()) {
            supportAlly = null;
            return;
        }
        if (!Objects.equals(supportAlly.getEntityWorld(), mob.getEntityWorld())) {
            return;
        }
        if (supportAlly.squaredDistanceTo(sentryPoint) > SUPPORT_RADIUS * SUPPORT_RADIUS) {
            EntityNavigation navigation = supportAlly.getNavigation();
            if (navigation != null) {
                navigation.startMovingTo(sentryPoint.x, sentryPoint.y, sentryPoint.z, 1.05D);
            }
        }
    }

    private void coordinateSupport(MobEntity ally, LivingEntity threat) {
        if (ally == null || threat == null || !ally.isAlive() || ally.isRemoved()) {
            return;
        }
        if (!isEngagementSafe(ally, threat)) {
            return;
        }
        if (ally.getTarget() == null && ally.getAttacker() == null) {
            ally.setTarget(threat);
        }
    }

    private MobEntity selectSupportAlly(List<MobEntity> packmates, @Nullable LivingEntity threat) {
        if (packmates == null || packmates.isEmpty()) {
            return null;
        }

        if (threat instanceof net.minecraft.entity.mob.CreeperEntity) {
            for (MobEntity mate : packmates) {
                if (mate instanceof CatEntity || mate instanceof OcelotEntity) {
                    if (canAssist(mate)) {
                        return mate;
                    }
                }
            }
        }

        boolean threatIsFlying = threat instanceof MobEntity mobThreat && MobCapabilities.canFly(mobThreat);
        if (threatIsFlying) {
            for (MobEntity mate : packmates) {
                if (MobCapabilities.canFly(mate) && canAssist(mate)) {
                    return mate;
                }
            }
        }

        return packmates.stream()
            .filter(this::canAssist)
            .min(Comparator.comparingDouble(mate -> mate.squaredDistanceTo(mob)))
            .orElse(null);
    }

    private boolean canAssist(MobEntity ally) {
        if (ally == null || !ally.isAlive() || ally.isRemoved()) {
            return false;
        }
        if (ally.getTarget() != null || ally.getAttacker() != null) {
            return false;
        }

        PetComponent component = PetComponent.get(ally);
        if (component != null) {
            float bond = MathHelper.clamp((float) (component.getBondStrength() / 100.0), 0.0F, 1.0F);
            if (bond < 0.4F) {
                return false;
            }
        }

        return true;
    }
}
