package woflo.petsplus.api.registry;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import woflo.petsplus.api.Trigger;
import woflo.petsplus.api.TriggerContext;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerBrokeBlockTriggerTest {

    @Test
    void blockIdFilterMatchesNamespacedIdentifier() {
        Trigger trigger = createTrigger(Optional.empty(), Optional.of(parseIdentifier("minecraft:diamond_ore")));
        TriggerContext context = createContext("minecraft:diamond_ore", true, true);
        assertTrue(trigger.shouldActivate(context), "Trigger should activate when block identifiers match");
    }

    @Test
    void blockIdFilterMatchesNamespaceFreePayload() {
        Trigger trigger = createTrigger(Optional.empty(), Optional.of(parseIdentifier("minecraft:diamond_ore")));
        TriggerContext context = createContext(null, false, true);
        context.withData("block_id_no_namespace", "diamond_ore");
        assertTrue(trigger.shouldActivate(context), "Trigger should accept namespace-free block identifiers");
    }

    @Test
    void blockIdFilterRejectsMismatchedPayload() {
        Trigger trigger = createTrigger(Optional.empty(), Optional.of(parseIdentifier("minecraft:diamond_ore")));
        TriggerContext context = createContext("minecraft:gold_ore", true, true);
        assertFalse(trigger.shouldActivate(context), "Trigger should reject non-matching block identifiers");
    }

    @Test
    void blockValuableRequirementRespected() {
        Trigger trigger = createTrigger(Optional.of(Boolean.TRUE), Optional.of(parseIdentifier("minecraft:diamond_ore")));

        TriggerContext matchingContext = createContext("minecraft:diamond_ore", true, true);
        assertTrue(trigger.shouldActivate(matchingContext), "Trigger should activate when block is valuable and matches id");

        TriggerContext nonValuable = createContext("minecraft:diamond_ore", true, false);
        assertFalse(trigger.shouldActivate(nonValuable), "Trigger should not activate when valuable requirement fails");
    }

    @Test
    void blockIdFilterMatchesCaseInsensitiveStrings() {
        Trigger trigger = createTrigger(Optional.empty(), Optional.of(parseIdentifier("minecraft:diamond_ore")));
        TriggerContext context = createContext("MINECRAFT:DIAMOND_ORE", false, true);
        assertTrue(trigger.shouldActivate(context), "Identifier comparison should ignore case differences");
    }

    @Test
    void eventTypeAllowsNamespacedTrigger() {
        Trigger trigger = createTrigger(Optional.empty(), Optional.empty());
        TriggerContext context = createContext("minecraft:stone", false, false, "petsplus:owner_broke_block");
        context.withData("block_id_no_namespace", "stone");
        assertTrue(trigger.shouldActivate(context), "Namespaced event identifiers should still activate");
    }

    private static TriggerContext createContext(String blockId, boolean includeIdentifier, boolean blockValuable) {
        return createContext(blockId, includeIdentifier, blockValuable, "owner_broke_block");
    }

    private static TriggerContext createContext(String blockId, boolean includeIdentifier, boolean blockValuable, String eventType) {
        MinecraftServer server = new MinecraftServer();
        ServerWorld world = new ServerWorld(server);
        ServerPlayerEntity owner = new ServerPlayerEntity(world);
        TriggerContext context = new TriggerContext(world, null, owner, eventType);
        if (blockId != null) {
            context.withData("block_id", blockId);
            if (includeIdentifier) {
                Identifier identifier = parseIdentifier(blockId);
                context.withData("block_identifier", identifier);
                context.withData("block_id_no_namespace", identifier.getPath());
            }
        }
        context.withData("block_valuable", blockValuable);
        return context;
    }

    private static Identifier parseIdentifier(String blockId) {
        int colonIndex = blockId.indexOf(':');
        if (colonIndex >= 0) {
            String namespace = blockId.substring(0, colonIndex);
            String path = blockId.substring(colonIndex + 1);
            return Identifier.of(namespace, path);
        }
        return Identifier.of("minecraft", blockId);
    }

    private static Trigger createTrigger(Optional<Boolean> requireValuable, Optional<Identifier> requiredBlockId) {
        Identifier triggerId = Identifier.of("petsplus", "owner_broke_block");
        return OwnerBrokeBlockTriggerFactory.create(triggerId, requireValuable, requiredBlockId, 0);
    }
}
