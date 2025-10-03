package woflo.petsplus.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import net.minecraft.entity.mob.MobEntity;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.roles.guardian.GuardianFortressBondManager;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PlayerTickDispatcher;
import woflo.petsplus.state.PlayerTickListener;

class GuardianFortressBondPetDrEffectTest {
    private final GuardianFortressBondPetDrEffect effect = new GuardianFortressBondPetDrEffect();

    @Test
    void reducesDamageForBondedGuardian() {
        ServerPlayerEntity owner = Mockito.mock(ServerPlayerEntity.class);
        MobEntity mockPet = Mockito.mock(MobEntity.class);
        ServerWorld world = Mockito.mock(ServerWorld.class);
        MinecraftServer server = Mockito.mock(MinecraftServer.class);

        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        Mockito.when(owner.getWorld()).thenReturn(world);
        Mockito.when(owner.getUuid()).thenReturn(ownerId);
        Mockito.when(owner.squaredDistanceTo(Mockito.any(MobEntity.class))).thenReturn(1.0);

        Mockito.when(mockPet.getWorld()).thenReturn(world);
        Mockito.when(mockPet.getUuid()).thenReturn(petId);
        Mockito.when(mockPet.isAlive()).thenReturn(true);

        Mockito.when(world.getTime()).thenReturn(200L);
        Mockito.when(world.getServer()).thenReturn(server);
        RegistryKey<net.minecraft.world.World> worldKey = Mockito.mock(RegistryKey.class);
        Mockito.when(world.getRegistryKey()).thenReturn(worldKey);

        Mockito.when(server.getTicks()).thenReturn(200);

        PetComponent component = Mockito.mock(PetComponent.class);
        Mockito.when(component.getOwner()).thenReturn(owner);
        Mockito.when(component.isOwnedBy(owner)).thenReturn(true);
        Mockito.when(component.getPetEntity()).thenReturn(mockPet);

        Mockito.doNothing().when(component).setStateData(Mockito.anyString(), Mockito.any());
        Mockito.doNothing().when(component).clearStateData(Mockito.anyString());

        try (MockedStatic<PetComponent> petComponentStatic = Mockito.mockStatic(PetComponent.class);
             MockedStatic<PlayerTickDispatcher> tickDispatcherStatic = Mockito.mockStatic(PlayerTickDispatcher.class)) {
            petComponentStatic.when(() -> PetComponent.get(mockPet)).thenReturn(component);
            tickDispatcherStatic.when(() -> PlayerTickDispatcher.requestImmediateRun(Mockito.eq(owner), Mockito.any(PlayerTickListener.class)))
                .thenAnswer(invocation -> null);

            assertTrue(GuardianFortressBondManager.activateBond(mockPet, owner, 0.5, 200));

            DamageInterceptionResult interception = new DamageInterceptionResult(8.0);
            TriggerContext trigger = new TriggerContext(world, mockPet, owner, "pet_incoming_damage")
                .withDamageContext(null, 8.0, false, interception);
            EffectContext context = new EffectContext(world, mockPet, owner, trigger);

            assertTrue(effect.execute(context));
            assertEquals(4.0, interception.getRemainingDamageAmount(), 1.0E-6);
        } finally {
            Mockito.when(owner.getWorld()).thenReturn(null);
            GuardianFortressBondManager.clearBond(owner);
        }
    }
}
