package woflo.petsplus.ai.context.perception;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.emotions.BehaviouralEnergyProfile;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import woflo.petsplus.ai.context.PetContextCrowdSummary;
import woflo.petsplus.ai.context.perception.StimulusSnapshot;
import woflo.petsplus.ai.context.social.SocialSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwarmPerceptionBridgeTest {

    @Test
    void swarmUpdatesDirtyCachedContext() {
        ServerWorld world = Mockito.mock(ServerWorld.class);
        Mockito.when(world.getTime()).thenReturn(50L, 51L, 52L);

        MobEntity mob = Mockito.mock(MobEntity.class);
        Mockito.when(mob.getEntityWorld()).thenReturn(world);
        Mockito.when(mob.getX()).thenReturn(0.0);
        Mockito.when(mob.getY()).thenReturn(0.0);
        Mockito.when(mob.getZ()).thenReturn(0.0);

        PetComponent component = new PetComponent(mob);
        component.setOwnerUuid(UUID.randomUUID());

        PetSwarmIndex index = new PetSwarmIndex();
        SwarmPerceptionBridge bridge = new SwarmPerceptionBridge();
        index.addListener(bridge);

        PetContextCache cache = component.getContextCache();

        AtomicInteger captures = new AtomicInteger();
        cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        assertEquals(1, captures.get(), "initial snapshot should execute exactly once");

        long cellKey = net.minecraft.util.math.ChunkSectionPos.asLong(0, 0, 0);
        component.applySwarmUpdate(index, cellKey, 0.0, 0.0, 0.0);

        cache.snapshot(mob, () -> buildContext(mob, captures.incrementAndGet()));
        assertEquals(2, captures.get(), "swarm update should dirty cache through perception bridge");
    }

    private PetContext buildContext(MobEntity mob, int index) {
        World world = mob.getEntityWorld();
        long time = world != null ? world.getTime() + index : index;
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
            time,
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
