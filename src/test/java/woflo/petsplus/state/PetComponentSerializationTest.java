package woflo.petsplus.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.netty.buffer.Unpooled;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.text.Text;
import woflo.petsplus.advancement.BestFriendTracker;
import woflo.petsplus.component.PetsplusComponents;
import woflo.petsplus.mixin.EntityComponentAccessor;
import woflo.petsplus.history.HistoryEvent;
import woflo.petsplus.naming.AttributeKey;
import woflo.petsplus.stats.PetAttributeManager;
import woflo.petsplus.stats.PetCharacteristics;
import woflo.petsplus.util.CodecUtils;

class PetComponentSerializationTest {

    private static final String GUARDIAN_ROLE_ID = "petsplus:guardian";
    private static final String ABILITY_ID = "petsplus:blink";
    private static final String TEST_ITEM_ID = "petsplus:fallback_probe";
    private static final UUID PET_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID OWNER_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174999");
    private static Item cachedTestItem;
    private static final Map<MobEntity, Map<ComponentType<?>, Object>> COMPONENT_STORES = new IdentityHashMap<>();

    private static Identifier identifier(String raw) {
        Identifier parsed = Identifier.tryParse(raw);
        Assumptions.assumeTrue(parsed != null, "Identifier parsing unavailable for " + raw);
        return parsed;
    }

    private static Identifier guardianRole() {
        return identifier(GUARDIAN_ROLE_ID);
    }

    private static Identifier abilityIdentifier() {
        return identifier(ABILITY_ID);
    }

