package woflo.petsplus.stats.nature.harmony;

import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds authored harmony/disharmony set definitions and provides per-nature lookups.
 */
public final class NatureHarmonyRegistry {

    public record HarmonyEntry(boolean replace, NatureHarmonySet set) {}

    private static final Map<Identifier, NatureHarmonySet> DEFAULTS = new LinkedHashMap<>();
    private static final Map<Identifier, NatureHarmonySet> REGISTRY = new LinkedHashMap<>();
    private static final Map<Identifier, List<NatureHarmonySet>> BY_NATURE = new HashMap<>();
    private static final Set<Identifier> KNOWN_NATURES = Set.of(
        id("frisky"),
        id("feral"),
        id("fierce"),
        id("radiant"),
        id("lunaris"),
        id("festival"),
        id("infernal"),
        id("otherworldly"),
        id("hearth"),
        id("tempest"),
        id("solace"),
        id("echoed"),
        id("mycelial"),
        id("gilded"),
        id("gloom"),
        id("verdant"),
        id("summit"),
        id("tidal"),
        id("molten"),
        id("frosty"),
        id("mire"),
        id("relic"),
        id("ceramic"),
        id("clockwork"),
        id("unnatural"),
        id("homestead"),
        id("blossom"),
        id("sentinel"),
        id("scrappy")
    );

    static {
        registerDefaults();
        REGISTRY.putAll(DEFAULTS);
        rebuildIndex();
    }

    private NatureHarmonyRegistry() {
    }

    private static Identifier id(String path) {
        return Identifier.of("petsplus", path);
    }

