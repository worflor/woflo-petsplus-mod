package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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

/**
 * Provides the Enchantment-Bound durability refund echo for owner tools.
 */
public class EnchantmentBoundDurabilityEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "enchantment_durability_refund");
    private static final String STATE_TICK = "eb_durability_tick";
    private static final String STATE_POS = "eb_durability_pos";

    private final double baseChance;
    private final double focusMultiplier;
    private final int minimumLevel;

    public EnchantmentBoundDurabilityEffect(JsonObject json) {
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        Identifier roleId = PetRoleType.ENCHANTMENT_BOUND.id();
        this.baseChance = Math.max(0.0D, RegistryJsonHelper.getDouble(json, "base_chance",
            config.getRoleDouble(roleId, "durabilityNoLossChance", 0.025D)));
        this.focusMultiplier = Math.max(1.0D, RegistryJsonHelper.getDouble(json, "focus_multiplier", 2.0D));
        this.minimumLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 1));
    }

    public EnchantmentBoundDurabilityEffect() {
        this.baseChance = 0.025D;
        this.focusMultiplier = 2.0D;
        this.minimumLevel = 1;
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

        ItemStack tool = owner.getMainHandStack();
        if (tool == null || tool.isEmpty() || !tool.isDamageable()) {
            return false;
        }

        BlockPos pos = context.getTriggerContext().getData("block_pos", BlockPos.class);
        long tick = world.getTime();
        long encodedPos = pos != null ? pos.asLong() : Long.MIN_VALUE;

        OwnerCombatState state = OwnerCombatState.getOrCreate(owner);
        long lastTick = state.getTempState(STATE_TICK);
        long lastPos = state.getTempState(STATE_POS);
        if (lastTick == tick && lastPos == encodedPos) {
            return false;
        }
        state.setTempState(STATE_TICK, tick);
        state.setTempState(STATE_POS, encodedPos);

        double chance = ChanceValidationUtil.validateChance(baseChance);
        if (EnchantmentBoundFocusHelper.isFocusActive(owner, EnchantmentBoundFocusHelper.Bucket.MINING)) {
            chance = ChanceValidationUtil.validateChance(chance * focusMultiplier);
        }
        if (!ChanceValidationUtil.checkChance(chance, world.getRandom())) {
            return false;
        }

        int damage = tool.getDamage();
        if (damage <= 0) {
            return false;
        }
        tool.setDamage(Math.max(0, damage - 1));
        return true;
    }
}


