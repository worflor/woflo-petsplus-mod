package woflo.petsplus.effects;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.state.OwnerCombatState;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnerNextAttackBonusEffectTest {

    @Test
    void executeReturnsFalseWhenOwnerMissing() {
        EffectContext context = mock(EffectContext.class);
        when(context.getOwner()).thenReturn(null);

        OwnerNextAttackBonusEffect effect = new OwnerNextAttackBonusEffect(0.25);

        boolean result = effect.execute(context);

        assertFalse(result, "Effect should not execute when owner is missing");
        verify(context).getOwner();
        verify(context, never()).getWorld();
    }

    @Test
    void executeFallsBackToOwnerWorldWhenContextMissing() {
        EffectContext context = mock(EffectContext.class);
        PlayerEntity owner = mock(PlayerEntity.class);
        ServerWorld ownerWorld = mock(ServerWorld.class);
        OwnerCombatState combatState = mock(OwnerCombatState.class);

        when(context.getOwner()).thenReturn(owner);
        when(context.getWorld()).thenReturn(null);
        when(owner.isAlive()).thenReturn(true);
        when(owner.isRemoved()).thenReturn(false);
        when(owner.getWorld()).thenReturn(ownerWorld);
        when(ownerWorld.getTime()).thenReturn(80L);

        try (MockedStatic<OwnerCombatState> ownerStateStatic = mockStatic(OwnerCombatState.class)) {
            ownerStateStatic.when(() -> OwnerCombatState.getOrCreate(owner)).thenReturn(combatState);

            OwnerNextAttackBonusEffect effect = new OwnerNextAttackBonusEffect(0.25);
            boolean result = effect.execute(context);

            assertTrue(result, "Effect should fallback to the owner's world when context world missing");
            verify(owner).getWorld();
            verify(combatState).addNextAttackRider(eq("damage_bonus"), any());
        }
    }
}
