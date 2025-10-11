package woflo.petsplus.ai.context.perception;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import woflo.petsplus.ai.context.PetContext;
import woflo.petsplus.state.PetComponent;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetContextPerceptionIntegrationTest {

    @Test
    void capturePrefersPerceptionCacheData() {
        MobEntity mob = Mockito.mock(MobEntity.class);
        World world = Mockito.mock(World.class);
        Mockito.when(mob.getEntityWorld()).thenReturn(world);
        Mockito.when(world.getOtherEntities(Mockito.eq(mob), Mockito.any(Box.class), Mockito.any()))
            .thenThrow(new AssertionError("capture should reuse cached crowd data"));
        Mockito.when(world.getTime()).thenReturn(1200L);
        Mockito.when(world.isDay()).thenReturn(true);
        Mockito.when(mob.getBoundingBox()).thenReturn(new Box(BlockPos.ORIGIN));

        PlayerEntity owner = Mockito.mock(PlayerEntity.class);
        Mockito.when(owner.isRemoved()).thenReturn(false);
        Mockito.when(mob.squaredDistanceTo(owner)).thenReturn(9.0);
        Mockito.when(mob.distanceTo(owner)).thenReturn(3.0f);

        MobEntity neighbor = Mockito.mock(MobEntity.class);
        Mockito.when(neighbor.isRemoved()).thenReturn(false);
        Mockito.when(mob.squaredDistanceTo(neighbor)).thenReturn(16.0);

        PetComponent component = new PetComponent(mob);

        component.getPerceptionBus().publish(new PerceptionStimulus(
            PerceptionStimulusType.OWNER_NEARBY,
            600L,
            EnumSet.of(ContextSlice.OWNER),
            owner
        ));

        component.getPerceptionBus().publish(new PerceptionStimulus(
            PerceptionStimulusType.CROWD_SUMMARY,
            600L,
            EnumSet.of(ContextSlice.CROWD),
            List.of(neighbor)
        ));

        PetContext captured = PetContext.captureFresh(mob, component);
        assertSame(owner, captured.owner());
        assertTrue(captured.ownerNearby());
        assertEquals(3.0f, captured.distanceToOwner(), 0.001f);
        assertEquals(List.of(neighbor), captured.nearbyEntities());
    }
}
