package woflo.petsplus.ai.goals.social;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.events.RelationshipEventHandler;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Curiosity and passion driven gift delivery. Pets now have to source a real item from the world
 * – either a loose item entity or a single piece from a nearby container – before presenting it to
 * their owner.
 */
public class GiftBringingGoal extends AdaptiveGoal {
    private static final String COOLDOWN_KEY = "gift_bringing";
    private static final double MAX_OWNER_DISTANCE = 12.0d;
    private static final double HARD_STOP_DISTANCE_SQ = (MAX_OWNER_DISTANCE * 1.65d) * (MAX_OWNER_DISTANCE * 1.65d);
    private static final double ITEM_SCAN_RADIUS = 7.5d;
    private static final double CONTAINER_SCAN_RADIUS = 6.0d;
    private static final int PRESENT_TICKS = 55;
    private static final int MAX_TOTAL_TICKS = 320;

    private enum Phase {
        SEEK_SOURCE,
        COLLECT_ITEM,
        APPROACH_OWNER,
        PRESENT,
        COMPLETE
    }

    private PlayerEntity targetPlayer;
    private GiftSource pendingSource;
    private GiftSource activeSource;
    private ItemStack giftStack = ItemStack.EMPTY;
    private float giftAffinity = 1.0f;
    private Phase phase = Phase.COMPLETE;
    private int phaseTicks = 0;
    private int totalTicks = 0;
    private boolean giftDelivered = false;
    private float neutralPitch = 0.0f;
    private boolean triggeredCurious;
    private boolean triggeredPassionate;

