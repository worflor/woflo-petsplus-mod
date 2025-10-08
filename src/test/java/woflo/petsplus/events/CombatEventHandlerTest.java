package woflo.petsplus.events;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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

/**
 * Simplified coverage for the combat event handler that exercises the public helper logic
 * without attempting to construct live Minecraft server instances.
 */
class CombatEventHandlerTest {

    @Test
    void ownerFallDamageDispatchesAbilityPayload() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerWorld world = mock(ServerWorld.class);
        when(world.getServer()).thenReturn(server);
        when(world.getTime()).thenReturn(200L);

        ServerPlayerEntity owner = mock(ServerPlayerEntity.class);
        when(owner.getEntityWorld()).thenReturn(world);
        when(owner.isAlive()).thenReturn(true);
        when(owner.isRemoved()).thenReturn(false);
        when(owner.getEntityPos()).thenReturn(Vec3d.ZERO);
        when(owner.getHealth()).thenReturn(20f);
        when(owner.getMaxHealth()).thenReturn(20f);
        UUID ownerId = UUID.randomUUID();
        when(owner.getUuid()).thenReturn(ownerId);
        when(owner.getUuidAsString()).thenReturn(ownerId.toString());
        owner.fallDistance = 6.5f;

        StateManager stateManager = mock(StateManager.class);
        PetSwarmIndex swarmIndex = mock(PetSwarmIndex.class);
        when(stateManager.getSwarmIndex()).thenReturn(swarmIndex);
        when(stateManager.getOwnerState(owner)).thenReturn(mock(OwnerCombatState.class));
        when(swarmIndex.snapshotOwner(ownerId)).thenReturn(List.of());

        List<String> events = new ArrayList<>();
        List<Map<String, Object>> payloads = new ArrayList<>();
        doAnswer(invocation -> {
            events.add(invocation.getArgument(1));
            payloads.add(invocation.getArgument(2));
            return null;
        }).when(stateManager).fireAbilityTrigger(any(ServerPlayerEntity.class), any(String.class), any());

        DamageSource fallDamage = mock(DamageSource.class);
        when(fallDamage.isOf(DamageTypes.FALL)).thenReturn(true);
        lenient().when(fallDamage.getAttacker()).thenReturn(null);
        lenient().when(fallDamage.getSource()).thenReturn(null);

