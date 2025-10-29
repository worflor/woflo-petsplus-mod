package woflo.petsplus.naming;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import woflo.petsplus.Petsplus;
import woflo.petsplus.naming.NameAffinityDefinitions.RoleAffinityVector;
import woflo.petsplus.state.PetComponent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing attribute handlers that process parsed pet name attributes.
 * This system allows modular handling of different attribute types.
 */
public class AttributeRegistry {
    private static final Map<String, AttributeHandler> HANDLERS = new ConcurrentHashMap<>();
    static {
        // Register built-in handlers
        registerBuiltinHandlers();
    }

    /**
     * Register an attribute handler for a specific attribute type.
     *
     * @param attributeType The attribute type to handle (e.g., "courage", "speed")
     * @param handler       The handler that will process this attribute type
     */
    public static void registerHandler(String attributeType, AttributeHandler handler) {
        if (attributeType == null || attributeType.trim().isEmpty()) {
            Petsplus.LOGGER.warn("Attempted to register attribute handler with null or empty type");
            return;
        }

        if (handler == null) {
            Petsplus.LOGGER.warn("Attempted to register null handler for attribute type: {}", attributeType);
            return;
        }

        String normalizedType = attributeType.trim().toLowerCase();
        AttributeHandler existing = HANDLERS.put(normalizedType, handler);

        if (existing != null) {
            Petsplus.LOGGER.debug("Replaced existing handler for attribute type: {}", normalizedType);
        } else {
            Petsplus.LOGGER.debug("Registered new handler for attribute type: {}", normalizedType);
        }
    }

    /**
     * Unregister an attribute handler.
     *
     * @param attributeType The attribute type to unregister
     * @return true if a handler was removed, false if none existed
     */
    public static boolean unregisterHandler(String attributeType) {
        if (attributeType == null || attributeType.trim().isEmpty()) {
            return false;
        }

        String normalizedType = attributeType.trim().toLowerCase();
        AttributeHandler removed = HANDLERS.remove(normalizedType);

        if (removed != null) {
            Petsplus.LOGGER.debug("Unregistered handler for attribute type: {}", normalizedType);
            return true;
        }

        return false;
    }

    /**
     * Check if a handler is registered for the given attribute type.
     *
     * @param attributeType The attribute type to check
     * @return true if a handler is registered, false otherwise
     */
    public static boolean hasHandler(String attributeType) {
        if (attributeType == null || attributeType.trim().isEmpty()) {
            return false;
        }
        return HANDLERS.containsKey(attributeType.trim().toLowerCase());
    }

    /**
     * Apply all attribute handlers to a pet based on its parsed name attributes.
     *
     * @param pet       The pet entity to modify
     * @param attributes The list of parsed attributes
     * @param component The pet's component
     */
    public static void applyAll(MobEntity pet, List<AttributeKey> attributes, PetComponent component) {
        if (pet == null || attributes == null || component == null) {
            return;
        }

        component.resetRoleAffinityBonuses();

        if (attributes.isEmpty()) {
            Petsplus.LOGGER.debug("No attributes to apply for pet: {}", pet.getUuid());
            return;
        }

        Petsplus.LOGGER.debug("Applying {} attributes to pet: {}", attributes.size(), pet.getUuid());

        int appliedCount = 0;
        for (AttributeKey attribute : attributes) {
            if (applyAttribute(pet, attribute, component)) {
                appliedCount++;
            }
        }

        Petsplus.LOGGER.debug("Successfully applied {}/{} attributes to pet: {}",
            appliedCount, attributes.size(), pet.getUuid());
    }

