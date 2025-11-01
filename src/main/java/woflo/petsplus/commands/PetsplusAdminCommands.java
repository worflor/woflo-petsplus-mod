package woflo.petsplus.commands;

import woflo.petsplus.api.registry.RoleIdentifierUtil;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.commands.arguments.PetRoleArgumentType;
import woflo.petsplus.datagen.PetsplusLootHandler;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.PetAttributeManager;
import woflo.petsplus.stats.nature.NatureModifierSampler;
import woflo.petsplus.stats.nature.PetNatureSelector;
import woflo.petsplus.stats.nature.astrology.AstrologyRegistry;
import woflo.petsplus.util.PetTargetingUtil;
import woflo.petsplus.config.DebugSettings;
import woflo.petsplus.data.DataMaintenance;
import woflo.petsplus.mood.EmotionStimulusBus;
import woflo.petsplus.state.processing.AsyncProcessingTelemetry;
import woflo.petsplus.state.emotions.PetMoodEngine;

import java.util.Locale;

/**
 * Admin commands for testing and debugging pet features.
 * Uses intelligent raycast targeting for pet selection.
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
            .then(CommandManager.literal("pet")
                .then(CommandManager.literal("setlevel")
                    .then(CommandManager.argument("level", IntegerArgumentType.integer(1, 30))
                        .executes(PetsplusAdminCommands::setPetLevel)))
                .then(CommandManager.literal("setxp")
                    .then(CommandManager.argument("xp", IntegerArgumentType.integer(0))
                        .executes(PetsplusAdminCommands::setPetXp)))
                .then(CommandManager.literal("setrole")
                    .then(CommandManager.argument("role", PetRoleArgumentType.petRole())
                        .executes(PetsplusAdminCommands::setPetRole)))
                .then(CommandManager.literal("addbond")
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 10000))
                        .executes(PetsplusAdminCommands::addBondStrength)))
                .then(CommandManager.literal("reset")
                    .executes(PetsplusAdminCommands::resetPet))
                .then(CommandManager.literal("info")
                    .executes(PetsplusAdminCommands::showPetInfo))
                .then(CommandManager.literal("nature")
                    .then(CommandManager.literal("info")
                        .executes(PetsplusAdminCommands::showPetNature))
                    .then(CommandManager.literal("set")
                        .then(CommandManager.argument("nature", IdentifierArgumentType.identifier())
                            .suggests((context, builder) -> {
                                for (Identifier id : PetNatureSelector.getRegisteredNatureIds()) {
                                    builder.suggest(id.toString());
                                }
                                builder.suggest("petsplus:custom");
                                return builder.buildFuture();
                            })
                            .executes(PetsplusAdminCommands::setPetNature)))
                    .then(CommandManager.literal("clear")
                        .executes(PetsplusAdminCommands::clearPetNature)))
                .then(CommandManager.literal("emotions")
                    .then(CommandManager.literal("set")
                        .then(CommandManager.argument("emotion", StringArgumentType.string())
                            .suggests((context, builder) -> {
                                // Add all emotion suggestions
                                for (PetComponent.Emotion emotion : PetComponent.Emotion.values()) {
                                    builder.suggest(emotion.name().toLowerCase());
                                }
                                return builder.buildFuture();
                            })
                            .then(CommandManager.argument("weight", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    builder.suggest("0.2");
                                    builder.suggest("0.5");
                                    builder.suggest("1.0");
                                    builder.suggest("2.0");
                                    builder.suggest("5.0");
                                    return builder.buildFuture();
                                })
                                .executes(PetsplusAdminCommands::setEmotion))))
                    .then(CommandManager.literal("clear")
                        .executes(PetsplusAdminCommands::clearEmotions))
                    .then(CommandManager.literal("preset")
                        .then(CommandManager.literal("happy")
                            .executes(PetsplusAdminCommands::presetHappy))
                        .then(CommandManager.literal("afraid")
                            .executes(PetsplusAdminCommands::presetAfraid))
                        .then(CommandManager.literal("angry")
                            .executes(PetsplusAdminCommands::presetAngry))
                        .then(CommandManager.literal("calm")
                            .executes(PetsplusAdminCommands::presetCalm))
                        .then(CommandManager.literal("playful")
                            .executes(PetsplusAdminCommands::presetPlayful))
                        .then(CommandManager.literal("protective")
                            .executes(PetsplusAdminCommands::presetProtective))
                        .then(CommandManager.literal("melancholy")
                            .executes(PetsplusAdminCommands::presetMelancholy)))
                    .then(CommandManager.literal("info")
                        .executes(PetsplusAdminCommands::showEmotionInfo))))
            .then(CommandManager.literal("test")
                .then(CommandManager.literal("loot")
                    .then(CommandManager.literal("death")
                        .executes(PetsplusAdminCommands::testPetDeathLoot))
                    .then(CommandManager.literal("mining")
                        .executes(PetsplusAdminCommands::testMiningLoot))
                    .then(CommandManager.literal("ability")
                        .executes(PetsplusAdminCommands::testAbilityLoot)))
                .then(CommandManager.literal("effects")
                    .executes(PetsplusAdminCommands::testStatusEffects)))
            .then(CommandManager.literal("debug")
                .then(CommandManager.literal("on")
                    .executes(PetsplusAdminCommands::debugOn))
                .then(CommandManager.literal("off")
                    .executes(PetsplusAdminCommands::debugOff))
                .then(CommandManager.literal("status")
                    .executes(PetsplusAdminCommands::debugStatus))
                .then(CommandManager.literal("telemetry")
                    .then(CommandManager.literal("on")
                        .executes(PetsplusAdminCommands::telemetryOn))
                    .then(CommandManager.literal("off")
                        .executes(PetsplusAdminCommands::telemetryOff))
                    .then(CommandManager.literal("status")
                        .executes(PetsplusAdminCommands::telemetryStatus))
                    .then(CommandManager.literal("rate")
                        .then(CommandManager.argument("ticks", IntegerArgumentType.integer(1))
                            .executes(PetsplusAdminCommands::telemetryRate)))
                    .then(CommandManager.literal("snapshot")
                        .executes(PetsplusAdminCommands::telemetrySnapshot)))
                .then(CommandManager.literal("pipeline")
                    .then(CommandManager.literal("on")
                        .executes(PetsplusAdminCommands::pipelineOn))
                    .then(CommandManager.literal("off")
                        .executes(PetsplusAdminCommands::pipelineOff))
                    .then(CommandManager.literal("status")
                        .executes(PetsplusAdminCommands::pipelineStatus)))
                .then(CommandManager.literal("trace")
                    .then(CommandManager.literal("coalesce")
                        .then(CommandManager.literal("on")
                            .executes(PetsplusAdminCommands::traceCoalesceOn))
                        .then(CommandManager.literal("off")
                            .executes(PetsplusAdminCommands::traceCoalesceOff))
                        .then(CommandManager.literal("status")
                            .executes(PetsplusAdminCommands::traceCoalesceStatus)))))
            .then(CommandManager.literal("data")
                .then(CommandManager.literal("validate")
                    // Defaults: what=all, output=chat
                    .executes(ctx -> dataValidate(ctx))
                    .then(CommandManager.argument("what", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("ai");
                            builder.suggest("abilities");
                            builder.suggest("all");
                            return builder.buildFuture();
                        })
                        // When only 'what' is provided, use default output=chat
                        .executes(ctx -> dataValidate(ctx))
                        .then(CommandManager.argument("output", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                builder.suggest("chat");
                                builder.suggest("file");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> dataValidate(ctx)))))
                .then(CommandManager.literal("reload")
                    // Defaults: what=all, safe=true
                    .executes(ctx -> dataReload(ctx))
                    .then(CommandManager.argument("what", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("ai");
                            builder.suggest("abilities");
                            builder.suggest("tags");
                            builder.suggest("all");
                            return builder.buildFuture();
                        })
                        // When only 'what' is provided, use default safe=true
                        .executes(ctx -> dataReload(ctx))
                        .then(CommandManager.argument("safe", BoolArgumentType.bool())
                            .executes(ctx -> dataReload(ctx))))))
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
    
    // ============ PET MANIPULATION ============
    
    private static int setPetLevel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int level = IntegerArgumentType.getInteger(context, "level");
        
        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
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
        
        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
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
        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        Identifier roleId = PetRoleArgumentType.getRoleId(context, "role");
        petComp.setRoleId(roleId);

        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
        Text label = RoleIdentifierUtil.roleLabel(roleId, roleType);

        player.sendMessage(Text.literal("Pet role set to ")
            .formatted(Formatting.GREEN)
            .append(label.copy().formatted(Formatting.AQUA)), false);
        return 1;
    }
    
    private static int addBondStrength(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int amount = IntegerArgumentType.getInteger(context, "amount");
        
        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
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
        
        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
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

        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
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
        Identifier roleId = petComp.getRoleId();
        if (roleId != null) {
            PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
            String roleLabel = RoleIdentifierUtil.roleLabel(roleId, roleType).getString();
            if (roleLabel.isBlank()) {
                roleLabel = roleId.toString();
            }
            player.sendMessage(Text.literal("Role: " + roleLabel), false);
        }
        player.sendMessage(Text.literal("Level: " + petComp.getLevel() + " (" + petComp.getExperience() + " XP)"), false);
        player.sendMessage(Text.literal("Bond Strength: " + petComp.getBondStrength()), false);
        Identifier natureId = petComp.getNatureId();
        String natureSource = describeNatureSource(petComp);
        Text natureDisplay = AstrologyRegistry.getNatureText(petComp).copy().formatted(Formatting.AQUA);
        player.sendMessage(Text.literal("Nature: ")
            .append(natureId != null ? natureDisplay : Text.literal("None").formatted(Formatting.GRAY))
            .append(Text.literal(" (" + natureSource + ")").formatted(Formatting.DARK_GRAY)), false);
        player.sendMessage(Text.literal("Owner: " + (petComp.getOwner() != null ? petComp.getOwner().getName().getString() : "None")), false);
        player.sendMessage(Text.literal("Waiting for Tribute: " + petComp.isWaitingForTribute()), false);

        return 1;
    }

    static int showPetNature(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
            return 0;
        }

        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }

        Identifier natureId = petComp.getNatureId();
        String source = describeNatureSource(petComp);
        Text natureText = natureId != null
            ? AstrologyRegistry.getNatureText(petComp).copy().formatted(Formatting.AQUA)
            : Text.literal("None").formatted(Formatting.GRAY);
        player.sendMessage(Text.literal("Current nature: ").formatted(Formatting.GOLD)
            .append(natureText)
            .append(Text.literal(" (" + source + ")").formatted(Formatting.DARK_GRAY)), false);

        NatureModifierSampler.NatureAdjustment adjustment = NatureModifierSampler.sample(petComp);
        if (!adjustment.isEmpty()) {
            sendNatureRoll(player, "Major", adjustment.majorRoll());
            sendNatureRoll(player, "Minor", adjustment.minorRoll());
        }
        return 1;
    }

    static int setPetNature(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
            return 0;
        }

        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }

        Identifier natureId = IdentifierArgumentType.getIdentifier(context, "nature");
        
        // Validate nature ID against registry
        if (!PetNatureSelector.getRegisteredNatureIds().contains(natureId)) {
            player.sendMessage(Text.literal("Invalid nature ID: '")
                .append(Text.literal(natureId.toString()).formatted(Formatting.YELLOW))
                .append(Text.literal("'"))
                .formatted(Formatting.RED), false);
            player.sendMessage(Text.literal("Use tab completion to see valid nature IDs.").formatted(Formatting.GRAY), false);
            return 0;
        }
        
        petComp.setNatureId(natureId);
        petComp.clearStateData(PetComponent.StateKeys.BREEDING_ASSIGNED_NATURE);
        petComp.clearStateData(PetComponent.StateKeys.WILD_ASSIGNED_NATURE);

        AstrologyRegistry.SignCycleSnapshot cycleSnapshot = AstrologyRegistry.SignCycleSnapshot.EMPTY;

        if (natureId.equals(AstrologyRegistry.LUNARIS_NATURE_ID)) {
            cycleSnapshot = AstrologyRegistry.advanceSignCycle();
            Identifier cycleAssignment = cycleSnapshot.assigned();
            if (cycleAssignment != null) {
                AstrologyRegistry.applySign(petComp, cycleAssignment);
            }
        } else {
            AstrologyRegistry.applySign(petComp, null);
        }
        PetAttributeManager.applyAttributeModifiers(targetPet, petComp);

        Text display = AstrologyRegistry.getNatureText(petComp).copy().formatted(Formatting.AQUA);
        player.sendMessage(Text.literal("Set pet nature to ")
            .formatted(Formatting.GREEN)
            .append(display), false);

        if (natureId.equals(AstrologyRegistry.LUNARIS_NATURE_ID)) {
            if (cycleSnapshot.hasAssignment()) {
                Identifier currentSignId = petComp.getAstrologySignId();
                MutableText cycleMessage = Text.literal("Lunaris cycle → ")
                    .formatted(Formatting.DARK_AQUA)
                    .append(Text.literal(AstrologyRegistry.getDisplayTitle(currentSignId)).formatted(Formatting.AQUA));
                if (cycleSnapshot.total() > 0) {
                    cycleMessage.append(Text.literal(" (" + cycleSnapshot.displayIndex() + "/" + cycleSnapshot.total() + ")")
                        .formatted(Formatting.DARK_GRAY));
                }
                player.sendMessage(cycleMessage, false);
            } else {
                player.sendMessage(Text.literal("No Lunaris signs are configured; cycle cannot advance.")
                    .formatted(Formatting.YELLOW), false);
            }
        }
        return 1;
    }

    private static void sendNatureRoll(ServerPlayerEntity player, String label,
                                       NatureModifierSampler.NatureRoll roll) {
        if (roll == null || roll.isEmpty()) {
            return;
        }

        String statName = roll.stat().name().toLowerCase(Locale.ROOT);
        double base = roll.baseValue();
        double modifier = roll.modifier();
        double result = roll.value();

        player.sendMessage(Text.literal(String.format(Locale.ROOT,
                "%s %s ➜ base %.3f × %.3f = %.3f",
                label, statName, base, modifier, result))
            .formatted(Formatting.DARK_AQUA), false);
    }

    static int clearPetNature(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
            return 0;
        }

        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }

        petComp.setNatureId(null);
        petComp.clearStateData(PetComponent.StateKeys.BREEDING_ASSIGNED_NATURE);
        petComp.clearStateData(PetComponent.StateKeys.WILD_ASSIGNED_NATURE);
        PetAttributeManager.applyAttributeModifiers(targetPet, petComp);

        player.sendMessage(Text.literal("Cleared pet nature.").formatted(Formatting.GREEN), false);
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
        
        PetsplusLootHandler.handleScoutExplorationReward(player, player.getEntityPos());
        player.sendMessage(Text.literal("Ability reward loot spawned!").formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int testStatusEffects(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        MobEntity targetPet = findTargetPet(player);
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
        var world = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
        var stateManager = woflo.petsplus.state.StateManager.forWorld(world);
        woflo.petsplus.effects.PetsplusEffectManager.applyRoleAuraEffects(
            world,
            targetPet,
            petComp,
            player,
            stateManager.getAuraTargetResolver(),
            world.getTime()
        );
        
        player.sendMessage(Text.literal("Status effects applied from pet!").formatted(Formatting.GREEN), false);
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
    
    // ============ DEBUG TOGGLES ============
    
    private static int debugOn(CommandContext<ServerCommandSource> context) {
        DebugSettings.enableDebug();
        context.getSource().sendFeedback(() -> Text.literal("PetsPlus debug: ON"), true);
        return 1;
    }
    
    private static int debugOff(CommandContext<ServerCommandSource> context) {
        DebugSettings.disableDebug();
        context.getSource().sendFeedback(() -> Text.literal("PetsPlus debug: OFF"), true);
        return 1;
    }
    
    private static int debugStatus(CommandContext<ServerCommandSource> context) {
        boolean on = DebugSettings.isDebugEnabled();
        context.getSource().sendFeedback(() -> Text.literal("PetsPlus debug: " + (on ? "ON" : "OFF")), true);
        return on ? 1 : 0;
    }

    // ============ TELEMETRY TOGGLES ============
    private static int telemetryOn(CommandContext<ServerCommandSource> context) {
        DebugSettings.enableTelemetry();
        int rate = DebugSettings.getTelemetrySampleRateTicks();
        context.getSource().sendFeedback(() -> Text.literal("PetsPlus telemetry: ON (rate=" + rate + " ticks)"), true);
        return 1;
    }

    private static int telemetryOff(CommandContext<ServerCommandSource> context) {
        DebugSettings.disableTelemetry();
        context.getSource().sendFeedback(() -> Text.literal("PetsPlus telemetry: OFF"), true);
        return 1;
    }

    private static int telemetryStatus(CommandContext<ServerCommandSource> context) {
        boolean on = DebugSettings.isTelemetryEnabled();
        if (on) {
            int rate = DebugSettings.getTelemetrySampleRateTicks();
            context.getSource().sendFeedback(() -> Text.literal("PetsPlus telemetry: ON (rate=" + rate + " ticks)"), true);
            return 1;
        } else {
            context.getSource().sendFeedback(() -> Text.literal("PetsPlus telemetry: OFF"), true);
            return 0;
        }
    }

    private static int telemetryRate(CommandContext<ServerCommandSource> context) {
        int ticks = IntegerArgumentType.getInteger(context, "ticks");
        if (ticks < 1) ticks = 1; // enforce a sane lower bound, though the argument enforces >= 1
        DebugSettings.setTelemetrySampleRate(ticks);
        int effective = DebugSettings.getTelemetrySampleRateTicks();
        context.getSource().sendFeedback(() -> Text.literal("PetsPlus telemetry sample rate set to " + effective + " ticks"), true);
        return 1;
    }

    private static int telemetrySnapshot(CommandContext<ServerCommandSource> context) {
        // Read-only snapshot of current counters/timers; plain chat output.
        boolean enabled = AsyncProcessingTelemetry.isEnabled();
        int rate = AsyncProcessingTelemetry.sampleRateTicks();

        long ingressEvents = AsyncProcessingTelemetry.INGRESS_EVENTS.get();
        long ownerBatches = AsyncProcessingTelemetry.OWNER_BATCHES.get();

        long enq = AsyncProcessingTelemetry.TASKS_ENQUEUED.get();
        long exec = AsyncProcessingTelemetry.TASKS_EXECUTED.get();
        long drop = AsyncProcessingTelemetry.TASKS_DROPPED.get();

        long coalesced = AsyncProcessingTelemetry.STIMULI_COALESCED.get();

        long ingressNanos = AsyncProcessingTelemetry.INGRESS_TIME.getTotalNanos();
        long ingressCount = AsyncProcessingTelemetry.INGRESS_TIME.getCount();

        long dispatchNanos = AsyncProcessingTelemetry.DISPATCH_TIME.getTotalNanos();
        long dispatchCount = AsyncProcessingTelemetry.DISPATCH_TIME.getCount();

        long commitNanos = AsyncProcessingTelemetry.COMMIT_TIME.getTotalNanos();
        long commitCount = AsyncProcessingTelemetry.COMMIT_TIME.getCount();

        StringBuilder sb = new StringBuilder(160);
        sb.append("telemetry: ").append(enabled ? "on" : "off")
          .append(" rate=").append(rate)
          .append(" | ")
          .append("events[ingress=").append(ingressEvents)
          .append(" ownerBatches=").append(ownerBatches).append("]")
          .append(" | ")
          .append("tasks[enq=").append(enq)
          .append(" exec=").append(exec)
          .append(" drop=").append(drop).append("]")
          .append(" | ")
          .append("coalesced=").append(coalesced)
          .append(" | ")
          .append("time[nanos ")
          .append("ingress=").append(ingressNanos).append("/c=").append(ingressCount)
          .append(" dispatch=").append(dispatchNanos).append("/c=").append(dispatchCount)
          .append(" commit=").append(commitNanos).append("/c=").append(commitCount)
          .append("]");

        context.getSource().sendFeedback(() -> Text.literal(sb.toString()), true);
        return 1;
    }

    // ============ PIPELINE TOGGLES ============
    private static int pipelineOn(CommandContext<ServerCommandSource> context) {
        DebugSettings.enablePipeline();
        context.getSource().sendFeedback(() -> Text.literal("PetsPlus pipeline: ON"), true);
        return 1;
    }

    private static int pipelineOff(CommandContext<ServerCommandSource> context) {
        DebugSettings.disablePipeline();
        context.getSource().sendFeedback(() -> Text.literal("PetsPlus pipeline: OFF"), true);
        return 1;
    }

    private static int pipelineStatus(CommandContext<ServerCommandSource> context) {
        boolean on = DebugSettings.isPipelineEnabled();
        context.getSource().sendFeedback(() -> Text.literal("PetsPlus pipeline: " + (on ? "ON" : "OFF")), true);
        return on ? 1 : 0;
    }

    // ============ TRACE COALESCE (ADMIN) ============

    private static int traceCoalesceOn(CommandContext<ServerCommandSource> context) {
        EmotionStimulusBus.enableCoalesceTrace();
        boolean dbg = DebugSettings.isDebugEnabled();
        context.getSource().sendFeedback(() -> Text.literal("PetsPlus trace coalesce: ON (debug=" + dbg + ")"), true);
        return 1;
    }

    private static int traceCoalesceOff(CommandContext<ServerCommandSource> context) {
        EmotionStimulusBus.disableCoalesceTrace();
        context.getSource().sendFeedback(() -> Text.literal("PetsPlus trace coalesce: OFF"), true);
        return 1;
    }

    private static int traceCoalesceStatus(CommandContext<ServerCommandSource> context) {
        boolean on = EmotionStimulusBus.isCoalesceTraceEnabled();
        boolean dbg = DebugSettings.isDebugEnabled();
        context.getSource().sendFeedback(() -> Text.literal("PetsPlus trace coalesce: " + (on ? "ON" : "OFF") + " (debug=" + dbg + ")"), true);
        return on ? 1 : 0;
    }
    
    // ============ DATA MAINTENANCE (STUBS) ============

    private static int dataValidate(CommandContext<ServerCommandSource> context) {
        String what = "all";
        String output = "chat";

        try {
            what = normalizeValidateWhat(StringArgumentType.getString(context, "what"));
        } catch (Exception ignored) { /* default remains "all" */ }

        try {
            output = normalizeOutput(StringArgumentType.getString(context, "output"));
        } catch (Exception ignored) { /* default remains "chat" */ }

        String summary = DataMaintenance.validate(what, output);
        context.getSource().sendFeedback(() -> Text.literal(summary), true);
        return 1;
    }

    private static int dataReload(CommandContext<ServerCommandSource> context) {
        String what = "all";
        boolean safe = true;

        try {
            what = normalizeReloadWhat(StringArgumentType.getString(context, "what"));
        } catch (Exception ignored) { /* default remains "all" */ }

        try {
            safe = BoolArgumentType.getBool(context, "safe");
        } catch (Exception ignored) { /* default remains true */ }

        String summary = DataMaintenance.reload(what, safe);
        context.getSource().sendFeedback(() -> Text.literal(summary), true);
        return 1;
    }

    private static String normalizeValidateWhat(String s) {
        String v = s == null ? "all" : s.toLowerCase(Locale.ROOT);
        return switch (v) {
            case "ai", "abilities", "all" -> v;
            default -> "all";
        };
    }

    private static String normalizeReloadWhat(String s) {
        String v = s == null ? "all" : s.toLowerCase(Locale.ROOT);
        return switch (v) {
            case "ai", "abilities", "tags", "all" -> v;
            default -> "all";
        };
    }

    private static String normalizeOutput(String s) {
        String v = s == null ? "chat" : s.toLowerCase(Locale.ROOT);
        return switch (v) {
            case "chat", "file" -> v;
            default -> "chat";
        };
    }

    // ============ EMOTION DEBUG COMMANDS ============
    
    private static int setEmotion(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String emotionName = StringArgumentType.getString(context, "emotion").toUpperCase();
        String weightStr = StringArgumentType.getString(context, "weight");
        
        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        try {
            PetComponent.Emotion emotion = PetComponent.Emotion.valueOf(emotionName);
            float weight = Float.parseFloat(weightStr);
            
            petComp.pushEmotion(emotion, weight);
            petComp.updateMood();
            
            player.sendMessage(Text.literal("Set emotion ")
                .formatted(Formatting.GREEN)
                .append(Text.literal(emotion.name().toLowerCase()).formatted(Formatting.AQUA))
                .append(Text.literal(" to weight ").formatted(Formatting.GREEN))
                .append(Text.literal(String.valueOf(weight)).formatted(Formatting.YELLOW)), false);
            
            return 1;
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("Invalid emotion name or weight! Use tab completion to see valid emotions.").formatted(Formatting.RED), false);
            return 0;
        }
    }
    
    private static int clearEmotions(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Clear emotions by setting them to very low values - there's no direct clear method
        for (PetComponent.Emotion emotion : PetComponent.Emotion.values()) {
            petComp.pushEmotion(emotion, 0.0f);
        }
        petComp.updateMood();
        
        player.sendMessage(Text.literal("Cleared all emotions from pet!").formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int showEmotionInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        Runnable respond = () -> {
            player.sendMessage(Text.literal("=== Pet Emotion & Mood Info ===").formatted(Formatting.GOLD), false);

            player.sendMessage(Text.literal("Current Mood: ")
                .formatted(Formatting.GRAY)
                .append(petComp.getMoodTextWithDebug()), false);

            PetComponent.Emotion dominantEmotion = petComp.getDominantEmotion();
            if (dominantEmotion != null) {
                player.sendMessage(Text.literal("Dominant Emotion: ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(dominantEmotion.name().toLowerCase()).formatted(Formatting.AQUA)), false);
            } else {
                player.sendMessage(Text.literal("Dominant Emotion: None").formatted(Formatting.GRAY), false);
            }

            player.sendMessage(Text.literal("Mood Strengths:").formatted(Formatting.YELLOW), false);
            for (PetComponent.Mood mood : PetComponent.Mood.values()) {
                float strength = petComp.getMoodBlend().getOrDefault(mood, 0f);
                if (strength > 0.1f) {
                    player.sendMessage(Text.literal("  " + mood.name().toLowerCase() + ": ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(String.format("%.2f", strength)).formatted(Formatting.WHITE)), false);
                }
            }

            player.sendMessage(Text.literal(String.format(Locale.ROOT,
                    "Nature tuning: volatility ×%.2f, resilience ×%.2f, contagion ×%.2f, guard ×%.2f",
                    petComp.getNatureVolatilityMultiplier(),
                    petComp.getNatureResilienceMultiplier(),
                    petComp.getNatureContagionModifier(),
                    petComp.getNatureGuardModifier()))
                .formatted(Formatting.DARK_AQUA), false);

            PetComponent.NatureEmotionProfile profile = petComp.getNatureEmotionProfile();
            if (profile != null && !profile.isEmpty()) {
                player.sendMessage(Text.literal(formatNatureEmotionProfile(profile))
                    .formatted(Formatting.AQUA), false);
            }

            PetComponent.NatureGuardTelemetry guardTelemetry = petComp.getNatureGuardTelemetry();
            player.sendMessage(Text.literal(String.format(Locale.ROOT,
                    "Guard telemetry ➜ relationship %.2f, danger %.2f, contagion cap %.2f",
                    guardTelemetry.relationshipGuard(),
                    guardTelemetry.dangerWindow(),
                    guardTelemetry.contagionCap()))
                .formatted(Formatting.DARK_PURPLE), false);
        };

        petComp.updateMood();
        PetMoodEngine engine = petComp.getMoodEngine();
        if (engine != null) {
            long threshold = player.getEntityWorld() instanceof ServerWorld sw ? Math.max(0L, sw.getTime()) : 0L;
            engine.onNextResultApplied(threshold, respond);
        } else {
            respond.run();
        }

        return 1;
    }

    private static String formatNatureEmotionProfile(PetComponent.NatureEmotionProfile profile) {
        return String.format(Locale.ROOT,
            "Nature emotions ➜ major %s ×%.2f, minor %s ×%.2f, quirk %s ×%.2f",
            profile.majorEmotion() != null ? profile.majorEmotion().name().toLowerCase(Locale.ROOT) : "none",
            profile.majorStrength(),
            profile.minorEmotion() != null ? profile.minorEmotion().name().toLowerCase(Locale.ROOT) : "none",
            profile.minorStrength(),
            profile.quirkEmotion() != null ? profile.quirkEmotion().name().toLowerCase(Locale.ROOT) : "none",
            profile.quirkStrength());
    }
    
    // Emotion presets for common moods
    private static int presetHappy(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Happy", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.CHEERFUL, 3.0f),
            new EmotionWeight(PetComponent.Emotion.RELIEF, 2.5f),
            new EmotionWeight(PetComponent.Emotion.CHEERFUL, 1.5f),
            new EmotionWeight(PetComponent.Emotion.CONTENT, 1.0f)
        });
    }

    private static int presetAfraid(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Afraid", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.ANGST, 3.0f),
            new EmotionWeight(PetComponent.Emotion.FOREBODING, 2.5f),
            new EmotionWeight(PetComponent.Emotion.STARTLE, 2.0f)
        });
    }

    private static int presetAngry(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Angry", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.FRUSTRATION, 3.0f),
            new EmotionWeight(PetComponent.Emotion.DISGUST, 2.0f)
        });
    }

    private static int presetCalm(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Calm", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.CONTENT, 3.0f),
            new EmotionWeight(PetComponent.Emotion.LAGOM, 2.5f),
            new EmotionWeight(PetComponent.Emotion.WABI_SABI, 1.8f),
            new EmotionWeight(PetComponent.Emotion.RELIEF, 1.2f)
        });
    }
    
    private static int presetPlayful(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Playful", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.CHEERFUL, 3.0f),
            new EmotionWeight(PetComponent.Emotion.KEFI, 2.5f),
            new EmotionWeight(PetComponent.Emotion.CHEERFUL, 2.0f),
            new EmotionWeight(PetComponent.Emotion.SOBREMESA, 1.5f)
        });
    }
    
    private static int presetProtective(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Protective", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.GUARDIAN_VIGIL, 3.0f),
            new EmotionWeight(PetComponent.Emotion.QUERECIA, 2.2f),
            new EmotionWeight(PetComponent.Emotion.UBUNTU, 1.8f),
            new EmotionWeight(PetComponent.Emotion.SOBREMESA, 1.4f)
        });
    }
    
    private static int presetMelancholy(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return applyEmotionPreset(context, "Melancholy", new EmotionWeight[]{
            new EmotionWeight(PetComponent.Emotion.SAUDADE, 3.0f),
            new EmotionWeight(PetComponent.Emotion.HIRAETH, 2.5f),
            new EmotionWeight(PetComponent.Emotion.MONO_NO_AWARE, 2.0f),
            new EmotionWeight(PetComponent.Emotion.FERNWEH, 1.5f),
            new EmotionWeight(PetComponent.Emotion.ENNUI, 1.0f)
        });
    }
    
    private static int applyEmotionPreset(CommandContext<ServerCommandSource> context, String presetName, EmotionWeight[] emotions) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        
        MobEntity targetPet = findTargetPet(player);
        if (targetPet == null) {
            player.sendMessage(Text.literal("No pet found! Look at your pet or stand nearby.").formatted(Formatting.RED), false);
            return 0;
        }
        
        PetComponent petComp = PetComponent.get(targetPet);
        if (petComp == null) {
            player.sendMessage(Text.literal("Target entity is not a pet!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Clear existing emotions first
        for (PetComponent.Emotion emotion : PetComponent.Emotion.values()) {
            petComp.pushEmotion(emotion, 0.0f);
        }
        
        // Apply preset emotions
        for (EmotionWeight ew : emotions) {
            petComp.pushEmotion(ew.emotion, ew.weight);
        }
        petComp.updateMood();
        
        player.sendMessage(Text.literal("Applied ")
            .formatted(Formatting.GREEN)
            .append(Text.literal(presetName).formatted(Formatting.AQUA))
            .append(Text.literal(" emotion preset to pet!").formatted(Formatting.GREEN)), false);
        
        return 1;
    }

    private static class EmotionWeight {
        final PetComponent.Emotion emotion;
        final float weight;

        EmotionWeight(PetComponent.Emotion emotion, float weight) {
            this.emotion = emotion;
            this.weight = weight;
        }
    }

    // ============ UTILITY METHODS ============

    private static String describeNatureSource(PetComponent component) {
        if (component == null || component.getNatureId() == null) {
            return "unset";
        }
        if (component.getStateData(PetComponent.StateKeys.BREEDING_ASSIGNED_NATURE, String.class) != null) {
            return "breeding";
        }
        if (component.getStateData(PetComponent.StateKeys.WILD_ASSIGNED_NATURE, String.class) != null) {
            return "taming";
        }
        return "manual";
    }

    /**
     * Find the target pet using intelligent raycast targeting.
     */
    private static MobEntity findTargetPet(ServerPlayerEntity player) {
        return PetTargetingUtil.findTargetPet(player);
    }

}