    private static void registerDefaults() {
        // Harmony combinations
        registerDefault("harmony/radiant_blossom", NatureHarmonySet.Type.HARMONY,
            5.5d, 1.22f, 1.14f, 0.88f, 1.08f, 1.03f, 120,
            List.of("sunrise", "uplift"), "petsplus:radiant", "petsplus:blossom");
        registerDefault("harmony/hearth_homestead_verdant", NatureHarmonySet.Type.HARMONY,
            6.0d, 1.09f, 1.06f, 0.92f, 1.18f, 1.12f, 150,
            List.of("cozy", "nurture"), "petsplus:hearth", "petsplus:homestead", "petsplus:verdant");
        registerDefault("harmony/sentinel_clockwork", NatureHarmonySet.Type.HARMONY,
            5.0d, 1.04f, 1.02f, 0.86f, 1.10f, 1.18f, 110,
            List.of("order", "formation"), "petsplus:sentinel", "petsplus:clockwork");
        registerDefault("harmony/festival_scrappy_frisky", NatureHarmonySet.Type.HARMONY,
            5.75d, 1.15f, 1.18f, 1.08f, 0.98f, 1.00f, 110,
            List.of("celebration", "spark"), "petsplus:festival", "petsplus:scrappy", "petsplus:frisky");
        registerDefault("harmony/solace_echoed_otherworldly", NatureHarmonySet.Type.HARMONY,
            5.25d, 1.10f, 1.04f, 0.82f, 1.20f, 1.05f, 150,
            List.of("luminous", "soothe"), "petsplus:solace", "petsplus:echoed", "petsplus:otherworldly");
        registerDefault("harmony/mycelial_tidal", NatureHarmonySet.Type.HARMONY,
            5.5d, 1.08f, 1.07f, 0.86f, 1.10f, 1.02f, 130,
            List.of("soothing", "flow"), "petsplus:mycelial", "petsplus:tidal");
        registerDefault("harmony/gilded_relic_ceramic", NatureHarmonySet.Type.HARMONY,
            5.0d, 1.06f, 1.03f, 0.94f, 1.09f, 1.16f, 120,
            List.of("artistry", "curation"), "petsplus:gilded", "petsplus:relic", "petsplus:ceramic");
        registerDefault("harmony/tempest_molten_fierce", NatureHarmonySet.Type.HARMONY,
            5.75d, 1.07f, 1.02f, 1.12f, 1.05f, 1.13f, 120,
            List.of("bravery", "onslaught"), "petsplus:tempest", "petsplus:molten", "petsplus:fierce");
        registerDefault("harmony/feral_mire", NatureHarmonySet.Type.HARMONY,
            4.75d, 1.05f, 1.02f, 0.96f, 1.08f, 1.07f, 100,
            List.of("steadfast", "grounded"), "petsplus:feral", "petsplus:mire");
        registerDefault("harmony/summit_clockwork", NatureHarmonySet.Type.HARMONY,
            5.0d, 1.06f, 1.01f, 0.88f, 1.07f, 1.10f, 110,
            List.of("precision", "tempo"), "petsplus:summit", "petsplus:clockwork");
        registerDefault("harmony/gloom_mycelial", NatureHarmonySet.Type.HARMONY,
            5.25d, 1.04f, 1.06f, 0.85f, 1.08f, 1.01f, 130,
            List.of("reflective", "murmur"), "petsplus:gloom", "petsplus:mycelial");
        registerDefault("harmony/frosty_solace", NatureHarmonySet.Type.HARMONY,
            5.25d, 1.07f, 1.03f, 0.82f, 1.15f, 1.06f, 130,
            List.of("stillness", "comfort"), "petsplus:frosty", "petsplus:solace");
        registerDefault("harmony/lunaris_solace_frosty", NatureHarmonySet.Type.HARMONY,
            5.5d, 1.11f, 1.05f, 0.80f, 1.17f, 1.07f, 150,
            List.of("moonlit", "serene"), "petsplus:lunaris", "petsplus:solace", "petsplus:frosty");
        registerDefault("harmony/infernal_molten", NatureHarmonySet.Type.HARMONY,
            5.5d, 1.06f, 1.04f, 1.10f, 1.00f, 1.08f, 110,
            List.of("smolder", "onslaught"), "petsplus:infernal", "petsplus:molten");
        registerDefault("harmony/unnatural_otherworldly", NatureHarmonySet.Type.HARMONY,
            5.5d, 1.09f, 1.05f, 0.90f, 1.04f, 1.05f, 130,
            List.of("arcane", "weirdlight"), "petsplus:unnatural", "petsplus:otherworldly");

        registerDefault("harmony/lunaris_aries_tempest", NatureHarmonySet.Type.HARMONY,
            5.5d, 1.09f, 1.06f, 0.94f, 1.06f, 1.09f, 120,
            List.of("moonlit_bravery", "charge"),
            new NatureHarmonySet.Member(id("lunaris"), Set.of(id("lunaris/aries"))),
            new NatureHarmonySet.Member(id("tempest"), Set.of()));

        registerDefault("harmony/lunaris_cancer_hearth", NatureHarmonySet.Type.HARMONY,
            5.5d, 1.10f, 1.08f, 0.86f, 1.16f, 1.08f, 140,
            List.of("moonlit_haven", "care"),
            new NatureHarmonySet.Member(id("lunaris"), Set.of(id("lunaris/cancer"))),
            new NatureHarmonySet.Member(id("hearth"), Set.of()));

        // Disharmony combinations
        registerDefault("disharmony/radiant_gloom", NatureHarmonySet.Type.DISHARMONY,
            5.5d, 0.88f, 0.92f, 1.10f, 0.94f, 0.97f, 110,
            List.of("contrast", "mismatch"), "petsplus:radiant", "petsplus:gloom");
        registerDefault("disharmony/hearth_infernal", NatureHarmonySet.Type.DISHARMONY,
            5.5d, 0.90f, 0.93f, 1.12f, 0.92f, 0.95f, 110,
            List.of("clash", "scorch"), "petsplus:hearth", "petsplus:infernal");
        registerDefault("disharmony/homestead_unnatural", NatureHarmonySet.Type.DISHARMONY,
            5.0d, 0.91f, 0.95f, 1.09f, 0.93f, 0.95f, 120,
            List.of("unease", "draft"), "petsplus:homestead", "petsplus:unnatural");
        registerDefault("disharmony/festival_solace", NatureHarmonySet.Type.DISHARMONY,
            5.5d, 0.89f, 0.92f, 1.09f, 0.91f, 0.97f, 110,
            List.of("mixed_signals", "overwhelm"), "petsplus:festival", "petsplus:solace");
        registerDefault("disharmony/tempest_tidal", NatureHarmonySet.Type.DISHARMONY,
            5.25d, 0.90f, 0.93f, 1.12f, 0.92f, 0.96f, 110,
            List.of("storm", "undertow"), "petsplus:tempest", "petsplus:tidal");
        registerDefault("disharmony/mycelial_gilded", NatureHarmonySet.Type.DISHARMONY,
            5.0d, 0.92f, 0.91f, 1.07f, 0.93f, 0.95f, 110,
            List.of("values", "patina"), "petsplus:mycelial", "petsplus:gilded");
        registerDefault("disharmony/summit_mire", NatureHarmonySet.Type.DISHARMONY,
            5.0d, 0.90f, 0.92f, 1.09f, 0.91f, 0.94f, 110,
            List.of("friction", "drag"), "petsplus:summit", "petsplus:mire");
        registerDefault("disharmony/clockwork_scrappy", NatureHarmonySet.Type.DISHARMONY,
            5.25d, 0.91f, 0.90f, 1.11f, 0.93f, 0.95f, 100,
            List.of("tempo", "improvisation"), "petsplus:clockwork", "petsplus:scrappy");
        registerDefault("disharmony/feral_relic", NatureHarmonySet.Type.DISHARMONY,
            5.0d, 0.89f, 0.91f, 1.10f, 0.90f, 0.93f, 110,
            List.of("tradition", "wild"), "petsplus:feral", "petsplus:relic");
        registerDefault("disharmony/fierce_ceramic", NatureHarmonySet.Type.DISHARMONY,
            5.0d, 0.90f, 0.92f, 1.11f, 0.92f, 0.95f, 110,
            List.of("temper", "shatter"), "petsplus:fierce", "petsplus:ceramic");
        registerDefault("disharmony/frisky_sentinel", NatureHarmonySet.Type.DISHARMONY,
            5.0d, 0.90f, 0.92f, 1.09f, 0.92f, 0.96f, 100,
            List.of("pace", "fidget"), "petsplus:frisky", "petsplus:sentinel");
        registerDefault("disharmony/otherworldly_frosty", NatureHarmonySet.Type.DISHARMONY,
            5.25d, 0.91f, 0.93f, 1.06f, 0.93f, 0.95f, 120,
            List.of("distance", "quiet"), "petsplus:otherworldly", "petsplus:frosty");
        registerDefault("disharmony/verdant_molten", NatureHarmonySet.Type.DISHARMONY,
            5.25d, 0.89f, 0.92f, 1.12f, 0.90f, 0.96f, 110,
            List.of("scorch", "wilt"), "petsplus:verdant", "petsplus:molten");
        registerDefault("disharmony/echoed_festival", NatureHarmonySet.Type.DISHARMONY,
            5.5d, 0.90f, 0.92f, 1.09f, 0.92f, 0.96f, 120,
            List.of("din", "crowd"), "petsplus:echoed", "petsplus:festival");
        registerDefault("disharmony/blossom_infernal", NatureHarmonySet.Type.DISHARMONY,
            5.5d, 0.88f, 0.92f, 1.11f, 0.91f, 0.95f, 120,
            List.of("spark", "singed"), "petsplus:blossom", "petsplus:infernal");
        registerDefault("disharmony/lunaris_infernal", NatureHarmonySet.Type.DISHARMONY,
            5.5d, 0.88f, 0.91f, 1.12f, 0.90f, 0.95f, 120,
            List.of("ember_glare", "singe"), "petsplus:lunaris", "petsplus:infernal");

        registerDefault("disharmony/lunaris_leo_frosty", NatureHarmonySet.Type.DISHARMONY,
            5.5d, 0.87f, 0.92f, 1.10f, 0.90f, 0.95f, 120,
            List.of("pride_chill", "ego"),
            new NatureHarmonySet.Member(id("lunaris"), Set.of(id("lunaris/leo"))),
            new NatureHarmonySet.Member(id("frosty"), Set.of()));

        registerDefault("disharmony/lunaris_scorpio_festival", NatureHarmonySet.Type.DISHARMONY,
            5.5d, 0.86f, 0.90f, 1.14f, 0.89f, 0.94f, 120,
            List.of("shadow_noise", "needling"),
            new NatureHarmonySet.Member(id("lunaris"), Set.of(id("lunaris/scorpio"))),
            new NatureHarmonySet.Member(id("festival"), Set.of()));
    }

