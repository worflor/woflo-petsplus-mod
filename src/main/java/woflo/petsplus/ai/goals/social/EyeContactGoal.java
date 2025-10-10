package woflo.petsplus.ai.goals.social;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalType;

import java.util.EnumSet;

/**
 * Subtle eye contact behavior - pet looks at attentive players (favoring their owner).
 * Probabilistic and personality-driven - pets retain autonomy and don't always respond.
 * 
 * LOW PRIORITY - ambient behavior that happens when pet is idle/not busy.
 * HIGH PRIORITY can still override this (maintains free will).
 */
public class EyeContactGoal extends AdaptiveGoal {
    private int contactTicks = 0;
    private int maxContactDuration = 60; // recalculated when goal starts
    private PlayerEntity focusPlayer = null;
    private PlayerEntity trackingPlayer = null;
    private int playerLookingTickCount = 0;
    private long lastCheckTime = 0;
    private float lastAttentionStrength = 0f;
    private float trackingAttention = 0f;
    private float activeAttention = 0f;
    private int gazeCooldownTicks = 0;

    private enum GazePhase {
        ORIENTING,
        SOFTENING,
        CONNECTED
    }

    private GazePhase gazePhase = GazePhase.ORIENTING;
    private int phaseTicks = 0;
    private int orientDuration = 12;
    private int softenDuration = 20;
    private int connectionDuration = 40;
    private int microExpressionCooldown = 0;

    private static final int MIN_CONNECTION_DURATION = 20;  // 1 second minimum
    private static final int MAX_CONNECTION_DURATION = 120; // 6 seconds maximum
    private static final int RESPONSE_DELAY_MIN = 5;  // 0.25s min delay
    private static final int RESPONSE_DELAY_MAX = 40; // 2s max delay
    private static final int RETRY_COOLDOWN_MIN = 20;
    private static final int RETRY_COOLDOWN_MAX = 45;
    private static final float MIN_ALIGNMENT_DOT = 0.78f;
    private static final float DIRECT_ALIGNMENT_DOT = 0.97f;
    private static final float TRACKING_SMOOTHING = 0.35f;
    private static final float ACTIVE_SMOOTHING = 0.25f;

    private record GazeObservation(PlayerEntity player, float alignment, double distanceSq) {}
    
    public EyeContactGoal(MobEntity mob) {
        super(mob, GoalType.EYE_CONTACT, EnumSet.of(Control.LOOK));
    }
    
    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        if (gazeCooldownTicks > 0) {
            gazeCooldownTicks--;
        }

        GazeObservation observation = findLookingPlayer(ctx);
        if (observation == null) {
            resetTracking();
            return false;
        }

        PlayerEntity lookingPlayer = observation.player();
        if (lookingPlayer != trackingPlayer) {
            trackingPlayer = lookingPlayer;
            playerLookingTickCount = 0;
            trackingAttention = 0f;
        }

        playerLookingTickCount++;

        float attentionStrength = updateTrackingAttention(observation, playerLookingTickCount);

        PlayerEntity owner = ctx.owner();
        boolean isOwner = owner != null && owner.getUuid().equals(lookingPlayer.getUuid());

        // Require minimum attention duration before we consider reciprocating.
        float bondFactor = isOwner ? ctx.bondStrength() : 0.0f;
        int requiredDelay = MathHelper.floor(MathHelper.lerp(bondFactor, RESPONSE_DELAY_MAX, RESPONSE_DELAY_MIN));
        requiredDelay = Math.max(RESPONSE_DELAY_MIN, requiredDelay);
        if (playerLookingTickCount < requiredDelay || attentionStrength < 0.18f) {
            return false;
        }

        // Roll for response (checked every few ticks to avoid spam)
        long now = mob.getEntityWorld().getTime();
        if (now - lastCheckTime < 10) {
            return false; // Check every 0.5s
        }
        lastCheckTime = now;

        if (gazeCooldownTicks > 0) {
            return false;
        }

