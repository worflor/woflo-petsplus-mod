package woflo.petsplus.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.component.PetsplusComponents;
import woflo.petsplus.stats.PetAttributeManager;

class PetComponentSerializationTest {

    private static final Identifier GUARDIAN_ROLE = Identifier.of("petsplus", "guardian");
    private static final Identifier ABILITY_ID = Identifier.of("petsplus", "blink");
    private static final UUID PET_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID OWNER_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174999");

    @Test
    void nbtRoundTripPreservesCoreState() {
        PetComponent component = newComponent();
        NbtCompound source = sampleNbt();
        component.readFromNbt(source);

        NbtCompound encoded = new NbtCompound();
        try (MockedStatic<PetAttributeManager> ignored = mockStatic(PetAttributeManager.class)) {
            ignored.when(() -> PetAttributeManager.applyAttributeModifiers(any(), any())).thenAnswer(invocation -> null);
            component.writeToNbt(encoded);
        }

        PetComponent restored = newComponent();
        restored.readFromNbt(encoded);

        assertEquals(component.getRoleId(), restored.getRoleId(), "Role identifier should persist");
        assertEquals(component.isPerched(), restored.isPerched(), "Perch flag should persist");
        assertEquals(component.getLevel(), restored.getLevel(), "Level should persist");
        assertEquals(component.getExperience(), restored.getExperience(), "Experience should persist");
        assertEquals(component.copyCooldownSnapshot(), restored.copyCooldownSnapshot(), "Cooldown map should persist");
        assertEquals(component.getStateData("bondStrength", Long.class, 0L),
            restored.getStateData("bondStrength", Long.class, 0L), "State data should persist");
        assertEquals(component.getHistoryForOwner(OWNER_UUID).size(),
            restored.getHistoryForOwner(OWNER_UUID).size(), "History entries should persist");
        assertEquals(component.getXpFlashStartTick(), restored.getXpFlashStartTick(), "XP flash tick should persist");
    }

    @Test
    void petDataCodecRoundTrip() {
        PetsplusComponents.PetData data = PetsplusComponents.PetData.empty()
            .withRole(GUARDIAN_ROLE)
            .withLastAttackTick(128L)
            .withPerched(true)
            .withCooldown("burst", 512L);

        var encoded = PetsplusComponents.PetData.CODEC.encodeStart(NbtOps.INSTANCE, data)
            .getOrThrow(IllegalStateException::new);
        PetsplusComponents.PetData decoded = PetsplusComponents.PetData.CODEC.parse(NbtOps.INSTANCE, encoded)
            .getOrThrow(IllegalStateException::new);

        assertEquals(data, decoded, "PetData codec should round-trip without loss");
        assertTrue(decoded.cooldowns().containsKey("burst"), "Decoded cooldown map should retain entries");
    }

    private static PetComponent newComponent() {
        MobEntity pet = mock(MobEntity.class);
        ServerWorld world = mock(ServerWorld.class);
        when(world.getTime()).thenReturn(200L);
        when(pet.getWorld()).thenReturn(world);
        when(pet.getUuid()).thenReturn(PET_UUID);
        when(pet.getUuidAsString()).thenReturn(PET_UUID.toString());
        // Use lenient stubbing for type
        when(pet.getType()).thenAnswer(invocation -> EntityType.WOLF);
        return new PetComponent(pet);
    }

    private static NbtCompound sampleNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("role", GUARDIAN_ROLE.toString());
        nbt.putLong("lastAttackTick", 128L);
        nbt.putBoolean("isPerched", true);
        nbt.putInt("level", 3);
        nbt.putInt("experience", 42);
        nbt.putLong("xpFlashStartTick", 640L);

        NbtCompound milestones = new NbtCompound();
        milestones.putBoolean("3", true);
        nbt.put("milestones", milestones);

        NbtCompound abilities = new NbtCompound();
        abilities.putBoolean(ABILITY_ID.toString(), true);
        nbt.put("unlockedAbilities", abilities);

        NbtCompound statBoosts = new NbtCompound();
        statBoosts.putFloat("health", 2.5f);
        nbt.put("permanentStatBoosts", statBoosts);

        NbtCompound cooldowns = new NbtCompound();
        cooldowns.putLong("burst", 512L);
        nbt.put("cooldowns", cooldowns);

        NbtCompound stateData = new NbtCompound();
        stateData.putLong("bondStrength", 9L);
        stateData.putInt(PetComponent.StateKeys.PET_COUNT, 2);
        stateData.putBoolean("isSpecial", true);
        nbt.put("stateData", stateData);

        NbtList history = new NbtList();
        NbtCompound event = new NbtCompound();
        event.putLong("t", 400L);
        event.putString("e", "petsplus:test_event");
        event.putString("o", OWNER_UUID.toString());
        event.putString("n", "TestOwner");
        event.putString("d", "{}");
        history.add(event);
        nbt.put("petHistory", history);

        return nbt;
    }
}