    private static ItemStack createStack(int count) {
        try {
            Item item = ensureTestItem();
            ItemStack stack = new ItemStack(item, Math.max(1, count));
            stack.setCount(Math.max(0, count));
            return stack;
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "ItemStack unavailable in test environment: " + t.getMessage());
            return ItemStack.EMPTY;
        }
    }

    private static Item ensureTestItem() {
        if (cachedTestItem != null) {
            return cachedTestItem;
        }

        Identifier id = identifier(TEST_ITEM_ID);
        Item existing = Registries.ITEM.get(id);
        if (id.equals(Registries.ITEM.getId(existing))) {
            cachedTestItem = existing;
            return cachedTestItem;
        }

        Item registered;
        try {
            registered = Registry.register(Registries.ITEM, id, new Item(new Item.Settings()));
        } catch (RuntimeException ex) {
            Item fallback = Registries.ITEM.get(id);
            if (!id.equals(Registries.ITEM.getId(fallback))) {
                throw ex;
            }
            registered = fallback;
        }
        cachedTestItem = registered;
        return cachedTestItem;
    }

    private static boolean supportsStandaloneItemStacks() {
        try {
            Text.class.getMethod("translatable", String.class);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @Test
    void petDataCodecRoundTrip() {
        PetsplusComponents.PetData data = PetsplusComponents.PetData.empty()
            .withRole(guardianRole())
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

    @Test
    void resetAbilitiesClearsExperience() {
        PetComponent component = newComponent();
        component.addExperience(250);

        try (MockedStatic<PetAttributeManager> ignored = mockStatic(PetAttributeManager.class)) {
            ignored.when(() -> PetAttributeManager.applyAttributeModifiers(any(), any())).thenAnswer(invocation -> null);
            component.resetAbilities();
        }

        assertEquals(1, component.getLevel(), "Respec should restore base level");
        assertEquals(0, component.getExperience(), "Respec should clear accumulated XP");
    }

    @Test
    void resetAbilitiesClearsUnlockState() {
        PetComponent component = newComponent();
        component.unlockAbility(abilityIdentifier());
        component.unlockMilestone(3);
        component.addPermanentStatBoost("speed", 0.05f);
        component.setTributeMilestone(3, identifier("petsplus:glow_berry"));

        try (MockedStatic<PetAttributeManager> ignored = mockStatic(PetAttributeManager.class)) {
            ignored.when(() -> PetAttributeManager.applyAttributeModifiers(any(), any())).thenAnswer(invocation -> null);
            component.resetAbilities();
        }

        assertFalse(component.isAbilityUnlocked(abilityIdentifier()), "Respec should clear unlocked abilities");
        assertFalse(component.isMilestoneUnlocked(3), "Respec should clear milestones");
        assertEquals(0f, component.getPermanentStatBoost("speed"), "Respec should clear stat boosts");
        assertFalse(component.hasTributeMilestone(3), "Respec should clear tribute milestones");
    }

    @Test
    void xpProgressReflectsRemainderTracking() {
        PetComponent component = newComponent();
        component.setLevel(2);
        int needed = component.getXpNeededForNextLevel();
        component.setExperience(needed / 2);

        assertEquals(0.5f, component.getXpProgress(), 0.0001f,
            "XP progress should use remainder over next level requirement");
    }

    @Test
    void zeroXpAdditionProcessesPendingLevelUps() {
        PetComponent component = newComponent();
        component.setLevel(2);
        int needed = component.getXpNeededForNextLevel();
        component.setExperience(needed);

        assertTrue(component.addExperience(0),
            "Zero XP addition should still trigger pending level-up processing");
        assertEquals(3, component.getLevel(), "Level should advance when staged XP meets requirement");
        assertEquals(0, component.getExperience(), "Remainder XP should clear after level-up");
    }

    @Test
    void fallbackItemStackCodecPreservesInventories() {
        Assumptions.assumeTrue(supportsStandaloneItemStacks(),
            "Fallback codec requires ItemStack support in test environment");

        var originalCodec = CodecUtils.TestHooks.currentItemStackListCodec();
        var fallbackCodec = CodecUtils.TestHooks.fallbackItemStackListCodec();

        PetComponent component = newComponent();

        DefaultedList<ItemStack> inventory = DefaultedList.ofSize(3, ItemStack.EMPTY);
        inventory.set(0, createStack(5));
        inventory.set(1, createStack(3));
        inventory.set(2, createStack(1));
        component.setInventory("fallback", inventory);

        CodecUtils.TestHooks.forceItemStackListCodec(fallbackCodec);
        try {
            PetsplusComponents.PetData original = component.toComponentData();
            var encodedResult = PetsplusComponents.PetData.CODEC.encodeStart(NbtOps.INSTANCE, original);
            NbtElement encodedElement = encodedResult.result().orElseThrow(() ->
                new AssertionError("Failed to encode pet data: " +
                    encodedResult.error().map(Object::toString).orElse("unknown error"))
            );

            var parsedResult = PetsplusComponents.PetData.CODEC.parse(NbtOps.INSTANCE, encodedElement);
            PetsplusComponents.PetData decoded = parsedResult.result().orElseThrow(() ->
                new AssertionError("Failed to decode pet data: " +
                    parsedResult.error().map(Object::toString).orElse("unknown error"))
            );

            PetComponent restored = newComponent();
            restored.fromComponentData(decoded);

            DefaultedList<ItemStack> restoredInventory = restored.getInventoryIfPresent("fallback");
            assertNotNull(restoredInventory, "Fallback codec should restore the saved inventory");
            assertEquals(3, restoredInventory.size(), "Inventory size should persist through fallback codec round-trip");
            assertEquals(5, restoredInventory.get(0).getCount(), "First slot count should persist");
            assertEquals(3, restoredInventory.get(1).getCount(), "Second slot count should persist");
            assertEquals(1, restoredInventory.get(2).getCount(), "Third slot count should persist");
        } finally {
            CodecUtils.TestHooks.forceItemStackListCodec(originalCodec);
        }
    }

    @Test
    void dataComponentRoundTripRestoresState() {
        Assumptions.assumeTrue(supportsStandaloneItemStacks(),
            "Component persistence test requires ItemStack support in test environment");

        PetComponent component = newComponent();

        component.setPerched(true);
        component.setLevel(4);
        component.setExperience(18);
        component.unlockAbility(abilityIdentifier());
        component.addPermanentStatBoost("health", 1.25f);
        component.getSchedulingModule().setCooldown("blink", 900L);
        component.setStateData("petsplus:emotion_streak", 7);
        component.setStateData("context_weather", "sunny");
        BlockPos expectedJukeboxPos = new BlockPos(8, 72, -14);
        component.setStateData("petsplus:last_jukebox_pos", expectedJukeboxPos);
        component.setStateData("petsplus:state_identifier", guardianRole());
        component.setStateData("petsplus:state_list", List.of("alpha", "beta", "gamma"));

        DefaultedList<ItemStack> inventory = DefaultedList.ofSize(2, ItemStack.EMPTY);
        inventory.set(0, createStack(2));
        component.setInventory("component", inventory);

        component.getGossipLedger().recordRumor(
            42L, 0.7f, 0.55f, 1200L, UUID.randomUUID(), Text.literal("Guardians spotted")
        );

        component.pushEmotion(PetComponent.Emotion.CHEERFUL, 0.8f);
        component.pushEmotion(PetComponent.Emotion.HOPEFUL, 0.25f);
        component.updateMood();
        PetComponent.Mood expectedDominantMood = component.getDominantMood();
        Map<PetComponent.Mood, Float> expectedMoodBlend = new EnumMap<>(component.getMoodBlend());

        NbtCompound characteristicsNbt = new NbtCompound();
        characteristicsNbt.putFloat("healthMod", 0.12f);
        characteristicsNbt.putFloat("speedMod", -0.05f);
        characteristicsNbt.putFloat("attackMod", 0.03f);
        characteristicsNbt.putFloat("defenseMod", -0.02f);
        characteristicsNbt.putFloat("agilityMod", 0.04f);
        characteristicsNbt.putFloat("vitalityMod", 0.01f);
        characteristicsNbt.putLong("charSeed", 4242L);
        PetCharacteristics characteristics = PetCharacteristics.readFromNbt(characteristicsNbt);
        assertNotNull(characteristics, "Test characteristics payload should decode");
        component.setCharacteristics(characteristics);
        component.setNatureEmotionTuning(1.3f, 0.85f, 1.1f, 0.95f);
        component.setNatureEmotionProfile(new PetComponent.NatureEmotionProfile(
            PetComponent.Emotion.CHEERFUL, 0.6f,
            PetComponent.Emotion.HOPEFUL, 0.4f,
            PetComponent.Emotion.UBUNTU, 0.3f
        ));
        component.setNameAttributes(List.of(new AttributeKey("brave", "alpha", 2)));
        component.applyRoleAffinityBonuses(guardianRole(), new String[]{"health", "speed"}, new float[]{0.05f, -0.02f});

        try {
            var lastAttackField = PetComponent.class.getDeclaredField("lastAttackTick");
            lastAttackField.setAccessible(true);
            lastAttackField.setLong(component, 777L);

            var xpFlashField = PetComponent.class.getDeclaredField("xpFlashStartTick");
            xpFlashField.setAccessible(true);
            xpFlashField.setLong(component, 1337L);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set last attack tick", e);
        }

        component.saveToEntity();

        Map<ComponentType<?>, Object> componentStore = COMPONENT_STORES.get(component.getPetEntity());
        assertNotNull(componentStore, "Mock component store should be populated during save");
        componentStore.put(PetsplusComponents.PET_DATA, component.toComponentData());
        assertTrue(componentStore.containsKey(PetsplusComponents.PET_DATA),
            "Component store should contain serialized pet data");

        PetComponent restored = new PetComponent(component.getPetEntity());
        restored.setStateData("petsplus:stale", 99);
        restored.loadFromEntity();

        assertTrue(restored.isPerched(), "Perch state should persist through data component round-trip");
        assertEquals(777L, restored.getLastAttackTick(), "Last attack tick should persist");
        assertEquals(1337L, restored.getXpFlashStartTick(), "XP flash start tick should persist");
        assertEquals(4, restored.getLevel(), "Level should persist through data component round-trip");
        assertEquals(18, restored.getExperience(), "Experience remainder should persist");
        assertTrue(restored.isAbilityUnlocked(abilityIdentifier()), "Unlocked abilities should persist");
        assertEquals(1.25f, restored.getPermanentStatBoost("health"), 0.0001f,
            "Permanent stat boosts should persist");
        assertEquals(900L, restored.copyCooldownSnapshot().get("blink"),
            "Cooldown entries should persist");

        assertFalse(restored.getGossipLedger().isEmpty(),
            "Gossip ledger should persist through data component round-trip");
        assertTrue(restored.getGossipLedger().stream().anyMatch(entry -> entry.topicId() == 42L),
            "Persisted gossip entries should include the recorded topic");

        DefaultedList<ItemStack> restoredInventory = restored.getInventoryIfPresent("component");
        assertNotNull(restoredInventory, "Component inventory should persist");
        assertEquals(2, restoredInventory.get(0).getCount(), "Inventory contents should persist");
        assertEquals(7, restored.getStateData("petsplus:emotion_streak", Integer.class, 0),
            "Component save/load should preserve integer state data entries");
        assertEquals("sunny", restored.getStateData("context_weather", String.class, ""),
            "Component save/load should preserve string state data entries");
        assertEquals(expectedJukeboxPos, restored.getStateData("petsplus:last_jukebox_pos", BlockPos.class),
            "Component save/load should preserve BlockPos state data entries");
        assertEquals(guardianRole(), restored.getStateData("petsplus:state_identifier", Identifier.class),
            "Component save/load should preserve Identifier state data entries");
        assertEquals(List.of("alpha", "beta", "gamma"),
            restored.getStateData("petsplus:state_list", List.class, List.of()),
            "Component save/load should preserve list state data entries");
        assertNull(restored.getStateData("petsplus:stale", Integer.class),
            "Loading component data should replace stale state entries");

        PetCharacteristics restoredCharacteristics = restored.getCharacteristics();
        assertNotNull(restoredCharacteristics, "Characteristics should persist through data component round-trip");
        assertEquals(characteristics.getCharacteristicSeed(), restoredCharacteristics.getCharacteristicSeed(),
            "Characteristic seeds should persist");
        assertEquals(component.getNatureVolatilityMultiplier(), restored.getNatureVolatilityMultiplier(), 0.0001f,
            "Nature volatility should persist through data component round-trip");
        assertEquals(component.getNatureResilienceMultiplier(), restored.getNatureResilienceMultiplier(), 0.0001f,
            "Nature resilience should persist through data component round-trip");
        PetComponent.NatureEmotionProfile restoredProfile = restored.getNatureEmotionProfile();
        assertEquals(PetComponent.Emotion.CHEERFUL, restoredProfile.majorEmotion(),
            "Major emotion should persist through data component round-trip");
        assertEquals(0.6f, restoredProfile.majorStrength(), 0.0001f,
            "Major emotion strength should persist through data component round-trip");
        assertTrue(restored.getNameAttributes().stream().anyMatch(attr -> "brave".equals(attr.type())),
            "Name attributes should persist through data component round-trip");
        float[] affinity = restored.getCharacteristicsModule().getRoleAffinityBonuses().get(guardianRole());
        assertNotNull(affinity, "Role affinity bonuses should persist through data component round-trip");
        assertTrue(affinity.length > 1, "Role affinity vector should retain multiple stat entries");
        assertEquals(0.05f, affinity[0], 0.0001f, "Health affinity bonus should persist");

        restored.updateMood();
        Map<PetComponent.Mood, Float> restoredMoodBlend = new EnumMap<>(restored.getMoodBlend());
        assertEquals(expectedDominantMood, restored.getDominantMood(),
            "Dominant mood should persist through data component round-trip");
        assertEquals(expectedMoodBlend, restoredMoodBlend,
            "Mood blend weights should persist through data component round-trip");
    }

    @Test
    void loadingEmptyComponentDataClearsModules() {
        Assumptions.assumeTrue(supportsStandaloneItemStacks(),
            "Inventory reset test requires ItemStack support in test environment");

        PetComponent component = newComponent();
        component.setLevel(4);
        component.setExperience(42);
        component.unlockAbility(abilityIdentifier());
        component.addHistoryEvent(HistoryEvent.levelUp(100L, OWNER_UUID, "Owner", 3, "test"));
        component.getInventory("loot", 1).set(0, createStack(2));
        component.setOwnerUuid(OWNER_UUID);
        component.beginCrouchCuddle(OWNER_UUID, 400L);
        component.setStateData("custom_flag", true);
        component.setCooldown("raid", 5);
        component.getCharacteristicsModule().updateNatureTuning(1.2f, 0.8f, 1.1f, 1.05f);
        component.getCharacteristicsModule().setNatureEmotionProfile(
            new PetComponent.NatureEmotionProfile(
                PetComponent.Emotion.CHEERFUL,
                0.7f,
                PetComponent.Emotion.FOCUSED,
                0.3f,
                null,
                0f
            )
        );
        component.getCharacteristicsModule().setNameAttributes(List.of(new AttributeKey("title", "Alpha", 1)));
        component.getCharacteristicsModule().applyRoleAffinityBonuses(
            guardianRole(),
            new String[]{"health"},
            new float[]{0.1f}
        );

        PetsplusComponents.PetData empty = PetsplusComponents.PetData.empty();

        try (MockedStatic<BestFriendTracker> tracker = mockStatic(BestFriendTracker.class)) {
            tracker.when(() -> BestFriendTracker.get(any(ServerWorld.class))).thenReturn(mock(BestFriendTracker.class));
            component.fromComponentData(empty);
        }

        assertEquals(1, component.getLevel(), "Loading empty data should reset level");
        assertEquals(0, component.getExperience(), "Loading empty data should clear XP");
        assertFalse(component.isAbilityUnlocked(abilityIdentifier()), "Abilities should clear when data is absent");
        assertTrue(component.getHistory().isEmpty(), "History should reset when data is absent");
        assertNull(component.getInventoryIfPresent("loot"), "Inventories should clear when data is absent");
        assertNull(component.getOwnerUuid(), "Owner UUID should clear when data is absent");
        assertNull(component.getCrouchCuddleOwnerId(), "Crouch cuddle state should reset");
        assertFalse(component.isCrouchCuddleActive(200L), "Crouch cuddle activity should clear");
        assertTrue(component.copyCooldownSnapshot().isEmpty(), "Cooldowns should clear when data is absent");
        assertNull(component.getStateData("custom_flag", Boolean.class), "State data should reset when absent");
        assertEquals(1.0f, component.getCharacteristicsModule().getNatureVolatility(), 0.0001f,
            "Nature tuning should reset to defaults");
        assertEquals(1.0f, component.getCharacteristicsModule().getNatureResilience(), 0.0001f,
            "Nature tuning should reset to defaults");
        assertTrue(component.getCharacteristicsModule().getNatureEmotionProfile().isEmpty(),
            "Emotion profile should reset to empty");
        assertTrue(component.getCharacteristicsModule().getNameAttributes().isEmpty(),
            "Name attributes should reset to empty");
        assertTrue(component.getCharacteristicsModule().getRoleAffinityBonuses().isEmpty(),
            "Role affinity bonuses should clear");
    }

    @Test
    void petDataPacketCodecRoundTripPreservesPayloads() {
        Assumptions.assumeTrue(supportsStandaloneItemStacks(),
            "Packet codec test requires ItemStack support in test environment");

        PetComponent component = newComponent();
        component.setRoleId(guardianRole());
        component.setPerched(true);
        component.setLevel(5);
        component.setExperience(12);
        component.unlockAbility(abilityIdentifier());
        component.addPermanentStatBoost("health", 0.5f);
        component.getHistoryModule().recordEvent(
            HistoryEvent.levelUp(120L, OWNER_UUID, "Tester", 5, "packet_test")
        );
        component.setOwnerUuid(OWNER_UUID);
        component.getOwnerModule().recordCrouchCuddle(OWNER_UUID, 400L);
        component.getSchedulingModule().setCooldown("blink", 320L);
        component.getSchedulingModule().setCooldown("nap", 640L);
        component.setStateData("petsplus:test_flag", true);
        component.setStateData("petsplus:test_note", "synced");
        component.setStateData("petsplus:packet_block_pos", new BlockPos(-4, 66, 19));
        component.setStateData("petsplus:packet_identifier", guardianRole());
        component.setStateData("petsplus:packet_list", List.of("packet", "state"));

        DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
        inventory.set(0, createStack(3));
        component.setInventory("packet", inventory);

        component.getGossipLedger().recordRumor(
            99L, 0.45f, 0.5f, 800L, OWNER_UUID, Text.literal("Packet rumor")
        );

        NbtCompound characteristicsNbt = new NbtCompound();
        characteristicsNbt.putFloat("healthMod", 0.07f);
        characteristicsNbt.putFloat("speedMod", -0.01f);
        characteristicsNbt.putFloat("attackMod", 0.02f);
        characteristicsNbt.putFloat("defenseMod", 0.03f);
        characteristicsNbt.putFloat("agilityMod", -0.04f);
        characteristicsNbt.putFloat("vitalityMod", 0.05f);
        characteristicsNbt.putLong("charSeed", 9876L);
        PetCharacteristics characteristics = PetCharacteristics.readFromNbt(characteristicsNbt);
        assertNotNull(characteristics, "Characteristics payload should deserialize for packet test");
        component.setCharacteristics(characteristics);
        component.setNatureEmotionTuning(1.1f, 0.9f, 1.05f, 0.95f);
        component.setNatureEmotionProfile(new PetComponent.NatureEmotionProfile(
            PetComponent.Emotion.CHEERFUL, 0.5f,
            PetComponent.Emotion.HOPEFUL, 0.3f,
            PetComponent.Emotion.UBUNTU, 0.2f
        ));
        component.setNameAttributes(List.of(new AttributeKey("steadfast", "omega", 1)));
        component.applyRoleAffinityBonuses(guardianRole(), new String[]{"health"}, new float[]{0.03f});

        component.pushEmotion(PetComponent.Emotion.CHEERFUL, 0.6f);
        component.pushEmotion(PetComponent.Emotion.HOPEFUL, 0.2f);
        component.updateMood();

        try {
            var lastAttackField = PetComponent.class.getDeclaredField("lastAttackTick");
            lastAttackField.setAccessible(true);
            lastAttackField.setLong(component, 512L);

            var xpFlashField = PetComponent.class.getDeclaredField("xpFlashStartTick");
            xpFlashField.setAccessible(true);
            xpFlashField.setLong(component, 2020L);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set last attack tick for packet codec test", e);
        }

        PetsplusComponents.PetData original = component.toComponentData();

        PacketByteBuf base = new PacketByteBuf(Unpooled.buffer());
        RegistryByteBuf buffer = new RegistryByteBuf(base, DynamicRegistryManager.EMPTY);

        PetsplusComponents.PetData.PACKET_CODEC.encode(buffer, original);
        buffer.readerIndex(0);
        PetsplusComponents.PetData decoded = PetsplusComponents.PetData.PACKET_CODEC.decode(buffer);

        assertEquals(original.role(), decoded.role(), "Role identifier should survive packet round-trip");
        assertEquals(original.cooldowns(), decoded.cooldowns(), "Cooldown entries should survive packet round-trip");
        assertEquals(original.lastAttackTick(), decoded.lastAttackTick(), "Last attack tick should survive packet round-trip");
        assertEquals(original.isPerched(), decoded.isPerched(), "Perch state should survive packet round-trip");
        assertEquals(original.xpFlashStartTick(), decoded.xpFlashStartTick(), "XP flash tick should survive packet round-trip");
        assertEquals(original.progression(), decoded.progression(), "Progression data should survive packet round-trip");
        assertEquals(original.history(), decoded.history(), "History data should survive packet round-trip");
        assertEquals(original.inventories(), decoded.inventories(), "Inventory data should survive packet round-trip");
        assertEquals(original.owner(), decoded.owner(), "Owner data should survive packet round-trip");
        assertEquals(original.scheduling(), decoded.scheduling(), "Scheduling data should survive packet round-trip");
        assertEquals(original.characteristics(), decoded.characteristics(), "Characteristics data should survive packet round-trip");
        assertEquals(original.stateData(), decoded.stateData(), "State data should survive packet round-trip");
        assertEquals(original.schemaVersion(), decoded.schemaVersion(), "Schema version should survive packet round-trip");
        assertEquals(original.gossip().isPresent(), decoded.gossip().isPresent(),
            "Gossip ledger presence should survive packet round-trip");
        assertTrue(decoded.gossip().map(ledger -> ledger.stream().anyMatch(entry -> entry.topicId() == 99L)).orElse(false),
            "Gossip entries should survive packet round-trip");
        assertEquals(original.mood().isPresent(), decoded.mood().isPresent(),
            "Mood payload presence should survive packet round-trip");
        assertEquals(original.mood().map(NbtCompound::toString).orElse(null),
            decoded.mood().map(NbtCompound::toString).orElse(null),
            "Mood payload contents should survive packet round-trip");
    }

    @Test
    void writeCustomDataSavesLatestStateImmediately() {
        PetComponent component = Mockito.spy(newComponent());
        MobEntity pet = component.getPetEntity();
        when(pet.getWorld()).thenReturn(null);
        PetComponent.set(pet, component);

        component.setLevel(6);
        component.setExperience(14);
        component.getSchedulingModule().setCooldown("burst", 720L);

        AtomicReference<PetsplusComponents.PetData> captured = new AtomicReference<>();

        doAnswer(invocation -> {
            PetsplusComponents.PetData data = PetsplusComponents.PetData.empty()
                .withProgression(component.getProgressionModule().toData())
                .withScheduling(component.getSchedulingModule().toData());
            for (Map.Entry<String, Long> entry : component.getSchedulingModule().getAllCooldowns().entrySet()) {
                data = data.withCooldown(entry.getKey(), entry.getValue());
            }
            captured.set(data);
            return null;
        }).when(component).saveToEntity();

        PetComponent.getOrCreate(pet).saveToEntity();

        PetsplusComponents.PetData saved = captured.get();
        assertNotNull(saved, "writeCustomData hook should persist pet data on first save");
        assertEquals(6,
            saved.progression().map(woflo.petsplus.state.modules.ProgressionModule.Data::level).orElse(-1),
            "Progression level should persist on initial writeCustomData invocation");
        assertEquals(14L,
            saved.progression().map(woflo.petsplus.state.modules.ProgressionModule.Data::experience).orElse(-1L),
            "Experience remainder should persist on initial writeCustomData invocation");
        assertEquals(720L,
            saved.cooldowns().getOrDefault("burst", -1L).longValue(),
            "Cooldown data should persist on initial writeCustomData invocation");

        PetComponent.remove(pet);
    }

    private static PetComponent newComponent() {
        Map<ComponentType<?>, Object> components = new HashMap<>();

        MobEntity pet = mock(MobEntity.class, Mockito.withSettings()
            .extraInterfaces(EntityComponentAccessor.class)
            .defaultAnswer(invocation -> {
                if ("setComponent".equals(invocation.getMethod().getName())) {
                    ComponentType<?> type = invocation.getArgument(0);
                    Object value = invocation.getArgument(1);
                    components.put(type, value);
                    return null;
                }
                return Mockito.RETURNS_DEFAULTS.answer(invocation);
            }));
        ServerWorld world = mock(ServerWorld.class);
        when(world.getTime()).thenReturn(200L);
        when(pet.getWorld()).thenReturn(world);
        when(pet.getUuid()).thenReturn(PET_UUID);
        when(pet.getUuidAsString()).thenReturn(PET_UUID.toString());
        // Use lenient stubbing for type
        when(pet.getType()).thenAnswer(invocation -> EntityType.WOLF);

        COMPONENT_STORES.put(pet, components);

        when(((EntityComponentAccessor) pet).petsplus$castComponentValue(Mockito.any(ComponentType.class), Mockito.any())).thenAnswer(invocation -> {
            ComponentType<?> type = invocation.getArgument(0);
            Object fallback = invocation.getArgument(1);
            return components.getOrDefault(type, fallback);
        });

        return new PetComponent(pet);
    }

}
