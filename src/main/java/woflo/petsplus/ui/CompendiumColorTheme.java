package woflo.petsplus.ui;

import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Nature-driven color palette helpers for the Pet Compendium.
 * Each nature defines a single base hue; brighter, softer, and shadowed accents
 * are derived at runtime so born natures feel vivid while tamed natures keep a
 * calmer presentation.
 */
public final class CompendiumColorTheme {

    public static final String LIGHT_GRAY = "§7";
    public static final String DARK_GRAY = "§8";
    public static final String WHITE = "§f";
    public static final String RESET = "§r";
    public static final String BOLD = "§l";
    public static final String UNDERLINE = "§n";

    private static final Palette NEUTRAL_PALETTE = new Palette(0x8A8A8A, NatureLineage.TAME);
    private static final Map<String, Palette> PALETTES = buildPalettes();

    private CompendiumColorTheme() {
    }

    public static Palette palette(@Nullable Identifier natureId) {
        if (natureId == null) {
            return NEUTRAL_PALETTE;
        }
        Palette palette = PALETTES.get(normalizeKey(natureId));
        return palette != null ? palette : NEUTRAL_PALETTE;
    }

    public static TextColor getNatureAccent(@Nullable Identifier natureId) {
        return palette(natureId).accent();
    }

    public static String getNatureAccentCode(@Nullable Identifier natureId) {
        return palette(natureId).accentCode();
    }

    public static String getNatureHighlightCode(@Nullable Identifier natureId) {
        return palette(natureId).highlightCode();
    }

    public static String getNatureSoftCode(@Nullable Identifier natureId) {
        return palette(natureId).softCode();
    }

    public static String getNatureShadowCode(@Nullable Identifier natureId) {
        return palette(natureId).shadowCode();
    }

    public static String getNatureDeepShadowCode(@Nullable Identifier natureId) {
        return palette(natureId).deepShadowCode();
    }

    public static boolean isBornNature(@Nullable Identifier natureId) {
        return palette(natureId).lineage() == NatureLineage.BORN;
    }

    public static String formatSectionHeader(String label, @Nullable Identifier natureId) {
        Palette palette = palette(natureId);
        return palette.accentCode() + BOLD + label + RESET;
    }

    public static String formatLabelValue(String label, String value, @Nullable Identifier natureId) {
        return LIGHT_GRAY + label + DARK_GRAY + ": " + getNatureHighlightCode(natureId) + value + RESET;
    }

    public static String formatClickableLink(String label, @Nullable Identifier natureId) {
        return getNatureShadowCode(natureId) + "▸ " + getNatureAccentCode(natureId) + label + RESET;
    }

    public static String buildSectionDivider(@Nullable Identifier natureId) {
        Palette palette = palette(natureId);
        StringBuilder builder = new StringBuilder();
        builder.append(palette.shadowCode()).append("┈");
        for (int i = 0; i < 18; i++) {
            float blend = i / 17f;
            // Alternate between highlight and soft codes to hint at a gradient.
            builder.append(blend < 0.35f ? palette.softCode() : blend > 0.7f ? palette.deepShadowCode() : palette.highlightCode())
                .append("─");
        }
        builder.append(palette.shadowCode()).append("┈").append(RESET);
        return builder.toString();
    }

    public static String formatSoftNote(String text, @Nullable Identifier natureId) {
        Palette palette = palette(natureId);
        return palette.softCode() + text + RESET;
    }

    public static String formatInlineBadge(String text, @Nullable Identifier natureId) {
        Palette palette = palette(natureId);
        return palette.shadowCode() + "[" + palette.highlightCode() + text + palette.shadowCode() + "]" + RESET;
    }

