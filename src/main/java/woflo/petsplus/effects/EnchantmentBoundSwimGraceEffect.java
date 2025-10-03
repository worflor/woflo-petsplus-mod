package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.roles.enchantmentbound.EnchantmentBoundFocusHelper;
import woflo.petsplus.state.PetComponent;

/**
 * Applies the Enchantment-Bound swim aura, granting brief Dolphin's Grace.
 */
public class EnchantmentBoundSwimGraceEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "enchantment_swim_grace");

    private final int durationTicks;
    private final int minimumLevel;
    private final boolean triggerFocus;

    public EnchantmentBoundSwimGraceEffect(JsonObject json) {
        this.durationTicks = Math.max(1, RegistryJsonHelper.getInt(json, "duration_ticks", 40));
        this.minimumLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 1));
        this.triggerFocus = RegistryJsonHelper.getBoolean(json, "trigger_focus", false);
    }

    public EnchantmentBoundSwimGraceEffect() {
        this.durationTicks = 40;
        this.minimumLevel = 1;
        this.triggerFocus = false;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity ownerEntity = context.getOwner();
        if (!(ownerEntity instanceof ServerPlayerEntity owner)) {
            return false;
        }
        if (!(context.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        MobEntity pet = context.getPet();
        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.ENCHANTMENT_BOUND) || component.getLevel() < minimumLevel) {
            return false;
        }
        if (!owner.isTouchingWater()) {
            return false;
        }

        int aqua = getEnchantLevel(owner, Enchantments.AQUA_AFFINITY);
        int depth = getEnchantLevel(owner, Enchantments.DEPTH_STRIDER);
        if (aqua <= 0 && depth <= 0) {
            return false;
        }

        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, durationTicks, 0, false, false, true));
        if (triggerFocus) {
            EnchantmentBoundFocusHelper.tryActivate(owner, EnchantmentBoundFocusHelper.Bucket.SWIM,
                component.getLevel(), durationTicks, 1200, 2, true);
        }
        return true;
    }

    private int getEnchantLevel(PlayerEntity owner, RegistryEntry<Enchantment> entry) {
        return EnchantmentHelper.getEquipmentLevel(entry, owner);
    }

    private int getEnchantLevel(PlayerEntity owner, net.minecraft.registry.RegistryKey<Enchantment> key) {
        RegistryEntry<Enchantment> entry = owner.getWorld().getRegistryManager()
            .getOrThrow(RegistryKeys.ENCHANTMENT)
            .getOrThrow(key);
        return getEnchantLevel(owner, entry);
    }
}