        float responseChance = calculateResponseChance(ctx, lookingPlayer, attentionStrength, isOwner, observation);
        boolean start = mob.getRandom().nextFloat() < responseChance;
        if (start) {
            focusPlayer = lookingPlayer;
            lastAttentionStrength = attentionStrength;
            activeAttention = attentionStrength;
        }
        return start;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (focusPlayer == null || !focusPlayer.isAlive() || focusPlayer.isRemoved() || focusPlayer.isSpectator()) {
            return false;
        }
        if (focusPlayer.squaredDistanceTo(mob) > 100.0) {
            return false; // Beyond 10 blocks
        }
        if (contactTicks >= maxContactDuration) {
            return false;
        }

        GazeObservation observation = observePlayerGaze(focusPlayer);
        boolean playerLooking = observation != null;
        if (playerLooking) {
            activeAttention = smoothAttention(activeAttention, computeAttentionStrength(observation, contactTicks), ACTIVE_SMOOTHING);
        } else {
            activeAttention = smoothAttention(activeAttention, 0.0f, 0.12f);
        }

        if (playerLooking) {
            return true;
        }

        // Allow a short linger when the gaze breaks naturally.
        if (gazePhase == GazePhase.CONNECTED) {
            return phaseTicks < 12 || activeAttention > 0.22f;
        }
        return contactTicks < 8 || activeAttention > 0.18f;
    }

    @Override
    protected void onStartGoal() {
        contactTicks = 0;
        playerLookingTickCount = 0;
        phaseTicks = 0;
        gazePhase = GazePhase.ORIENTING;
        microExpressionCooldown = mob.getRandom().nextBetween(6, 16);

        configureDurations(getContext());
        maxContactDuration = orientDuration + softenDuration + connectionDuration;
        activeAttention = Math.max(activeAttention, lastAttentionStrength);
    }

    @Override
    protected void onStopGoal() {
        contactTicks = 0;
        focusPlayer = null;
        resetTracking();
        gazePhase = GazePhase.ORIENTING;
        phaseTicks = 0;
        microExpressionCooldown = 0;
        lastAttentionStrength = 0f;
        activeAttention = 0f;
        gazeCooldownTicks = mob.getRandom().nextBetween(RETRY_COOLDOWN_MIN, RETRY_COOLDOWN_MAX);
    }

    @Override
    protected void onTickGoal() {
        contactTicks++;
        phaseTicks++;

        if (focusPlayer == null) {
            return;
        }

        PetContext ctx = getContext();
        GazeObservation observation = observePlayerGaze(focusPlayer);
        boolean playerLooking = observation != null;

        if (playerLooking) {
            float attentionSample = computeAttentionStrength(observation, contactTicks);
            activeAttention = smoothAttention(activeAttention, attentionSample, 0.28f);
            lastAttentionStrength = activeAttention;
        } else {
            activeAttention = smoothAttention(activeAttention, 0.0f, 0.1f);
        }

        if (!playerLooking && activeAttention < 0.22f && gazePhase == GazePhase.CONNECTED && phaseTicks > 6) {
            advancePhase(GazePhase.SOFTENING);
        }

        steerTowardFocus(playerLooking, activeAttention);

        if (microExpressionCooldown <= 0) {
            playMicroExpression(ctx, activeAttention);
        } else {
            microExpressionCooldown--;
        }

        updatePhaseProgression(playerLooking, activeAttention);
    }

    private void configureDurations(PetContext ctx) {
        float bond = MathHelper.clamp(ctx.bondStrength(), 0.0f, 1.0f);
        float attention = MathHelper.clamp(lastAttentionStrength, 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);
        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);

        orientDuration = MathHelper.clamp(8 + Math.round((1.0f - bond) * 12.0f) - Math.round(attention * 6.0f), 6, 26);
        softenDuration = MathHelper.clamp(
            10 + Math.round(attention * 14.0f)
                + Math.round((0.5f - Math.abs(momentum - 0.5f)) * 10.0f)
                + Math.round((socialCharge - 0.5f) * 6.0f),
            8,
            32);

        int baseConnection = 32 + Math.round(bond * 48.0f) + Math.round(attention * 16.0f);
        baseConnection += Math.round((socialCharge - 0.5f) * 24.0f);
        if (ctx.hasPetsPlusComponent()) {
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.BONDED, 0.35f)) {
                baseConnection += 18;
            }
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.CALM, 0.45f)) {
                baseConnection += 12;
            }
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.RESTLESS, 0.3f)) {
                baseConnection -= 16;
            }
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.4f)) {
                baseConnection -= 10;
            }
        }

        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            baseConnection = Math.round(baseConnection * 0.7f);
        } else if (ctx.getAgeCategory() == PetContext.AgeCategory.MATURE) {
            baseConnection += 8;
        }

        float calmAlignment = MathHelper.clamp((0.5f - Math.abs(momentum - 0.5f)) * 2.0f, 0.0f, 1.0f);
        float calmFactor = MathHelper.lerp(calmAlignment, 0.85f, 1.15f);
        connectionDuration = MathHelper.clamp(Math.round(baseConnection * calmFactor), MIN_CONNECTION_DURATION, MAX_CONNECTION_DURATION);
    }

    private void steerTowardFocus(boolean playerLooking, float attentionLevel) {
        if (focusPlayer == null) {
            return;
        }

        double eyeY = focusPlayer.getEyeY();
        double baseEye = focusPlayer.getY() + focusPlayer.getStandingEyeHeight() * 0.6;
        double targetY;

        switch (gazePhase) {
            case ORIENTING -> targetY = MathHelper.lerp(0.55, baseEye, eyeY);
            case SOFTENING -> targetY = MathHelper.lerp(0.85, baseEye + 0.1, eyeY) - 0.05;
            case CONNECTED -> targetY = eyeY - 0.02;
            default -> targetY = eyeY;
        }

        float yawSpeed = switch (gazePhase) {
            case ORIENTING -> 24.0f;
            case SOFTENING -> 28.0f;
            case CONNECTED -> 34.0f;
        };
        float pitchSpeed = gazePhase == GazePhase.ORIENTING ? 24.0f : 30.0f;

        mob.getLookControl().lookAt(focusPlayer.getX(), targetY, focusPlayer.getZ(), yawSpeed, pitchSpeed);

        if ((playerLooking || attentionLevel > 0.45f) && gazePhase == GazePhase.CONNECTED) {
            mob.bodyYaw = MathHelper.lerp(0.12f, mob.bodyYaw, mob.headYaw);
        } else if (gazePhase == GazePhase.ORIENTING) {
            mob.headYaw = MathHelper.wrapDegrees(MathHelper.lerp(0.2f, mob.headYaw, mob.getYaw()));
        }
    }

    private void updatePhaseProgression(boolean playerLooking, float attentionLevel) {
        switch (gazePhase) {
            case ORIENTING -> {
                if (phaseTicks >= orientDuration && (playerLooking || attentionLevel > 0.24f)) {
                    advancePhase(GazePhase.SOFTENING);
                }
            }
            case SOFTENING -> {
                if (phaseTicks >= softenDuration && (playerLooking || attentionLevel > 0.32f || phaseTicks >= softenDuration + 10)) {
                    advancePhase(GazePhase.CONNECTED);
                }
            }
            case CONNECTED -> {
                if (phaseTicks >= connectionDuration || (attentionLevel < 0.14f && phaseTicks > MIN_CONNECTION_DURATION)) {
                    contactTicks = Math.max(contactTicks, maxContactDuration);
                }
            }
        }
    }

    private void advancePhase(GazePhase nextPhase) {
        if (gazePhase == nextPhase) {
            return;
        }
        gazePhase = nextPhase;
        phaseTicks = 0;
        microExpressionCooldown = mob.getRandom().nextBetween(8, 18);
    }

    private void playMicroExpression(PetContext ctx, float attentionLevel) {
        if (attentionLevel < 0.12f) {
            microExpressionCooldown = mob.getRandom().nextBetween(12, 20);
            return;
        }
        microExpressionCooldown = mob.getRandom().nextBetween(10, gazePhase == GazePhase.CONNECTED ? 20 : 16);

        switch (gazePhase) {
            case ORIENTING -> {
                float yawDrift = (mob.getRandom().nextFloat() - 0.5f) * 4.0f;
                mob.headYaw = MathHelper.wrapDegrees(mob.headYaw + yawDrift);
            }
            case SOFTENING -> {
                float tilt = (mob.getRandom().nextFloat() - 0.5f) * 6.0f;
                mob.setPitch(MathHelper.clamp(mob.getPitch() + tilt * 0.35f, -35.0f, 35.0f));
            }
            case CONNECTED -> {
                if (ctx.bondStrength() > 0.45f) {
                    mob.bodyYaw += (mob.getRandom().nextFloat() - 0.5f) * 5.0f;
                }
                if (ctx.bondStrength() > 0.6f && mob.getRandom().nextFloat() < 0.55f) {
                    float nod = (mob.getRandom().nextFloat() - 0.5f) * 4.0f;
                    mob.setPitch(MathHelper.clamp(mob.getPitch() + nod * 0.6f, -30.0f, 30.0f));
                }
            }
        }
    }
    
    /**
     * Calculate probability of pet responding to a player's gaze.
     * Respects pet autonomy - they don't always look back!
     */
    private float calculateResponseChance(PetContext ctx, PlayerEntity player, float attentionStrength, boolean isOwner, GazeObservation observation) {
        float baseChance = 0.70f; // 70% base expectation when someone is clearly engaged

        baseChance += (attentionStrength - 0.5f) * 0.30f;

        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);
        float mentalFocus = MathHelper.clamp(ctx.mentalFocus(), 0.0f, 1.0f);

        float socialBlend = MathHelper.clamp((socialCharge - 0.35f) / 0.3f, -1.0f, 1.0f);
        float socialScale = MathHelper.lerp((socialBlend + 1.0f) * 0.5f, 0.68f, 1.16f);
        baseChance *= socialScale;

        float focusBlend = MathHelper.clamp((mentalFocus - 0.45f) / 0.35f, -1.0f, 1.0f);
        float focusScale = MathHelper.lerp((focusBlend + 1.0f) * 0.5f, 0.74f, 1.08f);
        baseChance *= focusScale;

        if (observation != null) {
            float alignmentScale = MathHelper.clamp((observation.alignment() - MIN_ALIGNMENT_DOT) / (DIRECT_ALIGNMENT_DOT - MIN_ALIGNMENT_DOT), 0.0f, 1.0f);
            baseChance += alignmentScale * 0.08f;

            float proximity = MathHelper.clamp(1.0f - (float) Math.sqrt(observation.distanceSq()) / 10.0f, 0.0f, 1.0f);
            baseChance += proximity * 0.08f;
        }

        float bond = MathHelper.clamp(ctx.bondStrength(), 0.0f, 1.0f);
        if (isOwner) {
            baseChance += 0.12f;
            baseChance += bond * 0.35f;
        } else {
            if (ctx.owner() != null) {
                baseChance -= 0.05f;
            }
            baseChance += MathHelper.clamp((bond - 0.35f) * 0.25f, -0.06f, 0.18f);
        }

        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);
        float calmWeight = MathHelper.clamp((0.5f - Math.abs(momentum - 0.5f)) * 1.8f, 0.0f, 1.0f);
        baseChance = MathHelper.lerp(calmWeight, baseChance * 0.82f, baseChance * 1.1f);

        if (ctx.hasPetsPlusComponent()) {
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.BONDED, 0.4f)) {
                baseChance += 0.22f;
            }
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.CALM, 0.5f)) {
                baseChance += 0.14f;
            }
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.PLAYFUL, 0.5f)) {
                baseChance -= 0.12f;
            }
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.RESTLESS, 0.3f)) {
                baseChance -= 0.20f;
            }
            if (ctx.hasMoodInBlend(woflo.petsplus.state.PetComponent.Mood.FOCUSED, 0.4f)) {
                baseChance *= 0.6f;
            }
        }

        if (ctx.getAgeCategory() == PetContext.AgeCategory.YOUNG) {
            baseChance *= 0.75f;
        } else if (ctx.getAgeCategory() == PetContext.AgeCategory.MATURE) {
            baseChance += 0.05f;
        }

        if (playerLookingTickCount > 40) {
            baseChance += 0.12f + attentionStrength * 0.08f;
        }

        return MathHelper.clamp(baseChance, 0.08f, 0.98f);
    }

    /**
     * Check if a player is looking at the pet (uses same logic as UI focus detection).
     */
    private boolean isPlayerLookingAtPet(PlayerEntity player) {
        return observePlayerGaze(player) != null;
    }

    private void resetTracking() {
        trackingPlayer = null;
        playerLookingTickCount = 0;
        trackingAttention = 0f;
    }

    private GazeObservation findLookingPlayer(PetContext ctx) {
        PlayerEntity owner = ctx.owner();

        if (trackingPlayer != null) {
            GazeObservation existing = observePlayerGaze(trackingPlayer);
            if (existing != null) {
                return existing;
            }
        }

        if (owner != null) {
            GazeObservation ownerObservation = observePlayerGaze(owner);
            if (ownerObservation != null) {
                return ownerObservation;
            }
        }

        GazeObservation best = null;
        for (Entity entity : ctx.nearbyEntities()) {
            if (entity instanceof PlayerEntity player) {
                if (owner != null && player.getUuid().equals(owner.getUuid())) {
                    continue;
                }
                GazeObservation observation = observePlayerGaze(player);
                if (observation != null) {
                    if (best == null) {
                        best = observation;
                    } else if (observation.alignment() > best.alignment() + 0.02f ||
                        (Math.abs(observation.alignment() - best.alignment()) <= 0.02f && observation.distanceSq() < best.distanceSq())) {
                        best = observation;
                    }
                }
            }
        }

        return best;
    }

    private GazeObservation observePlayerGaze(PlayerEntity player) {
        if (player == null || !player.isAlive() || player.isRemoved() || player.isSpectator()) {
            return null;
        }

        double distanceSq = player.squaredDistanceTo(mob);
        if (distanceSq > 100.0) {
            return null;
        }

        if (!player.canSee(mob)) {
            return null;
        }

        Vec3d playerPos = player.getCameraPosVec(1.0f);
        Vec3d toPet = mob.getEyePos().subtract(playerPos);
        double lengthSq = toPet.lengthSquared();
        if (lengthSq < 1.0e-4) {
            return null;
        }

        Vec3d lookVector = player.getRotationVec(1.0f);
        double dot = lookVector.normalize().dotProduct(toPet.normalize());
        float alignment = (float) dot;

        if (alignment < MIN_ALIGNMENT_DOT) {
            return null;
        }

        return new GazeObservation(player, alignment, distanceSq);
    }

    private float updateTrackingAttention(GazeObservation observation, int holdTicks) {
        float sample = computeAttentionStrength(observation, holdTicks);
        trackingAttention = smoothAttention(trackingAttention, sample, TRACKING_SMOOTHING);
        return trackingAttention;
    }

    private float computeAttentionStrength(GazeObservation observation, int holdTicks) {
        float alignmentScale = MathHelper.clamp((observation.alignment() - MIN_ALIGNMENT_DOT) / (DIRECT_ALIGNMENT_DOT - MIN_ALIGNMENT_DOT), 0.0f, 1.0f);
        float hold = MathHelper.clamp(holdTicks / 70.0f, 0.0f, 1.0f);
        float proximity = MathHelper.clamp(1.0f - (float) Math.sqrt(observation.distanceSq()) / 10.0f, 0.0f, 1.0f);
        return MathHelper.clamp(alignmentScale * 0.6f + hold * 0.25f + proximity * 0.15f, 0.0f, 1.0f);
    }

    private float smoothAttention(float current, float sample, float smoothing) {
        return MathHelper.lerp(MathHelper.clamp(smoothing, 0.0f, 1.0f), current, sample);
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        // Eye contact is a bonding moment - subtle but meaningful emotions
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.18f)      // Connection
            .add(woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.15f)     // Devotion
            .add(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.12f)   // Comfortable intimacy
            .add(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.10f)       // Perfect moment
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.UBUNTU, 0.015f)
            .build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.8f;

        // Very engaging for bonded pets
        if (ctx.bondStrength() > 0.7f) {
            engagement = 1.0f;
        }

        // Peak engagement in first few seconds (novelty)
        if (contactTicks < 20) {
            engagement = Math.min(1.0f, engagement + 0.2f);
        }

        if (activeAttention > 0.4f) {
            engagement = Math.min(1.0f, engagement + activeAttention * 0.15f);
        }

        return engagement;
    }
}

