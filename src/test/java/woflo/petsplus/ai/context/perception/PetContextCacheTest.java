package woflo.petsplus.ai.context.perception;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.context.social.SocialSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class PetContextCacheTest {

    @Test
    void cacheRefreshesOnStimulusAndIdleExpiry() {
        PetContextCache cache = new PetContextCache();
        PerceptionBus bus = new PerceptionBus();
        cache.attachTo(bus);

        MobEntity mob = Mockito.mock(MobEntity.class);
        World world = Mockito.mock(World.class);
        Mockito.when(mob.getEntityWorld()).thenReturn(world);
        Mockito.when(world.getTime()).thenReturn(0L, 1L, 5L, 40L, 41L);

        AtomicInteger captures = new AtomicInteger();
        PetContext snapshot1 = cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        PetContext snapshot2 = cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));

        assertSame(snapshot1, snapshot2, "snapshot should be reused while clean");
        assertEquals(1, captures.get(), "capture supplier should only run once so far");

        bus.publish(PerceptionStimulus.of(
            PerceptionStimulusType.GOAL_HISTORY,
            5L,
            ContextSlice.HISTORY,
            Identifier.of("petsplus", "play")
        ));

        PetContext snapshot3 = cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        assertNotSame(snapshot1, snapshot3, "history stimulus should dirty cache");
        assertEquals(2, captures.get(), "second capture should run after stimulus");

        PetContext snapshot4 = cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        assertNotSame(snapshot3, snapshot4, "idle expiry should force refresh");
        assertEquals(3, captures.get(), "third capture should run after idle expiry");
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
