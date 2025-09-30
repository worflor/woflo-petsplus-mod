package woflo.petsplus.advancement.criteria;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetLevelCriterionTest {

    @Test
    void roleSpecificCriterionDoesNotMatchWhenRoleMissing() {
        PetLevelCriterion.Conditions conditions = new PetLevelCriterion.Conditions(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("support")
        );

        assertFalse(conditions.matches(5, null));
    }

    @Test
    void roleSpecificCriterionMatchesWhenRoleMatches() {
        PetLevelCriterion.Conditions conditions = new PetLevelCriterion.Conditions(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("support")
        );

        assertTrue(conditions.matches(5, "support"));
    }
}
