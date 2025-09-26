package woflo.petsplus.stats.nature;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.PetCharacteristics;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Generates lightweight stat adjustments for each nature using the same seed
 * that powers characteristic modifiers. This keeps nature tuning deterministic
 * per pet while avoiding extra random state.
 */
public final class NatureModifierSampler {
    private static final Map<Identifier, NatureDefinition> DEFINITIONS = new java.util.HashMap<>();

    static {
        register("radiant", NatureStat.SPEED, 0.06f, NatureStat.VITALITY, 0.03f);
        register("nocturne", NatureStat.AGILITY, 0.05f, NatureStat.FOCUS, 0.02f);
        register("hearth", NatureStat.DEFENSE, 0.06f, NatureStat.LOYALTY, 0.03f);
        register("tempest", NatureStat.ATTACK, 0.06f, NatureStat.VITALITY, 0.03f);
        register("solace", NatureStat.VITALITY, 0.05f, NatureStat.DEFENSE, 0.02f);
        register("festival", NatureStat.LOYALTY, 0.05f, NatureStat.SPEED, 0.03f);
        register("otherworldly", NatureStat.VITALITY, 0.05f, NatureStat.AGILITY, 0.02f);
        register("infernal", NatureStat.HEALTH, 0.06f, NatureStat.ATTACK, 0.03f);
        register("echoed", NatureStat.DEFENSE, 0.06f, NatureStat.FOCUS, 0.03f);
        register("mycelial", NatureStat.HEALTH, 0.06f, NatureStat.VITALITY, 0.02f);
        register("gilded", NatureStat.FOCUS, 0.05f, NatureStat.AGILITY, 0.03f);
        register("gloom", NatureStat.AGILITY, 0.05f, NatureStat.DEFENSE, 0.02f);
        register("verdant", NatureStat.VITALITY, 0.05f, NatureStat.HEALTH, 0.03f);
        register("summit", NatureStat.SPEED, 0.06f, NatureStat.AGILITY, 0.03f);
        register("tidal", NatureStat.SWIM_SPEED, 0.06f, NatureStat.HEALTH, 0.03f);
        register("molten", NatureStat.ATTACK, 0.06f, NatureStat.DEFENSE, 0.02f);
        register("frosty", NatureStat.DEFENSE, 0.06f, NatureStat.SPEED, 0.03f);
        register("mire", NatureStat.HEALTH, 0.05f, NatureStat.VITALITY, 0.03f);
        register("relic", NatureStat.FOCUS, 0.05f, NatureStat.DEFENSE, 0.02f);
        register("unnatural", NatureStat.SPEED, 0.06f, NatureStat.AGILITY, 0.03f);
    }

    private NatureModifierSampler() {
    }

    private static void register(String path, NatureStat majorStat, float majorBase,
                                 NatureStat minorStat, float minorBase) {
        Identifier id = Identifier.of("petsplus", path);
        DEFINITIONS.put(id, new NatureDefinition(majorStat, majorBase, minorStat, minorBase));
    }

    public static NatureAdjustment sample(PetComponent component) {
        if (component == null) {
            return NatureAdjustment.NONE;
        }

        Identifier natureId = component.getNatureId();
        if (natureId == null) {
            return NatureAdjustment.NONE;
        }

        PetCharacteristics characteristics = component.getCharacteristics();
        long seed = resolveSeed(component, characteristics);
        return sample(natureId, seed);
    }

    public static NatureAdjustment sample(Identifier natureId, long seed) {
        NatureDefinition definition = DEFINITIONS.get(natureId);
        if (definition == null) {
            return NatureAdjustment.NONE;
        }

        Random random = new Random(seed ^ (long) natureId.hashCode());
        float majorValue = roll(random, definition.majorBase());
        float minorValue = roll(random, definition.minorBase());

        return new NatureAdjustment(definition.majorStat(), majorValue, definition.minorStat(), minorValue);
    }

    private static long resolveSeed(PetComponent component, @Nullable PetCharacteristics characteristics) {
        if (characteristics != null) {
            return characteristics.getCharacteristicSeed();
        }

        UUID uuid = component.getPet().getUuid();
        Long stored = component.getStateData(PetComponent.StateKeys.TAMED_TICK, Long.class);
        long tameTick = stored != null ? stored : component.getPet().getWorld().getTime();
        return uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits() ^ tameTick;
    }

    private static float roll(Random random, float base) {
        if (base == 0.0f) {
            return 0.0f;
        }
        float curve = (random.nextFloat() + random.nextFloat()) * 0.5f; // bell curve
        float scale = 0.85f + curve * 0.3f; // ±15%
        return base * scale;
    }

    public enum NatureStat {
        NONE,
        HEALTH,
        SPEED,
        ATTACK,
        DEFENSE,
        AGILITY,
        VITALITY,
        FOCUS,
        LOYALTY,
        SWIM_SPEED
    }

    private record NatureDefinition(NatureStat majorStat, float majorBase,
                                     NatureStat minorStat, float minorBase) {
    }

    public record NatureAdjustment(NatureStat majorStat, float majorValue,
                                    NatureStat minorStat, float minorValue) {
        public static final NatureAdjustment NONE = new NatureAdjustment(NatureStat.NONE, 0.0f, NatureStat.NONE, 0.0f);

        public boolean isEmpty() {
            return (majorValue == 0.0f || majorStat == NatureStat.NONE)
                && (minorValue == 0.0f || minorStat == NatureStat.NONE);
        }

        public float valueFor(NatureStat stat) {
            float total = 0.0f;
            if (majorStat == stat) {
                total += majorValue;
            }
            if (minorStat == stat) {
                total += minorValue;
            }
            return total;
        }
    }
}
