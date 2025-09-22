package woflo.petsplus.roles.striker;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StrikerExecutionFallbackTest {

    @AfterEach
    void clearCache() {
        StrikerExecution.clearCachedExecutionResult();
    }

    @Test
    void applyOwnerExecuteBonusUsesCachedResultWhenPresent() {
        float baseDamage = 6.0f;
        StrikerExecution.ExecutionResult cached = new StrikerExecution.ExecutionResult(
                4.0f,
                true,
                0.35f,
                5,
                2,
                0.5f
        );

        try (MockedStatic<StrikerExecution> mocked = Mockito.mockStatic(StrikerExecution.class)) {
            mocked.when(() -> StrikerExecution.consumeCachedExecutionResult((PlayerEntity) null, (LivingEntity) null))
                    .thenReturn(cached);

            float modified = StrikerExecutionFallback.applyOwnerExecuteBonus(null, null, baseDamage);

            assertEquals(cached.totalDamage(baseDamage), modified);

            mocked.verify(() -> StrikerExecution.consumeCachedExecutionResult((PlayerEntity) null, (LivingEntity) null));
            mocked.verifyNoMoreInteractions();
        }
    }

    @Test
    void applyOwnerExecuteBonusEvaluatesWithoutMomentumWhenNoCache() {
        float baseDamage = 3.0f;
        StrikerExecution.ExecutionResult evaluated = new StrikerExecution.ExecutionResult(
                1.5f,
                true,
                0.30f,
                3,
                1,
                0.75f
        );

        try (MockedStatic<StrikerExecution> mocked = Mockito.mockStatic(StrikerExecution.class)) {
            mocked.when(() -> StrikerExecution.consumeCachedExecutionResult((PlayerEntity) null, (LivingEntity) null))
                    .thenReturn(null);
            mocked.when(() -> StrikerExecution.evaluateExecution(null, null, baseDamage, false)).thenReturn(evaluated);

            float modified = StrikerExecutionFallback.applyOwnerExecuteBonus(null, null, baseDamage);

            assertEquals(evaluated.totalDamage(baseDamage), modified);

            mocked.verify(() -> StrikerExecution.consumeCachedExecutionResult((PlayerEntity) null, (LivingEntity) null));
            mocked.verify(() -> StrikerExecution.evaluateExecution(null, null, baseDamage, false));
            mocked.verifyNoMoreInteractions();
        }
    }

    @Test
    void cacheAndConsumeExecutionResultByUuid() {
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        StrikerExecution.ExecutionResult cached = new StrikerExecution.ExecutionResult(
                2.0f,
                true,
                0.25f,
                4,
                1,
                0.25f
        );

        StrikerExecution.cacheExecutionResult(ownerId, targetId, cached);

        assertEquals(cached, StrikerExecution.consumeCachedExecutionResult(ownerId, targetId));
        assertNull(StrikerExecution.consumeCachedExecutionResult(ownerId, targetId));
    }
}