    public GiftBringingGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.GIFT_BRINGING), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        pendingSource = null;
        triggeredCurious = false;
        triggeredPassionate = false;

        if (owner == null || owner.isRemoved() || owner.isSleeping() || owner.isSpectator()) {
            return false;
        }

        if (!ctx.ownerNearby() || ctx.distanceToOwner() > MAX_OWNER_DISTANCE) {
            return false;
        }

        PetComponent component = PetComponent.get(mob);
        if (component == null || component.isOnCooldown(COOLDOWN_KEY)) {
            return false;
        }

        if (component.hasMoodAbove(PetComponent.Mood.ANGRY, 0.35f)
            || component.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.7f)) {
            return false;
        }

        boolean curious = component.hasMoodAbove(PetComponent.Mood.CURIOUS, 0.55f);
        boolean passionate = component.hasMoodAbove(PetComponent.Mood.PASSIONATE, 0.5f);
        if (!curious && !passionate) {
            return false;
        }

        if (ctx.behavioralMomentum() > 0.82f) {
            return false;
        }

        OwnerCombatState state = OwnerCombatState.get(owner);
        if (state != null) {
            long now = mob.getEntityWorld().getTime();
            if (state.isInCombat() || state.recentlyDamaged(now, 100) || state.isMounted()) {
                return false;
            }
        }

        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }

        GiftSource source = findGiftSource(serverWorld, owner);
        if (source == null) {
            return false;
        }

        triggeredCurious = curious;
        triggeredPassionate = passionate;
        pendingSource = source;
        targetPlayer = owner;
        return true;
    }

    @Override
    protected boolean shouldContinueGoal() {
        if (phase == Phase.COMPLETE || targetPlayer == null || targetPlayer.isRemoved()) {
            return false;
        }

        if (totalTicks >= MAX_TOTAL_TICKS) {
            return false;
        }

        if (phase == Phase.SEEK_SOURCE || phase == Phase.COLLECT_ITEM) {
            if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
                return false;
            }
            if (activeSource == null || !activeSource.isValid(serverWorld)) {
                return false;
            }
            return true;
        }

        return mob.squaredDistanceTo(targetPlayer) <= HARD_STOP_DISTANCE_SQ;
    }

    @Override
    protected void onStartGoal() {
        PetContext ctx = getContext();
        targetPlayer = ctx.owner();

        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld) || pendingSource == null || targetPlayer == null) {
            requestStop();
            return;
        }

        activeSource = pendingSource;
        pendingSource = null;

        if (!activeSource.isValid(serverWorld)) {
            requestStop();
            return;
        }

        float bond = MathHelper.clamp(ctx.bondStrength(), 0.0f, 1.0f);
        float affinity = 0.7f + bond * 0.3f;
        if (triggeredCurious) {
            affinity += 0.1f;
        }
        if (triggeredPassionate) {
            affinity += 0.2f;
        }
        giftAffinity = MathHelper.clamp(affinity, 0.45f, 1.35f);

        giftStack = ItemStack.EMPTY;
        giftDelivered = false;
        phase = Phase.SEEK_SOURCE;
        phaseTicks = 0;
        totalTicks = 0;
        neutralPitch = mob.getPitch();
    }

    @Override
    protected void onStopGoal() {
        if (mob.getNavigation() != null) {
            mob.getNavigation().stop();
        }
        mob.setPitch(neutralPitch);
        phase = Phase.COMPLETE;
        phaseTicks = 0;
        totalTicks = 0;
        boolean delivered = giftDelivered;
        giftDelivered = false;

        if (!giftStack.isEmpty() && mob.getEntityWorld() instanceof ServerWorld serverWorld) {
            ItemScatterer.spawn(serverWorld, mob.getX(), mob.getY(), mob.getZ(), giftStack);
        }

        giftStack = ItemStack.EMPTY;
        activeSource = null;
        pendingSource = null;
        targetPlayer = null;

        PetComponent component = PetComponent.get(mob);
        if (component != null) {
            int base = delivered ? secondsToTicks(240) : secondsToTicks(90);
            int variance = delivered ? secondsToTicks(160) : secondsToTicks(40);
            int cooldown = base + mob.getRandom().nextInt(Math.max(1, variance));
            component.setCooldown(COOLDOWN_KEY, cooldown);
        }

        triggeredCurious = false;
        triggeredPassionate = false;
    }

    @Override
    protected void onTickGoal() {
        if (phase == Phase.COMPLETE || targetPlayer == null) {
            requestStop();
            return;
        }

        totalTicks++;

        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
            requestStop();
            return;
        }

        if (targetPlayer != null && mob.squaredDistanceTo(targetPlayer) > HARD_STOP_DISTANCE_SQ) {
            requestStop();
            return;
        }

        switch (phase) {
            case SEEK_SOURCE -> tickSeekSource(serverWorld);
            case COLLECT_ITEM -> tickCollect(serverWorld);
            case APPROACH_OWNER -> tickApproachOwner();
            case PRESENT -> tickPresent(serverWorld);
            default -> requestStop();
        }
    }

    private void tickSeekSource(ServerWorld world) {
        phaseTicks++;
        if (activeSource == null || !activeSource.isValid(world)) {
            requestStop();
            return;
        }

        Vec3d focus = activeSource.position(world);
        mob.getLookControl().lookAt(focus.x, focus.y, focus.z, 20.0f, 20.0f);

        if (mob.squaredDistanceTo(focus) > 4.0d) {
            mob.getNavigation().startMovingTo(focus.x, focus.y, focus.z, 1.05);
        } else {
            mob.getNavigation().stop();
            transitionTo(Phase.COLLECT_ITEM);
        }

        if (phaseTicks > 120) {
            transitionTo(Phase.COLLECT_ITEM);
        }
    }

    private void tickCollect(ServerWorld world) {
        phaseTicks++;
        if (activeSource == null || !activeSource.isValid(world)) {
            requestStop();
            return;
        }

        Vec3d focus = activeSource.position(world);
        mob.getLookControl().lookAt(focus.x, focus.y, focus.z, 25.0f, 25.0f);

        double distanceSq = mob.squaredDistanceTo(focus);
        if (distanceSq > 2.25d) {
            if (mob.getNavigation().isIdle()) {
                mob.getNavigation().startMovingTo(focus.x, focus.y, focus.z, 0.9);
            }
            return;
        }

        ItemStack collected = collectFromSource(world, activeSource);
        if (collected.isEmpty()) {
            requestStop();
            return;
        }

        giftStack = collected;
        mob.getNavigation().stop();
        transitionTo(Phase.APPROACH_OWNER);
    }

    private void tickApproachOwner() {
        phaseTicks++;
        if (targetPlayer == null) {
            requestStop();
            return;
        }

        mob.getNavigation().startMovingTo(targetPlayer, 0.95);
        mob.getLookControl().lookAt(targetPlayer, 20.0f, 20.0f);

        if (mob.squaredDistanceTo(targetPlayer) <= 4.0d) {
            mob.getNavigation().stop();
            transitionTo(Phase.PRESENT);
        }
    }

    private void tickPresent(ServerWorld world) {
        phaseTicks++;
        if (targetPlayer == null) {
            requestStop();
            return;
        }

        mob.getLookControl().lookAt(targetPlayer, 28.0f, 28.0f);
        float bob = MathHelper.sin(phaseTicks * 0.45f) * 12.0f;
        mob.setPitch(MathHelper.clamp(bob - 6.0f, -22.0f, 22.0f));

        if (!giftDelivered && phaseTicks >= 4) {
            deliverGift(world);
        }

        if (phaseTicks >= PRESENT_TICKS) {
            requestStop();
        }
    }

    private void deliverGift(ServerWorld world) {
        if (giftDelivered || giftStack.isEmpty() || targetPlayer == null) {
            return;
        }

        ItemStack preparedGift = giftStack;
        giftStack = ItemStack.EMPTY;

        ItemStack handoffStack = preparedGift.copy();
        ItemStack messageStack = preparedGift.copy();

        boolean accepted = targetPlayer.giveItemStack(handoffStack);
        boolean dropped = false;
        if (!accepted && !handoffStack.isEmpty()) {
            var droppedEntity = targetPlayer.dropItem(handoffStack, false, true);
            if (droppedEntity != null) {
                droppedEntity.setPickupDelay(0);
                droppedEntity.setOwner(targetPlayer.getUuid());
                dropped = true;
            } else if (!handoffStack.isEmpty()) {
                ItemScatterer.spawn(world, targetPlayer.getX(), targetPlayer.getY() + 0.2d, targetPlayer.getZ(), handoffStack);
                dropped = true;
            }
        }

        if (accepted || dropped) {
            giftDelivered = true;
            RelationshipEventHandler.onGiftGiven(mob, targetPlayer, MathHelper.clamp(giftAffinity, 0.35f, 1.4f));
            sendGiftQuip(messageStack);
        } else {
            giftStack = preparedGift;
        }
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(PetComponent.Emotion.CURIOUS, 0.26f)
            .add(PetComponent.Emotion.PRIDE, 0.19f)
            .add(PetComponent.Emotion.HOPEFUL, 0.12f)
            .withContagion(PetComponent.Emotion.CURIOUS, 0.02f)
            .build();
    }

    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float bond = MathHelper.clamp(ctx.bondStrength(), 0.0f, 1.0f);
        float base = 0.54f + bond * 0.28f;

        if (triggeredCurious) {
            base += 0.06f;
        }
        if (triggeredPassionate) {
            base += 0.09f;
        }

        if (phase == Phase.APPROACH_OWNER) {
            base += 0.08f;
        } else if (phase == Phase.PRESENT) {
            base = Math.max(base, 0.95f);
        }

        if (targetPlayer != null) {
            double distance = Math.sqrt(Math.max(0.0d, mob.squaredDistanceTo(targetPlayer)));
            base += MathHelper.clamp((float) ((MAX_OWNER_DISTANCE - distance) / MAX_OWNER_DISTANCE) * 0.1f, 0.0f, 0.1f);
        }

        return MathHelper.clamp(base, 0.0f, 1.0f);
    }

    private void transitionTo(Phase next) {
        phase = next;
        phaseTicks = 0;
        if (phase == Phase.PRESENT) {
            neutralPitch = mob.getPitch();
        }
    }

    private void sendGiftQuip(ItemStack gift) {
        if (!(targetPlayer instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        MutableText prefix = Text.literal("[Curio Gesture] ").formatted(Formatting.LIGHT_PURPLE);
        String petName = mob.getDisplayName().getString();
        String ownerName = serverPlayer.getDisplayName().getString();
        String itemName = gift.getName().getString();

        String line;
        if (triggeredPassionate && triggeredCurious) {
            line = petName + " practically buzzes, presenting " + itemName + " it *had* to share with " + ownerName + ".";
        } else if (triggeredPassionate) {
            line = petName + " presses " + itemName + " into " + ownerName + "'s hands with radiant pride.";
        } else {
            line = petName + " trots up, eyes wide with discovery, offering " + itemName + " to " + ownerName + ".";
        }

        MutableText body = Text.literal(line).formatted(Formatting.LIGHT_PURPLE);
        if (giftDelivered && triggeredCurious && !triggeredPassionate) {
            body.append(Text.literal(" (They couldn't rest until you saw this.)").formatted(Formatting.AQUA));
        } else if (giftDelivered && triggeredPassionate) {
            body.append(Text.literal(" (All that affection needed an outlet.)").formatted(Formatting.GOLD));
        }

        serverPlayer.sendMessage(prefix.append(body), false);
    }

    private GiftSource findGiftSource(ServerWorld world, PlayerEntity owner) {
        GiftSource itemSource = findLooseItem(world);
        GiftSource containerSource = owner == null ? null : findContainerItem(world, owner);

        if (itemSource == null) {
            return containerSource;
        }
        if (containerSource == null) {
            return itemSource;
        }

        double itemDistance = mob.squaredDistanceTo(itemSource.position(world));
        double containerDistance = mob.squaredDistanceTo(containerSource.position(world));
        return itemDistance <= containerDistance ? itemSource : containerSource;
    }

    private GiftSource findLooseItem(ServerWorld world) {
        List<ItemEntity> items = world.getEntitiesByClass(
            ItemEntity.class,
            mob.getBoundingBox().expand(ITEM_SCAN_RADIUS),
            entity -> entity != null && entity.isAlive() && !entity.getStack().isEmpty() && !entity.cannotPickup()
        );

        if (items.isEmpty()) {
            return null;
        }

        items.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mob)));
        return new ItemEntitySource(items.get(0));
    }

    private GiftSource findContainerItem(ServerWorld world, PlayerEntity owner) {
        BlockPos origin = mob.getBlockPos();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        double bestDistance = Double.MAX_VALUE;
        ContainerSlotSource bestSource = null;
        int radius = (int) Math.ceil(CONTAINER_SCAN_RADIUS);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    double distanceSq = mutable.getSquaredDistance(mob.getX(), mob.getY(), mob.getZ());
                    if (distanceSq > CONTAINER_SCAN_RADIUS * CONTAINER_SCAN_RADIUS) {
                        continue;
                    }

                    BlockEntity be = world.getBlockEntity(mutable);
                    if (!(be instanceof Inventory inventory) || inventory.isEmpty()) {
                        continue;
                    }

                    if (!inventory.canPlayerUse(owner)) {
                        continue;
                    }

                    if (be instanceof LockableContainerBlockEntity lockable && lockable.isLocked()) {
                        continue;
                    }

                    for (int slot = 0; slot < inventory.size(); slot++) {
                        ItemStack stack = inventory.getStack(slot);
                        if (stack.isEmpty()) {
                            continue;
                        }
                        if (distanceSq < bestDistance) {
                            bestDistance = distanceSq;
                            bestSource = new ContainerSlotSource(mutable.toImmutable(), slot);
                        }
                        break;
                    }
                }
            }
        }

        return bestSource;
    }

    private ItemStack collectFromSource(ServerWorld world, GiftSource source) {
        if (source instanceof ItemEntitySource itemSource) {
            ItemEntity entity = itemSource.entity();
            if (entity == null || !entity.isAlive() || entity.getStack().isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = entity.getStack().copy();
            entity.discard();
            return stack;
        }

        if (source instanceof ContainerSlotSource containerSource) {
            BlockEntity be = world.getBlockEntity(containerSource.pos());
            if (!(be instanceof Inventory inventory)) {
                return ItemStack.EMPTY;
            }
            if (containerSource.slot() < 0 || containerSource.slot() >= inventory.size()) {
                return ItemStack.EMPTY;
            }

            if (targetPlayer != null && !inventory.canPlayerUse(targetPlayer)) {
                return ItemStack.EMPTY;
            }

            if (be instanceof LockableContainerBlockEntity lockable && lockable.isLocked()) {
                return ItemStack.EMPTY;
            }

            ItemStack stack = inventory.getStack(containerSource.slot());
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack taken = stack.split(1);
            if (stack.isEmpty()) {
                inventory.setStack(containerSource.slot(), ItemStack.EMPTY);
            }
            inventory.markDirty();
            be.markDirty();
            world.updateListeners(containerSource.pos(), world.getBlockState(containerSource.pos()), world.getBlockState(containerSource.pos()), 3);
            return taken;
        }

        return ItemStack.EMPTY;
    }

    private static int secondsToTicks(int seconds) {
        return Math.max(0, seconds * 20);
    }

    private interface GiftSource {
        Vec3d position(ServerWorld world);

        boolean isValid(ServerWorld world);
    }

    private record ItemEntitySource(ItemEntity entity) implements GiftSource {
        @Override
        public Vec3d position(ServerWorld world) {
            return entity != null ? new Vec3d(entity.getX(), entity.getY(), entity.getZ()) : Vec3d.ZERO;
        }

        @Override
        public boolean isValid(ServerWorld world) {
            return entity != null && entity.isAlive() && !entity.getStack().isEmpty();
        }
    }

    private record ContainerSlotSource(BlockPos pos, int slot) implements GiftSource {
        @Override
        public Vec3d position(ServerWorld world) {
            return Vec3d.ofCenter(pos);
        }

        @Override
        public boolean isValid(ServerWorld world) {
            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof Inventory inventory)) {
                return false;
            }
            if (slot < 0 || slot >= inventory.size()) {
                return false;
            }
            if (be instanceof LockableContainerBlockEntity lockable && lockable.isLocked()) {
                return false;
            }
            return !inventory.getStack(slot).isEmpty();
        }
    }
}
