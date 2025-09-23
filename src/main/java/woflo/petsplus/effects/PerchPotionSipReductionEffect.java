package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.util.PetPerchUtil;

/**
 * Effect that reduces potion consumption when pet is perched.
 * This is a passive effect that modifies potion sip behavior.
 */
public class PerchPotionSipReductionEffect implements Effect {
    private final double discountPercent;
    
    public PerchPotionSipReductionEffect(JsonObject config) {
        this.discountPercent = getDoubleOrDefault(config, "discount_percent",
            PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.SUPPORT.id(), "perchSipDiscount", 0.20));
    }

    private static double getDoubleOrDefault(JsonObject json, String key, double defaultValue) {
        if (!json.has(key)) return defaultValue;

        com.google.gson.JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsDouble();
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            return parseConfigVariable(value, defaultValue);
        }

        return defaultValue;
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
        
        if (owner == null || pet == null) {
            return false;
        }
        
        // Mark that this pet provides potion sip reduction
        // This could be used by potion consumption logic to check if reduction applies
        context.withData("perch_sip_reduction_active", true);
        context.withData("perch_sip_discount", discountPercent);
        
        return true;
    }
    
    /**
     * Check if the pet is perched on the owner.
     */
    public static boolean isPetPerched(MobEntity pet, PlayerEntity owner) {
        if (pet == null || owner == null) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component != null &&
            component.hasRole(PetRoleType.SUPPORT) &&
            component.isOwnedBy(owner) &&
            PetPerchUtil.isPetPerched(component)) {
            return true;
        }

        return PetPerchUtil.ownerHasPerchedRole(owner, PetRoleType.SUPPORT);
    }
    
    /**
     * Calculate the reduced potion sip amount.
     */
    public static double getReducedSipAmount(double originalAmount, double discountPercent) {
        return originalAmount * (1.0 - discountPercent);
    }
}