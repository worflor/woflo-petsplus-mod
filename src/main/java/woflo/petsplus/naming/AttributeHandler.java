package woflo.petsplus.naming;

import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.state.PetComponent;

/**
 * Interface for handling specific attribute types parsed from pet names.
 * Implementations should apply the appropriate effects or modifications to the pet.
 */
@FunctionalInterface
public interface AttributeHandler {

    /**
     * Apply the effect of this attribute to a pet.
     *
     * @param pet       The pet entity to modify
     * @param attribute The parsed attribute key containing type, value, and priority
     * @param component The pet's component for accessing/modifying state
     */
    void apply(MobEntity pet, AttributeKey attribute, PetComponent component);
}
