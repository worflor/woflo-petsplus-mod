package woflo.petsplus.stats.nature;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetComponent.Emotion;
import woflo.petsplus.stats.PetImprint;
import woflo.petsplus.stats.StatModifierProvider;
import woflo.petsplus.stats.nature.astrology.AstrologyRegistry;
import woflo.petsplus.util.BehaviorSeedUtil;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Generates nature-based stat multipliers for pets using deterministic seeding.
 * 
 * <p>Nature modifiers provide small themed bonuses (1.03x to 1.08x on primary stat)
 * that define a pet's archetype (e.g., Radiant = healthy, Tempest = aggressive).
 * Each nature has a major and minor stat it favors, with ±8% variance to ensure
 * not all Radiant pets are identical.
 * 
 * <p>Modifiers are multiplicative and compound with Imprint, Role, and Level bonuses.
 * 
 * @see StatModifierProvider
 * @see woflo.petsplus.stats.PetAttributeManager
 */
public final class NatureModifierSampler {
    private static final Map<Identifier, NatureDefinition> DEFINITIONS = new java.util.HashMap<>();

    static {
        register("frisky", NatureStat.SPEED, 0.06f, NatureStat.AGILITY, 0.02f,
            1.15f, 0.90f, 0.95f, 1.00f,
            Emotion.PLAYFULNESS, 0.35f, Emotion.VIGILANT, 0.25f, Emotion.STARTLE, 0.20f);
        
        register("feral", NatureStat.ATTACK, 0.05f, NatureStat.DEFENSE, 0.03f,
            1.10f, 1.00f, 0.90f, 1.10f,
            Emotion.FOCUSED, 0.35f, Emotion.VIGILANT, 0.25f, Emotion.GUARDIAN_VIGIL, 0.20f);
        
        register("fierce", NatureStat.ATTACK, 0.06f, NatureStat.VITALITY, 0.03f,
            1.25f, 1.10f, 0.95f, 1.05f,
            Emotion.GUARDIAN_VIGIL, 0.35f, Emotion.FRUSTRATION, 0.25f, Emotion.PRIDE, 0.20f);
        
        register("radiant", NatureStat.SPEED, 0.06f, NatureStat.VITALITY, 0.03f,
            1.20f, 0.80f, 1.25f, 1.05f,
            Emotion.CHEERFUL, 0.38f, Emotion.HOPEFUL, 0.28f, Emotion.CONTENT, 0.24f);
        
        register("festival", NatureStat.LOYALTY, 0.05f, NatureStat.SPEED, 0.03f,
            1.30f, 0.75f, 1.50f, 0.90f,
            Emotion.CHEERFUL, 0.40f, Emotion.PACK_SPIRIT, 0.35f, Emotion.PLAYFULNESS, 0.28f);
        
        register("infernal", NatureStat.ATTACK, 0.07f, NatureStat.SPEED, 0.03f,
            1.45f, 0.90f, 1.10f, 1.25f,
            Emotion.FRUSTRATION, 0.40f, Emotion.PROTECTIVE, 0.32f, Emotion.KEFI, 0.26f);
        
        register("otherworldly", NatureStat.VITALITY, 0.05f, NatureStat.AGILITY, 0.02f,
            1.15f, 0.95f, 0.60f, 1.00f,
            Emotion.YUGEN, 0.40f, Emotion.ENNUI, 0.30f, Emotion.ARCANE_OVERFLOW, 0.26f);
        
        register("hearth", NatureStat.DEFENSE, 0.06f, NatureStat.LOYALTY, 0.03f,
            0.85f, 0.80f, 1.20f, 1.10f,
            Emotion.CONTENT, 0.36f, Emotion.PACK_SPIRIT, 0.28f, Emotion.SOBREMESA, 0.22f);
        
        register("tempest", NatureStat.ATTACK, 0.06f, NatureStat.VITALITY, 0.03f,
            1.35f, 1.15f, 1.05f, 0.95f,
            Emotion.RESTLESS, 0.38f, Emotion.KEFI, 0.28f, Emotion.STARTLE, 0.22f);
        
        register("solace", NatureStat.VITALITY, 0.05f, NatureStat.DEFENSE, 0.02f,
            0.75f, 1.35f, 0.60f, 1.10f,
            Emotion.HIRAETH, 0.40f, Emotion.STOIC, 0.28f, Emotion.PACK_SPIRIT, 0.24f);
        
        register("echoed", NatureStat.DEFENSE, 0.06f, NatureStat.FOCUS, 0.03f,
            0.85f, 0.95f, 0.80f, 1.05f,
            Emotion.ECHOED_RESONANCE, 0.48f, Emotion.NOSTALGIA, 0.28f, Emotion.MONO_NO_AWARE, 0.22f);
        
        register("mycelial", NatureStat.HEALTH, 0.06f, NatureStat.VITALITY, 0.02f,
            0.70f, 1.20f, 0.95f, 1.00f,
            Emotion.MONO_NO_AWARE, 0.40f, Emotion.WABI_SABI, 0.32f, Emotion.YUGEN, 0.26f);
        
        register("gilded", NatureStat.FOCUS, 0.05f, NatureStat.AGILITY, 0.03f,
            1.15f, 1.05f, 0.70f, 1.40f,
            Emotion.PRIDE, 0.42f, Emotion.QUERECIA, 0.30f, Emotion.ARCANE_OVERFLOW, 0.26f);
        
        register("gloom", NatureStat.AGILITY, 0.05f, NatureStat.DEFENSE, 0.02f,
            1.40f, 0.70f, 0.80f, 1.10f,
            Emotion.MELANCHOLY, 0.38f, Emotion.SAUDADE, 0.32f, Emotion.ANGST, 0.26f);
        
        register("verdant", NatureStat.VITALITY, 0.05f, NatureStat.HEALTH, 0.03f,
            0.65f, 1.35f, 0.90f, 1.00f,
            Emotion.LAGOM, 0.40f, Emotion.SOBREMESA, 0.30f, Emotion.RELIEF, 0.24f);
        
        register("summit", NatureStat.SPEED, 0.06f, NatureStat.AGILITY, 0.03f,
            1.30f, 1.05f, 0.70f, 1.15f,
            Emotion.PRIDE, 0.42f, Emotion.FOCUSED, 0.30f, Emotion.RESTLESS, 0.24f);
        
        register("tidal", NatureStat.SWIM_SPEED, 0.06f, NatureStat.HEALTH, 0.03f,
            0.60f, 1.40f, 0.85f, 0.95f,
            Emotion.LAGOM, 0.40f, Emotion.RELIEF, 0.30f, Emotion.ENNUI, 0.24f);
        
        register("molten", NatureStat.ATTACK, 0.06f, NatureStat.DEFENSE, 0.02f,
            1.40f, 1.10f, 0.95f, 1.25f,
            Emotion.FRUSTRATION, 0.42f, Emotion.KEFI, 0.30f, Emotion.PROTECTIVE, 0.26f);
        
        register("frosty", NatureStat.DEFENSE, 0.06f, NatureStat.VITALITY, 0.03f,
            0.55f, 1.50f, 0.70f, 1.05f,
            Emotion.STOIC, 0.42f, Emotion.ENNUI, 0.30f, Emotion.FOCUSED, 0.22f);
        
        register("mire", NatureStat.HEALTH, 0.05f, NatureStat.VITALITY, 0.03f,
            1.05f, 1.00f, 0.95f, 0.95f,
            Emotion.HIRAETH, 0.36f, Emotion.SAUDADE, 0.28f, Emotion.LOYALTY, 0.22f);
        
        register("relic", NatureStat.FOCUS, 0.05f, NatureStat.DEFENSE, 0.02f,
            0.85f, 0.95f, 0.85f, 1.20f,
            Emotion.NOSTALGIA, 0.38f, Emotion.YUGEN, 0.28f, Emotion.ECHOED_RESONANCE, 0.24f);
        
        register("ceramic", NatureStat.FOCUS, 0.05f, NatureStat.VITALITY, 0.03f,
            0.80f, 1.10f, 0.90f, 1.05f,
            Emotion.WABI_SABI, 0.38f, Emotion.GAMAN, 0.28f, Emotion.CONTENT, 0.24f);
        
        register("clockwork", NatureStat.AGILITY, 0.06f, NatureStat.FOCUS, 0.03f,
            1.10f, 1.00f, 0.85f, 1.10f,
            Emotion.FOCUSED, 0.40f, Emotion.CURIOUS, 0.28f, Emotion.RESTLESS, 0.22f);

        register("unnatural", NatureStat.SPEED, 0.06f, NatureStat.AGILITY, 0.03f,
            1.55f, 0.70f, 1.30f, 0.75f,
            Emotion.ECHOED_RESONANCE, 0.42f, Emotion.ARCANE_OVERFLOW, 0.32f, Emotion.FERNWEH, 0.28f);

        register("abstract", NatureStat.FOCUS, 0.07f, NatureStat.SPEED, 0.04f,
            0.44f, 1.62f, 0.64f, 1.18f,
            Emotion.YUGEN, 0.45f, Emotion.MONO_NO_AWARE, 0.34f, Emotion.MELANCHOLY, 0.26f);

        register("homestead", NatureStat.LOYALTY, 0.05f, NatureStat.VITALITY, 0.03f,
            0.75f, 1.05f, 1.10f, 1.15f,
            Emotion.CONTENT, 0.38f, Emotion.QUERECIA, 0.28f, Emotion.SOBREMESA, 0.22f);
        
        register("blossom", NatureStat.SPEED, 0.05f, NatureStat.AGILITY, 0.04f,
            1.50f, 0.65f, 1.50f, 0.85f,
            Emotion.CHEERFUL, 0.42f, Emotion.PLAYFULNESS, 0.35f, Emotion.QUERECIA, 0.28f);
        
        register("sentinel", NatureStat.DEFENSE, 0.06f, NatureStat.FOCUS, 0.04f,
            1.05f, 1.45f, 1.00f, 1.55f,
            Emotion.VIGILANT, 0.40f, Emotion.FOCUSED, 0.32f, Emotion.STOIC, 0.26f);
        
        register("scrappy", NatureStat.ATTACK, 0.05f, NatureStat.VITALITY, 0.04f,
            1.45f, 0.75f, 1.40f, 1.35f,
            Emotion.KEFI, 0.40f, Emotion.HOPEFUL, 0.30f, Emotion.FRUSTRATION, 0.26f);
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

        PetImprint imprint = component.getImprint();
        long seed = resolveSeed(component, imprint);
        if (natureId.equals(AstrologyRegistry.LUNARIS_NATURE_ID)) {
            return AstrologyRegistry.sampleAdjustment(component, seed);
        }
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

    private static long resolveSeed(PetComponent component, @Nullable PetImprint imprint) {
        if (imprint != null) {
            return imprint.getImprintSeed();
        }

        UUID uuid = component.getPet().getUuid();
        Long stored = component.getStateData(PetComponent.StateKeys.TAMED_TICK, Long.class);
        long tameTick = stored != null ? stored : component.getPet().getEntityWorld().getTime();
        return uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits() ^ tameTick;
    }

    private static float rollStatModifier(Random random, float base) {
        if (base == 0.0f) {
            return 1.0f;
        }
        float curve = (random.nextFloat() + random.nextFloat()) * 0.5f; // bell curve
        // Returns multiplier in range 0.92-1.08 (±8% variance)
        return 0.92f + curve * 0.16f;
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

    /**
     * Nature adjustment containing stat multipliers and emotional tuning for a pet.
     * Implements StatModifierProvider to integrate with the unified stat pipeline.
     */
    public record NatureAdjustment(NatureRoll majorRoll, NatureRoll minorRoll,
                                    float volatilityMultiplier, float resilienceMultiplier,
                                    float contagionModifier, float guardModifier,
                                    PetComponent.NatureEmotionProfile emotionProfile) 
            implements StatModifierProvider {
        
        public static final NatureAdjustment NONE = new NatureAdjustment(NatureRoll.EMPTY,
            NatureRoll.EMPTY, 1.0f, 1.0f, 1.0f, 1.0f,
            PetComponent.NatureEmotionProfile.EMPTY);

        @Override
        public boolean isEmpty() {
            return majorRoll.isEmpty() && minorRoll.isEmpty();
        }

        public float valueFor(NatureStat stat) {
            return majorRoll.contributionFor(stat) + minorRoll.contributionFor(stat);
        }

        // StatModifierProvider implementation - returns multiplicative bonuses
        @Override
        public float getHealthMultiplier() {
            return 1.0f + valueFor(NatureStat.HEALTH);
        }

        @Override
        public float getSpeedMultiplier() {
            return 1.0f + valueFor(NatureStat.SPEED);
        }

        @Override
        public float getAttackMultiplier() {
            return 1.0f + valueFor(NatureStat.ATTACK);
        }

        @Override
        public float getDefenseMultiplier() {
            return 1.0f + valueFor(NatureStat.DEFENSE);
        }

        @Override
        public float getAgilityMultiplier() {
            return 1.0f + valueFor(NatureStat.AGILITY);
        }

        @Override
        public float getVitalityMultiplier() {
            return 1.0f + valueFor(NatureStat.VITALITY);
        }

        @Override
        public float getSwimSpeedMultiplier() {
            return 1.0f + valueFor(NatureStat.SWIM_SPEED);
        }

        @Override
        public float getFocusMultiplier() {
            return 1.0f + valueFor(NatureStat.FOCUS);
        }

        @Override
        public float getLoyaltyMultiplier() {
            return 1.0f + valueFor(NatureStat.LOYALTY);
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