    /**
     * Apply a single attribute to a pet.
     *
     * @param pet       The pet entity to modify
     * @param attribute The attribute to apply
     * @param component The pet's component
     * @return true if the attribute was successfully applied, false otherwise
     */
    public static boolean applyAttribute(MobEntity pet, AttributeKey attribute, PetComponent component) {
        if (pet == null || attribute == null || component == null) {
            return false;
        }

        String normalizedType = attribute.normalizedType();
        if (normalizedType.isEmpty()) {
            Petsplus.LOGGER.warn("Attempted to apply attribute with empty type to pet: {}", pet.getUuid());
            return false;
        }

        AttributeHandler handler = HANDLERS.get(normalizedType);
        if (handler == null) {
            Petsplus.LOGGER.debug("No handler registered for attribute type '{}' on pet: {}",
                normalizedType, pet.getUuid());
            return false;
        }

        try {
            handler.apply(pet, attribute, component);
            Petsplus.LOGGER.debug("Applied attribute '{}' to pet: {}", attribute, pet.getUuid());
            return true;
        } catch (Exception e) {
            Petsplus.LOGGER.error("Error applying attribute '{}' to pet {}: {}",
                attribute, pet.getUuid(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get the number of registered handlers.
     */
    public static int getHandlerCount() {
        return HANDLERS.size();
    }

    /**
     * Clear all registered handlers. Use with caution.
     */
    public static void clearAllHandlers() {
        int count = HANDLERS.size();
        HANDLERS.clear();
        Petsplus.LOGGER.warn("Cleared {} attribute handlers", count);
    }

    /**
     * Register the built-in attribute handlers.
     */
    private static void registerBuiltinHandlers() {
        SpecialNameDefinitions.bootstrap();

        // Example handlers - these demonstrate the system and can be expanded

        // Courage/bravery attributes
        registerHandler("courage", (pet, attr, comp) -> {
            Petsplus.LOGGER.info("Pet {} shows courage: {}", pet.getUuid(), attr.value());
            // Could modify AI behavior, reduce fear responses, etc.
            comp.setStateData("courage_level", attr.value());
        });

        // Speed attributes
        registerHandler("speed", (pet, attr, comp) -> {
            Petsplus.LOGGER.info("Pet {} has speed attribute: {}", pet.getUuid(), attr.value());
            // Could modify movement speed, agility, etc.
            comp.setStateData("speed_modifier", attr.value());
        });

        // Combat attributes
        registerHandler("combat", (pet, attr, comp) -> {
            Petsplus.LOGGER.info("Pet {} has combat trait: {}", pet.getUuid(), attr.value());
            // Could modify attack damage, combat AI, etc.
            comp.setStateData("combat_style", attr.value());
        });

        // Temperament attributes
        registerHandler("temperament", (pet, attr, comp) -> {
            Petsplus.LOGGER.info("Pet {} has temperament: {}", pet.getUuid(), attr.value());
            // Could affect mood changes, social behavior, etc.
            comp.setStateData("base_temperament", attr.value());
        });

        // Elemental attributes
        registerHandler("element", (pet, attr, comp) -> {
            Petsplus.LOGGER.info("Pet {} has elemental affinity: {}", pet.getUuid(), attr.value());
            // Could add particle effects, resistance, special abilities, etc.
            comp.setStateData("elemental_type", attr.value());
        });

        // Level/rating attributes
        registerHandler("level", (pet, attr, comp) -> {
            try {
                int level = Integer.parseInt(attr.value());
                Petsplus.LOGGER.info("Pet {} has level designation: {}", pet.getUuid(), level);
                // Could set initial level, experience, etc.
                comp.setStateData("designated_level", level);
            } catch (NumberFormatException e) {
                Petsplus.LOGGER.warn("Invalid level value for pet {}: {}", pet.getUuid(), attr.value());
            }
        });

        registerHandler("rating", (pet, attr, comp) -> {
            try {
                int rating = Integer.parseInt(attr.value());
                Petsplus.LOGGER.info("Pet {} has star rating: {}", pet.getUuid(), rating);
                // Could affect initial stats, special abilities, etc.
                comp.setStateData("star_rating", rating);
            } catch (NumberFormatException e) {
                Petsplus.LOGGER.warn("Invalid rating value for pet {}: {}", pet.getUuid(), attr.value());
            }
        });

        // Intelligence attributes
        registerHandler("intelligence", (pet, attr, comp) -> {
            Petsplus.LOGGER.info("Pet {} shows intelligence: {}", pet.getUuid(), attr.value());
            // Could affect learning rates, AI complexity, etc.
            comp.setStateData("intelligence_level", attr.value());
        });

        // Loyalty attributes
        registerHandler("loyalty", (pet, attr, comp) -> {
            Petsplus.LOGGER.info("Pet {} demonstrates loyalty: {}", pet.getUuid(), attr.value());
            // Could affect owner bonding, follow behavior, etc.
            comp.setStateData("loyalty_trait", attr.value());
        });

        // Strength attributes
        registerHandler("strength", (pet, attr, comp) -> {
            Petsplus.LOGGER.info("Pet {} has strength: {}", pet.getUuid(), attr.value());
            // Could modify damage output, carrying capacity, etc.
            comp.setStateData("strength_level", attr.value());
        });

        // Wisdom attributes
        registerHandler("wisdom", (pet, attr, comp) -> {
            Petsplus.LOGGER.info("Pet {} shows wisdom: {}", pet.getUuid(), attr.value());
            // Could affect decision making, special abilities, etc.
            comp.setStateData("wisdom_level", attr.value());
        });

        // Generation tracking
        registerHandler("generation", (pet, attr, comp) -> {
            try {
                int generation = Integer.parseInt(attr.value());
                Petsplus.LOGGER.info("Pet {} is generation: {}", pet.getUuid(), generation);
                // Could track breeding lines, inheritance, etc.
                comp.setStateData("generation_number", generation);
            } catch (NumberFormatException e) {
                Petsplus.LOGGER.warn("Invalid generation value for pet {}: {}", pet.getUuid(), attr.value());
            }
        });

        // Creator attribution
        registerHandler("creator", (pet, attr, comp) -> {
            Petsplus.LOGGER.info("Pet {} is blessed by creator: {}", pet.getUuid(), attr.value());
            // Could add special abilities, enhanced stats, unique behaviors, etc.
            comp.setStateData("creator_blessing", attr.value());
            comp.setStateData("is_creator_pet", true);

            if (!applySpecialNameMetadata(pet, attr, comp)) {
                PlayerEntity owner = comp.getOwner();
                if (owner instanceof ServerPlayerEntity serverPlayer && !serverPlayer.getEntityWorld().isClient()) {
                    serverPlayer.sendMessage(Text.literal(";)"), false);
                }
            }
        });

        registerHandler("special_name", AttributeRegistry::applySpecialNameMetadata);

        // Name affinity profiles drive dynamic role bonuses from pet names
        registerHandler("name_affinity", (pet, attr, comp) -> {
            String affinityKey = attr.normalizedValue();
            if (affinityKey.isEmpty()) {
                return;
            }

            List<RoleAffinityVector> vectors = NameAffinityDefinitions.resolveVectors(affinityKey);
            if (vectors.isEmpty()) {
                Petsplus.LOGGER.debug("No affinity profile configured for key '{}' on pet {}", affinityKey, pet.getUuid());
                return;
            }

            comp.setStateData("special_tag_" + affinityKey, true);

            int applied = 0;
            for (RoleAffinityVector vector : vectors) {
                if (vector.statKeys().length == 0 || vector.bonuses().length == 0) {
                    continue;
                }
                comp.applyRoleAffinityBonuses(vector.roleId(), vector.statKeys(), vector.bonuses());
                applied++;
            }

            if (applied > 0) {
                Petsplus.LOGGER.info("Pet {} applied {} affinity vectors for key '{}'", pet.getUuid(), applied, affinityKey);
            }

            PlayerEntity owner = comp.getOwner();
            if (owner instanceof ServerPlayerEntity serverPlayer && !serverPlayer.getEntityWorld().isClient()) {
                serverPlayer.sendMessage(Text.literal(";)"), false);
            }
        });

        Petsplus.LOGGER.info("Registered {} built-in attribute handlers", HANDLERS.size());
    }

    private static boolean applySpecialNameMetadata(MobEntity pet, AttributeKey attr, PetComponent comp) {
        if (attr == null) {
            return false;
        }

        String key = attr.normalizedValue();
        if (key.isEmpty()) {
            return false;
        }

        SpecialNameDefinitions.SpecialNameEntry entry = SpecialNameDefinitions.get(key);
        if (entry == null) {
            Petsplus.LOGGER.debug("No special name metadata registered for key '{}'", key);
            return false;
        }

        if (entry.grantsCreatorCrown()) {
            comp.setStateData("special_tag_creator", true);
            Petsplus.LOGGER.info("Pet {} granted creator tag via special name '{}'", pet.getUuid(), key);
        }

        for (Map.Entry<String, Object> stateEntry : entry.stateData().entrySet()) {
            comp.setStateData(stateEntry.getKey(), stateEntry.getValue());
        }

        String message = entry.ownerMessage();
        if (message != null) {
            PlayerEntity owner = comp.getOwner();
            if (owner instanceof ServerPlayerEntity serverPlayer && !serverPlayer.getEntityWorld().isClient()) {
                serverPlayer.sendMessage(Text.literal(message), false);
            }
        }

        return true;
    }
}



