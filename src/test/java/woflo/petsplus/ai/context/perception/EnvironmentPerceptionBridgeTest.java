package woflo.petsplus.ai.context.perception;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.context.social.SocialSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EnvironmentPerceptionBridgeTest {

    @Test
    void broadcastsOnWeatherAndTimeChanges() {
        EnvironmentPerceptionBridge bridge = new EnvironmentPerceptionBridge();

        MobEntity mob = Mockito.mock(MobEntity.class);
        ServerWorld world = Mockito.mock(ServerWorld.class);
        Mockito.when(mob.getEntityWorld()).thenReturn(world);

        AtomicLong worldTime = new AtomicLong(0L);
        AtomicLong timeOfDay = new AtomicLong(0L);
        AtomicLong timeSegment = new AtomicLong(0L);

        Mockito.when(world.getTime()).thenAnswer(invocation -> worldTime.get());
        Mockito.when(world.getTimeOfDay()).thenAnswer(invocation -> timeOfDay.get());
        Mockito.when(world.isDay()).thenAnswer(invocation -> timeSegment.get() % 2 == 0);
        Mockito.when(world.isRaining()).thenReturn(false);
        Mockito.when(world.isThundering()).thenReturn(false);
        RegistryKey<net.minecraft.world.World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("minecraft", "overworld"));
        Mockito.when(world.getRegistryKey()).thenReturn(dimension);

        PetComponent component = new PetComponent(mob);
        PetContextCache cache = component.getContextCache();

        AtomicInteger captures = new AtomicInteger();
        cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        assertEquals(1, captures.get());

        // Initial broadcast should dirty cache.
        bridge.onWorldTick(world, List.of(component));
        worldTime.set(5L);
        PetContext refreshed = cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        assertEquals(2, captures.get(), "initial environment broadcast should refresh context");

        // Stable conditions should not trigger additional captures.
        bridge.onWorldTick(world, List.of(component));
        worldTime.set(10L);
        PetContext unchanged = cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        assertSame(refreshed, unchanged, "unchanged environment should reuse cached snapshot");
        assertEquals(2, captures.get());

        // Weather change should invalidate cache.
        Mockito.when(world.isRaining()).thenReturn(true);
        bridge.onWorldTick(world, List.of(component));
        worldTime.set(15L);
        cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        assertEquals(3, captures.get(), "weather change should dirty cache");

        // Advance time segment to trigger world slice update.
        Mockito.when(world.isRaining()).thenReturn(false);
        timeOfDay.set(2000L);
        timeSegment.incrementAndGet();
        bridge.onWorldTick(world, List.of(component));
        worldTime.set(20L);
        cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        assertEquals(4, captures.get(), "time segment change should dirty cache");
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
