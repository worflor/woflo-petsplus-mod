package woflo.petsplus.stats.nature;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
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
    private static final List<NatureRule<PetBreedEvent.BirthContext>> BIRTH_RULES = new ArrayList<>();
    private static final List<NatureRule<TameContext>> TAME_RULES = new ArrayList<>();
    private static final Identifier OVERWORLD_DIMENSION = Identifier.of("minecraft", "overworld");
    private static final Identifier NETHER_DIMENSION = Identifier.of("minecraft", "the_nether");

    private PetNatureSelector() {
    }

    static {
        registerBirthNature(
            Identifier.of("petsplus", "radiant"),
            context -> context != null && context.isDaytime() && !context.isIndoors()
        );

        registerBirthNature(
            Identifier.of("petsplus", "festival"),
            context -> context != null && (context.getNearbyPlayerCount() >= 4 || context.getNearbyPetCount() >= 6)
        );

        registerBirthNature(
            Identifier.of("petsplus", "infernal"),
            context -> context != null && Objects.equals(context.getDimensionId(), NETHER_DIMENSION)
        );

        registerBirthNature(
            Identifier.of("petsplus", "otherworldly"),
            context ->
                context != null
                    && !Objects.equals(context.getDimensionId(), OVERWORLD_DIMENSION)
                    && !Objects.equals(context.getDimensionId(), NETHER_DIMENSION)
        );

        registerTameNature(
            Identifier.of("petsplus", "frozen"),
            context -> context != null && context.temperatureBand() == TameContext.TemperatureBand.COLD
        );

        registerTameNature(
            Identifier.of("petsplus", "feral"),
            context -> context != null && context.temperatureBand() == TameContext.TemperatureBand.NEUTRAL
        );

        registerTameNature(
            Identifier.of("petsplus", "fierce"),
            context -> context != null && context.temperatureBand() == TameContext.TemperatureBand.HOT
        );
    }

    private static void registerBirthNature(Identifier id, Predicate<PetBreedEvent.BirthContext> predicate) {
        BIRTH_RULES.add(new NatureRule<>(id, predicate));
    }

    private static void registerTameNature(Identifier id, Predicate<TameContext> predicate) {
        TAME_RULES.add(new NatureRule<>(id, predicate));
    }

    /**
     * Returns the identifier of the first matching nature. If several natures
     * qualify the selection is made uniformly at random using the child's RNG
     * so a tie between two candidates is effectively a coin flip. This scales
     * naturally to any number of overlapping rules.
     */
    public static Identifier selectNature(MobEntity child, PetBreedEvent.BirthContext context) {
        return pickNature(child, context, BIRTH_RULES);
    }

    public static Identifier selectTameNature(MobEntity pet, TameContext context) {
        return pickNature(pet, context, TAME_RULES);
    }

    public static TameContext captureTameContext(ServerWorld world, MobEntity pet) {
        Identifier dimensionId = world.getRegistryKey().getValue();

        RegistryEntry<Biome> biomeEntry = world.getBiome(pet.getBlockPos());
        Identifier biomeId = biomeEntry.getKey()
            .map(RegistryKey::getValue)
            .orElse(Identifier.of("minecraft", "unknown"));

        float temperature = biomeEntry.value().getTemperature();
        TameContext.TemperatureBand band = TameContext.TemperatureBand.fromTemperature(temperature);

        return new TameContext(dimensionId, biomeId, temperature, band);
    }

    private static <T> Identifier pickNature(MobEntity entity, T context, List<NatureRule<T>> rules) {
        if (entity == null || rules.isEmpty()) {
            return null;
        }

        Identifier chosen = null;
        int matchCount = 0;

        for (NatureRule<T> rule : rules) {
            if (!rule.predicate.test(context)) {
                continue;
            }

            matchCount++;
            if (matchCount == 1) {
                chosen = rule.id;
                continue;
            }

            // Reservoir sampling: keep each candidate with equal probability without
            // allocating intermediate collections. This keeps the selection O(n) time
            // and O(1) space regardless of how many datapack rules are registered.
            if (entity.getRandom().nextInt(matchCount) == 0) {
                chosen = rule.id;
            }
        }

        return chosen;
    }

    public record TameContext(Identifier dimensionId, Identifier biomeId, float biomeTemperature,
                              TemperatureBand temperatureBand) {
        public enum TemperatureBand {
            COLD,
            NEUTRAL,
            HOT;

            public static TemperatureBand fromTemperature(float temperature) {
                if (temperature <= 0.15f) {
                    return COLD;
                }
                if (temperature >= 1.0f) {
                    return HOT;
                }
                return NEUTRAL;
            }
        }
    }

    private record NatureRule<T>(Identifier id, Predicate<T> predicate) {
    }
}
