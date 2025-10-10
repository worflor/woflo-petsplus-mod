package woflo.petsplus.state.emotions;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.lang.ReflectiveOperationException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import woflo.petsplus.config.MoodEngineConfig;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.events.EmotionsEventHandler;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PetMoodEngineTest {

    @Test
    void frustrationDefaultsFavorAngryMood() {
        Map<PetComponent.Mood, Float> defaults =
                PetMoodEngine.getAuthoredEmotionToMoodDefaults(PetComponent.Emotion.FRUSTRATION);

        assertNotNull(defaults, "Defaults should exist for frustration emotion");

        float angryWeight = defaults.getOrDefault(PetComponent.Mood.ANGRY, 0f);
        float strongestOther = defaults.entrySet().stream()
                .filter(entry -> entry.getKey() != PetComponent.Mood.ANGRY)
                .map(Map.Entry::getValue)
                .max(Float::compare)
                .orElse(0f);

        assertTrue(angryWeight > strongestOther,
                "Angry defaults should prioritise the angry mood over other moods");

        float total = (float) defaults.values().stream()
                .mapToDouble(Float::doubleValue)
                .sum();
        assertEquals(1f, total, 1.0e-4f, "Default weights should normalise to 1");
    }

    @Test
    void resolveUsesDefaultsWhenConfigMissing() {
        EnumMap<PetComponent.Mood, Float> resolved =
                PetMoodEngine.resolveEmotionToMoodWeights(null, PetComponent.Emotion.FRUSTRATION);
        Map<PetComponent.Mood, Float> defaults =
                PetMoodEngine.getAuthoredEmotionToMoodDefaults(PetComponent.Emotion.FRUSTRATION);

        assertEquals(defaults, resolved,
                "Resolved weights should fall back to authored defaults when config is absent");
    }

    @Test
    void contagionHelperClampsToDynamicCap() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        when(parent.getBondStrength()).thenReturn(3200L);
        when(parent.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class)).thenReturn(null);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class, Long.MIN_VALUE))
                .thenReturn(Long.MIN_VALUE);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_SENSITIZED_STREAK, Integer.class, 0))
                .thenReturn(0);
        when(parent.getNatureVolatilityMultiplier()).thenReturn(1f);
        when(parent.getNatureResilienceMultiplier()).thenReturn(1f);
        when(parent.getNatureContagionModifier()).thenReturn(1f);
        when(parent.getNatureGuardModifier()).thenReturn(1f);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.addContagionShare(PetComponent.Emotion.UBUNTU, 1.0f, 1200L, 0.8f);

        Object record = getEmotionRecord(engine, PetComponent.Emotion.UBUNTU);
        float share = getContagionShare(record);

        float cap = invokeContagionCap(engine, 0.8f);
        assertEquals(cap, share, 1.0e-4f, "Contagion share should clamp to dynamic cap");
    }

    @Test
    void contagionShareDecaysWhenNoPackNearby() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        when(parent.getBondStrength()).thenReturn(2400L);
        when(parent.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class)).thenReturn(null);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class, Long.MIN_VALUE))
                .thenReturn(Long.MIN_VALUE);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_SENSITIZED_STREAK, Integer.class, 0))
                .thenReturn(0);
        when(parent.getNatureVolatilityMultiplier()).thenReturn(1f);
        when(parent.getNatureResilienceMultiplier()).thenReturn(1f);
        when(parent.getNatureContagionModifier()).thenReturn(1f);
        when(parent.getNatureGuardModifier()).thenReturn(1f);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.addContagionShare(PetComponent.Emotion.CHEERFUL, 0.2f, 2000L, 0.7f);

        Object record = getEmotionRecord(engine, PetComponent.Emotion.CHEERFUL);
        float initial = getContagionShare(record);

        var refreshMethod = PetMoodEngine.class.getDeclaredMethod("refreshContextGuards",
                record.getClass(), long.class, long.class);
        refreshMethod.setAccessible(true);
        refreshMethod.invoke(engine, record, 2240L, 240L);

        float after = getContagionShare(record);
        assertTrue(after < initial, "Contagion share should decay when no new contributions arrive");
    }

    @Test
    void cleanupPreservesContagionOnlyEmotions() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        when(parent.getNatureVolatilityMultiplier()).thenReturn(1f);
        when(parent.getNatureResilienceMultiplier()).thenReturn(1f);
        when(parent.getNatureContagionModifier()).thenReturn(1f);
        when(parent.getNatureGuardModifier()).thenReturn(1f);
        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.addContagionShare(PetComponent.Emotion.UBUNTU, 0.3f, 1000L, 0.9f);

        var cachedEpsilonField = PetMoodEngine.class.getDeclaredField("cachedEpsilon");
        cachedEpsilonField.setAccessible(true);
        float cachedEpsilon = cachedEpsilonField.getFloat(engine);
        var baseEpsilonField = PetMoodEngine.class.getDeclaredField("EPSILON");
        baseEpsilonField.setAccessible(true);
        float baseEpsilon = baseEpsilonField.getFloat(null);
        float epsilon = Math.max(baseEpsilon, cachedEpsilon);

        var collectMethod = PetMoodEngine.class.getDeclaredMethod("collectActiveRecords", long.class, float.class);
        collectMethod.setAccessible(true);
        collectMethod.invoke(engine, 1240L, epsilon);

        Object record = getEmotionRecord(engine, PetComponent.Emotion.UBUNTU);
        float share = getContagionShare(record);
        assertTrue(share > 0f,
                "Contagion share should persist through cleanup when it is the only contribution");
    }

    @Test
    void majorEmotionReceivesStimulusBias() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        when(parent.getBondStrength()).thenReturn(2600L);
        when(parent.getStateData(PetComponent.StateKeys.LAST_PET_TIME, Long.class)).thenReturn(null);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Long.class, Long.MIN_VALUE))
                .thenReturn(Long.MIN_VALUE);
        when(parent.getStateData(PetComponent.StateKeys.THREAT_SENSITIZED_STREAK, Integer.class, 0))
                .thenReturn(0);
        when(parent.getNatureVolatilityMultiplier()).thenReturn(1f);
        when(parent.getNatureResilienceMultiplier()).thenReturn(1f);
        when(parent.getNatureContagionModifier()).thenReturn(1f);
        when(parent.getNatureGuardModifier()).thenReturn(1f);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.CHEERFUL, 0.4f,
                PetComponent.Emotion.RELIEF, 0.3f,
                PetComponent.Emotion.CHEERFUL, 0.2f));

        engine.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.CHEERFUL, 0.6f), 100L);
        engine.applyStimulus(new PetComponent.EmotionDelta(PetComponent.Emotion.ANGST, 0.6f), 100L);

        Object majorRecord = getEmotionRecord(engine, PetComponent.Emotion.CHEERFUL);
        Object neutralRecord = getEmotionRecord(engine, PetComponent.Emotion.ANGST);

        float majorIntensity = getIntensity(majorRecord);
        float neutralIntensity = getIntensity(neutralRecord);

        assertTrue(majorIntensity > neutralIntensity,
                "Major emotion should accumulate more intensity than neutral emotions when profile bias is applied");

        var weightBiasMethod = PetMoodEngine.class.getDeclaredMethod("getNatureWeightBias", PetComponent.Emotion.class);
        weightBiasMethod.setAccessible(true);

        float majorWeightBias = (float) weightBiasMethod.invoke(engine, PetComponent.Emotion.CHEERFUL);
        float neutralWeightBias = (float) weightBiasMethod.invoke(engine, PetComponent.Emotion.ANGST);

        assertTrue(majorWeightBias > neutralWeightBias,
                "Major emotion weight bias should exceed neutral emotion bias");
    }

    @Test
    void majorEmotionBoostsContagionSpread() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        when(parent.getBondStrength()).thenReturn(2400L);
        when(parent.getNatureVolatilityMultiplier()).thenReturn(1f);
        when(parent.getNatureResilienceMultiplier()).thenReturn(1f);
        when(parent.getNatureContagionModifier()).thenReturn(1f);
        when(parent.getNatureGuardModifier()).thenReturn(1f);

        PetMoodEngine engine = new PetMoodEngine(parent);
        engine.onNatureEmotionProfileChanged(new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.CHEERFUL, 0.35f,
                PetComponent.Emotion.RELIEF, 0.25f,
                PetComponent.Emotion.CHEERFUL, 0.2f));

        engine.addContagionShare(PetComponent.Emotion.CHEERFUL, 0.25f, 1200L, 0.9f);
        engine.addContagionShare(PetComponent.Emotion.ANGST, 0.25f, 1200L, 0.9f);

        Object majorRecord = getEmotionRecord(engine, PetComponent.Emotion.CHEERFUL);
        Object neutralRecord = getEmotionRecord(engine, PetComponent.Emotion.ANGST);

        float majorShare = getContagionShare(majorRecord);
        float neutralShare = getContagionShare(neutralRecord);

        assertTrue(majorShare > neutralShare,
                "Major emotion contagion should accumulate faster than neutral emotions when profile bias is active");
    }

    @Test
    void guardianVigilPersistsWhenOwnerRecentlyHurt() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put(PetComponent.StateKeys.OWNER_LAST_HURT_TICK, 100L);
        state.put(PetComponent.StateKeys.OWNER_LAST_HURT_SEVERITY, 0.6f);

        PetComponent parent = createParentWithState(state, mock(MobEntity.class), null);
        PetMoodEngine engine = new PetMoodEngine(parent);

        assertTrue(invokeHasOngoingCondition(engine, PetComponent.Emotion.GUARDIAN_VIGIL, 180L),
                "Guardian Vigil should persist while the stored owner danger window is active");
        assertTrue(invokeHasOngoingCondition(engine, PetComponent.Emotion.PROTECTIVE, 180L),
                "Protective should persist while the stored owner danger window is active");
    }

    @Test
    void guardianVigilPersistsForHarmfulStatusEffects() throws Exception {
        Map<String, Object> state = new HashMap<>();
        MobEntity pet = mock(MobEntity.class);
        PlayerEntity owner = mock(PlayerEntity.class);
        ServerWorld world = mock(ServerWorld.class);

        when(owner.getEntityWorld()).thenReturn(world);
        when(owner.isAlive()).thenReturn(true);
        when(owner.getHealth()).thenReturn(18f);
        when(owner.getMaxHealth()).thenReturn(20f);
        when(owner.isOnFire()).thenReturn(false);
        when(owner.getFireTicks()).thenReturn(0);
        when(owner.getFrozenTicks()).thenReturn(0);
        when(owner.getStatusEffects()).thenReturn(List.of(new StatusEffectInstance(StatusEffects.POISON, 200, 1)));

        PetComponent parent = createParentWithState(state, pet, owner);

        try (MockedStatic<OwnerCombatState> combat = Mockito.mockStatic(OwnerCombatState.class)) {
            combat.when(() -> OwnerCombatState.get(owner)).thenReturn(null);

            PetMoodEngine engine = new PetMoodEngine(parent);
            assertTrue(invokeHasOngoingCondition(engine, PetComponent.Emotion.GUARDIAN_VIGIL, 120L),
                "Guardian Vigil should persist while the owner is poisoned");
            assertTrue(state.containsKey(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_TICK),
                "Poison should record a hazard timestamp on the pet component");
        }
    }

    @Test
    void guardianDangerQueriesOwnerStateWithoutCopying() {
        PlayerEntity owner = mock(PlayerEntity.class);
        ServerWorld world = mock(ServerWorld.class);
        when(owner.getEntityWorld()).thenReturn(world);

        OwnerCombatState combatState = mock(OwnerCombatState.class);
        StateManager manager = mock(StateManager.class);
        when(manager.getOwnerStateIfPresent(owner)).thenReturn(combatState);

        try (MockedStatic<StateManager> stateManagerStatic = Mockito.mockStatic(StateManager.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(manager);

            assertSame(combatState, OwnerCombatState.get(owner),
                "Owner combat state lookup should reuse the tracked instance");
            assertSame(combatState, OwnerCombatState.get(owner),
                "Repeated lookups should continue to reuse the tracked instance");

            Mockito.verify(manager, Mockito.times(2)).getOwnerStateIfPresent(owner);
            Mockito.verify(manager, Mockito.never()).getAllOwnerStates();
        }
    }

    @Test
    void guardianVigilClearsStoredStatusHazardAfterGrace() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_TICK, 100L);
        state.put(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_SEVERITY, 0.6f);

        PetComponent parent = createParentWithState(state, null, null);
        PetMoodEngine engine = new PetMoodEngine(parent);

        assertTrue(invokeHasOngoingCondition(engine, PetComponent.Emotion.PROTECTIVE, 240L),
            "Protective should persist while hazard grace remains active");

        assertFalse(invokeHasOngoingCondition(engine, PetComponent.Emotion.PROTECTIVE, 360L),
            "Protective should clear once the stored hazard window expires");
        assertFalse(state.containsKey(PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_TICK),
            "Expired hazard data should be purged after the grace window");
    }

    @Test
    void packSpiritPersistsWithNearbyAllies() throws Exception {
        Map<String, Object> state = new HashMap<>();
        MobEntity pet = mock(MobEntity.class);
        ServerWorld world = mock(ServerWorld.class);
        when(pet.getEntityWorld()).thenReturn(world);

        PlayerEntity owner = mock(PlayerEntity.class);
        UUID ownerId = UUID.randomUUID();
        when(owner.getUuid()).thenReturn(ownerId);
        when(owner.getEntityWorld()).thenReturn(world);
        when(owner.isAlive()).thenReturn(true);

        PetComponent parent = createParentWithState(state, pet, owner);
        when(parent.getOwnerUuid()).thenReturn(ownerId);
        when(parent.getBondStrength()).thenReturn(4800L);

        PetSwarmIndex.SwarmEntry entry = mock(PetSwarmIndex.SwarmEntry.class);
        MobEntity ally = mock(MobEntity.class);
        when(ally.isAlive()).thenReturn(true);
        when(ally.isRemoved()).thenReturn(false);
        when(entry.pet()).thenReturn(ally);
        PetComponent allyComponent = mock(PetComponent.class);
        when(allyComponent.isOwnedBy(owner)).thenReturn(true);
        when(allyComponent.getRoleType(false)).thenReturn(PetRoleType.GUARDIAN);
        when(allyComponent.getMoodStrength(PetComponent.Mood.PROTECTIVE)).thenReturn(0.8f);
        when(allyComponent.getMoodStrength(PetComponent.Mood.FOCUSED)).thenReturn(0.2f);
        when(allyComponent.isInCombat()).thenReturn(true);
        when(entry.component()).thenReturn(allyComponent);
        when(entry.x()).thenReturn(2.0);
        when(entry.y()).thenReturn(0.0);
        when(entry.z()).thenReturn(1.0);

        StateManager manager = mock(StateManager.class);
        PetSwarmIndex swarmIndex = mock(PetSwarmIndex.class);
        when(manager.getSwarmIndex()).thenReturn(swarmIndex);
        when(swarmIndex.snapshotOwner(ownerId)).thenReturn(List.of(entry));

        try (MockedStatic<StateManager> stateManagerStatic = Mockito.mockStatic(StateManager.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(manager);

            PetMoodEngine engine = new PetMoodEngine(parent);
            assertTrue(invokeHasOngoingCondition(engine, PetComponent.Emotion.PACK_SPIRIT, 400L),
                    "Pack Spirit should remain active when a bonded ally is nearby");
            assertTrue(state.containsKey(PetComponent.StateKeys.PACK_LAST_NEARBY_TICK),
                    "Pack Spirit proximity should update cached state");
            assertEquals(1, state.get(PetComponent.StateKeys.PACK_LAST_NEARBY_ALLIES));
            assertTrue(((Number) state.get(PetComponent.StateKeys.PACK_LAST_NEARBY_WEIGHTED_STRENGTH)).floatValue() > 0.2f,
                "Pack Spirit should record weighted engagement from allies");
            assertTrue(((Number) state.get(PetComponent.StateKeys.PACK_LAST_ROLE_DIVERSITY)).floatValue() > 0f,
                "Pack Spirit should record role diversity for nearby allies");
        }
    }

    @Test
    void arcaneOverflowHonorsSurgeGraceWindow() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put(PetComponent.StateKeys.ARCANE_LAST_SURGE_TICK, 100L);
        state.put(PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, 0.5f);

        PetComponent parent = createParentWithState(state, mock(MobEntity.class), null);
        PetMoodEngine engine = new PetMoodEngine(parent);

        assertTrue(invokeHasOngoingCondition(engine, PetComponent.Emotion.ARCANE_OVERFLOW, 280L),
                "Arcane Overflow should persist while recent surge strength is within the linger window");
        assertFalse(invokeHasOngoingCondition(engine, PetComponent.Emotion.ARCANE_OVERFLOW, 400L),
                "Arcane Overflow should decay once the surge window expires");
    }

    @Test
    void arcaneAmbientScalesWithStructureDensity() throws Exception {
        Map<String, Object> state = new HashMap<>();
        MobEntity pet = mock(MobEntity.class);
        ServerWorld world = mock(ServerWorld.class);
        when(pet.getEntityWorld()).thenReturn(world);
        when(pet.getBlockPos()).thenReturn(BlockPos.ORIGIN);
        when(pet.getMainHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getOffHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getEquippedStack(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);
        when(pet.getStatusEffects()).thenReturn(Collections.emptyList());
        when(world.getRegistryKey()).thenReturn(World.NETHER);

        Map<BlockPos, BlockState> structures = new HashMap<>();
        when(world.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos queried = ((BlockPos) invocation.getArgument(0)).toImmutable();
            return structures.getOrDefault(queried, Blocks.AIR.getDefaultState());
        });

        PetComponent parent = createParentWithState(state, pet, null);
        PetMoodEngine engine = new PetMoodEngine(parent);

        var radiusField = PetMoodEngine.class.getDeclaredField("cachedArcaneOverflowAmbientScanRadius");
        radiusField.setAccessible(true);
        radiusField.setInt(engine, 2);

        var mysticField = PetMoodEngine.class.getDeclaredField("cachedArcaneAmbientMysticBonus");
        mysticField.setAccessible(true);
        mysticField.setFloat(engine, 0f);

        var maxField = PetMoodEngine.class.getDeclaredField("cachedArcaneAmbientStructureMaxEnergy");
        maxField.setAccessible(true);
        maxField.setFloat(engine, 1.0f);

        var sampleMethod = PetMoodEngine.class.getDeclaredMethod("sampleArcaneAmbient", ServerWorld.class, BlockPos.class);
        sampleMethod.setAccessible(true);

        structures.put(BlockPos.ORIGIN, Blocks.ENCHANTING_TABLE.getDefaultState());
        float single = (float) sampleMethod.invoke(engine, world, BlockPos.ORIGIN);

        structures.put(new BlockPos(1, 0, 0), Blocks.AMETHYST_BLOCK.getDefaultState());
        structures.put(new BlockPos(0, 0, 1), Blocks.BREWING_STAND.getDefaultState());
        float cluster = (float) sampleMethod.invoke(engine, world, BlockPos.ORIGIN);

        assertTrue(cluster > single,
            "Multiple nearby arcane structures should yield higher ambient energy than a lone source");
    }

    @Test
    void arcaneAmbientRewardsAdjacentStructures() throws Exception {
        Map<String, Object> state = new HashMap<>();
        MobEntity pet = mock(MobEntity.class);
        ServerWorld world = mock(ServerWorld.class);
        when(pet.getEntityWorld()).thenReturn(world);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        when(world.getTime()).thenReturn(0L);

        Map<BlockPos, BlockState> structures = new HashMap<>();
        when(world.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos queried = ((BlockPos) invocation.getArgument(0)).toImmutable();
            return structures.getOrDefault(queried, Blocks.AIR.getDefaultState());
        });
        when(world.getBlockEntity(any(BlockPos.class))).thenReturn(null);

        PetComponent parent = createParentWithState(state, pet, null);
        PetMoodEngine engine = new PetMoodEngine(parent);

        Field radiusField = PetMoodEngine.class.getDeclaredField("cachedArcaneOverflowAmbientScanRadius");
        radiusField.setAccessible(true);
        radiusField.setInt(engine, 3);

        Field mysticField = PetMoodEngine.class.getDeclaredField("cachedArcaneAmbientMysticBonus");
        mysticField.setAccessible(true);
        mysticField.setFloat(engine, 0f);

        Field baseField = PetMoodEngine.class.getDeclaredField("cachedArcaneAmbientStructureBaseWeight");
        baseField.setAccessible(true);
        baseField.setFloat(engine, 1.0f);

        Field maxField = PetMoodEngine.class.getDeclaredField("cachedArcaneAmbientStructureMaxEnergy");
        maxField.setAccessible(true);
        maxField.setFloat(engine, 1.0f);

        Field exponentField = PetMoodEngine.class.getDeclaredField("cachedArcaneAmbientDistanceExponent");
        exponentField.setAccessible(true);
        exponentField.setFloat(engine, 1.0f);

        java.lang.reflect.Method collectMethod = PetMoodEngine.class.getDeclaredMethod(
            "collectArcaneStructureEnergy", ServerWorld.class, BlockPos.class, int.class);
        collectMethod.setAccessible(true);

        BlockPos origin = BlockPos.ORIGIN;
        structures.put(origin.add(1, 0, 0), Blocks.ENCHANTING_TABLE.getDefaultState());
        float adjacent = (float) collectMethod.invoke(engine, world, origin, 3);

        structures.clear();
        structures.put(origin.add(3, 0, 0), Blocks.ENCHANTING_TABLE.getDefaultState());
        float distant = (float) collectMethod.invoke(engine, world, origin, 3);

        assertTrue(adjacent > distant,
            "Arcane ambient energy should reward closer structures with higher contributions");
    }

    @Test
    void arcaneAmbientScalesWithRespawnAnchorCharges() throws Exception {
        Map<String, Object> state = new HashMap<>();
        MobEntity pet = mock(MobEntity.class);
        ServerWorld world = mock(ServerWorld.class);
        when(pet.getEntityWorld()).thenReturn(world);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        when(world.getTime()).thenReturn(2400L);

        Map<BlockPos, BlockState> structures = new HashMap<>();
        when(world.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos queried = ((BlockPos) invocation.getArgument(0)).toImmutable();
            return structures.getOrDefault(queried, Blocks.AIR.getDefaultState());
        });
        when(world.getBlockEntity(any(BlockPos.class))).thenReturn(null);

        PetComponent parent = createParentWithState(state, pet, null);
        PetMoodEngine engine = new PetMoodEngine(parent);

        Field radiusField = PetMoodEngine.class.getDeclaredField("cachedArcaneOverflowAmbientScanRadius");
        radiusField.setAccessible(true);
        radiusField.setInt(engine, 2);

        Field baseField = PetMoodEngine.class.getDeclaredField("cachedArcaneAmbientStructureBaseWeight");
        baseField.setAccessible(true);
        baseField.setFloat(engine, 0.8f);

        Field emptyField = PetMoodEngine.class.getDeclaredField("cachedArcaneRespawnAnchorEmptyMultiplier");
        emptyField.setAccessible(true);
        emptyField.setFloat(engine, 0.05f);

        Field stepField = PetMoodEngine.class.getDeclaredField("cachedArcaneRespawnAnchorChargeStep");
        stepField.setAccessible(true);
        stepField.setFloat(engine, 0.28f);

        Field maxField = PetMoodEngine.class.getDeclaredField("cachedArcaneRespawnAnchorMaxMultiplier");
        maxField.setAccessible(true);
        maxField.setFloat(engine, 1.8f);

        java.lang.reflect.Method collectMethod = PetMoodEngine.class.getDeclaredMethod(
            "collectArcaneStructureEnergy", ServerWorld.class, BlockPos.class, int.class);
        collectMethod.setAccessible(true);

        BlockPos origin = BlockPos.ORIGIN;
        BlockState emptyAnchor = Blocks.RESPAWN_ANCHOR.getDefaultState()
            .with(RespawnAnchorBlock.CHARGES, 0);
        structures.put(origin, emptyAnchor);
        float emptyEnergy = (float) collectMethod.invoke(engine, world, origin, 2);

        BlockState chargedAnchor = Blocks.RESPAWN_ANCHOR.getDefaultState()
            .with(RespawnAnchorBlock.CHARGES, 4);
        structures.put(origin, chargedAnchor);
        float chargedEnergy = (float) collectMethod.invoke(engine, world, origin, 2);

        assertTrue(chargedEnergy > emptyEnergy,
            "Higher respawn anchor charges should amplify ambient arcane energy");
    }

    @Test
    void arcaneAmbientWeightsBeaconLevels() throws Exception {
        Map<String, Object> state = new HashMap<>();
        MobEntity pet = mock(MobEntity.class);
        ServerWorld world = mock(ServerWorld.class);
        when(pet.getEntityWorld()).thenReturn(world);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        when(world.getTime()).thenReturn(4800L);

        Map<BlockPos, BlockState> structures = new HashMap<>();
        when(world.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos queried = ((BlockPos) invocation.getArgument(0)).toImmutable();
            return structures.getOrDefault(queried, Blocks.AIR.getDefaultState());
        });
        AtomicReference<BlockEntity> beaconRef = new AtomicReference<>();
        when(world.getBlockEntity(any(BlockPos.class))).thenAnswer(invocation -> beaconRef.get());

        PetComponent parent = createParentWithState(state, pet, null);
        PetMoodEngine engine = new PetMoodEngine(parent);

        Field radiusField = PetMoodEngine.class.getDeclaredField("cachedArcaneOverflowAmbientScanRadius");
        radiusField.setAccessible(true);
        radiusField.setInt(engine, 2);

        Field baseField = PetMoodEngine.class.getDeclaredField("cachedArcaneAmbientStructureBaseWeight");
        baseField.setAccessible(true);
        baseField.setFloat(engine, 0.7f);

        Field beaconBaseField = PetMoodEngine.class.getDeclaredField("cachedArcaneBeaconBaseMultiplier");
        beaconBaseField.setAccessible(true);
        beaconBaseField.setFloat(engine, 0.4f);

        Field beaconStepField = PetMoodEngine.class.getDeclaredField("cachedArcaneBeaconPerLevelMultiplier");
        beaconStepField.setAccessible(true);
        beaconStepField.setFloat(engine, 0.3f);

        Field beaconMaxField = PetMoodEngine.class.getDeclaredField("cachedArcaneBeaconMaxMultiplier");
        beaconMaxField.setAccessible(true);
        beaconMaxField.setFloat(engine, 2.5f);

        java.lang.reflect.Method collectMethod = PetMoodEngine.class.getDeclaredMethod(
            "collectArcaneStructureEnergy", ServerWorld.class, BlockPos.class, int.class);
        collectMethod.setAccessible(true);

        BlockPos origin = BlockPos.ORIGIN;
        structures.put(origin, Blocks.BEACON.getDefaultState());

        beaconRef.set(new TestBeaconBlockEntity(origin, 1));
        float tierOneEnergy = (float) collectMethod.invoke(engine, world, origin, 2);

        beaconRef.set(new TestBeaconBlockEntity(origin, 4));
        float tierFourEnergy = (float) collectMethod.invoke(engine, world, origin, 2);

        assertTrue(tierFourEnergy > tierOneEnergy,
            "Beacon pyramid level should scale ambient arcane energy");
    }

    @Test
    void arcaneAmbientDampensInactiveSculkCatalysts() throws Exception {
        Map<String, Object> state = new HashMap<>();
        MobEntity pet = mock(MobEntity.class);
        ServerWorld world = mock(ServerWorld.class);
        when(pet.getEntityWorld()).thenReturn(world);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        when(world.getTime()).thenReturn(5000L);

        Map<BlockPos, BlockState> structures = new HashMap<>();
        when(world.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos queried = ((BlockPos) invocation.getArgument(0)).toImmutable();
            return structures.getOrDefault(queried, Blocks.AIR.getDefaultState());
        });
        AtomicReference<BlockEntity> catalystRef = new AtomicReference<>();
        when(world.getBlockEntity(any(BlockPos.class))).thenAnswer(invocation -> catalystRef.get());

        PetComponent parent = createParentWithState(state, pet, null);
        PetMoodEngine engine = new PetMoodEngine(parent);

        Field radiusField = PetMoodEngine.class.getDeclaredField("cachedArcaneOverflowAmbientScanRadius");
        radiusField.setAccessible(true);
        radiusField.setInt(engine, 2);

        Field baseField = PetMoodEngine.class.getDeclaredField("cachedArcaneAmbientStructureBaseWeight");
        baseField.setAccessible(true);
        baseField.setFloat(engine, 0.65f);

        Field recentField = PetMoodEngine.class.getDeclaredField("cachedArcaneCatalystRecentBloomTicks");
        recentField.setAccessible(true);
        recentField.setLong(engine, 300L);

        Field activeField = PetMoodEngine.class.getDeclaredField("cachedArcaneCatalystActiveMultiplier");
        activeField.setAccessible(true);
        activeField.setFloat(engine, 1.05f);

        Field inactiveField = PetMoodEngine.class.getDeclaredField("cachedArcaneCatalystInactiveMultiplier");
        inactiveField.setAccessible(true);
        inactiveField.setFloat(engine, 0.25f);

        java.lang.reflect.Method collectMethod = PetMoodEngine.class.getDeclaredMethod(
            "collectArcaneStructureEnergy", ServerWorld.class, BlockPos.class, int.class);
        collectMethod.setAccessible(true);

        BlockPos origin = BlockPos.ORIGIN;
        structures.put(origin, Blocks.SCULK_CATALYST.getDefaultState());

        catalystRef.set(new TestSculkCatalystBlockEntity(origin, 4980L));
        float recentEnergy = (float) collectMethod.invoke(engine, world, origin, 2);

        catalystRef.set(new TestSculkCatalystBlockEntity(origin, 4400L));
        float staleEnergy = (float) collectMethod.invoke(engine, world, origin, 2);

        assertTrue(recentEnergy > staleEnergy,
            "Recently-bloomed sculk catalysts should deliver stronger ambient energy than stale ones");
    }

    @Test
    void arcaneOverflowRecognizesPetBeneficialStatus() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put(PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, 0f);

        MobEntity pet = mock(MobEntity.class);
        when(pet.getStatusEffects()).thenReturn(List.of(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0)));
        when(pet.getMainHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getOffHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getEquippedStack(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);
        when(pet.getEntityWorld()).thenReturn(null);

        PetComponent parent = createParentWithState(state, pet, null);
        PetMoodEngine engine = new PetMoodEngine(parent);

        var minField = PetMoodEngine.class.getDeclaredField("cachedArcaneOverflowMinimumEnergy");
        minField.setAccessible(true);
        minField.setFloat(engine, 0.15f);

        assertTrue(invokeHasOngoingCondition(engine, PetComponent.Emotion.ARCANE_OVERFLOW, 40L),
            "Beneficial pet status effects should sustain Arcane Overflow without owner input");
        assertTrue(state.containsKey(PetComponent.StateKeys.ARCANE_LAST_SURGE_TICK),
            "Pet-driven arcane surges should stamp the last surge tick");
    }

    @Test
    void arcaneMomentumScalesWithBuffStrength() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put(PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, 0f);

        MobEntity pet = mock(MobEntity.class);
        when(pet.getStatusEffects()).thenReturn(List.of(new StatusEffectInstance(StatusEffects.REGENERATION, 80, 0)));
        when(pet.getMainHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getOffHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getEquippedStack(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);
        when(pet.getEntityWorld()).thenReturn(null);

        PetComponent parent = createParentWithState(state, pet, null);
        PetMoodEngine engine = new PetMoodEngine(parent);

        var minField = PetMoodEngine.class.getDeclaredField("cachedArcaneOverflowMinimumEnergy");
        minField.setAccessible(true);
        minField.setFloat(engine, 0.25f);

        assertFalse(invokeHasOngoingCondition(engine, PetComponent.Emotion.ARCANE_OVERFLOW, 40L),
            "Weak beneficial effects should not meet the arcane overflow threshold on their own");

        when(pet.getStatusEffects()).thenReturn(List.of(
            new StatusEffectInstance(StatusEffects.REGENERATION, 400, 2),
            new StatusEffectInstance(StatusEffects.STRENGTH, 300, 1)));

        assertTrue(invokeHasOngoingCondition(engine, PetComponent.Emotion.ARCANE_OVERFLOW, 60L),
            "Stacked high-amplifier buffs should drive arcane overflow above the threshold");
    }

    @Test
    void weightCacheReturnsImmutableSingletonPerEmotion() throws Exception {
        PetComponent parent = mock(PetComponent.class);
        PetMoodEngine engine = new PetMoodEngine(parent);

        var method = PetMoodEngine.class.getDeclaredMethod("getEmotionToMoodWeights", PetComponent.Emotion.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<PetComponent.Mood, Float> first = (Map<PetComponent.Mood, Float>) method.invoke(engine, PetComponent.Emotion.CHEERFUL);
        @SuppressWarnings("unchecked")
        Map<PetComponent.Mood, Float> second = (Map<PetComponent.Mood, Float>) method.invoke(engine, PetComponent.Emotion.CHEERFUL);

        assertSame(first, second, "Subsequent lookups should reuse cached map instances for the same emotion");
        assertThrows(UnsupportedOperationException.class,
                () -> first.put(PetComponent.Mood.CALM, 1.0f),
                "Cached mood weight map should be immutable");

        var generationField = PetMoodEngine.class.getDeclaredField("cachedConfigGeneration");
        generationField.setAccessible(true);
        generationField.setInt(engine, -1);

        @SuppressWarnings("unchecked")
        Map<PetComponent.Mood, Float> third = (Map<PetComponent.Mood, Float>) method.invoke(engine, PetComponent.Emotion.CHEERFUL);

        assertNotSame(first, third,
                "Invalidating the config cache should rebuild the mood weight map");
    }


    @Test
    void saudadeRequiresOwnerSeparationMetadata() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put(PetComponent.StateKeys.OWNER_LAST_NEARBY_TICK, 200L);
        state.put(PetComponent.StateKeys.OWNER_LAST_SEEN_TICK, 200L);
        state.put(PetComponent.StateKeys.OWNER_LAST_SEEN_DISTANCE, 32f);
        PetComponent parent = createParentWithState(state, null, null);

        PetMoodEngine engine = new PetMoodEngine(parent);

        assertTrue(invokeHasOngoingCondition(engine, PetComponent.Emotion.SAUDADE, 700L),
            "Saudade should persist when the owner has been absent beyond the grace window");

        state.put(PetComponent.StateKeys.OWNER_LAST_NEARBY_TICK, 680L);
        assertFalse(invokeHasOngoingCondition(engine, PetComponent.Emotion.SAUDADE, 700L),
            "Saudade should clear once the owner was recently nearby");
    }

    @Test
    void positiveCuesExtendContentAndCheerfulMoods() throws Exception {
        Map<String, Object> state = new HashMap<>();
        PetComponent parent = createParentWithState(state, null, null);
        PetMoodEngine engine = new PetMoodEngine(parent);

        long now = 1000L;
        state.put(PetComponent.StateKeys.LAST_PLAY_INTERACTION_TICK, now - 120L);
        state.put(PetComponent.StateKeys.LAST_PET_TIME, now - 50L);
        var positiveMethod = PetMoodEngine.class.getDeclaredMethod("hasPositiveComfort",
            PetComponent.Emotion.class, long.class);
        positiveMethod.setAccessible(true);
        assertTrue((boolean) positiveMethod.invoke(engine, PetComponent.Emotion.CONTENT, now),
            "Content should persist when a recent play cue is present");

        state.put(PetComponent.StateKeys.LAST_PLAY_INTERACTION_TICK, now - 600L);
        state.put(PetComponent.StateKeys.LAST_PET_TIME, now - 800L);
        assertFalse((boolean) positiveMethod.invoke(engine, PetComponent.Emotion.CONTENT, now),
            "Content should fade once positive cues expire");

        state.put(PetComponent.StateKeys.LAST_PET_TIME, now - 60L);
        state.put(PetComponent.StateKeys.LAST_PLAY_INTERACTION_TICK, now - 80L);
        assertTrue((boolean) positiveMethod.invoke(engine, PetComponent.Emotion.CHEERFUL, now),
            "Cheerful should remain while multiple strong cues are fresh");

        state.put(PetComponent.StateKeys.LAST_PLAY_INTERACTION_TICK, now - 500L);
        state.put(PetComponent.StateKeys.LAST_PET_TIME, now - 800L);
        state.put(PetComponent.StateKeys.LAST_FEED_TICK, now - 70L);
        assertFalse((boolean) positiveMethod.invoke(engine, PetComponent.Emotion.CHEERFUL, now),
            "Cheerful should require more than a single recent cue");
    }

    @Test
    void lonelinessAccountsForSocialAndPackPresence() throws Exception {
        long now = 2000L;
        Map<String, Object> state = new HashMap<>();
        state.put(PetComponent.StateKeys.OWNER_LAST_NEARBY_TICK, now - 1200L);
        state.put(PetComponent.StateKeys.OWNER_LAST_SEEN_TICK, now - 1200L);
        state.put(PetComponent.StateKeys.OWNER_LAST_SEEN_DISTANCE, 40f);
        state.put(PetComponent.StateKeys.LAST_SOCIAL_BUFFER_TICK, now - 40L);
        state.put(PetComponent.StateKeys.PACK_LAST_NEARBY_TICK, now - 900L);

        ServerWorld world = mock(ServerWorld.class);
        MobEntity pet = mock(MobEntity.class);
        when(pet.getEntityWorld()).thenReturn(world);
        when(pet.getBlockPos()).thenReturn(BlockPos.ORIGIN);
        when(pet.getX()).thenReturn(0.0);
        when(pet.getY()).thenReturn(0.0);
        when(pet.getZ()).thenReturn(0.0);
        when(pet.isRemoved()).thenReturn(false);

        PlayerEntity owner = mock(PlayerEntity.class);
        UUID ownerId = UUID.randomUUID();
        when(owner.getUuid()).thenReturn(ownerId);
        when(owner.isAlive()).thenReturn(true);
        when(owner.getEntityWorld()).thenReturn(world);
        when(owner.squaredDistanceTo(pet)).thenReturn(1600.0);

        StateManager manager = mock(StateManager.class);
        PetSwarmIndex swarmIndex = mock(PetSwarmIndex.class);
        when(manager.getSwarmIndex()).thenReturn(swarmIndex);
        when(manager.getArcaneAmbientCache()).thenReturn(new StateManager.ArcaneAmbientCache());

        AtomicReference<List<PetSwarmIndex.SwarmEntry>> packEntries = new AtomicReference<>(List.of());
        when(swarmIndex.snapshotOwner(ownerId)).thenAnswer(invocation -> packEntries.get());

        PetComponent parent = createParentWithState(state, pet, owner);
        PetMoodEngine engine = new PetMoodEngine(parent);

        try (MockedStatic<StateManager> stateManagerStatic = Mockito.mockStatic(StateManager.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(manager);

            assertFalse(invokeHasOngoingCondition(engine, PetComponent.Emotion.SAUDADE, now),
                "Recent social buffers should suppress loneliness even when the owner is distant");

            state.put(PetComponent.StateKeys.LAST_SOCIAL_BUFFER_TICK, now - 800L);

            MobEntity ally = mock(MobEntity.class);
            when(ally.isAlive()).thenReturn(true);
            when(ally.isRemoved()).thenReturn(false);
            PetComponent allyComponent = mock(PetComponent.class);
            when(allyComponent.isOwnedBy(owner)).thenReturn(true);
            PetSwarmIndex.SwarmEntry entry = mock(PetSwarmIndex.SwarmEntry.class);
            when(entry.pet()).thenReturn(ally);
            when(entry.component()).thenReturn(allyComponent);
            when(entry.x()).thenReturn(2.0);
            when(entry.y()).thenReturn(0.0);
            when(entry.z()).thenReturn(2.0);
            packEntries.set(List.of(entry));

            long packTick = now + 40L;
            assertFalse(invokeHasOngoingCondition(engine, PetComponent.Emotion.SAUDADE, packTick),
                "Fresh pack companionship should prevent loneliness while the owner is away");

            Object storedTick = state.get(PetComponent.StateKeys.PACK_LAST_NEARBY_TICK);
            assertEquals(packTick, storedTick,
                "Pack refresh should stamp the last nearby tick when allies are found");

            packEntries.set(List.of());
            state.put(PetComponent.StateKeys.PACK_LAST_NEARBY_TICK, now - 900L);
            state.put(PetComponent.StateKeys.OWNER_LAST_NEARBY_TICK, now - 900L);
            long later = packTick + 600L;
            assertTrue(invokeHasOngoingCondition(engine, PetComponent.Emotion.SAUDADE, later),
                "Loneliness should return once social and pack comfort expire");
        }
    }

    @Test
    void arcaneMomentumUsesCachedAmbientEnergy() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put(PetComponent.StateKeys.ARCANE_LAST_SCAN_TICK, 90L);
        state.put(PetComponent.StateKeys.ARCANE_CACHED_AMBIENT_ENERGY, 0.4f);
        state.put(PetComponent.StateKeys.ARCANE_LAST_SCAN_POS, BlockPos.ORIGIN);
        state.put(PetComponent.StateKeys.ARCANE_LAST_ENCHANT_TICK, 0L);
        state.put(PetComponent.StateKeys.ARCANE_ENCHANT_STREAK, 0);
        state.put(PetComponent.StateKeys.ARCANE_LAST_SURGE_TICK, 0L);
        state.put(PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, 0f);

        MobEntity pet = mock(MobEntity.class);
        ServerWorld world = mock(ServerWorld.class);
        when(pet.getEntityWorld()).thenReturn(world);
        when(pet.getBlockPos()).thenReturn(BlockPos.ORIGIN);
        when(pet.getMainHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getOffHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getEquippedStack(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);

        PetComponent parent = createParentWithState(state, pet, null);

        StateManager manager = mock(StateManager.class);
        when(manager.getArcaneAmbientCache()).thenReturn(new StateManager.ArcaneAmbientCache());

        try (MockedStatic<StateManager> stateManagerStatic = Mockito.mockStatic(StateManager.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(manager);

            PetMoodEngine engine = new PetMoodEngine(parent);
            assertTrue(invokeHasOngoingCondition(engine, PetComponent.Emotion.ARCANE_OVERFLOW, 100L),
                "Cached ambient energy should satisfy arcane overflow without forcing a rescan");
        }
    }

    @Test
    void arcaneAmbientCacheIsSharedAcrossPets() throws Exception {
        Map<String, Object> firstState = new HashMap<>();
        Map<String, Object> secondState = new HashMap<>();

        MobEntity firstPet = mock(MobEntity.class);
        MobEntity secondPet = mock(MobEntity.class);
        ServerWorld world = mock(ServerWorld.class);

        when(firstPet.getEntityWorld()).thenReturn(world);
        when(secondPet.getEntityWorld()).thenReturn(world);
        when(firstPet.getBlockPos()).thenReturn(BlockPos.ORIGIN);
        when(secondPet.getBlockPos()).thenReturn(BlockPos.ORIGIN);
        when(firstPet.getMainHandStack()).thenReturn(ItemStack.EMPTY);
        when(firstPet.getOffHandStack()).thenReturn(ItemStack.EMPTY);
        when(firstPet.getEquippedStack(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);
        when(secondPet.getMainHandStack()).thenReturn(ItemStack.EMPTY);
        when(secondPet.getOffHandStack()).thenReturn(ItemStack.EMPTY);
        when(secondPet.getEquippedStack(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);
        when(firstPet.getStatusEffects()).thenReturn(Collections.emptyList());
        when(secondPet.getStatusEffects()).thenReturn(Collections.emptyList());
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        when(world.getBlockState(any(BlockPos.class))).thenReturn(Blocks.ENCHANTING_TABLE.getDefaultState());

        StateManager manager = mock(StateManager.class);
        StateManager.ArcaneAmbientCache sharedCache = new StateManager.ArcaneAmbientCache();
        when(manager.getArcaneAmbientCache()).thenReturn(sharedCache);

        try (MockedStatic<StateManager> stateManagerStatic = Mockito.mockStatic(StateManager.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(manager);

            PetComponent firstParent = createParentWithState(firstState, firstPet, null);
            PetMoodEngine firstEngine = new PetMoodEngine(firstParent);
            assertTrue(invokeHasOngoingCondition(firstEngine, PetComponent.Emotion.ARCANE_OVERFLOW, 120L),
                "Initial pet should generate an ambient scan when none exist");

            PetComponent secondParent = createParentWithState(secondState, secondPet, null);
            PetMoodEngine secondEngine = new PetMoodEngine(secondParent);
            assertTrue(invokeHasOngoingCondition(secondEngine, PetComponent.Emotion.ARCANE_OVERFLOW, 160L),
                "Second pet should reuse the shared ambient scan while it remains valid");

            assertEquals(firstState.get(PetComponent.StateKeys.ARCANE_LAST_SCAN_TICK),
                secondState.get(PetComponent.StateKeys.ARCANE_LAST_SCAN_TICK),
                "Shared ambient cache should reuse the original scan tick across pets");
        }
    }

    @Test
    void arcaneAmbientCacheInvalidatesOnArcaneBlockChange() {
        StateManager.ArcaneAmbientCache cache = new StateManager.ArcaneAmbientCache();
        BlockPos pos = BlockPos.ORIGIN;
        ChunkSectionPos section = ChunkSectionPos.from(pos);

        cache.store(section, pos, 4, 200L, 160L, 0.55f);
        assertNotNull(cache.tryGet(section, pos, 4, 210L, 160L, 9.0d),
            "Precondition: sample should exist before targeted invalidation");

        ServerWorld world = mock(ServerWorld.class);
        StateManager manager = mock(StateManager.class);
        doAnswer(invocation -> {
            BlockPos target = invocation.getArgument(0);
            cache.invalidateRadius(target, 4);
            return null;
        }).when(manager).invalidateArcaneAmbient(any(BlockPos.class));

        try (MockedStatic<StateManager> stateManagerStatic = Mockito.mockStatic(StateManager.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(manager);
            stateManagerStatic.when(() -> StateManager.invalidateArcaneAmbientAt(world, pos)).thenCallRealMethod();

            StateManager.invalidateArcaneAmbientAt(world, pos);
        }

        assertNull(cache.tryGet(section, pos, 4, 220L, 160L, 9.0d),
            "Targeted arcane invalidation should drop cached ambient samples");
    }

    @Test
    void arcaneAmbientInvalidationCoversScanRadius() {
        StateManager.ArcaneAmbientCache cache = new StateManager.ArcaneAmbientCache();
        BlockPos origin = BlockPos.ORIGIN;
        ChunkSectionPos originSection = ChunkSectionPos.from(origin);
        cache.store(originSection, origin, 4, 0L, 160L, 0.4f);

        ServerWorld world = mock(ServerWorld.class);
        StateManager manager = mock(StateManager.class);
        doAnswer(invocation -> {
            BlockPos target = invocation.getArgument(0);
            cache.invalidateRadius(target, 4);
            return null;
        }).when(manager).invalidateArcaneAmbient(any(BlockPos.class));

        try (MockedStatic<StateManager> stateManagerStatic = Mockito.mockStatic(StateManager.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(manager);
            stateManagerStatic.when(() -> StateManager.invalidateArcaneAmbientAt(world, new BlockPos(16, 0, 0)))
                .thenCallRealMethod();

            StateManager.invalidateArcaneAmbientAt(world, new BlockPos(16, 0, 0));
        }

        assertNull(cache.tryGet(originSection, origin, 4, 40L, 160L, 9.0d),
            "Invalidating near a neighbouring section should clear cached ambient samples");
    }

    @Test
    void arcaneAmbientCacheInvalidatesOnBlockEntityPotencyChange() {
        StateManager.ArcaneAmbientCache cache = new StateManager.ArcaneAmbientCache();
        BlockPos pos = new BlockPos(0, 64, 0);
        ChunkSectionPos section = ChunkSectionPos.from(pos);
        cache.store(section, pos, 4, 0L, 160L, 0.6f);

        ServerWorld world = mock(ServerWorld.class);
        BlockState beaconState = Blocks.BEACON.getDefaultState();
        when(world.getBlockState(pos)).thenReturn(beaconState);

        StateManager manager = mock(StateManager.class);
        doAnswer(invocation -> {
            BlockPos target = invocation.getArgument(0);
            cache.invalidateRadius(target, 4);
            return null;
        }).when(manager).invalidateArcaneAmbient(any(BlockPos.class));

        TestBeaconBlockEntity beacon = new TestBeaconBlockEntity(pos, 1);
        beacon.attachWorld(world);

        try (MockedStatic<StateManager> stateManagerStatic = Mockito.mockStatic(StateManager.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(manager);
            stateManagerStatic.when(() -> StateManager.invalidateArcaneAmbientAt(world, pos)).thenCallRealMethod();

            EmotionsEventHandler.invalidateArcaneAmbientForBlockEntity(beacon);
        }

        assertNull(cache.tryGet(section, pos, 4, 20L, 160L, 9.0d),
            "Arcane block entity potency changes should invalidate cached ambient energy");
    }

    @Test
    void arcaneMomentumScalesWithEnchantedGear() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put(PetComponent.StateKeys.ARCANE_LAST_ENCHANT_TICK, 0L);
        state.put(PetComponent.StateKeys.ARCANE_ENCHANT_STREAK, 0);
        state.put(PetComponent.StateKeys.ARCANE_LAST_SURGE_TICK, 0L);
        state.put(PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, 0f);

        ServerWorld world = mock(ServerWorld.class);
        AtomicReference<ItemStack> petMainHand = new AtomicReference<>(createEnchantedStack(1, 1));
        AtomicReference<ItemStack> ownerMainHand = new AtomicReference<>(ItemStack.EMPTY);

        MobEntity pet = mock(MobEntity.class);
        when(pet.getEntityWorld()).thenReturn(world);
        when(pet.getBlockPos()).thenReturn(BlockPos.ORIGIN);
        when(pet.getMainHandStack()).thenAnswer(invocation -> petMainHand.get());
        when(pet.getOffHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getEquippedStack(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);
        when(pet.getStatusEffects()).thenReturn(Collections.emptyList());

        PlayerEntity owner = mock(PlayerEntity.class);
        when(owner.isAlive()).thenReturn(true);
        when(owner.getEntityWorld()).thenReturn(world);
        when(owner.getUuid()).thenReturn(UUID.randomUUID());
        when(owner.squaredDistanceTo(pet)).thenReturn(400.0);
        when(owner.getMainHandStack()).thenAnswer(invocation -> ownerMainHand.get());
        when(owner.getOffHandStack()).thenReturn(ItemStack.EMPTY);
        when(owner.getEquippedStack(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);
        when(owner.getStatusEffects()).thenReturn(Collections.emptyList());

        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        when(world.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.getDefaultState());

        StateManager manager = mock(StateManager.class);
        StateManager.ArcaneAmbientCache cache = new StateManager.ArcaneAmbientCache();
        when(manager.getArcaneAmbientCache()).thenReturn(cache);
        when(manager.getSwarmIndex()).thenReturn(mock(PetSwarmIndex.class));

        PetComponent parent = createParentWithState(state, pet, owner);

        try (MockedStatic<StateManager> stateManagerStatic = Mockito.mockStatic(StateManager.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(manager);

            PetMoodEngine engine = new PetMoodEngine(parent);

            long now = 200L;
            invokeHasOngoingCondition(engine, PetComponent.Emotion.ARCANE_OVERFLOW, now);
            float weakEnergy = ((Number) state.getOrDefault(
                PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, 0f)).floatValue();

            petMainHand.set(createEnchantedStack(2, 4));
            ownerMainHand.set(createEnchantedStack(1, 3));
            cache.invalidateAll();
            state.put(PetComponent.StateKeys.ARCANE_LAST_SURGE_TICK, 0L);
            state.put(PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, 0f);

            long later = now + 200L;
            invokeHasOngoingCondition(engine, PetComponent.Emotion.ARCANE_OVERFLOW, later);
            float strongEnergy = ((Number) state.getOrDefault(
                PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, 0f)).floatValue();

            assertTrue(strongEnergy > weakEnergy,
                "Higher-level enchantments should contribute more arcane energy than weak gear");
        }
    }

    @Test
    void arcaneAmbientCacheInvalidatesOnConfigRefresh() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put(PetComponent.StateKeys.ARCANE_LAST_ENCHANT_TICK, 0L);
        state.put(PetComponent.StateKeys.ARCANE_ENCHANT_STREAK, 0);
        state.put(PetComponent.StateKeys.ARCANE_LAST_SURGE_TICK, 0L);
        state.put(PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, 0f);

        ServerWorld world = mock(ServerWorld.class);
        AtomicBoolean arcaneBlocks = new AtomicBoolean(true);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        when(world.getBlockState(any(BlockPos.class))).thenAnswer(invocation ->
            arcaneBlocks.get() ? Blocks.ENCHANTING_TABLE.getDefaultState() : Blocks.AIR.getDefaultState());

        MobEntity pet = mock(MobEntity.class);
        when(pet.getEntityWorld()).thenReturn(world);
        when(pet.getBlockPos()).thenReturn(BlockPos.ORIGIN);
        when(pet.getMainHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getOffHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getEquippedStack(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);
        when(pet.getStatusEffects()).thenReturn(Collections.emptyList());

        StateManager manager = mock(StateManager.class);
        StateManager.ArcaneAmbientCache cache = new StateManager.ArcaneAmbientCache();
        when(manager.getArcaneAmbientCache()).thenReturn(cache);
        when(manager.getSwarmIndex()).thenReturn(mock(PetSwarmIndex.class));

        PetComponent parent = createParentWithState(state, pet, null);

        AtomicInteger invalidations = new AtomicInteger();
        MoodEngineConfig configV1 = buildConfig(42, 10);
        MoodEngineConfig configV2 = buildConfig(42, 11);
        AtomicReference<MoodEngineConfig> currentConfig = new AtomicReference<>(configV1);

        try (MockedStatic<StateManager> stateManagerStatic = Mockito.mockStatic(StateManager.class);
             MockedStatic<MoodEngineConfig> configStatic = Mockito.mockStatic(MoodEngineConfig.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(manager);
            stateManagerStatic.when(StateManager::invalidateArcaneAmbientCaches).thenAnswer(invocation -> {
                invalidations.incrementAndGet();
                cache.invalidateAll();
                return null;
            });

            configStatic.when(MoodEngineConfig::get).thenAnswer(invocation -> currentConfig.get());

            PetMoodEngine engine = new PetMoodEngine(parent);

            long firstTick = 300L;
            assertTrue(invokeHasOngoingCondition(engine, PetComponent.Emotion.ARCANE_OVERFLOW, firstTick),
                "Arcane structures should provide ambient energy with the initial config");
            float initialEnergy = ((Number) state.getOrDefault(
                PetComponent.StateKeys.ARCANE_CACHED_AMBIENT_ENERGY, 0f)).floatValue();
            assertTrue(initialEnergy > 0f,
                "Initial ambient scan should cache non-zero energy from enchanting blocks");

            arcaneBlocks.set(false);
            currentConfig.set(configV2);
            cache.invalidateAll();
            state.put(PetComponent.StateKeys.ARCANE_LAST_SURGE_TICK, 0L);
            state.put(PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, 0f);
            state.put(PetComponent.StateKeys.ARCANE_LAST_SCAN_TICK, 0L);
            state.put(PetComponent.StateKeys.ARCANE_CACHED_AMBIENT_ENERGY, 0f);
            state.remove(PetComponent.StateKeys.ARCANE_LAST_SCAN_POS);

            long secondTick = firstTick + 200L;
            assertFalse(invokeHasOngoingCondition(engine, PetComponent.Emotion.ARCANE_OVERFLOW, secondTick),
                "After config refresh without structures, arcane overflow should not persist");
            float refreshedEnergy = ((Number) state.getOrDefault(
                PetComponent.StateKeys.ARCANE_CACHED_AMBIENT_ENERGY, 0f)).floatValue();
            assertEquals(0f, refreshedEnergy, 1.0e-3f,
                "Ambient cache should be recomputed using the updated config weights");
            assertEquals(2, invalidations.get(),
                "Arcane ambient caches should invalidate on initial load and after config regeneration");
        }
    }

    @Test
    void arcaneAmbientScanIncludesVerticalRadius() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put(PetComponent.StateKeys.ARCANE_LAST_ENCHANT_TICK, 0L);
        state.put(PetComponent.StateKeys.ARCANE_ENCHANT_STREAK, 0);
        state.put(PetComponent.StateKeys.ARCANE_LAST_SURGE_TICK, 0L);
        state.put(PetComponent.StateKeys.ARCANE_SURGE_STRENGTH, 0f);

        ServerWorld world = mock(ServerWorld.class);
        when(world.getRegistryKey()).thenReturn(World.END);
        BlockPos origin = BlockPos.ORIGIN;
        BlockPos above = origin.up(3);
        BlockPos below = origin.down(3);
        when(world.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return (pos.equals(above) || pos.equals(below))
                ? Blocks.ENCHANTING_TABLE.getDefaultState()
                : Blocks.AIR.getDefaultState();
        });

        MobEntity pet = mock(MobEntity.class);
        when(pet.getEntityWorld()).thenReturn(world);
        when(pet.getBlockPos()).thenReturn(origin);
        when(pet.getMainHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getOffHandStack()).thenReturn(ItemStack.EMPTY);
        when(pet.getEquippedStack(any(EquipmentSlot.class))).thenReturn(ItemStack.EMPTY);
        when(pet.getStatusEffects()).thenReturn(Collections.emptyList());

        StateManager manager = mock(StateManager.class);
        StateManager.ArcaneAmbientCache cache = new StateManager.ArcaneAmbientCache();
        when(manager.getArcaneAmbientCache()).thenReturn(cache);
        when(manager.getSwarmIndex()).thenReturn(mock(PetSwarmIndex.class));

        PetComponent parent = createParentWithState(state, pet, null);

        MoodEngineConfig config = buildConfig(42, 20);

        try (MockedStatic<StateManager> stateManagerStatic = Mockito.mockStatic(StateManager.class);
             MockedStatic<MoodEngineConfig> configStatic = Mockito.mockStatic(MoodEngineConfig.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(manager);
            stateManagerStatic.when(StateManager::invalidateArcaneAmbientCaches).thenAnswer(invocation -> {
                cache.invalidateAll();
                return null;
            });
            configStatic.when(MoodEngineConfig::get).thenReturn(config);

            PetMoodEngine engine = new PetMoodEngine(parent);
            long now = 120L;
            invokeHasOngoingCondition(engine, PetComponent.Emotion.ARCANE_OVERFLOW, now);

            float cachedEnergy = ((Number) state.getOrDefault(
                PetComponent.StateKeys.ARCANE_CACHED_AMBIENT_ENERGY, 0f)).floatValue();
            assertTrue(cachedEnergy > 0f,
                "Arcane ambient scan should consider structures placed above or below the pet");
        }
    }

    @Test
    void arcaneAmbientCachePrunesExpiredEntriesOnStore() throws Exception {
        StateManager.ArcaneAmbientCache cache = new StateManager.ArcaneAmbientCache();
        long ttl = 40L;

        ChunkSectionPos firstSection = ChunkSectionPos.from(BlockPos.ORIGIN);
        cache.store(firstSection, BlockPos.ORIGIN, 3, 100L, ttl, 0.4f);
        assertEquals(1, getAmbientCacheSize(cache),
            "Storing the first sample should populate the cache");

        ChunkSectionPos secondSection = ChunkSectionPos.from(new BlockPos(16, 16, 0));
        long later = 100L + ttl * 3;
        cache.store(secondSection, new BlockPos(16, 16, 0), 3, later, ttl, 0.6f);
        assertEquals(1, getAmbientCacheSize(cache),
            "Cleanup should purge expired samples when inserting a new scan");

        long rolling = later;
        for (int i = 0; i < 520; i++) {
            BlockPos pos = new BlockPos(i * 16, i * 16, 0);
            cache.store(ChunkSectionPos.from(pos), pos, 3, ++rolling, ttl, 0.2f);
        }

        assertTrue(getAmbientCacheSize(cache) <= 512,
            "Arcane ambient cache should cap its capacity when storing many samples");
    }

    private static Object getEmotionRecord(PetMoodEngine engine, PetComponent.Emotion emotion) throws Exception {
        var field = PetMoodEngine.class.getDeclaredField("emotionRecords");
        field.setAccessible(true);
        Map<PetComponent.Emotion, ?> records = (Map<PetComponent.Emotion, ?>) field.get(engine);
        Object record = records.get(emotion);
        assertNotNull(record, "Emotion record should exist after contagion share");
        return record;
    }

    private ItemStack createEnchantedStack(int enchantmentCount, int level) {
        ItemStack stack = new ItemStack(Items.STICK);
        ItemEnchantmentsComponent.Builder builder =
            new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        for (int i = 0; i < Math.max(1, enchantmentCount); i++) {
            RegistryEntry<Enchantment> entry = mock(RegistryEntry.class);
            builder.set(entry, level);
        }
        stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
        return stack;
    }

    private MoodEngineConfig buildConfig(int version, int generation) throws Exception {
        String json = Files.readString(Path.of(
            "src/main/resources/assets/petsplus/configs/moodengine.json"));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        root.addProperty("_version", version);
        JsonObject moods = root.getAsJsonObject("moods");
        Constructor<MoodEngineConfig> ctor = MoodEngineConfig.class.getDeclaredConstructor(
            JsonObject.class, JsonObject.class, int.class, String.class, int.class);
        ctor.setAccessible(true);
        String schema = root.has("_schema") && root.get("_schema").isJsonPrimitive()
            ? root.get("_schema").getAsString()
            : "petsplus:emotions";
        return ctor.newInstance(root, moods, version, schema, generation);
    }

    private int getAmbientCacheSize(StateManager.ArcaneAmbientCache cache) throws Exception {
        Field field = StateManager.ArcaneAmbientCache.class.getDeclaredField("samples");
        field.setAccessible(true);
        Map<?, ?> samples = (Map<?, ?>) field.get(cache);
        return samples.size();
    }

    private static final class TestSculkCatalystBlockEntity extends BlockEntity {
        private final long lastBloomTick;

        private TestSculkCatalystBlockEntity(BlockPos pos, long lastBloomTick) {
            super(BlockEntityType.SCULK_CATALYST, pos, Blocks.SCULK_CATALYST.getDefaultState());
            this.lastBloomTick = lastBloomTick;
        }

        @SuppressWarnings("unused")
        public long getLastBloomTick() {
            return lastBloomTick;
        }
    }

    private static final class TestBeaconBlockEntity extends BeaconBlockEntity {
        private TestBeaconBlockEntity(BlockPos pos, int level) {
            super(pos, Blocks.BEACON.getDefaultState());
            try {
                Field levelField = BeaconBlockEntity.class.getDeclaredField("level");
                levelField.setAccessible(true);
                levelField.setInt(this, level);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to seed beacon level for test", e);
            }
        }

        private void attachWorld(World world) {
            try {
                Field worldField = BlockEntity.class.getDeclaredField("world");
                worldField.setAccessible(true);
                worldField.set(this, world);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to attach world to test beacon", e);
            }
        }
    }

    private PetComponent createParentWithState(Map<String, Object> state,
                                               MobEntity pet,
                                               PlayerEntity owner) {
        PetComponent parent = mock(PetComponent.class);
        when(parent.getPetEntity()).thenReturn(pet);
        when(parent.getOwner()).thenReturn(owner);
        when(parent.getBondStrength()).thenReturn(0L);

        Answer<Object> directLookup = invocation -> {
            String key = invocation.getArgument(0);
            Class<?> type = invocation.getArgument(1);
            Object value = state.get(key);
            if (value != null && type.isInstance(value)) {
                return value;
            }
            return null;
        };
        Answer<Object> defaultLookup = invocation -> {
            String key = invocation.getArgument(0);
            Class<?> type = invocation.getArgument(1);
            Object defaultValue = invocation.getArgument(2);
            Object value = state.get(key);
            if (value != null && type.isInstance(value)) {
                return value;
            }
            return defaultValue;
        };

        when(parent.getStateData(anyString(), Mockito.<Class<?>>any())).thenAnswer(directLookup);
        when(parent.getStateData(anyString(), Mockito.<Class<?>>any(), any())).thenAnswer(defaultLookup);

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = invocation.getArgument(1);
            state.put(key, value);
            return null;
        }).when(parent).setStateData(anyString(), any());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            state.remove(key);
            return null;
        }).when(parent).clearStateData(anyString());

        return parent;
    }

    private boolean invokeHasOngoingCondition(PetMoodEngine engine,
                                              PetComponent.Emotion emotion,
                                              long now) throws Exception {
        var method = PetMoodEngine.class.getDeclaredMethod("hasOngoingCondition",
                PetComponent.Emotion.class, long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(engine, emotion, now);
    }

    private static float getContagionShare(Object record) throws Exception {
        var contagionField = record.getClass().getDeclaredField("contagionShare");
        contagionField.setAccessible(true);
        return contagionField.getFloat(record);
    }

    private static float getIntensity(Object record) throws Exception {
        var intensityField = record.getClass().getDeclaredField("intensity");
        intensityField.setAccessible(true);
        return intensityField.getFloat(record);
    }

    private static float invokeContagionCap(PetMoodEngine engine, float bondFactor) throws Exception {
        var method = PetMoodEngine.class.getDeclaredMethod("computeContagionCap", float.class);
        method.setAccessible(true);
        return (float) method.invoke(engine, bondFactor);
    }
}