        try (MockedStatic<StateManager> stateManagerStatic = mockStatic(StateManager.class);
             MockedStatic<OwnerCombatState> ownerStateStatic = mockStatic(OwnerCombatState.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(stateManager);
            ownerStateStatic.when(() -> OwnerCombatState.getOrCreate(owner))
                .thenReturn(stateManager.getOwnerState(owner));

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
        MinecraftServer server = mock(MinecraftServer.class);
        ServerWorld world = mock(ServerWorld.class);
        when(world.getServer()).thenReturn(server);
        when(world.getTime()).thenReturn(120L);

        ServerPlayerEntity owner = mock(ServerPlayerEntity.class);
        when(owner.getEntityWorld()).thenReturn(world);
        when(owner.isAlive()).thenReturn(true);
        when(owner.isRemoved()).thenReturn(false);
        when(owner.getEntityPos()).thenReturn(Vec3d.ZERO);
        when(owner.getHealth()).thenReturn(18f);
        when(owner.getMaxHealth()).thenReturn(20f);
        UUID ownerId = UUID.randomUUID();
        when(owner.getUuid()).thenReturn(ownerId);
        when(owner.getUuidAsString()).thenReturn(ownerId.toString());

        LivingEntity target = mock(LivingEntity.class);
        when(target.getMaxHealth()).thenReturn(30f);

        StateManager stateManager = mock(StateManager.class);
        PetSwarmIndex swarmIndex = mock(PetSwarmIndex.class);
        when(stateManager.getSwarmIndex()).thenReturn(swarmIndex);
        when(stateManager.getOwnerState(owner)).thenReturn(mock(OwnerCombatState.class));
        when(swarmIndex.snapshotOwner(ownerId)).thenReturn(List.of());

        List<String> events = new ArrayList<>();
        List<Map<String, Object>> payloads = new ArrayList<>();
        doAnswer(invocation -> {
            events.add(invocation.getArgument(1));
            payloads.add(invocation.getArgument(2));
            return null;
        }).when(stateManager).fireAbilityTrigger(any(ServerPlayerEntity.class), any(String.class), any());

        PersistentProjectileEntity projectile = mock(PersistentProjectileEntity.class);
        when(projectile.getOwner()).thenReturn(owner);
        when(projectile.getType()).thenReturn((EntityType) EntityType.ARROW);
        when(projectile.isCritical()).thenReturn(false);

        DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.getSource()).thenReturn(projectile);
        lenient().when(damageSource.getAttacker()).thenReturn(owner);

        try (MockedStatic<StateManager> stateManagerStatic = mockStatic(StateManager.class);
             MockedStatic<OwnerCombatState> ownerStateStatic = mockStatic(OwnerCombatState.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(stateManager);
            ownerStateStatic.when(() -> OwnerCombatState.getOrCreate(owner))
                .thenReturn(stateManager.getOwnerState(owner));

            invokeHandleProjectileDamage(owner, target, 5.0f, projectile);
        }

        int idx = events.indexOf("owner_shot_projectile");
        assertTrue(idx >= 0, "Expected owner_shot_projectile trigger");
        Map<String, Object> payload = payloads.get(idx);
        assertNotNull(payload, "Projectile payload should not be null");
        assertEquals("minecraft:arrow", payload.get("projectile_type"), "Projectile type should be derived from identifier");
    }

    @Test
    void populateProjectileMetadata() {
        Map<String, Object> payload = new java.util.HashMap<>();
        Identifier arrowId = Identifier.of("minecraft", "arrow");
        CombatEventHandler.populateProjectileMetadata(payload, arrowId, null);

        assertEquals("minecraft:arrow", payload.get("projectile_type"), "Type string should use identifier value");
        assertEquals("minecraft:arrow", payload.get("projectile_type_id"), "Type id should match identifier string");
        assertEquals("minecraft:arrow", payload.get("projectile_id"), "Legacy projectile_id should mirror identifier");
        assertEquals(arrowId, payload.get("projectile_identifier"), "Identifier instance should be stored");
        assertEquals("arrow", payload.get("projectile_type_no_namespace"), "Namespace-stripped variant should be present");

        ServerWorld world = mock(ServerWorld.class);
        ServerPlayerEntity owner = mock(ServerPlayerEntity.class);
        TriggerContext context = new TriggerContext(world, null, owner, "owner_shot_projectile");
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            context.withData(entry.getKey(), entry.getValue());
        }
        boolean matches = ownerShotProjectileMatches(context, "arrow");
        assertTrue(matches, () -> "OwnerShotProjectile trigger with projectile_type=arrow should activate, payload=" + payload);
    }

    @Test
    void ownerShotProjectileMatcherHandlesFallbacks() {
        ServerWorld world = mock(ServerWorld.class);
        ServerPlayerEntity owner = mock(ServerPlayerEntity.class);
        TriggerContext context = new TriggerContext(world, null, owner, "owner_shot_projectile");
        context.withData("projectile_identifier", Identifier.of("minecraft", "snowball"));

        assertTrue(ownerShotProjectileMatches(context, "snowball"), "Identifier fallback should match");
        assertTrue(ownerShotProjectileMatches(context, "minecraft:snowball"), "Fully qualified filter should match");

        context.withData("projectile_identifier", Identifier.of("minecraft", "egg"));
        assertFalse(ownerShotProjectileMatches(context, "arrow"), "Mismatched identifiers should not match");
    }

    private static void invokeHandleOwnerDamageReceived(PlayerEntity owner,
                                                        DamageSource damageSource,
                                                        float amount) throws Exception {
        Method method = CombatEventHandler.class.getDeclaredMethod(
            "handleOwnerDamageReceived",
            PlayerEntity.class,
            DamageSource.class,
            float.class,
            boolean.class
        );
        method.setAccessible(true);
        method.invoke(null, owner, damageSource, amount, false);
    }

    private static void invokeHandleProjectileDamage(PlayerEntity shooter,
                                                     LivingEntity target,
                                                     float damage,
                                                     PersistentProjectileEntity projectile) throws Exception {
        Method method = CombatEventHandler.class.getDeclaredMethod(
            "handleProjectileDamage",
            PlayerEntity.class,
            LivingEntity.class,
            float.class,
            PersistentProjectileEntity.class
        );
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
