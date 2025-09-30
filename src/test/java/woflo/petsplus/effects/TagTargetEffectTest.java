package woflo.petsplus.effects;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import woflo.petsplus.api.EffectContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagTargetEffectTest {

    @BeforeEach
    void clearTags() {
        TagTargetEffect.cleanupExpiredTags(Long.MAX_VALUE);
    }

    @Test
    void executeReturnsFalseWhenTriggerContextMissing() {
        EffectContext context = mock(EffectContext.class);

        when(context.getData("target", Entity.class)).thenReturn(null);
        when(context.getTriggerContext()).thenReturn(null);
        when(context.getTarget()).thenReturn(null);

        TagTargetEffect effect = new TagTargetEffect("target", "petsplus:test", 40);

        boolean result = effect.execute(context);

        assertFalse(result, "Effect should not execute without a target or trigger context");
        verify(context).getData("target", Entity.class);
        verify(context).getTriggerContext();
        verify(context).getTarget();
        verify(context, never()).getWorld();
    }

    @Test
    void executeUsesStoredTargetWhenTriggerContextMissing() {
        EffectContext context = mock(EffectContext.class);
        Entity storedTarget = mock(Entity.class);
        ServerWorld world = mock(ServerWorld.class);

        when(context.getData("target", Entity.class)).thenReturn(null);
        when(context.getTriggerContext()).thenReturn(null);
        when(context.getTarget()).thenReturn(storedTarget);
        when(context.getWorld()).thenReturn(world);
        when(storedTarget.getWorld()).thenReturn(world);
        when(world.getTime()).thenReturn(100L);

        TagTargetEffect effect = new TagTargetEffect("target", "petsplus:test", 40);

        boolean result = effect.execute(context);

        assertTrue(result, "Effect should execute when a stored target is available");
        assertTrue(TagTargetEffect.hasTag(storedTarget, "petsplus:test"), "Stored target should be tagged");

        TagTargetEffect.removeTag(storedTarget, "petsplus:test");
        verify(context).getWorld();
    }

    @Test
    void executeFallsBackToTargetWorld() {
        EffectContext context = mock(EffectContext.class);
        Entity target = mock(Entity.class);
        ServerWorld world = mock(ServerWorld.class);

        when(context.getData("target", Entity.class)).thenReturn(target);
        when(context.getWorld()).thenReturn(null);
        when(target.getWorld()).thenReturn(world);
        when(world.getTime()).thenReturn(150L);

        TagTargetEffect effect = new TagTargetEffect("target", "petsplus:test", 20);

        boolean result = effect.execute(context);

        assertTrue(result, "Effect should use the target world when context world is unavailable");
        assertTrue(TagTargetEffect.hasTag(target, "petsplus:test"), "Target should receive tag using fallback world");

        TagTargetEffect.removeTag(target, "petsplus:test");
    }
}
