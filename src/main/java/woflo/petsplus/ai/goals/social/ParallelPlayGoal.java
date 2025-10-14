package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
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

        if (!performingPlay) {
            if (playSpot != null) {
                double targetDistanceSq = playSpot.squaredDistanceTo(mob.getX(), mob.getY(), mob.getZ());
                if (targetDistanceSq > 1.4d) {
                    double speed = computeTravelSpeed(ctx, targetDistanceSq);
                    // Mild lane bias to avoid shoulder-to-shoulder approach shoves
                    Vec3d moveTarget = playSpot;
                    if (targetDistanceSq > 1.0d) {
                        int h = mob.getUuid().hashCode();
                        double laneSign = ((h >>> 1) & 1) == 0 ? -1.0 : 1.0;
                        double width = Math.max(0.3d, mob.getWidth());
                        double laneMag = Math.min(0.35d, 0.25d * width);
                        Vec3d toTarget = playSpot.subtract(mob.getEntityPos());
                        double lenSq = toTarget.lengthSquared();
                        if (lenSq > 1.0e-4d) {
                            Vec3d perp = new Vec3d(-toTarget.z, 0.0, toTarget.x).normalize().multiply(laneMag * laneSign);
                            moveTarget = moveTarget.add(perp);
                        }
                    }
                    if ((parallelTicks % 5) == 0 || mob.getNavigation().isIdle()) {
                        mob.getNavigation().startMovingTo(moveTarget.x, moveTarget.y, moveTarget.z, speed);
                    }
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
            Vec3d offset = computePlayOffset(animationTicks);
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
        // Mild slowdown near owner to reduce clustering nudges
        if (ctx != null && ctx.ownerNearby() && ctx.distanceToOwner() < 3.0f) {
            speed *= 0.88d;
        }
        return MathHelper.clamp(speed, 0.5d, 1.1d);
    }

    private Vec3d computePlayOffset(int ticks) {
        double phase = (ticks % 60) / 60.0d * MathHelper.TAU;
        double radius = 1.1d + Math.sin(ticks * 0.12d) * 0.25d;
        double x = Math.cos(phase) * radius;
        double z = Math.sin(phase) * radius;
        double y = Math.sin(ticks * 0.18d) * 0.05d;
        return new Vec3d(x, y, z);
    }

    private Vec3d selectPlaySpot(PlayerEntity owner) {
        Vec3d ownerPos = owner.getEntityPos();
        // Deterministic, size-aware slot around owner so multiple pets spread naturally.
        Vec3d preferred = ownerPos.add(computeOwnerSlot(owner));
        Vec3d candidate = (mob instanceof net.minecraft.entity.mob.PathAwareEntity p) ? NoPenaltyTargeting.find(p, 6, 2) : null;
        if (candidate != null) {
            return candidate;
        }
        return preferred;
    }

    private Vec3d computeOwnerSlot(PlayerEntity owner) {
        int h = mob.getUuid().hashCode();
        double u = ((long)(h & 0x7fffffff)) / (double)0x7fffffff; // [0,1)
        double angle = u * MathHelper.TAU;
        double width = Math.max(0.3d, mob.getWidth());
        // A bit farther than leaning, gives room for play without crowding.
        double radius = 2.2d + 0.25d * width;

        final double GOLDEN_ANGLE = MathHelper.TAU * 0.38196601125d;
        final double OCC_MARGIN = 0.20d;
        Vec3d ownerPos = owner.getEntityPos();
        Vec3d best = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            double ox = Math.cos(angle) * radius;
            double oz = Math.sin(angle) * radius;
            Vec3d slotWorld = ownerPos.add(ox, 0.0, oz);
            if (!isSlotOccupied(owner, slotWorld, width, OCC_MARGIN)) {
                best = new Vec3d(ox, 0.0, oz);
                break;
            }
            angle += GOLDEN_ANGLE;
        }
        if (best == null) {
            best = new Vec3d(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
        }
        return best;
    }

    private boolean isSlotOccupied(PlayerEntity owner, Vec3d slotWorld, double thisWidth, double occMargin) {
        double occ = 0.5d * thisWidth + occMargin;
        Box box = new Box(
            slotWorld.x - occ, slotWorld.y - 0.75d, slotWorld.z - occ,
            slotWorld.x + occ, slotWorld.y + 0.75d, slotWorld.z + occ
        );
        return !mob.getEntityWorld().getOtherEntities(mob, box, e -> {
            if (!(e instanceof MobEntity other)) return false;
            PetComponent pc = PetComponent.get(other);
            return pc != null && pc.isOwnedBy(owner);
        }).isEmpty();
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



