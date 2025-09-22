package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.state.PetComponent;

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
        // Determine owner: prefer true tamed owner, but optionally allow trusted/leashed via config
        PlayerEntity owner = null;
        if (mob instanceof TameableEntity tameable) {
            if (tameable.isTamed() && tameable.getOwner() instanceof PlayerEntity player) {
                owner = player;
            }
        }
        // Ocelots (trusted): they don't set an owner but can trust players; use a proximity heuristic
        if (owner == null && mob.getType().toString().contains("ocelot")) {
            PlayerEntity nearest = mob.getWorld().getClosestPlayer(mob, 8.0);
            if (nearest != null && nearest.isAlive()) owner = nearest;
        }
        // Foxes: treat nearest player within small range as trusted owner; leash not required
        if (owner == null && mob.getType().toString().contains("fox")) {
            PlayerEntity nearest = mob.getWorld().getClosestPlayer(mob, 8.0);
            if (nearest != null && nearest.isAlive()) owner = nearest;
        }
        // Tamed mounts (horses, donkeys, llamas) are handled through TameableEntity above in current versions
        
        // If no owner found, skip registration
        if (owner == null) {
            return;
        }
        
        // Check if this pet already has a role assigned
        PetComponent existingComponent = PetComponent.get(mob);
        if (existingComponent != null && existingComponent.getRole() != null) {
            return; // Already registered
        }
        
        // Prompt player for role selection
        promptRoleSelection(mob, owner);
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
            .append(Text.literal(mob.getType().getName().getString()).formatted(Formatting.AQUA).formatted(Formatting.BOLD))
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
        
        // Create an array of role suggestions using the ChatLinks helper
        woflo.petsplus.ui.ChatLinks.Suggest[] suggests = new woflo.petsplus.ui.ChatLinks.Suggest[PetRole.values().length];
        int i = 0;
        for (PetRole role : PetRole.values()) {
            suggests[i] = new woflo.petsplus.ui.ChatLinks.Suggest(
                "[" + role.getDisplayName() + "]",
                "/petsplus role " + role.getKey(),
                getRoleDescription(role),
                "aqua",
                true
            );
            i++;
        }
        
        // Send the suggestions in rows of 3 for better readability
        woflo.petsplus.ui.ChatLinks.sendSuggestRow(sp, suggests, 3);
    }

    
    /**
     * Get a short description for a pet role.
     */
    private static String getRoleDescription(PetRole role) {
        return switch (role) {
            case GUARDIAN -> "Defensive tank, protects owner";
            case STRIKER -> "Aggressive damage dealer";
            case SCOUT -> "Fast explorer, utility abilities";
            case SKYRIDER -> "Aerial support, mobility";
            case SUPPORT -> "Healing and buffs";
            case ENCHANTMENT_BOUND -> "Magic-focused abilities";
            case CURSED_ONE -> "Dark magic, high risk/reward";
            case EEPY_EEPER -> "Sleep-based abilities";
            case ECLIPSED -> "Shadow magic, stealth";
        };
    }
    
    /**
     * Assign a role to a pending pet.
     */
    public static boolean assignPendingRole(PlayerEntity player, MobEntity pet, PetRole role) {
        PlayerEntity pendingOwner = pendingRoleSelection.get(pet);
        if (pendingOwner != null && pendingOwner.equals(player)) {
            // Create pet component with the selected role
            PetComponent component = PetComponent.getOrCreate(pet);
            component.setOwner(player);
            component.setRole(role);
            
            // Generate unique characteristics for this pet (first time only)
            component.ensureCharacteristics();
            
            // Remove from pending list
            pendingRoleSelection.remove(pet);
            
            // Confirm to player
            player.sendMessage(Text.literal("✓ ").formatted(Formatting.GREEN)
                .append(Text.literal("Assigned role ").formatted(Formatting.WHITE))
                .append(Text.literal(role.getDisplayName()).formatted(Formatting.AQUA))
                .append(Text.literal(" to your pet!").formatted(Formatting.WHITE)), false);
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                woflo.petsplus.ui.ChatLinks.sendSuggestRow(sp, new woflo.petsplus.ui.ChatLinks.Suggest[] {
                    new woflo.petsplus.ui.ChatLinks.Suggest("[XP Info]", "/petsplus xp info", "See your pet's level & progress", "yellow", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Change Role…]", "/petsplus role ", "Click then type a role or pick from buttons", "aqua", true)
                }, 4);
            }
                
            Petsplus.LOGGER.info("Assigned role {} to pet {} for owner {}", 
                role, pet.getType().toString(), player.getName().getString());
            
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
     * Determine the appropriate role for a pet based on its type and characteristics.
     */
    @SuppressWarnings("unused")
    private static PetRole determinePetRole(MobEntity mob) {
        // This is a basic implementation - could be made more sophisticated
        // or data-driven through configuration files
        
        String mobType = mob.getType().toString();
        
        // Guardian roles - tanky mobs
        if (mobType.contains("iron_golem") || 
            mobType.contains("snow_golem") ||
            mobType.contains("turtle")) {
            return PetRole.GUARDIAN;
        }
        
        // Striker roles - aggressive mobs
        if (mobType.contains("wolf") ||
            mobType.contains("cat") ||
            mobType.contains("ocelot")) {
            return PetRole.STRIKER;
        }
        
        // Scout roles - fast/utility mobs
        if (mobType.contains("horse") ||
            mobType.contains("donkey") ||
            mobType.contains("llama") ||
            mobType.contains("fox")) {
            return PetRole.SCOUT;
        }
        
        // Skyrider roles - flying mobs
        if (mobType.contains("parrot") ||
            mobType.contains("bee") ||
            mobType.contains("phantom")) {
            return PetRole.SKYRIDER;
        }
        
        // Support roles - passive utility mobs
        if (mobType.contains("sheep") ||
            mobType.contains("cow") ||
            mobType.contains("pig")) {
            return PetRole.SUPPORT;
        }
        
        // Default to Guardian for unknown types
        return PetRole.GUARDIAN;
    }
    
    /**
     * Called when an entity is tamed (via mixin hook).
     * This provides immediate detection of newly tamed pets.
     */
    public static void onEntityTamed(TameableEntity tameable, PlayerEntity owner) {
        Petsplus.LOGGER.info("Detected newly tamed entity: {} for player {}", 
            tameable.getType().toString(), owner.getName().getString());
        
        // Check if this pet already has a role assigned
        PetComponent existingComponent = PetComponent.get(tameable);
        if (existingComponent != null && existingComponent.getRole() != null) {
            return; // Already registered
        }
        
        // Prompt player for role selection
        promptRoleSelection(tameable, owner);
    }
    
    /**
     * Manually register a pet with a specific role and owner.
     * Useful for custom pet registration or admin commands.
     */
    public static void registerPet(MobEntity mob, PlayerEntity owner, PetRole role) {
        PetComponent component = PetComponent.getOrCreate(mob);
        component.setOwner(owner);
        component.setRole(role);
        
        // Generate unique characteristics for this pet (first time only)
        component.ensureCharacteristics();

        Petsplus.LOGGER.info("Manually registered pet {} with role {} for owner {}", 
            mob.getType().toString(), role, owner.getName().getString());
    }    /**
     * Unregister a pet from the PetsPlus system.
     */
    public static void unregisterPet(MobEntity mob) {
        PetComponent.remove(mob);
        Petsplus.LOGGER.debug("Unregistered pet {}", mob.getType().toString());
    }
}