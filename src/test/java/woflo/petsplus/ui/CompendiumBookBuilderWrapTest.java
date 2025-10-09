package woflo.petsplus.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.text.Text;

final class CompendiumBookBuilderWrapTest {

    @Test
    void wrapPreservesIndentOnContinuationLines() throws Exception {
        Method method = CompendiumBookBuilder.class.getDeclaredMethod(
            "wrapLiteralWithFormatting",
            String.class,
            int.class
        );
        method.setAccessible(true);

        String text = "§7  • Traded with the Wandering Trader for a stack of shimmering "
            + "emerald-infused yarn bundles.";

        @SuppressWarnings("unchecked")
        List<String> fragments = (List<String>) method.invoke(null, text, 50);

        assertTrue(fragments.size() > 1, "Expected wrapped output to span multiple lines");

        for (int i = 0; i < fragments.size(); i++) {
            String fragment = fragments.get(i).replaceAll("§.", "");
            assertTrue(
                fragment.startsWith("  "),
                "Wrapped fragment " + i + " should preserve its two-space indent"
            );
        }
    }

    @Test
    void wrapHandlesCompositeTextLines() throws Exception {
        Method method = CompendiumBookBuilder.class.getDeclaredMethod(
            "wrapLinePreservingFormatting",
            net.minecraft.text.Text.class
        );
        method.setAccessible(true);

        Text composite = Text.literal("§7  • ")
            .append(Text.literal("§fThis composite entry stitches together multiple text components "
                + "so it easily overruns the vanilla book width limit when left unwrapped."));

        @SuppressWarnings("unchecked")
        List<Text> fragments = (List<Text>) method.invoke(null, composite);

        assertTrue(fragments.size() > 1, "Composite lines should wrap across multiple fragments");

        for (Text fragment : fragments) {
            String stripped = fragment.getString();
            assertTrue(
                stripped.startsWith("  "),
                "Wrapped composite fragment should retain the two-space indent"
            );
        }
    }
}
