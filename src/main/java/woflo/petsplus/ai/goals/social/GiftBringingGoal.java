package woflo.petsplus.ai.goals.social;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.events.RelationshipEventHandler;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Gift bringing - purposeful care gesture that reacts to owner state instead of scavenging.
 */
public class GiftBringingGoal extends AdaptiveGoal {
    private static final String COOLDOWN_KEY = "gift_bringing";
    private static final double MAX_OWNER_DISTANCE = 12.0d;
    private static final double MAX_OWNER_DISTANCE_SQ = MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE;
    private static final double HARD_STOP_DISTANCE_SQ = (MAX_OWNER_DISTANCE * 1.65d) * (MAX_OWNER_DISTANCE * 1.65d);
    private static final int PREPARE_TICKS = 35;
    private static final int PRESENT_TICKS = 55;
    private static final int MAX_TOTAL_TICKS = 220;

    private enum Phase { APPROACH, PREPARE, PRESENT, COMPLETE }

    private static final List<GiftOption> TOKEN_GIFTS = List.of(
        GiftOption.simple(Items.POPPY, 1.0f),
        GiftOption.simple(Items.BLUE_ORCHID, 0.9f),
        GiftOption.simple(Items.FEATHER, 0.85f),
        GiftOption.simple(Items.PRISMARINE_SHARD, 0.5f)
    );

    private static final List<GiftOption> COMFORT_GIFTS = List.of(
        GiftOption.simple(Items.COOKED_SALMON, 1.1f),
        GiftOption.simple(Items.COOKED_CHICKEN, 1.0f),
        GiftOption.simple(Items.BREAD, 0.9f),
        GiftOption.simple(Items.SWEET_BERRIES, 0.75f)
    );

    private static final List<GiftOption> CARE_GIFTS = List.of(
        GiftOption.simple(Items.HONEY_BOTTLE, 1.0f),
        GiftOption.simple(Items.GLOW_BERRIES, 0.85f),
        GiftOption.simple(Items.GOLDEN_APPLE, 0.35f),
        GiftOption.simple(Items.MILK_BUCKET, 0.45f)
    );

    private static final List<GiftOption> DRY_GIFTS = List.of(
        GiftOption.simple(Items.SPONGE, 1.0f),
        GiftOption.simple(Items.WHITE_WOOL, 0.85f),
        GiftOption.simple(Items.CAMPFIRE, 0.6f),
        GiftOption.simple(Items.TORCH, 0.9f)
    );

    private static final List<GiftOption> TRUSTED_GIFTS = List.of(
        GiftOption.simple(Items.GOLD_NUGGET, 1.0f),
        GiftOption.simple(Items.LAPIS_LAZULI, 0.85f),
        GiftOption.dynamic((pet, owner, random, context) -> {
            ItemStack keepsake = new ItemStack(Items.AMETHYST_SHARD);
            MutableText name = Text.literal("Keepsake Prism").formatted(Formatting.LIGHT_PURPLE);
            MutableText petLine = Text.literal("Bond stored by ").formatted(Formatting.GRAY)
                .append(pet.getDisplayName().copy().formatted(Formatting.LIGHT_PURPLE));
            MutableText ownerLine = Text.literal("Presented to ").formatted(Formatting.GRAY)
                .append(owner != null
                    ? owner.getDisplayName().copy().formatted(Formatting.AQUA)
                    : Text.literal("the bravest nearby human").formatted(Formatting.AQUA));
            keepsake.set(DataComponentTypes.CUSTOM_NAME, name);
            keepsake.set(DataComponentTypes.LORE, new LoreComponent(List.of(petLine, ownerLine)));
            return keepsake;
        }, 0.55f),
        GiftOption.simple(Items.EMERALD, 0.5f)
    );

    private static final List<GiftOption> RARE_GIFTS = List.of(
        GiftOption.simple(Items.NAUTILUS_SHELL, 0.2f),
        GiftOption.simple(Items.ENDER_PEARL, 0.25f),
        GiftOption.simple(Items.DIAMOND, 0.15f)
    );

