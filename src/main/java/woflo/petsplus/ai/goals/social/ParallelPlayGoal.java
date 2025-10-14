package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * Parallel play - stays near owner doing independent activities.
 * Creates companionable, comfortable togetherness.
 */
public class ParallelPlayGoal extends AdaptiveGoal {
    private static final int MAX_PARALLEL_TICKS = 400; // 20 seconds
    private static final double COMFORTABLE_DISTANCE = 5.0;
    private static final String COOLDOWN_KEY = "parallel_play";
    private static final double LOOK_THRESHOLD = 0.2d;
    private static final int MAX_OWNER_FOCUS_GRACE = 40;
    private static final int PLAY_TRACKING_INTERVAL = 100; // Track every 5 seconds

    private int parallelTicks = 0;
    private Vec3d playSpot;
    private boolean performingPlay = false;
    private int animationTicks = 0;
    private int lastPlayTick = 0;
    private int ownerFocusGrace = MAX_OWNER_FOCUS_GRACE;
    
    public ParallelPlayGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.PARALLEL_PLAY), EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        
        if (owner == null) {
            return false;
        }
        if (!ctx.ownerNearby() || ctx.distanceToOwner() >= 12.0f) {
            return false;
        }

        PetComponent component = PetComponent.get(mob);
        if (component != null) {
            if (component.isOnCooldown(COOLDOWN_KEY)) {
                return false;
            }
            if (component.hasMoodAbove(PetComponent.Mood.ANGRY, 0.4f)
                || component.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.7f)) {
                return false;
            }
            if (!component.hasMoodAbove(PetComponent.Mood.PLAYFUL, 0.2f) && mob.getRandom().nextFloat() > 0.5f) {
                return false;
            }
        }

        return isOwnerEngaged(owner);
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        if (owner == null) {
            return false;
        }
        if (parallelTicks >= MAX_PARALLEL_TICKS) {
            return false;
        }
        if (!ctx.ownerNearby() || ctx.distanceToOwner() >= 15.0f) {
            return false;
        }
        if (playSpot == null) {
            return false;
        }

        PetComponent component = PetComponent.get(mob);
        if (component != null && component.hasMoodAbove(PetComponent.Mood.ANGRY, 0.5f)) {
            return false;
        }

        if (!isOwnerEngaged(owner) && ownerFocusGrace <= 0) {
            return false;
        }

        return true;
    }
    
    @Override
    protected void onStartGoal() {
        parallelTicks = 0;
        animationTicks = 0;
        performingPlay = false;
        lastPlayTick = 0;
        ownerFocusGrace = MAX_OWNER_FOCUS_GRACE;
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        playSpot = owner != null ? selectPlaySpot(owner) : null;
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        playSpot = null;
        performingPlay = false;
        PetComponent component = PetComponent.get(mob);
        if (component != null) {
            int cooldown = 200 + mob.getRandom().nextInt(80);
            component.setCooldown(COOLDOWN_KEY, cooldown);
        }
    }
    
    @Override
    protected void onTickGoal() {
        parallelTicks++;

        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        if (owner == null) {
            return;
        }

        if (isOwnerEngaged(owner)) {
            ownerFocusGrace = MAX_OWNER_FOCUS_GRACE;
        } else if (ownerFocusGrace > 0) {
            ownerFocusGrace--;
        }

        if (playSpot == null) {
            playSpot = selectPlaySpot(owner);
        }

        double radiusScale = computeRadiusScale(ctx);

        if (!performingPlay) {
            if (playSpot != null) {
                double targetDistanceSq = playSpot.squaredDistanceTo(mob.getX(), mob.getY(), mob.getZ());
                if (targetDistanceSq > 1.4d) {
                    double speed = computeTravelSpeed(ctx, targetDistanceSq);
                    if (radiusScale < 0.999d) {
                        speed *= MathHelper.clamp(0.55d + radiusScale * 0.45d, 0.55d, 0.95d);
                    }
                    mob.getNavigation().startMovingTo(playSpot.x, playSpot.y, playSpot.z, speed);
                    mob.getLookControl().lookAt(owner, 20.0f, 20.0f);
                } else {
                    performingPlay = true;
                    animationTicks = 0;
                    mob.getNavigation().stop();
                    lastPlayTick = parallelTicks;
                    woflo.petsplus.events.RelationshipEventHandler.onPlayInteraction(mob, owner);
                }
            }
        } else {
            mob.getNavigation().stop();
            animationTicks++;
            Vec3d animationCenter = playSpot != null ? playSpot : owner.getEntityPos();
            Vec3d offset = computePlayOffset(animationTicks, radiusScale);
            Vec3d desired = animationCenter.add(offset);
            Vec3d delta = desired.subtract(mob.getEntityPos());
            mob.addVelocity(delta.x * 0.08, delta.y * 0.04, delta.z * 0.08);
            mob.velocityModified = true;
            mob.getLookControl().lookAt(owner, 30.0f, 30.0f);

            if (animationTicks % 40 == 0) {
                woflo.petsplus.events.RelationshipEventHandler.onPlayInteraction(mob, owner);
                lastPlayTick = parallelTicks;
            }
        }

        if (playSpot != null && owner.squaredDistanceTo(playSpot.x, playSpot.y, playSpot.z) > Math.pow(COMFORTABLE_DISTANCE + 2.5d, 2)) {
            playSpot = selectPlaySpot(owner);
            performingPlay = false;
        }

        if (parallelTicks - lastPlayTick >= PLAY_TRACKING_INTERVAL && performingPlay) {
            woflo.petsplus.events.RelationshipEventHandler.onPlayInteraction(mob, owner);
            lastPlayTick = parallelTicks;
        }
    }

    private double computeTravelSpeed(PetContext ctx, double distanceSq) {
        double distance = Math.sqrt(Math.max(distanceSq, 0.0d));
        double distanceFactor = MathHelper.clamp((distance - COMFORTABLE_DISTANCE) / 4.0d, 0.0d, 1.0d);
        double stamina = ctx != null ? MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f) : 0.6d;
        double momentum = ctx != null ? MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f) : 0.6d;
        double playfulBoost = (ctx != null && ctx.hasPetsPlusComponent()
            && ctx.hasMoodInBlend(PetComponent.Mood.PLAYFUL, 0.3f)) ? 0.12d : 0.0d;
        double speed = 0.6d + distanceFactor * 0.25d + stamina * 0.18d + momentum * 0.12d + playfulBoost;
        return MathHelper.clamp(speed, 0.5d, 1.1d);
    }

    private Vec3d computePlayOffset(int ticks, double radiusScale) {
        double phase = (ticks % 60) / 60.0d * MathHelper.TAU;
        double radius = (1.1d + Math.sin(ticks * 0.12d) * 0.25d) * MathHelper.clamp(radiusScale, 0.45d, 1.0d);
        double x = Math.cos(phase) * radius;
        double z = Math.sin(phase) * radius;
        double y = Math.sin(ticks * 0.18d) * 0.05d;
        return new Vec3d(x, y, z);
    }

    private double computeRadiusScale(PetContext ctx) {
        if (ctx == null || ctx.nearbyMobAgeProfile() == null) {
            return 1.0d;
        }
        double nearest = ctx.nearbyMobAgeProfile().nearestFriendlyBabyDistance();
        if (!Double.isFinite(nearest)) {
            nearest = ctx.nearbyMobAgeProfile().nearestBabyDistance();
        }
        if (!Double.isFinite(nearest) || nearest > 2.5d) {
            return 1.0d;
        }
        double scale = MathHelper.clamp(nearest / 2.5d, 0.45d, 1.0d);
        if (ctx.mobInteractionProfile() != null && ctx.mobInteractionProfile().softenAnimations()) {
            scale = Math.min(scale, 0.85d);
        }
        return scale;
    }

    private Vec3d selectPlaySpot(PlayerEntity owner) {
        Vec3d ownerPos = owner.getEntityPos();
        Vec3d forward = owner.getRotationVec(1.0f).normalize();
        Vec3d lateral = new Vec3d(-forward.z, 0.0, forward.x);
        if (lateral.lengthSquared() < 1.0e-4d) {
            lateral = new Vec3d(1.0, 0.0, 0.0);
        } else {
            lateral = lateral.normalize();
        }
        double side = mob.getRandom().nextBoolean() ? 1.0d : -1.0d;
        Vec3d preferred = ownerPos.add(forward.multiply(1.6d)).add(lateral.multiply(side * 2.2d));
        Vec3d direction = preferred.subtract(mob.getEntityPos());
        Vec3d candidate = (mob instanceof net.minecraft.entity.mob.PathAwareEntity p) ? NoPenaltyTargeting.find(p, 6, 2) : null;
        if (candidate != null) {
            return candidate;
        }
        return preferred;
    }

    private boolean isOwnerEngaged(PlayerEntity owner) {
        Vec3d ownerEye = owner.getCameraPosVec(1.0f);
        Vec3d ownerLook = owner.getRotationVec(1.0f).normalize();
        Vec3d toPet = mob.getEntityPos().add(0.0, mob.getStandingEyeHeight(), 0.0).subtract(ownerEye).normalize();
        double dot = ownerLook.dotProduct(toPet);
        if (!Double.isFinite(dot)) {
            return false;
        }
        return dot >= LOOK_THRESHOLD || mob.squaredDistanceTo(owner) < 6.0d;
    }
    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.22f)
            .add(woflo.petsplus.state.PetComponent.Emotion.KEFI, 0.20f)
            .add(woflo.petsplus.state.PetComponent.Emotion.PLAYFULNESS, 0.18f)
            .add(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.15f)
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.025f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);
        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);

        float socialBlend = MathHelper.clamp((socialCharge - 0.35f) / 0.3f, -1.0f, 1.0f);
        float engagement = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.5f, 0.88f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.4f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.82f, 1.08f);
        engagement *= staminaScale;

        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.CALM, 0.5f)) {
            engagement += 0.12f;
        }

        engagement += ctx.bondStrength() * 0.15f;

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}



