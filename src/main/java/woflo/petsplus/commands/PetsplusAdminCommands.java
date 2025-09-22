package woflo.petsplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.component.PetsplusComponents;
import woflo.petsplus.items.PetsplusItemUtils;
import woflo.petsplus.datagen.PetsplusLootHandler;
import woflo.petsplus.state.PetComponent;

/**
 * Admin commands for testing and debugging pet features.
 * Provides tools for spawning items, debugging pet state, and testing mechanics.
 */
public class PetsplusAdminCommands {
    
    /**
     * Register admin commands with the command dispatcher.
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("petsplusadmin")
            .requires(source -> source.hasPermissionLevel(2)) // Require OP level 2
            .then(CommandManager.literal("kit")
                .executes(PetsplusAdminCommands::giveAdminKit)
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(context -> giveAdminKit(context, EntityArgumentType.getPlayer(context, "player")))))
            .then(CommandManager.literal("spawn")
                .then(CommandManager.literal("respec")
                    .then(CommandManager.argument("type", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            builder.suggest("general");
                            builder.suggest("role");
                            return builder.buildFuture();
                        })
                        .executes(PetsplusAdminCommands::spawnRespecToken)))
                .then(CommandManager.literal("whistle")
                    .executes(PetsplusAdminCommands::spawnLinkedWhistle))
                .then(CommandManager.literal("nametag")
                    .then(CommandManager.argument("special", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            builder.suggest("true");
                            builder.suggest("false");
                            return builder.buildFuture();
                        })
                        .executes(PetsplusAdminCommands::spawnMetadataTag)))
                .then(CommandManager.literal("debug")
                    .executes(PetsplusAdminCommands::spawnDebugStick)))
            .then(CommandManager.literal("pet")
                .then(CommandManager.literal("setlevel")
                    .then(CommandManager.argument("level", IntegerArgumentType.integer(1, 30))
                        .executes(PetsplusAdminCommands::setPetLevel)))
                .then(CommandManager.literal("setxp")
                    .then(CommandManager.argument("xp", IntegerArgumentType.integer(0))
                        .executes(PetsplusAdminCommands::setPetXp)))
                .then(CommandManager.literal("setrole")
                    .then(CommandManager.argument("role", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            for (PetRole role : PetRole.values()) {
                                builder.suggest(role.getKey());
                            }
                            return builder.buildFuture();
                        })
                        .executes(PetsplusAdminCommands::setPetRole)))
                .then(CommandManager.literal("addbond")
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 10000))
                        .executes(PetsplusAdminCommands::addBondStrength)))
                .then(CommandManager.literal("reset")
                    .executes(PetsplusAdminCommands::resetPet))
                .then(CommandManager.literal("info")
                    .executes(PetsplusAdminCommands::showPetInfo)))
            .then(CommandManager.literal("test")
                .then(CommandManager.literal("loot")
                    .then(CommandManager.literal("death")
                        .executes(PetsplusAdminCommands::testPetDeathLoot))
                    .then(CommandManager.literal("mining")
                        .executes(PetsplusAdminCommands::testMiningLoot))
                    .then(CommandManager.literal("ability")
                        .executes(PetsplusAdminCommands::testAbilityLoot)))
                .then(CommandManager.literal("effects")
                    .executes(PetsplusAdminCommands::testStatusEffects))
                .then(CommandManager.literal("components")
                    .executes(PetsplusAdminCommands::testDataComponents)))
            .then(CommandManager.literal("reload")
                .executes(PetsplusAdminCommands::reloadConfig)));
    }
    
    // ============ ADMIN KIT ============
    
    private static int giveAdminKit(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return giveAdminKit(context, context.getSource().getPlayerOrThrow());
    }
    
    private static int giveAdminKit(CommandContext<ServerCommandSource> context, ServerPlayerEntity player) {
        try {
            PetsplusLootHandler.giveAdminTestingKit(player);
            
            context.getSource().sendFeedback(() -> 
                Text.literal("Admin kit given to " + player.getName().getString()).formatted(Formatting.GREEN), true);
            player.sendMessage(Text.literal("Admin testing kit received!").formatted(Formatting.GOLD), false);
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Failed to give admin kit: " + e.getMessage()));
            return 0;
        }
    }
    
    // ============ ITEM SPAWNING ============
    
    private static int spawnRespecToken(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String type = StringArgumentType.getString(context, "type");
        
        ItemStack token = switch (type.toLowerCase()) {
            case "role" -> PetsplusItemUtils.createRoleRespecToken("guardian"); // Example role
            default -> PetsplusItemUtils.createRespecToken();
        };
        
        player.getInventory().insertStack(token);
        
        player.sendMessage(Text.literal("Respec token (" + type + ") created!").formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int spawnLinkedWhistle(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        // Find nearest pet to link, or create an unlinked whistle
        MobEntity targetPet = findNearestPet(player);
        ItemStack whistle = PetsplusItemUtils.createLinkedWhistle(targetPet);
        player.getInventory().insertStack(whistle);
        
        String message = targetPet != null ? 
            "Linked whistle created (linked to nearby pet)!" : 
            "Linked whistle created (no pet nearby to link)!";
        player.sendMessage(Text.literal(message).formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int spawnMetadataTag(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String specialStr = StringArgumentType.getString(context, "special");
        boolean isSpecial = Boolean.parseBoolean(specialStr);
        
        ItemStack nameTag = PetsplusItemUtils.createPetMetadataTag("Test Pet", isSpecial);
        player.getInventory().insertStack(nameTag);
        
        player.sendMessage(Text.literal("Metadata tag created (special: " + isSpecial + ")!").formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int spawnDebugStick(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        ItemStack debugStick = PetsplusItemUtils.createDebugInfoStick();
        player.getInventory().insertStack(debugStick);
        
        player.sendMessage(Text.literal("Debug info stick created!").formatted(Formatting.GREEN), false);
        return 1;
    }
    
    // ============ PET MANIPULATION ============
    
    private static int setPetLevel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int level = IntegerArgumentType.getInteger(context, "level");
        
        MobEntity targetPet = findNearestPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found nearby!").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        petComp.setLevel(level);
        player.sendMessage(Text.literal("Pet level set to " + level).formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int setPetXp(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int xp = IntegerArgumentType.getInteger(context, "xp");
        
        MobEntity targetPet = findNearestPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found nearby!").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        petComp.setExperience(xp);
        player.sendMessage(Text.literal("Pet XP set to " + xp).formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int setPetRole(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String roleKey = StringArgumentType.getString(context, "role");
        
        MobEntity targetPet = findNearestPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found nearby!").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetRole role = PetRole.fromKey(roleKey);
        if (role == null) {
            player.sendMessage(Text.literal("Invalid role: " + roleKey).formatted(Formatting.RED), false);
            return 0;
        }
        
        petComp.setRole(role);
        player.sendMessage(Text.literal("Pet role set to " + role.getDisplayName()).formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int addBondStrength(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int amount = IntegerArgumentType.getInteger(context, "amount");
        
        MobEntity targetPet = findNearestPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found nearby!").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        petComp.addBondStrength(amount);
        long newBond = petComp.getBondStrength();
        player.sendMessage(Text.literal("Added " + amount + " bond strength (total: " + newBond + ")").formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int resetPet(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        MobEntity targetPet = findNearestPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found nearby!").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        petComp.resetAbilities();
        player.sendMessage(Text.literal("Pet abilities reset!").formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int showPetInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        MobEntity targetPet = findNearestPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found nearby!").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Display comprehensive pet information
        player.sendMessage(Text.literal("=== Pet Information ===").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("Type: " + targetPet.getType().getName().getString()), false);
        player.sendMessage(Text.literal("Role: " + petComp.getRole().getDisplayName()), false);
        player.sendMessage(Text.literal("Level: " + petComp.getLevel() + " (" + petComp.getExperience() + " XP)"), false);
        player.sendMessage(Text.literal("Bond Strength: " + petComp.getBondStrength()), false);
        player.sendMessage(Text.literal("Owner: " + (petComp.getOwner() != null ? petComp.getOwner().getName().getString() : "None")), false);
        player.sendMessage(Text.literal("Waiting for Tribute: " + petComp.isWaitingForTribute()), false);
        
        return 1;
    }
    
    // ============ TESTING COMMANDS ============
    
    private static int testPetDeathLoot(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        PetsplusLootHandler.handlePetDeath(null, player);
        player.sendMessage(Text.literal("Pet death loot spawned!").formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int testMiningLoot(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        PetsplusLootHandler.handleEnchantedBoundMiningBonus(player, player.getBlockPos(), new ItemStack(Items.DIAMOND));
        player.sendMessage(Text.literal("Mining bonus loot spawned!").formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int testAbilityLoot(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        PetsplusLootHandler.handleScoutExplorationReward(player, player.getPos());
        player.sendMessage(Text.literal("Ability reward loot spawned!").formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int testStatusEffects(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        MobEntity targetPet = findNearestPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found nearby to test effects!").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Force apply aura effects
        woflo.petsplus.effects.PetsplusEffectManager.applyRoleAuraEffects(
            (net.minecraft.server.world.ServerWorld) player.getWorld(), targetPet, petComp, player);
        
        player.sendMessage(Text.literal("Status effects applied from pet!").formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int testDataComponents(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        player.sendMessage(Text.literal("=== Data Component Test ===").formatted(Formatting.GOLD), false);
        
        // Test all component types
        PetsplusComponents.RespecData respec = PetsplusComponents.RespecData.create();
        player.sendMessage(Text.literal("Respec Data: " + respec.toString()), false);
        
        PetsplusComponents.LinkedWhistleData whistle = PetsplusComponents.LinkedWhistleData.create(
            player.getUuid(), "Test Pet");
        player.sendMessage(Text.literal("Whistle Data: " + whistle.toString()), false);
        
        PetsplusComponents.PetMetadata metadata = PetsplusComponents.PetMetadata.create()
            .withDisplayName("Test").withBondStrength(100);
        player.sendMessage(Text.literal("Metadata: " + metadata.toString()), false);
        
        return 1;
    }
    
    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        try {
            woflo.petsplus.config.PetsPlusConfig.getInstance().reload();
            context.getSource().sendFeedback(() -> 
                Text.literal("Configuration reloaded!").formatted(Formatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Failed to reload config: " + e.getMessage()));
            return 0;
        }
    }
    
    // ============ UTILITY METHODS ============
    
    private static MobEntity findNearestPet(PlayerEntity player) {
        return player.getWorld().getEntitiesByClass(MobEntity.class, 
            player.getBoundingBox().expand(10.0), 
            entity -> {
                PetComponent petComp = PetComponent.get(entity);
                return petComp != null && petComp.isOwnedBy(player);
            }).stream().findFirst().orElse(null);
    }
}