    private static final List<GiftOption> CELEBRATION_GIFTS = List.of(
        GiftOption.simple(Items.GLOW_BERRIES, 0.9f),
        GiftOption.simple(Items.AMETHYST_SHARD, 0.8f),
        GiftOption.simple(Items.FIRE_CHARGE, 0.45f),
        GiftOption.dynamic((pet, owner, random, context) -> {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Victory Sparkler").formatted(Formatting.GOLD));
            MutableText loreLine = Text.literal("Launch by ").formatted(Formatting.GRAY)
                .append(owner != null
                    ? owner.getDisplayName().copy().formatted(Formatting.AQUA)
                    : Text.literal("any triumphant bystander").formatted(Formatting.AQUA))
                .append(Text.literal(" — courtesy of ").formatted(Formatting.GRAY))
                .append(pet.getDisplayName().copy().formatted(Formatting.LIGHT_PURPLE));
            rocket.set(DataComponentTypes.LORE, new LoreComponent(List.of(loreLine)));
            return rocket;
        }, 0.75f)
    );

    private static final List<GiftOption> CHAOTIC_GIFTS = List.of(
        GiftOption.simple(Items.STICK, 1.0f),
        GiftOption.simple(Items.PUFFERFISH, 0.6f),
        GiftOption.simple(Items.SLIME_BALL, 0.8f),
        GiftOption.simple(Items.GLOW_INK_SAC, 0.75f),
        GiftOption.simple(Items.CLAY_BALL, 0.85f)
    );

    private PlayerEntity targetPlayer;
    private GiftSelection pendingSelection;
    private ItemStack giftStack = ItemStack.EMPTY;
    private float giftAffinity = 1.0f;
    private GiftScenario giftScenario = GiftScenario.TOKEN;
    private ScenarioContext giftScenarioContext = ScenarioContext.empty();
    private Phase phase = Phase.COMPLETE;
    private int phaseTicks = 0;
    private int totalTicks = 0;
    private boolean giftDelivered = false;
    private float neutralPitch = 0.0f;

