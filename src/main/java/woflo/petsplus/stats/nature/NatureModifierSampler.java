package woflo.petsplus.stats.nature;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetComponent.Emotion;
import woflo.petsplus.stats.PetCharacteristics;
import woflo.petsplus.util.BehaviorSeedUtil;

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
        register("radiant", NatureStat.SPEED, 0.06f, NatureStat.VITALITY, 0.03f,
            0.75f, 0.85f, 1.10f, 1.05f,
            Emotion.CHEERFUL, 0.35f, Emotion.HOPEFUL, 0.25f, Emotion.GLEE, 0.20f);
        register("nocturne", NatureStat.AGILITY, 0.05f, NatureStat.FOCUS, 0.02f,
            1.25f, 1.10f, 0.90f, 0.95f,
            Emotion.YUGEN, 0.35f, Emotion.MELANCHOLY, 0.25f, Emotion.VIGILANT, 0.20f);
        register("hearth", NatureStat.DEFENSE, 0.06f, NatureStat.LOYALTY, 0.03f,
            0.85f, 0.80f, 1.15f, 1.10f,
            Emotion.CONTENT, 0.35f, Emotion.LOYALTY, 0.25f, Emotion.SOBREMESA, 0.20f);
        register("tempest", NatureStat.ATTACK, 0.06f, NatureStat.VITALITY, 0.03f,
            1.30f, 1.15f, 1.05f, 0.95f,
            Emotion.RESTLESS, 0.35f, Emotion.FOCUSED, 0.25f, Emotion.STARTLE, 0.20f);
        register("solace", NatureStat.VITALITY, 0.05f, NatureStat.DEFENSE, 0.02f,
            0.80f, 0.80f, 1.00f, 1.10f,
            Emotion.RELIEF, 0.35f, Emotion.GAMAN, 0.25f, Emotion.EMPATHY, 0.20f);
        register("festival", NatureStat.LOYALTY, 0.05f, NatureStat.SPEED, 0.03f,
            1.25f, 1.10f, 1.30f, 0.90f,
            Emotion.PLAYFULNESS, 0.35f, Emotion.GLEE, 0.25f, Emotion.CHEERFUL, 0.20f);
        register("otherworldly", NatureStat.VITALITY, 0.05f, NatureStat.AGILITY, 0.02f,
            1.10f, 0.95f, 1.05f, 1.00f,
            Emotion.CURIOUS, 0.35f, Emotion.YUGEN, 0.25f, Emotion.FERNWEH, 0.20f);
        register("infernal", NatureStat.HEALTH, 0.06f, NatureStat.ATTACK, 0.03f,
            1.30f, 1.05f, 0.95f, 1.20f,
            Emotion.PROTECTIVENESS, 0.35f, Emotion.FRUSTRATION, 0.25f, Emotion.ANGST, 0.20f);
        register("echoed", NatureStat.DEFENSE, 0.06f, NatureStat.FOCUS, 0.03f,
            0.90f, 0.90f, 0.85f, 1.05f,
            Emotion.NOSTALGIA, 0.35f, Emotion.MONO_NO_AWARE, 0.25f, Emotion.FERNWEH, 0.20f);
        register("mycelial", NatureStat.HEALTH, 0.06f, NatureStat.VITALITY, 0.02f,
            0.80f, 0.85f, 1.05f, 1.00f,
            Emotion.WABI_SABI, 0.35f, Emotion.CONTENT, 0.25f, Emotion.CURIOUS, 0.20f);
        register("gilded", NatureStat.FOCUS, 0.05f, NatureStat.AGILITY, 0.03f,
            1.15f, 1.05f, 1.10f, 0.95f,
            Emotion.PRIDE, 0.35f, Emotion.PLAYFULNESS, 0.25f, Emotion.CHEERFUL, 0.20f);
        register("gloom", NatureStat.AGILITY, 0.05f, NatureStat.DEFENSE, 0.02f,
            1.20f, 1.10f, 0.80f, 1.10f,
            Emotion.FOREBODING, 0.35f, Emotion.MELANCHOLY, 0.25f, Emotion.ANGST, 0.20f);
        register("verdant", NatureStat.VITALITY, 0.05f, NatureStat.HEALTH, 0.03f,
            0.85f, 0.90f, 1.10f, 1.00f,
            Emotion.HOPEFUL, 0.35f, Emotion.LAGOM, 0.25f, Emotion.RELIEF, 0.20f);
        register("summit", NatureStat.SPEED, 0.06f, NatureStat.AGILITY, 0.03f,
            1.05f, 0.95f, 0.95f, 1.10f,
            Emotion.FOCUSED, 0.35f, Emotion.VIGILANT, 0.25f, Emotion.PRIDE, 0.20f);
        register("tidal", NatureStat.SWIM_SPEED, 0.06f, NatureStat.HEALTH, 0.03f,
            0.80f, 0.90f, 1.15f, 0.95f,
            Emotion.RELIEF, 0.35f, Emotion.HANYAUKU, 0.25f, Emotion.PLAYFULNESS, 0.20f);
        register("molten", NatureStat.ATTACK, 0.06f, NatureStat.DEFENSE, 0.02f,
            1.25f, 1.05f, 0.90f, 1.20f,
            Emotion.PROTECTIVENESS, 0.35f, Emotion.FRUSTRATION, 0.25f, Emotion.PRIDE, 0.20f);
        register("frosty", NatureStat.DEFENSE, 0.06f, NatureStat.SPEED, 0.03f,
            0.75f, 0.85f, 0.85f, 1.05f,
            Emotion.STOIC, 0.35f, Emotion.FOCUSED, 0.25f, Emotion.PLAYFULNESS, 0.20f);
        register("mire", NatureStat.HEALTH, 0.05f, NatureStat.VITALITY, 0.03f,
            1.10f, 0.95f, 1.00f, 0.95f,
            Emotion.HIRAETH, 0.35f, Emotion.SAUDADE, 0.25f, Emotion.EMPATHY, 0.20f);
        register("relic", NatureStat.FOCUS, 0.05f, NatureStat.DEFENSE, 0.02f,
            0.90f, 0.90f, 0.90f, 1.15f,
            Emotion.NOSTALGIA, 0.35f, Emotion.PRIDE, 0.25f, Emotion.CURIOUS, 0.20f);
        register("unnatural", NatureStat.SPEED, 0.06f, NatureStat.AGILITY, 0.03f,
            1.35f, 1.20f, 1.20f, 0.80f,
            Emotion.RESTLESS, 0.35f, Emotion.ANGST, 0.25f, Emotion.STARTLE, 0.20f);
    }

    private NatureModifierSampler() {
    }

    private static void register(String path, NatureStat majorStat, float majorBase,
                                 NatureStat minorStat, float minorBase,
                                 float volatilityMultiplier, float resilienceMultiplier,
                                 float contagionModifier, float guardModifier,
                                 Emotion majorEmotion, float majorEmotionBase,
                                 Emotion minorEmotion, float minorEmotionBase,
                                 Emotion quirkEmotion, float quirkEmotionBase) {
        Identifier id = Identifier.of("petsplus", path);
        DEFINITIONS.put(id, new NatureDefinition(majorStat, majorBase, minorStat, minorBase,
            volatilityMultiplier, resilienceMultiplier, contagionModifier, guardModifier,
            majorEmotion, majorEmotionBase, minorEmotion, minorEmotionBase,
            quirkEmotion, quirkEmotionBase));
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

        long baseSeed = seed ^ (long) natureId.hashCode();
        long enhancedSeed = BehaviorSeedUtil.mixBehaviorSeed(baseSeed, baseSeed);
        Random random = new Random(enhancedSeed);
        float majorModifier = rollStatModifier(random, definition.majorBase());
        float minorModifier = rollStatModifier(random, definition.minorBase());
        NatureRoll majorRoll = new NatureRoll(definition.majorStat(), definition.majorBase(), majorModifier);
        NatureRoll minorRoll = new NatureRoll(definition.minorStat(), definition.minorBase(), minorModifier);
        float volatility = rollMultiplier(random, definition.volatilityMultiplier(), 0.3f, 1.75f);
        float resilience = rollMultiplier(random, definition.resilienceMultiplier(), 0.5f, 1.5f);
        float contagion = rollMultiplier(random, definition.contagionModifier(), 0.5f, 1.5f);
        float guard = rollMultiplier(random, definition.guardModifier(), 0.5f, 1.5f);

        PetComponent.NatureEmotionProfile emotionProfile = new PetComponent.NatureEmotionProfile(
            definition.majorEmotion(), rollEmotion(random, definition.majorEmotionBase()),
            definition.minorEmotion(), rollEmotion(random, definition.minorEmotionBase()),
            definition.quirkEmotion(), rollEmotion(random, definition.quirkEmotionBase()));

        return new NatureAdjustment(majorRoll, minorRoll,
            volatility, resilience,
            contagion, guard,
            emotionProfile);
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

    private static float rollStatModifier(Random random, float base) {
        if (base == 0.0f) {
            return 1.0f;
        }
        float curve = (random.nextFloat() + random.nextFloat()) * 0.5f; // bell curve
        return 0.85f + curve * 0.3f; // ±15%
    }

    private static float rollMultiplier(Random random, float base, float min, float max) {
        if (base == 0.0f) {
            return 0.0f;
        }
        float curve = (random.nextFloat() + random.nextFloat()) * 0.5f;
        float scale = 0.9f + curve * 0.2f; // ±10%
        return MathHelper.clamp(base * scale, min, max);
    }

    private static float rollEmotion(Random random, float base) {
        if (base <= 0.0f) {
            return 0.0f;
        }
        float curve = (random.nextFloat() + random.nextFloat()) * 0.5f;
        float scale = 0.85f + curve * 0.3f; // ±15%
        return MathHelper.clamp(base * scale, 0.05f, 1.0f);
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
                                     NatureStat minorStat, float minorBase,
                                     float volatilityMultiplier, float resilienceMultiplier,
                                     float contagionModifier, float guardModifier,
                                     Emotion majorEmotion, float majorEmotionBase,
                                     Emotion minorEmotion, float minorEmotionBase,
                                     Emotion quirkEmotion, float quirkEmotionBase) {
    }

    public record NatureAdjustment(NatureRoll majorRoll, NatureRoll minorRoll,
                                    float volatilityMultiplier, float resilienceMultiplier,
                                    float contagionModifier, float guardModifier,
                                    PetComponent.NatureEmotionProfile emotionProfile) {
        public static final NatureAdjustment NONE = new NatureAdjustment(NatureRoll.EMPTY,
            NatureRoll.EMPTY, 1.0f, 1.0f, 1.0f, 1.0f,
            PetComponent.NatureEmotionProfile.EMPTY);

        public boolean isEmpty() {
            return majorRoll.isEmpty() && minorRoll.isEmpty();
        }

        public NatureStat majorStat() {
            return majorRoll.stat();
        }

        public NatureStat minorStat() {
            return minorRoll.stat();
        }

        public float majorBase() {
            return majorRoll.baseValue();
        }

        public float minorBase() {
            return minorRoll.baseValue();
        }

        public float majorModifier() {
            return majorRoll.modifier();
        }

        public float minorModifier() {
            return minorRoll.modifier();
        }

        public float majorValue() {
            return majorRoll.value();
        }

        public float minorValue() {
            return minorRoll.value();
        }

        public float valueFor(NatureStat stat) {
            return majorRoll.contributionFor(stat) + minorRoll.contributionFor(stat);
        }
    }

    public record NatureRoll(NatureStat stat, float baseValue, float modifier) {
        public static final NatureRoll EMPTY = new NatureRoll(NatureStat.NONE, 0.0f, 1.0f);

        public boolean isEmpty() {
            return stat == NatureStat.NONE || baseValue == 0.0f;
        }

        public float value() {
            if (stat == NatureStat.NONE || baseValue == 0.0f) {
                return 0.0f;
            }
            return baseValue * modifier;
        }

        public float contributionFor(NatureStat target) {
            return stat == target ? value() : 0.0f;
        }
    }
}
