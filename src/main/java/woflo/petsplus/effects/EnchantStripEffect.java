package woflo.petsplus.effects;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.particle.ParticleTypes;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.ui.UIFeedbackManager;
import org.jetbrains.annotations.Nullable;

/**
 * Removes the strongest enchantment from the owner's held item and bottles it into a book.
 */
public class EnchantStripEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "enchant_strip");

    private final int xpCostLevels;
    private final boolean preferMainHand;
    private final boolean allowOffhand;
    private final boolean dropAsBook;

    public EnchantStripEffect(int xpCostLevels, boolean preferMainHand, boolean allowOffhand, boolean dropAsBook) {
        this.xpCostLevels = Math.max(0, xpCostLevels);
        this.preferMainHand = preferMainHand;
        this.allowOffhand = allowOffhand;
        this.dropAsBook = dropAsBook;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getEntityWorld() instanceof ServerWorld world)) {
            return false;
        }

        MobEntity pet = context.getPet();
        PlayerEntity ownerEntity = context.getOwner();
        if (!(ownerEntity instanceof ServerPlayerEntity owner) || pet == null) {
            return false;
        }

        if (!owner.isAlive() || owner.isRemoved()) {
            return false;
        }

        if (!(owner.getEntityWorld() instanceof ServerWorld ownerWorld) || ownerWorld != world) {
            return false;
        }

        if (xpCostLevels > 0 && !owner.getAbilities().creativeMode && owner.experienceLevel < xpCostLevels) {
            UIFeedbackManager.sendEnchantmentStripInsufficientXp(owner, xpCostLevels);
            return false;
        }

        Hand hand = resolveTargetHand(owner);
        if (hand == null) {
            UIFeedbackManager.sendEnchantmentStripNoTarget(owner, pet);
            return false;
        }

        ItemStack targetStack = owner.getStackInHand(hand);
        ItemEnchantmentsComponent enchantments = targetStack.getOrDefault(DataComponentTypes.ENCHANTMENTS,
            ItemEnchantmentsComponent.DEFAULT);
        if (enchantments.isEmpty()) {
            UIFeedbackManager.sendEnchantmentStripNoTarget(owner, pet);
            return false;
        }

        SelectedEnchantment strongest = resolveStrongest(enchantments);
        if (strongest == null) {
            UIFeedbackManager.sendEnchantmentStripNoTarget(owner, pet);
            return false;
        }

        Text itemName = targetStack.getName();
        ItemEnchantmentsComponent updated = removeEnchantment(enchantments, strongest.entry());
        if (updated.isEmpty()) {
            targetStack.remove(DataComponentTypes.ENCHANTMENTS);
        } else {
            targetStack.set(DataComponentTypes.ENCHANTMENTS, updated);
        }

        if (targetStack.isOf(Items.ENCHANTED_BOOK) && !hasEnchantments(targetStack)) {
            owner.setStackInHand(hand, new ItemStack(Items.BOOK));
        }

        if (xpCostLevels > 0 && !owner.getAbilities().creativeMode) {
            owner.addExperienceLevels(-xpCostLevels);
        }

        if (dropAsBook) {
            ItemStack book = createBookCopy(strongest);
            if (!owner.getInventory().insertStack(book)) {
                ItemScatterer.spawn(owner.getEntityWorld(), owner.getX(), owner.getY() + 0.5, owner.getZ(), book);
            }
        }

        Text enchantName = strongest.entry().value().getName(strongest.entry(), strongest.level());
        UIFeedbackManager.sendEnchantmentStripSuccess(owner, pet, enchantName, itemName);
        ownerWorld.playSound(null, owner.getBlockPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundCategory.PLAYERS, 0.8f, 1.15f);
        owner.swingHand(hand);
        spawnParticles(ownerWorld, owner, pet);
        return true;
    }

    private Hand resolveTargetHand(ServerPlayerEntity owner) {
        ItemStack main = owner.getMainHandStack();
        ItemStack off = owner.getOffHandStack();

        if (preferMainHand) {
            if (hasEnchantments(main)) {
                return Hand.MAIN_HAND;
            }
            if (allowOffhand && hasEnchantments(off)) {
                return Hand.OFF_HAND;
            }
        } else {
            if (allowOffhand && hasEnchantments(off)) {
                return Hand.OFF_HAND;
            }
            if (hasEnchantments(main)) {
                return Hand.MAIN_HAND;
            }
        }

        if (!preferMainHand && hasEnchantments(main)) {
            return Hand.MAIN_HAND;
        }
        if (allowOffhand && hasEnchantments(off)) {
            return Hand.OFF_HAND;
        }
        return hasEnchantments(main) ? Hand.MAIN_HAND : null;
    }

    private static boolean hasEnchantments(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemEnchantmentsComponent component = stack.get(DataComponentTypes.ENCHANTMENTS);
        return component != null && !component.isEmpty();
    }

    private static SelectedEnchantment resolveStrongest(ItemEnchantmentsComponent enchantments) {
        SelectedEnchantment best = null;
        for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
            int level = enchantments.getLevel(entry);
            if (best == null || level > best.level() || (level == best.level() && compare(entry, best.entry()) > 0)) {
                best = new SelectedEnchantment(entry, level);
            }
        }
        return best;
    }

    private static ItemEnchantmentsComponent removeEnchantment(ItemEnchantmentsComponent component,
        RegistryEntry<Enchantment> enchantment) {
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(component);
        builder.remove(candidate -> candidate.equals(enchantment));
        return builder.build();
    }

    private static ItemStack createBookCopy(SelectedEnchantment enchantment) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        int maxLevel = Math.max(1, enchantment.entry().value().getMaxLevel());
        int storedLevel = MathHelper.clamp(enchantment.level(), 1, maxLevel);
        builder.set(enchantment.entry(), storedLevel);
        book.set(DataComponentTypes.ENCHANTMENTS, builder.build());
        return book;
    }

    private static void spawnParticles(ServerWorld world, ServerPlayerEntity owner, @Nullable MobEntity pet) {
        world.spawnParticles(ParticleTypes.ENCHANT,
            owner.getX(), owner.getBodyY(0.6), owner.getZ(),
            28, 0.6, 0.6, 0.6, 0.0);

        if (pet != null && pet.getEntityWorld() == world) {
            world.spawnParticles(ParticleTypes.SOUL,
                pet.getX(), pet.getBodyY(0.5), pet.getZ(),
                16, 0.4, 0.4, 0.4, 0.02);
        }
    }

    private static int compare(RegistryEntry<Enchantment> left, RegistryEntry<Enchantment> right) {
        Identifier leftId = bestId(left);
        Identifier rightId = bestId(right);
        return leftId.compareTo(rightId);
    }

    private static Identifier bestId(RegistryEntry<Enchantment> entry) {
        return entry.getKey().map(RegistryKey::getValue).orElseGet(() -> Identifier.of("minecraft", "unknown"));
    }

    private record SelectedEnchantment(RegistryEntry<Enchantment> entry, int level) {
    }
}