    private static void registerDefault(String id, NatureHarmonySet.Type type,
                                        double radius, float mood, float contagion,
                                        float volatility, float resilience, float guard,
                                        int lingerTicks, List<String> tags, String... memberIds) {
        List<NatureHarmonySet.Member> members = new ArrayList<>(memberIds.length);
        for (String member : memberIds) {
            Identifier nature = Identifier.tryParse(member);
            if (nature == null) {
                continue;
            }
            members.add(new NatureHarmonySet.Member(nature, Set.of()));
        }
        registerDefault(id, type, radius, mood, contagion, volatility, resilience, guard, lingerTicks, tags, members);
    }

    private static void registerDefault(String id, NatureHarmonySet.Type type,
                                        double radius, float mood, float contagion,
                                        float volatility, float resilience, float guard,
                                        int lingerTicks, List<String> tags, List<NatureHarmonySet.Member> members) {
        NatureHarmonySet set = new NatureHarmonySet(Identifier.of("petsplus", id), type, members, radius,
            mood, contagion, volatility, resilience, guard, lingerTicks, tags);
        DEFAULTS.put(set.id(), set);
    }

    private static void registerDefault(String id, NatureHarmonySet.Type type,
                                        double radius, float mood, float contagion,
                                        float volatility, float resilience, float guard,
                                        int lingerTicks, List<String> tags, NatureHarmonySet.Member... members) {
        registerDefault(id, type, radius, mood, contagion, volatility, resilience, guard, lingerTicks, tags,
            List.of(members));
    }

