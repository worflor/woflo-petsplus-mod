package woflo.petsplus.ai.goals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.OwnerCombatState.AggroSnapshot;
import woflo.petsplus.state.PetComponent;

/**
 * Mirrors the vanilla wolf "assist owner" behaviour but layers in light mood checks
 * so pets still feel individual without thrashing their navigation.
 */
public class OwnerAssistAttackGoal extends Goal {
    private static final int SNAPSHOT_REFRESH_TICKS = 20;
    private static final int PATH_RECALC_MIN = 4;
    private static final int PATH_RECALC_MAX = 12;
    private static final int TARGET_LOSS_GRACE_TICKS = 30;
    private static final int AGGRO_REFRESH_TICKS = 60;
    private static final float MIN_START_DESIRE = 0.28f;
    private static final float MIN_STAY_DESIRE = 0.20f;
    private static final float FEAR_BLOCK_THRESHOLD = 0.92f;
    private static final String STATE_ASSIST_HESITATION_UNTIL = "assist_hesitation_until";
    private static final String STATE_ASSIST_REGROUP_UNTIL = "assist_regroup_until";
    private static final int ASSIST_HESITATION_TICKS = 40;
    private static final int ASSIST_REGROUP_TICKS = 60;
    private static final double PACK_SUPPORT_RADIUS = 6.0d;
    private static final double PACK_OWNER_RADIUS = 8.0d;
    private static final double PACK_DESIRED_SPACING = 2.4d;
    private static final double PACK_MAX_SPACING = 6.5d;

    private final MobEntity mob;
    private final PetComponent petComponent;

    private @Nullable PlayerEntity owner;
    private @Nullable LivingEntity assistTarget;
    private float assistDesire;
    private int pathRecalcCooldown;
    private int snapshotCooldown;
    private int lostTargetGrace;
    private MoodSample moodSample = MoodSample.EMPTY;
    private PackSample packSample = PackSample.EMPTY;

