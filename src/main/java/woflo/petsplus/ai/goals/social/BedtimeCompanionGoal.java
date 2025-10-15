package woflo.petsplus.ai.goals.social;

import net.minecraft.block.BedBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.EmotionFeedback;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.relationships.InteractionType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Adaptive social goal where pets gather around a sleeping owner for cozy bedtime companionship.
 */
public class BedtimeCompanionGoal extends AdaptiveGoal {
    private static final double START_DISTANCE_SQ = 14.0d * 14.0d;
    private static final double ARRIVE_DISTANCE_SQ_TOP = 0.36d;
    private static final double ARRIVE_DISTANCE_SQ_SIDE = 0.85d;
    private static final double ARRIVE_DISTANCE_SQ_PERCH = 0.42d;
    private static final int RELATIONSHIP_PULSE_INTERVAL = 100;

    private SnuggleStyle style = SnuggleStyle.BED_SIDE;
    private SnugglePlan plan;
    private UUID ownerUuid;
    private int settleTicks;
    private int relationshipTicks;
    private int ambienceCooldown;
    private int planCooldown;
    private int stuckTicks;
    private int affectionCooldown;
    private double lastDistanceSq = Double.POSITIVE_INFINITY;
    private boolean forcedCatPose;
    private boolean forcedSit;
    private float closenessAffinity = 0.62f;
    private float displayedCloseness = 0.6f;
    private boolean participationHealGranted;
    private int reachabilityCooldown;

