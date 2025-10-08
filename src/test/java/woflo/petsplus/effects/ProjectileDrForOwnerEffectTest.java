package woflo.petsplus.effects;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import woflo.petsplus.api.EffectContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectileDrForOwnerEffectTest {

    @BeforeEach
    void resetActiveDr() {
        ProjectileDrForOwnerEffect.cleanupExpired(Long.MAX_VALUE);
    }

    @Test
    void executeReturnsFalseWhenOwnerMissing() {
        EffectContext context = mock(EffectContext.class);
        when(context.getOwner()).thenReturn(null);

        ProjectileDrForOwnerEffect effect = new ProjectileDrForOwnerEffect(0.25, 40);

        boolean result = effect.execute(context);

        assertFalse(result, "Effect should not execute when owner is missing");
        verify(context).getOwner();
        verify(context, never()).getWorld();
    }

    @Test
    void getProjectileDrClearsRemovedOwner() {
        ProjectileDrForOwnerEffect effect = new ProjectileDrForOwnerEffect(0.5, 60);
        EffectContext context = mock(EffectContext.class);
        PlayerEntity owner = mock(PlayerEntity.class);
        ServerWorld world = mock(ServerWorld.class);

        when(context.getOwner()).thenReturn(owner);
        when(context.getWorld()).thenReturn(world);
        when(owner.isRemoved()).thenReturn(false);
        when(owner.isAlive()).thenReturn(true);
        when(world.getTime()).thenReturn(100L);

        boolean applied = effect.execute(context);
        assertTrue(applied, "Effect should apply when owner is valid");

        when(owner.isRemoved()).thenReturn(true);

        double damageReduction = ProjectileDrForOwnerEffect.getProjectileDr(owner);

        assertEquals(0.0, damageReduction, 0.0001, "Damage reduction should be cleared for removed owners");

        double subsequent = ProjectileDrForOwnerEffect.getProjectileDr(owner);
        assertEquals(0.0, subsequent, 0.0001, "Removed owners should stay absent from the cache");

        verify(world).getTime();
    }

    @Test
    void executeFallsBackToOwnerWorldWhenContextMissing() {
        ProjectileDrForOwnerEffect effect = new ProjectileDrForOwnerEffect(0.5, 60);
        EffectContext context = mock(EffectContext.class);
        PlayerEntity owner = mock(PlayerEntity.class);
        ServerWorld world = mock(ServerWorld.class);

        when(context.getOwner()).thenReturn(owner);
        when(context.getWorld()).thenReturn(null);
        when(owner.isRemoved()).thenReturn(false);
        when(owner.isAlive()).thenReturn(true);
        when(owner.getEntityWorld()).thenReturn(world);
        when(world.getTime()).thenReturn(200L);

        boolean applied = effect.execute(context);

        assertTrue(applied, "Effect should resolve owner world when context world missing");
        verify(owner).getEntityWorld();
        verify(world).getTime();
    }
}


