package woflo.petsplus.ai.goals.follow;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.movement.PositionalOffsetModifier;
import woflo.petsplus.ai.movement.TargetDistanceModifier;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.behavior.social.PetSocialData;
import woflo.petsplus.behavior.social.SocialContextSnapshot;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Influencer goal that keeps a comfortable spacing within owner pet packs.
 */
public class MaintainPackSpacingGoal extends AdaptiveGoal {
    static final double SPACING_SAMPLE_RADIUS = 4.5;
    static final double SPACING_PACK_RADIUS = 4.0;
    static final int SPACING_MAX_SAMPLES = 3;
    private static final long FOLLOW_SAMPLE_INTERVAL = 5L;
    private static final long SWARM_CHECK_INTERVAL = 20L;

    private long lastSwarmCheckTick = Long.MIN_VALUE;
    private int lastObservedSwarmSize = 1;

    public MaintainPackSpacingGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.MAINTAIN_PACK_SPACING), EnumSet.noneOf(Control.class));
    }

    @Override
    protected boolean canStartGoal() {
        if (petComponent == null || movementDirector == null) {
            return false;
        }
        MobEntity pet = petComponent.getPetEntity();
        if (pet == null || pet.isSleeping()) {
            return false;
        }
        if (pet instanceof PetsplusTameable tameable && tameable.petsplus$isSitting()) {
            return false;
        }
        PlayerEntity owner = petComponent.getOwner() instanceof PlayerEntity player ? player : null;
        if (owner == null) {
            return false;
        }
        long now = owner.getEntityWorld().getTime();
        refreshSwarmSize(owner, now);
        return lastObservedSwarmSize > 1;
    }

    @Override
    protected boolean shouldContinueGoal() {
        return canStartGoal();
    }

    @Override
    protected void onStartGoal() {
        updateSpacing();
    }

    @Override
    protected void onStopGoal() {
        if (movementDirector != null) {
            movementDirector.clearSource(goalId);
        }
        petComponent.setFollowSpacingFocus(null, Long.MIN_VALUE);
        lastSwarmCheckTick = Long.MIN_VALUE;
        lastObservedSwarmSize = 1;
    }

    @Override
    protected void onTickGoal() {
        updateSpacing();
    }

    @Override
    protected float calculateEngagement() {
        return 0.3f;
    }

    private void updateSpacing() {
        if (movementDirector == null) {
            return;
        }

        PlayerEntity owner = petComponent.getOwner() instanceof PlayerEntity player ? player : null;
        if (owner == null) {
            movementDirector.clearSource(goalId);
            petComponent.setFollowSpacingFocus(null, Long.MIN_VALUE);
            return;
        }
        long now = owner.getEntityWorld().getTime();
        refreshSwarmSize(owner, now);
        if (lastObservedSwarmSize <= 1) {
            movementDirector.clearSource(goalId);
            petComponent.setFollowSpacingFocus(null, Long.MIN_VALUE);
            return;
        }

        long lastSample = petComponent.getFollowSpacingSampleTick();
        if (lastSample != Long.MIN_VALUE && now - lastSample < FOLLOW_SAMPLE_INTERVAL) {
            return;
        }

        FollowSpacingResult result = FollowSpacingResult.empty();
        StateManager manager = petComponent.getStateManager();
        if (manager == null && owner.getEntityWorld() instanceof ServerWorld serverWorld) {
            manager = StateManager.forWorld(serverWorld);
        }
        if (manager != null) {
            PetSwarmIndex swarmIndex = manager.getSwarmIndex();
            if (swarmIndex != null) {
                result = computeSpacing(now, owner, swarmIndex);
            }
        }

        double weight = Math.max(0.0, movementConfig.influencerWeight());
        if (weight <= 0.0) {
            movementDirector.clearSource(goalId);
            petComponent.setFollowSpacingFocus(null, Long.MIN_VALUE);
            return;
        }

        movementDirector.setPositionalOffset(goalId,
            new PositionalOffsetModifier(result.offsetX, 0.0, result.offsetZ, weight));
        movementDirector.setTargetDistance(goalId,
            new TargetDistanceModifier(0.0, result.padding, weight));
        petComponent.setFollowSpacingSample(result.offsetX, result.offsetZ, result.padding, now);
        if (result.focus != null) {
            petComponent.setFollowSpacingFocus(result.focus, now + FOLLOW_SAMPLE_INTERVAL);
        } else {
            petComponent.setFollowSpacingFocus(null, Long.MIN_VALUE);
        }
    }

    private void refreshSwarmSize(PlayerEntity owner, long now) {
        if (lastSwarmCheckTick != Long.MIN_VALUE && now - lastSwarmCheckTick < SWARM_CHECK_INTERVAL) {
            return;
        }
        lastSwarmCheckTick = now;
        lastObservedSwarmSize = Math.max(1, resolveSwarmSize(owner));
    }

    private int resolveSwarmSize(PlayerEntity owner) {
        StateManager manager = petComponent.getStateManager();
        if (manager == null && owner.getEntityWorld() instanceof ServerWorld serverWorld) {
            manager = StateManager.forWorld(serverWorld);
        }
        if (manager == null) {
            return 1;
        }
        PetSwarmIndex swarmIndex = manager.getSwarmIndex();
        if (swarmIndex == null) {
            return 1;
        }
        List<PetSwarmIndex.SwarmEntry> entries = swarmIndex.snapshotOwner(owner.getUuid());
        if (entries == null || entries.isEmpty()) {
            return 1;
        }
        return entries.size();
    }

    private FollowSpacingResult computeSpacing(long now, PlayerEntity owner, PetSwarmIndex swarmIndex) {
        Map<MobEntity, PetSocialData> cache = new HashMap<>();
        PetSocialData selfData = new PetSocialData(mob, petComponent, now);
        cache.put(mob, selfData);

        SocialContextSnapshot.NeighborSummary summary = SocialContextSnapshot.NeighborSummary.collect(
            swarmIndex, mob, petComponent, selfData, cache, now,
            SPACING_SAMPLE_RADIUS, SPACING_MAX_SAMPLES, SPACING_PACK_RADIUS);

        List<SocialContextSnapshot.NeighborSample> neighbors = summary.nearestWithin(
            SPACING_PACK_RADIUS * SPACING_PACK_RADIUS, SPACING_MAX_SAMPLES);
        if (neighbors.isEmpty()) {
            return FollowSpacingResult.empty();
        }

        Vec3d ownerPos = owner.getEntityPos();
        Vec3d petPos = mob.getEntityPos();
        Vec3d ownerToPet = petPos.subtract(ownerPos);
        double ownerDistanceSq = ownerToPet.lengthSquared();
        Vec3d forward = ownerDistanceSq < 0.0001 ? new Vec3d(1.0, 0.0, 0.0) : ownerToPet.normalize();
        Vec3d lateral = new Vec3d(-forward.z, 0.0, forward.x);

        double extension = 0.0;
        double lateralOffset = 0.0;
        double padding = 0.0;
        double weight = 0.0;
        MobEntity focus = null;

        for (SocialContextSnapshot.NeighborSample sample : neighbors) {
            Vec3d toNeighbor = new Vec3d(sample.pet().getX() - ownerPos.x, 0.0, sample.pet().getZ() - ownerPos.z);
            double forwardComponent = toNeighbor.dotProduct(forward);
            double lateralComponent = toNeighbor.dotProduct(lateral);
            double weightContribution = Math.max(0.1, 1.0 - Math.min(1.0, Math.sqrt(sample.squaredDistance()) / 6.0));
            extension += forwardComponent * weightContribution;
            lateralOffset += lateralComponent * weightContribution;
            padding += Math.max(0.0, 1.5 - Math.abs(lateralComponent)) * weightContribution;
            weight += weightContribution;
            if (focus == null) {
                focus = sample.pet();
            }
        }

        if (weight > 0.0) {
            extension /= weight;
            lateralOffset /= weight;
            padding = MathHelper.clamp((float) (padding / weight), 0.0f, 3.0f);
        }

        Vec3d offset = forward.multiply(extension).add(lateral.multiply(lateralOffset));
        return new FollowSpacingResult(offset.x, offset.z, (float) padding, focus);
    }

    private record FollowSpacingResult(double offsetX, double offsetZ, float padding, MobEntity focus) {
        static FollowSpacingResult empty() {
            return new FollowSpacingResult(0.0, 0.0, 0.0f, null);
        }
    }
}
