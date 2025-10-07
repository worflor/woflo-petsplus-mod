package woflo.petsplus.state;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.junit.jupiter.api.Test;
import woflo.petsplus.component.PetsplusComponents;
import woflo.petsplus.state.modules.RelationshipModule;
import woflo.petsplus.state.relationships.InteractionType;
import woflo.petsplus.state.relationships.RelationshipProfile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetComponentRelationshipPersistenceTest {

    @Test
    void relationshipModuleStateSurvivesSerializationRoundTrip() {
        MinecraftServer server = new MinecraftServer();
        ServerWorld world = new ServerWorld(server);
        MobEntity pet = new MobEntity(world);
        PetComponent component = new PetComponent(pet);

        UUID entityId = UUID.randomUUID();
        RelationshipModule relationshipModule = component.getRelationshipModule();

        relationshipModule.recordInteraction(entityId, InteractionType.PETTING, 1200L, 1.0f, 1.0f, 1.0f);
        relationshipModule.recordInteraction(entityId, InteractionType.FEEDING, 1230L, 1.0f, 1.0f, 1.0f);

        RelationshipProfile before = relationshipModule.getRelationship(entityId);
        assertNotNull(before, "Expected relationship profile to be created after interactions");

        PetsplusComponents.PetData serialized = component.toComponentData();
        assertTrue(serialized.relationships().isPresent(), "Serialized payload should include relationships when data exists");

        PetComponent reloaded = new PetComponent(new MobEntity(world));
        reloaded.fromComponentData(serialized);

        RelationshipProfile after = reloaded.getRelationshipModule().getRelationship(entityId);
        assertNotNull(after, "Relationship profile should persist through serialization");

        assertEquals(before.trust(), after.trust(), 1.0e-4f, "Trust should be preserved");
        assertEquals(before.affection(), after.affection(), 1.0e-4f, "Affection should be preserved");
        assertEquals(before.respect(), after.respect(), 1.0e-4f, "Respect should be preserved");
    }

    @Test
    void emptyRelationshipModuleOmitsSerializationPayload() {
        MinecraftServer server = new MinecraftServer();
        ServerWorld world = new ServerWorld(server);
        PetComponent component = new PetComponent(new MobEntity(world));

        PetsplusComponents.PetData serialized = component.toComponentData();

        assertTrue(serialized.relationships().isEmpty(), "Empty relationship module should not serialize data");
    }
}

