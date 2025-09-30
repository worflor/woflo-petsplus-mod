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

    // Criterion instances
    public static final PetLevelCriterion PET_LEVEL = new PetLevelCriterion();
    public static final PetMoodLevelCriterion PET_MOOD_LEVEL = new PetMoodLevelCriterion();
    public static final PetMoodTransitionCriterion PET_MOOD_TRANSITION = new PetMoodTransitionCriterion();
    public static final PetStatThresholdCriterion PET_STAT_THRESHOLD = new PetStatThresholdCriterion();
    public static final PetDeathCriterion PET_DEATH = new PetDeathCriterion();
    public static final PetInteractionCriterion PET_INTERACTION = new PetInteractionCriterion();

    /**
     * Register all custom advancement criteria.
     * Called during mod initialization.
     */
    public static void register() {
        Petsplus.LOGGER.info("Registering custom advancement criteria...");

        registerCriterion(PetLevelCriterion.ID, PET_LEVEL);
        registerCriterion(PetMoodLevelCriterion.ID, PET_MOOD_LEVEL);
        registerCriterion(PetMoodTransitionCriterion.ID, PET_MOOD_TRANSITION);
        registerCriterion(PetStatThresholdCriterion.ID, PET_STAT_THRESHOLD);
        registerCriterion(PetDeathCriterion.ID, PET_DEATH);
        registerCriterion(PetInteractionCriterion.ID, PET_INTERACTION);

        Petsplus.LOGGER.info("Successfully registered {} custom advancement criteria", 6);
    }

    private static <T extends Criterion<?>> void registerCriterion(net.minecraft.util.Identifier id, T criterion) {
        Registry.register(Registries.CRITERION, id, criterion);
        Petsplus.LOGGER.debug("Registered criterion: {}", id);
    }
}