    private static Map<String, Palette> buildPalettes() {
        Map<String, Palette> map = new HashMap<>();
        register(map, "fenn", 0x4E8AD1, NatureLineage.TAME);
        register(map, "falsi", 0x7FCB5F, NatureLineage.TAME);
        register(map, "frisky", 0x58B5F6, NatureLineage.TAME);
        register(map, "feral", 0xA17A4E, NatureLineage.TAME);
        register(map, "fierce", 0xD95F3C, NatureLineage.TAME);

        register(map, "radiant", 0xF6C94C, NatureLineage.BORN);
        register(map, "lunaris", 0x7A5CF5, NatureLineage.BORN);
        register(map, "homestead", 0xD48D63, NatureLineage.BORN);
        register(map, "hearth", 0xE67F4B, NatureLineage.BORN);
        register(map, "tempest", 0x5195F6, NatureLineage.BORN);
        register(map, "solace", 0x7A9AB1, NatureLineage.BORN);
        register(map, "festival", 0xEB63B0, NatureLineage.BORN);
        register(map, "otherworldly", 0x6C7DF5, NatureLineage.BORN);
        register(map, "infernal", 0xD64545, NatureLineage.BORN);
        register(map, "echoed", 0x3DB6C4, NatureLineage.BORN);
        register(map, "mycelial", 0xA887F0, NatureLineage.BORN);
        register(map, "gilded", 0xE6B64D, NatureLineage.BORN);
        register(map, "gloom", 0x5B5D72, NatureLineage.BORN);
        register(map, "verdant", 0x3FA856, NatureLineage.BORN);
        register(map, "summit", 0x9FD3F9, NatureLineage.BORN);
        register(map, "tidal", 0x3FA7D6, NatureLineage.BORN);
        register(map, "molten", 0xF07A35, NatureLineage.BORN);
        register(map, "frosty", 0x79C8F2, NatureLineage.BORN);
        register(map, "mire", 0x6B8F39, NatureLineage.BORN);
        register(map, "relic", 0x8A9BA8, NatureLineage.BORN);
        register(map, "ceramic", 0xC69E72, NatureLineage.BORN);
        register(map, "blossom", 0xF28ABF, NatureLineage.BORN);
        register(map, "clockwork", 0xD4A55A, NatureLineage.BORN);
        register(map, "sentinel", 0x5F8FB2, NatureLineage.BORN);
        register(map, "scrappy", 0xB8573F, NatureLineage.BORN);
        register(map, "unnatural", 0xC95CF2, NatureLineage.BORN);
        register(map, "abstract", 0x4F6BD8, NatureLineage.BORN);

        return Map.copyOf(map);
    }

    private static void register(Map<String, Palette> map, String key, int baseRgb, NatureLineage lineage) {
        map.put(key, new Palette(baseRgb, lineage));
    }

    private static String normalizeKey(Identifier identifier) {
        if ("petsplus".equals(identifier.getNamespace())) {
            return identifier.getPath();
        }
        return identifier.toString().toLowerCase(Locale.ROOT);
    }

