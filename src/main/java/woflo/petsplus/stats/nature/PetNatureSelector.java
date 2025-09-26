package woflo.petsplus.stats.nature;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.event.PetBreedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Evaluates lightweight birth-context rules to determine which nature, if any,
 * should be assigned to a newborn pet. This implementation keeps a small set of
 * built-in predicates so the system remains data-light while we prove out the
 * concept, but the selection logic is written to support any number of
 * candidates.
 */
public final class PetNatureSelector {
    private static final List<NatureRule> RULES = new ArrayList<>();
    private static final Identifier OVERWORLD_DIMENSION = Identifier.of("minecraft", "overworld");
    private static final Identifier NETHER_DIMENSION = Identifier.of("minecraft", "the_nether");

    private PetNatureSelector() {
    }

    static {
        register(
            Identifier.of("petsplus", "radiant"),
            context -> context != null && context.isDaytime() && !context.isIndoors()
        );

        register(
            Identifier.of("petsplus", "festival"),
            context -> context != null && (context.getNearbyPlayerCount() >= 4 || context.getNearbyPetCount() >= 6)
        );

        register(
            Identifier.of("petsplus", "infernal"),
            context -> context != null && Objects.equals(context.getDimensionId(), NETHER_DIMENSION)
        );

        register(
            Identifier.of("petsplus", "otherworldly"),
            context ->
                context != null
                    && !Objects.equals(context.getDimensionId(), OVERWORLD_DIMENSION)
                    && !Objects.equals(context.getDimensionId(), NETHER_DIMENSION)
        );
    }

    private static void register(Identifier id, Predicate<PetBreedEvent.BirthContext> predicate) {
        RULES.add(new NatureRule(id, predicate));
    }

    /**
     * Returns the identifier of the first matching nature. If several natures
     * qualify the selection is made uniformly at random using the child's RNG
     * so a tie between two candidates is effectively a coin flip. This scales
     * naturally to any number of overlapping rules.
     */
    public static Identifier selectNature(MobEntity child, PetBreedEvent.BirthContext context) {
        if (child == null || RULES.isEmpty()) {
            return null;
        }

        List<Identifier> matches = new ArrayList<>();
        for (NatureRule rule : RULES) {
            if (rule.predicate.test(context)) {
                matches.add(rule.id);
            }
        }

        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }

        // Explicit coin flip when exactly two natures qualify to keep the behavior obvious.
        if (matches.size() == 2) {
            return child.getRandom().nextBoolean() ? matches.get(0) : matches.get(1);
        }

        return matches.get(child.getRandom().nextInt(matches.size()));
    }

    private record NatureRule(Identifier id, Predicate<PetBreedEvent.BirthContext> predicate) {
    }
}
