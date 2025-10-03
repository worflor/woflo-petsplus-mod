package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import java.util.function.DoubleSupplier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
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
        this.discountPercent = getDoubleOrDefault(config, "discount_percent",
            () -> PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.SUPPORT.id(), "perchSipDiscount", 0.20));
        int configuredLinger = config.has("linger_ticks") ? config.get("linger_ticks").getAsInt() : 80;
        this.lingerTicks = Math.max(20, configuredLinger);
    }

    private static double getDoubleOrDefault(JsonObject json, String key, DoubleSupplier defaultSupplier) {
        if (!json.has(key)) return defaultSupplier.getAsDouble();

        com.google.gson.JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsDouble();
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            return parseConfigVariable(value, defaultSupplier.getAsDouble());
        }

        return defaultSupplier.getAsDouble();
    }

    private static double parseConfigVariable(String value, double defaultValue) {
        if (value.startsWith("${") && value.endsWith("}")) {
            String configPath = value.substring(2, value.length() - 1);
            int delimiter = configPath.lastIndexOf('.');
            if (delimiter > 0 && delimiter < configPath.length() - 1) {
                String scope = configPath.substring(0, delimiter);
                String key = configPath.substring(delimiter + 1);
                return PetsPlusConfig.getInstance().resolveScopedDouble(scope, key, defaultValue);
            }
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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
        ServerWorld world = context.getWorld();

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