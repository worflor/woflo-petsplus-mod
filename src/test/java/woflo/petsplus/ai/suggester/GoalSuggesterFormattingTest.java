package woflo.petsplus.ai.suggester;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoalSuggesterFormattingTest {

    private static Method formatter;

    @BeforeAll
    static void resolveFormatter() throws Exception {
        formatter = GoalSuggester.class.getDeclaredMethod(
            "appendScaledPercent",
            StringBuilder.class,
            float.class
        );
        formatter.setAccessible(true);
    }

    @Test
    void formatsPositiveHalfStepAwayFromZero() throws Exception {
        assertMatchesFormatter(0.005f);
    }

    @Test
    void formatsNegativeHalfStepAwayFromZero() throws Exception {
        assertMatchesFormatter(-0.005f);
    }

    @Test
    void preservesNegativeZeroWhenRounded() throws Exception {
        assertMatchesFormatter(-0.004f);
    }

    @Test
    void rendersPositiveInfinity() throws Exception {
        assertMatchesFormatter(Float.POSITIVE_INFINITY);
    }

    @Test
    void rendersNegativeInfinity() throws Exception {
        assertMatchesFormatter(Float.NEGATIVE_INFINITY);
    }

    @Test
    void rendersNan() throws Exception {
        assertMatchesFormatter(Float.NaN);
    }

    private static String format(float value) throws Exception {
        StringBuilder out = new StringBuilder();
        formatter.invoke(null, out, value);
        return out.toString();
    }

    private static void assertMatchesFormatter(float value) throws Exception {
        String expected = String.format(Locale.ROOT, "%.2f", value);
        assertEquals(expected, format(value));
    }
}
