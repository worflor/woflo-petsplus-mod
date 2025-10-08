package woflo.petsplus.ai.goals.idle;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.server.world.ServerWorld;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TailChaseGoalTest {

    @Test
    void tailChaseYawStaysNormalizedAcrossSpins() throws Exception {
        MobEntity mob = mock(MobEntity.class);
        ServerWorld world = mock(ServerWorld.class);
        when(world.isClient()).thenReturn(false);
        when(mob.getEntityWorld()).thenReturn(world);
        when(mob.getRandom()).thenReturn(net.minecraft.util.math.random.Random.create(42));

        AtomicReference<Float> yawRef = new AtomicReference<>(0f);
        doAnswer(invocation -> {
            float yaw = invocation.getArgument(0);
            yawRef.set(yaw);
            return null;
        }).when(mob).setYaw(anyFloat());
        when(mob.getYaw()).thenAnswer(invocation -> yawRef.get());

        mob.setYaw(0f);
        mob.bodyYaw = 0f;
        mob.headYaw = 0f;

        TailChaseGoal goal = new TailChaseGoal(mob);

        Method start = TailChaseGoal.class.getDeclaredMethod("onStartGoal");
        start.setAccessible(true);
        start.invoke(goal);

        Method tick = TailChaseGoal.class.getDeclaredMethod("onTickGoal");
        tick.setAccessible(true);
        for (int i = 0; i < 120; i++) {
            tick.invoke(goal);
        }

        float yaw = mob.getYaw();
        assertTrue(yaw <= 180f && yaw >= -180f, "Yaw should remain normalized after tail spins");
        assertEquals(yaw, mob.bodyYaw, 0.0001f, "Body yaw should match normalized yaw");
        assertEquals(yaw, mob.headYaw, 0.0001f, "Head yaw should match normalized yaw");
        assertEquals(yaw, (float) MathHelper.wrapDegrees(yaw), 0.0001f,
            "Yaw should already be wrapped into [-180, 180]");
    }
}