    public BedtimeCompanionGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.BEDTIME_COMPANION), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        LivingEntity owner = ctx.owner();
        if (!(owner instanceof net.minecraft.entity.player.PlayerEntity player)) {
            return false;
        }
        if (!player.isSleeping() || player.isSpectator() || !player.isAlive()) {
            return false;
        }
        if (ctx.distanceToOwner() >= 12.5f) {
            return false;
        }
        if (mob.squaredDistanceTo(player) > START_DISTANCE_SQ) {
            return false;
        }
        if (mob.isLeashed()) {
            return false;
        }

        this.style = determineStyle();
        this.closenessAffinity = calculateSnuggleAffinity(mob, ctx, player);
        this.displayedCloseness = closenessAffinity;
        SnugglePlan computed = resolvePlan(player, style);
        if (computed == null) {
            return false;
        }

        this.plan = computed;
        this.style = computed.style();
        this.ownerUuid = player.getUuid();
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        PetContext ctx = getContext();
        LivingEntity owner = ctx.owner();
        if (!(owner instanceof net.minecraft.entity.player.PlayerEntity player)) {
            return false;
        }
        if (!player.isSleeping() || !player.isAlive()) {
            return false;
        }
        if (ctx.distanceToOwner() > 16.0f) {
            return false;
        }
        if (plan == null) {
            return false;
        }
        if (lastDistanceSq > 16.0d && settleTicks <= 0 && mob.getNavigation().isIdle()) {
            return false;
        }
        return true;
    }

    @Override
    protected void onStartGoal() {
        settleTicks = 0;
        relationshipTicks = 0;
        ambienceCooldown = 0;
        planCooldown = 30 + mob.getRandom().nextInt(20);
        stuckTicks = 0;
        affectionCooldown = 0;
        lastDistanceSq = Double.POSITIVE_INFINITY;
        forcedCatPose = false;
        forcedSit = false;
        displayedCloseness = closenessAffinity;
        participationHealGranted = false;
        reachabilityCooldown = 0;
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        releaseForcedStates();
        releaseAffectionState();
        plan = null;
        ownerUuid = null;
        settleTicks = 0;
        relationshipTicks = 0;
        ambienceCooldown = 0;
        planCooldown = 0;
        stuckTicks = 0;
        affectionCooldown = 0;
        lastDistanceSq = Double.POSITIVE_INFINITY;
        participationHealGranted = false;
        reachabilityCooldown = 0;
    }

    @Override
    protected void onTickGoal() {
        LivingEntity owner = getSleepingOwner();
        if (!(owner instanceof net.minecraft.entity.player.PlayerEntity player)) {
            requestStop();
            return;
        }

        if (planCooldown > 0) {
            planCooldown--;
        }
        if (affectionCooldown > 0) {
            affectionCooldown--;
            if (affectionCooldown == 0) {
                releaseAffectionState();
            }
        }

        if ((mob.age & 7) == 0) {
            float sample = calculateSnuggleAffinity(mob, getContext(), player);
            this.closenessAffinity = MathHelper.clamp(
                this.closenessAffinity + (sample - this.closenessAffinity) * 0.35f,
                0.0f,
                1.0f
            );
        }

        SnugglePlan activePlan = this.plan;
        if (activePlan == null) {
            activePlan = resolvePlan(player, style);
            if (activePlan == null) {
                requestStop();
                return;
            }
            this.plan = activePlan;
            this.style = activePlan.style();
        }

        SnugglePlan validatedPlan = ensurePlanReachable(player, activePlan);
        if (validatedPlan == null) {
            requestStop();
            return;
        }
        if (validatedPlan != activePlan) {
            this.plan = validatedPlan;
            this.style = validatedPlan.style();
            activePlan = validatedPlan;
            settleTicks = 0;
            relationshipTicks = 0;
            stuckTicks = 0;
            lastDistanceSq = Double.POSITIVE_INFINITY;
            releaseAffectionState();
        } else {
            activePlan = validatedPlan;
        }

        Vec3d target = activePlan.target();
        if (target == null) {
            requestStop();
            return;
        }
        double previousDistanceSq = lastDistanceSq;
        double distanceSq = mob.squaredDistanceTo(target);
        lastDistanceSq = distanceSq;

        boolean navigationIdle = mob.getNavigation().isIdle();
        if (Double.isFinite(previousDistanceSq)) {
            if (distanceSq > previousDistanceSq - 0.1d) {
                stuckTicks = Math.min(stuckTicks + 1, 160);
            } else {
                stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
        }

        double arriveSq = switch (style) {
            case BED_TOP -> ARRIVE_DISTANCE_SQ_TOP;
            case PERCH_NEARBY -> ARRIVE_DISTANCE_SQ_PERCH;
            case BED_SIDE -> ARRIVE_DISTANCE_SQ_SIDE;
        };

        if (planCooldown <= 0 || (navigationIdle && distanceSq > arriveSq * 3.0d) || stuckTicks > 45) {
            SnugglePlan refreshed = resolvePlan(player, style);
            if (refreshed != null) {
                activePlan = refreshed;
                plan = refreshed;
                style = refreshed.style();
                target = activePlan.target();
                distanceSq = mob.squaredDistanceTo(target);
                lastDistanceSq = distanceSq;
                settleTicks = 0;
                relationshipTicks = 0;
                stuckTicks = 0;
                reachabilityCooldown = 10 + mob.getRandom().nextInt(10);
                releaseAffectionState();
            }
            planCooldown = 40 + mob.getRandom().nextInt(30);
        }

        float planCloseness = activePlan.closenessMatch();
        displayedCloseness = MathHelper.clamp(
            displayedCloseness + (planCloseness - displayedCloseness) * 0.25f,
            0.0f,
            1.0f
        );

        if (distanceSq > arriveSq) {
            settleTicks = 0;
            relationshipTicks = 0;
            releaseAffectionState();
            double baseSpeed = switch (style) {
                case BED_TOP -> 0.9d;
                case PERCH_NEARBY -> 0.65d;
                case BED_SIDE -> 0.8d;
            };
            double eagerness = MathHelper.clamp(planCloseness * 0.25d + closenessAffinity * 0.2d, 0.0d, 0.45d);
            double speed = baseSpeed + eagerness;
            if ((mob.age & 3) == 0 || navigationIdle) {
                mob.getNavigation().startMovingTo(target.x, target.y, target.z, speed);
            }
        } else {
            mob.getNavigation().stop();
            settleTicks++;
            relationshipTicks++;

            mob.getLookControl().lookAt(player, 18.0f, 18.0f);
            float closenessForEffects = MathHelper.clamp((displayedCloseness * 0.6f) + (planCloseness * 0.4f), 0.0f, 1.0f);
            applyStyleSettled(player, closenessForEffects, planCloseness);

            if (!participationHealGranted && mob.getEntityWorld() instanceof ServerWorld serverWorld) {
                grantParticipationHeal(serverWorld);
            }

            if (relationshipTicks >= RELATIONSHIP_PULSE_INTERVAL) {
                relationshipTicks = 0;
                rewardBond(player);
            }
        }
    }

    @Override
    protected float calculateEngagement() {
        if (plan == null) {
            return MathHelper.clamp(0.48f + closenessAffinity * 0.3f, 0.35f, 0.92f);
        }
        double threshold = switch (style) {
            case BED_TOP -> ARRIVE_DISTANCE_SQ_TOP;
            case PERCH_NEARBY -> ARRIVE_DISTANCE_SQ_PERCH;
            case BED_SIDE -> ARRIVE_DISTANCE_SQ_SIDE;
        };
        float closeness = MathHelper.clamp((plan.closenessMatch() * 0.5f) + (displayedCloseness * 0.5f), 0.0f, 1.0f);
        float eagerness = MathHelper.clamp(0.52f + closenessAffinity * 0.34f, 0.45f, 0.94f);
        float settledBonus = lastDistanceSq <= threshold ? eagerness + closeness * 0.32f : eagerness * 0.75f + closeness * 0.24f;
        if (settleTicks > 40) {
            settledBonus = Math.max(settledBonus, 0.9f + closeness * 0.1f);
        }
        return MathHelper.clamp(settledBonus, 0.42f, 0.995f);
    }

    @Override
    protected EmotionFeedback defineEmotionFeedback() {
        return new EmotionFeedback.Builder()
            .add(PetComponent.Emotion.CONTENT, 0.25f)
            .add(PetComponent.Emotion.SOBREMESA, 0.18f)
            .add(PetComponent.Emotion.LOYALTY, 0.15f)
            .withContagion(PetComponent.Emotion.CONTENT, 0.02f)
            .build();
    }

    private void applyStyleSettled(net.minecraft.entity.player.PlayerEntity owner, float closenessForEffects, float planCloseness) {
        World world = mob.getEntityWorld();
        if (world == null) {
            return;
        }

        switch (style) {
            case BED_TOP -> {
                if (mob instanceof CatEntity cat) {
                    if (planCloseness > 0.45f) {
                        if (!cat.isInSleepingPose()) {
                            cat.setInSleepingPose(true);
                        }
                        forcedCatPose = true;
                    } else if (forcedCatPose && cat.isInSleepingPose()) {
                        cat.setInSleepingPose(false);
                        forcedCatPose = false;
                    }
                }
                if (world instanceof ServerWorld serverWorld) {
                    maybePlayAmbient(serverWorld, SoundEvents.ENTITY_CAT_PURR, 0.4f, 0.95f, 0.08f, closenessForEffects);
                }
            }
            case BED_SIDE -> {
                boolean allowSit = planCloseness >= 0.32f;
                if (mob instanceof WolfEntity wolf) {
                    if (allowSit && !wolf.isInSittingPose()) {
                        wolf.setInSittingPose(true);
                        forcedSit = true;
                    }
                    if (planCloseness > 0.55f && wolf.isBegging() && affectionCooldown <= 0) {
                        wolf.setBegging(false);
                    }
                } else if (allowSit && mob instanceof TameableEntity tameable && !tameable.isInSittingPose()) {
                    tameable.setInSittingPose(true);
                    forcedSit = true;
                } else if (!allowSit && forcedSit) {
                    releaseForcedStates();
                }
                if (world instanceof ServerWorld serverWorld) {
                    maybePlayAmbient(serverWorld, null, 0.25f, 1.0f, 0.06f, closenessForEffects);
                }
            }
            case PERCH_NEARBY -> {
                if (planCloseness > 0.28f && mob instanceof ParrotEntity parrot) {
                    if (!parrot.isSitting()) {
                        parrot.setSitting(true);
                        forcedSit = true;
                    }
                } else if (mob instanceof ParrotEntity parrot && forcedSit && parrot.isSitting()) {
                    parrot.setSitting(false);
                    forcedSit = false;
                }
                if (world instanceof ServerWorld serverWorld) {
                    maybePlayAmbient(serverWorld, SoundEvents.ENTITY_PARROT_AMBIENT, 0.3f, 1.05f, 0.05f, closenessForEffects);
                }
            }
        }

        if (closenessForEffects < 0.3f) {
            releaseAffectionState();
            affectionCooldown = Math.min(affectionCooldown, 20);
        } else if (world instanceof ServerWorld serverWorld) {
            maybePerformAffection(serverWorld, owner, closenessForEffects);
        }

        // gentle sway towards owner for cuddly effect
        if (mob.isOnGround()) {
            Vec3d ownerPos = owner.getEntityPos();
            Vec3d petPos = mob.getEntityPos();
            double swayScale = 0.008d + (double) closenessForEffects * 0.014d;
            Vec3d towardOwner = ownerPos.subtract(petPos).multiply(swayScale);
            mob.addVelocity(towardOwner.x, 0.0d, towardOwner.z);
            mob.velocityModified = true;
        }
    }

    private void maybePlayAmbient(ServerWorld world, @Nullable SoundEvent sound, float volume, float basePitch, float particleChance, float closeness) {
        if (ambienceCooldown > 0) {
            ambienceCooldown--;
            return;
        }
        float closenessClamp = MathHelper.clamp(closeness, 0.0f, 1.0f);
        int baseCooldown = (int) MathHelper.lerp(closenessClamp, 90.0f, 38.0f);
        ambienceCooldown = baseCooldown + world.random.nextInt(35);
        if (sound != null) {
            float closenessVolume = volume * (0.75f + closenessClamp * 0.55f);
            float pitch = basePitch + world.random.nextFloat() * (0.08f + closenessClamp * 0.05f);
            world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundCategory.NEUTRAL, closenessVolume, pitch);
        }
        if (particleChance > 0f) {
            float adjustedChance = particleChance * (0.45f + closenessClamp * 1.1f);
            if (world.random.nextFloat() < adjustedChance) {
                world.spawnParticles(
                    ParticleTypes.HEART,
                    mob.getX(),
                    mob.getBodyY(0.6d),
                    mob.getZ(),
                    1,
                    0.12d,
                    0.02d,
                    0.12d,
                    0.005d
                );
            }
        }
    }

    private void maybePerformAffection(ServerWorld world, net.minecraft.entity.player.PlayerEntity owner, float closeness) {
        if (affectionCooldown > 0 || settleTicks < 20) {
            return;
        }
        float closenessClamp = MathHelper.clamp(closeness, 0.0f, 1.0f);
        float baseChance = 0.0025f + closenessClamp * 0.0075f;
        if (style == SnuggleStyle.BED_TOP) {
            baseChance += 0.0015f;
        } else if (style == SnuggleStyle.BED_SIDE) {
            baseChance += 0.0008f;
        }
        if (world.random.nextFloat() >= baseChance) {
            return;
        }

        affectionCooldown = 60 + world.random.nextInt(45);
        float volume = 0.25f + closenessClamp * 0.35f;
        float pitch = 0.95f + world.random.nextFloat() * 0.2f;

        if (mob instanceof CatEntity) {
            world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, volume, pitch);
        } else if (mob instanceof WolfEntity wolf) {
            wolf.setBegging(true);
        } else if (mob instanceof ParrotEntity) {
            world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_PARROT_AMBIENT, SoundCategory.NEUTRAL, volume * 0.75f, 1.1f + world.random.nextFloat() * 0.1f);
        }

        Vec3d mobPos = mob.getEntityPos().add(0.0d, mob.getHeight() * 0.35d, 0.0d);
        Vec3d ownerPos = owner.getEntityPos().add(0.0d, owner.getHeight() * 0.25d, 0.0d);
        Vec3d midpoint = mobPos.add(ownerPos.subtract(mobPos).multiply(0.35d));
        world.spawnParticles(
            ParticleTypes.HEART,
            midpoint.x,
            midpoint.y,
            midpoint.z,
            1,
            0.08d,
            0.04d,
            0.08d,
            0.01d
        );

        ambienceCooldown = Math.max(ambienceCooldown, 20);
    }

    private void rewardBond(net.minecraft.entity.player.PlayerEntity owner) {
        PetComponent component = PetComponent.get(mob);
        if (component == null || ownerUuid == null) {
            return;
        }
        float affectionScale = switch (style) {
            case BED_TOP -> 1.65f;
            case PERCH_NEARBY -> 1.35f;
            case BED_SIDE -> 1.5f;
        };
        float closenessBoost = 0.6f + MathHelper.clamp(displayedCloseness, 0.0f, 1.0f) * 0.4f;
        component.recordEntityInteraction(
            ownerUuid,
            InteractionType.PROXIMITY,
            1.0f,
            affectionScale * closenessBoost,
            0.6f + displayedCloseness * 0.25f
        );
    }

    @Nullable
    private LivingEntity getSleepingOwner() {
        PetComponent component = PetComponent.get(mob);
        if (component != null) {
            LivingEntity cached = component.getCachedOwnerEntity();
            if (cached != null && cached.isSleeping()) {
                return cached;
            }
        }
        return getContext().owner();
    }

    private SnugglePlan resolvePlan(net.minecraft.entity.player.PlayerEntity owner, SnuggleStyle preferred, SnuggleStyle... exclude) {
        SnuggleStyle[] order = switch (preferred) {
            case BED_TOP -> new SnuggleStyle[]{SnuggleStyle.BED_TOP, SnuggleStyle.BED_SIDE, SnuggleStyle.PERCH_NEARBY};
            case BED_SIDE -> new SnuggleStyle[]{SnuggleStyle.BED_SIDE, SnuggleStyle.BED_TOP, SnuggleStyle.PERCH_NEARBY};
            case PERCH_NEARBY -> new SnuggleStyle[]{SnuggleStyle.PERCH_NEARBY, SnuggleStyle.BED_SIDE, SnuggleStyle.BED_TOP};
        };
        for (SnuggleStyle candidate : order) {
            if (isExcluded(candidate, exclude)) {
                continue;
            }
            SnugglePlan plan = computePlanForStyle(owner, candidate);
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    private SnugglePlan ensurePlanReachable(net.minecraft.entity.player.PlayerEntity owner, SnugglePlan plan) {
        if (plan == null) {
            return null;
        }
        World world = mob.getEntityWorld();
        if (world == null) {
            return null;
        }

        Vec3d target = plan.target();
        if (target == null || !Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) {
            SnugglePlan fallback = resolvePlan(owner, plan.style());
            if (fallback != null && !fallback.equals(plan)) {
                return fallback;
            }
            return resolvePlan(owner, style, plan.style());
        }

        BlockPos targetPos = BlockPos.ofFloored(target);
        if (!world.isChunkLoaded(targetPos)) {
            SnugglePlan fallback = resolvePlan(owner, plan.style());
            if (fallback != null && !fallback.equals(plan)) {
                return fallback;
            }
            return resolvePlan(owner, style, plan.style());
        }

        if (reachabilityCooldown > 0) {
            reachabilityCooldown--;
            return plan;
        }

        double distanceSq = mob.squaredDistanceTo(target);
        if (distanceSq <= 1.75d) {
            reachabilityCooldown = 20;
            return plan;
        }

        Path path = mob.getNavigation().findPathTo(targetPos, 0);
        reachabilityCooldown = 34 + mob.getRandom().nextInt(20);
        if (path == null || !path.reachesTarget()) {
            SnugglePlan fallback = resolvePlan(owner, plan.style());
            if (fallback != null && !fallback.equals(plan)) {
                return fallback;
            }
            fallback = resolvePlan(owner, style, plan.style());
            if (fallback != null) {
                return fallback;
            }
        }
        return plan;
    }

    private static boolean isExcluded(SnuggleStyle candidate, SnuggleStyle... exclude) {
        if (exclude == null) {
            return false;
        }
        for (SnuggleStyle style : exclude) {
            if (style == candidate) {
                return true;
            }
        }
        return false;
    }

    private SnugglePlan computePlanForStyle(net.minecraft.entity.player.PlayerEntity owner, SnuggleStyle style) {
        World world = mob.getEntityWorld();
        if (world == null) {
            return null;
        }

        BlockPos headPos = resolveHeadPosition(world, owner);
        Direction facing = resolveFacing(world, headPos, owner);
        Vec3d target = selectTarget(world, owner, headPos, facing, style);
        if (target == null) {
            return null;
        }
        float closenessMatch = computeClosenessForTarget(owner, target);
        return new SnugglePlan(style, headPos, facing, target, closenessMatch);
    }

    private SnuggleStyle determineStyle() {
        PetComponent component = PetComponent.get(mob);
        boolean feline = mob instanceof CatEntity || matches(component, "cat", "feline", "lynx", "ocelot");
        if (feline) {
            return SnuggleStyle.BED_TOP;
        }
        boolean parrot = mob instanceof ParrotEntity || matches(component, "parrot", "macaw", "cockatiel");
        if (parrot) {
            return SnuggleStyle.PERCH_NEARBY;
        }
        boolean avian = matches(component, "bird", "avian", "winged") && mob.getWidth() <= 0.8f;
        if (avian) {
            return SnuggleStyle.PERCH_NEARBY;
        }
        return SnuggleStyle.BED_SIDE;
    }

    private boolean matches(@Nullable PetComponent component, String... keywords) {
        return component != null && component.matchesSpeciesKeyword(keywords);
    }

    private BlockPos resolveHeadPosition(World world, net.minecraft.entity.player.PlayerEntity owner) {
        BlockPos fallback = owner.getBlockPos();
        BlockPos pos = owner.getSleepingPosition().orElse(fallback);
        if (!world.isChunkLoaded(pos)) {
            return fallback;
        }
        var state = world.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock) {
            BedPart part = state.get(BedBlock.PART);
            Direction facing = state.get(BedBlock.FACING);
            if (part == BedPart.FOOT) {
                BlockPos head = pos.offset(facing);
                if (world.getBlockState(head).getBlock() instanceof BedBlock) {
                    return head;
                }
            }
        }
        return pos;
    }

    private Direction resolveFacing(World world, BlockPos headPos, net.minecraft.entity.player.PlayerEntity owner) {
        var state = world.getBlockState(headPos);
        if (state.getBlock() instanceof BedBlock) {
            return state.get(BedBlock.FACING);
        }
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            return state.get(Properties.HORIZONTAL_FACING);
        }
        return owner.getHorizontalFacing();
    }

    private Vec3d selectTarget(World world, net.minecraft.entity.player.PlayerEntity owner, BlockPos headPos, Direction facing, SnuggleStyle style) {
        double baseX = headPos.getX() + 0.5d;
        double baseY = headPos.getY();
        double baseZ = headPos.getZ() + 0.5d;
        List<Vec3d> candidates = new ArrayList<>();
        Vec3d ownerFocus = new Vec3d(owner.getX(), owner.getY(), owner.getZ()).add(0.0d, 0.3d, 0.0d);

        switch (style) {
            case BED_TOP -> {
                double y = baseY + 0.8d;
                Vec3d center = new Vec3d(baseX, y, baseZ);
                candidates.add(center.add(facing.getOpposite().getOffsetX() * 0.32d, 0.0d, facing.getOpposite().getOffsetZ() * 0.32d));
                candidates.add(center.add(facing.rotateYClockwise().getOffsetX() * 0.28d, 0.0d, facing.rotateYClockwise().getOffsetZ() * 0.28d));
                candidates.add(center);
                candidates.add(center.add(facing.rotateYCounterclockwise().getOffsetX() * 0.34d, 0.0d, facing.rotateYCounterclockwise().getOffsetZ() * 0.34d));
            }
            case BED_SIDE -> {
                Direction[] sides = new Direction[]{facing.rotateYClockwise(), facing.rotateYCounterclockwise()};
                double lateral = 0.95d + (mob.getWidth() * 0.4d);
                for (Direction side : sides) {
                    Vec3d raw = new Vec3d(
                        baseX + side.getOffsetX() * lateral,
                        baseY,
                        baseZ + side.getOffsetZ() * lateral
                    );
                    double groundY = sampleGroundY(world, raw, baseY);
                    candidates.add(new Vec3d(raw.x, groundY, raw.z));
                }
                Vec3d foot = new Vec3d(
                    baseX + facing.getOpposite().getOffsetX() * (0.8d + mob.getWidth() * 0.3d),
                    baseY,
                    baseZ + facing.getOpposite().getOffsetZ() * (0.8d + mob.getWidth() * 0.3d)
                );
                double groundY = sampleGroundY(world, foot, baseY);
                candidates.add(new Vec3d(foot.x, groundY, foot.z));
                double aloofDistance = 1.55d + mob.getWidth() * 0.25d;
                for (Direction side : sides) {
                    Vec3d aloof = new Vec3d(
                        baseX + side.getOffsetX() * aloofDistance + facing.getOpposite().getOffsetX() * 0.25d,
                        baseY,
                        baseZ + side.getOffsetZ() * aloofDistance + facing.getOpposite().getOffsetZ() * 0.25d
                    );
                    double aloofY = sampleGroundY(world, aloof, baseY);
                    candidates.add(new Vec3d(aloof.x, aloofY, aloof.z));
                }
            }
            case PERCH_NEARBY -> {
                double y = baseY + 1.25d;
                Direction side = facing.rotateYClockwise();
                candidates.add(new Vec3d(
                    baseX + side.getOffsetX() * 0.45d,
                    y,
                    baseZ + side.getOffsetZ() * 0.45d
                ));
                candidates.add(new Vec3d(
                    baseX + side.getOpposite().getOffsetX() * 0.45d,
                    y,
                    baseZ + side.getOpposite().getOffsetZ() * 0.45d
                ));
                candidates.add(new Vec3d(
                    baseX + facing.getOpposite().getOffsetX() * 0.35d,
                    y,
                    baseZ + facing.getOpposite().getOffsetZ() * 0.35d
                ));
                candidates.add(new Vec3d(
                    baseX + facing.getOpposite().getOffsetX() * 0.58d,
                    y - 0.1d,
                    baseZ + facing.getOpposite().getOffsetZ() * 0.58d
                ));
            }
        }

        Vec3d best = null;
        double bestScore = Double.MAX_VALUE;
        for (Vec3d candidate : candidates) {
            if (!isCandidateValid(world, candidate, style)) {
                continue;
            }
            double distance = candidate.distanceTo(ownerFocus);
            float closeness = MathHelper.clamp((float) (1.2d - distance * 0.55d), 0.0f, 1.0f);
            double closenessDelta = closeness - closenessAffinity;
            double closenessWeight = MathHelper.lerp(MathHelper.clamp(closenessAffinity, 0.0f, 1.0f), 0.55d, 1.3d);
            double closenessPenalty = closenessDelta * closenessDelta * closenessWeight;
            double distancePenalty = Math.max(0.0d, distance - 1.0d) * 0.08d;
            double aloofBias = (1.0d - closenessAffinity) * Math.max(0.0d, closenessAffinity - closeness) * 0.05d;
            double cuddleBias = closenessAffinity * Math.max(0.0d, closeness - closenessAffinity) * 0.05d;
            double jitter = (Math.abs(closenessDelta) + 0.03d) * mob.getRandom().nextDouble() * 0.06d;
            double score = closenessPenalty + distancePenalty + jitter - cuddleBias - aloofBias;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null) {
            return best;
        }
        return null;
    }

    private float computeClosenessForTarget(net.minecraft.entity.player.PlayerEntity owner, Vec3d target) {
        Vec3d ownerFocus = new Vec3d(owner.getX(), owner.getY(), owner.getZ()).add(0.0d, 0.3d, 0.0d);
        double distance = target.distanceTo(ownerFocus);
        return MathHelper.clamp((float) (1.2d - distance * 0.55d), 0.0f, 1.0f);
    }

    private double sampleGroundY(World world, Vec3d pos, double defaultY) {
        BlockPos base = BlockPos.ofFloored(pos.x, defaultY - 0.001d, pos.z);
        for (int i = 0; i < 3; i++) {
            BlockPos check = base.down(i);
            if (!world.getBlockState(check).isAir()) {
                return check.getY() + 1.0d;
            }
        }
        return defaultY;
    }

    private boolean isCandidateValid(World world, Vec3d candidate, SnuggleStyle style) {
        if (!Double.isFinite(candidate.x) || !Double.isFinite(candidate.y) || !Double.isFinite(candidate.z)) {
            return false;
        }
        if (!world.isChunkLoaded(BlockPos.ofFloored(candidate.x, candidate.y, candidate.z))) {
            return false;
        }
        double radius = switch (style) {
            case BED_TOP -> 0.35d;
            case PERCH_NEARBY -> 0.3d;
            case BED_SIDE -> Math.max(0.45d, mob.getWidth() * 0.6d);
        };
        Box box = new Box(
            candidate.x - radius,
            candidate.y - 0.4d,
            candidate.z - radius,
            candidate.x + radius,
            candidate.y + 0.6d,
            candidate.z + radius
        );
        return world.getOtherEntities(mob, box, entity -> entity instanceof MobEntity).isEmpty();
    }

    private void releaseForcedStates() {
        if (forcedCatPose && mob instanceof CatEntity cat) {
            cat.setInSleepingPose(false);
        }
        if (forcedSit) {
            if (mob instanceof ParrotEntity parrot) {
                parrot.setSitting(false);
            } else if (mob instanceof TameableEntity tameable) {
                tameable.setInSittingPose(false);
            }
        }
        forcedCatPose = false;
        forcedSit = false;
    }

    private void releaseAffectionState() {
        if (mob instanceof WolfEntity wolf) {
            wolf.setBegging(false);
        }
    }

    private void grantParticipationHeal(ServerWorld world) {
        if (participationHealGranted) {
            return;
        }

        PetComponent component = PetComponent.get(mob);
        if (component == null) {
            return;
        }

        UUID ownerId = component.getOwnerUuid();
        if (ownerId == null) {
            return;
        }

        int participantCount = countActiveParticipants(world, ownerId);
        int additionalPets = Math.max(0, participantCount - 1);
        float healPercent = MathHelper.clamp(0.5f + (additionalPets * 0.05f), 0.5f, 0.8f);
        float healAmount = mob.getMaxHealth() * healPercent;
        applySafeHeal(mob, healAmount);
        participationHealGranted = true;
    }

    private int countActiveParticipants(ServerWorld world, UUID ownerId) {
        StateManager manager = StateManager.forWorld(world);
        if (manager == null) {
            return 1;
        }

        PetSwarmIndex swarmIndex = manager.getSwarmIndex();
        List<PetSwarmIndex.SwarmEntry> entries = swarmIndex.snapshotOwner(ownerId);
        if (entries == null || entries.isEmpty()) {
            return 1;
        }

        int count = 0;
        for (PetSwarmIndex.SwarmEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            MobEntity participant = entry.pet();
            PetComponent participantComponent = entry.component();
            if (participant == null || participantComponent == null) {
                continue;
            }
            if (!participant.isAlive() || participant.isRemoved()) {
                continue;
            }
            if (!GoalIds.BEDTIME_COMPANION.equals(participantComponent.getActiveAdaptiveGoalId())) {
                continue;
            }
            count++;
        }
        return Math.max(count, 1);
    }

    private static void applySafeHeal(LivingEntity entity, float amount) {
        if (entity == null || amount <= 0f) {
            return;
        }
        float currentHealth = entity.getHealth();
        float maxHealth = entity.getMaxHealth();
        float missingHealth = Math.max(0f, maxHealth - currentHealth);
        if (missingHealth <= 0f) {
            return;
        }
        float healAmount = Math.min(amount, missingHealth);
        if (healAmount <= 0f) {
            return;
        }
        try {
            entity.heal(healAmount);
        } catch (Throwable t) {
            float newHealth = Math.min(maxHealth, currentHealth + healAmount);
            entity.setHealth(newHealth);
        }
    }

    private record SnugglePlan(SnuggleStyle style, BlockPos headPos, Direction facing, Vec3d target, float closenessMatch) {
    }

    private enum SnuggleStyle {
        BED_TOP,
        BED_SIDE,
        PERCH_NEARBY
    }

    public static float calculateSnuggleAffinity(MobEntity mob, PetContext ctx, net.minecraft.entity.player.PlayerEntity owner) {
        if (owner == null) {
            return 0.3f;
        }
        PetComponent component = ctx.component();
        float base = 0.68f;

        if (component != null) {
            float affection = MathHelper.clamp(component.getAffectionWith(owner.getUuid()), 0.0f, 1.0f);
            float trust = MathHelper.clamp((component.getTrustWith(owner.getUuid()) + 1.0f) * 0.5f, 0.0f, 1.0f);
            base += affection * 0.35f;
            base += trust * 0.28f;
        } else {
            base += 0.22f;
        }

        if (mob.isTouchingWaterOrRain()) {
            base -= 0.08f;
        }

        if (mob instanceof CatEntity) {
            base += 0.08f;
        } else if (mob instanceof WolfEntity) {
            base += 0.06f;
        } else if (mob instanceof ParrotEntity) {
            base += 0.04f;
        }

        float socialCharge = MathHelper.clamp(ctx.socialCharge(), 0.0f, 1.0f);
        base += (socialCharge - 0.5f) * 0.24f;

        float stamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        base += (0.5f - Math.abs(stamina - 0.55f)) * 0.06f;

        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);
        base += (0.55f - Math.abs(momentum - 0.45f)) * 0.12f;

        if (ctx.ownerNearby()) {
            base += 0.05f;
        }
        if (ctx.distanceToOwner() < 4.0f) {
            base += 0.03f;
        }
        PetContextCrowdSummary crowd = ctx.crowdSummary();
        if (crowd != null) {
            base += Math.min(crowd.friendlyCount(), 4) * 0.012f;
            if (crowd.hostileCount() > 0) {
                base -= 0.12f;
            }
        }

        if (ctx.dormant()) {
            base -= 0.12f;
        }

        if (ctx.hasMoodInBlend(PetComponent.Mood.BONDED, 0.22f) || ctx.hasMoodInBlend(PetComponent.Mood.CALM, 0.24f)) {
            base += 0.12f;
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.RESTLESS, 0.22f) || ctx.hasMoodInBlend(PetComponent.Mood.AFRAID, 0.18f)) {
            base -= 0.15f;
        }
        if (ctx.hasMoodInBlend(PetComponent.Mood.PROTECTIVE, 0.25f)) {
            base -= 0.05f;
        }

        if (ctx.hasEmotionAbove(PetComponent.Emotion.CONTENT, 0.28f)) {
            base += 0.1f;
        }
        if (ctx.hasEmotionAbove(PetComponent.Emotion.LOYALTY, 0.22f)) {
            base += 0.07f;
        }
        if (ctx.hasEmotionAbove(PetComponent.Emotion.WORRIED, 0.22f) || ctx.hasEmotionAbove(PetComponent.Emotion.STARTLE, 0.2f)) {
            base -= 0.14f;
        }

        long jitterSeed = mob.getUuid().getLeastSignificantBits() ^ owner.getUuid().getMostSignificantBits();
        float jitter = (float) ((Math.floorMod(jitterSeed, 997L) / 997.0d) * 0.18d - 0.09d);
        base += jitter;

        return MathHelper.clamp(base, 0.25f, 0.995f);
    }
}
