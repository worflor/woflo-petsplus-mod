package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.entity.PetsplusTameable;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.nature.PetNatureSelector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Handles detection and registration of pets for the PetsPlus system.
 */
public class PetDetectionHandler {
    // Track pets awaiting role selection
    private static final Map<MobEntity, PlayerEntity> pendingRoleSelection = new ConcurrentHashMap<>();
    
    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(PetDetectionHandler::onEntityLoad);
        ServerEntityEvents.ENTITY_UNLOAD.register(PetDetectionHandler::onEntityUnload);
        
        Petsplus.LOGGER.info("Pet detection handlers registered");
    }
    
    /**
     * Called when an entity is loaded into the world.
     */
    private static void onEntityLoad(net.minecraft.entity.Entity entity, net.minecraft.server.world.ServerWorld world) {
        if (entity instanceof MobEntity mob) {
            detectAndRegisterPet(mob);
        }
    }
    
    /**
     * Called when an entity is unloaded from the world.
     */
    private static void onEntityUnload(net.minecraft.entity.Entity entity, net.minecraft.server.world.ServerWorld world) {
        if (entity instanceof MobEntity mob) {
            PetComponent.remove(mob);
        }
    }
    
    /**
     * Detect if a mob should be registered as a pet and assign appropriate role.
     */
    private static void detectAndRegisterPet(MobEntity mob) {
        PlayerEntity owner = resolveOwner(mob);
        if (owner == null) {
            return;
        }

        MinecraftServer server = mob.getServer();
        if (server == null) {
            return;
        }

        server.execute(() -> {
            if (!mob.isAlive()) {
                pendingRoleSelection.remove(mob);
                return;
            }

            PlayerEntity resolvedOwner = resolveOwner(mob);
            PlayerEntity promptOwner = resolvedOwner != null ? resolvedOwner : owner;
            if (promptOwner == null) {
                return;
            }

            PetComponent existingComponent = PetComponent.get(mob);
            if (existingComponent != null) {
                if (clearPendingForInheritedRole(mob, existingComponent)) {
                    return;
                }

                Identifier existingRole = existingComponent.getRoleId();
                if (existingRole != null && PetsPlusRegistries.petRoleTypeRegistry().get(existingRole) != null) {
                    pendingRoleSelection.remove(mob);
                    return;
                }
            }

            if (pendingRoleSelection.containsKey(mob)) {
                return;
            }

            promptRoleSelection(mob, promptOwner);
        });
    }

    private static PlayerEntity resolveOwner(MobEntity mob) {
        PlayerEntity owner = null;
        if (mob instanceof PetsplusTameable tameable) {
            if (tameable.petsplus$isTamed() && tameable.petsplus$getOwner() instanceof PlayerEntity player) {
                owner = player;
            }
        }
        if (owner == null && mob.getType().toString().contains("ocelot")) {
            PlayerEntity nearest = mob.getWorld().getClosestPlayer(mob, 8.0);
            if (nearest != null && nearest.isAlive()) owner = nearest;
        }
        if (owner == null && mob instanceof FoxEntity fox) {
            PlayerEntity nearest = mob.getWorld().getClosestPlayer(mob, 8.0);
            if (nearest != null && nearest.isAlive()) {
                boolean trusted = false;
                try {
                    java.lang.reflect.Method m = FoxEntity.class.getMethod("isTrusted", LivingEntity.class);
                    Object res = m.invoke(fox, (LivingEntity) nearest);
                    if (res instanceof Boolean b) trusted = b.booleanValue();
                } catch (NoSuchMethodException ignored) {
                    try {
                        java.lang.reflect.Method m2 = FoxEntity.class.getMethod("isTrustedUuid", java.util.UUID.class);
                        Object res2 = m2.invoke(fox, nearest.getUuid());
                        if (res2 instanceof Boolean b2) trusted = b2.booleanValue();
                    } catch (ReflectiveOperationException ignored2) {
                    }
                } catch (ReflectiveOperationException ignored) {
                }

                boolean leashedToPlayer = fox.isLeashed() && fox.getLeashHolder() == nearest;
                if (trusted || leashedToPlayer) {
                    owner = nearest;
                }
            }
        }

        return owner;
    }

    private static boolean clearPendingForInheritedRole(MobEntity mob, PetComponent component) {
        String inheritedRole = component.getStateData(PetComponent.StateKeys.BREEDING_INHERITED_ROLE, String.class);
        if (inheritedRole == null || inheritedRole.isBlank()) {
            return false;
        }

        pendingRoleSelection.remove(mob);
        Petsplus.LOGGER.info("Skipping manual role prompt for pet {} due to inherited role {}", mob.getUuid(), inheritedRole);
        return true;
    }
    
    /**
     * Prompt the player to select a role for their newly tamed pet.
     */
    private static void promptRoleSelection(MobEntity mob, PlayerEntity owner) {
        // Store the pending pet
        pendingRoleSelection.put(mob, owner);
        
        // Send interactive message header
        owner.sendMessage(Text.literal("🐾 ").formatted(Formatting.GOLD)
            .append(Text.literal("Choose a role for your new pet ").formatted(Formatting.GRAY))
            .append(Text.literal(mob.getType().getName().getString()).formatted(Formatting.AQUA, Formatting.BOLD))
            .append(Text.literal("!").formatted(Formatting.DARK_GRAY)), false);

    // Build and send clickable role lines using tellraw JSON for wide compatibility
    sendClickableRoleLines(owner);

        // Quick suggestion to prefill the command
        if (owner instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            woflo.petsplus.ui.ChatLinks.sendSuggest(
                sp,
                new woflo.petsplus.ui.ChatLinks.Suggest(
                    "Suggest: /petsplus role …",
                    "/petsplus role ",
                    "Click to prefill then type a role or pick a button",
                    "gray",
                    false
                )
            );
        }
    }

    private static void sendClickableRoleLines(PlayerEntity owner) {
        if (!(owner instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return;
        
        java.util.List<woflo.petsplus.ui.ChatLinks.Suggest> suggests = new java.util.ArrayList<>();

        for (PetRoleType type : PetsPlusRegistries.petRoleTypeRegistry()) {
            Identifier id = type.id();
            Text label = resolveRoleLabel(id, type);
            String hover = getRoleDescription(id, type);
            suggests.add(new woflo.petsplus.ui.ChatLinks.Suggest(
                "[" + label.getString() + "]",
                "/petsplus role " + id,
                hover,
                "aqua",
                true
            ));
        }

        if (suggests.isEmpty()) {
            return;
        }

        woflo.petsplus.ui.ChatLinks.sendSuggestRow(
            sp,
            suggests.toArray(new woflo.petsplus.ui.ChatLinks.Suggest[0]),
            3
        );
    }


    /**
     * Get a short description for a pet role.
     */
    private static String getRoleDescription(Identifier roleId, PetRoleType type) {
        return PetRoleType.defaultDescription(roleId);
    }

    private static Text resolveRoleLabel(Identifier roleId, PetRoleType type) {
        Text translated = Text.translatable(type.translationKey());
        if (!translated.getString().equals(type.translationKey())) {
            return translated;
        }
        return Text.literal(PetRoleType.fallbackName(roleId));
    }

    /**
     * Assign a role to a pending pet.
     */
    public static boolean assignPendingRole(PlayerEntity player, MobEntity pet, Identifier roleId) {
        PlayerEntity pendingOwner = pendingRoleSelection.get(pet);
        if (pendingOwner != null && pendingOwner.equals(player)) {
            // Create pet component with the selected role
            PetComponent component = PetComponent.getOrCreate(pet);
            component.setOwner(player);
            component.setRoleId(roleId);

            // Generate unique characteristics for this pet (first time only)
            component.ensureCharacteristics();
            
            // Remove from pending list
            pendingRoleSelection.remove(pet);

            // Confirm to player
            PetRoleType type = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
            Text label = type != null ? resolveRoleLabel(roleId, type) : Text.literal(roleId.toString());
            player.sendMessage(Text.literal("✓ ").formatted(Formatting.GREEN)
                .append(Text.literal("Assigned role ").formatted(Formatting.WHITE))
                .append(label.copy().formatted(Formatting.AQUA))
                .append(Text.literal(" to your pet!").formatted(Formatting.WHITE)), false);
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                woflo.petsplus.ui.ChatLinks.sendSuggestRow(sp, new woflo.petsplus.ui.ChatLinks.Suggest[] {
                    new woflo.petsplus.ui.ChatLinks.Suggest("[XP Info]", "/petsplus xp info", "See your pet's level & progress", "yellow", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Change Role…]", "/petsplus role ", "Click then type a role or pick from buttons", "aqua", true)
                }, 4);
            }

            Petsplus.LOGGER.info("Assigned role {} to pet {} for owner {}",
                roleId, pet.getType().toString(), player.getName().getString());

            return true;
        }
        return false;
    }
    
    /**
     * Get all pets pending role selection for a player.
     */
    public static java.util.List<MobEntity> getPendingPets(PlayerEntity player) {
        return pendingRoleSelection.entrySet().stream()
            .filter(entry -> entry.getValue().equals(player))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Called when an entity is tamed (via mixin hook).
     * This provides immediate detection of newly tamed pets.
     */
    public static void onEntityTamed(MobEntity mob, PlayerEntity owner) {
        if (!(mob instanceof PetsplusTameable tameable) || !tameable.petsplus$isTamed()) {
            return;
        }

        Petsplus.LOGGER.info("Detected newly tamed entity: {} for player {}",
            mob.getType().toString(), owner.getName().getString());

        // Check if this pet already has a role assigned
        PetComponent existingComponent = PetComponent.get(mob);
        boolean hasRegisteredRole = false;
        if (existingComponent != null && !pendingRoleSelection.containsKey(mob)) {
            Identifier roleId = existingComponent.getRoleId();
            hasRegisteredRole = PetsPlusRegistries.petRoleTypeRegistry().get(roleId) != null;
        }

        if (!hasRegisteredRole) {
            // Prompt player for role selection
            promptRoleSelection(mob, owner);
        }

        if (mob.getWorld() instanceof ServerWorld serverWorld) {
            PetComponent component = existingComponent != null ? existingComponent : PetComponent.getOrCreate(mob);
            component.ensureCharacteristics();
            if (component.getNatureId() == null) {
                PetNatureSelector.TameContext context = PetNatureSelector.captureTameContext(serverWorld, mob);
                Identifier wildNature = PetNatureSelector.selectTameNature(mob, context);
                if (wildNature != null) {
                    component.setNatureId(wildNature);
                    component.setStateData(PetComponent.StateKeys.WILD_ASSIGNED_NATURE, wildNature.toString());
                }
            }
        }
    }
    
    /**
     * Manually register a pet with a specific role and owner.
     * Useful for custom pet registration or admin commands.
     */
    public static void registerPet(MobEntity mob, PlayerEntity owner, PetRoleType roleType) {
        Identifier roleId = roleType != null ? roleType.id() : PetRoleType.GUARDIAN_ID;
        registerPet(mob, owner, roleId);
    }

    public static void registerPet(MobEntity mob, PlayerEntity owner, Identifier roleId) {
        PetComponent component = PetComponent.getOrCreate(mob);
        component.setOwner(owner);
        component.setRoleId(roleId);

        // Generate unique characteristics for this pet (first time only)
        component.ensureCharacteristics();
        


        Petsplus.LOGGER.info("Manually registered pet {} with role {} for owner {}",
            mob.getType().toString(), roleId, owner.getName().getString());
    }

    /**
     * Unregister a pet from the PetsPlus system.
     */
    public static void unregisterPet(MobEntity mob) {
        PetComponent.remove(mob);
        Petsplus.LOGGER.debug("Unregistered pet {}", mob.getType().toString());
    }
}