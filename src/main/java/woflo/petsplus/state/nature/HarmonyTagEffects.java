package woflo.petsplus.state.nature;

import net.minecraft.util.math.MathHelper;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.nature.harmony.NatureHarmonySet;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps authored harmony tags to lightweight emotion accents so the harmony system can
 * bias ambient emotions without duplicating bespoke hooks in multiple systems.
 */
final class HarmonyTagEffects {

    private static final Map<String, TagProfile> TAGS = new HashMap<>();

    static {
        register("sunrise")
            .harmony(PetComponent.Emotion.CHEERFUL, 0.32f)
            .harmony(PetComponent.Emotion.HOPEFUL, 0.22f)
            .disharmony(PetComponent.Emotion.MELANCHOLY, 0.18f);
        register("uplift")
            .harmony(PetComponent.Emotion.HOPEFUL, 0.26f)
            .harmony(PetComponent.Emotion.CHEERFUL, 0.24f)
            .disharmony(PetComponent.Emotion.MELANCHOLY, 0.16f);
        register("cozy")
            .harmony(PetComponent.Emotion.QUERECIA, 0.28f)
            .harmony(PetComponent.Emotion.CONTENT, 0.2f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.16f);
        register("nurture")
            .harmony(PetComponent.Emotion.PROTECTIVE, 0.26f)
            .harmony(PetComponent.Emotion.QUERECIA, 0.18f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.16f);
        register("order")
            .harmony(PetComponent.Emotion.VIGILANT, 0.26f)
            .harmony(PetComponent.Emotion.FOCUSED, 0.22f)
            .disharmony(PetComponent.Emotion.RESTLESS, 0.18f);
        register("formation")
            .harmony(PetComponent.Emotion.VIGILANT, 0.28f)
            .harmony(PetComponent.Emotion.FOCUSED, 0.2f)
            .disharmony(PetComponent.Emotion.RESTLESS, 0.18f);
        register("celebration")
            .harmony(PetComponent.Emotion.KEFI, 0.3f)
            .harmony(PetComponent.Emotion.PLAYFULNESS, 0.22f)
            .disharmony(PetComponent.Emotion.RESTLESS, 0.18f);
        register("spark")
            .harmony(PetComponent.Emotion.KEFI, 0.28f)
            .harmony(PetComponent.Emotion.RESTLESS, 0.18f)
            .disharmony(PetComponent.Emotion.STARTLE, 0.18f);
        register("luminous")
            .harmony(PetComponent.Emotion.YUGEN, 0.28f)
            .harmony(PetComponent.Emotion.ARCANE_OVERFLOW, 0.22f)
            .disharmony(PetComponent.Emotion.FOREBODING, 0.18f);
        register("soothe")
            .harmony(PetComponent.Emotion.RELIEF, 0.28f)
            .harmony(PetComponent.Emotion.YUGEN, 0.2f)
            .disharmony(PetComponent.Emotion.ANGST, 0.18f);
        register("soothing")
            .harmony(PetComponent.Emotion.RELIEF, 0.24f)
            .harmony(PetComponent.Emotion.CONTENT, 0.16f)
            .disharmony(PetComponent.Emotion.ANGST, 0.16f);
        register("flow")
            .harmony(PetComponent.Emotion.CONTENT, 0.24f)
            .harmony(PetComponent.Emotion.LAGOM, 0.18f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.16f);
        register("artistry")
            .harmony(PetComponent.Emotion.PRIDE, 0.26f)
            .harmony(PetComponent.Emotion.NOSTALGIA, 0.18f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.18f);
        register("curation")
            .harmony(PetComponent.Emotion.PRIDE, 0.24f)
            .harmony(PetComponent.Emotion.WABI_SABI, 0.16f)
            .disharmony(PetComponent.Emotion.REGRET, 0.18f);
        register("bravery")
            .harmony(PetComponent.Emotion.SISU, 0.28f)
            .harmony(PetComponent.Emotion.FOCUSED, 0.22f)
            .disharmony(PetComponent.Emotion.ANGST, 0.2f);
        register("charge")
            .harmony(PetComponent.Emotion.SISU, 0.26f)
            .harmony(PetComponent.Emotion.KEFI, 0.18f)
            .disharmony(PetComponent.Emotion.RESTLESS, 0.18f);
        register("onslaught")
            .harmony(PetComponent.Emotion.PACK_SPIRIT, 0.28f)
            .harmony(PetComponent.Emotion.GUARDIAN_VIGIL, 0.2f)
            .disharmony(PetComponent.Emotion.ANGST, 0.2f);
        register("grounded")
            .harmony(PetComponent.Emotion.STOIC, 0.24f)
            .harmony(PetComponent.Emotion.CONTENT, 0.18f)
            .disharmony(PetComponent.Emotion.RESTLESS, 0.16f);
        register("steadfast")
            .harmony(PetComponent.Emotion.PROTECTIVE, 0.28f)
            .harmony(PetComponent.Emotion.SISU, 0.2f)
            .disharmony(PetComponent.Emotion.RESTLESS, 0.18f);
        register("precision")
            .harmony(PetComponent.Emotion.FOCUSED, 0.28f)
            .harmony(PetComponent.Emotion.VIGILANT, 0.18f)
            .disharmony(PetComponent.Emotion.RESTLESS, 0.18f);
        register("tempo")
            .harmony(PetComponent.Emotion.FOCUSED, 0.24f)
            .harmony(PetComponent.Emotion.RESTLESS, 0.18f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.18f);
        register("murmur")
            .harmony(PetComponent.Emotion.MELANCHOLY, 0.24f)
            .harmony(PetComponent.Emotion.YUGEN, 0.2f)
            .disharmony(PetComponent.Emotion.ANGST, 0.18f);
        register("reflective")
            .harmony(PetComponent.Emotion.SAUDADE, 0.26f)
            .harmony(PetComponent.Emotion.MONO_NO_AWARE, 0.22f)
            .disharmony(PetComponent.Emotion.REGRET, 0.2f);
        register("stillness")
            .harmony(PetComponent.Emotion.STOIC, 0.26f)
            .harmony(PetComponent.Emotion.YUGEN, 0.22f)
            .disharmony(PetComponent.Emotion.ENNUI, 0.2f);
        register("comfort")
            .harmony(PetComponent.Emotion.RELIEF, 0.28f)
            .harmony(PetComponent.Emotion.QUERECIA, 0.22f)
            .disharmony(PetComponent.Emotion.ANGST, 0.18f);
        register("moonlit")
            .harmony(PetComponent.Emotion.YUGEN, 0.3f)
            .harmony(PetComponent.Emotion.HIRAETH, 0.22f)
            .disharmony(PetComponent.Emotion.FOREBODING, 0.2f);
        register("serene")
            .harmony(PetComponent.Emotion.CONTENT, 0.26f)
            .harmony(PetComponent.Emotion.RELIEF, 0.2f)
            .disharmony(PetComponent.Emotion.ENNUI, 0.18f);
        register("moonlit_bravery")
            .harmony(PetComponent.Emotion.SISU, 0.26f)
            .harmony(PetComponent.Emotion.YUGEN, 0.2f)
            .disharmony(PetComponent.Emotion.ANGST, 0.18f);
        register("moonlit_haven")
            .harmony(PetComponent.Emotion.RELIEF, 0.28f)
            .harmony(PetComponent.Emotion.QUERECIA, 0.2f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.18f);
        register("care")
            .harmony(PetComponent.Emotion.PROTECTIVE, 0.26f)
            .harmony(PetComponent.Emotion.QUERECIA, 0.2f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.18f);
        register("arcane")
            .harmony(PetComponent.Emotion.ARCANE_OVERFLOW, 0.3f)
            .harmony(PetComponent.Emotion.CURIOUS, 0.22f)
            .disharmony(PetComponent.Emotion.FOREBODING, 0.2f);
        register("weirdlight")
            .harmony(PetComponent.Emotion.ARCANE_OVERFLOW, 0.26f)
            .harmony(PetComponent.Emotion.CURIOUS, 0.2f)
            .disharmony(PetComponent.Emotion.FOREBODING, 0.18f);
        register("smolder")
            .harmony(PetComponent.Emotion.KEFI, 0.26f)
            .harmony(PetComponent.Emotion.PACK_SPIRIT, 0.2f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.2f);
        register("contrast")
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.26f)
            .disharmony(PetComponent.Emotion.MELANCHOLY, 0.18f);
        register("mismatch")
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.28f)
            .disharmony(PetComponent.Emotion.ANGST, 0.2f);
        register("clash")
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.3f)
            .disharmony(PetComponent.Emotion.ANGST, 0.22f);
        register("singe")
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.3f)
            .disharmony(PetComponent.Emotion.STARTLE, 0.2f);
        register("singed")
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.28f)
            .disharmony(PetComponent.Emotion.STARTLE, 0.18f);
        register("overwhelm")
            .disharmony(PetComponent.Emotion.ANGST, 0.28f)
            .disharmony(PetComponent.Emotion.RESTLESS, 0.2f);
        register("storm")
            .disharmony(PetComponent.Emotion.ANGST, 0.28f)
            .disharmony(PetComponent.Emotion.RESTLESS, 0.22f);
        register("undertow")
            .disharmony(PetComponent.Emotion.MELANCHOLY, 0.26f)
            .disharmony(PetComponent.Emotion.SAUDADE, 0.2f);
        register("values")
            .disharmony(PetComponent.Emotion.REGRET, 0.26f)
            .disharmony(PetComponent.Emotion.DISGUST, 0.18f);
        register("patina")
            .disharmony(PetComponent.Emotion.NOSTALGIA, 0.26f)
            .disharmony(PetComponent.Emotion.REGRET, 0.2f);
        register("drag")
            .disharmony(PetComponent.Emotion.RESTLESS, 0.28f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.2f);
        register("pace")
            .disharmony(PetComponent.Emotion.RESTLESS, 0.28f)
            .disharmony(PetComponent.Emotion.ANGST, 0.2f);
        register("fidget")
            .disharmony(PetComponent.Emotion.RESTLESS, 0.26f)
            .disharmony(PetComponent.Emotion.ANGST, 0.18f);
        register("distance")
            .disharmony(PetComponent.Emotion.MELANCHOLY, 0.26f)
            .disharmony(PetComponent.Emotion.ENNUI, 0.2f);
        register("quiet")
            .disharmony(PetComponent.Emotion.ENNUI, 0.26f)
            .disharmony(PetComponent.Emotion.MELANCHOLY, 0.18f);
        register("scorch")
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.3f)
            .disharmony(PetComponent.Emotion.RESTLESS, 0.2f);
        register("wilt")
            .disharmony(PetComponent.Emotion.MELANCHOLY, 0.28f)
            .disharmony(PetComponent.Emotion.REGRET, 0.18f);
        register("din")
            .disharmony(PetComponent.Emotion.ANGST, 0.26f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.2f);
        register("crowd")
            .disharmony(PetComponent.Emotion.ANGST, 0.24f)
            .disharmony(PetComponent.Emotion.RESTLESS, 0.2f);
        register("shadow_noise")
            .disharmony(PetComponent.Emotion.FOREBODING, 0.3f)
            .disharmony(PetComponent.Emotion.ANGST, 0.22f);
        register("ego")
            .disharmony(PetComponent.Emotion.PRIDE, 0.24f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.18f);
        register("pride_chill")
            .disharmony(PetComponent.Emotion.PRIDE, 0.26f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.18f);
        register("draft")
            .disharmony(PetComponent.Emotion.ENNUI, 0.24f)
            .disharmony(PetComponent.Emotion.MELANCHOLY, 0.18f);
        register("unease")
            .disharmony(PetComponent.Emotion.ANGST, 0.28f)
            .disharmony(PetComponent.Emotion.FOREBODING, 0.2f);
        register("temper")
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.3f)
            .disharmony(PetComponent.Emotion.ANGST, 0.2f);
        register("shatter")
            .disharmony(PetComponent.Emotion.STARTLE, 0.28f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.2f);
        register("tradition")
            .disharmony(PetComponent.Emotion.REGRET, 0.26f)
            .disharmony(PetComponent.Emotion.NOSTALGIA, 0.2f);
        register("wild")
            .disharmony(PetComponent.Emotion.RESTLESS, 0.26f)
            .disharmony(PetComponent.Emotion.ANGST, 0.18f);
        register("improvisation")
            .disharmony(PetComponent.Emotion.RESTLESS, 0.26f)
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.18f);
        register("ember_glare")
            .disharmony(PetComponent.Emotion.FRUSTRATION, 0.28f)
            .disharmony(PetComponent.Emotion.FOREBODING, 0.2f);
    }

    private HarmonyTagEffects() {
    }

    static void accumulate(List<String> tags, NatureHarmonySet.Type type, float intensity,
                           EnumMap<PetComponent.Emotion, Float> sink) {
        if (tags == null || tags.isEmpty() || intensity <= 0f) {
            return;
        }
        float clampedIntensity = MathHelper.clamp(intensity, 0f, 1f);
        for (String rawTag : tags) {
            if (rawTag == null || rawTag.isEmpty()) {
                continue;
            }
            TagProfile profile = TAGS.get(rawTag.toLowerCase(Locale.ROOT));
            if (profile == null) {
                continue;
            }
            profile.apply(type, clampedIntensity, sink);
        }
    }

    private static TagProfile register(String tag) {
        TagProfile profile = new TagProfile();
        TAGS.put(tag.toLowerCase(Locale.ROOT), profile);
        return profile;
    }

    private static final class TagProfile {
        private final EnumMap<PetComponent.Emotion, Float> harmony = new EnumMap<>(PetComponent.Emotion.class);
        private final EnumMap<PetComponent.Emotion, Float> disharmony = new EnumMap<>(PetComponent.Emotion.class);

        TagProfile harmony(PetComponent.Emotion emotion, float weight) {
            harmony.put(emotion, clampWeight(weight));
            return this;
        }

        TagProfile disharmony(PetComponent.Emotion emotion, float weight) {
            disharmony.put(emotion, clampWeight(weight));
            return this;
        }

        void apply(NatureHarmonySet.Type type, float intensity, EnumMap<PetComponent.Emotion, Float> sink) {
            Map<PetComponent.Emotion, Float> map = type == NatureHarmonySet.Type.HARMONY ? harmony : disharmony;
            if (map.isEmpty() || intensity <= 0f) {
                return;
            }
            for (Map.Entry<PetComponent.Emotion, Float> entry : map.entrySet()) {
                float contribution = MathHelper.clamp(entry.getValue() * intensity, 0f, 1f);
                sink.merge(entry.getKey(), contribution, (a, b) -> MathHelper.clamp(a + b, 0f, 1.2f));
            }
        }

        private static float clampWeight(float value) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                return 0f;
            }
            return MathHelper.clamp(value, 0f, 1f);
        }
    }
}