    public OwnerAssistAttackGoal(MobEntity mob, PetComponent petComponent) {
        this.mob = mob;
        this.petComponent = petComponent;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.TARGET));
    }

    @Override
    public boolean canStart() {
        if (mob.getEntityWorld().isClient() || !mob.isAlive()) {
            return false;
        }

        long now = mob.getEntityWorld().getTime();
        if (isPetRegrouping(petComponent, now) || isPetHesitating(petComponent, now)) {
            return false;
        }

        owner = petComponent.getOwner();
        if (owner == null || owner.isRemoved()) {
            return false;
        }

        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }

        OwnerCombatState combatState = OwnerCombatState.get(owner);
        if (combatState == null) {
            return false;
        }

        AggroSnapshot snapshot = combatState.getAggroSnapshot(serverWorld);
        if (snapshot == null) {
            return false;
        }

        LivingEntity candidate = snapshot.target();
        if (!isValidAssistTarget(candidate)) {
            return false;
        }

        if (mob instanceof TameableEntity tameable && tameable.isSitting()) {
            return false;
        }

        moodSample = sampleMoods();
        float fear = moodSample.fear();
        if (fear > FEAR_BLOCK_THRESHOLD) {
            markAssistHesitation(petComponent, now);
            nudgeTowardOwner(owner);
            return false;
        }

        updatePackContext(candidate);
        float desire = computeAssistDesire(snapshot);
        if (desire < MIN_START_DESIRE) {
            markAssistHesitation(petComponent, now);
            return false;
        }

        assistTarget = candidate;
        assistDesire = desire;
        pathRecalcCooldown = 0;
        snapshotCooldown = SNAPSHOT_REFRESH_TICKS;
        lostTargetGrace = TARGET_LOSS_GRACE_TICKS;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (assistTarget == null || !assistTarget.isAlive() || assistTarget.isRemoved()) {
            return false;
        }
        if (owner == null || owner.isRemoved()) {
            return false;
        }
        if (mob instanceof TameableEntity tameable && tameable.isSitting()) {
            return false;
        }
        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }

        OwnerCombatState combatState = OwnerCombatState.get(owner);
        if (combatState == null) {
            return false;
        }

        AggroSnapshot snapshot = combatState.getAggroSnapshot(serverWorld);
        if (snapshot == null) {
            return lostTargetGrace-- > 0;
        }

        LivingEntity snapshotTarget = snapshot.target();
        if (snapshotTarget == null) {
            return lostTargetGrace-- > 0;
        }

        if (!isValidAssistTarget(snapshotTarget)) {
            return false;
        }

        boolean retargeted = assistTarget == null || !assistTarget.getUuid().equals(snapshotTarget.getUuid());
        if (retargeted) {
            assistTarget = snapshotTarget;
            mob.setTarget(assistTarget);
            updatePackContext(assistTarget);
            pathRecalcCooldown = 0;
            snapshotCooldown = SNAPSHOT_REFRESH_TICKS;
            lostTargetGrace = TARGET_LOSS_GRACE_TICKS;
        }

        if (!isValidAssistTarget(assistTarget)) {
            return false;
        }

        if (!retargeted) {
            updatePackContext(assistTarget);
        }

        moodSample = sampleMoods();
        float fear = moodSample.fear();
        if (fear > 0.96f) {
            markAssistRegroup(petComponent, mob.getEntityWorld().getTime());
            return false;
        }

        float desire = computeAssistDesire(snapshot);
        if (retargeted) {
            assistDesire = MathHelper.clamp(desire, 0f, 1f);
        }
        if (desire < MIN_STAY_DESIRE) {
            return false;
        }

        if (!retargeted) {
            assistDesire = MathHelper.clamp(assistDesire * 0.6f + desire * 0.4f, 0f, 1f);
        }
        lostTargetGrace = TARGET_LOSS_GRACE_TICKS;

        double leash = MathHelper.lerp(Math.min(1f, assistDesire + 0.35f), 12.0d, 24.0d);
        double leashSq = leash * leash;
        if (mob.squaredDistanceTo(owner) > leashSq && mob.squaredDistanceTo(assistTarget) > leashSq) {
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        if (assistTarget != null) {
            mob.setTarget(assistTarget);
        }
        pathRecalcCooldown = 0;
        snapshotCooldown = SNAPSHOT_REFRESH_TICKS;
        lostTargetGrace = TARGET_LOSS_GRACE_TICKS;
        moodSample = sampleMoods();

        if (owner != null && mob.getEntityWorld() instanceof ServerWorld serverWorld && assistTarget != null) {
            OwnerCombatState combatState = OwnerCombatState.get(owner);
            if (combatState != null) {
                combatState.extendAggroIfMatching(assistTarget, serverWorld.getTime(), AGGRO_REFRESH_TICKS,
                    assistDesire, assistDesire, true);
            }
        }
    }

    @Override
    public void stop() {
        if (assistTarget != null && mob.getTarget() == assistTarget) {
            mob.setTarget(null);
        }
        assistTarget = null;
        owner = null;
        assistDesire = 0f;
        pathRecalcCooldown = 0;
        snapshotCooldown = 0;
        lostTargetGrace = 0;
        moodSample = MoodSample.EMPTY;
        packSample = PackSample.EMPTY;
    }

    @Override
    public void tick() {
        if (assistTarget == null) {
            return;
        }

        mob.getLookControl().lookAt(assistTarget, 30.0F, 30.0F);

        if (--snapshotCooldown <= 0) {
            snapshotCooldown = SNAPSHOT_REFRESH_TICKS;
            refreshSnapshot();
        }

        if (--pathRecalcCooldown <= 0) {
            pathRecalcCooldown = computePathCooldown();
            moodSample = sampleMoods();
            double speed = computeApproachSpeed();
            if (!mob.getNavigation().isFollowingPath() || shouldIssueNewPath()) {
                mob.getNavigation().startMovingTo(assistTarget, speed);
            } else {
                mob.getNavigation().setSpeed(speed);
            }
        }
    }

    private void refreshSnapshot() {
        if (owner == null || assistTarget == null || !(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        OwnerCombatState combatState = OwnerCombatState.get(owner);
        if (combatState == null) {
            return;
        }
        AggroSnapshot snapshot = combatState.getAggroSnapshot(serverWorld);
        if (snapshot == null || snapshot.target() == null) {
            return;
        }

        LivingEntity snapshotTarget = snapshot.target();
        if (!isValidAssistTarget(snapshotTarget)) {
            return;
        }
        if (assistTarget == null || !assistTarget.getUuid().equals(snapshotTarget.getUuid())) {
            assistTarget = snapshotTarget;
            mob.setTarget(assistTarget);
        }

        moodSample = sampleMoods();
        updatePackContext(assistTarget);
        float updated = computeAssistDesire(snapshot);
        assistDesire = MathHelper.clamp(assistDesire * 0.7f + updated * 0.3f, 0f, 1f);
        combatState.extendAggroIfMatching(assistTarget, serverWorld.getTime(), AGGRO_REFRESH_TICKS,
            assistDesire, assistDesire, true);
    }

    private boolean shouldIssueNewPath() {
        if (assistTarget == null) {
            return false;
        }
        Vec3d targetPos = assistTarget.getEntityPos();
        double distanceSq = mob.squaredDistanceTo(targetPos);
        if (packSample.cohesion() < 0.2f) {
            return true;
        }
        return distanceSq > 4.0d || mob.getNavigation().isIdle();
    }

    private int computePathCooldown() {
        int interval = PATH_RECALC_MAX - Math.round(assistDesire * (PATH_RECALC_MAX - PATH_RECALC_MIN));
        if (packSample.cohesion() < 0.25f) {
            interval = Math.max(PATH_RECALC_MIN, interval - 2);
        }
        return MathHelper.clamp(interval, PATH_RECALC_MIN, PATH_RECALC_MAX);
    }

    private double computeApproachSpeed() {
        double baseSpeed = computeBaseAssistSpeed(mob);

        double positive = moodSample.speedDrive();
        double packDragComponent = packSample.confidence() > 0f ? (1.0d - packSample.cohesion()) * 0.20d : 0.0d;
        double drag = moodSample.speedDrag() + packDragComponent;

        double packBoost = 0.18d * packSample.confidence() + 0.12d * packSample.assistMomentum();
        double guardTempo = packSample.guardBias() >= 0f
            ? packSample.guardBias() * 0.18d
            : packSample.guardBias() * 0.28d;
        double speed = baseSpeed + 0.35d * assistDesire + positive * 0.25d + packBoost + guardTempo - drag * 0.3d;
        return MathHelper.clamp(speed, 0.6d, 1.8d);
    }

    private float computeAssistDesire(AggroSnapshot snapshot) {
        float drive = moodSample.assistDrive();
        float inhibition = moodSample.assistInhibition();
        float ownerSignal = 0.35f * snapshot.aggressionSignal() + 0.25f * snapshot.urgencySignal();

        float packSupport = MathHelper.clamp(packSample.confidence() * 0.6f + packSample.assistMomentum() * 0.4f, 0f, 1f);
        float packDrag = packSample.confidence() > 0f
            ? MathHelper.clamp((1f - packSample.cohesion()) * 0.35f, 0f, 0.35f)
            : 0f;

        float guardAssist = packSample.guardBias() >= 0f ? packSample.guardBias() * 0.14f : packSample.guardBias() * 0.08f;
        float desire = 0.18f + drive + ownerSignal + 0.18f * packSupport - inhibition - packDrag + guardAssist;
        return MathHelper.clamp(desire, 0f, 1f);
    }

    private boolean isValidAssistTarget(@Nullable LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive() || candidate.isRemoved() || !candidate.isAttackable()) {
            return false;
        }
        if (candidate.getEntityWorld() != mob.getEntityWorld()) {
            return false;
        }
        if (!mob.canTarget(candidate)) {
            return false;
        }
        if (candidate.equals(mob) || candidate.equals(owner)) {
            return false;
        }
        if (candidate instanceof MobEntity mobCandidate) {
            PetComponent targetComponent = PetComponent.get(mobCandidate);
            if (targetComponent != null && owner != null && targetComponent.isOwnedBy(owner)) {
                return false;
            }
            // Also guard against vanilla tamed entities with the same owner
            if (mobCandidate instanceof TameableEntity tame) {
                LivingEntity tameOwner = tame.getOwner();
                if (tameOwner != null && tameOwner.equals(owner)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void nudgeTowardOwner(@Nullable PlayerEntity owner) {
        if (owner == null || owner.isRemoved()) {
            return;
        }
        double speed = computeApproachSpeed();
        mob.getNavigation().startMovingTo(owner, speed);
    }

    private void updatePackContext(@Nullable LivingEntity target) {
        packSample = PackSample.EMPTY;
        if (target == null || owner == null || !(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        int primaryLimit = computePrimaryPackSampleSize();
        int awarenessLimit = computeAwarenessSampleSize(primaryLimit);
        List<MobEntity> allies = collectNearestOwnedAllies(serverWorld, target, awarenessLimit);
        if (allies.isEmpty()) {
            return;
        }

        float totalWeight = 0f;
        float confidenceAccum = 0f;
        float cohesionAccum = 0f;
        float engagedAccum = 0f;
        float guardBiasAccum = 0f;

        for (int index = 0; index < allies.size(); index++) {
            MobEntity ally = allies.get(index);
            PetComponent allyComponent = PetComponent.get(ally);
            if (allyComponent == null) {
                continue;
            }

            float weight = index < primaryLimit ? 1.0f : 0.45f;
            totalWeight += weight;

            float healthRatio = getHealthRatio(ally);
            float levelFactor = MathHelper.clamp(allyComponent.getLevel() / 30.0f, 0f, 1f);
            float readiness = computeAllyReadiness(allyComponent, healthRatio, levelFactor);
            confidenceAccum += readiness * weight;

            double cohesionScore = computeCohesionScore(ally, target);
            double anchorScore = computeAnchorScore(ally);
            cohesionAccum += (float) (0.6d * cohesionScore + 0.4d * anchorScore) * weight;

            if (ally.getTarget() == target) {
                engagedAccum += weight;
            }

            float guardBias = computeGuardBiasSignal(allyComponent, healthRatio);
            float flankBias = computeFlankBiasSignal(allyComponent);
            guardBiasAccum += (guardBias - flankBias * 0.5f) * weight;
        }

        if (totalWeight <= 0f) {
            return;
        }

        packSample = new PackSample(
            MathHelper.clamp(confidenceAccum / totalWeight, 0f, 1f),
            MathHelper.clamp(cohesionAccum / totalWeight, 0f, 1f),
            MathHelper.clamp(engagedAccum / totalWeight, 0f, 1f),
            MathHelper.clamp(guardBiasAccum / totalWeight, -1f, 1f)
        );
    }

    private MoodSample sampleMoods() {
        return new MoodSample(
            petComponent.getMoodStrength(PetComponent.Mood.PROTECTIVE),
            petComponent.getMoodStrength(PetComponent.Mood.ANGRY),
            petComponent.getMoodStrength(PetComponent.Mood.RESTLESS),
            petComponent.getMoodStrength(PetComponent.Mood.PASSIONATE),
            petComponent.getMoodStrength(PetComponent.Mood.FOCUSED),
            petComponent.getMoodStrength(PetComponent.Mood.BONDED),
            petComponent.getMoodStrength(PetComponent.Mood.CALM),
            petComponent.getMoodStrength(PetComponent.Mood.SAUDADE),
            petComponent.getMoodStrength(PetComponent.Mood.YUGEN),
            petComponent.getMoodStrength(PetComponent.Mood.AFRAID)
        );
    }

    private float getHealthRatio(LivingEntity ally) {
        float maxHealth = ally.getMaxHealth();
        if (maxHealth <= 0f) {
            return 1f;
        }
        return MathHelper.clamp(ally.getHealth() / maxHealth, 0f, 1f);
    }

    private float computeAllyReadiness(PetComponent component, float healthRatio, float levelFactor) {
        float protective = component.getMoodStrength(PetComponent.Mood.PROTECTIVE);
        float angry = component.getMoodStrength(PetComponent.Mood.ANGRY);
        float restless = component.getMoodStrength(PetComponent.Mood.RESTLESS);
        float focused = component.getMoodStrength(PetComponent.Mood.FOCUSED);
        float passionate = component.getMoodStrength(PetComponent.Mood.PASSIONATE);
        float afraid = component.getMoodStrength(PetComponent.Mood.AFRAID);
        float calm = component.getMoodStrength(PetComponent.Mood.CALM);
        float saudade = component.getMoodStrength(PetComponent.Mood.SAUDADE);

        float drive = 0.32f + 0.38f * protective + 0.34f * angry + 0.24f * restless + 0.18f * focused + 0.16f * passionate;
        float hesitation = 0.55f * afraid + 0.22f * calm + 0.18f * saudade;
        float resilience = 0.25f + 0.45f * healthRatio + 0.30f * levelFactor;
        return MathHelper.clamp(drive * 0.45f + resilience - hesitation, 0f, 1f);
    }

    private double computeCohesionScore(MobEntity ally, LivingEntity target) {
        double targetDistance = Math.sqrt(Math.max(0.0001d, ally.squaredDistanceTo(target)));
        double capped = Math.min(targetDistance, PACK_MAX_SPACING);
        return 1.0d - MathHelper.clamp(
            (capped - PACK_DESIRED_SPACING) / (PACK_MAX_SPACING - PACK_DESIRED_SPACING),
            0.0d,
            1.0d
        );
    }

    private double computeAnchorScore(MobEntity ally) {
        if (owner == null) {
            return 0.0d;
        }
        double ownerDistance = Math.sqrt(Math.max(0.0001d, ally.squaredDistanceTo(owner)));
        double capped = Math.min(ownerDistance, PACK_OWNER_RADIUS);
        return 1.0d - MathHelper.clamp(
            (capped - PACK_DESIRED_SPACING) / (PACK_OWNER_RADIUS - PACK_DESIRED_SPACING),
            0.0d,
            1.0d
        );
    }

    private float computeGuardBiasSignal(PetComponent component, float healthRatio) {
        float protective = component.getMoodStrength(PetComponent.Mood.PROTECTIVE);
        float bonded = component.getMoodStrength(PetComponent.Mood.BONDED);
        float afraid = component.getMoodStrength(PetComponent.Mood.AFRAID);
        float calm = component.getMoodStrength(PetComponent.Mood.CALM);
        float vulnerability = MathHelper.clamp((0.5f - healthRatio) * 1.2f, -0.6f, 0.6f);
        float readiness = 0.45f * protective + 0.25f * bonded;
        float hesitation = 0.55f * afraid + 0.25f * calm;
        return MathHelper.clamp(vulnerability + readiness - hesitation, -1f, 1f);
    }

    private float computeFlankBiasSignal(PetComponent component) {
        float curious = component.getMoodStrength(PetComponent.Mood.CURIOUS);
        float restless = component.getMoodStrength(PetComponent.Mood.RESTLESS);
        float passionate = component.getMoodStrength(PetComponent.Mood.PASSIONATE);
        float focused = component.getMoodStrength(PetComponent.Mood.FOCUSED);
        return MathHelper.clamp(0.35f * curious + 0.30f * restless + 0.18f * passionate + 0.12f * focused, 0f, 1f);
    }

    private int computePrimaryPackSampleSize() {
        int base = 2;
        var roleId = petComponent.getRoleId();
        if (PetRoleType.GUARDIAN_ID.equals(roleId) || PetRoleType.STRIKER_ID.equals(roleId)) {
            base += 1;
        }
        if (PetRoleType.SUPPORT_ID.equals(roleId)) {
            base += 1;
        }
        if (PetRoleType.SKYRIDER_ID.equals(roleId)) {
            base += 1;
        }
        return MathHelper.clamp(base, 1, 4);
    }

    private int computeAwarenessSampleSize(int primaryLimit) {
        int awareness = primaryLimit;
        var roleId = petComponent.getRoleId();
        if (PetRoleType.GUARDIAN_ID.equals(roleId) || PetRoleType.SUPPORT_ID.equals(roleId)) {
            awareness = Math.min(primaryLimit + 2, 5);
        } else if (PetRoleType.SKYRIDER_ID.equals(roleId) || PetRoleType.SCOUT_ID.equals(roleId)) {
            awareness = Math.min(primaryLimit + 1, 5);
        }
        return Math.max(awareness, primaryLimit);
    }

    private List<MobEntity> collectNearestOwnedAllies(ServerWorld serverWorld, LivingEntity target, int limit) {
        if (limit <= 0 || owner == null) {
            return Collections.emptyList();
        }

        Box searchBox = target.getBoundingBox().expand(PACK_SUPPORT_RADIUS);
        List<MobEntity> candidates = serverWorld.getEntitiesByClass(MobEntity.class, searchBox, other -> {
            if (other == null || other == mob || other == target || !other.isAlive() || other.isRemoved()) {
                return false;
            }
            PetComponent component = PetComponent.get(other);
            return component != null && component.isOwnedBy(owner);
        });

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        int cappedLimit = Math.min(limit, candidates.size());
        candidates.sort((a, b) -> Double.compare(a.squaredDistanceTo(target), b.squaredDistanceTo(target)));
        return new ArrayList<>(candidates.subList(0, cappedLimit));
    }

    public static void markAssistHesitation(@Nullable PetComponent component, long now) {
        if (component != null) {
            component.setStateData(STATE_ASSIST_HESITATION_UNTIL, now + ASSIST_HESITATION_TICKS);
        }
    }

    public static boolean isPetHesitating(@Nullable PetComponent component, long now) {
        if (component == null) {
            return false;
        }
        Long until = component.getStateData(STATE_ASSIST_HESITATION_UNTIL, Long.class);
        return until != null && until > now;
    }

    public static void clearAssistHesitation(@Nullable PetComponent component) {
        if (component != null) {
            component.setStateData(STATE_ASSIST_HESITATION_UNTIL, 0L);
        }
    }

    public static void markAssistRegroup(@Nullable PetComponent component, long now) {
        if (component != null) {
            component.setStateData(STATE_ASSIST_REGROUP_UNTIL, now + ASSIST_REGROUP_TICKS);
        }
    }

    public static boolean isPetRegrouping(@Nullable PetComponent component, long now) {
        if (component == null) {
            return false;
        }
        Long until = component.getStateData(STATE_ASSIST_REGROUP_UNTIL, Long.class);
        return until != null && until > now;
    }

    public static void clearAssistRegroup(@Nullable PetComponent component) {
        if (component != null) {
            component.setStateData(STATE_ASSIST_REGROUP_UNTIL, 0L);
        }
    }

    public static void primeNavigationForAssist(MobEntity mob, LivingEntity target) {
        double speed = computeBaseAssistSpeed(mob);
        if (!mob.getNavigation().isFollowingPath()) {
            mob.getNavigation().startMovingTo(target, speed);
        } else {
            mob.getNavigation().setSpeed(speed);
        }
    }

    private boolean isThreatAcceptable(@Nullable LivingEntity target) {
        if (target == null) {
            return false;
        }
        double targetStrength = estimateThreatStrength(target);
        double petStrength = estimatePetStrength();
        double bravery = estimateBraveryFactor();
        double packSupport = 1.0d + MathHelper.clamp(packSample.confidence(), 0f, 1f) * 0.35d;
        double tolerance = petStrength * bravery * packSupport;
        return targetStrength <= tolerance;
    }

    private double estimateThreatStrength(LivingEntity target) {
        double health = MathHelper.clamp(target.getMaxHealth(), 1.0f, 200.0f);
        double attack = readAttributeOr(target, EntityAttributes.ATTACK_DAMAGE, 3.0d);
        double armor = target.getArmor();
        return (health * 0.55d) + (attack * 4.5d) + (armor * 0.5d);
    }

    private double estimatePetStrength() {
        double health = MathHelper.clamp(mob.getMaxHealth(), 1.0f, 200.0f);
        double attack = readAttributeOr(mob, EntityAttributes.ATTACK_DAMAGE, 2.5d);
        double armor = mob.getArmor();
        return (health * 0.6d) + (attack * 4.0d) + (armor * 0.4d);
    }

    private double estimateBraveryFactor() {
        double base = 0.75d;
        if (petComponent != null) {
            base += MathHelper.clamp(petComponent.getLevel() / 40.0d, 0.0d, 0.6d);
        }
        if (moodSample != null) {
            base += moodSample.protective() * 0.5d;
            base += moodSample.passionate() * 0.35d;
            base += moodSample.angry() * 0.25d;
            base -= moodSample.afraid() * 0.6d;
            base -= moodSample.saudade() * 0.2d;
        }
        base = MathHelper.clamp(base, 0.45d, 1.75d);
        return base;
    }

    private double readAttributeOr(LivingEntity entity, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute, double fallback) {
        double value = entity.getAttributeValue(attribute);
        return (Double.isFinite(value) && value > 0.0d) ? value : fallback;
    }

    private static double computeBaseAssistSpeed(MobEntity mob) {
        double movement = mob.getAttributeValue(EntityAttributes.MOVEMENT_SPEED);
        if (Double.isNaN(movement) || movement <= 0d) {
            return 1.0d;
        }
        return MathHelper.clamp(0.8d + movement * 1.6d, 0.6d, 1.8d);
    }

    private record MoodSample(
        float protective,
        float angry,
        float restless,
        float passionate,
        float focused,
        float bonded,
        float calm,
        float saudade,
        float yugen,
        float afraid
    ) {
        static final MoodSample EMPTY = new MoodSample(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);

        float fear() {
            return afraid;
        }

        float assistDrive() {
            return 0.40f * protective + 0.35f * angry + 0.25f * restless + 0.20f * passionate
                + 0.18f * focused + 0.12f * bonded;
        }

        float assistInhibition() {
            return 0.55f * afraid + 0.30f * calm + 0.24f * saudade + 0.18f * yugen;
        }

        double speedDrive() {
            return 0.35d * protective + 0.30d * angry + 0.20d * restless + 0.18d * focused;
        }

        double speedDrag() {
            return 0.25d * calm + 0.18d * saudade + 0.12d * yugen;
        }
    }

    private record PackSample(float confidence, float cohesion, float assistMomentum, float guardBias) {
        static final PackSample EMPTY = new PackSample(0f, 0f, 0f, 0f);
    }
}





