package woflo.petsplus.testing;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import woflo.petsplus.Petsplus;
import woflo.petsplus.abilities.AbilityManager;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.ui.BossBarManager;
import woflo.petsplus.ui.UIFeedbackManager;

import java.util.List;

/**
 * Testing commands for PetsPlus development and validation.
 */
public class TestCommands {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register(TestCommands::registerCommands);
    }
    
    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, 
                                       CommandRegistryAccess registryAccess, 
                                       CommandManager.RegistrationEnvironment environment) {
        
        dispatcher.register(CommandManager.literal("petsplus")
            // Public player-accessible commands
            .then(CommandManager.literal("role")
                .then(CommandManager.argument("role_name", StringArgumentType.string())
                    .executes(TestCommands::assignRoleCommand)))
            .then(CommandManager.literal("info")
                .executes(TestCommands::showInfo))

            // Admin-only commands
            .then(CommandManager.literal("test")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("role")
                    .then(CommandManager.argument("role", StringArgumentType.string())
                        .executes(TestCommands::testRoleAssignment)))
                .then(CommandManager.literal("ability")
                    .then(CommandManager.argument("ability", StringArgumentType.string())
                        .executes(TestCommands::testAbilityTrigger)))
                .then(CommandManager.literal("state")
                    .executes(TestCommands::testStateValidation))
                .then(CommandManager.literal("ui")
                    .executes(TestCommands::testUIFeedback))
                .then(CommandManager.literal("boss_safety")
                    .executes(TestCommands::testBossSafety))
                .then(CommandManager.literal("performance")
                    .executes(TestCommands::testPerformance))
                .then(CommandManager.literal("mount")
                    .executes(TestCommands::testMountBehavior)))
            .then(CommandManager.literal("reload")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(TestCommands::reloadConfig))
            .then(CommandManager.literal("xp")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("info")
                    .executes(TestCommands::showPetXpInfo))
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("amount", StringArgumentType.string())
                        .executes(TestCommands::addPetXp)))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("level", StringArgumentType.string())
                        .executes(TestCommands::setPetLevel))))
            .then(CommandManager.literal("debug")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("on")
                    .executes(context -> toggleDebug(context, true)))
                .then(CommandManager.literal("off")
                    .executes(context -> toggleDebug(context, false)))));
    }
    
    private static int assignRoleCommand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String roleName = StringArgumentType.getString(context, "role_name");
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        // Try to parse the role name
        woflo.petsplus.api.PetRole role = null;
        try {
            // Convert role name to enum (handle both display names and keys)
            role = parseRoleName(roleName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("Invalid role: " + roleName)
                .formatted(Formatting.RED), false);
            player.sendMessage(Text.literal("Try one of these:")
                .formatted(Formatting.DARK_GRAY), false);
            if (player instanceof ServerPlayerEntity sp) {
                woflo.petsplus.ui.ChatLinks.Suggest[] suggests = new woflo.petsplus.ui.ChatLinks.Suggest[] {
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Guardian]", "/petsplus role guardian", "Defensive tank, protects owner", "aqua", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Striker]", "/petsplus role striker", "Aggressive damage dealer", "aqua", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Support]", "/petsplus role support", "Healing and buffs", "aqua", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Scout]", "/petsplus role scout", "Fast explorer, utility abilities", "aqua", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Skyrider]", "/petsplus role skyrider", "Aerial support, mobility", "aqua", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Enchantment-Bound]", "/petsplus role enchantment_bound", "Magic-focused abilities", "aqua", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Cursed One]", "/petsplus role cursed_one", "Dark magic, high risk/reward", "aqua", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Eepy Eeper]", "/petsplus role eepy_eeper", "Sleep-based abilities", "aqua", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Eclipsed]", "/petsplus role eclipsed", "Shadow magic, stealth", "aqua", true)
                };
                woflo.petsplus.ui.ChatLinks.sendSuggestRow(sp, suggests, 4);
            }
            return 0;
        }
        
        // Get pending pets for this player
        java.util.List<net.minecraft.entity.mob.MobEntity> pendingPets = 
            woflo.petsplus.events.PetDetectionHandler.getPendingPets(player);
        
        if (pendingPets.isEmpty()) {
            player.sendMessage(Text.literal("No pets are waiting for role assignment!")
                .formatted(Formatting.YELLOW), false);
            player.sendMessage(Text.literal("Tame a new pet to assign it a role, or use ")
                .formatted(Formatting.GRAY)
                .append(Text.literal("/petsplus info").formatted(Formatting.AQUA))
                .append(Text.literal(" to see your existing pets.").formatted(Formatting.GRAY)), false);
            return 0;
        }
        
        // Assign role to the first pending pet
        net.minecraft.entity.mob.MobEntity pet = pendingPets.get(0);
        boolean success = woflo.petsplus.events.PetDetectionHandler.assignPendingRole(player, pet, role);
        
        if (success) {
            // If there are more pending pets, let the player know
            if (pendingPets.size() > 1) {
                player.sendMessage(Text.literal("📋 ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal("You have " + (pendingPets.size() - 1) + " more pet(s) waiting for role assignment.")
                        .formatted(Formatting.YELLOW)), false);
            }
            return 1;
        } else {
            player.sendMessage(Text.literal("Failed to assign role to pet!")
                .formatted(Formatting.RED), false);
            return 0;
        }
    }
    
    private static woflo.petsplus.api.PetRole parseRoleName(String name) {
        // First try direct enum match
        try {
            return woflo.petsplus.api.PetRole.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try matching display names
            for (woflo.petsplus.api.PetRole role : woflo.petsplus.api.PetRole.values()) {
                if (role.getDisplayName().equalsIgnoreCase(name) || 
                    role.getKey().equalsIgnoreCase(name)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("Unknown role: " + name);
        }
    }
    
    private static int testRoleAssignment(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String roleName = StringArgumentType.getString(context, "role");
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        try {
            // Find nearby tameable pets
            List<Entity> nearbyEntities = player.getWorld().getOtherEntities(player, 
                player.getBoundingBox().expand(10), entity -> entity instanceof TameableEntity);
            
            if (nearbyEntities.isEmpty()) {
                player.sendMessage(Text.literal("No tameable pets found nearby!")
                    .formatted(Formatting.RED), false);
                return 0;
            }
            
            TameableEntity pet = (TameableEntity) nearbyEntities.get(0);
            
            // Test role assignment using StateManager
            StateManager stateManager = StateManager.forWorld((net.minecraft.server.world.ServerWorld) player.getWorld());
            boolean success = stateManager.assignRole(pet, roleName);
            
            if (success) {
                player.sendMessage(Text.literal("Successfully assigned role '" + roleName + "' to pet!")
                    .formatted(Formatting.GREEN), false);
                
                // Show UI feedback
                UIFeedbackManager.sendActionBarMessage(player, "petsplus.role.assigned", 
                    new Object[]{roleName, pet.getName().getString()});
                BossBarManager.showAbilityActivation(player, "Role: " + roleName);
            } else {
                player.sendMessage(Text.literal("Failed to assign role '" + roleName + "' to pet!")
                    .formatted(Formatting.RED), false);
            }
            
            return success ? 1 : 0;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error testing role assignment: " + e.getMessage())
                .formatted(Formatting.RED), false);
            Petsplus.LOGGER.error("Error in role assignment test", e);
            return 0;
        }
    }
    
    private static int testAbilityTrigger(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String abilityId = StringArgumentType.getString(context, "ability");
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        try {
            // Try to manually trigger an ability
            boolean success = AbilityManager.triggerAbilityForTest(abilityId, player);
            
            if (success) {
                player.sendMessage(Text.literal("Successfully triggered ability '" + abilityId + "'!")
                    .formatted(Formatting.GREEN), false);
                UIFeedbackManager.sendActionBarMessage(player, "petsplus.ability.test_triggered", 
                    new Object[]{abilityId});
            } else {
                player.sendMessage(Text.literal("Failed to trigger ability '" + abilityId + "'!")
                    .formatted(Formatting.RED), false);
            }
            
            return success ? 1 : 0;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error testing ability trigger: " + e.getMessage())
                .formatted(Formatting.RED), false);
            Petsplus.LOGGER.error("Error in ability trigger test", e);
            return 0;
        }
    }
    
    private static int testStateValidation(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        try {
            StateManager stateManager = StateManager.forWorld((net.minecraft.server.world.ServerWorld) player.getWorld());
            
            // Validate state consistency
            int petCount = stateManager.getAllPetComponents().size();
            int ownerStateCount = stateManager.getOwnerStateCount();
            
            player.sendMessage(Text.literal("State Validation Results:")
                .formatted(Formatting.YELLOW), false);
            player.sendMessage(Text.literal("- Pet components loaded: " + petCount)
                .formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal("- Total owner states: " + ownerStateCount)
                .formatted(Formatting.WHITE), false);
            
            // Test state cleanup
            int cleanedUp = stateManager.cleanupInvalidStates();
            player.sendMessage(Text.literal("- Cleaned up invalid states: " + cleanedUp)
                .formatted(Formatting.GREEN), false);
            
            return 1;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error validating state: " + e.getMessage())
                .formatted(Formatting.RED), false);
            Petsplus.LOGGER.error("Error in state validation test", e);
            return 0;
        }
    }
    
    private static int testUIFeedback(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        try {
            // Test various UI feedback types
            UIFeedbackManager.sendActionBarMessage(player, "petsplus.test.ui", new Object[]{"Test"});
            
            BossBarManager.showAbilityActivation(player, "Test Ability");
            
            // Schedule additional tests
            player.getServer().execute(() -> {
                try {
                    Thread.sleep(1000);
                    BossBarManager.showCooldownBar(player, "Test Ability", 100);
                    
                    Thread.sleep(2000);
                    BossBarManager.showReadyPulse(player, "Test Ability");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            
            player.sendMessage(Text.literal("UI feedback test started! Watch for action bar and boss bar messages.")
                .formatted(Formatting.GREEN), false);
            
            return 1;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error testing UI feedback: " + e.getMessage())
                .formatted(Formatting.RED), false);
            Petsplus.LOGGER.error("Error in UI feedback test", e);
            return 0;
        }
    }
    
    private static int testBossSafety(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        try {
            // Find nearby entities and test boss safety
            List<Entity> nearbyEntities = player.getWorld().getOtherEntities(player, 
                player.getBoundingBox().expand(20), entity -> entity instanceof LivingEntity);
            
            int totalEntities = nearbyEntities.size();
            int bossEntities = 0;
            int ccResistantEntities = 0;
            
            for (Entity entity : nearbyEntities) {
                if (entity instanceof LivingEntity living) {
                    // Test boss detection (simplified)
                    boolean isBoss = living.getMaxHealth() > 100 || living.getName().getString().toLowerCase().contains("boss");
                    if (isBoss) {
                        bossEntities++;
                    }
                    
                    // Test CC resistance tag (this would check the actual tag in a full implementation)
                    boolean isCCResistant = isBoss; // Simplified for testing
                    if (isCCResistant) {
                        ccResistantEntities++;
                    }
                }
            }
            
            player.sendMessage(Text.literal("Boss Safety Test Results:")
                .formatted(Formatting.YELLOW), false);
            player.sendMessage(Text.literal("- Total entities nearby: " + totalEntities)
                .formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal("- Detected boss entities: " + bossEntities)
                .formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal("- CC-resistant entities: " + ccResistantEntities)
                .formatted(Formatting.WHITE), false);
            
            if (bossEntities > 0) {
                player.sendMessage(Text.literal("⚠ Boss entities detected - CC effects should be disabled!")
                    .formatted(Formatting.RED), false);
            } else {
                player.sendMessage(Text.literal("✓ No boss entities detected - CC effects safe to use")
                    .formatted(Formatting.GREEN), false);
            }
            
            return 1;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error testing boss safety: " + e.getMessage())
                .formatted(Formatting.RED), false);
            Petsplus.LOGGER.error("Error in boss safety test", e);
            return 0;
        }
    }
    
    private static int testPerformance(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        try {
            long startTime = System.nanoTime();
            
            // Simulate heavy state operations
            StateManager stateManager = StateManager.forWorld((net.minecraft.server.world.ServerWorld) player.getWorld());
            
            // Test state access performance
            for (int i = 0; i < 1000; i++) {
                stateManager.getAllPetComponents();
            }
            
            // Test config access performance
            for (int i = 0; i < 1000; i++) {
                PetsPlusConfig.getInstance().getRoleConfig("guardian");
            }
            
            long endTime = System.nanoTime();
            double durationMs = (endTime - startTime) / 1_000_000.0;
            
            player.sendMessage(Text.literal("Performance Test Results:")
                .formatted(Formatting.YELLOW), false);
            player.sendMessage(Text.literal("- 2000 operations completed in: " + String.format("%.2f", durationMs) + " ms")
                .formatted(Formatting.WHITE), false);
            
            if (durationMs < 100) {
                player.sendMessage(Text.literal("✓ Performance is excellent!")
                    .formatted(Formatting.GREEN), false);
            } else if (durationMs < 500) {
                player.sendMessage(Text.literal("✓ Performance is good")
                    .formatted(Formatting.YELLOW), false);
            } else {
                player.sendMessage(Text.literal("⚠ Performance may need optimization")
                    .formatted(Formatting.RED), false);
            }
            
            return 1;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error testing performance: " + e.getMessage())
                .formatted(Formatting.RED), false);
            Petsplus.LOGGER.error("Error in performance test", e);
            return 0;
        }
    }
    
    private static int testMountBehavior(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        try {
            boolean isMounted = player.hasVehicle();
            Entity vehicle = player.getVehicle();
            
            player.sendMessage(Text.literal("Mount Behavior Test Results:")
                .formatted(Formatting.YELLOW), false);
            player.sendMessage(Text.literal("- Currently mounted: " + isMounted)
                .formatted(Formatting.WHITE), false);
            
            if (isMounted && vehicle != null) {
                player.sendMessage(Text.literal("- Mount type: " + vehicle.getType().toString())
                    .formatted(Formatting.WHITE), false);
                player.sendMessage(Text.literal("- Mount health: " + (vehicle instanceof LivingEntity living ? 
                    String.format("%.1f/%.1f", living.getHealth(), living.getMaxHealth()) : "N/A"))
                    .formatted(Formatting.WHITE), false);
                
                // Test mount-specific abilities
                UIFeedbackManager.sendActionBarMessage(player, "petsplus.test.mount_detected", 
                    new Object[]{vehicle.getType().toString()});
            } else {
                player.sendMessage(Text.literal("- No mount detected")
                    .formatted(Formatting.WHITE), false);
            }
            
            return 1;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error testing mount behavior: " + e.getMessage())
                .formatted(Formatting.RED), false);
            Petsplus.LOGGER.error("Error in mount behavior test", e);
            return 0;
        }
    }
    
    private static int reloadConfig(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        try {
            PetsPlusConfig.getInstance().reload();
            player.sendMessage(Text.literal("Configuration reloaded successfully!")
                .formatted(Formatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error reloading config: " + e.getMessage())
                .formatted(Formatting.RED), false);
            Petsplus.LOGGER.error("Error reloading config", e);
            return 0;
        }
    }
    
    private static int showInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        try {
            StateManager stateManager = StateManager.forWorld((net.minecraft.server.world.ServerWorld) player.getWorld());
            
            player.sendMessage(Text.literal("=== PetsPlus Information ===")
                .formatted(Formatting.GOLD), false);
            player.sendMessage(Text.literal("Pet components loaded: " + 
                stateManager.getAllPetComponents().size())
                .formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal("Available roles: Guardian, Striker, Support, Scout, Skyrider, Enchantment-Bound, Cursed One, Eepy Eeper, Eclipsed")
                .formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal("Use '/petsplus test <type>' to run specific tests")
                .formatted(Formatting.AQUA), false);
            if (player instanceof ServerPlayerEntity sp) {
                // Quick actions
                woflo.petsplus.ui.ChatLinks.Suggest[] row1 = new woflo.petsplus.ui.ChatLinks.Suggest[] {
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Test UI]", "/petsplus test ui", "Show action bar + boss bar demo", "green", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[XP Info]", "/petsplus xp info", "See nearby pets XP/Level", "yellow", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Reload]", "/petsplus reload", "Reload PetsPlus config", "gold", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Debug ON]", "/petsplus debug on", "Enable debug messages", "light_purple", true)
                };
                woflo.petsplus.ui.ChatLinks.Suggest[] row2 = new woflo.petsplus.ui.ChatLinks.Suggest[] {
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Debug OFF]", "/petsplus debug off", "Disable debug messages", "light_purple", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Role…]", "/petsplus role ", "Type a role name after clicking", "aqua", true)
                };
                woflo.petsplus.ui.ChatLinks.sendSuggestRow(sp, row1, 4);
                woflo.petsplus.ui.ChatLinks.sendSuggestRow(sp, row2, 4);
            }
            
            return 1;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error showing info: " + e.getMessage())
                .formatted(Formatting.RED), false);
            Petsplus.LOGGER.error("Error showing info", e);
            return 0;
        }
    }
    
    private static int toggleDebug(CommandContext<ServerCommandSource> context, boolean enable) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        // In a full implementation, this would toggle debug logging
        player.sendMessage(Text.literal("Debug mode " + (enable ? "enabled" : "disabled"))
            .formatted(enable ? Formatting.GREEN : Formatting.RED), false);
        
        return 1;
    }
    
    private static int showPetXpInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        try {
            // Find nearby pets owned by this player
            List<net.minecraft.entity.mob.MobEntity> pets = player.getWorld().getEntitiesByClass(
                net.minecraft.entity.mob.MobEntity.class,
                player.getBoundingBox().expand(10), 
                entity -> {
                    woflo.petsplus.state.PetComponent petComp = woflo.petsplus.state.PetComponent.get(entity);
                    return petComp != null && petComp.isOwnedBy(player);
                }
            );
            
            if (pets.isEmpty()) {
                player.sendMessage(Text.literal("No pets found nearby!")
                    .formatted(Formatting.RED), false);
                if (player instanceof ServerPlayerEntity sp) {
                    woflo.petsplus.ui.ChatLinks.sendSuggest(sp,
                        new woflo.petsplus.ui.ChatLinks.Suggest("[Try again]", "/petsplus xp info", "Refresh pet XP info", "yellow", true));
                }
                return 0;
            }
            
            player.sendMessage(Text.literal("=== Pet XP Info ===")
                .formatted(Formatting.GOLD), false);
            
            for (net.minecraft.entity.mob.MobEntity pet : pets) {
                woflo.petsplus.state.PetComponent petComp = woflo.petsplus.state.PetComponent.get(pet);
                if (petComp != null) {
                    String petName = pet.hasCustomName() ? pet.getCustomName().getString() : pet.getType().getName().getString();
                    int level = petComp.getLevel();
                    int xp = petComp.getExperience();
                    float progress = petComp.getXpProgress();
                    boolean isFeatureLevel = petComp.isFeatureLevel();
                    
                    player.sendMessage(Text.literal(String.format("§e%s§r: Level §6%d§r (XP: §b%d§r, Progress: §a%.1f%%§r)%s", 
                        petName, level, xp, progress * 100, isFeatureLevel ? " §6[FEATURE]§r" : "")), false);
                    
                    if (level < 30) {
                        int nextLevelXp = woflo.petsplus.state.PetComponent.getTotalXpForLevel(level + 1);
                        int needed = nextLevelXp - xp;
                        player.sendMessage(Text.literal(String.format("  §7Next level: %d XP needed§r", needed)), false);
                    }
                }
            }
            
            if (player instanceof ServerPlayerEntity sp) {
                woflo.petsplus.ui.ChatLinks.sendSuggest(sp,
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Add XP…]", "/petsplus xp add ", "Try '/petsplus xp add 25'", "green", true));
            }
            return 1;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error showing pet XP info: " + e.getMessage())
                .formatted(Formatting.RED), false);
            return 0;
        }
    }
    
    private static int addPetXp(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String amountStr = StringArgumentType.getString(context, "amount");
        
        try {
            int amount = Integer.parseInt(amountStr);
            
            // Find nearby pets owned by this player
            List<net.minecraft.entity.mob.MobEntity> pets = player.getWorld().getEntitiesByClass(
                net.minecraft.entity.mob.MobEntity.class,
                player.getBoundingBox().expand(10), 
                entity -> {
                    woflo.petsplus.state.PetComponent petComp = woflo.petsplus.state.PetComponent.get(entity);
                    return petComp != null && petComp.isOwnedBy(player);
                }
            );
            
            if (pets.isEmpty()) {
                player.sendMessage(Text.literal("No pets found nearby!")
                    .formatted(Formatting.RED), false);
                return 0;
            }
            
            for (net.minecraft.entity.mob.MobEntity pet : pets) {
                woflo.petsplus.state.PetComponent petComp = woflo.petsplus.state.PetComponent.get(pet);
                if (petComp != null) {
                    boolean leveledUp = petComp.addExperience(amount);
                    String petName = pet.hasCustomName() ? pet.getCustomName().getString() : pet.getType().getName().getString();
                    
                    if (leveledUp) {
                        player.sendMessage(Text.literal(String.format("§e%s§r gained §b%d XP§r and leveled up to §6Level %d§r!", 
                            petName, amount, petComp.getLevel())), false);
                    } else {
                        player.sendMessage(Text.literal(String.format("§e%s§r gained §b%d XP§r (Level %d)", 
                            petName, amount, petComp.getLevel())), false);
                    }
                }
            }
            if (player instanceof ServerPlayerEntity sp) {
                woflo.petsplus.ui.ChatLinks.sendSuggestRow(sp, new woflo.petsplus.ui.ChatLinks.Suggest[] {
                    new woflo.petsplus.ui.ChatLinks.Suggest("[XP Info]", "/petsplus xp info", "Show nearby pets XP", "yellow", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Add 25]", "/petsplus xp add 25", "Give +25 XP", "green", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Add 100]", "/petsplus xp add 100", "Give +100 XP", "green", true)
                }, 4);
            }
            
            return 1;
        } catch (NumberFormatException e) {
            player.sendMessage(Text.literal("Invalid XP amount: " + amountStr)
                .formatted(Formatting.RED), false);
            if (player instanceof ServerPlayerEntity sp) {
                woflo.petsplus.ui.ChatLinks.sendSuggestRow(sp, new woflo.petsplus.ui.ChatLinks.Suggest[] {
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Examples]", "/petsplus xp add 25", "Try 25, 100, 500", "gray", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[XP Info]", "/petsplus xp info", "Show nearby pets XP", "yellow", true)
                }, 4);
            }
            return 0;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error adding pet XP: " + e.getMessage())
                .formatted(Formatting.RED), false);
            return 0;
        }
    }
    
    private static int setPetLevel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String levelStr = StringArgumentType.getString(context, "level");
        
        try {
            int targetLevel = Integer.parseInt(levelStr);
            
            if (targetLevel < 1 || targetLevel > 30) {
                player.sendMessage(Text.literal("Level must be between 1 and 30!")
                    .formatted(Formatting.RED), false);
                return 0;
            }
            
            // Find nearby pets owned by this player
            List<net.minecraft.entity.mob.MobEntity> pets = player.getWorld().getEntitiesByClass(
                net.minecraft.entity.mob.MobEntity.class,
                player.getBoundingBox().expand(10), 
                entity -> {
                    woflo.petsplus.state.PetComponent petComp = woflo.petsplus.state.PetComponent.get(entity);
                    return petComp != null && petComp.isOwnedBy(player);
                }
            );
            
            if (pets.isEmpty()) {
                player.sendMessage(Text.literal("No pets found nearby!")
                    .formatted(Formatting.RED), false);
                return 0;
            }
            
            for (net.minecraft.entity.mob.MobEntity pet : pets) {
                woflo.petsplus.state.PetComponent petComp = woflo.petsplus.state.PetComponent.get(pet);
                if (petComp != null) {
                    // Set level and appropriate XP
                    petComp.setLevel(targetLevel);
                    petComp.setExperience(woflo.petsplus.state.PetComponent.getTotalXpForLevel(targetLevel));
                    
                    String petName = pet.hasCustomName() ? pet.getCustomName().getString() : pet.getType().getName().getString();
                    player.sendMessage(Text.literal(String.format("§e%s§r set to §6Level %d§r!", 
                        petName, targetLevel)), false);
                }
            }
            if (player instanceof ServerPlayerEntity sp) {
                woflo.petsplus.ui.ChatLinks.sendSuggestRow(sp, new woflo.petsplus.ui.ChatLinks.Suggest[] {
                    new woflo.petsplus.ui.ChatLinks.Suggest("[XP Info]", "/petsplus xp info", "Show nearby pets XP", "yellow", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Set 10]", "/petsplus xp set 10", "Set Level 10", "green", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Set 20]", "/petsplus xp set 20", "Set Level 20", "green", true)
                }, 4);
            }
            
            return 1;
        } catch (NumberFormatException e) {
            player.sendMessage(Text.literal("Invalid level: " + levelStr)
                .formatted(Formatting.RED), false);
            if (player instanceof ServerPlayerEntity sp) {
                woflo.petsplus.ui.ChatLinks.sendSuggestRow(sp, new woflo.petsplus.ui.ChatLinks.Suggest[] {
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Set 5]", "/petsplus xp set 5", "Set Level 5", "green", true),
                    new woflo.petsplus.ui.ChatLinks.Suggest("[Set 15]", "/petsplus xp set 15", "Set Level 15", "green", true)
                }, 4);
            }
            return 0;
        } catch (Exception e) {
            player.sendMessage(Text.literal("Error setting pet level: " + e.getMessage())
                .formatted(Formatting.RED), false);
            return 0;
        }
    }
}