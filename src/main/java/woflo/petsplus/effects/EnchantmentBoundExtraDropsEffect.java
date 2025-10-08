package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.roles.enchantmentbound.EnchantmentBoundFocusHelper;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.util.ChanceValidationUtil;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;

/**
 * Handles the Enchantment-Bound extra drop echoes for mining and combat.
 */
public class EnchantmentBoundExtraDropsEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "enchantment_extra_drops");
    private static final String STATE_BLOCK_TICK = "eb_block_dup_tick";
    private static final String STATE_BLOCK_POS = "eb_block_dup_pos";
    private static final String STATE_LOOT_TICK = "eb_loot_dup_tick";
    private static final String STATE_LOOT_HIGH = "eb_loot_dup_high";
    private static final String STATE_LOOT_LOW = "eb_loot_dup_low";

    private final Mode mode;
    private final double baseChance;
    private final double perLevelBonus;
    private final double focusMultiplier;
    private final int minimumLevel;
    private final boolean requireHostile;
    private final boolean playFeedback;

    public EnchantmentBoundExtraDropsEffect(JsonObject json) {
        String modeName = RegistryJsonHelper.getString(json, "mode", "block");
        this.mode = "mob".equalsIgnoreCase(modeName) ? Mode.MOB : Mode.BLOCK;
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        Identifier roleId = PetRoleType.ENCHANTMENT_BOUND.id();
        this.baseChance = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "base_chance",
            config.getRoleDouble(roleId, "extraDuplicationChanceBase", 0.05D)));
        this.perLevelBonus = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "per_level_bonus", 0.02D));
        this.focusMultiplier = Math.max(1.0D, RegistryJsonHelper.getDouble(json, "focus_multiplier", 2.0D));
        this.minimumLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 1));
        this.requireHostile = RegistryJsonHelper.getBoolean(json, "require_hostile", true);
        this.playFeedback = RegistryJsonHelper.getBoolean(json, "play_feedback", true);
    }

    public EnchantmentBoundExtraDropsEffect() {
        this.mode = Mode.BLOCK;
        this.baseChance = 0.05D;
        this.perLevelBonus = 0.02D;
        this.focusMultiplier = 2.0D;
        this.minimumLevel = 1;
        this.requireHostile = true;
        this.playFeedback = true;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getOwner() instanceof ServerPlayerEntity owner)) {
            return false;
        }
        if (!(context.getEntityWorld() instanceof ServerWorld world)) {
            return false;
        }
        PetComponent component = PetComponent.get(context.getPet());
        if (component == null || !component.hasRole(PetRoleType.ENCHANTMENT_BOUND) || component.getLevel() < minimumLevel) {
            return false;
        }

        return switch (mode) {
            case BLOCK -> handleBlockDuplication(owner, world, context);
            case MOB -> handleMobDuplication(owner, world, context);
        };
    }

    private boolean handleBlockDuplication(ServerPlayerEntity owner,
                                           ServerWorld world,
                                           EffectContext context) {
        BlockState state = context.getTriggerContext().getData("block_state", BlockState.class);
        BlockPos pos = context.getTriggerContext().getData("block_pos", BlockPos.class);
        if (state == null || pos == null) {
            return false;
        }

        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);
        long tick = world.getTime();
        long encodedPos = pos.asLong();
        long lastTick = combatState.getTempState(STATE_BLOCK_TICK);
        long lastPos = combatState.getTempState(STATE_BLOCK_POS);
        if (lastTick == tick && lastPos == encodedPos) {
            return false;
        }
        combatState.setTempState(STATE_BLOCK_TICK, tick);
        combatState.setTempState(STATE_BLOCK_POS, encodedPos);

        double chance = baseChance + getEnchantLevel(owner, Enchantments.FORTUNE) * perLevelBonus;
        if (EnchantmentBoundFocusHelper.isFocusActive(owner, EnchantmentBoundFocusHelper.Bucket.MINING)) {
            chance = ChanceValidationUtil.validateChance(chance * focusMultiplier);
        } else {
            chance = ChanceValidationUtil.validateChance(chance);
        }
        if (!ChanceValidationUtil.checkChance(chance, world.getRandom())) {
            return false;
        }

        List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, null, owner, owner.getMainHandStack());
        if (drops.isEmpty()) {
            return false;
        }
        for (ItemStack drop : drops) {
            Block.dropStack(world, pos, drop.copy());
        }
        return true;
    }

    private boolean handleMobDuplication(ServerPlayerEntity owner,
                                         ServerWorld world,
                                         EffectContext context) {
        @SuppressWarnings("unchecked")
        List<ItemStack> drops = (List<ItemStack>) context.getTriggerContext().getData("drops", List.class);
        if (drops == null || drops.isEmpty()) {
            return false;
        }
        Entity victim = context.getTriggerContext().getVictim();
        if (requireHostile && !(victim instanceof net.minecraft.entity.mob.Monster)) {
            return false;
        }

        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);
        long tick = world.getTime();
        long lastTick = combatState.getTempState(STATE_LOOT_TICK);
        long lastHigh = combatState.getTempState(STATE_LOOT_HIGH);
        long lastLow = combatState.getTempState(STATE_LOOT_LOW);
        long victimHigh = victim != null ? victim.getUuid().getMostSignificantBits() : Long.MIN_VALUE;
        long victimLow = victim != null ? victim.getUuid().getLeastSignificantBits() : Long.MIN_VALUE;
        if (lastTick == tick && lastHigh == victimHigh && lastLow == victimLow) {
            return false;
        }
        combatState.setTempState(STATE_LOOT_TICK, tick);
        combatState.setTempState(STATE_LOOT_HIGH, victimHigh);
        combatState.setTempState(STATE_LOOT_LOW, victimLow);

        double chance = baseChance + getEnchantLevel(owner, Enchantments.LOOTING) * perLevelBonus;
        if (EnchantmentBoundFocusHelper.isFocusActive(owner, EnchantmentBoundFocusHelper.Bucket.COMBAT)) {
            chance = ChanceValidationUtil.validateChance(chance * focusMultiplier);
        } else {
            chance = ChanceValidationUtil.validateChance(chance);
        }

        Random random = context.getTriggerContext().getData("loot_random", Random.class);
        if (random == null) {
            random = world.getRandom();
        }
        if (!ChanceValidationUtil.checkChance(chance, random)) {
            return false;
        }

        ItemStack duplicated = drops.get(random.nextInt(drops.size())).copy();
        drops.add(duplicated);

        if (playFeedback && victim != null) {
            world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, SoundCategory.PLAYERS, 0.2f, 1.2f);
        }
        return true;
    }

    private int getEnchantLevel(PlayerEntity owner, RegistryEntry<Enchantment> entry) {
        return EnchantmentHelper.getEquipmentLevel(entry, owner);
    }

    private int getEnchantLevel(PlayerEntity owner, net.minecraft.registry.RegistryKey<Enchantment> key) {
        RegistryEntry<Enchantment> entry = owner.getEntityWorld().getRegistryManager()
            .getOrThrow(RegistryKeys.ENCHANTMENT)
            .getOrThrow(key);
        return getEnchantLevel(owner, entry);
    }

    private enum Mode {
        BLOCK,
        MOB
    }
}


