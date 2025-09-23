package woflo.petsplus.abilities;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Ability;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.event.AbilityActivationEvent;
import woflo.petsplus.api.registry.AbilityType;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.state.PetComponent;

import java.util.*;

/**
 * Manages all abilities and their activation for pets.
 */
public class AbilityManager {
    private static final Map<Identifier, List<Ability>> ROLE_ABILITIES = new HashMap<>();
    private static final Map<Identifier, Ability> ALL_ABILITIES = new HashMap<>();

    /**
     * Initialize the ability system with default abilities.
     */
    public static void initialize() {
        reloadFromRegistry();
    }

    /**
     * Rebuilds cached ability instances from the active registry contents.
     */
    public static synchronized void reloadFromRegistry() {
        ROLE_ABILITIES.clear();
        ALL_ABILITIES.clear();

        Map<Identifier, Ability> instantiated = new HashMap<>();
        for (AbilityType type : PetsPlusRegistries.abilityTypeRegistry()) {
            Ability ability = type.createAbility();
            if (ability == null) {
                Petsplus.LOGGER.warn("Registry ability {} returned null instance", type.id());
                continue;
            }
            instantiated.put(type.id(), ability);
            ALL_ABILITIES.put(type.id(), ability);
        }

        for (PetRoleType roleType : PetsPlusRegistries.petRoleTypeRegistry()) {
            List<Ability> loadout = new ArrayList<>();
            for (Identifier abilityId : roleType.defaultAbilities()) {
                Ability ability = instantiated.get(abilityId);
                if (ability == null) {
                    Petsplus.LOGGER.error("Role {} references unknown ability {}", roleType.id(), abilityId);
                    continue;
                }
                loadout.add(ability);
            }
            ROLE_ABILITIES.put(roleType.id(), List.copyOf(loadout));
        }

        if (ALL_ABILITIES.isEmpty()) {
            Petsplus.LOGGER.debug("Ability registry reload completed with no abilities available.");
        } else {
            Petsplus.LOGGER.info("Loaded {} abilities across {} roles", ALL_ABILITIES.size(), ROLE_ABILITIES.size());
        }
    }

    /**
     * Trigger abilities for a pet based on the given context.
     */
    public static void triggerAbilities(MobEntity pet, TriggerContext context) {
        if (pet == null) return;

        PetComponent component = PetComponent.get(pet);
        if (component == null) return;

        Identifier roleId = component.getRoleId();
        List<Ability> abilities = ROLE_ABILITIES.get(roleId);
        if (abilities == null || abilities.isEmpty()) {
            if (PetsPlusRegistries.petRoleTypeRegistry().get(roleId) == null) {
                Petsplus.LOGGER.warn("Pet {} has role {} without a registered definition; skipping ability triggers.", pet.getUuid(), roleId);
            }
            return;
        }

        // Create a pet-aware context that preserves event data
        TriggerContext petContext = new TriggerContext(
            context.getWorld(),
            pet,
            context.getOwner(),
            context.getEventType()
        );
        for (var entry : context.getEventData().entrySet()) {
            petContext.withData(entry.getKey(), entry.getValue());
        }

        PetRoleType roleType = component.getRoleType(false);

        for (Ability ability : abilities) {
            String cooldownKey = ability.getId().toString();
            boolean onCooldown = component.isOnCooldown(cooldownKey);
            int baseCooldown = ability.getTrigger().getInternalCooldownTicks();

            AbilityActivationEvent.Context eventContext = new AbilityActivationEvent.Context(
                ability,
                petContext,
                component,
                roleType,
                onCooldown,
                baseCooldown
            );

            AbilityActivationEvent.firePre(eventContext);

            if (eventContext.isCancelled()) {
                continue;
            }

            if (onCooldown && !eventContext.shouldBypassCooldown()) {
                continue;
            }

            boolean succeeded;
            if (eventContext.shouldRunDefaultExecution()) {
                succeeded = ability.tryActivate(petContext);
                eventContext.setSucceeded(succeeded);
            } else {
                succeeded = eventContext.didSucceed();
            }

            AbilityActivationEvent.firePost(eventContext);

            boolean applyCooldown = eventContext.shouldApplyCooldown()
                && (eventContext.didSucceed() || eventContext.shouldApplyCooldownOnFailure());

            if (applyCooldown) {
                int cooldown = eventContext.getCooldownTicks();
                if (cooldown > 0) {
                    component.setCooldown(cooldownKey, cooldown);
                } else {
                    component.clearCooldown(cooldownKey);
                }
            }
        }
    }

    /**
     * Get all abilities for a specific role.
     */
    public static List<Ability> getAbilitiesForRole(Identifier roleId) {
        return ROLE_ABILITIES.getOrDefault(roleId, Collections.emptyList());
    }

    /**
     * Get a specific ability by ID.
     */
    public static Ability getAbility(Identifier id) {
        return ALL_ABILITIES.get(id);
    }

    /**
     * Trigger an ability for testing purposes.
     */
    public static boolean triggerAbilityForTest(String abilityId, net.minecraft.server.network.ServerPlayerEntity player) {
        try {
            Identifier id = Identifier.of("petsplus", abilityId);
            Ability ability = ALL_ABILITIES.get(id);

            if (ability != null) {
                Petsplus.LOGGER.info("Test triggered ability: {}", abilityId);
                return true;
            }

            return false;
        } catch (Exception e) {
            Petsplus.LOGGER.error("Error triggering ability for test", e);
            return false;
        }
    }
}
