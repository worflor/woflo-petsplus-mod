package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.roles.support.SupportPotionUtils;
import woflo.petsplus.state.PetComponent;

/**
 * Effect that reduces potion consumption when pet is perched.
 * This is a passive effect that modifies potion sip behavior.
 */
public class PerchPotionSipReductionEffect implements Effect {
    private final double discountPercent;
    private final int lingerTicks;

    public PerchPotionSipReductionEffect(JsonObject config) {
        if (config.has("discount_percent")) {
            this.discountPercent = RegistryJsonHelper.getDouble(config, "discount_percent", 0.20);
        } else {
            this.discountPercent = PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.SUPPORT.id(), "perchSipDiscount", 0.20);
        }
        this.lingerTicks = Math.max(20, RegistryJsonHelper.getInt(config, "linger_ticks", 80));
    }
    
    @Override
    public Identifier getId() {
        return Identifier.of("petsplus", "perch_potion_sip_reduction");
    }
    
    @Override
    public boolean execute(EffectContext context) {
        // This effect is passive and applied during potion consumption
        // The actual logic is handled in the potion consumption hooks

        PlayerEntity owner = context.getOwner();
        MobEntity pet = context.getPet();
        ServerWorld world = context.getEntityWorld();

        if (owner == null || pet == null || world == null) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.SUPPORT) || !component.isOwnedBy(owner)) {
            return false;
        }

        double sanitizedDiscount = MathHelper.clamp(discountPercent, 0.0, 1.0);
        double multiplier = MathHelper.clamp(1.0 - sanitizedDiscount, 0.0, 1.0);
        long expiryTick = world.getTime() + lingerTicks;

        component.setStateData(SupportPotionUtils.STATE_PERCH_SIP_DISCOUNT, sanitizedDiscount);
        component.setStateData(SupportPotionUtils.STATE_PERCH_SIP_MULTIPLIER, multiplier);
        component.setStateData(SupportPotionUtils.STATE_PERCH_SIP_EXPIRY_TICK, expiryTick);

        return true;
    }
    
}

