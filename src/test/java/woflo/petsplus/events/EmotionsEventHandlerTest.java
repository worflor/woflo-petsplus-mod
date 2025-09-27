package woflo.petsplus.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EmotionsEventHandlerTest {

    @Test
    void archaeologyItemsAffectInventorySignature() {
        String withoutArchaeology = EmotionsEventHandler.buildInventorySignature(false, false, false, false, false, false);
        String withArchaeology = EmotionsEventHandler.buildInventorySignature(false, false, false, false, false, true);

        assertEquals(withoutArchaeology.length(), withArchaeology.length(), "signature length should be stable");
        assertEquals(6, withArchaeology.length(), "signature should encode all six toggles");
        assertEquals(withoutArchaeology.substring(0, 5), withArchaeology.substring(0, 5),
            "only the archaeology bit should change in this scenario");
        assertEquals('0', withoutArchaeology.charAt(5), "archaeology should be off without matching items");
        assertEquals('1', withArchaeology.charAt(5), "archaeology items should flip the final bit");
        assertNotEquals(withoutArchaeology, withArchaeology,
            "changing archaeology inventory should alter the signature");
    }
}

