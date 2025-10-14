package woflo.petsplus.ai.goals.wander;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;

/**
 * Casual meandering wander - slow, curved paths with pauses.
 */
public class CasualWanderGoal extends AdaptiveGoal {
    private static final int MAX_DURATION_TICKS = 200;
    private static final int RECENT_TARGET_HISTORY = 6;
    private static final int ENTITY_INTEREST_RADIUS_SQ = 64;
    private static final String COOLDOWN_KEY = "casual_wander";

    private BlockPos target;
    private int pauseTicks = 0;
    private boolean isPaused = false;
    private int pauseDurationTicks = 0;
    private int ticksActive = 0;
    private int curiosityCooldown = 0;
    private int navigationStallTicks = 0;
    private final Deque<BlockPos> recentTargets = new ArrayDeque<>(RECENT_TARGET_HISTORY);
    
    public CasualWanderGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.CASUAL_WANDER), EnumSet.of(Control.MOVE));
    }
    
    @Override
    protected boolean canStartGoal() {
        if (!mob.isOnGround() || mob.isSubmergedInWater()) {
            return false;
        }
        if (!mob.getNavigation().isIdle()) {
            return false;
        }

        PetComponent component = PetComponent.get(mob);
        if (component != null) {
            if (component.isOnCooldown(COOLDOWN_KEY)) {
                return false;
            }
            if (component.hasMoodAbove(PetComponent.Mood.ANGRY, 0.5f)) {
                return false;
            }
        }

        PetContext ctx = getContext();
        return ctx != null && !ctx.dormant();
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        if (ticksActive >= MAX_DURATION_TICKS) {
            return false;
        }
        if (!mob.isAlive() || mob.isRemoved()) {
            return false;
        }
        if (mob.isSubmergedInWater()) {
            return false;
        }
        PetComponent component = PetComponent.get(mob);
        if (component != null && component.hasMoodAbove(PetComponent.Mood.ANGRY, 0.55f)) {
            return false;
        }
        return true;
    }
    
    @Override
    protected void onStartGoal() {
        ticksActive = 0;
        navigationStallTicks = 0;
        curiosityCooldown = 0;
        isPaused = false;
        pauseTicks = 0;
        selectNewTarget(getContext(), true);
    }
    
    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        target = null;
        isPaused = false;
        PetComponent component = PetComponent.get(mob);
        if (component != null) {
            int cooldown = 80 + mob.getRandom().nextInt(41);
            component.setCooldown(COOLDOWN_KEY, cooldown);
        }
    }
    
    @Override
    protected void onTickGoal() {
        ticksActive++;
        PetContext ctx = getContext();

        if (isPaused) {
            handlePause(ctx);
            return;
        }

        curiosityCooldown = Math.max(0, curiosityCooldown - 1);

        if (target == null || curiosityCooldown == 0) {
            selectNewTarget(ctx, false);
        }

        if (target == null) {
            enterPause(30 + mob.getRandom().nextInt(30));
            return;
        }

        double distanceSq = target.getSquaredDistance(mob.getX(), mob.getY(), mob.getZ());
        if (distanceSq < 1.6d) {
            if (mob.getRandom().nextFloat() < 0.55f) {
                enterPause(25 + mob.getRandom().nextInt(25));
            } else {
                selectNewTarget(ctx, false);
            }
            return;
        }

        double speed = computeTravelSpeed(ctx, distanceSq);
        mob.getNavigation().startMovingTo(
            target.getX() + 0.5,
            target.getY(),
            target.getZ() + 0.5,
            speed
        );
        mob.getLookControl().lookAt(
            target.getX() + 0.5,
            target.getY() + 0.3,
            target.getZ() + 0.5,
            20.0f,
            20.0f
        );

        if (mob.getNavigation().isIdle()) {
            navigationStallTicks++;
            if (navigationStallTicks > 45) {
                selectNewTarget(ctx, true);
                navigationStallTicks = 0;
            }
        } else {
            navigationStallTicks = 0;
        }

        if (ticksActive % 60 == 0 && ctx != null && ctx.owner() != null) {
            mob.getLookControl().lookAt(ctx.owner(), 15.0f, 15.0f);
        }
    }

    private void handlePause(PetContext ctx) {
        mob.getNavigation().stop();
        pauseTicks++;
        if (ctx != null && ctx.owner() != null && pauseTicks % 24 == 0) {
            mob.getLookControl().lookAt(ctx.owner(), 12.0f, 12.0f);
        } else if (target != null && pauseTicks % 18 == 0) {
            mob.getLookControl().lookAt(
                target.getX() + 0.5,
                target.getY() + 0.3,
                target.getZ() + 0.5,
                18.0f,
                18.0f
            );
        }
        if (pauseDurationTicks > 0 && pauseTicks >= pauseDurationTicks) {
            isPaused = false;
            pauseTicks = 0;
            pauseDurationTicks = 0;
            selectNewTarget(ctx, false);
        }
    }

    private void enterPause(int durationTicks) {
        isPaused = true;
        pauseTicks = 0;
        pauseDurationTicks = Math.max(15, durationTicks);
        curiosityCooldown = Math.max(curiosityCooldown, 15);
        mob.getNavigation().stop();
    }

    private double computeTravelSpeed(PetContext ctx, double distanceSq) {
        double distance = Math.sqrt(Math.max(distanceSq, 0.0d));
        double distanceFactor = MathHelper.clamp((distance - 2.0d) / 6.0d, 0.0d, 1.0d);
        double stamina = ctx != null ? MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f) : 0.55d;
        double momentum = ctx != null ? MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f) : 0.55d;
        double curiosityBoost = (ctx != null && ctx.hasPetsPlusComponent()
            && ctx.hasMoodInBlend(PetComponent.Mood.CURIOUS, 0.35f)) ? 0.12d : 0.0d;
        double base = 0.58d + distanceFactor * 0.30d + stamina * 0.20d + momentum * 0.15d + curiosityBoost;
        return MathHelper.clamp(base, 0.45d, 1.05d);
    }

    private void selectNewTarget(PetContext ctx, boolean allowPauseFallback) {
        BlockPos chosen = null;
        if (ctx != null) {
            chosen = pickEntityInterest(ctx).orElse(null);
            if (chosen == null) {
                chosen = pickEnvironmentalInterest(ctx).orElse(null);
            }
        }
        if (chosen == null) {
            chosen = pickExplorationTarget(ctx);
        }

        if (chosen != null) {
            target = chosen.toImmutable();
            rememberTarget(target);
            curiosityCooldown = 50 + mob.getRandom().nextInt(40);
            return;
        }

        target = null;
        if (allowPauseFallback) {
            enterPause(25 + mob.getRandom().nextInt(25));
        }
    }

    private Optional<BlockPos> pickEntityInterest(PetContext ctx) {
        List<Entity> entities = ctx.nearbyEntities();
        if (entities == null || entities.isEmpty()) {
            return Optional.empty();
        }
        Entity owner = ctx.owner();
        double bestScore = 0.0d;
        BlockPos best = null;
        for (Entity entity : entities) {
            if (entity == null || entity == mob || !entity.isAlive() || entity.isRemoved()) {
                continue;
            }
            if (entity == owner || entity instanceof HostileEntity) {
                continue;
            }
            double distanceSq = entity.squaredDistanceTo(mob);
            if (distanceSq < 4.0d || distanceSq > ENTITY_INTEREST_RADIUS_SQ) {
                continue;
            }
            double baseScore;
            if (entity instanceof PassiveEntity) {
                baseScore = 1.0d;
            } else if (entity instanceof ItemEntity) {
                baseScore = 0.65d;
            } else {
                baseScore = 0.45d;
            }
            double curiosity = baseScore / Math.sqrt(distanceSq);
            BlockPos stand = findStandableGround(entity.getBlockPos());
            if (stand == null || isRecentlyVisited(stand)) {
                continue;
            }
            if (curiosity > bestScore) {
                bestScore = curiosity;
                best = stand;
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<BlockPos> pickEnvironmentalInterest(PetContext ctx) {
        World world = mob.getEntityWorld();
        BlockPos origin = mob.getBlockPos();
        double bestScore = 0.0d;
        BlockPos best = null;
        int radius = 7;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos stand = findStandableGround(origin.add(dx, 0, dz));
                if (stand == null || isRecentlyVisited(stand)) {
                    continue;
                }
                double score = scoreBlock(world, stand.down());
                if (score <= 0.0d) {
                    continue;
                }
                double distance = Math.sqrt(stand.getSquaredDistance(origin.getX(), origin.getY(), origin.getZ()));
                if (distance < 2.5d || distance > 9.5d) {
                    continue;
                }
                double weighted = score / (0.9d + distance);
                if (weighted > bestScore) {
                    bestScore = weighted;
                    best = stand;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private BlockPos pickExplorationTarget(PetContext ctx) {
        BlockPos origin = mob.getBlockPos();
        for (int attempt = 0; attempt < 8; attempt++) {
            int radius = 4 + mob.getRandom().nextInt(5);
            double angle = mob.getRandom().nextDouble() * MathHelper.TAU;
            int dx = MathHelper.floor(Math.cos(angle) * radius);
            int dz = MathHelper.floor(Math.sin(angle) * radius);
            BlockPos stand = findStandableGround(origin.add(dx, 0, dz));
            if (stand == null || isRecentlyVisited(stand)) {
                continue;
            }
            if (!mob.getEntityWorld().getWorldBorder().contains(stand)) {
                continue;
            }
            return stand;
        }
        return null;
    }

    private double scoreBlock(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return 0.0d;
        }
        double score = 0.0d;
        if (state.isIn(BlockTags.FLOWERS)) {
            score = 1.0d;
        } else if (state.isIn(BlockTags.LEAVES)) {
            score = 0.55d;
        } else if (state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)) {
            score = 0.85d;
        } else if (state.isOf(Blocks.MOSS_BLOCK) || state.isOf(Blocks.HAY_BLOCK)) {
            score = 0.6d;
        }
        if (world.getFluidState(pos).isIn(FluidTags.WATER)) {
            score = Math.max(score, 1.1d);
        }
        return score;
    }

    private BlockPos findStandableGround(BlockPos initial) {
        World world = mob.getEntityWorld();
        BlockPos.Mutable mutable = initial.mutableCopy();
        int minY = world.getBottomY();
        int maxY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, initial.getX(), initial.getZ());

        for (int up = 0; up < 6 && mutable.getY() < maxY; up++) {
            BlockPos head = mutable.up();
            BlockState headState = world.getBlockState(head);
            if ((headState.isAir() || headState.getCollisionShape(world, head).isEmpty())
                && world.getBlockState(mutable).isSolidBlock(world, mutable)) {
                return head.toImmutable();
            }
            mutable.move(0, 1, 0);
        }

        mutable = initial.mutableCopy();
        for (int down = 0; down < 6 && mutable.getY() > minY; down++) {
            BlockPos head = mutable.up();
            BlockState headState = world.getBlockState(head);
            if ((headState.isAir() || headState.getCollisionShape(world, head).isEmpty())
                && world.getBlockState(mutable).isSolidBlock(world, mutable)) {
                return head.toImmutable();
            }
            mutable.move(0, -1, 0);
        }
        return null;
    }

    private void rememberTarget(BlockPos pos) {
        if (pos == null) {
            return;
        }
        recentTargets.addLast(pos);
        while (recentTargets.size() > RECENT_TARGET_HISTORY) {
            recentTargets.removeFirst();
        }
    }

    private boolean isRecentlyVisited(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        for (BlockPos recent : recentTargets) {
            if (recent != null && recent.getSquaredDistance(pos) <= 4.0d) {
                return true;
            }
        }
        return false;
    }

    
    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        PetContext ctx = getContext();
        
        // Base exploration emotions - gentle contentment and ambient appreciation
        var builder = new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.CONTENT, 0.12f)    // Pleasant routine
            .add(woflo.petsplus.state.PetComponent.Emotion.YUGEN, 0.08f);     // Subtle wonder
        
        // Context-aware modulation based on distance to owner
        if (ctx.distanceToOwner() > 20.0) {
            // Far from owner: territorial species feel longing
            builder.add(woflo.petsplus.state.PetComponent.Emotion.HIRAETH, 0.10f);
        } else if (ctx.ownerNearby()) {
            // Near owner: comfortable proximity
            builder.add(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.10f);
            builder.add(woflo.petsplus.state.PetComponent.Emotion.LAGOM, 0.05f);
        }
        
        return builder.build();
    }
    
    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float engagement = 0.5f;

        float physicalStamina = MathHelper.clamp(ctx.physicalStamina(), 0.0f, 1.0f);
        float momentum = MathHelper.clamp(ctx.behavioralMomentum(), 0.0f, 1.0f);

        float staminaBlend = MathHelper.clamp((physicalStamina - 0.45f) / 0.35f, -1.0f, 1.0f);
        float staminaScale = MathHelper.lerp((staminaBlend + 1.0f) * 0.5f, 0.6f, 1.05f);
        engagement *= staminaScale;

        float momentumBlend = MathHelper.clamp((momentum - 0.45f) / 0.35f, -1.0f, 1.0f);
        float momentumScale = MathHelper.lerp((momentumBlend + 1.0f) * 0.5f, 0.7f, 1.08f);
        engagement *= momentumScale;

        // More engaging if calm
        if (ctx.hasPetsPlusComponent() && ctx.hasMoodInBlend(
            woflo.petsplus.state.PetComponent.Mood.CALM, 0.4f)) {
            engagement += 0.2f;
        }

        return MathHelper.clamp(engagement, 0.0f, 1.0f);
    }
}
