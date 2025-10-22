package woflo.petsplus.ai.goals.special;

import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.capability.MobCapabilities;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.EmotionFeedback;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.special.survey.SurveyTargetRegistry;
import woflo.petsplus.ai.goals.special.survey.SurveyTargetRegistry.SurveyTarget;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Aerial survey behaviour for bonded flying companions. The pet gains height,
 * fixes on an interesting structure in the vicinity, and guides its owner by
 * holding a steady hover and gaze instead of spelling out directions. Targets
 * and search heuristics are data-driven through {@link SurveyTargetRegistry} so
 * the behaviour can evolve alongside new world content.
 */
public class SkySurveyGoal extends AdaptiveGoal {

    private static final int ASCENT_SETTLE_TICKS = 120;
    private static final int SURVEY_HOVER_TICKS = 160;
    private static final int DESCENT_TIMEOUT_TICKS = 120;
    private static final double HOVER_SMOOTHING = 0.18;
    private static final double DESCENT_RANGE = 1.8;
    private static final double ORBIT_SPEED = 0.045;
    private static final double ORBIT_RADIUS_MIN = 1.6;
    private static final double ORBIT_RADIUS_MAX = 6.0;
    private static final double ORBIT_RADIUS_SCALE = 0.1;

    private SurveyCandidate preparedCandidate;
    private SurveyCandidate activeCandidate;
    private Phase phase = Phase.ASCEND;
    private Vec3d hoverTarget;
    private Vec3d landingTarget;
    private Vec3d startingPos;
    private int phaseTicks;
    private int totalTicks;
    private double orbitSeed;
    private double bobSeed;