    public GiftBringingGoal(MobEntity mob) {
        super(mob, GoalRegistry.require(GoalIds.GIFT_BRINGING), EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    protected boolean canStartGoal() {
        PetContext ctx = getContext();
        PlayerEntity owner = ctx.owner();
        pendingSelection = null;
        if (owner == null || owner.isRemoved() || owner.isSleeping() || owner.isSpectator()) {
            return false;
        }

        if (!ctx.ownerNearby() || ctx.distanceToOwner() > MAX_OWNER_DISTANCE) {
            return false;
        }

        if (ctx.bondStrength() < 0.35f) {
            return false;
        }

        if (ctx.behavioralMomentum() > 0.78f) {
            return false;
        }

        PetComponent component = PetComponent.get(mob);
        if (component == null || component.isOnCooldown(COOLDOWN_KEY)) {
            return false;
        }
        if (component.hasMoodAbove(PetComponent.Mood.ANGRY, 0.35f)
            || component.hasMoodAbove(PetComponent.Mood.RESTLESS, 0.6f)) {
            return false;
        }

        OwnerCombatState state = OwnerCombatState.get(owner);
        if (state != null) {
            long now = mob.getEntityWorld().getTime();
            if (state.isInCombat() || state.recentlyDamaged(now, 100) || state.isMounted()) {
                return false;
            }
        }

        GiftSelection selection = chooseGift(ctx, owner);
        if (selection == null || selection.stack().isEmpty()) {
            return false;
        }

        pendingSelection = selection;
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
        return mob.squaredDistanceTo(targetPlayer) <= HARD_STOP_DISTANCE_SQ;
    }

    @Override
    protected void onStartGoal() {
        PetContext ctx = getContext();
        targetPlayer = ctx.owner();
        if (targetPlayer == null) {
            requestStop();
            return;
        }

        giftStack = pendingSelection != null ? pendingSelection.stack().copy() : ItemStack.EMPTY;
        giftAffinity = pendingSelection != null ? pendingSelection.affinity() : 1.0f;
        giftScenario = pendingSelection != null ? pendingSelection.scenario() : GiftScenario.TOKEN;
        giftScenarioContext = pendingSelection != null ? pendingSelection.context() : ScenarioContext.empty();
        pendingSelection = null;
        giftDelivered = false;
        phase = Phase.APPROACH;
        phaseTicks = 0;
        totalTicks = 0;
        neutralPitch = mob.getPitch();
    }

    @Override
    protected void onStopGoal() {
        mob.getNavigation().stop();
        mob.setPitch(neutralPitch);
        phase = Phase.COMPLETE;
        phaseTicks = 0;
        totalTicks = 0;
        boolean delivered = giftDelivered;
        giftDelivered = false;
        giftStack = ItemStack.EMPTY;
        giftScenario = GiftScenario.TOKEN;
        giftScenarioContext = ScenarioContext.empty();
        pendingSelection = null;
        targetPlayer = null;

        PetComponent component = PetComponent.get(mob);
        if (component != null) {
            int base = delivered ? secondsToTicks(260) : secondsToTicks(90);
            int variance = delivered ? secondsToTicks(120) : secondsToTicks(40);
            int cooldown = base + mob.getRandom().nextInt(Math.max(1, variance));
            component.setCooldown(COOLDOWN_KEY, cooldown);
        }
    }

    @Override
    protected void onTickGoal() {
        if (phase == Phase.COMPLETE || targetPlayer == null) {
            requestStop();
            return;
        }

        totalTicks++;

        double distanceSq = mob.squaredDistanceTo(targetPlayer);
        if (distanceSq > HARD_STOP_DISTANCE_SQ) {
            requestStop();
            return;
        }

        switch (phase) {
            case APPROACH -> tickApproach(distanceSq);
            case PREPARE -> tickPrepare();
            case PRESENT -> tickPresent();
            default -> requestStop();
        }
    }

    private void tickApproach(double distanceSq) {
        phaseTicks++;

        if (distanceSq > 9.0d) {
            mob.getNavigation().startMovingTo(targetPlayer, 0.95);
        } else {
            mob.getNavigation().startMovingTo(targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(), 0.85);
        }
        mob.getLookControl().lookAt(targetPlayer, 18.0f, 18.0f);

        if (distanceSq <= 4.0d || phaseTicks > 60) {
            mob.getNavigation().stop();
            transitionTo(Phase.PREPARE);
        }
    }

    private void tickPrepare() {
        phaseTicks++;
        mob.getNavigation().stop();
        if (targetPlayer != null) {
            double focusX = targetPlayer.getX();
            double focusY = targetPlayer.getY() + targetPlayer.getStandingEyeHeight() * 0.4;
            double focusZ = targetPlayer.getZ();
            mob.getLookControl().lookAt(focusX, focusY, focusZ, 25.0f, 25.0f);
        }

        float sway = MathHelper.sin(phaseTicks * 0.35f) * 10.0f;
        mob.setPitch(MathHelper.clamp(sway - 8.0f, -25.0f, 20.0f));

        if (phaseTicks >= PREPARE_TICKS) {
            transitionTo(Phase.PRESENT);
        }
    }

    private void tickPresent() {
        phaseTicks++;
        mob.getNavigation().stop();
        if (targetPlayer != null) {
            mob.getLookControl().lookAt(targetPlayer, 28.0f, 28.0f);
        }

        float bob = MathHelper.sin(phaseTicks * 0.45f) * 12.0f;
        mob.setPitch(MathHelper.clamp(bob, -18.0f, 22.0f));

        if (!giftDelivered && phaseTicks >= 4) {
            deliverGift();
        }

        if (phaseTicks >= PRESENT_TICKS) {
            requestStop();
        }
    }

    private void deliverGift() {
        if (giftDelivered || giftStack.isEmpty() || targetPlayer == null) {
            return;
        }

        if (!(mob.getEntityWorld() instanceof ServerWorld serverWorld)) {
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
                ItemScatterer.spawn(serverWorld, targetPlayer.getX(), targetPlayer.getY() + 0.2d, targetPlayer.getZ(), handoffStack);
                dropped = true;
            }
        }

        if (accepted || dropped) {
            giftDelivered = true;
            RelationshipEventHandler.onGiftGiven(mob, targetPlayer, MathHelper.clamp(giftAffinity, 0.35f, 1.4f));
            sendGiftQuip(messageStack);
            updateScenarioCounters();
        } else {
            giftStack = preparedGift;
        }
    }

    @Override
    protected woflo.petsplus.ai.goals.EmotionFeedback defineEmotionFeedback() {
        return new woflo.petsplus.ai.goals.EmotionFeedback.Builder()
            .add(woflo.petsplus.state.PetComponent.Emotion.LOYALTY, 0.32f)
            .add(woflo.petsplus.state.PetComponent.Emotion.HOPEFUL, 0.18f)
            .add(woflo.petsplus.state.PetComponent.Emotion.SOBREMESA, 0.14f)
            .withContagion(woflo.petsplus.state.PetComponent.Emotion.HOPEFUL, 0.015f)
            .build();
    }

    @Override
    protected float calculateEngagement() {
        PetContext ctx = getContext();
        float bond = MathHelper.clamp(ctx.bondStrength(), 0.0f, 1.0f);
        float base = 0.58f + bond * 0.28f;
        base += MathHelper.clamp(1.0f - ctx.behavioralMomentum(), 0.0f, 1.0f) * 0.12f;

        if (phase == Phase.PREPARE) {
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
    }

    private GiftSelection chooseGift(PetContext ctx, PlayerEntity owner) {
        float bond = MathHelper.clamp(ctx.bondStrength(), 0.0f, 1.0f);
        boolean ownerHurt = owner.getHealth() < owner.getMaxHealth() * 0.75f;
        boolean ownerHungry = !owner.isCreative() && !owner.isSpectator()
            && owner.getHungerManager().getFoodLevel() < 18;
        boolean ownerWet = owner.isTouchingWaterOrRain() || owner.isSubmergedInWater();
        boolean inventoryFull = owner.getInventory().getEmptySlot() == -1;

        Map<String, Integer> quirkCounters = ctx.quirkCounters();
        int lastScenarioOrdinal = Math.max(-1, quirkCounters.getOrDefault("gift_bringing_last", 0) - 1);
        int repeatCount = Math.max(0, quirkCounters.getOrDefault("gift_bringing_repeat", 0));

        PetComponent.Mood mood = ctx.currentMood();
        boolean playfulMood = mood == PetComponent.Mood.PLAYFUL || mood == PetComponent.Mood.CURIOUS;
        boolean protectiveMood = mood == PetComponent.Mood.PROTECTIVE || mood == PetComponent.Mood.SISU;
        boolean calmMoment = ctx.behavioralMomentum() < 0.28f;

        ScenarioContext context = new ScenarioContext(
            ownerHurt,
            ownerHungry,
            ownerWet,
            inventoryFull,
            !ctx.isDaytime(),
            bond,
            mood,
            repeatCount
        );

        GiftScenario scenario = determineScenario(context, playfulMood, protectiveMood, calmMoment, lastScenarioOrdinal);
        List<GiftOption> pool = buildGiftPool(scenario, context);

        if (pool.isEmpty()) {
            return null;
        }

        GiftOption selected = selectWeighted(pool, mob.getRandom());
        if (selected == null) {
            return null;
        }

        ItemStack choice = selected.create(mob, owner, mob.getRandom(), context);
        if (choice.isEmpty()) {
            choice = new ItemStack(Items.POPPY);
        } else {
            choice = choice.copy();
        }
        if (choice.isEmpty()) {
            return null;
        }
        choice.setCount(MathHelper.clamp(choice.getCount(), 1, Math.max(1, choice.getMaxCount())));

        float affinity = 0.7f + bond * 0.45f;
        if (context.ownerHurt()) {
            affinity += 0.25f;
        } else if (context.ownerHungry()) {
            affinity += 0.15f;
        }
        if (scenario == GiftScenario.CELEBRATE) {
            affinity += 0.18f;
        } else if (scenario == GiftScenario.PLAYFUL) {
            affinity += 0.05f;
        }

        float clampedAffinity = MathHelper.clamp(affinity, 0.45f, 1.45f);
        return new GiftSelection(choice, clampedAffinity, scenario, context);
    }

    private static GiftOption selectWeighted(List<GiftOption> pool, Random random) {
        float total = 0.0f;
        for (GiftOption option : pool) {
            total += Math.max(option.weight(), 0.0f);
        }
        if (total <= 0.0f) {
            return null;
        }

        float roll = random.nextFloat() * total;
        float cumulative = 0.0f;
        for (GiftOption option : pool) {
            cumulative += Math.max(option.weight(), 0.0f);
            if (roll <= cumulative) {
                return option;
            }
        }

        return pool.get(pool.size() - 1);
    }

    private static int secondsToTicks(int seconds) {
        return Math.max(0, seconds * 20);
    }

    private void sendGiftQuip(ItemStack gift) {
        if (!(targetPlayer instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        GiftScenario scenario = giftScenario != null ? giftScenario : GiftScenario.TOKEN;
        ScenarioContext context = giftScenarioContext != null ? giftScenarioContext : ScenarioContext.empty();
        Text message = scenario.createMessage(mob, serverPlayer, gift, context, mob.getRandom());
        if (message != null) {
            serverPlayer.sendMessage(message, false);
        }
    }

    private void updateScenarioCounters() {
        PetComponent component = PetComponent.get(mob);
        if (component == null) {
            return;
        }
        Map<String, Integer> counters = component.getQuirkCountersSnapshot();
        int lastOrdinal = Math.max(-1, counters.getOrDefault("gift_bringing_last", 0) - 1);
        int repeat = counters.getOrDefault("gift_bringing_repeat", 0);
        if (giftScenario != null && giftScenario.ordinal() == lastOrdinal) {
            repeat = Math.min(repeat + 1, 6);
        } else {
            repeat = 0;
        }
        if (giftScenario != null) {
            component.setQuirkCounter("gift_bringing_last", giftScenario.ordinal() + 1);
        }
        component.setQuirkCounter("gift_bringing_repeat", repeat);
    }

    private GiftScenario determineScenario(ScenarioContext context, boolean playfulMood, boolean protectiveMood,
                                           boolean calmMoment, int lastScenarioOrdinal) {
        GiftScenario scenario;
        if (context.ownerHurt()) {
            scenario = GiftScenario.CARE;
        } else if (context.ownerHungry()) {
            scenario = GiftScenario.COMFORT;
        } else if (context.ownerWet()) {
            scenario = GiftScenario.DRY;
        } else if (playfulMood && mob.getRandom().nextFloat() < 0.6f) {
            scenario = GiftScenario.PLAYFUL;
        } else if (context.bond() > 0.7f && (calmMoment || mob.getRandom().nextBoolean())) {
            scenario = GiftScenario.CELEBRATE;
        } else if (protectiveMood) {
            scenario = GiftScenario.CARE;
        } else {
            scenario = GiftScenario.TOKEN;
        }

        if (scenario.ordinal() == lastScenarioOrdinal && context.repeat() >= 2) {
            if (!context.ownerHurt() && !context.ownerHungry() && scenario == GiftScenario.CARE) {
                scenario = GiftScenario.TOKEN;
            } else if (!context.ownerWet() && scenario == GiftScenario.DRY) {
                scenario = GiftScenario.TOKEN;
            } else if (scenario == GiftScenario.PLAYFUL) {
                scenario = GiftScenario.TOKEN;
            } else if (scenario == GiftScenario.CELEBRATE) {
                scenario = GiftScenario.PLAYFUL;
            }
        }

        return scenario;
    }

    private List<GiftOption> buildGiftPool(GiftScenario scenario, ScenarioContext context) {
        List<GiftOption> pool = new ArrayList<>();
        switch (scenario) {
            case CARE -> addAllScaled(pool, CARE_GIFTS, 1.35f);
            case COMFORT -> addAllScaled(pool, COMFORT_GIFTS, 1.3f);
            case DRY -> addAllScaled(pool, DRY_GIFTS, 1.25f);
            case CELEBRATE -> {
                addAllScaled(pool, CELEBRATION_GIFTS, 1.25f);
                addAllScaled(pool, TRUSTED_GIFTS, 0.9f + context.bond() * 0.35f);
            }
            case PLAYFUL -> addAllScaled(pool, CHAOTIC_GIFTS, 1.5f);
            case TOKEN -> addAllScaled(pool, TOKEN_GIFTS, 1.2f);
        }

        addAllScaled(pool, TOKEN_GIFTS, 0.55f);

        if (context.bond() >= 0.35f && scenario != GiftScenario.PLAYFUL) {
            addAllScaled(pool, COMFORT_GIFTS, 0.4f + context.bond() * 0.25f);
        }
        if (context.bond() >= 0.5f) {
            addAllScaled(pool, TRUSTED_GIFTS, 0.4f + context.bond() * 0.3f);
        }
        if (context.bond() >= 0.85f) {
            float rareScale = 0.3f + (context.bond() - 0.85f) * 0.6f;
            addAllScaled(pool, RARE_GIFTS, rareScale);
        }

        if (pool.isEmpty()) {
            pool.addAll(TOKEN_GIFTS);
        }
        return pool;
    }

    private static void addAllScaled(List<GiftOption> pool, List<GiftOption> source, float multiplier) {
        float scale = Math.max(0.0f, multiplier);
        for (GiftOption option : source) {
            pool.add(option.scaled(scale));
        }
    }

    private static String pick(Random random, String... options) {
        if (options.length == 0) {
            return "";
        }
        int idx = MathHelper.clamp(random.nextInt(options.length), 0, options.length - 1);
        return options[idx];
    }

    private record GiftOption(GiftItemSupplier supplier, float weight) {
        GiftOption scaled(float multiplier) {
            return new GiftOption(supplier, Math.max(0.0f, weight * multiplier));
        }

        ItemStack create(MobEntity pet, PlayerEntity owner, Random random, ScenarioContext context) {
            ItemStack result = supplier.create(pet, owner, random, context);
            return result == null ? ItemStack.EMPTY : result;
        }

        static GiftOption simple(ItemConvertible item, float weight) {
            return dynamic((pet, owner, random, context) -> new ItemStack(item), weight);
        }

        static GiftOption simple(ItemStack stack, float weight) {
            ItemStack template = stack.copy();
            return dynamic((pet, owner, random, context) -> template.copy(), weight);
        }

        static GiftOption dynamic(GiftItemSupplier supplier, float weight) {
            return new GiftOption(supplier, weight);
        }
    }

    @FunctionalInterface
    private interface GiftItemSupplier {
        ItemStack create(MobEntity pet, PlayerEntity owner, Random random, ScenarioContext context);
    }

    private record GiftSelection(ItemStack stack, float affinity, GiftScenario scenario, ScenarioContext context) {
    }

    private record ScenarioContext(
        boolean ownerHurt,
        boolean ownerHungry,
        boolean ownerWet,
        boolean inventoryFull,
        boolean night,
        float bond,
        PetComponent.Mood mood,
        int repeat
    ) {
        static ScenarioContext empty() {
            return new ScenarioContext(false, false, false, false, false, 0.0f, null, 0);
        }
    }

    private enum GiftScenario {
        CARE,
        COMFORT,
        DRY,
        CELEBRATE,
        PLAYFUL,
        TOKEN;

        Text createMessage(MobEntity pet, PlayerEntity owner, ItemStack item, ScenarioContext context, Random random) {
            String petName = pet.getDisplayName().getString();
            String ownerName = owner.getDisplayName().getString();
            String itemName = item.getName().getString();

            String line = switch (this) {
                case CARE -> {
                    String extra = context.ownerHurt()
                        ? "They keep fussing over your scuffs until you accept it."
                        : "Apparently it's preventive care now.";
                    yield pick(random,
                        petName + " nudges a " + itemName + " into " + ownerName + "'s hands like a tiny field medic.",
                        petName + " offers you " + itemName + " with a worried little chirp.") + " " + extra;
                }
                case COMFORT -> {
                    String hungerCue = context.ownerHungry()
                        ? "Your stomach growl absolutely betrayed you."
                        : "They seem convinced snacks fix every mood.";
                    yield pick(random,
                        petName + " prances up with " + itemName + ", delivering it with chef's-kiss confidence.",
                        petName + " drops " + itemName + " into your hands, tail wagging like it's dinner theatre.") + " " + hungerCue;
                }
                case DRY -> pick(random,
                        petName + " drops " + itemName + " at your soaked boots and declares a damp emergency.",
                        petName + " stages a rescue mission with " + itemName + ".")
                    + " Please pretend this counts as a towel.";
                case CELEBRATE -> {
                    String vibe = context.night()
                        ? "Moonlight looks good on victory laps."
                        : "Apparently heroes get loot drops now.";
                    yield pick(random,
                        petName + " struts over with celebratory " + itemName + ".",
                        petName + " insists you take " + itemName + " before the victory theme ends.") + " " + vibe;
                }
                case PLAYFUL -> pick(random,
                        petName + " dramatically delivers " + itemName + " and waits for the punchline.",
                        petName + " sprints in with " + itemName + ", eyes sparkling with mischief.")
                    + " You're the joke, by the way.";
                case TOKEN -> pick(random,
                        petName + " nudges " + itemName + " into your palm, soft eyes and softer heart.",
                        petName + " gifts you " + itemName + ", a tiny reminder you're their favourite human.");
            };

            MutableText base = Text.literal("[Pet Gift] ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal(line).formatted(Formatting.LIGHT_PURPLE));

            if (context.repeat() >= 3) {
                base.append(Text.literal(" (They're very committed to this bit.)").formatted(Formatting.GRAY));
            } else if (context.inventoryFull()) {
                base.append(Text.literal(" (They noticed your pockets are overflowing.)").formatted(Formatting.GRAY));
            }

            PetComponent.Mood mood = context.mood();
            if (mood == PetComponent.Mood.PLAYFUL && this == GiftScenario.PLAYFUL) {
                base.append(Text.literal(" (Playful mode: engaged.)").formatted(Formatting.AQUA));
            } else if (mood == PetComponent.Mood.PROTECTIVE && this == GiftScenario.CARE) {
                base.append(Text.literal(" (Guardian instincts at maximum fluff.)").formatted(Formatting.GOLD));
            }

            return base;
        }
    }
}