    private static String toLegacyCode(int rgb) {
        String hex = String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
        StringBuilder builder = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            builder.append('§').append(c);
        }
        return builder.toString();
    }

    private enum NatureLineage {
        TAME,
        BORN
    }

    public static final class Palette {
        private final int baseRgb;
        private final int accentRgb;
        private final int highlightRgb;
        private final int softRgb;
        private final int shadowRgb;
        private final int deepShadowRgb;
        private final NatureLineage lineage;
        private final TextColor accent;
        private final TextColor highlight;
        private final TextColor soft;
        private final TextColor shadow;
        private final TextColor deepShadow;
        private final String accentCode;
        private final String highlightCode;
        private final String softCode;
        private final String shadowCode;
        private final String deepShadowCode;

        private Palette(int baseRgb, NatureLineage lineage) {
            this.baseRgb = baseRgb;
            this.lineage = lineage;

            float[] hsl = rgbToHsl(baseRgb);
            float accentS = clamp01(hsl[1] * (lineage == NatureLineage.BORN ? 1.18f : 0.82f));
            float accentL = clamp01(hsl[2] + (lineage == NatureLineage.BORN ? 0.02f : -0.02f));
            this.accentRgb = hslToRgb(hsl[0], accentS, accentL);

            float highlightL = clamp01(accentL + (lineage == NatureLineage.BORN ? 0.18f : 0.12f));
            float highlightS = clamp01(accentS * (lineage == NatureLineage.BORN ? 0.88f : 0.78f));
            this.highlightRgb = hslToRgb(hsl[0], highlightS, highlightL);

            float softL = clamp01(accentL + (lineage == NatureLineage.BORN ? 0.10f : 0.06f));
            float softS = clamp01(accentS * (lineage == NatureLineage.BORN ? 0.75f : 0.70f));
            this.softRgb = hslToRgb(hsl[0], softS, softL);

            float shadowL = clamp01(accentL - (lineage == NatureLineage.BORN ? 0.20f : 0.16f));
            float shadowS = clamp01(accentS * (lineage == NatureLineage.BORN ? 1.05f : 0.95f));
            this.shadowRgb = hslToRgb(hsl[0], shadowS, shadowL);

            float deepShadowL = clamp01(accentL - (lineage == NatureLineage.BORN ? 0.30f : 0.24f));
            float deepShadowS = clamp01(accentS * (lineage == NatureLineage.BORN ? 1.10f : 1.00f));
            this.deepShadowRgb = hslToRgb(hsl[0], deepShadowS, deepShadowL);

            this.accent = TextColor.fromRgb(this.accentRgb);
            this.highlight = TextColor.fromRgb(this.highlightRgb);
            this.soft = TextColor.fromRgb(this.softRgb);
            this.shadow = TextColor.fromRgb(this.shadowRgb);
            this.deepShadow = TextColor.fromRgb(this.deepShadowRgb);

            this.accentCode = toLegacyCode(this.accentRgb);
            this.highlightCode = toLegacyCode(this.highlightRgb);
            this.softCode = toLegacyCode(this.softRgb);
            this.shadowCode = toLegacyCode(this.shadowRgb);
            this.deepShadowCode = toLegacyCode(this.deepShadowRgb);
        }

        public TextColor accent() {
            return accent;
        }

        public TextColor highlight() {
            return highlight;
        }

        public TextColor soft() {
            return soft;
        }

        public TextColor shadow() {
            return shadow;
        }

        public TextColor deepShadow() {
            return deepShadow;
        }

        public NatureLineage lineage() {
            return lineage;
        }

        public String accentCode() {
            return accentCode;
        }

        public String highlightCode() {
            return highlightCode;
        }

        public String softCode() {
            return softCode;
        }

        public String shadowCode() {
            return shadowCode;
        }

        public String deepShadowCode() {
            return deepShadowCode;
        }

        public int baseRgb() {
            return baseRgb;
        }
    }

    private static float[] rgbToHsl(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float h;
        float s;
        float l = (max + min) / 2f;

        if (MathHelper.approximatelyEquals(max, min)) {
            h = 0f;
            s = 0f;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (MathHelper.approximatelyEquals(max, r)) {
                h = (g - b) / d + (g < b ? 6f : 0f);
            } else if (MathHelper.approximatelyEquals(max, g)) {
                h = (b - r) / d + 2f;
            } else {
                h = (r - g) / d + 4f;
            }
            h /= 6f;
        }

        return new float[]{h, s, l};
    }

    private static int hslToRgb(float h, float s, float l) {
        if (s <= 0f) {
            int gray = Math.round(l * 255f);
            return (gray << 16) | (gray << 8) | gray;
        }

        float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
        float p = 2f * l - q;

        float r = hueToRgb(p, q, h + 1f / 3f);
        float g = hueToRgb(p, q, h);
        float b = hueToRgb(p, q, h - 1f / 3f);

        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }

    private static float clamp01(float value) {
        return MathHelper.clamp(value, 0f, 1f);
    }
}

