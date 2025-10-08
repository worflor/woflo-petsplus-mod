package woflo.petsplus.state;

import org.junit.jupiter.api.Test;
import woflo.petsplus.component.PetsplusComponents;
import woflo.petsplus.state.modules.RelationshipModule;
import woflo.petsplus.state.relationships.InteractionType;
import woflo.petsplus.state.relationships.RelationshipProfile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PetComponentRelationshipPersistenceTest {

    @Test
    void relationshipModuleStateSurvivesSerializationRoundTrip() {
        net.minecraft.server.MinecraftServer server = mock(net.minecraft.server.MinecraftServer.class);
        net.minecraft.server.world.ServerWorld world = mock(net.minecraft.server.world.ServerWorld.class);
        net.minecraft.entity.mob.MobEntity pet = mock(net.minecraft.entity.mob.MobEntity.class);
        when(pet.getEntityWorld()).thenReturn(world);
        when(pet.getUuid()).thenReturn(UUID.randomUUID());
        when(pet.getUuidAsString()).thenReturn(pet.getUuid().toString());

        PetComponent component = new PetComponent(pet);

        UUID entityId = UUID.randomUUID();
        RelationshipModule relationshipModule = component.getRelationshipModule();

        relationshipModule.recordInteraction(entityId, InteractionType.PETTING, 1200L, 1.0f, 1.0f, 1.0f);
        relationshipModule.recordInteraction(entityId, InteractionType.FEEDING, 1230L, 1.0f, 1.0f, 1.0f);

        RelationshipProfile before = relationshipModule.getRelationship(entityId);
        assertNotNull(before, "Expected relationship profile to be created after interactions");

        PetsplusComponents.PetData serialized = component.toComponentData();
        assertTrue(serialized.relationships().isPresent(), "Serialized payload should include relationships when data exists");

        net.minecraft.entity.mob.MobEntity reloadedPet = mock(net.minecraft.entity.mob.MobEntity.class);
        when(reloadedPet.getEntityWorld()).thenReturn(world);
        when(reloadedPet.getUuid()).thenReturn(UUID.randomUUID());
        when(reloadedPet.getUuidAsString()).thenReturn(reloadedPet.getUuid().toString());
        PetComponent reloaded = new PetComponent(reloadedPet);
        reloaded.fromComponentData(serialized);

        RelationshipProfile after = reloaded.getRelationshipModule().getRelationship(entityId);
        assertNotNull(after, "Relationship profile should persist through serialization");

        assertEquals(before.trust(), after.trust(), 1.0e-4f, "Trust should be preserved");
        assertEquals(before.affection(), after.affection(), 1.0e-4f, "Affection should be preserved");
        assertEquals(before.respect(), after.respect(), 1.0e-4f, "Respect should be preserved");
    }

    @Test
    void emptyRelationshipModuleOmitsSerializationPayload() {
        net.minecraft.server.MinecraftServer server = mock(net.minecraft.server.MinecraftServer.class);
        net.minecraft.server.world.ServerWorld world = mock(net.minecraft.server.world.ServerWorld.class);
        net.minecraft.entity.mob.MobEntity pet = mock(net.minecraft.entity.mob.MobEntity.class);
        when(pet.getEntityWorld()).thenReturn(world);
        when(pet.getUuid()).thenReturn(UUID.randomUUID());
        when(pet.getUuidAsString()).thenReturn(pet.getUuid().toString());
        PetComponent component = new PetComponent(pet);

        PetsplusComponents.PetData serialized = component.toComponentData();

        assertTrue(serialized.relationships().isEmpty(), "Empty relationship module should not serialize data");
    }
}

