package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.PetMobInteractionProfile;
import woflo.petsplus.ai.context.NearbyMobAgeProfile;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.EmotionFeedback;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Social caretaking behaviour: the pet seeks out nearby baby mobs and gently
 * nurses them, accelerating their growth when it is safe to do so.
 */
public class NurseBabyGoal extends AdaptiveGoal {
    private static final double MAX_TARGET_DISTANCE = 9.0d;
    private static final double MAX_TARGET_DISTANCE_SQ = MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
    private static final double CLOSE_DISTANCE_SQ = 1.65d * 1.65d;
    private static final double BRAKE_DISTANCE_SQ = 3.1d * 3.1d;
    private static final int RETARGET_INTERVAL = 20;
    private static final int GROWTH_PULSE_INTERVAL = 40;
    private static final int MAX_STATIONARY_TICKS = 80;

    private static final float MIN_NURTURE_AFFINITY = 0.32f;
    private static final Map<PetComponent.Emotion, Float> NURTURING_NATURE_WEIGHTS = Map.ofEntries(
        Map.entry(PetComponent.Emotion.UBUNTU, 0.45f),
        Map.entry(PetComponent.Emotion.PROTECTIVE, 0.32f),
        Map.entry(PetComponent.Emotion.CONTENT, 0.26f),
        Map.entry(PetComponent.Emotion.CHEERFUL, 0.18f),
        Map.entry(PetComponent.Emotion.RELIEF, 0.18f),
        Map.entry(PetComponent.Emotion.SOBREMESA, 0.16f),
        Map.entry(PetComponent.Emotion.HANYAUKU, 0.14f),
        Map.entry(PetComponent.Emotion.LAGOM, 0.14f),
        Map.entry(PetComponent.Emotion.WABI_SABI, 0.12f)
    );
    private static final Map<PetComponent.Emotion, Float> DISSONANT_NATURE_WEIGHTS = Map.ofEntries(
        Map.entry(PetComponent.Emotion.FRUSTRATION, 0.40f),
        Map.entry(PetComponent.Emotion.ANGST, 0.36f),
        Map.entry(PetComponent.Emotion.FOREBODING, 0.34f),
        Map.entry(PetComponent.Emotion.DISGUST, 0.28f),
        Map.entry(PetComponent.Emotion.REGRET, 0.22f),
        Map.entry(PetComponent.Emotion.ENNUI, 0.20f)
    );

    private PassiveEntity targetBaby;
    private UUID targetBabyId;
    private int activeTicks;
    private long lastGrowthPulseTick;
    private int stationaryTicks;
    private float nurtureAffinity;

