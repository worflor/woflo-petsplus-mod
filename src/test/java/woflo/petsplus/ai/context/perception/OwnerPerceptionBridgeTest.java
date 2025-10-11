package woflo.petsplus.ai.context.perception;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;
import woflo.petsplus.state.processing.OwnerBatchSnapshot;
import woflo.petsplus.state.processing.OwnerEventFrame;
import woflo.petsplus.state.processing.OwnerEventType;
import woflo.petsplus.state.processing.OwnerTaskBatch;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.context.social.SocialSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OwnerPerceptionBridgeTest {

    @Test
    void movementStimulusInvalidatesCachedContext() {
        OwnerPerceptionBridge bridge = new OwnerPerceptionBridge();

        MobEntity mob = Mockito.mock(MobEntity.class);
        World world = Mockito.mock(World.class);
        Mockito.when(mob.getEntityWorld()).thenReturn(world);
        Mockito.when(world.getTime()).thenReturn(0L, 0L, 0L);

        PetComponent component = new PetComponent(mob);
        PetContextCache cache = component.getContextCache();

        AtomicInteger captures = new AtomicInteger();
        cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        assertEquals(1, captures.get(), "initial capture should execute once");

        OwnerTaskBatch batch = OwnerTaskBatch.adHoc(null, List.of(component), EnumSet.of(OwnerEventType.MOVEMENT), 10L, null);
        OwnerBatchSnapshot snapshot = OwnerBatchSnapshot.capture(batch);

        try (OwnerEventFrame frame = OwnerEventFrame.obtain(
            OwnerEventType.MOVEMENT,
            Mockito.mock(net.minecraft.server.world.ServerWorld.class),
            null,
            null,
            List.of(component),
            List.of(),
            EnumSet.of(OwnerEventType.MOVEMENT),
            10L,
            batch,
            snapshot,
            null
        )) {
            bridge.onOwnerEvent(frame);
        }

        batch.close();

        PetContext second = cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        assertEquals(2, captures.get(), "movement event should dirty cache");

        OwnerTaskBatch abilityBatch = OwnerTaskBatch.adHoc(null, List.of(component), EnumSet.of(OwnerEventType.ABILITY_TRIGGER), 20L, null);
        OwnerBatchSnapshot abilitySnapshot = OwnerBatchSnapshot.capture(abilityBatch);

        try (OwnerEventFrame frame = OwnerEventFrame.obtain(
            OwnerEventType.ABILITY_TRIGGER,
            Mockito.mock(net.minecraft.server.world.ServerWorld.class),
            null,
            null,
            List.of(component),
            List.of(),
            EnumSet.of(OwnerEventType.ABILITY_TRIGGER),
            20L,
            abilityBatch,
            abilitySnapshot,
            null
        )) {
            bridge.onOwnerEvent(frame);
        }

        abilityBatch.close();

        PetContext third = cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        assertSame(second, third, "non-perception events should not dirty cache");
        assertEquals(2, captures.get(), "cache should not refresh after ability trigger");
    }

    private PetContext buildContext(MobEntity mob, int index) {
        return new PetContext(
            mob,
            null,
            null,
            0,
            Map.of(),
            Map.of(),
            null,
            null,
            null,
            1,
            0f,
            0L,
            null,
            false,
            Float.MAX_VALUE,
            List.<Entity>of(),
            PetContextCrowdSummary.empty(),
            BlockPos.ORIGIN,
            1000L + index,
            true,
            StimulusSnapshot.empty(),
            SocialSnapshot.empty(),
            false,
            new ArrayDeque<>(),
            Map.of(),
            Map.of(),
            0f,
            BehaviouralEnergyProfile.neutral()
        );
    }
}
