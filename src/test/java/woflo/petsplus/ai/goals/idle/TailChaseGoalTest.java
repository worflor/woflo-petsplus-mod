package woflo.petsplus.ai.goals.idle;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.entity.mob.MobEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TailChaseGoalTest {

    @Test
    void tailChaseYawStaysNormalizedAcrossSpins() throws Exception {
        World world = new World(new MinecraftServer());
        MobEntity mob = new MobEntity(world);
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