    public NurseBabyGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.NURSE_BABY), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        PetMobInteractionProfile interactionProfile = ctx.mobInteractionProfile();
        if (interactionProfile == null || interactionProfile.maintainBuffer() || !interactionProfile.shouldApproachBabies()) {
            return false;
        }

        if (!updateNurtureAffinity(ctx)) {
            return false;
        }

        NearbyMobAgeProfile ageProfile = ctx.nearbyMobAgeProfile();
        if (ageProfile == null || (!ageProfile.hasBabies()) ||
            (ageProfile.babyFriendlyCount() + ageProfile.babyNeutralCount()) <= 0) {
            return false;
        }

        PassiveEntity candidate = findNursableBaby(ctx);
        if (candidate == null) {
            return false;
        }

        PetComponent component = PetComponent.get(mob);
        if (component != null) {
            if (component.hasMoodAbove(PetComponent.Mood.ANGRY, 0.45f)
                || component.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.65f)) {
                return false;
            }
        }

        this.targetBaby = candidate;
        this.targetBabyId = candidate.getUuid();
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (targetBaby == null || targetBaby.isRemoved() || !targetBaby.isAlive()) {
            return false;
        }
        if (!targetBaby.isBaby()) {
            return false;
        }
        double distanceSq = mob.squaredDistanceTo(targetBaby);
        if (!Double.isFinite(distanceSq) || distanceSq > MAX_TARGET_DISTANCE_SQ) {
            return false;
        }

        PetContext ctx = getContext();
        PetMobInteractionProfile interactionProfile = ctx.mobInteractionProfile();
        if (interactionProfile == null || interactionProfile.maintainBuffer()) {
            return false;
        }
        if (!interactionProfile.shouldApproachBabies()) {
            return false;
        }

        if (!updateNurtureAffinity(ctx) && nurtureAffinity < (MIN_NURTURE_AFFINITY * 0.85f)) {
            return false;
        }

        return true;
    }

    @Override
    protected void onStartGoal() {
        activeTicks = 0;
        stationaryTicks = 0;
        lastGrowthPulseTick = Long.MIN_VALUE;
        updateNurtureAffinity(getContext());
        reacquireTarget(getContext());
    }

    @Override
    protected void onStopGoal() {
        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        targetBaby = null;
        targetBabyId = null;
    }

    @Override
    protected void onTickGoal() {
        activeTicks++;
        PetContext ctx = getContext();
        if (!updateNurtureAffinity(ctx)) {
            requestStop();
            return;
        }
        PassiveEntity currentTarget = reacquireTarget(ctx);
        if (currentTarget == null) {
            requestStop();
            return;
        }

        double distanceSq = mob.squaredDistanceTo(currentTarget);
        if (!Double.isFinite(distanceSq)) {
            requestStop();
            return;
        }

        if (distanceSq > CLOSE_DISTANCE_SQ) {
            stationaryTicks = 0;
            double speed = distanceSq > BRAKE_DISTANCE_SQ ? 1.05d : 0.75d;
            if ((activeTicks % 4 == 0) || mob.getNavigation().isIdle()) {
                mob.getNavigation().startMovingTo(currentTarget, speed);
            }
        } else {
            stationaryTicks++;
            mob.getNavigation().stop();
        }

        mob.getLookControl().lookAt(currentTarget, 30.0f, 30.0f);
        gentlyNudgeTowards(currentTarget, distanceSq);

        if (mob.getEntityWorld() instanceof ServerWorld serverWorld) {
            long now = serverWorld.getTime();
            if (lastGrowthPulseTick == Long.MIN_VALUE) {
                lastGrowthPulseTick = now;
            }
            if ((now - lastGrowthPulseTick) >= GROWTH_PULSE_INTERVAL) {
                lastGrowthPulseTick = now;
                applyGrowthPulse(serverWorld, currentTarget);
            }
        }

        if (stationaryTicks > MAX_STATIONARY_TICKS) {
            requestStop();
        }
    }

    @Override
    protected float calculateEngagement() {
        PassiveEntity baby = this.targetBaby;
        if (baby == null) {
            return MathHelper.clamp(0.25f + nurtureAffinity * 0.45f, 0.0f, 1.0f);
        }
        double distance = Math.sqrt(Math.max(0.0d, mob.squaredDistanceTo(baby)));
        float proximity = (float) MathHelper.clamp(1.0d - (distance / MAX_TARGET_DISTANCE), 0.0d, 1.0d);
        float base = 0.30f + nurtureAffinity * 0.45f;
        float proximityScale = 0.20f + nurtureAffinity * 0.25f;
        return MathHelper.clamp(base + proximity * proximityScale, 0.0f, 1.0f);
    }

    @Override
    protected EmotionFeedback defineEmotionFeedback() {
        return EmotionFeedback.triple(
            PetComponent.Emotion.UBUNTU, 0.10f,
            PetComponent.Emotion.PROTECTIVE, 0.07f,
            PetComponent.Emotion.CONTENT, 0.05f
        );
    }

    private PassiveEntity reacquireTarget(PetContext ctx) {
        if (targetBaby != null && targetBaby.isAlive() && !targetBaby.isRemoved() && targetBaby.isBaby()) {
            return targetBaby;
        }
        if (ctx == null) {
            targetBaby = null;
            return null;
        }
        targetBaby = resolveBabyById(targetBabyId);
        if (targetBaby != null) {
            return targetBaby;
        }
        if (activeTicks % RETARGET_INTERVAL == 0) {
            PassiveEntity replacement = findNursableBaby(ctx);
            if (replacement != null) {
                targetBaby = replacement;
                targetBabyId = replacement.getUuid();
                return targetBaby;
            }
        }
        targetBaby = null;
        targetBabyId = null;
        return null;
    }

    private PassiveEntity findNursableBaby(PetContext ctx) {
        if (ctx == null) {
            return null;
        }
        NearbyMobAgeProfile ageProfile = ctx.nearbyMobAgeProfile();
        if (ageProfile != null) {
            PassiveEntity byProfile = resolveBabyById(ageProfile.nearestBabyId());
            if (isNursableBaby(byProfile)) {
                return byProfile;
            }
        }

        List<Entity> entities = ctx.nearbyEntities();
        if (entities == null || entities.isEmpty()) {
            return null;
        }

        PassiveEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Entity entity : entities) {
            if (!(entity instanceof PassiveEntity passive)) {
                continue;
            }
            if (!isNursableBaby(passive)) {
                continue;
            }

            double distanceSq = mob.squaredDistanceTo(passive);
            if (!Double.isFinite(distanceSq) || distanceSq > MAX_TARGET_DISTANCE_SQ) {
                continue;
            }

            double distanceScore = 1.0d - (MathHelper.clamp(distanceSq, 0.0d, MAX_TARGET_DISTANCE_SQ)
                / MAX_TARGET_DISTANCE_SQ);
            double affinityScore = 0.25d + nurtureAffinity * 0.5d;
            double candidateScore = distanceScore * 0.7d + affinityScore * 0.3d;

            if (candidateScore > bestScore) {
                bestScore = candidateScore;
                best = passive;
            }
        }

        return best;
    }

    private void gentlyNudgeTowards(PassiveEntity baby, double distanceSq) {
        if (baby == null || distanceSq > BRAKE_DISTANCE_SQ) {
            return;
        }
        Vec3d fromBaby = baby.getBoundingBox().getCenter().subtract(mob.getBoundingBox().getCenter());
        double lengthSq = fromBaby.lengthSquared();
        if (!Double.isFinite(lengthSq) || lengthSq < 1.0e-4d) {
            return;
        }
        double clamped = MathHelper.clamp(distanceSq, 0.0d, BRAKE_DISTANCE_SQ);
        double strength = 0.015d + (1.0d - (clamped / BRAKE_DISTANCE_SQ)) * 0.02d;
        double invLength = 1.0d / Math.sqrt(lengthSq);
        Vec3d push = fromBaby.multiply(invLength * strength);
        mob.addVelocity(push.x, 0.0d, push.z);
        mob.velocityModified = true;
    }

    private void applyGrowthPulse(ServerWorld world, PassiveEntity baby) {
        if (baby == null || !baby.isAlive()) {
            requestStop();
            return;
        }
        if (!baby.isBaby()) {
            requestStop();
            return;
        }

        int breedingAge = getBreedingAge(baby);
        if (breedingAge >= 0) {
            requestStop();
            return;
        }

        int incrementCeiling = MathHelper.floor(MathHelper.clamp(80.0f + nurtureAffinity * 160.0f, 40.0f, 220.0f));
        int increment = Math.min(incrementCeiling, -breedingAge);
        int targetAge = breedingAge + increment;
        targetAge = Math.min(targetAge, 0);
        setBreedingAge(baby, targetAge);
        if (!baby.isBaby()) {
            setBreedingAge(baby, 0);
            growBaby(baby);
        }

        world.spawnParticles(
            ParticleTypes.HEART,
            baby.getX(),
            baby.getBodyY(0.6d),
            baby.getZ(),
            2,
            0.2d,
            0.1d,
            0.2d,
            0.0d
        );
        world.playSound(
            null,
            baby.getX(),
            baby.getY(),
            baby.getZ(),
            SoundEvents.ENTITY_CAT_PURR,
            SoundCategory.NEUTRAL,
            0.35f,
            0.9f + mob.getRandom().nextFloat() * 0.1f
        );

        PetComponent component = PetComponent.get(mob);
        if (component != null) {
            component.pushEmotion(PetComponent.Emotion.UBUNTU, 0.02f);
            component.pushEmotion(PetComponent.Emotion.PROTECTIVE, 0.015f);
        }
    }

    private boolean updateNurtureAffinity(PetContext ctx) {
        this.nurtureAffinity = computeNurtureAffinity(ctx);
        return this.nurtureAffinity >= MIN_NURTURE_AFFINITY;
    }

    private float computeNurtureAffinity(PetContext ctx) {
        if (ctx == null) {
            return 0.0f;
        }

        float score = ctx.hasPetsPlusComponent() ? 0.18f : 0.08f;

        PetRoleType role = ctx.role();
        if (role != null) {
            score += switch (role.archetype()) {
                case SUPPORT -> 0.38f;
                case TANK -> 0.22f;
                case MOBILITY -> 0.14f;
                case UTILITY -> 0.10f;
                case DPS -> -0.28f;
            };
            String rolePath = role.id() != null ? role.id().getPath() : "";
            if (!rolePath.isEmpty()) {
                if (rolePath.contains("support") || rolePath.contains("medic") || rolePath.contains("caretaker")) {
                    score += 0.08f;
                }
                if (rolePath.contains("striker") || rolePath.contains("berserk") || rolePath.contains("raider")) {
                    score -= 0.12f;
                }
            }
        }

        score += moodDisposition(ctx.currentMood());

        Map<PetComponent.Mood, Float> moodBlend = ctx.moodBlend();
        if (moodBlend != null && !moodBlend.isEmpty()) {
            score += clamp01(moodBlend.getOrDefault(PetComponent.Mood.BONDED, 0.0f)) * 0.18f;
            score += clamp01(moodBlend.getOrDefault(PetComponent.Mood.CALM, 0.0f)) * 0.10f;
            score += clamp01(moodBlend.getOrDefault(PetComponent.Mood.PROTECTIVE, 0.0f)) * 0.12f;
            score += clamp01(moodBlend.getOrDefault(PetComponent.Mood.HAPPY, 0.0f)) * 0.08f;
            score -= clamp01(moodBlend.getOrDefault(PetComponent.Mood.RESTLESS, 0.0f)) * 0.22f;
            score -= clamp01(moodBlend.getOrDefault(PetComponent.Mood.ANGRY, 0.0f)) * 0.35f;
        }

        score += MathHelper.clamp(ctx.bondStrength(), 0.0f, 1.0f) * 0.12f;

        Map<PetComponent.Emotion, Float> activeEmotions = ctx.activeEmotions();
        if (activeEmotions != null && !activeEmotions.isEmpty()) {
            score += sampleEmotion(activeEmotions, PetComponent.Emotion.UBUNTU, 0.32f);
            score += sampleEmotion(activeEmotions, PetComponent.Emotion.PROTECTIVE, 0.22f);
            score += sampleEmotion(activeEmotions, PetComponent.Emotion.CONTENT, 0.18f);
            score += sampleEmotion(activeEmotions, PetComponent.Emotion.CHEERFUL, 0.12f);
            score += sampleEmotion(activeEmotions, PetComponent.Emotion.RELIEF, 0.10f);
            score += sampleEmotion(activeEmotions, PetComponent.Emotion.SOBREMESA, 0.08f);
            score -= sampleEmotion(activeEmotions, PetComponent.Emotion.FRUSTRATION, 0.30f);
            score -= sampleEmotion(activeEmotions, PetComponent.Emotion.ANGST, 0.26f);
            score -= sampleEmotion(activeEmotions, PetComponent.Emotion.FOREBODING, 0.24f);
            score -= sampleEmotion(activeEmotions, PetComponent.Emotion.DISGUST, 0.20f);
            score -= sampleEmotion(activeEmotions, PetComponent.Emotion.ENNUI, 0.12f);
        }

        PetComponent.NatureEmotionProfile natureProfile = ctx.natureProfile();
        if (natureProfile != null && !natureProfile.isEmpty()) {
            score += applyNatureSlot(natureProfile.majorEmotion(), natureProfile.majorStrength(), 1.0f);
            score += applyNatureSlot(natureProfile.minorEmotion(), natureProfile.minorStrength(), 0.65f);
            score += applyNatureSlot(natureProfile.quirkEmotion(), natureProfile.quirkStrength(), 0.35f);
        }

        float behavioralMomentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);
        if (behavioralMomentum < 0.35f) {
            score += 0.05f;
        } else if (behavioralMomentum > 0.75f) {
            score -= 0.06f;
        }

        if (ctx.socialCharge() < 0.25f) {
            score -= 0.05f;
        }

        return MathHelper.clamp(score, 0.0f, 1.0f);
    }

    private float applyNatureSlot(PetComponent.Emotion emotion, float strength, float slotWeight) {
        if (emotion == null || strength <= 0.0f || slotWeight <= 0.0f) {
            return 0.0f;
        }
        float intensity = MathHelper.clamp(strength, 0.0f, 1.0f) * slotWeight;
        Float nurturing = NURTURING_NATURE_WEIGHTS.get(emotion);
        if (nurturing != null) {
            return nurturing * intensity;
        }
        Float dissonant = DISSONANT_NATURE_WEIGHTS.get(emotion);
        if (dissonant != null) {
            return -dissonant * intensity;
        }
        return 0.0f;
    }

    private float sampleEmotion(Map<PetComponent.Emotion, Float> emotions, PetComponent.Emotion emotion, float weight) {
        if (emotions == null || emotion == null || weight == 0.0f) {
            return 0.0f;
        }
        float intensity = clamp01(emotions.getOrDefault(emotion, 0.0f));
        if (intensity <= 0.0f) {
            return 0.0f;
        }
        return weight * intensity;
    }

    private float moodDisposition(PetComponent.Mood mood) {
        if (mood == null) {
            return 0.0f;
        }
        return switch (mood) {
            case BONDED -> 0.18f;
            case HAPPY -> 0.12f;
            case CALM -> 0.12f;
            case PLAYFUL -> 0.06f;
            case PROTECTIVE -> 0.08f;
            case CURIOUS -> 0.04f;
            case PASSIONATE -> 0.03f;
            case RESTLESS -> -0.22f;
            case AFRAID -> -0.14f;
            case ANGRY -> -0.38f;
            case SAUDADE, YUGEN -> -0.06f;
            case SISU, FOCUSED -> -0.04f;
            default -> 0.0f;
        };
    }

    private PassiveEntity resolveBabyById(UUID id) {
        if (id == null) {
            return null;
        }
        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }
        Entity resolved = serverWorld.getEntity(id);
        if (resolved instanceof PassiveEntity passive && isNursableBaby(passive)) {
            return passive;
        }
        return null;
    }

    private boolean isNursableBaby(Entity entity) {
        if (!(entity instanceof PassiveEntity passive)) {
            return false;
        }
        if (!passive.isAlive() || passive.isRemoved() || !passive.isBaby()) {
            return false;
        }
        if (passive instanceof Monster) {
            return false;
        }
        if (passive.getUuid().equals(mob.getUuid())) {
            return false;
        }
        return true;
    }

    private int getBreedingAge(PassiveEntity entity) {
        return entity.getBreedingAge();
    }

    private void setBreedingAge(PassiveEntity entity, int age) {
        entity.setBreedingAge(age);
    }

    private void growBaby(PassiveEntity entity) {
        entity.growUp(0, false);
    }

    private float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return MathHelper.clamp(value, 0.0f, 1.0f);
    }
}