    public SkySurveyGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.SKY_SURVEY), java.util.EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        if (!(mob.getNavigation() instanceof BirdNavigation) || !MobCapabilities.canFly(mob)) {
            return false;
        }
        if (mob.isLeashed()) {
            return false;
        }
        if (mob.isTouchingWater() && !MobCapabilities.prefersWater(mob)) {
            return false;
        }

        PetContext context = getContext();
        if (context == null || context.owner() == null) {
            return false;
        }
        if (context.dormant()) {
            return false;
        }
        if (context.component() != null && context.component().isPerched()) {
            return false;
        }
        if (context.owner() != null) {
            PlayerEntity owner = context.owner();
            if (!owner.isAlive() || owner.isSpectator()) {
                return false;
            }
        }
        if (!context.ownerNearby() || context.distanceToOwner() > 32.0f) {
            return false;
        }
        if (context.hasPetsPlusComponent() && context.normalizedMentalActivity() < 0.1f) {
            return false;
        }
        PetContextCrowdSummary summary = context.crowdSummary();
        if (summary != null && summary.hostileCount() > 0) {
            return false; // keep pets from leaving during tense moments
        }

        preparedCandidate = Resolver.prepare(mob, context).orElse(null);
        return preparedCandidate != null;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (!(mob.getNavigation() instanceof BirdNavigation)) {
            return false;
        }
        if (activeCandidate == null) {
            return false;
        }
        if (mob.isLeashed()) {
            return false;
        }
        PetContext context = getContext();
        if (context == null) {
            return false;
        }
        PetContextCrowdSummary summary = context.crowdSummary();
        if (summary != null && summary.hostileCount() > 0) {
            return false;
        }
        if (phase == Phase.DESCEND && mob.isOnGround()) {
            return false;
        }
        return true;
    }

    @Override
    protected void onStartGoal() {
        this.activeCandidate = this.preparedCandidate;
        this.preparedCandidate = null;
        this.phase = Phase.ASCEND;
        this.phaseTicks = 0;
        this.totalTicks = 0;
        this.startingPos = mob.getEntityPos();
        this.hoverTarget = computeHoverTarget(getAnchorPosition());
        this.landingTarget = startingPos;
        this.orbitSeed = mob.getRandom().nextDouble() * Math.PI * 2.0;
        this.bobSeed = mob.getRandom().nextDouble() * Math.PI * 2.0;

        PetComponent component = PetComponent.get(mob);
        if (component != null && mob.getEntityWorld() instanceof ServerWorld serverWorld) {
            component.setStateData(PetComponent.StateKeys.SURVEY_LAST_TARGET_TICK, serverWorld.getTime());
        }
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        preparedCandidate = null;
        activeCandidate = null;
        hoverTarget = null;
        landingTarget = null;
        startingPos = null;
        phaseTicks = 0;
    }

    @Override
    protected void onTickGoal() {
        totalTicks++;
        if (activeCandidate == null) {
            requestStop();
            return;
        }

        phaseTicks++;
        Vec3d focus = activeCandidate.focus();

        switch (phase) {
            case ASCEND -> tickAscend(focus);
            case SURVEY -> tickSurvey(focus);
            case DESCEND -> tickDescend();
        }
    }

    private void tickAscend(Vec3d focus) {
        Vec3d anchor = getAnchorPosition();
        Vec3d desired = computeHoverTarget(anchor);
        hoverTarget = smoothHover(desired, hoverTarget);
        moveTowards(hoverTarget, 1.05);
        orientTowards(focus, 25.0f, 20.0f);

        double distance = mob.getEntityPos().distanceTo(hoverTarget);
        if (distance < 1.4 || phaseTicks >= ASCENT_SETTLE_TICKS) {
            switchPhase(Phase.SURVEY);
            mob.playAmbientSound();
        }
    }

    private void tickSurvey(Vec3d focus) {
        Vec3d anchor = getAnchorPosition();
        Vec3d desired = computeHoverTarget(anchor);
        hoverTarget = smoothHover(desired, hoverTarget);
        moveTowards(hoverTarget, 0.92);

        PetContext ctx = getContext();
        PlayerEntity owner = ctx != null ? ctx.owner() : null;
        boolean glanceAtOwner = owner != null && (phaseTicks % 55 == 0 || owner.squaredDistanceTo(mob) < 36.0);
        if (glanceAtOwner) {
            mob.getLookControl().lookAt(owner, 20.0f, 20.0f);
        } else {
            orientTowards(focus, 40.0f, 22.0f);
        }

        if (phaseTicks % 48 == 0) {
            mob.playAmbientSound();
        }

        double dx = focus.x - anchor.x;
        double dz = focus.z - anchor.z;
        double horizontalSq = (dx * dx) + (dz * dz);
        SurveyTarget target = activeCandidate.target();
        double minRange = target.minDistance() * 0.5;
        double maxRange = target.maxDistance() * 1.25;
        double minRangeSq = minRange * minRange;
        double maxRangeSq = maxRange * maxRange;
        if (horizontalSq < minRangeSq || horizontalSq > maxRangeSq) {
            switchPhase(Phase.DESCEND);
            return;
        }

        if (phaseTicks >= SURVEY_HOVER_TICKS) {
            switchPhase(Phase.DESCEND);
        }
    }

    private void tickDescend() {
        if (landingTarget == null) {
            landingTarget = computeLandingTarget();
        }

        moveTowards(landingTarget, 1.12);
        orientTowards(landingTarget.add(0.0, 0.5, 0.0), 20.0f, 20.0f);

        if (mob.isOnGround() || mob.getEntityPos().distanceTo(landingTarget) <= DESCENT_RANGE || phaseTicks >= DESCENT_TIMEOUT_TICKS) {
            requestStop();
        }
    }

    private void moveTowards(Vec3d target, double speed) {
        if (target == null) {
            return;
        }
        mob.getMoveControl().moveTo(target.x, target.y, target.z, speed);
        if (mob.getNavigation() instanceof BirdNavigation navigation) {
            navigation.setSpeed(speed);
            navigation.startMovingTo(target.x, target.y, target.z, speed);
        }
    }

    private void orientTowards(Vec3d focus, float yawSpeed, float pitchSpeed) {
        mob.getLookControl().lookAt(focus.x, focus.y, focus.z, yawSpeed, pitchSpeed);
        mob.headYaw = mob.getYaw();
    }

    private Vec3d smoothHover(Vec3d desired, @Nullable Vec3d current) {
        if (desired == null) {
            return current;
        }
        if (current == null) {
            return desired;
        }
        Vec3d delta = desired.subtract(current).multiply(HOVER_SMOOTHING);
        Vec3d next = current.add(delta);
        return ensureAirspace(next);
    }

    private Vec3d computeHoverTarget(Vec3d anchor) {
        if (activeCandidate == null) {
            return anchor;
        }
        Vec3d focus = activeCandidate.focus();
        Vec3d delta = new Vec3d(focus.x - anchor.x, 0.0, focus.z - anchor.z);
        double distanceSquared = delta.lengthSquared();
        double distance = Math.sqrt(distanceSquared);
        double forwardMag = MathHelper.clamp(distance * 0.18 + 2.2, 2.6, 11.0);
        Vec3d forward;
        if (distance > 0.0001) {
            double maxForward = Math.min(forwardMag, distance * 0.45);
            double scale = maxForward / distance;
            forward = delta.multiply(scale);
        } else {
            forward = Vec3d.ZERO;
        }

        double orbitRadius = MathHelper.clamp(distance * ORBIT_RADIUS_SCALE + ORBIT_RADIUS_MIN, ORBIT_RADIUS_MIN, ORBIT_RADIUS_MAX);
        double orbitAngle = orbitSeed + totalTicks * ORBIT_SPEED;
        Vec3d orbit = new Vec3d(Math.cos(orbitAngle), 0.0, Math.sin(orbitAngle)).multiply(orbitRadius);

        double altitude = MathHelper.clamp(distance * 0.32 + 6.0, 8.0, 30.0);
        double bob = Math.sin((totalTicks * 0.12) + bobSeed) * 0.8;
        double targetY = anchor.y + altitude + bob;

        World world = mob.getEntityWorld();
        if (world != null) {
            BlockPos anchorPos = BlockPos.ofFloored(anchor);
            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, anchorPos.getX(), anchorPos.getZ());
            double maxY = Math.max(topY + 18.0, anchor.y + 8.0);
            targetY = MathHelper.clamp(targetY, anchor.y + 6.0, maxY);
        }

        Vec3d desired = new Vec3d(anchor.x + forward.x + orbit.x, targetY, anchor.z + forward.z + orbit.z);
        return ensureAirspace(desired);
    }

    private Vec3d ensureAirspace(Vec3d desired) {
        if (desired == null) {
            return null;
        }
        World world = mob.getEntityWorld();
        if (world == null) {
            return desired;
        }
        Box box = mob.getBoundingBox();
        double offsetX = desired.x - mob.getX();
        double offsetY = desired.y - mob.getY();
        double offsetZ = desired.z - mob.getZ();
        Box shifted = box.offset(offsetX, offsetY, offsetZ);
        if (world.isSpaceEmpty(mob, shifted.expand(0.1))) {
            return desired;
        }
        // Try nudging upward slightly to clear obstacles
        for (double y = 0.5; y <= 4.0; y += 0.5) {
            Box raised = shifted.offset(0.0, y, 0.0);
            if (world.isSpaceEmpty(mob, raised.expand(0.1))) {
                return new Vec3d(desired.x, desired.y + y, desired.z);
            }
        }
        return desired;
    }

    private Vec3d getAnchorPosition() {
        PetContext context = getContext();
        PlayerEntity owner = context != null ? context.owner() : null;
        if (owner != null && owner.isAlive()) {
            return owner.getEntityPos();
        }
        return startingPos != null ? startingPos : mob.getEntityPos();
    }

    private Vec3d computeLandingTarget() {
        Vec3d anchor = getAnchorPosition();
        World world = mob.getEntityWorld();
        if (world == null) {
            return anchor;
        }
        BlockPos anchorPos = BlockPos.ofFloored(anchor);
        int groundY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, anchorPos.getX(), anchorPos.getZ());
        return new Vec3d(anchor.x, groundY + 1.0, anchor.z);
    }

    private void switchPhase(Phase next) {
        if (phase == next) {
            return;
        }
        phase = next;
        phaseTicks = 0;
        if (next == Phase.DESCEND) {
            landingTarget = computeLandingTarget();
        }
    }

    @Override
    protected EmotionFeedback defineEmotionFeedback() {
        return new EmotionFeedback.Builder()
            .add(PetComponent.Emotion.CURIOUS, 0.22f)
            .add(PetComponent.Emotion.YUGEN, 0.16f)
            .add(PetComponent.Emotion.FOCUSED, 0.12f)
            .build();
    }

    @Override
    protected float calculateEngagement() {
        PetContext context = getContext();
        if (context == null) {
            return 0.6f;
        }
        float base = 0.65f;
        if (context.hasMoodInBlend(PetComponent.Mood.PROTECTIVE, 0.35f)) {
            base += 0.15f;
        }
        base += MathHelper.clamp((context.behavioralMomentum() - 0.5f) * 0.2f, -0.1f, 0.1f);
        return MathHelper.clamp(base, 0.4f, 0.95f);
    }

    private enum Phase {
        ASCEND,
        SURVEY,
        DESCEND
    }

    private record SurveyCandidate(SurveyTarget target, BlockPos interestPos, Vec3d focus) {
    }

    private static final class Resolver {

        private static final int MIN_SEARCH_INTERVAL_TICKS = 40;

        private Resolver() {
        }

        static Optional<SurveyCandidate> prepare(MobEntity mob, PetContext context) {
            if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
                return Optional.empty();
            }

            PetComponent component = context.component();
            long now = serverWorld.getTime();
            if (component != null) {
                Optional<SurveyCandidate> cached = reuseCached(serverWorld, component, mob.getBlockPos(), now);
                if (cached.isPresent()) {
                    return cached;
                }
                Long lastSearchTick = component.getStateData(PetComponent.StateKeys.SURVEY_LAST_SEARCH_TICK, Long.class);
                if (lastSearchTick != null && now - lastSearchTick < MIN_SEARCH_INTERVAL_TICKS) {
                    return Optional.empty();
                }
                component.setStateData(PetComponent.StateKeys.SURVEY_LAST_SEARCH_TICK, now);
            }

            Optional<SurveyCandidate> located = locateFresh(serverWorld, mob, context);
            located.ifPresent(candidate -> store(component, serverWorld, candidate, now));
            return located;
        }

        private static Optional<SurveyCandidate> reuseCached(ServerWorld world, PetComponent component, BlockPos origin, long now) {
            BlockPos cachedPos = component.getStateData(PetComponent.StateKeys.SURVEY_LAST_TARGET_POS, BlockPos.class);
            Identifier targetId = component.getStateData(PetComponent.StateKeys.SURVEY_LAST_TARGET_ID, Identifier.class);
            String kindRaw = component.getStateData(PetComponent.StateKeys.SURVEY_LAST_TARGET_KIND, String.class);
            Identifier dimensionId = component.getStateData(PetComponent.StateKeys.SURVEY_LAST_TARGET_DIMENSION, Identifier.class);
            Long cachedTick = component.getStateData(PetComponent.StateKeys.SURVEY_LAST_TARGET_TICK, Long.class);
            if (cachedPos == null || targetId == null || kindRaw == null || dimensionId == null) {
                return Optional.empty();
            }
            if (!Objects.equals(dimensionId, world.getRegistryKey().getValue())) {
                return Optional.empty();
            }
            SurveyTarget target = matchTarget(world.getRegistryKey(), targetId, kindRaw);
            if (target == null) {
                return Optional.empty();
            }
            if (cachedTick != null && target.recacheCooldownTicks() > 0 && now - cachedTick > target.recacheCooldownTicks()) {
                return Optional.empty();
            }
            if (!world.isChunkLoaded(cachedPos)) {
                return Optional.empty();
            }
            Vec3d originCenter = centerXZ(origin);
            Vec3d cachedCenter = centerXZ(cachedPos);
            double dx = cachedCenter.x - originCenter.x;
            double dz = cachedCenter.z - originCenter.z;
            double horizontalSq = (dx * dx) + (dz * dz);
            double minSq = target.minDistance() * target.minDistance();
            double maxSq = target.maxDistance() * target.maxDistance();
            if (horizontalSq < minSq || horizontalSq > maxSq) {
                return Optional.empty();
            }
            Vec3d focus = Vec3d.ofCenter(cachedPos);
            return Optional.of(new SurveyCandidate(target, cachedPos, focus));
        }

        private static Optional<SurveyCandidate> locateFresh(ServerWorld world, MobEntity mob, PetContext context) {
            List<SurveyTarget> candidates = new ArrayList<>(SurveyTargetRegistry.targetsFor(world.getRegistryKey()));
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            candidates.removeIf(target -> !target.matchesDimension(world.getRegistryKey()));
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            List<SurveyTarget> ordered = prioritizeTargets(mob, context, candidates);
            for (SurveyTarget target : ordered) {
                Optional<SurveyCandidate> located = locateForTarget(world, mob, target);
                if (located.isPresent()) {
                    return located;
                }
            }
            return Optional.empty();
        }

        private static List<SurveyTarget> prioritizeTargets(MobEntity mob, PetContext context, List<SurveyTarget> base) {
            if (base.isEmpty()) {
                return List.of();
            }
            List<WeightedTarget> pool = new ArrayList<>(base.size());
            for (SurveyTarget target : base) {
                double weight = computeWeight(target, mob, context);
                if (weight <= 0.0) {
                    continue;
                }
                pool.add(new WeightedTarget(target, weight));
            }
            if (pool.isEmpty()) {
                return List.of();
            }

            List<SurveyTarget> ordered = new ArrayList<>(pool.size());
            while (!pool.isEmpty()) {
                double total = 0.0;
                for (WeightedTarget entry : pool) {
                    total += entry.weight();
                }
                double pick = mob.getRandom().nextDouble() * Math.max(total, 0.0001);
                double cursor = 0.0;
                int chosenIndex = pool.size() - 1;
                for (int i = 0; i < pool.size(); i++) {
                    cursor += pool.get(i).weight();
                    if (pick <= cursor) {
                        chosenIndex = i;
                        break;
                    }
                }
                WeightedTarget chosen = pool.remove(chosenIndex);
                ordered.add(chosen.target());
            }
            return ordered;
        }

        private static double computeWeight(SurveyTarget target, MobEntity mob, PetContext context) {
            double weight = Math.max(0.001, target.weight());
            if (context != null && context.hasPetsPlusComponent()) {
                if (context.hasMoodInBlend(PetComponent.Mood.CURIOUS, 0.28f)) {
                    weight *= 1.12;
                }
                if (context.hasMoodInBlend(PetComponent.Mood.FOCUSED, 0.24f)) {
                    weight *= 1.08;
                }
                if (context.hasMoodInBlend(PetComponent.Mood.RESTLESS, 0.28f)) {
                    weight *= 0.88;
                }
                float focus = context.normalizedMentalActivity();
                weight *= MathHelper.clamp(0.85 + focus * 0.5, 0.85, 1.35);
            }
            weight *= 0.85 + mob.getRandom().nextDouble() * 0.3;
            return weight;
        }

        private static Optional<SurveyCandidate> locateForTarget(ServerWorld world, MobEntity mob, SurveyTarget target) {
            if (target.kind() != SurveyTargetRegistry.Kind.STRUCTURE_TAG) {
                return Optional.empty();
            }
            BlockPos origin = mob.getBlockPos();
            Vec3d mobPos = mob.getEntityPos();
            Optional<BlockPos> located = locateStructureTag(world, origin, target);
            if (located.isEmpty()) {
                return Optional.empty();
            }
            BlockPos pos = located.get();
            Vec3d focus = Vec3d.ofCenter(pos);
            double dx = focus.x - mobPos.x;
            double dz = focus.z - mobPos.z;
            double horizontalSq = dx * dx + dz * dz;
            double min = target.minDistance();
            double max = target.maxDistance();
            double minSq = min * min;
            double maxSq = max * max;
            if (horizontalSq < minSq || horizontalSq > maxSq) {
                return Optional.empty();
            }
            return Optional.of(new SurveyCandidate(target, pos, focus));
        }

        private static Optional<BlockPos> locateStructureTag(ServerWorld world, BlockPos origin, SurveyTarget target) {
            TagKey<Structure> tag = TagKey.of(RegistryKeys.STRUCTURE, target.id());
            BlockPos located = world.locateStructure(tag, origin, target.searchRadius(), target.skipKnown());
            return located != null ? Optional.of(located) : Optional.empty();
        }

        private static void store(@Nullable PetComponent component, ServerWorld world, SurveyCandidate candidate, long now) {
            if (component == null) {
                return;
            }
            component.setStateData(PetComponent.StateKeys.SURVEY_LAST_TARGET_ID, candidate.target().id());
            component.setStateData(PetComponent.StateKeys.SURVEY_LAST_TARGET_KIND, candidate.target().kind().name().toLowerCase(Locale.ROOT));
            component.setStateData(PetComponent.StateKeys.SURVEY_LAST_TARGET_DIMENSION, world.getRegistryKey().getValue());
            component.setStateData(PetComponent.StateKeys.SURVEY_LAST_TARGET_POS, candidate.interestPos());
            component.setStateData(PetComponent.StateKeys.SURVEY_LAST_TARGET_TICK, now);
        }

        private record WeightedTarget(SurveyTarget target, double weight) {
        }

        private static SurveyTarget matchTarget(RegistryKey<World> dimension, Identifier id, String kindRaw) {
            List<SurveyTarget> targets = SurveyTargetRegistry.targetsFor(dimension);
            for (SurveyTarget target : targets) {
                if (target.id().equals(id) && target.kind().name().equalsIgnoreCase(kindRaw)) {
                    return target;
                }
            }
            return null;
        }

        private static Vec3d centerXZ(BlockPos pos) {
            return new Vec3d(pos.getX() + 0.5, 0.0, pos.getZ() + 0.5);
        }
    }
}
