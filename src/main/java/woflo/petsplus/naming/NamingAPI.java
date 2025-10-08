package woflo.petsplus.naming;

import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.ActionResult;
import woflo.petsplus.state.PetComponent;

import java.util.List;

/**
 * Public API for the pet naming system, allowing external mods to interact
 * with name-based attribute parsing and handling.
 */
public class NamingAPI {

    /**
     * Event fired when a pet's name attributes are parsed and about to be applied.
     * Listeners can modify the attribute list or cancel the processing.
     */
    public static final net.fabricmc.fabric.api.event.Event<PetNamedCallback> PET_NAMED_EVENT =
        EventFactory.createArrayBacked(PetNamedCallback.class,
            (listeners) -> (pet, attributes, component) -> {
                for (PetNamedCallback listener : listeners) {
                    ActionResult result = listener.onPetNamed(pet, attributes, component);
                    if (result != ActionResult.PASS) {
                        return result;
                    }
                }
                return ActionResult.PASS;
            });

    /**
     * Register a custom attribute handler.
     *
     * @param attributeType The attribute type to handle (e.g., "magical", "cursed")
     * @param handler       The handler implementation
     */
    public static void registerHandler(String attributeType, AttributeHandler handler) {
        AttributeRegistry.registerHandler(attributeType, handler);
    }

    /**
     * Unregister an attribute handler.
     *
     * @param attributeType The attribute type to unregister
     * @return true if a handler was removed, false if none existed
     */
    public static boolean unregisterHandler(String attributeType) {
        return AttributeRegistry.unregisterHandler(attributeType);
    }

    /**
     * Check if a handler exists for the given attribute type.
     *
     * @param attributeType The attribute type to check
     * @return true if a handler is registered
     */
    public static boolean hasHandler(String attributeType) {
        return AttributeRegistry.hasHandler(attributeType);
    }

    /**
     * Manually parse a name string into attributes.
     *
     * @param name The name to parse
     * @return List of parsed attributes
     */
    public static List<AttributeKey> parseName(String name) {
        return NameParser.parse(name);
    }

    /**
     * Manually apply attributes to a pet.
     *
     * @param pet        The pet entity
     * @param attributes The attributes to apply
     * @param component  The pet's component
     */
    public static void applyAttributes(MobEntity pet, List<AttributeKey> attributes, PetComponent component) {
        AttributeRegistry.applyAll(pet, attributes, component);
    }

    /**
     * Fire the PET_NAMED_EVENT for external processing.
     *
     * @param pet        The pet entity
     * @param attributes The parsed attributes
     * @param component  The pet's component
     * @return The event result
     */
    public static ActionResult firePetNamedEvent(MobEntity pet, List<AttributeKey> attributes, PetComponent component) {
        return PET_NAMED_EVENT.invoker().onPetNamed(pet, attributes, component);
    }

    /**
     * Get the current name attributes for a pet.
     *
     * @param pet The pet entity
     * @return List of current name attributes, or empty list if no pet component exists
     */
    public static List<AttributeKey> getPetNameAttributes(MobEntity pet) {
        PetComponent component = PetComponent.get(pet);
        return component != null ? component.getNameAttributes() : List.of();
    }

    /**
     * Set name attributes for a pet directly (bypasses name parsing).
     *
     * @param pet        The pet entity
     * @param attributes The attributes to set
     * @return true if successful, false if no pet component exists
     */
    public static boolean setPetNameAttributes(MobEntity pet, List<AttributeKey> attributes) {
        PetComponent component = PetComponent.get(pet);
        if (component != null) {
            component.setNameAttributes(attributes);
            return true;
        }
        return false;
    }

    /**
     * Clear the name parsing cache for a specific pet.
     *
     * @param pet The pet entity
     */
    public static void clearParsingCache(MobEntity pet) {
        if (pet != null) {
            NameParser.clearCache(pet.getUuid());
        }
    }

    /**
     * Clear the entire name parsing cache.
     */
    public static void clearAllParsingCache() {
        NameParser.clearAllCache();
    }

    /**
     * Callback interface for the PET_NAMED_EVENT.
     */
    @FunctionalInterface
    public interface PetNamedCallback {
        /**
         * Called when a pet's name attributes have been parsed.
         *
         * @param pet        The pet entity that was named
         * @param attributes The parsed attributes (modifiable list)
         * @param component  The pet's component
         * @return ActionResult.SUCCESS to prevent further processing,
         *         ActionResult.FAIL to cancel attribute application,
         *         ActionResult.PASS to continue normal processing
         */
        ActionResult onPetNamed(MobEntity pet, List<AttributeKey> attributes, PetComponent component);
    }
}
