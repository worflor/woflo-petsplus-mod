package woflo.petsplus.advancement;

import net.minecraft.advancement.criterion.Criterion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import woflo.petsplus.Petsplus;
import woflo.petsplus.advancement.criteria.*;

/**
 * Registry for custom advancement criteria triggers.
 * Replaces inefficient tick-based advancement system with event-driven predicates.
 */
public class AdvancementCriteriaRegistry {

    // Criterion instances - registered in static block like vanilla
    public static final PetLevelCriterion PET_LEVEL;
    public static final PetMoodLevelCriterion PET_MOOD_LEVEL;
    public static final PetMoodTransitionCriterion PET_MOOD_TRANSITION;
    public static final PetStatThresholdCriterion PET_STAT_THRESHOLD;
    public static final PetDeathCriterion PET_DEATH;
    public static final PetInteractionCriterion PET_INTERACTION;
    public static final PetMoodVarietyCriterion PET_MOOD_VARIETY;
    public static final PetRoleLevelCriterion PET_ROLE_LEVEL;
    public static final PetInteractionComboCriterion PET_INTERACTION_COMBO;
    public static final PetTradingCriterion PET_TRADING;

    static {
        // Register criteria immediately in static initializer, just like vanilla Criteria class
        PET_LEVEL = registerCriterion("pet_level", new PetLevelCriterion());
        PET_MOOD_LEVEL = registerCriterion("pet_mood_level", new PetMoodLevelCriterion());
        PET_MOOD_TRANSITION = registerCriterion("pet_mood_transition", new PetMoodTransitionCriterion());
        PET_STAT_THRESHOLD = registerCriterion("pet_stat_threshold", new PetStatThresholdCriterion());
        PET_DEATH = registerCriterion("pet_death", new PetDeathCriterion());
        PET_INTERACTION = registerCriterion("pet_interaction", new PetInteractionCriterion());
        PET_MOOD_VARIETY = registerCriterion("pet_mood_variety", new PetMoodVarietyCriterion());
        PET_ROLE_LEVEL = registerCriterion("pet_role_level", new PetRoleLevelCriterion());
        PET_INTERACTION_COMBO = registerCriterion("pet_interaction_combo", new PetInteractionComboCriterion());
        PET_TRADING = registerCriterion("pet_trading", new PetTradingCriterion());
    }

    /**
     * Called during mod initialization to log registration.
     * Actual registration happens in static initializer above.
     */
    public static void register() {
        Petsplus.LOGGER.info("Registering custom advancement criteria...");
        // Trigger static initializer by accessing a field
        Petsplus.LOGGER.info("Successfully registered {} custom advancement criteria", 10);
    }

    private static <T extends Criterion<?>> T registerCriterion(String id, T criterion) {
        return Registry.register(Registries.CRITERION, net.minecraft.util.Identifier.of(Petsplus.MOD_ID, id), criterion);
    }
}
