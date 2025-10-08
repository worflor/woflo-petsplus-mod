package woflo.petsplus.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EmotionsEventHandlerTest {

    @BeforeEach
    void resetSharedBaselines() {
        EmotionsEventHandler.resetSharedMoodBaselinesForTest();
    }

    @Test
    void archaeologyItemsAffectInventorySignature() {
        String withoutArchaeology = EmotionsEventHandler.buildInventorySignature(
            false, false, false, false, false, false
        );
        String withArchaeology = EmotionsEventHandler.buildInventorySignature(
            false, false, false, false, false, true
        );

        assertEquals(withoutArchaeology.length(), withArchaeology.length(), "Signature length should be stable");
        assertEquals(6, withArchaeology.length(), "Signature should encode all six toggles");
        assertEquals(withoutArchaeology.substring(0, 5), withArchaeology.substring(0, 5),
            "Only the archaeology bit should change in this scenario");
        assertEquals('0', withoutArchaeology.charAt(5), "Archaeology should be off without matching items");
        assertEquals('1', withArchaeology.charAt(5), "Archaeology items should flip the final bit");
        assertNotEquals(withoutArchaeology, withArchaeology,
            "Changing archaeology inventory should alter the signature");
    }
}