    public static synchronized void reload(Map<Identifier, HarmonyEntry> overrides) {
        Map<Identifier, NatureHarmonySet> merged = new LinkedHashMap<>(DEFAULTS);
        if (overrides != null) {
            for (Map.Entry<Identifier, HarmonyEntry> entry : overrides.entrySet()) {
                HarmonyEntry override = entry.getValue();
                if (override == null || override.set() == null) {
                    continue;
                }
                Identifier id = override.set().id();
                if (override.replace() || !merged.containsKey(id)) {
                    merged.put(id, override.set());
                } else {
                    Petsplus.LOGGER.warn("Duplicate harmony set {} ignored because replace=false", id);
                }
            }
        }
        REGISTRY.clear();
        REGISTRY.putAll(merged);
        rebuildIndex();
    }

    private static void rebuildIndex() {
        BY_NATURE.clear();
        for (NatureHarmonySet set : REGISTRY.values()) {
            for (NatureHarmonySet.Member member : set.members()) {
                BY_NATURE.computeIfAbsent(member.natureId(), k -> new ArrayList<>()).add(set);
            }
        }
        for (Map.Entry<Identifier, List<NatureHarmonySet>> entry : BY_NATURE.entrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        validateCoverage();
    }

    private static void validateCoverage() {
        for (Identifier natureId : KNOWN_NATURES) {
            List<NatureHarmonySet> sets = BY_NATURE.getOrDefault(natureId, List.of());
            boolean hasHarmony = sets.stream().anyMatch(set -> set.type() == NatureHarmonySet.Type.HARMONY);
            boolean hasDisharmony = sets.stream().anyMatch(set -> set.type() == NatureHarmonySet.Type.DISHARMONY);
            if (!hasHarmony || !hasDisharmony) {
                Petsplus.LOGGER.warn("Nature '{}' is missing {} harmony coverage",
                    natureId, !hasHarmony ? "positive" : "negative");
            }
        }
    }

    public static List<NatureHarmonySet> getSetsForNature(Identifier natureId) {
        if (natureId == null) {
            return List.of();
        }
        return BY_NATURE.getOrDefault(natureId, List.of());
    }

    public static Collection<NatureHarmonySet> all() {
        return REGISTRY.values();
    }
}
