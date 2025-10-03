package woflo.petsplus.events;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetSwarmIndex;
import woflo.petsplus.state.StateManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CombatEventHandlerTest {

    @Test
    void ownerFallDamageDispatchesAbilityPayload() throws Exception {
        MinecraftServer server = new MinecraftServer();
        ServerWorld world = new ServerWorld(server);
        world.setTime(200L);
        ServerPlayerEntity owner = new ServerPlayerEntity(world);
        owner.setMaxHealth(20f);
        owner.setHealth(20f);
        owner.fallDistance = 6.5f;

        StateManager stateManager = mock(StateManager.class);
        PetSwarmIndex swarmIndex = mock(PetSwarmIndex.class);
        when(stateManager.getSwarmIndex()).thenReturn(swarmIndex);
        lenient().when(swarmIndex.snapshotOwner(any())).thenReturn(List.of());
        OwnerCombatState ownerState = new OwnerCombatState(owner);
        when(stateManager.getOwnerState(owner)).thenReturn(ownerState);

        List<String> events = new ArrayList<>();
        List<Map<String, Object>> payloads = new ArrayList<>();
        doAnswer(invocation -> {
            events.add(invocation.getArgument(1));
            payloads.add(invocation.getArgument(2));
            return null;
        }).when(stateManager).fireAbilityTrigger(any(ServerPlayerEntity.class), any(String.class), any());

        try (MockedStatic<StateManager> stateManagerStatic = mockStatic(StateManager.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(stateManager);

            DamageSource fallDamage = mock(DamageSource.class);
            when(fallDamage.isOf(DamageTypes.FALL)).thenReturn(true);
            lenient().when(fallDamage.getAttacker()).thenReturn(null);
            lenient().when(fallDamage.getSource()).thenReturn(null);

            invokeHandleOwnerDamageReceived(owner, fallDamage, 3.5f);
        }

        int idx = events.indexOf("owner_took_fall_damage");
        assertTrue(idx >= 0, "Expected owner_took_fall_damage trigger");
        Map<String, Object> payload = payloads.get(idx);
        assertNotNull(payload, "Fall damage payload should not be null");
        assertEquals(3.5d, (double) payload.get("damage"), 1.0e-4, "Damage amount should match event");
        assertEquals(6.5d, (double) payload.get("fall_distance"), 1.0e-4, "Fall distance should be recorded");
    }

    @Test
    void projectileDamageEmitsOwnerShotProjectile() throws Exception {
        MinecraftServer server = new MinecraftServer();
        ServerWorld world = new ServerWorld(server);
        world.setTime(120L);
        ServerPlayerEntity owner = new ServerPlayerEntity(world);
        owner.setMaxHealth(20f);
        owner.setHealth(18f);

        LivingEntity target = new LivingEntity(world);
        target.setMaxHealth(30f);
        target.setHealth(30f);

        StateManager stateManager = mock(StateManager.class);
        PetSwarmIndex swarmIndex = mock(PetSwarmIndex.class);
        when(stateManager.getSwarmIndex()).thenReturn(swarmIndex);
        lenient().when(swarmIndex.snapshotOwner(any())).thenReturn(List.of());
        OwnerCombatState ownerState = new OwnerCombatState(owner);
        when(stateManager.getOwnerState(owner)).thenReturn(ownerState);

        List<String> events = new ArrayList<>();
        List<Map<String, Object>> payloads = new ArrayList<>();
        doAnswer(invocation -> {
            events.add(invocation.getArgument(1));
            payloads.add(invocation.getArgument(2));
            return null;
        }).when(stateManager).fireAbilityTrigger(any(ServerPlayerEntity.class), any(String.class), any());

        PersistentProjectileEntity projectile = new PersistentProjectileEntity(world);
        projectile.setCritical(false);

        try (MockedStatic<StateManager> stateManagerStatic = mockStatic(StateManager.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(stateManager);
            invokeHandleProjectileDamage(owner, target, 4.0f, projectile);
        }

        int idx = events.indexOf("owner_shot_projectile");
        assertTrue(idx >= 0, "Expected owner_shot_projectile trigger");
        Map<String, Object> payload = payloads.get(idx);
        assertNotNull(payload, "Projectile payload should not be null");
        assertEquals(Boolean.FALSE, payload.get("projectile_critical"), "Payload should reflect non-critical shot");
        assertEquals(payload.get("projectile_type"), payload.get("projectile_type_id"),
            "Projectile type and id strings should match");
        assertEquals(payload.get("projectile_type"), payload.get("projectile_id"),
            "Legacy projectile_id should mirror type string");
        assertEquals(projectile, payload.get("projectile_entity"), "Projectile entity should be forwarded");
        assertEquals(target, payload.get("victim"), "Victim entity should be included in payload");
        assertEquals(4.0d, (double) payload.get("damage"), 1.0e-4, "Damage should be captured in payload");

        TriggerContext context = new TriggerContext(world, null, owner, "owner_shot_projectile");
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            context.withData(entry.getKey(), entry.getValue());
        }
        assertEquals(target, context.getVictim(), "Trigger context should expose projectile victim");
        assertEquals(4.0d, context.getDamage(), 1.0e-4, "Trigger context should expose projectile damage");
        assertFalse(ownerShotProjectileMatches(context, "arrow"),
            "Unknown projectile type should not match arrow filter");
    }

    @Test
    void populateProjectileMetadataProducesArrowIdentifier() {
        Map<String, Object> payload = new java.util.HashMap<>();
        Identifier arrowId = Identifier.of("minecraft", "arrow");
        CombatEventHandler.populateProjectileMetadata(payload, arrowId, null);

        assertEquals("minecraft:arrow", payload.get("projectile_type"), "Type string should use identifier value");
        assertEquals("minecraft:arrow", payload.get("projectile_type_id"), "Type id should match identifier string");
        assertEquals("minecraft:arrow", payload.get("projectile_id"), "Legacy projectile_id should mirror identifier");
        assertEquals(arrowId, payload.get("projectile_identifier"), "Identifier instance should be stored");
        assertEquals("arrow", payload.get("projectile_type_no_namespace"), "Namespace-stripped variant should be present");

        MinecraftServer server = new MinecraftServer();
        ServerWorld world = new ServerWorld(server);
        ServerPlayerEntity owner = new ServerPlayerEntity(world);
        TriggerContext context = new TriggerContext(world, null, owner, "owner_shot_projectile");
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            context.withData(entry.getKey(), entry.getValue());
        }
        boolean matches = ownerShotProjectileMatches(context, "arrow");
        assertTrue(matches, () -> "OwnerShotProjectile trigger with projectile_type=arrow should activate, payload=" + payload);
    }

    @Test
    void populateProjectileMetadataSanitizesRawStrings() {
        Map<String, Object> payload = new java.util.HashMap<>();
        CombatEventHandler.populateProjectileMetadata(payload, null, "EntityType[minecraft:egg]");

        assertEquals("minecraft:egg", payload.get("projectile_type"), "Raw projectile strings should be sanitized");
        assertEquals("egg", payload.get("projectile_type_no_namespace"), "Sanitized payload should expose stripped path");
        assertEquals("minecraft:egg", payload.get("projectile_type_id"));
        assertEquals("minecraft:egg", payload.get("projectile_id"));
    }

    private static void invokeHandleOwnerDamageReceived(PlayerEntity owner, DamageSource damageSource, float amount) throws Exception {
        Method method = CombatEventHandler.class.getDeclaredMethod("handleOwnerDamageReceived", PlayerEntity.class, DamageSource.class, float.class);
        method.setAccessible(true);
        method.invoke(null, owner, damageSource, amount);
    }

    private static void invokeHandleProjectileDamage(PlayerEntity shooter, LivingEntity target, float damage, PersistentProjectileEntity projectile) throws Exception {
        Method method = CombatEventHandler.class.getDeclaredMethod("handleProjectileDamage", PlayerEntity.class, LivingEntity.class, float.class, PersistentProjectileEntity.class);
        method.setAccessible(true);
        method.invoke(null, shooter, target, damage, projectile);
    }

    private static boolean ownerShotProjectileMatches(TriggerContext context, String filter) {
        if (!"owner_shot_projectile".equals(context.getEventType())) {
            return false;
        }
        String projectileType = context.getData("projectile_type", String.class);
        if (projectileType == null) {
            projectileType = context.getData("projectile_id", String.class);
        }
        if (projectileType == null) {
            Identifier identifier = context.getData("projectile_identifier", Identifier.class);
            projectileType = identifier != null ? identifier.toString() : null;
        }
        if (projectileType == null) {
            projectileType = context.getData("projectile_type_no_namespace", String.class);
            if (projectileType == null) {
                return false;
            }
            projectileType = projectileType.toLowerCase(Locale.ROOT);
            return filter.toLowerCase(Locale.ROOT).equals(projectileType);
        }
        projectileType = projectileType.toLowerCase(Locale.ROOT);
        String normalizedFilter = filter.toLowerCase(Locale.ROOT);
        if (normalizedFilter.equals(projectileType)) {
            return true;
        }
        int colonIndex = projectileType.indexOf(':');
        if (colonIndex >= 0 && colonIndex + 1 < projectileType.length()) {
            String withoutNamespace = projectileType.substring(colonIndex + 1);
            if (normalizedFilter.equals(withoutNamespace)) {
                return true;
            }
        }
        return false;
    }
}
