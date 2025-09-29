package woflo.petsplus.state.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AsyncMigrationProgressTrackerTest {

    @BeforeEach
    void reset() {
        AsyncMigrationProgressTracker.reset();
    }

    @AfterEach
    void cleanup() {
        AsyncMigrationProgressTracker.reset();
    }

    @Test
    void phasesCanBeMarkedAndObserved() {
        assertFalse(AsyncMigrationProgressTracker.isComplete(AsyncMigrationProgressTracker.Phase.CORE_EMOTION));
        assertFalse(AsyncMigrationProgressTracker.allComplete());

        AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.CORE_EMOTION);
        AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.PET_STATE);

        assertTrue(AsyncMigrationProgressTracker.isComplete(AsyncMigrationProgressTracker.Phase.CORE_EMOTION));
        assertTrue(AsyncMigrationProgressTracker.isComplete(AsyncMigrationProgressTracker.Phase.PET_STATE));
        assertFalse(AsyncMigrationProgressTracker.isComplete(AsyncMigrationProgressTracker.Phase.OWNER_PROCESSING));

        AsyncMigrationProgressTracker.PhaseStatus status = AsyncMigrationProgressTracker.snapshot();
        assertTrue(status.isComplete(AsyncMigrationProgressTracker.Phase.CORE_EMOTION));
        assertTrue(status.completedPhases().contains(AsyncMigrationProgressTracker.Phase.PET_STATE));
        assertFalse(status.isComplete(AsyncMigrationProgressTracker.Phase.ADVANCED_SYSTEMS));
        assertFalse(status.allComplete());

        AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.OWNER_PROCESSING);
        AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.ADVANCED_SYSTEMS);

        assertTrue(AsyncMigrationProgressTracker.allComplete());
        assertTrue(AsyncMigrationProgressTracker.snapshot().allComplete());
    }

    @Test
    void resetClearsCompletionState() {
        AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.CORE_EMOTION);
        assertTrue(AsyncMigrationProgressTracker.isComplete(AsyncMigrationProgressTracker.Phase.CORE_EMOTION));

        AsyncMigrationProgressTracker.reset();
        assertFalse(AsyncMigrationProgressTracker.isComplete(AsyncMigrationProgressTracker.Phase.CORE_EMOTION));
        assertEquals(0, AsyncMigrationProgressTracker.snapshot().completedPhases().size());
    }

    @Test
    void progressSummaryReflectsRatio() {
        String initialSummary = AsyncMigrationProgressTracker.progressSummary();
        assertNotNull(initialSummary);
        assertTrue(initialSummary.contains("0/"));

        AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.CORE_EMOTION);
        AsyncMigrationProgressTracker.markComplete(AsyncMigrationProgressTracker.Phase.PET_STATE);

        assertEquals(2, AsyncMigrationProgressTracker.completedCount());
        assertEquals(0.5D, AsyncMigrationProgressTracker.completionRatio(), 1.0E-9);

        AsyncMigrationProgressTracker.PhaseStatus status = AsyncMigrationProgressTracker.snapshot();
        assertEquals(2, status.completedCount());
        assertEquals(0.5D, status.completionRatio(), 1.0E-9);
        assertEquals(50.0D, status.completionPercentage(), 1.0E-9);

        String updatedSummary = AsyncMigrationProgressTracker.progressSummary();
        assertTrue(updatedSummary.contains("2/"));
        assertTrue(updatedSummary.contains("CORE_EMOTION"));
    }
}
