package woflo.petsplus.events;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import woflo.petsplus.mood.EmotionBaselineTracker;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetMoodEngineTestUtil;
import woflo.petsplus.state.PetSwarmIndex;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.processing.AsyncWorkCoordinator;
import woflo.petsplus.state.processing.AsyncJobPriority;
import woflo.petsplus.stats.nature.NatureFlavorHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class EmotionsEventHandlerTest {

    @BeforeEach
    void resetSharedBaselines() {
        EmotionsEventHandler.resetSharedMoodBaselinesForTest();
    }

    @Test
    void archaeologyItemsAffectInventorySignature() {
        String withoutArchaeology = EmotionsEventHandler.buildInventorySignature(false, false, false, false, false, false);
        String withArchaeology = EmotionsEventHandler.buildInventorySignature(false, false, false, false, false, true);

        assertEquals(withoutArchaeology.length(), withArchaeology.length(), "signature length should be stable");
        assertEquals(6, withArchaeology.length(), "signature should encode all six toggles");
        assertEquals(withoutArchaeology.substring(0, 5), withArchaeology.substring(0, 5),
            "only the archaeology bit should change in this scenario");
        assertEquals('0', withoutArchaeology.charAt(5), "archaeology should be off without matching items");
        assertEquals('1', withArchaeology.charAt(5), "archaeology items should flip the final bit");
        assertNotEquals(withoutArchaeology, withArchaeology,
            "changing archaeology inventory should alter the signature");
    }

    @Test
    void emotionAccumulatorFlushCapturesAsyncSummary() throws Exception {
        MinecraftServer server = new MinecraftServer();
        ServerWorld world = new ServerWorld(server);
        world.setTime(200L);

        MobEntity pet = new MobEntity(world);
        pet.setPos(0.0, 0.0, 0.0);
        pet.setVelocity(Vec3d.ZERO);
        pet.bodyYaw = 0f;
        pet.headYaw = 0f;

        UUID ownerId = UUID.randomUUID();

        PetComponent component = mock(PetComponent.class);
        when(component.getPet()).thenReturn(pet);
        when(component.getOwnerUuid()).thenReturn(ownerId);
        when(component.getTamedTick()).thenReturn(0L);
        when(component.getBondStrength()).thenReturn(0L);
        when(component.getCurrentMood()).thenReturn(PetComponent.Mood.CALM);
        when(component.getMoodLevel()).thenReturn(1);
        when(component.getOrCreateSocialJitterSeed()).thenReturn(0);
        lenient().when(component.getStateData(anyString(), eq(Long.class))).thenReturn(null);
        lenient().when(component.getStateData(anyString(), eq(Long.class), anyLong()))
            .thenAnswer(inv -> inv.getArgument(2));
        lenient().when(component.getStateData(anyString(), eq(Integer.class))).thenReturn(null);
        lenient().when(component.getStateData(anyString(), eq(Integer.class), anyInt()))
            .thenAnswer(inv -> inv.getArgument(2));

        EnumMap<PetComponent.Mood, Float> baseBlend = new EnumMap<>(PetComponent.Mood.class);
        baseBlend.put(PetComponent.Mood.CALM, 0.8f);
        baseBlend.put(PetComponent.Mood.HAPPY, 0.2f);
        AtomicReference<EnumMap<PetComponent.Mood, Float>> moodRef = new AtomicReference<>(baseBlend);
        when(component.getMoodBlend()).thenAnswer(inv -> new EnumMap<>(moodRef.get()));
        doNothing().when(component).updateMood();
        AtomicBoolean emotionApplied = new AtomicBoolean(false);
        doAnswer(invocation -> {
            PetComponent.Emotion emotion = invocation.getArgument(0);
            float amount = invocation.getArgument(1);
            EnumMap<PetComponent.Mood, Float> updated = new EnumMap<>(moodRef.get());
            if (emotion == PetComponent.Emotion.CHEERFUL) {
                updated.merge(PetComponent.Mood.HAPPY, amount * 0.75f, Float::sum);
                updated.merge(PetComponent.Mood.CALM, -amount * 0.75f, Float::sum);
            }
            moodRef.set(updated);
            emotionApplied.set(true);
            return null;
        }).when(component).pushEmotion(any(PetComponent.Emotion.class), anyFloat());

        AsyncWorkCoordinator coordinator = newCoordinator(() -> 1.0D);

        try (MockedStatic<PetComponent> componentStatic = mockStatic(PetComponent.class);
             MockedStatic<EmotionContextCues> cuesStatic = mockStatic(EmotionContextCues.class)) {
            componentStatic.when(() -> PetComponent.getOrCreate(pet)).thenReturn(component);
            CompletableFuture<StimulusSummary> summaryFuture = new CompletableFuture<>();
            cuesStatic.when(() -> EmotionContextCues.recordStimulus(eq(null), any(StimulusSummary.class)))
                .thenAnswer(invocation -> {
                    assertTrue(emotionApplied.get(), "stimulus summary should record after emotions apply");
                    summaryFuture.complete(invocation.getArgument(1));
                    return null;
                });
            cuesStatic.when(() -> EmotionContextCues.sendCue(any(), anyString(), any(Text.class), anyLong()))
                .thenAnswer(inv -> null);
            cuesStatic.when(() -> EmotionContextCues.sendCue(any(), anyString(), any(Text.class)))
                .thenAnswer(inv -> null);

            Class<?> batchClass = Class.forName("woflo.petsplus.events.EmotionsEventHandler$EmotionAccumulatorBatch");
            Constructor<?> ctor = batchClass.getDeclaredConstructor(
                AsyncWorkCoordinator.class,
                ServerPlayerEntity.class,
                java.util.function.LongSupplier.class,
                boolean.class
            );
            ctor.setAccessible(true);
            Object batch = ctor.newInstance(coordinator, null, (java.util.function.LongSupplier) world::getTime, true);

            Method push = batchClass.getMethod("push", PetComponent.class, PetComponent.Emotion.class, float.class);
            push.invoke(batch, component, PetComponent.Emotion.CHEERFUL, 0.4f);

            Method flush = batchClass.getDeclaredMethod("flush");
            flush.setAccessible(true);
            CompletableFuture<?> flushFuture = (CompletableFuture<?>) flush.invoke(batch);

            StimulusSummary summary = summaryFuture.get(2, TimeUnit.SECONDS);
            flushFuture.get(2, TimeUnit.SECONDS);
            assertEquals(1, summary.petCount(), "expected single pet sample for async flush");
            assertTrue(summary.totalDelta() > 0f, "summary should capture non-zero mood delta");
            Map<PetComponent.Mood, Float> deltas = summary.moodDeltas();
            assertEquals(0.3f, deltas.get(PetComponent.Mood.HAPPY), 1.0e-4f);
            assertEquals(-0.3f, deltas.get(PetComponent.Mood.CALM), 1.0e-4f);
        } finally {
            coordinator.close();
        }
    }

    @Test
    void emotionAccumulatorCapturesOverlappingAsyncFlushes() throws Exception {
        MinecraftServer server = new MinecraftServer();
        ServerWorld world = new ServerWorld(server);
        world.setTime(260L);

        MobEntity pet = new MobEntity(world);
        pet.setPos(0.0, 0.0, 0.0);
        pet.setVelocity(Vec3d.ZERO);
        pet.bodyYaw = 0f;
        pet.headYaw = 0f;

        PetComponent component = mock(PetComponent.class);
        when(component.getPet()).thenReturn(pet);
        when(component.getOwnerUuid()).thenReturn(UUID.randomUUID());
        when(component.getTamedTick()).thenReturn(0L);
        when(component.getBondStrength()).thenReturn(0L);
        when(component.getCurrentMood()).thenReturn(PetComponent.Mood.CALM);
        when(component.getMoodLevel()).thenReturn(1);
        when(component.getOrCreateSocialJitterSeed()).thenReturn(0);

        EnumMap<PetComponent.Mood, Float> baseBlend = new EnumMap<>(PetComponent.Mood.class);
        baseBlend.put(PetComponent.Mood.CALM, 0.6f);
        baseBlend.put(PetComponent.Mood.HAPPY, 0.4f);
        AtomicReference<EnumMap<PetComponent.Mood, Float>> moodRef = new AtomicReference<>(baseBlend);
        when(component.getMoodBlend()).thenAnswer(inv -> new EnumMap<>(moodRef.get()));
        doNothing().when(component).updateMood();
        doAnswer(invocation -> {
            PetComponent.Emotion emotion = invocation.getArgument(0);
            float amount = invocation.getArgument(1);
            EnumMap<PetComponent.Mood, Float> updated = new EnumMap<>(moodRef.get());
            if (emotion == PetComponent.Emotion.CHEERFUL) {
                updated.merge(PetComponent.Mood.HAPPY, amount, Float::sum);
                updated.merge(PetComponent.Mood.CALM, -amount, Float::sum);
            }
            moodRef.set(updated);
            return null;
        }).when(component).pushEmotion(any(PetComponent.Emotion.class), anyFloat());

        AsyncWorkCoordinator coordinator = mock(AsyncWorkCoordinator.class);

        CountDownLatch firstJobStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstJob = new CountDownLatch(1);
        CountDownLatch secondJobCompleted = new CountDownLatch(1);
        AtomicInteger submissionIndex = new AtomicInteger();

        Answer<CompletableFuture<Object>> asyncAnswer = invocation -> {
            @SuppressWarnings("unchecked")
            Callable<Object> job = (Callable<Object>) invocation.getArgument(1);
            @SuppressWarnings("unchecked")
            Consumer<Object> applier = (Consumer<Object>) invocation.getArgument(2);
            CompletableFuture<Object> future = new CompletableFuture<>();
            int index = submissionIndex.getAndIncrement();
            Thread worker = new Thread(() -> {
                try {
                    if (index == 0) {
                        firstJobStarted.countDown();
                        releaseFirstJob.await(2, TimeUnit.SECONDS);
                    }
                    Object result = job.call();
                    applier.accept(result);
                    if (index == 1) {
                        secondJobCompleted.countDown();
                    }
                    future.complete(result);
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
            worker.setDaemon(true);
            worker.start();
            return future;
        };

        when(coordinator.submitStandalone(anyString(), any(Callable.class), any(Consumer.class))).thenAnswer(asyncAnswer);
        when(coordinator.submitStandalone(anyString(), any(Callable.class), any(Consumer.class), any(AsyncJobPriority.class)))
            .thenAnswer(asyncAnswer);

        try (MockedStatic<PetComponent> componentStatic = mockStatic(PetComponent.class);
             MockedStatic<EmotionContextCues> cuesStatic = mockStatic(EmotionContextCues.class)) {
            componentStatic.when(() -> PetComponent.getOrCreate(pet)).thenReturn(component);

            List<StimulusSummary> summaries = new ArrayList<>();
            CountDownLatch summaryLatch = new CountDownLatch(2);
            cuesStatic.when(() -> EmotionContextCues.recordStimulus(eq(null), any(StimulusSummary.class)))
                .thenAnswer(invocation -> {
                    summaries.add(invocation.getArgument(1));
                    summaryLatch.countDown();
                    return null;
                });
            cuesStatic.when(() -> EmotionContextCues.sendCue(any(), anyString(), any(Text.class), anyLong()))
                .thenAnswer(inv -> null);
            cuesStatic.when(() -> EmotionContextCues.sendCue(any(), anyString(), any(Text.class)))
                .thenAnswer(inv -> null);

            Class<?> batchClass = Class.forName("woflo.petsplus.events.EmotionsEventHandler$EmotionAccumulatorBatch");
            Constructor<?> ctor = batchClass.getDeclaredConstructor(
                AsyncWorkCoordinator.class,
                ServerPlayerEntity.class,
                java.util.function.LongSupplier.class,
                boolean.class
            );
            ctor.setAccessible(true);
            Object firstBatch = ctor.newInstance(coordinator, null, (java.util.function.LongSupplier) world::getTime, true);
            Object secondBatch = ctor.newInstance(coordinator, null, (java.util.function.LongSupplier) world::getTime, true);

            Method push = batchClass.getMethod("push", PetComponent.class, PetComponent.Emotion.class, float.class);
            Method flush = batchClass.getDeclaredMethod("flush");
            flush.setAccessible(true);

            push.invoke(firstBatch, component, PetComponent.Emotion.CHEERFUL, 0.2f);
            CompletableFuture<?> firstFlush = (CompletableFuture<?>) flush.invoke(firstBatch);

            firstJobStarted.await(1, TimeUnit.SECONDS);

            push.invoke(secondBatch, component, PetComponent.Emotion.CHEERFUL, 0.1f);
            CompletableFuture<?> secondFlush = (CompletableFuture<?>) flush.invoke(secondBatch);

            secondJobCompleted.await(2, TimeUnit.SECONDS);
            releaseFirstJob.countDown();

            assertTrue(summaryLatch.await(3, TimeUnit.SECONDS), "timed out waiting for summaries");

            firstFlush.get(2, TimeUnit.SECONDS);
            secondFlush.get(2, TimeUnit.SECONDS);

            assertEquals(2, summaries.size(), "should record summaries for both flushes");
            long smallCount = summaries.stream()
                .map(StimulusSummary::moodDeltas)
                .filter(deltas ->
                    Math.abs(deltas.get(PetComponent.Mood.HAPPY) - 0.1f) < 1.0e-4f
                        && Math.abs(deltas.get(PetComponent.Mood.CALM) + 0.1f) < 1.0e-4f)
                .count();
            assertEquals(1L, smallCount, "should capture exactly one summary with the second batch delta");

            long largeCount = summaries.stream()
                .map(StimulusSummary::moodDeltas)
                .filter(deltas ->
                    Math.abs(deltas.get(PetComponent.Mood.HAPPY) - 0.2f) < 1.0e-4f
                        && Math.abs(deltas.get(PetComponent.Mood.CALM) + 0.2f) < 1.0e-4f)
                .count();
            assertEquals(1L, largeCount, "should capture exactly one summary with the first batch delta");
        } finally {
            releaseFirstJob.countDown();
        }
    }

    @Test
    void blockBreakDispatchesValuableFlag() throws Exception {
        MinecraftServer server = new MinecraftServer();
        ServerWorld world = new ServerWorld(server);
        world.setTime(80L);
        ServerPlayerEntity player = new ServerPlayerEntity(world);

        StateManager stateManager = mock(StateManager.class);
        List<String> events = new ArrayList<>();
        List<Map<String, Object>> payloads = new ArrayList<>();
        doAnswer(inv -> {
            events.add(inv.getArgument(1));
            payloads.add(inv.getArgument(2));
            return null;
        }).when(stateManager).dispatchAbilityTrigger(any(ServerPlayerEntity.class), anyString(), any());

        EmotionCueConfig config = mock(EmotionCueConfig.class);
        when(config.findBlockBreakDefinition(any())).thenReturn(null);

        Identifier blockId = Identifier.of("minecraft", "diamond_ore");
        TestBlockState state = new TestBlockState(blockId);
        state.setValuable(true);

        try (MockedStatic<StateManager> stateManagerStatic = mockStatic(StateManager.class);
             MockedStatic<EmotionCueConfig> configStatic = mockStatic(EmotionCueConfig.class);
             MockedStatic<NatureFlavorHandler> natureStatic = mockStatic(NatureFlavorHandler.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(stateManager);
            configStatic.when(EmotionCueConfig::get).thenReturn(config);
            natureStatic.when(() -> NatureFlavorHandler.triggerForOwner(any(), anyInt(), any())).thenAnswer(inv -> null);

            EmotionsEventHandler.emitOwnerBrokeBlockTrigger(player, state);
        }

        int idx = events.indexOf("owner_broke_block");
        assertTrue(idx >= 0, "Expected owner_broke_block trigger");
        Map<String, Object> payload = payloads.get(idx);
        assertNotNull(payload, "Block break payload should not be null");
        assertEquals(Boolean.TRUE, payload.get("block_valuable"), "Valuable ores should set block_valuable flag");
        assertEquals(blockId, payload.get("block_identifier"), "Payload should include block identifier instance");
        assertEquals(blockId.toString(), payload.get("block_id"), "Payload should include namespaced string identifier");
        assertEquals(blockId.getPath(), payload.get("block_id_no_namespace"),
            "Payload should include namespace-free identifier path");
    }

    @Test
    void blockBreakDispatchesNonValuableFlag() throws Exception {
        MinecraftServer server = new MinecraftServer();
        ServerWorld world = new ServerWorld(server);
        world.setTime(92L);
        ServerPlayerEntity player = new ServerPlayerEntity(world);

        StateManager stateManager = mock(StateManager.class);
        List<String> events = new ArrayList<>();
        List<Map<String, Object>> payloads = new ArrayList<>();
        doAnswer(inv -> {
            events.add(inv.getArgument(1));
            payloads.add(inv.getArgument(2));
            return null;
        }).when(stateManager).dispatchAbilityTrigger(any(ServerPlayerEntity.class), anyString(), any());

        EmotionCueConfig config = mock(EmotionCueConfig.class);
        when(config.findBlockBreakDefinition(any())).thenReturn(null);

        Identifier blockId = Identifier.of("minecraft", "stone");
        TestBlockState state = new TestBlockState(blockId);
        state.setValuable(false);

        try (MockedStatic<StateManager> stateManagerStatic = mockStatic(StateManager.class);
             MockedStatic<EmotionCueConfig> configStatic = mockStatic(EmotionCueConfig.class);
             MockedStatic<NatureFlavorHandler> natureStatic = mockStatic(NatureFlavorHandler.class)) {
            stateManagerStatic.when(() -> StateManager.forWorld(world)).thenReturn(stateManager);
            configStatic.when(EmotionCueConfig::get).thenReturn(config);
            natureStatic.when(() -> NatureFlavorHandler.triggerForOwner(any(), anyInt(), any())).thenAnswer(inv -> null);

            EmotionsEventHandler.emitOwnerBrokeBlockTrigger(player, state);
        }

        int idx = events.indexOf("owner_broke_block");
        assertTrue(idx >= 0, "Expected owner_broke_block trigger");
        Map<String, Object> payload = payloads.get(idx);
        assertNotNull(payload, "Block break payload should not be null");
        assertEquals(Boolean.FALSE, payload.get("block_valuable"), "Non-valuable blocks should clear block_valuable flag");
        assertEquals(blockId, payload.get("block_identifier"), "Payload should include block identifier instance");
        assertEquals(blockId.toString(), payload.get("block_id"), "Payload should include namespaced string identifier");
        assertEquals(blockId.getPath(), payload.get("block_id_no_namespace"),
            "Payload should include namespace-free identifier path");
    }

    @Test
    void isValuableBlockDetectsOreTags() throws Exception {
        BlockState state = mock(BlockState.class);
        when(state.isIn(BlockTags.DIAMOND_ORES)).thenReturn(true);

        assertTrue(invokeIsValuableBlock(state), "diamond ores should be valuable");
    }

    @Test
    void isValuableBlockTreatsNullAsNotValuable() throws Exception {
        assertFalse(invokeIsValuableBlock(null), "null block states should not be valuable");
    }

    private static boolean invokeIsValuableBlock(BlockState state) throws Exception {
        Method method = EmotionsEventHandler.class.getDeclaredMethod("isValuableBlock", BlockState.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, state);
    }

    private static final class TestBlockState extends BlockState implements EmotionsEventHandler.BlockIdentifierProvider {
        private final Identifier identifier;
        private boolean valuable;

        private TestBlockState(Identifier identifier) {
            this.identifier = identifier;
        }

        void setValuable(boolean value) {
            this.valuable = value;
        }

        @Override
        public boolean isIn(TagKey<Block> tag) {
            return valuable;
        }

        @Override
        public boolean isOf(Block block) {
            return valuable;
        }

        @Override
        public Block getBlock() {
            return null;
        }

        @Override
        public Identifier petsplus$getIdentifier() {
            return identifier;
        }
    }

    @Test
    void directEmotionPushesRefreshSharedBaselines() throws Exception {
        MinecraftServer server = new MinecraftServer();
        ServerWorld world = new ServerWorld(server);
        world.setTime(400L);

        MobEntity pet = new MobEntity(world);
        EnumMap<PetComponent.Mood, Float> baseBlend = new EnumMap<>(PetComponent.Mood.class);
        baseBlend.put(PetComponent.Mood.CALM, 0.7f);
        baseBlend.put(PetComponent.Mood.HAPPY, 0.3f);
        AtomicReference<EnumMap<PetComponent.Mood, Float>> blendRef = new AtomicReference<>(baseBlend);

        try (var ignored = PetMoodEngineTestUtil.mockEngine(blendRef, delta -> {
            EnumMap<PetComponent.Mood, Float> updated = new EnumMap<>(blendRef.get());
            if (delta != null && delta.emotion() == PetComponent.Emotion.CHEERFUL) {
                float deltaAmount = delta.amount() * 0.75f;
                updated.merge(PetComponent.Mood.HAPPY, deltaAmount, Float::sum);
                updated.merge(PetComponent.Mood.CALM, -deltaAmount, Float::sum);
            }
            blendRef.set(updated);
        })) {
            PetComponent component = new PetComponent(pet);

            Map<PetComponent.Mood, Float> seededBaseline = EmotionBaselineTracker.copyBaseline(component);
            assertEquals(0.7f, seededBaseline.get(PetComponent.Mood.CALM), 1.0e-4f);
            assertEquals(0.3f, seededBaseline.get(PetComponent.Mood.HAPPY), 1.0e-4f);

            component.pushEmotion(PetComponent.Emotion.CHEERFUL, 0.4f);

            EnumMap<PetComponent.Mood, Float> expected = blendRef.get();
            Map<PetComponent.Mood, Float> refreshedBaseline = EmotionBaselineTracker.copyBaseline(component);
            assertEquals(expected.get(PetComponent.Mood.HAPPY), refreshedBaseline.get(PetComponent.Mood.HAPPY), 1.0e-4f);
            assertEquals(expected.get(PetComponent.Mood.CALM), refreshedBaseline.get(PetComponent.Mood.CALM), 1.0e-4f);
        }
    }

    @Test
    void emotionAccumulatorFlushesSummaryViaServerSubmit() throws Exception {
        class TrackingServer extends MinecraftServer {
            private final AtomicBoolean submitted = new AtomicBoolean(false);

            @Override
            public java.util.concurrent.CompletableFuture<?> submit(Runnable runnable) {
                submitted.set(true);
                return super.submit(runnable);
            }
        }

        TrackingServer server = new TrackingServer();
        ServerWorld world = new ServerWorld(server);
        world.setTime(320L);
        ServerPlayerEntity owner = new ServerPlayerEntity(world);

        MobEntity pet = new MobEntity(world);
        pet.setPos(0.0, 0.0, 0.0);
        pet.setVelocity(Vec3d.ZERO);

        PetComponent component = mock(PetComponent.class);
        when(component.getPet()).thenReturn(pet);
        when(component.getOwnerUuid()).thenReturn(owner.getUuid());
        when(component.getTamedTick()).thenReturn(0L);
        when(component.getBondStrength()).thenReturn(0L);
        when(component.getCurrentMood()).thenReturn(PetComponent.Mood.CALM);
        when(component.getMoodLevel()).thenReturn(1);
        when(component.getOrCreateSocialJitterSeed()).thenReturn(0);

        EnumMap<PetComponent.Mood, Float> blend = new EnumMap<>(PetComponent.Mood.class);
        blend.put(PetComponent.Mood.CALM, 0.75f);
        blend.put(PetComponent.Mood.HAPPY, 0.25f);
        AtomicReference<EnumMap<PetComponent.Mood, Float>> blendRef = new AtomicReference<>(blend);
        when(component.getMoodBlend()).thenAnswer(inv -> new EnumMap<>(blendRef.get()));
        doNothing().when(component).updateMood();
        doAnswer(invocation -> {
            PetComponent.Emotion emotion = invocation.getArgument(0);
            float amount = invocation.getArgument(1);
            EnumMap<PetComponent.Mood, Float> updated = new EnumMap<>(blendRef.get());
            if (emotion == PetComponent.Emotion.CHEERFUL) {
                updated.merge(PetComponent.Mood.HAPPY, amount * 0.6f, Float::sum);
                updated.merge(PetComponent.Mood.CALM, -amount * 0.6f, Float::sum);
            }
            blendRef.set(updated);
            return null;
        }).when(component).pushEmotion(any(PetComponent.Emotion.class), anyFloat());

        AsyncWorkCoordinator coordinator = newCoordinator(() -> 1.0D);

        try (MockedStatic<PetComponent> componentStatic = mockStatic(PetComponent.class);
             MockedStatic<EmotionContextCues> cuesStatic = mockStatic(EmotionContextCues.class)) {
            componentStatic.when(() -> PetComponent.getOrCreate(pet)).thenReturn(component);

            CompletableFuture<StimulusSummary> summaryFuture = new CompletableFuture<>();
            cuesStatic.when(() -> EmotionContextCues.recordStimulus(eq(owner), any(StimulusSummary.class)))
                .thenAnswer(invocation -> {
                    summaryFuture.complete(invocation.getArgument(1));
                    return null;
                });
            cuesStatic.when(() -> EmotionContextCues.sendCue(any(), anyString(), any(Text.class), anyLong()))
                .thenAnswer(inv -> null);
            cuesStatic.when(() -> EmotionContextCues.sendCue(any(), anyString(), any(Text.class)))
                .thenAnswer(inv -> null);

            Class<?> batchClass = Class.forName("woflo.petsplus.events.EmotionsEventHandler$EmotionAccumulatorBatch");
            Constructor<?> ctor = batchClass.getDeclaredConstructor(
                AsyncWorkCoordinator.class,
                ServerWorld.class,
                ServerPlayerEntity.class
            );
            ctor.setAccessible(true);
            Object batch = ctor.newInstance(coordinator, world, owner);

            Method push = batchClass.getMethod("push", PetComponent.class, PetComponent.Emotion.class, float.class);
            push.invoke(batch, component, PetComponent.Emotion.CHEERFUL, 0.3f);

            Method flush = batchClass.getDeclaredMethod("flush");
            flush.setAccessible(true);
            CompletableFuture<?> flushFuture = (CompletableFuture<?>) flush.invoke(batch);

            StimulusSummary summary = summaryFuture.get(2, TimeUnit.SECONDS);
            assertTrue(summary.totalDelta() > 0f, "summary should include mood deltas");
            assertTrue(server.submitted.get(), "summary recording should run via server submit");
            flushFuture.get(2, TimeUnit.SECONDS);
        } finally {
            coordinator.close();
        }
    }

    @Test
    void packSummaryFallbackAppliesStimuliWhenAsyncRejected() throws Exception {
        MinecraftServer server = new MinecraftServer();
        ServerWorld world = new ServerWorld(server);
        world.setTime(400L);

        MobEntity petA = new MobEntity(world);
        petA.setPos(0.0, 0.0, 0.0);
        MobEntity petB = new MobEntity(world);
        petB.setPos(2.0, 0.0, 0.0);

        PetComponent componentA = mock(PetComponent.class);
        PetComponent componentB = mock(PetComponent.class);
        configureComponentForPack(componentA, petA, PetComponent.Mood.CALM);
        configureComponentForPack(componentB, petB, PetComponent.Mood.HAPPY);

        AtomicBoolean emotionsApplied = new AtomicBoolean(false);
        doAnswer(invocation -> {
            emotionsApplied.set(true);
            return null;
        }).when(componentA).pushEmotion(any(PetComponent.Emotion.class), anyFloat());

        PetSwarmIndex.SwarmEntry entryA = mock(PetSwarmIndex.SwarmEntry.class);
        when(entryA.pet()).thenReturn(petA);
        when(entryA.component()).thenReturn(componentA);
        PetSwarmIndex.SwarmEntry entryB = mock(PetSwarmIndex.SwarmEntry.class);
        when(entryB.pet()).thenReturn(petB);
        when(entryB.component()).thenReturn(componentB);

        Map<UUID, PetSwarmIndex.SwarmEntry> entriesById = new HashMap<>();
        entriesById.put(petA.getUuid(), entryA);
        entriesById.put(petB.getUuid(), entryB);

        Map<MobEntity, woflo.petsplus.behavior.social.PetSocialData> petDataCache = new HashMap<>();

        List<Object> inputs = new ArrayList<>();
        inputs.add(newPackInput(petA.getUuid(), 6000L, 0.7f, PetComponent.Mood.CALM, 0.0, 0.0, 0.0));
        inputs.add(newPackInput(petB.getUuid(), 4000L, 0.6f, PetComponent.Mood.HAPPY, 2.0, 0.0, 0.0));

        AsyncWorkCoordinator coordinator = newCoordinator(() -> 1.0D);
        StateManager stateManager = mock(StateManager.class);
        when(stateManager.getAsyncWorkCoordinator()).thenReturn(coordinator);

        try (MockedStatic<PetComponent> componentStatic = mockStatic(PetComponent.class);
             MockedStatic<EmotionContextCues> cuesStatic = mockStatic(EmotionContextCues.class)) {
            componentStatic.when(() -> PetComponent.getOrCreate(petA)).thenReturn(componentA);
            componentStatic.when(() -> PetComponent.getOrCreate(petB)).thenReturn(componentB);

            cuesStatic.when(() -> EmotionContextCues.sendCue(any(), anyString(), any(Text.class), anyLong()))
                .thenAnswer(inv -> null);
            cuesStatic.when(() -> EmotionContextCues.sendCue(any(), anyString(), any(Text.class)))
                .thenAnswer(inv -> null);
            cuesStatic.when(() -> EmotionContextCues.recordStimulus(any(), any(StimulusSummary.class)))
                .thenAnswer(inv -> null);

            Method fallback = EmotionsEventHandler.class.getDeclaredMethod(
                "runPackSummaryFallback",
                ServerPlayerEntity.class,
                ServerWorld.class,
                long.class,
                StateManager.class,
                Map.class,
                Map.class,
                List.class
            );
            fallback.setAccessible(true);
            fallback.invoke(null, null, world, world.getTime(), stateManager, petDataCache, entriesById, inputs);

            assertTrue(emotionsApplied.get(), "fallback should apply pack emotions");
        } finally {
            coordinator.close();
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void packSummariesFallbackRunsOnMainThreadWhenAsyncFails() throws Exception {
        TrackingServer server = new TrackingServer();
        ServerWorld world = new ServerWorld(server);
        world.setTime(600L);

        AsyncWorkCoordinator coordinator = mock(AsyncWorkCoordinator.class);
        StateManager stateManager = mock(StateManager.class);
        when(stateManager.getAsyncWorkCoordinator()).thenReturn(coordinator);

        CompletableFuture failingFuture = new CompletableFuture<>();
        when(coordinator.submitStandalone(anyString(), any(), any(), eq(AsyncJobPriority.NORMAL)))
            .thenReturn(failingFuture);

        Map<MobEntity, Object> petDataCache = new HashMap<>();
        Map<UUID, PetSwarmIndex.SwarmEntry> entriesById = new HashMap<>();

        Object input = newPackInput(UUID.randomUUID(), 36000L, 0.4f, PetComponent.Mood.CALM,
            0.0, 0.0, 0.0);
        List<Object> inputs = List.of(input);

        Method schedule = EmotionsEventHandler.class.getDeclaredMethod("schedulePackSummaries",
            ServerPlayerEntity.class,
            ServerWorld.class,
            long.class,
            StateManager.class,
            Map.class,
            Map.class,
            List.class
        );
        schedule.setAccessible(true);
        schedule.invoke(null, null, world, world.getTime(), stateManager, petDataCache, entriesById, inputs);

        failingFuture.completeExceptionally(new RuntimeException("boom"));

        assertTrue(server.awaitSubmit(), "fallback should run through the server executor");
        verify(coordinator).submitStandalone(anyString(), any(), any(), eq(AsyncJobPriority.NORMAL));
    }

    private static final class TrackingServer extends MinecraftServer {
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public CompletableFuture<?> submit(Runnable runnable) {
            try {
                if (runnable != null) {
                    runnable.run();
                }
            } finally {
                latch.countDown();
            }
            return CompletableFuture.completedFuture(null);
        }

        boolean awaitSubmit() throws InterruptedException {
            return latch.await(1, TimeUnit.SECONDS);
        }
    }

    private static AsyncWorkCoordinator newCoordinator(DoubleSupplier loadSupplier)
        throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Constructor<AsyncWorkCoordinator> ctor = AsyncWorkCoordinator.class
            .getDeclaredConstructor(DoubleSupplier.class, Executor.class);
        ctor.setAccessible(true);
        return ctor.newInstance(loadSupplier, (Executor) Runnable::run);
    }

    private static void configureComponentForPack(PetComponent component, MobEntity pet, PetComponent.Mood mood) {
        when(component.getPet()).thenReturn(pet);
        when(component.getOwnerUuid()).thenReturn(UUID.randomUUID());
        when(component.getTamedTick()).thenReturn(0L);
        when(component.getBondStrength()).thenReturn(0L);
        when(component.getCurrentMood()).thenReturn(mood);
        when(component.getMoodLevel()).thenReturn(1);
        when(component.getOrCreateSocialJitterSeed()).thenReturn(0);
        EnumMap<PetComponent.Mood, Float> blend = new EnumMap<>(PetComponent.Mood.class);
        blend.put(PetComponent.Mood.CALM, mood == PetComponent.Mood.CALM ? 0.7f : 0.3f);
        blend.put(PetComponent.Mood.HAPPY, mood == PetComponent.Mood.HAPPY ? 0.7f : 0.3f);
        AtomicReference<EnumMap<PetComponent.Mood, Float>> blendRef = new AtomicReference<>(blend);
        when(component.getMoodBlend()).thenAnswer(inv -> new EnumMap<>(blendRef.get()));
        doNothing().when(component).updateMood();
        doAnswer(invocation -> {
            PetComponent.Emotion emotion = invocation.getArgument(0);
            float amount = invocation.getArgument(1);
            EnumMap<PetComponent.Mood, Float> updated = new EnumMap<>(blendRef.get());
            if (emotion == PetComponent.Emotion.UBUNTU || emotion == PetComponent.Emotion.SOBREMESA
                || emotion == PetComponent.Emotion.PROTECTIVENESS || emotion == PetComponent.Emotion.GLEE) {
                updated.merge(PetComponent.Mood.HAPPY, amount * 0.5f, Float::sum);
                updated.merge(PetComponent.Mood.CALM, -amount * 0.25f, Float::sum);
            }
            blendRef.set(updated);
            return null;
        }).when(component).pushEmotion(any(PetComponent.Emotion.class), anyFloat());

        Map<String, Object> stateData = new HashMap<>();
        lenient().when(component.getStateData(anyString(), eq(Long.class)))
            .thenAnswer(inv -> stateData.get(inv.getArgument(0)));
        lenient().when(component.getStateData(anyString(), eq(Long.class), anyLong()))
            .thenAnswer(inv -> stateData.getOrDefault(inv.getArgument(0), inv.getArgument(2)));
        lenient().when(component.getStateData(anyString(), eq(Integer.class)))
            .thenAnswer(inv -> stateData.get(inv.getArgument(0)));
        lenient().when(component.getStateData(anyString(), eq(Integer.class), anyInt()))
            .thenAnswer(inv -> stateData.getOrDefault(inv.getArgument(0), inv.getArgument(2)));
        doAnswer(invocation -> {
            stateData.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(component).setStateData(anyString(), any());
    }

    private static Object newPackInput(UUID id, long age, float bond, PetComponent.Mood mood,
                                       double x, double y, double z) throws Exception {
        Class<?> packInputClass = Class.forName("woflo.petsplus.events.EmotionsEventHandler$PackPetInput");
        Constructor<?> ctor = packInputClass.getDeclaredConstructor(UUID.class, long.class, float.class,
            PetComponent.Mood.class, double.class, double.class, double.class);
        ctor.setAccessible(true);
        return ctor.newInstance(id, age, bond, mood, x, y, z);
    }
}
